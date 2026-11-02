package com.dzaurov.tv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.data.AppEntry
import com.dzaurov.tv.data.AppVisual
import com.dzaurov.tv.dz
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Icon, banner and colour for an app, loaded off the main thread and cached. */
@Composable
fun rememberAppVisual(app: AppEntry): State<AppVisual?> {
    val icons = LocalContext.current.dz.icons
    return produceState(initialValue = icons.peek(app), app) {
        value = icons.peek(app) ?: run {
            value = null
            icons.load(app)
        }
    }
}

/**
 * A glass app tile in the iOS style: the app's own icon as a squircle, its name below,
 * frosted glass behind. On focus it lifts, grows, swings in from the side you came from,
 * tints the glass in the app's colour, glows, and a highlight sweeps across once.
 * Only scale, rotation, alpha and translation animate, so rows stay smooth on the box.
 *
 * [launchFrom]: when set, OK hands over the tile's on-screen bounds and colour so the app
 * can open with a zoom out of this tile.
 * [appearDelay]: >= 0 plays a staggered entrance after that many ms.
 */
@Composable
fun AppTile(
    app: AppEntry,
    width: Dp,
    height: Dp,
    perf: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    moving: Boolean = false,
    starred: Boolean = false,
    focusScale: Float = 1.1f,
    jiggle: Boolean = false,
    isNew: Boolean = false,
    appearDelay: Long = -1L,
    launchFrom: ((Rect, Color) -> Unit)? = null,
) {
    val visual by rememberAppVisual(app)
    var focused by remember { mutableStateOf(false) }
    val active = focused || moving
    val brand = visual?.color ?: Dz.Blue
    val corner = if (height >= 84.dp) 18.dp else 15.dp

    val scale = animateFloatAsState(
        targetValue = if (moving) focusScale + 0.05f else if (focused) focusScale else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 400f),
        label = "tileScale",
    )
    val lift = animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 420f), label = "tileLift")
    val ring = animateFloatAsState(if (active) 1f else 0f, tween(180), label = "tileRing")
    val tint by animateColorAsState(
        targetValue = if (active) brand.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.10f),
        animationSpec = tween(240),
        label = "tileTint",
    )
    val swingY = remember { Animatable(0f) }
    val swingX = remember { Animatable(0f) }
    val gloss = remember { Animatable(-1f) }
    val appear = remember { Animatable(if (appearDelay >= 0L) 0f else 1f) }
    val bounds = remember { arrayOfNulls<Rect>(1) }
    val wiggle = if (jiggle || moving) rememberJiggle(app.packageName) else null

    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            delay(appearDelay.coerceAtLeast(0L))
            appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
        }
    }
    LaunchedEffect(focused) {
        if (!focused) return@LaunchedEffect
        val dir = NavDir.last
        launch {
            swingY.snapTo(
                when (dir) {
                    NavDir.RIGHT -> 10f
                    NavDir.LEFT -> -10f
                    else -> 0f
                }
            )
            swingY.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 230f))
        }
        launch {
            swingX.snapTo(
                when (dir) {
                    NavDir.DOWN -> 9f
                    NavDir.UP -> -9f
                    else -> 0f
                }
            )
            swingX.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 230f))
        }
        if (!perf) launch {
            gloss.snapTo(-0.6f)
            gloss.animateTo(1.6f, tween(950, delayMillis = 140, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                val s = scale.value * (0.9f + 0.1f * appear.value)
                scaleX = s
                scaleY = s
                alpha = appear.value
                translationY = -7.dp.toPx() * lift.value + (1f - appear.value) * 20.dp.toPx()
                rotationY = swingY.value
                rotationX = swingX.value
                rotationZ = (wiggle?.value ?: 0f) * (if (moving) 2.2f else 1.3f)
                cameraDistance = 14f * density
            }
            .onGloballyPositioned { bounds[0] = it.boundsInRoot() }
            .drawWithCache {
                val pad = 20.dp.toPx()
                val glow = Brush.radialGradient(
                    colors = listOf(brand.copy(alpha = 0.55f), Color.Transparent),
                    center = size.center,
                    radius = size.maxDimension * 0.72f,
                )
                val shadow = Brush.radialGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.8f),
                    radius = size.maxDimension * 0.62f,
                )
                onDrawBehind {
                    val a = lift.value
                    if (!perf && a > 0.01f) {
                        drawRect(shadow, topLeft = Offset(-pad, 0f), size = Size(size.width + 2 * pad, size.height + pad * 1.5f), alpha = a)
                        drawRect(glow, topLeft = Offset(-pad, -pad), size = Size(size.width + 2 * pad, size.height + 2 * pad), alpha = a)
                    }
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onLongClick = onLongClick) {
                val b = bounds[0]
                if (launchFrom != null && b != null) launchFrom(b, brand) else onClick()
            }
            .glass(corner, tint = tint)
    ) {
        val v = visual
        val iconSize = (height * 0.43f).coerceAtMost(44.dp)
        val iconMod = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 9.dp).size(iconSize)
        if (v != null) {
            Image(bitmap = v.icon, contentDescription = null, filterQuality = FilterQuality.Medium, modifier = iconMod)
        } else {
            Box(iconMod.background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(iconSize * 0.3f)))
        }
        Text(
            app.label,
            style = dzStyle(if (height >= 84.dp) 12.sp else 11.sp, FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 11.dp, end = 10.dp, bottom = 8.dp),
        )
        if (!perf) GlossSweep(corner) { gloss.value }
        Spacer(
            Modifier.matchParentSize().drawBehind {
                val a = ring.value
                if (a > 0.01f) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.9f * a),
                        cornerRadius = CornerRadius(corner.toPx()),
                        style = Stroke(1.6.dp.toPx()),
                    )
                }
            }
        )
        if (starred || isNew) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp)
                    .background(if (isNew && !starred) Dz.Blue else Color(0xEBFFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (starred) Icon(DzIcons.Star, contentDescription = "On Home", tint = Dz.Dark, modifier = Modifier.size(10.dp))
            }
        }
        if (isNew) {
            Text(
                "NEW",
                style = dzStyle(8.sp, FontWeight.SemiBold, Dz.Blue, spacing = 1.sp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 9.dp),
            )
        }
        if (moving) {
            Icon(DzIcons.ChevronLeft, contentDescription = null, tint = Dz.Ink, modifier = Modifier.align(Alignment.CenterStart).offset(x = (-22).dp).size(16.dp))
            Icon(DzIcons.ChevronRight, contentDescription = null, tint = Dz.Ink, modifier = Modifier.align(Alignment.CenterEnd).offset(x = 22.dp).size(16.dp))
        }
    }
}

