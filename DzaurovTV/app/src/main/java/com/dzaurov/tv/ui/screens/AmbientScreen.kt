package com.dzaurov.tv.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.data.Weather
import com.dzaurov.tv.ui.MainViewModel
import com.dzaurov.tv.ui.components.ClockFmt
import com.dzaurov.tv.ui.components.Divider
import com.dzaurov.tv.ui.components.DzIcons
import com.dzaurov.tv.ui.components.glass
import com.dzaurov.tv.ui.components.safeRequest
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Random

private val LockDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)

/** In-app Ambient mode (screensaver): any button wakes it back to Home. */
@Composable
fun AmbientScreen(vm: MainViewModel, now: LocalDateTime, weather: Weather?) {
    val focus = remember { FocusRequester() }
    val helperOn by vm.sleepServiceOn.collectAsStateWithLifecycle()
    // Recomputed every minute, because `now` changes every minute.
    val sleepIn = remember(now) { vm.minutesUntilAutoSleep() }
    val status = when {
        sleepIn == null -> null
        helperOn -> "Box and TV sleep in $sleepIn min"
        else -> "Screen goes dark in $sleepIn min"
    }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) vm.exitAmbient()
                true
            }
            .focusable()
    ) {
        AmbientContent(now, weather, showWakeHint = true, status = status)
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.safeRequest()
    }
    LaunchedEffect(Unit) {
        vm.focusRestore.collect { focus.safeRequest() }
    }
}

/**
 * Lock-screen style: date and a huge clock at the top centre, weather below,
 * a soft "press any button" capsule at the bottom. Shared with the system screensaver.
 * Everything drifts a few dp every few minutes so nothing burns into an OLED panel.
 */
@Composable
fun AmbientContent(now: LocalDateTime, weather: Weather?, showWakeHint: Boolean, status: String? = null) {
    val driftX = remember { Animatable(0f) }
    val driftY = remember { Animatable(0f) }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 120f))
    }
    LaunchedEffect(Unit) {
        val rnd = Random()
        while (true) {
            delay(150_000)
            val x = rnd.nextFloat() * 36f - 18f
            val y = rnd.nextFloat() * 20f - 10f
            driftX.animateTo(x, tween(6000))
            driftY.animateTo(y, tween(6000))
        }
    }

    Box(Modifier.fillMaxSize()) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val top = Brush.verticalGradient(
                    0f to Color(0x8004060A), 0.42f to Color(0x0004060A), 0.70f to Color(0x0004060A), 1f to Color(0x9904060A),
                )
                onDrawBehind { drawRect(top) }
            }
        )
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 44.dp)
                .graphicsLayer {
                    translationX = driftX.value.dp.toPx()
                    translationY = driftY.value.dp.toPx() + (1f - appear.value) * 30.dp.toPx()
                    alpha = appear.value
                    val s = 0.94f + 0.06f * appear.value
                    scaleX = s
                    scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(now.format(LockDateFmt), style = dzStyle(19.sp, FontWeight.Medium, Dz.Ink2))
            Text(
                now.format(ClockFmt),
                style = dzStyle(150.sp, FontWeight.SemiBold, Color.White.copy(alpha = 0.94f), spacing = (-5).sp),
            )
            if (weather != null) {
                Row(
                    Modifier.height(34.dp).glass(17.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(DzIcons.weather(weather.code, weather.isDay), contentDescription = null, tint = Dz.Ink, modifier = Modifier.size(18.dp))
                    Text("${weather.tempC}°", style = dzStyle(16.sp, FontWeight.SemiBold))
                    val label = DzIcons.weatherLabel(weather.code)
                    if (label.isNotEmpty()) {
                        Divider(Modifier.height(14.dp))
                        Text(label, style = dzStyle(14.sp, color = Dz.Ink2))
                    }
                    Divider(Modifier.height(14.dp))
                    Text("Almaty", style = dzStyle(14.sp, color = Dz.Ink2))
                }
            }
        }
        if (status != null) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(DzIcons.Sleep, contentDescription = null, tint = Dz.Ink2, modifier = Modifier.size(14.dp))
                Text(status, style = dzStyle(12.sp, color = Dz.Ink2))
            }
        }
        if (showWakeHint) {
            val t = rememberInfiniteTransition(label = "wake")
            val pulse = t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse), label = "pulse")
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp)
                    .graphicsLayer { alpha = pulse.value * appear.value }
                    .height(32.dp)
                    .glass(16.dp)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Press any button to wake", style = dzStyle(12.sp, FontWeight.Medium))
            }
        }
    }
}

/**
 * Shown only when the box can't be switched off by the app (helper off): a black screen
 * that is kind to the panel. The box's own sleep timer takes it from here.
 */
@Composable
fun SleepScreen(vm: MainViewModel) {
    val focus = remember { FocusRequester() }
    val hint = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(5000)
        hint.animateTo(0f, tween(1800))
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onKeyEvent {
                if (it.type == KeyEventType.KeyDown) vm.exitAmbient()
                true
            }
            .focusable()
    ) {
        Column(
            Modifier.align(Alignment.Center).width(460.dp).graphicsLayer { alpha = hint.value },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(DzIcons.Sleep, contentDescription = null, tint = Dz.Ink2, modifier = Modifier.size(28.dp))
            Text("Sleeping", style = dzStyle(22.sp, FontWeight.SemiBold))
            Text(
                "Press any button to wake. To switch the box and TV fully off, turn on " +
                    "\"Home button and sleep\" in Settings.",
                style = dzStyle(13.sp, color = Dz.Ink2),
                textAlign = TextAlign.Center,
            )
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focus.safeRequest()
    }
}
