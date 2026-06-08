package com.mesha.api.worker.orchestration;

public record SessionRequest(
        String issueId,
        String issueTitle,
        String issueDescription,
        String repositoryContext,
        String apiKey
) {}
