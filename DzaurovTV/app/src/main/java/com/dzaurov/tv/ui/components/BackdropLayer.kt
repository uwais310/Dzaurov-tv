package com.dzaurov.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.dzaurov.tv.ui.theme.Dz
import kotlin.math.roundToInt

/**
 * The scene behind everything: background picture (moved only by translation for parallax),
 * drifting fog, up to 12 light particles, the app's brand-color wash and the readability scrims.
 */
@Composable
fun BackdropLayer(
    state: BackdropState,
    wash: Color?,
    dim: Float,
    atmosphere: Boolean,
    particles: Boolean,
    modifier: Modifier = Modifier,
    kenBurns: Boolean = false,
) {
    // Screensaver only: a very slow zoom and pan, like a photo frame (Ken Burns).
    val kb = if (kenBurns) rememberKenBurns() else null
    Box(modifier.fillMaxSize().background(Dz.Room)) {
        Crossfade(targetState = state.backdrop, animationSpec = tween(900), label = "backdrop") { b ->
            if (b != null) {
                Canvas(
                    Modifier.fillMaxSize().graphicsLayer {
                        val k = kb?.value ?: 0f
                        translationX = state.parallax.value + (k - 0.5f) * 36.dp.toPx() * (if (kb != null) 1f else 0f)
                        val sc = 1f + 0.07f * k
                        scaleX = sc
                        scaleY = sc
                    }
                ) {
                    val w = size.width
                    val h = size.height
                    drawImage(
                        image = b.full,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(b.full.width, b.full.height),
                        dstOffset = IntOffset((-w * OVERSCAN / 2f).roundToInt(), (-h * OVERSCAN / 2f).roundToInt()),
                        dstSize = IntSize((w * (1f + OVERSCAN)).roundToInt(), (h * (1f + OVERSCAN)).roundToInt()),
                        filterQuality = FilterQuality.Medium,
                    )
                }
            }
        }
        if (atmosphere) Atmosphere(state.backdrop?.fog, particles)
        Crossfade(targetState = wash, animationSpec = tween(650), label = "wash") { c ->
            if (c != null) BrandWash(c)
        }
        Scrims(dim)
    }
}

@Composable
private fun rememberKenBurns(): State<Float> {
    val t = rememberInfiniteTransition(label = "kenBurns")
    return t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(45_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "kenBurnsT",
    )
}

@Composable
private fun BrandWash(c: Color) {
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val glow = Brush.radialGradient(
                colors = listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(size.width * 0.74f, size.height * 0.40f),
                radius = size.width * 0.55f,
            )
            onDrawBehind {
                drawRect(Color(0xFF05070C), alpha = 0.38f)
                drawRect(glow)
            }
        }
    )
}

@Composable
private fun Scrims(dim: Float) {
    val d = animateFloatAsState(dim, tween(400), label = "dim")
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            val left = Brush.horizontalGradient(
                0f to Color(0xA605070C), 0.38f to Color(0x4805070C), 0.62f to Color(0x0005070C),
            )
            val bottom = Brush.verticalGradient(
                0f to Color(0x0005070C), 0.48f to Color(0x0005070C), 0.75f to Color(0x5C05070C), 1f to Color(0xD405070C),
            )
            onDrawBehind {
                drawRect(left)
                drawRect(bottom)
                if (d.value > 0.01f) drawRect(Color(0xFF05070C), alpha = d.value)
            }
        }
    )
}

private class Mote(val x: Float, val y: Float, val r: Float, val speed: Int, val phase: Float, val drift: Float)

/** Fixed set of 12 particles: positions are computed from time, nothing is allocated per frame. */
private val MOTES: List<Mote> = List(12) { i ->
    val rnd = java.util.Random(7L + i * 31L)
    Mote(
        x = rnd.nextFloat(),
        y = 0.52f + rnd.nextFloat() * 0.46f,
        r = 1.1f + rnd.nextFloat() * 1.8f,
        speed = 1 + (i % 2),
        phase = rnd.nextFloat(),
        drift = 16f + rnd.nextFloat() * 36f,
    )
}

/**
 * Fog and particles move so slowly that 20 updates a second look the same as 60, and the box's
 * GPU gets two thirds of its time back for the tiles. One fog layer, not two.
 */
@Composable
private fun Atmosphere(fog: ImageBitmap?, particles: Boolean) {
    val clock = produceState(0L) {
        val start = System.nanoTime()
        while (true) {
            value = (System.nanoTime() - start) / 1_000_000L
            delay(50)
        }
    }
    val a = remember { derivedStateOf { (clock.value % 48_000L) / 48_000f } }
    val p = remember { derivedStateOf { (clock.value % 26_000L) / 26_000f } }
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (fog != null) {
            val src = IntSize(fog.width, fog.height)
            val fh = (h * 1.1f).roundToInt()
            val fw = w.roundToInt()
            val top = (h * 0.06f).roundToInt()
            val x1 = (-a.value * fw).roundToInt()
            drawImage(fog, IntOffset.Zero, src, IntOffset(x1, top), IntSize(fw, fh), alpha = 0.7f, filterQuality = FilterQuality.Low)
            drawImage(fog, IntOffset.Zero, src, IntOffset(x1 + fw, top), IntSize(fw, fh), alpha = 0.7f, filterQuality = FilterQuality.Low)
        }
        if (particles) {
            val unit = density
            for (m in MOTES) {
                val ph = (p.value * m.speed + m.phase) % 1f
                val y = (m.y - ph * 0.42f) * h
                val x = m.x * w + ph * m.drift * unit
                val fade = when {
                    ph < 0.15f -> ph / 0.15f
                    ph > 0.8f -> (1f - ph) / 0.2f
                    else -> 1f
                } * 0.85f
                drawCircle(Color(0xFFFFF0D2), radius = m.r * 3.4f * unit, center = Offset(x, y), alpha = fade * 0.16f)
                drawCircle(Color(0xFFFFF8E6), radius = m.r * unit, center = Offset(x, y), alpha = fade)
            }
        }
    }
}
