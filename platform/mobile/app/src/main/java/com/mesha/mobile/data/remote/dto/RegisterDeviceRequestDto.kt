package com.mesha.mobile.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Body for `POST /api/devices` — registers this device's FCM registration token so the
 * backend can send it push notifications on ticket status changes.
 */
@Serializable
data class RegisterDeviceRequestDto(
    val fcmToken: String,
    val platform: String = "ANDROID",
)
