package com.dzaurov.tv.ui.components

import android.content.pm.ApplicationInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.data.Weather
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val ClockFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
val DateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

fun greeting(now: LocalDateTime): String = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Good night"
}

/** Current time, updated on each new minute (not every second). */
@Composable
fun rememberNow(): State<LocalDateTime> = produceState(LocalDateTime.now()) {
    while (true) {
        val n = LocalDateTime.now()
        value = n
        val ms = (60 - n.second) * 1000L - n.nano / 1_000_000
        delay(ms.coerceAtLeast(250L))
    }
}

fun categoryName(category: Int): String = when (category) {
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_AUDIO -> "Music"
    ApplicationInfo.CATEGORY_GAME -> "Game"
    ApplicationInfo.CATEGORY_IMAGE -> "Photos"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Maps"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Tools"
    else -> "App"
}

fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - at) / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 24 * 60 -> "${m / 60} h ago"
        m < 48 * 60 -> "yesterday"
        else -> "${m / (24 * 60)} days ago"
    }
}

/** Small glass pill with a colored dot, used above titles. */
@Composable
fun Chip(text: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(24.dp)
            .glass(12.dp)
            .padding(start = 9.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(6.dp).background(dot, CircleShape))
        Text(text.uppercase(Locale.ENGLISH), style = dzStyle(9.sp, FontWeight.SemiBold, spacing = 1.6.sp))
    }
}

@Composable
fun WeatherBadge(weather: Weather?, size: Dp = 16.dp, textSize: Int = 14, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (weather != null) {
            Icon(DzIcons.weather(weather.code, weather.isDay), contentDescription = DzIcons.weatherLabel(weather.code), tint = Dz.Ink, modifier = Modifier.size(size))
            Text("${weather.tempC}°", style = dzStyle(textSize.sp, FontWeight.Medium))
        } else {
            Text("—°", style = dzStyle(textSize.sp, FontWeight.Medium, Dz.Ink3))
        }
    }
}

/** Glass circle button with an icon; focus = lift + white ring. */
@Composable
fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 32.dp,
    tint: Color = Dz.Ink,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.12f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "btn")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "ring")
    Box(
        modifier
            .size(diameter)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .glass(diameter / 2)
            .drawBehind { focusRing(ring.value, diameter.toPx() / 2) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(diameter * 0.5f))
    }
}

/** White focus ring drawn just outside the element. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.focusRing(amount: Float, radius: Float) {
    if (amount <= 0.01f) return
    val g = 2.5.dp.toPx()
    drawRoundRect(
        color = Color.White.copy(alpha = amount),
        topLeft = Offset(-g, -g),
        size = Size(size.width + 2 * g, size.height + 2 * g),
        cornerRadius = CornerRadius(radius + g),
        style = Stroke(2.dp.toPx()),
    )
}

@Composable
fun PageTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 44.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = dzStyle(34.sp, FontWeight.SemiBold, spacing = (-1).sp))
        if (subtitle != null) Text(subtitle, style = dzStyle(13.sp, color = Dz.Ink2), modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
fun RowLabel(title: String, count: Int?, modifier: Modifier = Modifier) {
    Row(modifier.padding(start = 48.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(Locale.ENGLISH), style = dzStyle(10.sp, FontWeight.SemiBold, spacing = 2.sp))
        if (count != null) Text("$count", style = dzStyle(10.sp, color = Dz.Ink3))
    }
}

/**
 * Short message in a black "Dynamic Island" capsule: it springs open sideways from the
 * middle, like the iPhone's island, then closes back into itself.
 */
@Composable
fun BoxScope.Toast(message: String?) {
    // Keep the last text while the capsule closes, so it doesn't go blank mid-animation.
    var shown by remember { mutableStateOf("") }
    if (message != null) shown = message
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(120)) +
            expandHorizontally(spring(dampingRatio = 0.7f, stiffness = 420f), expandFrom = Alignment.CenterHorizontally) +
            scaleIn(spring(dampingRatio = 0.6f, stiffness = 420f), initialScale = 0.7f),
        exit = fadeOut(tween(180)) +
            shrinkHorizontally(tween(220), shrinkTowards = Alignment.CenterHorizontally) +
            scaleOut(tween(220), targetScale = 0.8f),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp),
    ) {
        Row(
            Modifier
                .height(36.dp)
                .background(Color.Black, CircleShape)
                .padding(start = 8.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(22.dp).background(Dz.Blue, CircleShape), contentAlignment = Alignment.Center) {
                Icon(DzIcons.Star, contentDescription = null, tint = Dz.Dark, modifier = Modifier.size(11.dp))
            }
            Text(shown, style = dzStyle(13.sp, FontWeight.Medium), maxLines = 1)
        }
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Spacer(modifier.width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.3f)))
}

@Composable
fun FullWidthNote(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 44.dp)) {
        Text(text, style = dzStyle(15.sp, color = Dz.Ink2))
    }
}
