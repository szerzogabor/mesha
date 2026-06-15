package com.mesha.api.worker.orchestration;

import java.util.List;

public record SessionRequest(
        String issueId,
        String issueIdentifier,
        String issueTitle,
        String issueDescription,
        String issueStatus,
        String issuePriority,
        String issueAssigneeName,
        List<String> issueLabels,
        String issueCreatedAt,
        String issueUpdatedAt,
        String workspaceName,
        String projectName,
        String repositoryName,
        String repositoryUrl,
        String repositoryDefaultBranch,
        List<String> comments,
        String apiKey,
        String instructions,
        String agentLlm,
        String agentSystemPrompt,
        List<String> agentStartupCommands
) {}
