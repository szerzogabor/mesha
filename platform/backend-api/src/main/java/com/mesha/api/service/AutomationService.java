package com.mesha.api.service;

import com.mesha.api.dto.CreateAutomationRuleRequest;
import com.mesha.api.dto.UpdateAutomationRuleRequest;
import com.mesha.api.model.ActivityEventType;
import com.mesha.api.model.AutomationActionType;
import com.mesha.api.model.AutomationRule;
import com.mesha.api.model.AutomationTriggerType;
import com.mesha.api.model.Issue;
import com.mesha.api.model.Label;
import com.mesha.api.model.Project;
import com.mesha.api.model.User;
import com.mesha.api.repository.AutomationRuleRepository;
import com.mesha.api.repository.IssueRepository;
import com.mesha.api.repository.LabelRepository;
import com.mesha.api.repository.ProjectRepository;
import com.mesha.api.repository.ProjectStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AutomationService {

    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);

    private final AutomationRuleRepository ruleRepository;
    private final ProjectRepository projectRepository;
    private final ProjectStatusRepository projectStatusRepository;
    private final LabelRepository labelRepository;
    private final IssueRepository issueRepository;
    private final ActivityService activityService;

    public AutomationService(AutomationRuleRepository ruleRepository,
                             ProjectRepository projectRepository,
                             ProjectStatusRepository projectStatusRepository,
                             LabelRepository labelRepository,
                             IssueRepository issueRepository,
                             ActivityService activityService) {
        this.ruleRepository = ruleRepository;
        this.projectRepository = projectRepository;
        this.projectStatusRepository = projectStatusRepository;
        this.labelRepository = labelRepository;
        this.issueRepository = issueRepository;
        this.activityService = activityService;
    }

    public List<AutomationRule> list(UUID projectId) {
        return ruleRepository.findAllByProjectIdOrderByCreatedAtAsc(projectId);
    }

    @Transactional
    public AutomationRule create(UUID projectId, CreateAutomationRuleRequest req, User actor) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        validateActionValue(project, req.actionType(), req.actionValue());

        AutomationRule rule = new AutomationRule();
        rule.setProject(project);
        rule.setTriggerType(req.triggerType());
        rule.setActionType(req.actionType());
        rule.setActionValue(req.actionValue().trim());
        rule.setCreatedBy(actor);
        AutomationRule saved = ruleRepository.save(rule);
        log.info("Created automation rule ruleId={} projectId={} trigger={} action={} value={}",
                saved.getId(), projectId, req.triggerType(), req.actionType(), req.actionValue());
        return saved;
    }

    @Transactional
    public AutomationRule update(UUID projectId, UUID ruleId, UpdateAutomationRuleRequest req) {
        AutomationRule rule = getById(projectId, ruleId);

        if (req.triggerType() != null) {
            rule.setTriggerType(req.triggerType());
        }
        AutomationActionType actionType = req.actionType() != null ? req.actionType() : rule.getActionType();
        String actionValue = req.actionValue() != null ? req.actionValue().trim() : rule.getActionValue();
        if (req.actionType() != null || req.actionValue() != null) {
            validateActionValue(rule.getProject(), actionType, actionValue);
            rule.setActionType(actionType);
            rule.setActionValue(actionValue);
        }
        if (req.enabled() != null) {
            rule.setEnabled(req.enabled());
        }

        AutomationRule saved = ruleRepository.save(rule);
        log.info("Updated automation rule ruleId={} projectId={} enabled={}", ruleId, projectId, saved.isEnabled());
        return saved;
    }

    @Transactional
    public void delete(UUID projectId, UUID ruleId) {
        AutomationRule rule = getById(projectId, ruleId);
        ruleRepository.delete(rule);
        log.info("Deleted automation rule ruleId={} projectId={}", ruleId, projectId);
    }

    /**
     * Executes all enabled rules matching the trigger for the issue's project.
     * Never throws: an automation failure must not break the webhook/polling flow that fired it.
     */
    @Transactional
    public void executeFor(AutomationTriggerType trigger, Issue issue) {
        if (issue == null) {
            return;
        }
        UUID projectId = issue.getProject().getId();
        List<AutomationRule> rules;
        try {
            rules = ruleRepository.findAllByProjectIdAndTriggerTypeAndEnabledTrue(projectId, trigger);
        } catch (Exception e) {
            log.warn("automation_rule_lookup_failed projectId={} trigger={} error={}", projectId, trigger, e.getMessage());
            return;
        }
        for (AutomationRule rule : rules) {
            try {
                apply(rule, issue);
            } catch (Exception e) {
                log.warn("automation_rule_failed ruleId={} issueId={} trigger={} error={}",
                        rule.getId(), issue.getId(), trigger, e.getMessage());
            }
        }
    }

    private void apply(AutomationRule rule, Issue issue) {
        switch (rule.getActionType()) {
            case SET_STATUS -> applySetStatus(rule, issue);
            case ADD_LABEL -> applyAddLabel(rule, issue);
        }
    }

    private void applySetStatus(AutomationRule rule, Issue issue) {
        String newStatus = rule.getActionValue();
        UUID projectId = issue.getProject().getId();
        if (!projectStatusRepository.existsByProjectIdAndName(projectId, newStatus)) {
            log.warn("automation_status_missing ruleId={} projectId={} status={}", rule.getId(), projectId, newStatus);
            return;
        }
        String oldStatus = issue.getStatus();
        if (newStatus.equals(oldStatus)) {
            return;
        }
        issue.setStatus(newStatus);
        issueRepository.save(issue);
        activityService.record(issue, null, ActivityEventType.STATUS_CHANGED, oldStatus, newStatus);
        log.info("automation_status_applied ruleId={} issueId={} from={} to={}",
                rule.getId(), issue.getId(), oldStatus, newStatus);
    }

    private void applyAddLabel(AutomationRule rule, Issue issue) {
        UUID labelId;
        try {
            labelId = UUID.fromString(rule.getActionValue());
        } catch (IllegalArgumentException e) {
            log.warn("automation_label_value_invalid ruleId={} value={}", rule.getId(), rule.getActionValue());
            return;
        }
        boolean alreadyPresent = issue.getLabels().stream().anyMatch(l -> l.getId().equals(labelId));
        if (alreadyPresent) {
            return;
        }
        Optional<Label> label = labelRepository.findById(labelId);
        if (label.isEmpty()) {
            log.warn("automation_label_missing ruleId={} labelId={}", rule.getId(), labelId);
            return;
        }
        issue.getLabels().add(label.get());
        issueRepository.save(issue);
        activityService.record(issue, null, ActivityEventType.LABEL_ADDED, null, label.get().getName());
        log.info("automation_label_applied ruleId={} issueId={} labelId={}", rule.getId(), issue.getId(), labelId);
    }

    private void validateActionValue(Project project, AutomationActionType actionType, String actionValue) {
        if (actionValue == null || actionValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action value is required");
        }
        switch (actionType) {
            case SET_STATUS -> {
                if (!projectStatusRepository.existsByProjectIdAndName(project.getId(), actionValue.trim())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown status for this project: " + actionValue);
                }
            }
            case ADD_LABEL -> {
                UUID labelId;
                try {
                    labelId = UUID.fromString(actionValue.trim());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action value must be a label id");
                }
                UUID workspaceId = project.getWorkspace().getId();
                boolean inWorkspace = labelRepository.findById(labelId)
                    .map(l -> l.getWorkspace().getId().equals(workspaceId))
                    .orElse(false);
                if (!inWorkspace) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown label for this workspace: " + actionValue);
                }
            }
        }
    }

    private AutomationRule getById(UUID projectId, UUID ruleId) {
        return ruleRepository.findById(ruleId)
            .filter(r -> r.getProject().getId().equals(projectId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Automation rule not found"));
    }
}
