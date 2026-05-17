package com.mesha.api.service;

import com.mesha.api.model.*;
import com.mesha.api.repository.ActivityEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class ActivityService {

    private final ActivityEventRepository activityEventRepository;

    public ActivityService(ActivityEventRepository activityEventRepository) {
        this.activityEventRepository = activityEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void record(Issue issue, User user, ActivityEventType type, String oldValue, String newValue) {
        ActivityEvent event = new ActivityEvent();
        event.setIssue(issue);
        event.setUser(user);
        event.setEventType(type);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        activityEventRepository.save(event);
    }

    public List<ActivityEvent> getForIssue(UUID issueId) {
        return activityEventRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
    }
}
