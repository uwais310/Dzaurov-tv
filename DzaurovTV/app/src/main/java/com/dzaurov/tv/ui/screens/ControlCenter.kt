package com.dzaurov.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.data.NetKind
import com.dzaurov.tv.ui.MainViewModel
import com.dzaurov.tv.ui.Screen
import com.dzaurov.tv.ui.components.ClockFmt
import com.dzaurov.tv.ui.components.DateFmt
import com.dzaurov.tv.ui.components.DzIcons
import com.dzaurov.tv.ui.components.WeatherBadge
import com.dzaurov.tv.ui.components.focusRing
import com.dzaurov.tv.ui.components.safeRequest
import com.dzaurov.tv.ui.components.tvClickable
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle
import java.time.LocalDateTime

/**
 * iPhone-style Control Center: slides in from the right with quick switches for
 * network, Bluetooth, picture, sound, sleep, screensaver, theme and performance.
 * A real dialog window, so focus can't wander behind it and Back closes it.
 */
@Composable
fun ControlCenter(vm: MainViewModel, now: LocalDateTime, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val net by vm.network.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val helperOn by vm.sleepServiceOn.collectAsStateWithLifecycle()
    val timerEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val first = remember { FocusRequester() }
        val enter = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 320f))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier
                    .padding(end = 28.dp)
                    .width(396.dp)
                    .graphicsLayer {
                        translationX = (1f - enter.value) * 90.dp.toPx()
                        alpha = enter.value.coerceIn(0f, 1f)
                    }
                    .background(Color(0xEB0E121C), RoundedCornerShape(30.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(30.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(now.format(ClockFmt), style = dzStyle(30.sp, FontWeight.SemiBold, spacing = (-1).sp))
                        Text(now.format(DateFmt), style = dzStyle(11.sp, color = Dz.Ink2))
                    }
                    WeatherBadge(weather)
                }
                Spacer(Modifier.height(2.dp))

                val timerText = if (timerEndsAt == 0L) "Off" else {
                    val left = ((timerEndsAt - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L) + 1
                    "$left min left"
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcTile(
                        icon = DzIcons.network(net.kind, net.bars),
                        title = "Wi-Fi",
                        value = net.label,
                        active = net.kind != NetKind.Offline,
                        modifier = Modifier.focusRequester(first),
                        onClick = {
                            onDismiss()
                            vm.openWifi(context)
                        },
                    )
                    CcTile(DzIcons.Bluetooth, "Bluetooth", "Remotes, audio", false) {
                        onDismiss()
                        vm.openBluetooth(context)
                    }
                    CcTile(DzIcons.Picture, "Picture", "Display", false) {
                        onDismiss()
                        vm.openDisplay(context)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcTile(DzIcons.Sound, "Sound", "Output", false) {
                        onDismiss()
                        vm.openSound(context)
                    }
                    CcTile(DzIcons.Timer, "Sleep timer", timerText, timerEndsAt != 0L) {
                        vm.cycleSleepTimer(context)
                    }
                    CcTile(DzIcons.Power, "Sleep now", if (helperOn) "Box and TV off" else "Needs helper", false) {
                        vm.sleepNow()
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcTile(DzIcons.Moon, "Screensaver", "Start now", false) {
                        onDismiss()
                        vm.go(Screen.Ambient)
                    }
                    CcTile(DzIcons.Palette, "Theme", vm.backgrounds.label(prefs.theme), false) {
                        vm.cycleTheme()
                    }
                    CcTile(DzIcons.Bolt, "Performance", if (prefs.performance) "On" else "Off", prefs.performance) {
                        vm.setPerformance(!prefs.performance)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CcTile(DzIcons.Settings, "Dzaurov TV", "Settings", false) {
                        onDismiss()
                        vm.go(Screen.Settings)
                    }
                    CcTile(DzIcons.Cog, "Android TV", "All settings", false) {
                        onDismiss()
                        vm.openSystemSettings(context)
                    }
                    CcTile(DzIcons.Hand, "Home and sleep", if (helperOn) "Helper on" else "Turn on", helperOn) {
                        if (helperOn) {
                            vm.showHelperInfo()
                        } else {
                            onDismiss()
                            vm.openHelperSettings(context)
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            first.safeRequest()
        }
    }
}

/** A Control Center switch: white when on (like iOS), frosted when off. */
@Composable
private fun CcTile(
    icon: ImageVector,
    title: String,
    value: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.07f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "ccScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(150), label = "ccRing")
    val fg = if (active) Dz.Dark else Dz.Ink
    Column(
        modifier
            .size(112.dp, 78.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .background(if (active) Color.White else Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
            .drawBehind { focusRing(ring.value, 22.dp.toPx()) }
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(if (active) Dz.Blue else Color.White.copy(alpha = 0.14f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) Color.White else Dz.Ink, modifier = Modifier.size(14.dp))
        }
        Column {
            Text(title, style = dzStyle(11.sp, FontWeight.SemiBold, fg), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = dzStyle(10.sp, color = if (active) Dz.Dark.copy(alpha = 0.7f) else Dz.Ink2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
