package com.mesha.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens ported verbatim from the web app's `globals.css` (Linear-inspired
 * palette). Keeping the exact hex values here is what makes the native app read as the
 * same product as the PWA — do not "round" these to Material defaults.
 *
 * Light tokens are prefixed `L`, dark tokens `D`.
 */
internal object MeshaTokens {

    // ── Light theme ──────────────────────────────────────────────────────────
    val LBgApp = Color(0xFFF7F7F8)
    val LBgSurface = Color(0xFFFFFFFF)
    val LBgSurfaceHover = Color(0xFFF3F3F4)
    val LBgSurfaceRaised = Color(0xFFFFFFFF)
    val LOverlay = Color(0x66000000)

    val LBorder = Color(0xFFE5E5E7)
    val LBorderStrong = Color(0xFFD0D0D2)

    val LTextPrimary = Color(0xFF111116)
    val LTextSecondary = Color(0xFF6B6B78)
    val LTextTertiary = Color(0xFF9B9BA4)
    val LTextPlaceholder = Color(0xFFB8B8C0)

    val LAccent = Color(0xFF5E6AD2)
    val LAccentHover = Color(0xFF4F5BBF)
    val LAccentMuted = Color(0xFFECEEFE)
    val LAccentMutedText = Color(0xFF5E6AD2)

    val LDestructive = Color(0xFFE5534B)
    val LDestructiveMuted = Color(0xFFFFF1F0)
    val LSuccess = Color(0xFF30A46C)
    val LSuccessMuted = Color(0xFFF0FAF5)
    val LWarning = Color(0xFFC08A1E)
    val LWarningMuted = Color(0xFFFEF9EC)

    val LInputBg = Color(0xFFFFFFFF)
    val LInputBorder = Color(0xFFD0D0D4)

    // ── Dark theme ───────────────────────────────────────────────────────────
    val DBgApp = Color(0xFF0F0F10)
    val DBgSurface = Color(0xFF161618)
    val DBgSurfaceHover = Color(0xFF1E1E21)
    val DBgSurfaceRaised = Color(0xFF1E1E21)
    val DOverlay = Color(0x99000000)

    val DBorder = Color(0xFF26262A)
    val DBorderStrong = Color(0xFF36363C)

    val DTextPrimary = Color(0xFFE8E8F0)
    val DTextSecondary = Color(0xFF8E8E98)
    val DTextTertiary = Color(0xFF5E5E68)
    val DTextPlaceholder = Color(0xFF46464E)

    val DAccent = Color(0xFF7B87E0)
    val DAccentHover = Color(0xFF8B96E8)
    val DAccentMuted = Color(0xFF1E2040)
    val DAccentMutedText = Color(0xFF9BA8F0)

    val DDestructive = Color(0xFFF87171)
    val DDestructiveMuted = Color(0xFF2A1010)
    val DSuccess = Color(0xFF4ADE80)
    val DSuccessMuted = Color(0xFF0D2B18)
    val DWarning = Color(0xFFFBBF24)
    val DWarningMuted = Color(0xFF2A2008)

    val DInputBg = Color(0xFF1A1A1E)
    val DInputBorder = Color(0xFF2E2E34)

    val White = Color(0xFFFFFFFF)
}
