package com.mesha.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.dto.IssueDto;
import com.mesha.api.model.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class IssueSseService {

    private static final Logger log = LoggerFactory.getLogger(IssueSseService.class);
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final ObjectProvider<IssueSseService> self;

    public IssueSseService(ObjectMapper objectMapper, ObjectProvider<IssueSseService> self) {
        this.objectMapper = objectMapper;
        this.self = self;
    }

    public SseEmitter subscribe(UUID projectId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(projectId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> removeEmitter(projectId, emitter));
        emitter.onTimeout(() -> { removeEmitter(projectId, emitter); emitter.complete(); });
        emitter.onError(e -> removeEmitter(projectId, emitter));

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            removeEmitter(projectId, emitter);
        }

        log.debug("SSE subscriber added projectId={} total={}", projectId, list.size());
        return emitter;
    }

    /**
     * Broadcasts an {@code issue-updated} event to every subscriber of the issue's
     * project so open boards refresh without a manual reload.
     *
     * <p>The {@link IssueDto} is built <em>synchronously on the caller's thread</em> —
     * i.e. while the request's persistence context is still open — so lazy
     * associations ({@code project}, {@code assignee}, {@code labels}) initialize
     * safely. Serializing off-thread previously threw {@code LazyInitializationException}
     * (silently swallowed by the async executor), which dropped the event entirely
     * for status-only updates and left the Kanban board stale.
     *
     * <p>When a transaction is active the push is deferred until after commit, so a
     * subscriber's refetch cannot race ahead of the committed data.
     */
    public void broadcastUpdate(Issue issue) {
        UUID projectId = issue.getProject().getId();
        List<SseEmitter> projectEmitters = emitters.get(projectId);
        if (projectEmitters == null || projectEmitters.isEmpty()) return;

        IssueDto dto = IssueDto.from(issue);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatch(projectId, dto);
                }
            });
        } else {
            dispatch(projectId, dto);
        }
    }

    /**
     * Hands the broadcast off to the {@code @Async} executor via the bean's own
     * proxy. The dispatch is guarded because it runs inside {@code afterCommit}
     * (on the request thread, after the DB has already committed): a rejected or
     * misconfigured executor would otherwise let a {@code TaskRejectedException}
     * propagate and fail the client's request even though the update succeeded.
     * A dropped live refresh is non-fatal — clients recover on their next fetch.
     */
    private void dispatch(UUID projectId, IssueDto dto) {
        try {
            self.getObject().sendToSubscribers(projectId, dto);
        } catch (Exception e) {
            log.warn("Failed to dispatch issue-updated event projectId={} issueId={}", projectId, dto.id(), e);
        }
    }

    @Async
    public void sendToSubscribers(UUID projectId, IssueDto dto) {
        List<SseEmitter> projectEmitters = emitters.get(projectId);
        if (projectEmitters == null || projectEmitters.isEmpty()) return;

        String payload;
        try {
            payload = objectMapper.writeValueAsString(dto);
        } catch (IOException e) {
            log.warn("Failed to serialize issue-updated event issueId={}", dto.id(), e);
            return;
        }

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : projectEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("issue-updated")
                        .data(payload));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }

        if (!dead.isEmpty()) {
            projectEmitters.removeAll(dead);
        }
        log.debug("Broadcast issue-updated issueId={} to {} subscribers", dto.id(), projectEmitters.size());
    }

    private void removeEmitter(UUID projectId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(projectId);
        if (list != null) {
            list.remove(emitter);
            log.debug("SSE subscriber removed projectId={} remaining={}", projectId, list.size());
        }
    }
}
