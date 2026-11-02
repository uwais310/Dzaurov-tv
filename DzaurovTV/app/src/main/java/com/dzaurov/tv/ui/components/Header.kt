package com.dzaurov.tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.data.NetKind
import com.dzaurov.tv.data.NetState
import com.dzaurov.tv.data.Weather
import com.dzaurov.tv.ui.Screen
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun Header(
    screen: Screen,
    weather: Weather?,
    net: NetState,
    now: LocalDateTime,
    onTab: (Screen) -> Unit,
    onAmbient: () -> Unit,
    onWifi: () -> Unit,
    onControl: () -> Unit,
    sleepTimerEndsAt: Long,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 44.dp, end = 44.dp, top = 22.dp)
            .height(40.dp)
    ) {
        Row(
            Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(32.dp).glass(16.dp), contentAlignment = Alignment.Center) {
                Text("D", style = dzStyle(14.sp, FontWeight.SemiBold))
            }
            Column {
                Text("${greeting(now)}, Dzaurov", style = dzStyle(14.sp, FontWeight.Medium))
                Text(now.format(DateFmt), style = dzStyle(10.sp, color = Dz.Ink2))
            }
        }

        NavPill(screen, onTab, Modifier.align(Alignment.Center))

        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeatherBadge(weather)
            Divider()
            Text(now.format(ClockFmt), style = dzStyle(20.sp, FontWeight.Light, spacing = (-0.5).sp))
            RoundButton(
                icon = DzIcons.network(net.kind, net.bars),
                label = "${net.label} · open Wi-Fi settings",
                onClick = onWifi,
                tint = if (net.kind == NetKind.Offline) OfflineRed else Dz.Ink,
            )
            if (sleepTimerEndsAt > 0L) SleepChip(sleepTimerEndsAt, now)
            RoundButton(DzIcons.Toggles, "Control Center", onControl)
            RoundButton(DzIcons.Moon, "Ambient mode", onAmbient)
        }
    }
}

private data class Tab(val screen: Screen, val label: String?)

private val TABS = listOf(
    Tab(Screen.Home, "Home"),
    Tab(Screen.Apps, "All apps"),
    Tab(Screen.Search, null),
    Tab(Screen.Settings, null),
)

private data class Span(val left: Float, val width: Float)

/** Tabs in a glass pill. The white "lens" glides to the active tab with a springy overshoot. */
@Composable
private fun NavPill(screen: Screen, onTab: (Screen) -> Unit, modifier: Modifier) {
    val active = when (screen) {
        Screen.HiddenApps, Screen.Photos -> Screen.Settings
        else -> screen
    }
    val spans = remember { mutableStateMapOf<Screen, Span>() }
    // Two edges with different springs: the leading edge races ahead and the trailing one
    // catches up, so the lens stretches and settles like a drop of liquid.
    val lensL = remember { Animatable(0f) }
    val lensR = remember { Animatable(0f) }
    var placed by remember { mutableStateOf(false) }
    val target = spans[active]

    LaunchedEffect(target) {
        val t = target ?: return@LaunchedEffect
        val right = t.left + t.width
        if (!placed) {
            lensL.snapTo(t.left)
            lensR.snapTo(right)
            placed = true
        } else {
            val movingRight = t.left > lensL.value
            val fast = spring<Float>(dampingRatio = 0.68f, stiffness = 700f)
            val slow = spring<Float>(dampingRatio = 0.78f, stiffness = 260f)
            launch { lensL.animateTo(t.left, if (movingRight) slow else fast) }
            launch { lensR.animateTo(right, if (movingRight) fast else slow) }
        }
    }

    val lensBrush = remember { Brush.verticalGradient(listOf(Color.White, Color(0xFFE4E9F2))) }

    Box(modifier.glass(20.dp).padding(3.dp)) {
        Canvas(Modifier.matchParentSize()) {
            if (placed) {
                val l = minOf(lensL.value, lensR.value)
                val w = kotlin.math.abs(lensR.value - lensL.value).coerceAtLeast(size.height)
                drawRoundRect(
                    brush = lensBrush,
                    topLeft = Offset(l, 0f),
                    size = Size(w, size.height),
                    cornerRadius = CornerRadius(size.height / 2f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (tab in TABS) {
                TabButton(
                    tab = tab,
                    isActive = tab.screen == active,
                    onClick = { onTab(tab.screen) },
                    modifier = Modifier.onGloballyPositioned { c ->
                        spans[tab.screen] = Span(c.positionInParent().x, c.size.width.toFloat())
                    },
                )
            }
        }
    }
}

@Composable
private fun TabButton(tab: Tab, isActive: Boolean, onClick: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "tab")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "tabRing")
    val fg = if (isActive) Dz.Dark else Dz.Ink
    Box(
        modifier
            .height(30.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .drawBehind {
                if (!isActive && ring.value > 0.01f) {
                    drawRoundRect(Color.White.copy(alpha = 0.18f * ring.value), cornerRadius = CornerRadius(size.height / 2f))
                }
                focusRing(ring.value, size.height / 2f)
            }
            .padding(horizontal = if (tab.label != null) 14.dp else 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (tab.label != null) {
            Text(tab.label, style = dzStyle(13.sp, if (isActive) FontWeight.SemiBold else FontWeight.Medium, fg))
        } else {
            val icon = if (tab.screen == Screen.Search) DzIcons.Search else DzIcons.Settings
            Icon(icon, contentDescription = tab.screen.name, tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

private val OfflineRed = Color(0xFFFF7A7A)

/** Sleep timer countdown in the header, e.g. "42 min". */
@Composable
private fun SleepChip(endsAt: Long, now: LocalDateTime) {
    val left = ((endsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) + 1
    Row(
        Modifier.height(24.dp).glass(12.dp).padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(DzIcons.Timer, contentDescription = "Sleep timer", tint = Dz.Ink, modifier = Modifier.size(12.dp))
        Text("$left min", style = dzStyle(11.sp, FontWeight.Medium))
    }
}