/** One soft highlight sweeping across the tile when it gains focus (translation only). */
@Composable
private fun BoxScope.GlossSweep(corner: Dp, position: () -> Float) {
    Spacer(
        Modifier.matchParentSize().clip(RoundedCornerShape(corner)).drawWithCache {
            val bw = size.width * 0.38f
            val brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.24f), Color.Transparent),
                startX = 0f,
                endX = bw,
            )
            onDrawBehind {
                val x = position()
                if (x > -0.55f && x < 1.55f) {
                    translate(left = x * size.width - bw / 2f) {
                        drawRect(brush, size = Size(bw, size.height))
                    }
                }
            }
        }
    )
}

/** The "+" tile at the end of Favorites. */
@Composable
fun AddTile(
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
    appearDelay: Long = -1L,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.1f else 1f, spring(dampingRatio = 0.58f, stiffness = 400f), label = "addScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "addRing")
    val appear = remember { Animatable(if (appearDelay >= 0L) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            delay(appearDelay.coerceAtLeast(0L))
            appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
        }
    }
    val corner = if (height >= 84.dp) 18.dp else 15.dp
    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                val s = scale.value * (0.9f + 0.1f * appear.value)
                scaleX = s
                scaleY = s
                alpha = appear.value
                translationY = -7.dp.toPx() * ring.value + (1f - appear.value) * 20.dp.toPx()
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick)
            .glass(corner, tint = Color.White.copy(alpha = if (focused) 0.20f else 0.07f))
            .drawBehind {
                if (ring.value > 0.01f) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.9f * ring.value),
                        cornerRadius = CornerRadius(corner.toPx()),
                        style = Stroke(1.6.dp.toPx()),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier.size(30.dp).background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(DzIcons.Plus, contentDescription = null, tint = Dz.Ink, modifier = Modifier.size(18.dp))
            }
            Text("Add apps", style = dzStyle(11.sp, FontWeight.Medium))
        }
    }
}
