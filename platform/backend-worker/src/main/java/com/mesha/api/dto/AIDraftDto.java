package com.mesha.api.dto;

import com.mesha.api.model.AIDraft;
import com.mesha.api.model.AIDraftStatus;

import java.time.Instant;
import java.util.UUID;

public record AIDraftDto(
    UUID id,
    UUID projectId,
    String prompt,
    AIDraftStatus status,
    String generatedTitle,
    String generatedDescription,
    String acceptanceCriteria,
    String suggestedLabels,
    String prioritySuggestion,
    String implementationNotes,
    String scopeNotes,
    String outOfScopeNotes,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {
    public static AIDraftDto from(AIDraft d) {
        return new AIDraftDto(
            d.getId(),
            d.getProject().getId(),
            d.getPrompt(),
            d.getStatus(),
            d.getGeneratedTitle(),
            d.getGeneratedDescription(),
            d.getAcceptanceCriteria(),
            d.getSuggestedLabels(),
            d.getPrioritySuggestion(),
            d.getImplementationNotes(),
            d.getScopeNotes(),
            d.getOutOfScopeNotes(),
            d.getErrorMessage(),
            d.getCreatedAt(),
            d.getUpdatedAt()
        );
    }
}
