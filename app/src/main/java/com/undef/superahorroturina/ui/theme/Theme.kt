package com.undef.superahorroturina.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary          = Blue500,
    onPrimary        = LightSurface,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Blue600,
    secondary        = Cyan500,
    onSecondary      = LightSurface,
    secondaryContainer = CyanSoft,
    onSecondaryContainer = Cyan400,
    tertiary         = Green500,
    onTertiary       = LightSurface,
    background       = LightBackground,
    onBackground     = LightText,
    surface          = LightSurface,
    onSurface        = LightText,
    surfaceVariant   = LightSurface2,
    onSurfaceVariant = LightText2,
    outline          = LightBorder,
    error            = Red500,
    onError          = LightSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary          = Blue400,
    onPrimary        = DarkBackground,
    primaryContainer = BlueDark,
    onPrimaryContainer = Blue400,
    secondary        = Cyan400,
    onSecondary      = DarkBackground,
    secondaryContainer = CyanDark,
    onSecondaryContainer = Cyan400,
    tertiary         = Green400,
    onTertiary       = DarkBackground,
    background       = DarkBackground,
    onBackground     = DarkText,
    surface          = DarkSurface,
    onSurface        = DarkText,
    surfaceVariant   = DarkSurface2,
    onSurfaceVariant = DarkText2,
    outline          = DarkBorder,
    error            = Red400,
    onError          = DarkBackground,
)

@Composable
fun SuperAhorroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
