package com.mesha.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "automation_rule_actions",
       indexes = @Index(name = "idx_automation_rule_actions_rule_id", columnList = "rule_id"))
public class AutomationRuleAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private AutomationRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private AutomationActionType actionType;

    @Column(name = "action_value", nullable = false, length = 255)
    private String actionValue;

    @Column(nullable = false)
    private Integer position = 0;

    public UUID getId() { return id; }
    public AutomationRule getRule() { return rule; }
    public void setRule(AutomationRule rule) { this.rule = rule; }
    public AutomationActionType getActionType() { return actionType; }
    public void setActionType(AutomationActionType actionType) { this.actionType = actionType; }
    public String getActionValue() { return actionValue; }
    public void setActionValue(String actionValue) { this.actionValue = actionValue; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
