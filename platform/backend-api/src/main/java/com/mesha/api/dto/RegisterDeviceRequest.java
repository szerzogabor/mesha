package com.mesha.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for registering (or refreshing) the current user's push-notification
 * device token.
 *
 * @param fcmToken the Firebase Cloud Messaging registration token for this device
 * @param platform optional device platform label (defaults to {@code ANDROID})
 */
public record RegisterDeviceRequest(
    @NotBlank String fcmToken,
    String platform
) {}
