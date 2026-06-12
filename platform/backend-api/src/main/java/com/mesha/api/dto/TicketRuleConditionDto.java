package com.mesha.api.dto;

import com.mesha.api.model.TicketRuleCondition;
import com.mesha.api.model.TicketRuleConditionType;

import java.util.UUID;

public record TicketRuleConditionDto(
    UUID id,
    TicketRuleConditionType conditionType,
    String conditionValue,
    int position
) {
    public static TicketRuleConditionDto from(TicketRuleCondition c) {
        return new TicketRuleConditionDto(c.getId(), c.getConditionType(), c.getConditionValue(), c.getPosition());
    }
}
