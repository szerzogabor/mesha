package com.mesha.api.dto;

import com.mesha.api.model.AutomationActionType;
import com.mesha.api.model.AutomationRuleAction;

public record AutomationActionDto(
    AutomationActionType actionType,
    String actionValue
) {
    public static AutomationActionDto from(AutomationRuleAction a) {
        return new AutomationActionDto(a.getActionType(), a.getActionValue());
    }
}
