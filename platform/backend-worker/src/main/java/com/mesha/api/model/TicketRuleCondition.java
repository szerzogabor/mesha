package com.mesha.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "ticket_rule_conditions",
       indexes = @Index(name = "idx_ticket_rule_conditions_rule_id", columnList = "rule_id"))
public class TicketRuleCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private TicketRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 50)
    private TicketRuleConditionType conditionType;

    @Column(name = "condition_value", length = 255)
    private String conditionValue;

    @Column(nullable = false)
    private Integer position = 0;

    public UUID getId() { return id; }
    public TicketRule getRule() { return rule; }
    public void setRule(TicketRule rule) { this.rule = rule; }
    public TicketRuleConditionType getConditionType() { return conditionType; }
    public void setConditionType(TicketRuleConditionType conditionType) { this.conditionType = conditionType; }
    public String getConditionValue() { return conditionValue; }
    public void setConditionValue(String conditionValue) { this.conditionValue = conditionValue; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
