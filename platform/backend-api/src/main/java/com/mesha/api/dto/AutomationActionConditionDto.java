package com.mesha.api.dto;

import com.mesha.api.model.AutomationRuleActionCondition;
import com.mesha.api.model.TicketRuleConditionType;

public record AutomationActionConditionDto(
    TicketRuleConditionType conditionType,
    String conditionValue
) {
    public static AutomationActionConditionDto from(AutomationRuleActionCondition c) {
        return new AutomationActionConditionDto(c.getConditionType(), c.getConditionValue());
    }
}
