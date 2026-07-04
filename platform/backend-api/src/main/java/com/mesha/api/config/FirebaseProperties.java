package com.mesha.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firebase Cloud Messaging (FCM) service-account configuration used to send push
 * notifications. When {@link #credentialsJson()} is blank the push subsystem stays
 * disabled and simply no-ops, so local/dev environments run without Firebase.
 *
 * @param credentialsJson raw service-account JSON (the file Firebase generates under
 *                        Project Settings → Service accounts), injected via env var.
 */
@ConfigurationProperties(prefix = "firebase")
public record FirebaseProperties(String credentialsJson) {

    public boolean isConfigured() {
        return credentialsJson != null && !credentialsJson.isBlank();
    }
}
