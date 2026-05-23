// KlarityDesign.kt — Componentes y modificadores de diseño premium para Klarity.
// Contiene: borde luminoso degradado, textura de fondo, separador con degradado,
// colored shadow modifier y helpers de glassmorphism simulado.
package com.undef.superahorroturina.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Borde luminoso con gradiente ─────────────────────────────────────────────
// Dibuja un borde de 1px con gradiente alrededor de la card.
// En light mode es un borde sutil; en dark mode es "luminoso".
fun Modifier.glowBorder(
    cornerRadius: Dp = 20.dp,
    isDark: Boolean = false
): Modifier = this.drawWithContent {
    drawContent()
    val colors = if (isDark) {
        listOf(
            Color(0xFF3B82F6).copy(alpha = 0.8f),
            Color(0xFF06B6D4).copy(alpha = 0.6f),
            Color(0xFF3B82F6).copy(alpha = 0.3f),
            Color(0xFF06B6D4).copy(alpha = 0.6f),
        )
    } else {
        listOf(
            Color(0xFF3B82F6).copy(alpha = 0.25f),
            Color(0xFF06B6D4).copy(alpha = 0.15f),
            Color(0xFF3B82F6).copy(alpha = 0.10f),
            Color(0xFF06B6D4).copy(alpha = 0.15f),
        )
    }
    val r = cornerRadius.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = colors,
            start  = Offset(0f, 0f),
            end    = Offset(size.width, size.height)
        ),
        cornerRadius = CornerRadius(r, r),
        style = Stroke(width = 1.dp.toPx())
    )
}

// ── Colored shadow (sombra de color) ─────────────────────────────────────────
// Android no soporta box-shadow con color directamente, pero se puede simular
// dibujando un rect con paint blur detrás del contenido.
fun Modifier.coloredShadow(
    color: Color,
    borderRadius: Dp = 20.dp,
    blurRadius: Dp = 20.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 6.dp
): Modifier = this.drawBehind {
    val shadowColor  = color.copy(alpha = 0.28f).toArgb()
    val transparent  = color.copy(alpha = 0f).toArgb()
    drawIntoCanvas {
        val paint = Paint().also { p ->
            p.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color  = transparent
                setShadowLayer(
                    blurRadius.toPx(),
                    offsetX.toPx(),
                    offsetY.toPx(),
                    shadowColor
                )
            }
        }
        val r = borderRadius.toPx()
        it.drawRoundRect(
            left   = 0f,
            top    = 0f,
            right  = size.width,
            bottom = size.height,
            radiusX = r,
            radiusY = r,
            paint   = paint
        )
    }
}

// ── Separador con degradado ───────────────────────────────────────────────────
@Composable
fun GradientDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Spacer(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        color.copy(alpha = 0.5f),
                        color.copy(alpha = 0.5f),
                        Color.Transparent
                    ),
                    startX = 0f,
                    endX   = Float.POSITIVE_INFINITY
                )
            )
    )
}

// ── Textura de puntos para fondo ─────────────────────────────────────────────
// Dibuja una grilla de puntos muy sutil encima del fondo para dar profundidad.
fun Modifier.dotPatternBackground(
    dotColor: Color,
    dotRadius: Float = 1f,
    spacing: Float = 24f
): Modifier = this.drawBehind {
    val cols = (size.width / spacing).toInt() + 2
    val rows = (size.height / spacing).toInt() + 2
    for (col in 0..cols) {
        for (row in 0..rows) {
            drawCircle(
                color  = dotColor,
                radius = dotRadius,
                center = Offset(col * spacing, row * spacing)
            )
        }
    }
}

// ── Card glassmorphism simulado ───────────────────────────────────────────────
// Simula glass card con capas semi-transparentes (compatible API 26+).
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val glassColor = if (isDark)
        Color(0xFF1A2030).copy(alpha = 0.75f)
    else
        Color(0xFFFFFFFF).copy(alpha = 0.82f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(glassColor)
            .glowBorder(cornerRadius = cornerRadius, isDark = isDark)
            .padding(20.dp)
    ) {
        content()
    }
}
