package com.mesha.api.dto;

import com.mesha.api.model.TicketRuleRestriction;
import com.mesha.api.model.TicketRuleRestrictionType;

import java.util.UUID;

public record TicketRuleRestrictionDto(
    UUID id,
    TicketRuleRestrictionType restrictionType,
    String restrictionValue,
    int position
) {
    public static TicketRuleRestrictionDto from(TicketRuleRestriction r) {
        return new TicketRuleRestrictionDto(r.getId(), r.getRestrictionType(), r.getRestrictionValue(), r.getPosition());
    }
}
