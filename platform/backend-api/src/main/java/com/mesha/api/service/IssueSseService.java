package com.mesha.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mesha.api.dto.IssueDto;
import com.mesha.api.model.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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

    public IssueSseService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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

    @Async
    public void broadcastUpdate(Issue issue) {
        UUID projectId = issue.getProject().getId();
        List<SseEmitter> projectEmitters = emitters.get(projectId);
        if (projectEmitters == null || projectEmitters.isEmpty()) return;

        IssueDto dto = IssueDto.from(issue);
        List<SseEmitter> dead = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : projectEmitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("issue-updated")
                        .data(objectMapper.writeValueAsString(dto)));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }

        if (!dead.isEmpty()) {
            projectEmitters.removeAll(dead);
        }
        log.debug("Broadcast issue-updated issueId={} to {} subscribers", issue.getId(), projectEmitters.size());
    }

    private void removeEmitter(UUID projectId, SseEmitter emitter) {
        emitters.computeIfPresent(projectId, (id, list) -> {
            list.remove(emitter);
            log.debug("SSE subscriber removed projectId={} remaining={}", id, list.size());
            return list.isEmpty() ? null : list;
        });
    }
}
