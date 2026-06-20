package com.mesha.api.dto;

import com.mesha.api.model.AgentProviderType;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateAgentDefinitionRequest(
    @Size(max = 150) String title,
    @Size(max = 100) @Pattern(regexp = "^[a-z][a-z0-9]*(-[a-z0-9]+)*$", message = "Name must be lowercase kebab-case") String name,
    String description,
    AgentProviderType providerType,
    String systemPrompt,
    Map<String, Object> providerParameters,
    @Size(max = 100) @Pattern(regexp = "^(claude|codex|gemini|opencode|cursor|kimi|sisyphus)?$", message = "Must be a valid Blocks agent name: claude, codex, gemini, opencode, cursor, kimi, or sisyphus") String blocksAgentName,
    Boolean active
) {}
