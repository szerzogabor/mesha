package com.mesha.api.dto;

import com.mesha.api.model.Label;
import java.util.UUID;

public record LabelDto(UUID id, UUID workspaceId, String name, String color) {
    public static LabelDto from(Label l) {
        return new LabelDto(l.getId(), l.getWorkspace().getId(), l.getName(), l.getColor());
    }
}
