package com.mesha.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record AvailableRepositoryDto(
        Long id,
        String name,
        String fullName,
        String owner,
        Boolean isPrivate,
        String defaultBranch,
        String description,
        String htmlUrl
) {
    public static AvailableRepositoryDto from(JsonNode node) {
        return new AvailableRepositoryDto(
                node.path("id").asLong(),
                node.path("name").asText(),
                node.path("full_name").asText(),
                node.path("owner").path("login").asText(),
                node.path("private").asBoolean(false),
                node.path("default_branch").asText("main"),
                node.path("description").isNull() ? null : node.path("description").asText(null),
                node.path("html_url").asText()
        );
    }
}
