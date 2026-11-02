package com.dzaurov.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dzaurov.tv.ui.LaunchAnim
import kotlin.math.roundToInt

/**
 * iOS-style app opening: the tile grows out of its spot into a full-screen card in the
 * app's colour, with the icon swelling in the middle, while Android starts the app.
 */
@Composable
fun LaunchOverlay(anim: LaunchAnim) {
    val visual by rememberAppVisual(anim.app)
    val progress = remember(anim.id) { Animatable(0f) }
    LaunchedEffect(anim.id) {
        progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 170f))
    }
    val top = anim.color
    val bottom = mix(anim.color, Color(0xFF05070C), 0.65f)
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value.coerceIn(0f, 1.04f)
        val f = anim.from
        val l = lerpF(f.left, 0f, t)
        val tp = lerpF(f.top, 0f, t)
        val r = lerpF(f.right, size.width, t)
        val b = lerpF(f.bottom, size.height, t)
        val w = (r - l).coerceAtLeast(1f)
        val h = (b - tp).coerceAtLeast(1f)
        val radius = lerpF(16.dp.toPx(), 0f, t.coerceAtMost(1f))
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(top, bottom), startY = tp, endY = tp + h),
            topLeft = Offset(l, tp),
            size = Size(w, h),
            cornerRadius = CornerRadius(radius),
            alpha = (t * 2.6f).coerceIn(0f, 1f),
        )
        val icon = visual?.icon
        if (icon != null) {
            val s = lerpF(42.dp.toPx(), 128.dp.toPx(), t.coerceAtMost(1f))
            val cx = l + w / 2f
            val cy = tp + h / 2f
            drawImage(
                image = icon,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(icon.width, icon.height),
                dstOffset = IntOffset((cx - s / 2f).roundToInt(), (cy - s / 2f).roundToInt()),
                dstSize = IntSize(s.roundToInt(), s.roundToInt()),
                alpha = (1f - (t - 0.6f).coerceAtLeast(0f) * 2.4f).coerceIn(0f, 1f),
            )
        }
    }
}

private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = lerpF(a.red, b.red, t),
    green = lerpF(a.green, b.green, t),
    blue = lerpF(a.blue, b.blue, t),
    alpha = 1f,
)
