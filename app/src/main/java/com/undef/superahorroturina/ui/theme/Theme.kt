package com.undef.superahorroturina.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary              = Blue500,
    onPrimary            = LightSurface,
    primaryContainer     = BlueSoft,
    onPrimaryContainer   = Blue600,
    secondary            = Cyan500,
    onSecondary          = LightSurface,
    secondaryContainer   = CyanSoft,
    onSecondaryContainer = Cyan500,
    tertiary             = Green500,
    onTertiary           = LightSurface,
    tertiaryContainer    = Color_GreenSoft,
    onTertiaryContainer  = Green500,
    background           = LightBackground,
    onBackground         = LightText,
    surface              = LightSurface,
    onSurface            = LightText,
    surfaceVariant       = LightBackground2,
    onSurfaceVariant     = LightText2,
    surfaceContainer     = LightSurface2,
    surfaceContainerHigh = LightBackground2,
    outline              = LightBorder,
    outlineVariant       = LightBorder,
    error                = Red500,
    onError              = LightSurface,
    errorContainer       = Color_RedSoft,
    onErrorContainer     = Red500,
    inverseSurface       = LightText,
    inverseOnSurface     = LightSurface,
    inversePrimary       = BlueSoft,
)

private val DarkColorScheme = darkColorScheme(
    primary              = Blue400,
    onPrimary            = DarkBackground,
    primaryContainer     = BlueDark,
    onPrimaryContainer   = Blue400,
    secondary            = Cyan400,
    onSecondary          = DarkBackground,
    secondaryContainer   = CyanDark,
    onSecondaryContainer = Cyan400,
    tertiary             = Green400,
    onTertiary           = DarkBackground,
    tertiaryContainer    = Color_GreenDark,
    onTertiaryContainer  = Green400,
    background           = DarkBackground,
    onBackground         = DarkText,
    surface              = DarkSurface,
    onSurface            = DarkText,
    surfaceVariant       = DarkSurface2,
    onSurfaceVariant     = DarkText2,
    surfaceContainer     = DarkSurface,
    surfaceContainerHigh = DarkSurface2,
    outline              = DarkBorder,
    outlineVariant       = DarkBorder,
    error                = Red400,
    onError              = DarkBackground,
    errorContainer       = Color_RedDark,
    onErrorContainer     = Red400,
    inverseSurface       = DarkText,
    inverseOnSurface     = DarkBackground,
    inversePrimary       = BlueDark,
)

// Shapes modernas y redondeadas — estilo Klarity
val KlarityShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
        shapes      = KlarityShapes,
        content     = content
    )
}
