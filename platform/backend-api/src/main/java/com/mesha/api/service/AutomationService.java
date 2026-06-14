package com.mesha.api.service;

import com.mesha.api.dto.AutomationActionRequest;
import com.mesha.api.dto.CreateAutomationRuleRequest;
import com.mesha.api.dto.UpdateAutomationRuleRequest;
import com.mesha.api.model.ActivityEventType;
import com.mesha.api.model.AutomationRule;
import com.mesha.api.model.AutomationRuleAction;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final IssueSseService issueSseService;
    private final TransactionTemplate ruleTransactionTemplate;
    private BlocksSessionService blocksSessionService;

    public AutomationService(AutomationRuleRepository ruleRepository,
                             ProjectRepository projectRepository,
                             ProjectStatusRepository projectStatusRepository,
                             LabelRepository labelRepository,
                             IssueRepository issueRepository,
                             ActivityService activityService,
                             IssueSseService issueSseService,
                             PlatformTransactionManager transactionManager) {
        this.ruleRepository = ruleRepository;
        this.projectRepository = projectRepository;
        this.projectStatusRepository = projectStatusRepository;
        this.labelRepository = labelRepository;
        this.issueRepository = issueRepository;
        this.activityService = activityService;
        this.issueSseService = issueSseService;
        this.ruleTransactionTemplate = new TransactionTemplate(transactionManager);
        this.ruleTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Autowired
    public void setBlocksSessionService(@Lazy BlocksSessionService blocksSessionService) {
        this.blocksSessionService = blocksSessionService;
    }

    public List<AutomationRule> list(UUID projectId) {
        return ruleRepository.findAllByProjectIdWithActions(projectId);
    }

    @Transactional
    public AutomationRule create(UUID projectId, CreateAutomationRuleRequest req, User actor) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        validateTriggerValue(project, req.triggerType(), req.triggerValue());
        for (AutomationActionRequest action : req.actions()) {
            validateActionValue(project, action);
        }

        AutomationRule rule = new AutomationRule();
        rule.setProject(project);
        rule.setTriggerType(req.triggerType());
        rule.setTriggerValue(normalizeTriggerValue(req.triggerType(), req.triggerValue()));
        rule.setCreatedBy(actor);
        applyActions(rule, req.actions());
        AutomationRule saved = ruleRepository.save(rule);
        log.info("Created automation rule ruleId={} projectId={} trigger={} triggerValue={} actionCount={}",
                saved.getId(), projectId, req.triggerType(), saved.getTriggerValue(), req.actions().size());
        return saved;
    }

    @Transactional
    public AutomationRule update(UUID projectId, UUID ruleId, UpdateAutomationRuleRequest req) {
        AutomationRule rule = getById(projectId, ruleId);

        AutomationTriggerType effectiveTriggerType = req.triggerType() != null ? req.triggerType() : rule.getTriggerType();
        if (req.triggerType() != null || req.triggerValue() != null) {
            validateTriggerValue(rule.getProject(), effectiveTriggerType, req.triggerValue());
            if (req.triggerType() != null) {
                rule.setTriggerType(req.triggerType());
            }
            if (req.triggerValue() != null) {
                rule.setTriggerValue(normalizeTriggerValue(effectiveTriggerType, req.triggerValue()));
            }
        }
        if (req.actions() != null) {
            if (req.actions().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rule must have at least one action");
            }
            for (AutomationActionRequest action : req.actions()) {
                validateActionValue(rule.getProject(), action);
            }
            applyActions(rule, req.actions());
        }
        if (req.enabled() != null) {
            rule.setEnabled(req.enabled());
        }

        AutomationRule saved = ruleRepository.save(rule);
        log.info("Updated automation rule ruleId={} projectId={} enabled={} actionCount={}",
                ruleId, projectId, saved.isEnabled(), saved.getActions().size());
        return saved;
    }

    @Transactional
    public void delete(UUID projectId, UUID ruleId) {
        AutomationRule rule = getById(projectId, ruleId);
        ruleRepository.delete(rule);
        log.info("Deleted automation rule ruleId={} projectId={}", ruleId, projectId);
    }

    private void applyActions(AutomationRule rule, List<AutomationActionRequest> actions) {
        rule.getActions().clear();
        for (int i = 0; i < actions.size(); i++) {
            AutomationActionRequest req = actions.get(i);
            AutomationRuleAction action = new AutomationRuleAction();
            action.setRule(rule);
            action.setActionType(req.actionType());
            action.setActionValue(req.actionValue() != null ? req.actionValue().trim() : null);
            action.setPosition(i);
            rule.getActions().add(action);
        }
    }

    /**
     * Executes all enabled rules matching the trigger for the issue's project.
     * Never throws: an automation failure must not break the webhook/polling flow that fired it.
     *
     * When called inside an active transaction, execution is deferred until that transaction
     * commits — a rule failure could otherwise mark the shared transaction rollback-only, and
     * updating the issue row before the caller releases its lock could self-deadlock. Each rule
     * then runs in its own transaction so one failing rule cannot roll back the others.
     */
    public void executeFor(AutomationTriggerType trigger, Issue issue) {
        executeFor(trigger, issue, null);
    }

    /**
     * Executes rules for parameterized triggers (STATUS_UPDATED, LABEL_ADDED).
     * matchValue is compared against each rule's triggerValue; rules with no triggerValue always match.
     */
    public void executeFor(AutomationTriggerType trigger, Issue issue, String matchValue) {
        if (issue == null) {
            return;
        }
        UUID issueId = issue.getId();
        UUID projectId = issue.getProject().getId();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runRules(trigger, projectId, issueId, matchValue);
                }
            });
        } else {
            runRules(trigger, projectId, issueId, matchValue);
        }
    }

    private void runRules(AutomationTriggerType trigger, UUID projectId, UUID issueId, String matchValue) {
        List<AutomationRule> rules;
        try {
            if (matchValue != null) {
                rules = ruleRepository.findEnabledByProjectIdAndTriggerTypeAndValueWithActions(projectId, trigger, matchValue);
            } else {
                rules = ruleRepository.findEnabledByProjectIdAndTriggerTypeWithActions(projectId, trigger);
            }
        } catch (Exception e) {
            log.warn("automation_rule_lookup_failed projectId={} trigger={} error={}", projectId, trigger, e.getMessage());
            return;
        }
        for (AutomationRule rule : rules) {
            try {
                ruleTransactionTemplate.executeWithoutResult(tx -> apply(rule, projectId, issueId));
            } catch (Exception e) {
                log.warn("automation_rule_failed ruleId={} issueId={} trigger={} error={}",
                        rule.getId(), issueId, trigger, e.getMessage());
            }
        }
    }

    private void apply(AutomationRule rule, UUID projectId, UUID issueId) {
        Issue issue = issueRepository.findById(issueId).orElse(null);
        if (issue == null) {
            log.warn("automation_issue_missing ruleId={} issueId={}", rule.getId(), issueId);
            return;
        }
        for (AutomationRuleAction action : rule.getActions()) {
            switch (action.getActionType()) {
                case SET_STATUS -> applySetStatus(rule, action, projectId, issue);
                case ADD_LABEL -> applyAddLabel(rule, action, issue);
                case START_AI_SESSION -> applyStartAiSession(rule, issue);
            }
        }
    }

    private void applySetStatus(AutomationRule rule, AutomationRuleAction action, UUID projectId, Issue issue) {
        String newStatus = action.getActionValue();
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
        issueSseService.broadcastUpdate(issue);
        log.info("automation_status_applied ruleId={} issueId={} from={} to={}",
                rule.getId(), issue.getId(), oldStatus, newStatus);
    }

    private void applyAddLabel(AutomationRule rule, AutomationRuleAction action, Issue issue) {
        UUID labelId;
        try {
            labelId = UUID.fromString(action.getActionValue());
        } catch (IllegalArgumentException e) {
            log.warn("automation_label_value_invalid ruleId={} value={}", rule.getId(), action.getActionValue());
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

    private void applyStartAiSession(AutomationRule rule, Issue issue) {
        try {
            blocksSessionService.assignToBlocks(issue.getId(), null, null);
            log.info("automation_ai_session_started ruleId={} issueId={}", rule.getId(), issue.getId());
        } catch (Exception e) {
            log.warn("automation_ai_session_failed ruleId={} issueId={} error={}", rule.getId(), issue.getId(), e.getMessage());
        }
    }

    private void validateTriggerValue(Project project, AutomationTriggerType triggerType, String triggerValue) {
        switch (triggerType) {
            case STATUS_UPDATED -> {
                if (triggerValue == null || triggerValue.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "triggerValue (target status) is required for STATUS_UPDATED trigger");
                }
                String normalized = triggerValue.trim().toUpperCase();
                if (!projectStatusRepository.existsByProjectIdAndName(project.getId(), normalized)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown status for this project: " + triggerValue);
                }
            }
            case LABEL_ADDED -> {
                if (triggerValue == null || triggerValue.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "triggerValue (label id) is required for LABEL_ADDED trigger");
                }
                UUID labelId;
                try {
                    labelId = UUID.fromString(triggerValue.trim());
                } catch (IllegalArgumentException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "triggerValue must be a label id");
                }
                UUID workspaceId = project.getWorkspace().getId();
                boolean inWorkspace = labelRepository.findById(labelId)
                        .map(l -> l.getWorkspace().getId().equals(workspaceId))
                        .orElse(false);
                if (!inWorkspace) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown label for this workspace: " + triggerValue);
                }
            }
            default -> {
                // Non-parameterized triggers do not require a triggerValue
            }
        }
    }

    private String normalizeTriggerValue(AutomationTriggerType triggerType, String triggerValue) {
        if (triggerValue == null) return null;
        return switch (triggerType) {
            case STATUS_UPDATED -> triggerValue.trim().toUpperCase();
            default -> triggerValue.trim();
        };
    }

    private void validateActionValue(Project project, AutomationActionRequest action) {
        String actionValue = action.actionValue();
        switch (action.actionType()) {
            case START_AI_SESSION -> {
                // No value needed for this action
            }
            case SET_STATUS -> {
                if (actionValue == null || actionValue.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action value is required");
                }
                if (!projectStatusRepository.existsByProjectIdAndName(project.getId(), actionValue.trim())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown status for this project: " + actionValue);
                }
            }
            case ADD_LABEL -> {
                if (actionValue == null || actionValue.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action value is required");
                }
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
