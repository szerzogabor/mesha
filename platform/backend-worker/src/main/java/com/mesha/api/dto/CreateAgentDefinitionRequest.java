package com.mesha.api.dto;

import com.mesha.api.model.AgentProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateAgentDefinitionRequest(
    @NotBlank @Size(max = 150) String title,
    @NotBlank @Size(max = 100) @Pattern(regexp = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$", message = "Name must be lowercase kebab-case") String name,
    String description,
    @NotNull AgentProviderType providerType,
    @NotBlank String systemPrompt,
    Map<String, Object> providerParameters,
    @Size(max = 100) @Pattern(regexp = "^(claude|codex|gemini|opencode|cursor|kimi)?$", message = "Must be a valid Blocks agent name: claude, codex, gemini, opencode, cursor, or kimi") String blocksAgentName,
    Boolean active
) {}
