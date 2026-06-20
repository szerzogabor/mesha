package com.mesha.connector.git;

/** Mirrors the repo-wide branch naming convention from the root CLAUDE.md: {@code feature/<ticket-id>}. */
public final class BranchNamingStrategy {

    private BranchNamingStrategy() {}

    public static String branchFor(String issueIdentifier) {
        if (issueIdentifier == null || issueIdentifier.isBlank()) {
            throw new IllegalArgumentException("Cannot derive a branch name without an issue identifier");
        }
        return "feature/" + issueIdentifier.trim();
    }
}
