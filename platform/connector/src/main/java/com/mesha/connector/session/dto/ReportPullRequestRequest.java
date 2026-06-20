package com.mesha.connector.session.dto;

/** Mirrors backend-api's {@code ReportPullRequestRequest}. */
public record ReportPullRequestRequest(
        String githubUrl,
        String title,
        Integer number
) {}
