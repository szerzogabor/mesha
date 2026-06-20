package com.mesha.api.dto;

import com.mesha.api.model.AutomationActionType;
import com.mesha.api.model.AutomationRuleAction;

import java.util.List;

public record AutomationActionDto(
    AutomationActionType actionType,
    String actionValue,
    List<AutomationActionConditionDto> conditions
) {
    public static AutomationActionDto from(AutomationRuleAction a) {
        List<AutomationActionConditionDto> conditionDtos = a.getConditions().stream()
            .map(AutomationActionConditionDto::from)
            .toList();
        return new AutomationActionDto(a.getActionType(), a.getActionValue(), conditionDtos);
    }
}
