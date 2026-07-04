package com.mesha.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * PWA design tokens that don't map onto a Material [androidx.compose.material3.ColorScheme]
 * slot. Material only exposes primary/surface/error/etc., but the web app draws on a richer
 * set — a distinct tertiary text tone, two border weights, and success/warning semantic
 * colors. Exposing them here (mirroring `globals.css`) lets screens reference the same
 * tokens the PWA uses instead of approximating with Material roles.
 *
 * Access via [Mesha.colors].
 */
@Immutable
data class MeshaExtendedColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textPlaceholder: Color,
    val surface: Color,
    val surfaceHover: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val accent: Color,
    val accentHover: Color,
    val accentMuted: Color,
    val accentMutedText: Color,
    val success: Color,
    val successMuted: Color,
    val warning: Color,
    val warningMuted: Color,
    val destructive: Color,
    val destructiveMuted: Color,
)

internal val LightMeshaColors = MeshaExtendedColors(
    textPrimary = MeshaTokens.LTextPrimary,
    textSecondary = MeshaTokens.LTextSecondary,
    textTertiary = MeshaTokens.LTextTertiary,
    textPlaceholder = MeshaTokens.LTextPlaceholder,
    surface = MeshaTokens.LBgSurface,
    surfaceHover = MeshaTokens.LBgSurfaceHover,
    surfaceRaised = MeshaTokens.LBgSurfaceRaised,
    border = MeshaTokens.LBorder,
    borderStrong = MeshaTokens.LBorderStrong,
    accent = MeshaTokens.LAccent,
    accentHover = MeshaTokens.LAccentHover,
    accentMuted = MeshaTokens.LAccentMuted,
    accentMutedText = MeshaTokens.LAccentMutedText,
    success = MeshaTokens.LSuccess,
    successMuted = MeshaTokens.LSuccessMuted,
    warning = MeshaTokens.LWarning,
    warningMuted = MeshaTokens.LWarningMuted,
    destructive = MeshaTokens.LDestructive,
    destructiveMuted = MeshaTokens.LDestructiveMuted,
)

internal val DarkMeshaColors = MeshaExtendedColors(
    textPrimary = MeshaTokens.DTextPrimary,
    textSecondary = MeshaTokens.DTextSecondary,
    textTertiary = MeshaTokens.DTextTertiary,
    textPlaceholder = MeshaTokens.DTextPlaceholder,
    surface = MeshaTokens.DBgSurface,
    surfaceHover = MeshaTokens.DBgSurfaceHover,
    surfaceRaised = MeshaTokens.DBgSurfaceRaised,
    border = MeshaTokens.DBorder,
    borderStrong = MeshaTokens.DBorderStrong,
    accent = MeshaTokens.DAccent,
    accentHover = MeshaTokens.DAccentHover,
    accentMuted = MeshaTokens.DAccentMuted,
    accentMutedText = MeshaTokens.DAccentMutedText,
    success = MeshaTokens.DSuccess,
    successMuted = MeshaTokens.DSuccessMuted,
    warning = MeshaTokens.DWarning,
    warningMuted = MeshaTokens.DWarningMuted,
    destructive = MeshaTokens.DDestructive,
    destructiveMuted = MeshaTokens.DDestructiveMuted,
)

internal val LocalMeshaColors = staticCompositionLocalOf { LightMeshaColors }

/** Convenience accessors so call sites can write `Mesha.colors.textTertiary`. */
object Mesha {
    val colors: MeshaExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMeshaColors.current

    // Re-export so screens can grab the standard Material scheme from the same object.
    val materialColors
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme
}
