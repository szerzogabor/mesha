package com.mesha.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "automation_rule_action_conditions",
       indexes = @Index(name = "idx_automation_rule_action_conditions_action_id", columnList = "action_id"))
public class AutomationRuleActionCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "action_id", nullable = false)
    private AutomationRuleAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 50)
    private TicketRuleConditionType conditionType;

    @Column(name = "condition_value", length = 255)
    private String conditionValue;

    @Column(nullable = false)
    private Integer position = 0;

    public UUID getId() { return id; }
    public AutomationRuleAction getAction() { return action; }
    public void setAction(AutomationRuleAction action) { this.action = action; }
    public TicketRuleConditionType getConditionType() { return conditionType; }
    public void setConditionType(TicketRuleConditionType conditionType) { this.conditionType = conditionType; }
    public String getConditionValue() { return conditionValue; }
    public void setConditionValue(String conditionValue) { this.conditionValue = conditionValue; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
