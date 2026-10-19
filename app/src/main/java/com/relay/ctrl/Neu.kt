package com.relay.ctrl
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Pal(val bg: Color, val hi: Color, val lo: Color, val txt: Color, val dim: Color, val accent: Color)

val DarkPal = Pal(Color(0xFF1F2025), Color(0xFF2A2C33), Color(0xFF141519), Color(0xFFE8E6E3), Color(0xFF7C7F8A), Color(0xFFFF8A2B))
val LightPal = Pal(Color(0xFFE4E5EC), Color(0xFFFFFFFF), Color(0xFFA9ACBB), Color(0xFF2B2D35), Color(0xFF7A7D8A), Color(0xFFE5600A))
val LocalPal = staticCompositionLocalOf { DarkPal }

val Amber = Color(0xFFFFB366); val Orange = Color(0xFFFF8A2B); val Deep = Color(0xFFE5600A)

/** Raised: dual shadow (light top-left, dark bottom-right). Pressed: inset look. */
@Composable
fun Modifier.neu(radius: Dp = 22.dp, depth: Dp = 7.dp, pressed: Boolean = false): Modifier {
    val p = LocalPal.current
    return if (pressed) this
        .background(Brush.linearGradient(listOf(p.lo.copy(alpha = .7f), p.hi.copy(alpha = .7f))), RoundedCornerShape(radius))
        .border(1.dp, p.hi.copy(alpha = .3f), RoundedCornerShape(radius))
    else this.drawBehind {
        val r = radius.toPx(); val d = depth.toPx()
        drawIntoCanvas { c ->
            val paint = Paint().asFrameworkPaint().apply { isAntiAlias = true; color = p.bg.toArgb() }
            paint.setShadowLayer(d * 1.6f, -d, -d, p.hi.toArgb()); c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            paint.setShadowLayer(d * 1.6f, d, d, p.lo.toArgb()); c.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }
}

@Composable
fun Modifier.neuCircle(pressed: Boolean = false, depth: Dp = 6.dp) = this.neu(1000.dp, depth, pressed)

@Composable
fun NeuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPal else LightPal
    val scheme = if (dark) darkColorScheme(primary = Orange, background = p.bg, surface = p.bg, onSurface = p.txt, onBackground = p.txt)
                 else lightColorScheme(primary = Deep, background = p.bg, surface = p.bg, onSurface = p.txt, onBackground = p.txt)
    CompositionLocalProvider(LocalPal provides p) { MaterialTheme(colorScheme = scheme, content = content) }
}
