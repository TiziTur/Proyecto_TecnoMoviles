// Pantalla de splash con animación de entrada del logo.
// LaunchedEffect(Unit) se usa para dos propósitos:
//   1. Animación: Animatable con Spring (rebote del logo) y tween (fade del texto).
//   2. Auto-navegación: delay(1400ms) + onNavigateToLogin() — efecto de una sola vez.
// Este es el patrón correcto en Compose para efectos de ciclo de vida (no runBlocking, no Thread.sleep).
package com.undef.superahorroturina.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.undef.superahorroturina.R
import com.undef.superahorroturina.ui.components.KlarityLogoIcon

@Composable
fun SplashScreen(onNavigateToLogin: () -> Unit) {
    val scale    = remember { Animatable(0.6f) }
    val alpha    = remember { Animatable(0f) }
    val dotAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMediumLow
            )
        )
        alpha.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 500)
        )
        dotAlpha.animateTo(
            targetValue   = 1f,
            animationSpec = tween(durationMillis = 400, delayMillis = 200)
        )
        delay(1200L)
        onNavigateToLogin()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF1D4ED8), // Blue 700
                        Color(0xFF0E7490)  // Cyan 700
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(modifier = Modifier.scale(scale.value)) {
                KlarityLogoIcon(size = 96)
            }
            Column(
                modifier = Modifier.alpha(alpha.value),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.splash_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.80f)
                )
            }
        }

        // Indicador de carga sutil en la parte inferior
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .alpha(dotAlpha.value)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White.copy(alpha = 0.5f),
                strokeWidth = 2.dp
            )
        }
    }
}
