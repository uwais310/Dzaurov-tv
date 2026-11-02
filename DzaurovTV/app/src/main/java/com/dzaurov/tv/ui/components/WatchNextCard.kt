package com.dzaurov.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.dzaurov.tv.data.AppEntry
import com.dzaurov.tv.data.WatchNextItem
import com.dzaurov.tv.dz
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle

/** Poster for a Continue watching card, loaded off the main thread. */
@Composable
fun rememberPoster(uri: String?): State<ImageBitmap?> {
    val posters = LocalContext.current.dz.posters
    return produceState(initialValue = uri?.let { posters.peek(it) }, uri) {
        if (uri != null && value == null) value = posters.load(uri)
    }
}

/** "24 min left", "1 h 5 min left". */
fun remainingText(ms: Long?): String? {
    if (ms == null || ms <= 0) return null
    val min = (ms + 59_999) / 60_000
    return if (min >= 60) "${min / 60} h ${min % 60} min left" else "$min min left"
}

/**
 * A 16:9 Continue watching card: the app's poster, a soft bottom shade with the title,
 * the source app, and a progress bar. Focus = spring lift + glow in the app's color.
 */
@Composable
fun WatchNextCard(
    item: WatchNextItem,
    app: AppEntry?,
    width: Dp,
    height: Dp,
    perf: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
    val poster by rememberPoster(item.posterUri)
    val visual = if (app != null) rememberAppVisual(app).value else null
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 420f), label = "cwScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "cwRing")
    val brand = visual?.color ?: Dz.Blue
    val corner = 12.dp
    val shape = RoundedCornerShape(corner)

    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationY = -4.dp.toPx() * ring.value
            }
            .drawWithCache {
                val pad = 16.dp.toPx()
                val glow = Brush.radialGradient(
                    colors = listOf(brand.copy(alpha = 0.55f), Color.Transparent),
                    center = size.center,
                    radius = size.maxDimension * 0.75f,
                )
                onDrawBehind {
                    val a = ring.value
                    if (!perf && a > 0.01f) {
                        drawRect(glow, topLeft = Offset(-pad, -pad), size = Size(size.width + 2 * pad, size.height + 2 * pad), alpha = a)
                    }
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .tvClickable(onClick = onClick)
    ) {
        val inner = Modifier.fillMaxSize().clip(shape)
        val p = poster
        val v = visual
        when {
            p != null -> Image(
                bitmap = p,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = inner,
            )
            v != null && v.isBanner -> Image(
                bitmap = v.image,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
                modifier = inner,
            )
            else -> Box(inner.glass(corner, tint = brand.copy(alpha = 0.28f)))
        }

        // Bottom shade so the title stays readable on any poster.
        Spacer(
            Modifier.matchParentSize().clip(shape).drawWithCache {
                val shade = Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Color.Transparent,
                    1f to Color(0xD9000000),
                )
                onDrawBehind { drawRect(shade) }
            }
        )

        // Source app, top left.
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(7.dp)
                .height(18.dp)
                .background(Color(0x99000000), RoundedCornerShape(9.dp))
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(5.dp).background(brand, CircleShape))
            Text(
                app?.label ?: item.pkg.substringAfterLast('.'),
                style = dzStyle(9.sp, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(item.title, style = dzStyle(12.sp, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = listOfNotNull(item.subtitle, remainingText(item.remainingMs)).joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(line, style = dzStyle(9.sp, color = Dz.Ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val progress = item.progress
            if (progress != null) {
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0.03f, 1f))
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        Spacer(
            Modifier.matchParentSize().drawBehind {
                val a = ring.value
                if (a > 0.01f) {
                    drawRoundRect(Color.White.copy(alpha = a), cornerRadius = CornerRadius(corner.toPx()), style = Stroke(2.dp.toPx()))
                }
            }
        )
    }
}
