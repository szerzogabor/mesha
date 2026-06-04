package com.mesha.api.github;

public final class GitHubInstallationStatus {

    public static final String ACTIVE = "active";
    public static final String SUSPENDED = "suspended";
    public static final String UNINSTALLED = "uninstalled";
    /** @deprecated legacy webhook status; treated as {@link #UNINSTALLED} */
    public static final String DELETED = "deleted";

    private GitHubInstallationStatus() {}

    public static boolean isActive(String status) {
        return ACTIVE.equals(status);
    }

    public static boolean isUninstalled(String status) {
        return UNINSTALLED.equals(status) || DELETED.equals(status);
    }
}
