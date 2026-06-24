package com.mesha.mobile.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Mesha brand accent (indigo) — mirrors the web app's accent token.
private val MeshaIndigo = Color(0xFF6366F1)
private val MeshaIndigoDark = Color(0xFF818CF8)

private val LightColors = lightColorScheme(
    primary = MeshaIndigo,
    secondary = Color(0xFF7C3AED),
)

private val DarkColors = darkColorScheme(
    primary = MeshaIndigoDark,
    secondary = Color(0xFFA78BFA),
)

/**
 * Material 3 theme with dark-mode support and dynamic color (Android 12+). Falls back to
 * the Mesha brand palette on older devices.
 */
@Composable
fun MeshaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
