package com.mesha.api.ai;

public record AIDraftContent(
    String title,
    String description,
    String acceptanceCriteria,
    String suggestedLabels,
    String prioritySuggestion,
    String implementationNotes,
    String scopeNotes,
    String outOfScopeNotes
) {}
