package com.mesha.api.service;

import com.mesha.api.model.User;
import com.mesha.api.model.UserDevice;
import com.mesha.api.repository.UserDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the current user's push-notification device tokens. The mobile client
 * registers its FCM token after sign-in (and whenever notifications are switched on),
 * and unregisters it when notifications are switched off or on sign-out.
 */
@Service
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private static final String DEFAULT_PLATFORM = "ANDROID";

    private final UserDeviceRepository userDeviceRepository;

    public DeviceService(UserDeviceRepository userDeviceRepository) {
        this.userDeviceRepository = userDeviceRepository;
    }

    /**
     * Upsert a device token for the given user. A token is globally unique, so if it was
     * previously registered (possibly to another user on a shared device) it is re-owned
     * by the current user rather than duplicated.
     */
    @Transactional
    public void register(User user, String fcmToken, String platform) {
        String normalizedPlatform = (platform != null && !platform.isBlank())
            ? platform.toUpperCase() : DEFAULT_PLATFORM;

        UserDevice device = userDeviceRepository.findByFcmToken(fcmToken)
            .orElseGet(UserDevice::new);
        device.setUser(user);
        device.setFcmToken(fcmToken);
        device.setPlatform(normalizedPlatform);
        userDeviceRepository.save(device);
        log.info("device_registered userId={} platform={}", user.getId(), normalizedPlatform);
    }

    /** Remove a device token (idempotent). */
    @Transactional
    public void unregister(User user, String fcmToken) {
        userDeviceRepository.deleteByFcmToken(fcmToken);
        log.info("device_unregistered userId={}", user.getId());
    }
}
