package com.mesha.mobile.ui.components

import androidx.compose.ui.graphics.Color

/**
 * Parse a `#RRGGBB` (or `#AARRGGBB`) hex string into a Compose [Color], falling back to
 * [fallback] for null/blank/malformed input. Backend labels and project statuses carry
 * their color as such a hex string.
 */
fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> fallback
        }
    } catch (_: NumberFormatException) {
        fallback
    }
}
