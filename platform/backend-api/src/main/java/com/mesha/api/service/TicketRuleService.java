package com.mesha.api.service;

import com.mesha.api.dto.CreateTicketRuleRequest;
import com.mesha.api.dto.TicketRuleConditionRequest;
import com.mesha.api.dto.TicketRuleRestrictionRequest;
import com.mesha.api.dto.UpdateTicketRuleRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class TicketRuleService {

    private static final Logger log = LoggerFactory.getLogger(TicketRuleService.class);

    private final TicketRuleRepository ticketRuleRepository;
    private final ProjectRepository projectRepository;
    private final ProjectStatusRepository projectStatusRepository;
    private final LabelRepository labelRepository;
    private final IssueLinkRepository issueLinkRepository;

    public TicketRuleService(TicketRuleRepository ticketRuleRepository,
                             ProjectRepository projectRepository,
                             ProjectStatusRepository projectStatusRepository,
                             LabelRepository labelRepository,
                             IssueLinkRepository issueLinkRepository) {
        this.ticketRuleRepository = ticketRuleRepository;
        this.projectRepository = projectRepository;
        this.projectStatusRepository = projectStatusRepository;
        this.labelRepository = labelRepository;
        this.issueLinkRepository = issueLinkRepository;
    }

    @Transactional(readOnly = true)
    public List<TicketRule> list(UUID projectId) {
        List<TicketRule> rules = ticketRuleRepository.findAllByProjectIdWithConditions(projectId);
        ticketRuleRepository.findAllByProjectIdWithRestrictions(projectId);
        return rules;
    }

    @Transactional
    public TicketRule create(UUID projectId, CreateTicketRuleRequest req, User actor) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        validateConditions(project, req.conditions());
        validateRestrictions(project, req.restrictions());

        TicketRule rule = new TicketRule();
        rule.setProject(project);
        rule.setName(req.name().trim());
        rule.setCreatedBy(actor);
        applyConditions(rule, req.conditions());
        applyRestrictions(rule, req.restrictions());

        TicketRule saved = ticketRuleRepository.save(rule);
        log.info("Created ticket rule ruleId={} projectId={} name={} conditions={} restrictions={}",
                saved.getId(), projectId, saved.getName(),
                saved.getConditions().size(), saved.getRestrictions().size());
        return saved;
    }

    @Transactional
    public TicketRule update(UUID projectId, UUID ruleId, UpdateTicketRuleRequest req) {
        TicketRule rule = getById(projectId, ruleId);

        if (req.name() != null && !req.name().isBlank()) {
            rule.setName(req.name().trim());
        }
        if (req.enabled() != null) {
            rule.setEnabled(req.enabled());
        }
        if (req.conditions() != null) {
            if (req.conditions().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rule must have at least one condition");
            }
            validateConditions(rule.getProject(), req.conditions());
            applyConditions(rule, req.conditions());
        }
        if (req.restrictions() != null) {
            if (req.restrictions().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rule must have at least one restriction");
            }
            validateRestrictions(rule.getProject(), req.restrictions());
            applyRestrictions(rule, req.restrictions());
        }

        TicketRule saved = ticketRuleRepository.save(rule);
        log.info("Updated ticket rule ruleId={} projectId={} enabled={}", ruleId, projectId, saved.isEnabled());
        return saved;
    }

    @Transactional
    public void delete(UUID projectId, UUID ruleId) {
        TicketRule rule = getById(projectId, ruleId);
        ticketRuleRepository.delete(rule);
        log.info("Deleted ticket rule ruleId={} projectId={}", ruleId, projectId);
    }

    /**
     * Validates that the given issue can have an AI session started on it.
     * Checks configured ticket rules as well as dependency-based blocking.
     */
    @Transactional(readOnly = true)
    public void validateCanStartAiSession(Issue issue) {
        checkRulesForRestriction(issue, TicketRuleRestrictionType.CANNOT_START_AI_SESSION, null);
        checkDependencyBlocking(issue);
    }

    /**
     * Validates that the given issue can be moved to the target status.
     */
    @Transactional(readOnly = true)
    public void validateCanMoveToStatus(Issue issue, String targetStatus) {
        checkRulesForRestriction(issue, TicketRuleRestrictionType.CANNOT_MOVE_TO_STATUS, targetStatus);
    }

    private void checkRulesForRestriction(Issue issue, TicketRuleRestrictionType restrictionType, String restrictionValue) {
        UUID projectId = issue.getProject().getId();
        List<TicketRule> rules = ticketRuleRepository.findEnabledByProjectIdWithConditions(projectId);
        ticketRuleRepository.findEnabledByProjectIdWithRestrictions(projectId);
        for (TicketRule rule : rules) {
            if (conditionsMatch(rule, issue)) {
                for (TicketRuleRestriction restriction : rule.getRestrictions()) {
                    if (restriction.getRestrictionType() != restrictionType) {
                        continue;
                    }
                    if (restrictionType == TicketRuleRestrictionType.CANNOT_MOVE_TO_STATUS) {
                        String blocked = restriction.getRestrictionValue();
                        if (blocked != null && !blocked.equalsIgnoreCase(restrictionValue)) {
                            continue;
                        }
                    }
                    String message = buildViolationMessage(rule, restriction, restrictionValue);
                    log.info("ticket_rule_violation ruleId={} issueId={} restrictionType={} message={}",
                            rule.getId(), issue.getId(), restrictionType, message);
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
                }
            }
        }
    }

    /**
     * Blocks starting an AI session if any DEPENDS_ON dependency is not in DONE status.
     */
    private void checkDependencyBlocking(Issue issue) {
        List<IssueLink> links = issueLinkRepository.findAllByIssueId(issue.getId());
        for (IssueLink link : links) {
            if (link.getLinkType() == IssueLinkType.DEPENDS_ON
                    && link.getSourceIssue().getId().equals(issue.getId())) {
                Issue dependency = link.getTargetIssue();
                if (!"DONE".equalsIgnoreCase(dependency.getStatus())) {
                    String msg = String.format(
                        "Cannot start AI session: this ticket depends on '%s' which is not yet DONE (current status: %s)",
                        dependency.getTitle(), dependency.getStatus());
                    log.info("ticket_dependency_block issueId={} dependsOnId={} dependsOnStatus={}",
                            issue.getId(), dependency.getId(), dependency.getStatus());
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, msg);
                }
            }
        }
    }

    private boolean conditionsMatch(TicketRule rule, Issue issue) {
        for (TicketRuleCondition condition : rule.getConditions()) {
            if (!conditionMatches(condition, issue)) {
                return false;
            }
        }
        return true;
    }

    private boolean conditionMatches(TicketRuleCondition condition, Issue issue) {
        return switch (condition.getConditionType()) {
            case HAS_STATUS -> condition.getConditionValue().equalsIgnoreCase(issue.getStatus());
            case HAS_LABEL -> {
                UUID labelId;
                try {
                    labelId = UUID.fromString(condition.getConditionValue());
                } catch (IllegalArgumentException e) {
                    yield false;
                }
                yield issue.getLabels().stream().anyMatch(l -> l.getId().equals(labelId));
            }
            case ASSIGNED_TO_AGENT -> issue.getAiAssignmentState() != null && !issue.getAiAssignmentState().isBlank();
            case ASSIGNED_TO_HUMAN -> issue.getAssignee() != null;
        };
    }

    private String buildViolationMessage(TicketRule rule, TicketRuleRestriction restriction, String targetValue) {
        return switch (restriction.getRestrictionType()) {
            case CANNOT_START_AI_SESSION ->
                "Ticket rule '" + rule.getName() + "' prevents starting an AI session on this ticket";
            case CANNOT_MOVE_TO_STATUS ->
                "Ticket rule '" + rule.getName() + "' prevents moving this ticket to status '"
                    + (restriction.getRestrictionValue() != null ? restriction.getRestrictionValue() : targetValue) + "'";
        };
    }

    private void applyConditions(TicketRule rule, List<TicketRuleConditionRequest> requests) {
        rule.getConditions().clear();
        LinkedHashSet<TicketRuleCondition> newConditions = new LinkedHashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            TicketRuleConditionRequest req = requests.get(i);
            TicketRuleCondition condition = new TicketRuleCondition();
            condition.setRule(rule);
            condition.setConditionType(req.conditionType());
            condition.setConditionValue(req.conditionValue() != null ? req.conditionValue().trim() : null);
            condition.setPosition(i);
            newConditions.add(condition);
        }
        rule.getConditions().addAll(newConditions);
    }

    private void applyRestrictions(TicketRule rule, List<TicketRuleRestrictionRequest> requests) {
        rule.getRestrictions().clear();
        LinkedHashSet<TicketRuleRestriction> newRestrictions = new LinkedHashSet<>();
        for (int i = 0; i < requests.size(); i++) {
            TicketRuleRestrictionRequest req = requests.get(i);
            TicketRuleRestriction restriction = new TicketRuleRestriction();
            restriction.setRule(rule);
            restriction.setRestrictionType(req.restrictionType());
            restriction.setRestrictionValue(req.restrictionValue() != null ? req.restrictionValue().trim() : null);
            restriction.setPosition(i);
            newRestrictions.add(restriction);
        }
        rule.getRestrictions().addAll(newRestrictions);
    }

    private void validateConditions(Project project, List<TicketRuleConditionRequest> conditions) {
        for (TicketRuleConditionRequest condition : conditions) {
            TicketRuleConditionType type = condition.conditionType();
            if (type == TicketRuleConditionType.ASSIGNED_TO_AGENT || type == TicketRuleConditionType.ASSIGNED_TO_HUMAN) {
                continue;
            }
            String value = condition.conditionValue();
            if (value == null || value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Condition value is required");
            }
            switch (type) {
                case HAS_STATUS -> {
                    if (!projectStatusRepository.existsByProjectIdAndName(project.getId(), value.trim().toUpperCase())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown status for this project: " + value);
                    }
                }
                case HAS_LABEL -> {
                    UUID labelId;
                    try {
                        labelId = UUID.fromString(value.trim());
                    } catch (IllegalArgumentException e) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Condition value for HAS_LABEL must be a label UUID");
                    }
                    UUID workspaceId = project.getWorkspace().getId();
                    boolean inWorkspace = labelRepository.findById(labelId)
                        .map(l -> l.getWorkspace().getId().equals(workspaceId))
                        .orElse(false);
                    if (!inWorkspace) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Unknown label for this workspace: " + value);
                    }
                }
                default -> { }
            }
        }
    }

    private void validateRestrictions(Project project, List<TicketRuleRestrictionRequest> restrictions) {
        for (TicketRuleRestrictionRequest restriction : restrictions) {
            if (restriction.restrictionType() == TicketRuleRestrictionType.CANNOT_MOVE_TO_STATUS) {
                String value = restriction.restrictionValue();
                if (value == null || value.isBlank()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Restriction value is required for CANNOT_MOVE_TO_STATUS");
                }
                if (!projectStatusRepository.existsByProjectIdAndName(project.getId(), value.trim().toUpperCase())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown status for this project: " + value);
                }
            }
        }
    }

    private TicketRule getById(UUID projectId, UUID ruleId) {
        return ticketRuleRepository.findById(ruleId)
            .filter(r -> r.getProject().getId().equals(projectId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket rule not found"));
    }
}
