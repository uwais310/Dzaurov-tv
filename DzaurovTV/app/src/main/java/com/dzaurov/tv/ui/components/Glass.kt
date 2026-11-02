package com.dzaurov.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dzaurov.tv.data.Backdrop

/** Extra size the background is drawn at, so parallax never shows an edge. */
const val OVERSCAN = 0.06f

/** The current background and its parallax offset (px), shared by every glass panel. */
@Stable
class BackdropState {
    var backdrop by mutableStateOf<Backdrop?>(null)
    val parallax = Animatable(0f)
    var screenW = 1f
    var screenH = 1f
}

val LocalBackdrop = staticCompositionLocalOf { BackdropState() }

/**
 * Frosted "liquid glass" without live blur (the box can't afford it):
 * 1. the matching slice of a pre-blurred, colour-boosted copy of the background,
 *    drawn through a shader so the rounded corners stay anti-aliased,
 * 2. a translucent [tint] (white, or the app's colour when focused),
 * 3. a soft sheen at the top and a rim that is bright on top and fades down,
 *    like light catching the edge of a glass pane.
 * Position and parallax are read only while drawing, so moving panels never recompose.
 */
fun Modifier.glass(
    radius: Dp,
    tint: Color = Color.White.copy(alpha = 0.10f),
    border: Color = Color.White.copy(alpha = 0.20f),
    highlight: Float = 0.55f,
): Modifier = composed {
    val bs = LocalBackdrop.current
    val pos = remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos.value = it.positionInRoot() }
        .drawWithCache {
            val r = radius.toPx()
            val corner = CornerRadius(r)
            val b = bs.backdrop
            val shader = b?.let { ImageShader(it.blurred, TileMode.Clamp, TileMode.Clamp) }
            val shaderBrush = shader?.let { ShaderBrush(it) }
            val matrix = android.graphics.Matrix()
            val rim = Brush.verticalGradient(
                0f to Color.White.copy(alpha = highlight),
                0.30f to border,
                1f to border.copy(alpha = border.alpha * 0.45f),
            )
            val sheen = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.45f to Color.White.copy(alpha = 0.02f),
                1f to Color.Transparent,
            )
            val line = Stroke(width = 1.dp.toPx())
            onDrawBehind {
                if (b != null && shader != null && shaderBrush != null) {
                    val w = bs.screenW
                    val h = bs.screenH
                    val p = pos.value
                    val sx = w * (1f + OVERSCAN) / b.blurred.width
                    val sy = h * (1f + OVERSCAN) / b.blurred.height
                    val left = -(w * OVERSCAN / 2f) + bs.parallax.value - p.x
                    val top = -(h * OVERSCAN / 2f) - p.y
                    matrix.setScale(sx, sy)
                    matrix.postTranslate(left, top)
                    shader.setLocalMatrix(matrix)
                    drawRoundRect(brush = shaderBrush, cornerRadius = corner)
                } else {
                    drawRoundRect(Color(0xFF1A1F28), cornerRadius = corner)
                }
                drawRoundRect(tint, cornerRadius = corner)
                drawRoundRect(sheen, cornerRadius = corner)
                drawRoundRect(rim, cornerRadius = corner, style = line)
            }
        }
}
