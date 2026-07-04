package com.mesha.api.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import com.mesha.api.config.FirebaseProperties;
import com.mesha.api.model.Issue;
import com.mesha.api.model.User;
import com.mesha.api.model.UserDevice;
import com.mesha.api.repository.UserDeviceRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends Firebase Cloud Messaging (FCM) push notifications to workspace members when a
 * ticket's status changes. Messages are data-only so the Android client renders the
 * notification itself (and can honour the user's local on/off preference even for an
 * in-flight message). Delivery is best-effort: failures never propagate to the caller,
 * and tokens FCM reports as unregistered are pruned.
 *
 * <p>When Firebase credentials are not configured the service is disabled and every
 * call is a no-op, so non-production environments run without Firebase.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    /** Discriminator the mobile client reads to route the notification payload. */
    static final String TYPE_STATUS_CHANGED = "TICKET_STATUS_CHANGED";
    private static final String FIREBASE_APP_NAME = "mesha";

    private final FirebaseProperties properties;
    private final UserDeviceRepository userDeviceRepository;

    private volatile FirebaseMessaging messaging;

    public PushNotificationService(FirebaseProperties properties, UserDeviceRepository userDeviceRepository) {
        this.properties = properties;
        this.userDeviceRepository = userDeviceRepository;
    }

    @PostConstruct
    void init() {
        if (!properties.isConfigured()) {
            log.info("push_notifications_disabled reason=no_firebase_credentials");
            return;
        }
        try {
            FirebaseApp app = existingApp();
            if (app == null) {
                FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(
                        new ByteArrayInputStream(properties.credentialsJson().getBytes(StandardCharsets.UTF_8))))
                    .build();
                app = FirebaseApp.initializeApp(options, FIREBASE_APP_NAME);
            }
            this.messaging = FirebaseMessaging.getInstance(app);
            log.info("push_notifications_enabled");
        } catch (Exception e) {
            log.error("push_notifications_init_failed error={}", e.getMessage(), e);
        }
    }

    private static FirebaseApp existingApp() {
        for (FirebaseApp app : FirebaseApp.getApps()) {
            if (FIREBASE_APP_NAME.equals(app.getName())) {
                return app;
            }
        }
        return null;
    }

    public boolean isEnabled() {
        return messaging != null;
    }

    /**
     * Immutable snapshot of everything a status-change notification needs, extracted
     * from the {@link Issue} while its JPA associations are still loadable (i.e. inside
     * the caller's transaction). Passing primitives keeps the async send free of any
     * lazy-initialization on a detached entity.
     */
    public record StatusChange(
        UUID workspaceId,
        String issueId,
        String projectId,
        String identifier,
        String title,
        String newStatus,
        UUID actorId
    ) {
        public static StatusChange from(Issue issue, String newStatus, User actor) {
            String key = issue.getProject() != null ? issue.getProject().getKey() : null;
            Integer number = issue.getNumber();
            String identifier = (key != null && number != null) ? key + "-" + number : null;
            return new StatusChange(
                issue.getProject().getWorkspace().getId(),
                issue.getId().toString(),
                issue.getProject().getId().toString(),
                identifier,
                issue.getTitle() != null ? issue.getTitle() : "",
                newStatus,
                actor != null ? actor.getId() : null
            );
        }
    }

    /**
     * Notify every workspace member (except the actor who made the change) that a
     * ticket moved to a new status. Runs on a background thread; the device lookup and
     * FCM send never block or fail the triggering update.
     */
    @Async
    public void notifyStatusChanged(StatusChange change) {
        if (!isEnabled()) {
            return;
        }
        try {
            List<UserDevice> devices = userDeviceRepository.findAllForWorkspace(change.workspaceId()).stream()
                .filter(d -> change.actorId() == null || !d.getUser().getId().equals(change.actorId()))
                .toList();
            if (devices.isEmpty()) {
                return;
            }

            List<Message> messages = new ArrayList<>(devices.size());
            for (UserDevice device : devices) {
                messages.add(Message.builder()
                    .setToken(device.getFcmToken())
                    .putData("type", TYPE_STATUS_CHANGED)
                    .putData("issueId", change.issueId())
                    .putData("projectId", change.projectId())
                    .putData("identifier", change.identifier() != null ? change.identifier() : "")
                    .putData("title", change.title())
                    .putData("status", change.newStatus())
                    .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .build())
                    .build());
            }

            BatchResponse response = messaging.sendEach(messages);
            pruneStaleTokens(devices, response);
            log.info("push_status_changed issueId={} status={} sent={} failed={}",
                change.issueId(), change.newStatus(), response.getSuccessCount(), response.getFailureCount());
        } catch (Exception e) {
            // Never let notification delivery affect the triggering request.
            log.error("push_status_changed_failed issueId={} error={}", change.issueId(), e.getMessage(), e);
        }
    }

    /**
     * Remove device tokens FCM rejected as unregistered/invalid so we stop sending to
     * uninstalled or logged-out devices. Each removal runs in its own repository
     * transaction; failures here are logged and ignored.
     */
    private void pruneStaleTokens(List<UserDevice> devices, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (r.isSuccessful() || r.getException() == null) {
                continue;
            }
            MessagingErrorCode code = r.getException().getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                try {
                    userDeviceRepository.deleteByFcmToken(devices.get(i).getFcmToken());
                    log.debug("push_token_pruned reason={}", code);
                } catch (Exception e) {
                    log.warn("push_token_prune_failed error={}", e.getMessage());
                }
            }
        }
    }
}
