package com.mesha.api.service;

import com.mesha.api.model.*;
import com.mesha.api.repository.ActivityEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);

    private final ActivityEventRepository activityEventRepository;

    public ActivityService(ActivityEventRepository activityEventRepository) {
        this.activityEventRepository = activityEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Issue issue, User user, ActivityEventType type, String oldValue, String newValue) {
        log.debug("Recording activity issueId={} eventType={} userId={}", issue.getId(), type, user.getId());
        ActivityEvent event = new ActivityEvent();
        event.setIssue(issue);
        event.setUser(user);
        event.setEventType(type);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        activityEventRepository.save(event);
    }

    public List<ActivityEvent> getForIssue(UUID issueId) {
        log.debug("Fetching activity for issueId={}", issueId);
        List<ActivityEvent> events = activityEventRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
        log.debug("Fetched activity issueId={} count={}", issueId, events.size());
        return events;
    }
}
