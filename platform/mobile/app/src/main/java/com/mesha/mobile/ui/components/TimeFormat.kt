package com.mesha.mobile.ui.components

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** Parse a backend ISO-8601 timestamp, tolerating offset/local variants. */
private fun parseInstant(iso: String?): Instant? {
    if (iso.isNullOrBlank()) return null
    return runCatching { Instant.parse(iso) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC) }.getOrNull()
}

/**
 * Compact "time ago" label mirroring the web app's `formatRelativeTime`
 * (just now / Nm / Nh / Nd ago). Returns null when [iso] can't be parsed.
 */
fun formatRelativeTime(iso: String?): String? {
    val instant = parseInstant(iso) ?: return null
    val minutes = (System.currentTimeMillis() - instant.toEpochMilli()) / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}
