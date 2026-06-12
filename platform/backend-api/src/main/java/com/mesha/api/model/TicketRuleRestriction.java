package com.mesha.api.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "ticket_rule_restrictions",
       indexes = @Index(name = "idx_ticket_rule_restrictions_rule_id", columnList = "rule_id"))
public class TicketRuleRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "rule_id", nullable = false)
    private TicketRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "restriction_type", nullable = false, length = 50)
    private TicketRuleRestrictionType restrictionType;

    @Column(name = "restriction_value", length = 255)
    private String restrictionValue;

    @Column(nullable = false)
    private Integer position = 0;

    public UUID getId() { return id; }
    public TicketRule getRule() { return rule; }
    public void setRule(TicketRule rule) { this.rule = rule; }
    public TicketRuleRestrictionType getRestrictionType() { return restrictionType; }
    public void setRestrictionType(TicketRuleRestrictionType restrictionType) { this.restrictionType = restrictionType; }
    public String getRestrictionValue() { return restrictionValue; }
    public void setRestrictionValue(String restrictionValue) { this.restrictionValue = restrictionValue; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
