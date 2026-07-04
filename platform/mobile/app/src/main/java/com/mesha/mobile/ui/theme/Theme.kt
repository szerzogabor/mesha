package com.mesha.mobile.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Material color scheme wired to the Mesha (Linear-inspired) tokens. Every Material slot
 * is pinned to an exact web-app value so components that read `MaterialTheme.colorScheme`
 * — TopAppBar, Button, Card, TextField, Chip — render the same palette as the PWA.
 *
 * `surfaceTint` is neutralised (set to the surface color) so Material's tonal-elevation
 * overlay can't push a lilac tint onto raised surfaces; the web app draws flat, bordered
 * surfaces and we match that.
 */
private val LightColorScheme = lightColorScheme(
    primary = MeshaTokens.LAccent,
    onPrimary = MeshaTokens.White,
    primaryContainer = MeshaTokens.LAccentMuted,
    onPrimaryContainer = MeshaTokens.LAccentMutedText,
    secondary = MeshaTokens.LAccent,
    onSecondary = MeshaTokens.White,
    secondaryContainer = MeshaTokens.LAccentMuted,
    onSecondaryContainer = MeshaTokens.LAccentMutedText,
    tertiary = MeshaTokens.LAccent,
    onTertiary = MeshaTokens.White,
    background = MeshaTokens.LBgApp,
    onBackground = MeshaTokens.LTextPrimary,
    surface = MeshaTokens.LBgSurface,
    onSurface = MeshaTokens.LTextPrimary,
    surfaceVariant = MeshaTokens.LBgSurfaceHover,
    onSurfaceVariant = MeshaTokens.LTextSecondary,
    surfaceTint = MeshaTokens.LBgSurface,
    surfaceContainerLowest = MeshaTokens.LBgSurface,
    surfaceContainerLow = MeshaTokens.LBgSurface,
    surfaceContainer = MeshaTokens.LBgSurface,
    surfaceContainerHigh = MeshaTokens.LBgSurfaceHover,
    surfaceContainerHighest = MeshaTokens.LBgSurfaceHover,
    inverseSurface = MeshaTokens.LTextPrimary,
    inverseOnSurface = MeshaTokens.LBgSurface,
    outline = MeshaTokens.LBorderStrong,
    outlineVariant = MeshaTokens.LBorder,
    error = MeshaTokens.LDestructive,
    onError = MeshaTokens.White,
    errorContainer = MeshaTokens.LDestructiveMuted,
    onErrorContainer = MeshaTokens.LDestructive,
    scrim = MeshaTokens.LOverlay,
)

private val DarkColorScheme = darkColorScheme(
    primary = MeshaTokens.DAccent,
    onPrimary = MeshaTokens.White,
    primaryContainer = MeshaTokens.DAccentMuted,
    onPrimaryContainer = MeshaTokens.DAccentMutedText,
    secondary = MeshaTokens.DAccent,
    onSecondary = MeshaTokens.White,
    secondaryContainer = MeshaTokens.DAccentMuted,
    onSecondaryContainer = MeshaTokens.DAccentMutedText,
    tertiary = MeshaTokens.DAccent,
    onTertiary = MeshaTokens.White,
    background = MeshaTokens.DBgApp,
    onBackground = MeshaTokens.DTextPrimary,
    surface = MeshaTokens.DBgSurface,
    onSurface = MeshaTokens.DTextPrimary,
    surfaceVariant = MeshaTokens.DBgSurfaceHover,
    onSurfaceVariant = MeshaTokens.DTextSecondary,
    surfaceTint = MeshaTokens.DBgSurface,
    surfaceContainerLowest = MeshaTokens.DBgApp,
    surfaceContainerLow = MeshaTokens.DBgSurface,
    surfaceContainer = MeshaTokens.DBgSurface,
    surfaceContainerHigh = MeshaTokens.DBgSurfaceHover,
    surfaceContainerHighest = MeshaTokens.DBgSurfaceRaised,
    inverseSurface = MeshaTokens.DTextPrimary,
    inverseOnSurface = MeshaTokens.DBgSurface,
    outline = MeshaTokens.DBorderStrong,
    outlineVariant = MeshaTokens.DBorder,
    error = MeshaTokens.DDestructive,
    onError = MeshaTokens.White,
    errorContainer = MeshaTokens.DDestructiveMuted,
    onErrorContainer = MeshaTokens.DDestructive,
    scrim = MeshaTokens.DOverlay,
)

/**
 * App theme. Unlike the previous Material-You setup, dynamic (wallpaper-derived) color is
 * intentionally NOT used: the point of this theme is to look like the Mesha web app, and
 * that requires a fixed brand palette on every device.
 */
@Composable
fun MeshaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkMeshaColors else LightMeshaColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Edge-to-edge (enabled in MainActivity) already makes the system bars
            // transparent; here we just drive icon contrast off the active theme so the
            // status/nav icons stay legible over the app's surface color.
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalMeshaColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeshaTypography,
            shapes = MeshaShapes,
            content = content,
        )
    }
}
