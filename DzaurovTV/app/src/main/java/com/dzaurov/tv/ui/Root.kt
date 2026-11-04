package com.dzaurov.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dzaurov.tv.dz
import com.dzaurov.tv.ui.components.AppMenu
import com.dzaurov.tv.ui.components.BackdropLayer
import com.dzaurov.tv.ui.components.BackdropState
import com.dzaurov.tv.ui.components.Header
import com.dzaurov.tv.ui.components.LaunchOverlay
import com.dzaurov.tv.ui.components.LocalBackdrop
import com.dzaurov.tv.ui.components.PromptDialog
import com.dzaurov.tv.ui.components.Toast
import com.dzaurov.tv.ui.components.rememberNow
import com.dzaurov.tv.ui.screens.AmbientScreen
import com.dzaurov.tv.ui.screens.AppsScreen
import com.dzaurov.tv.ui.screens.ControlCenter
import com.dzaurov.tv.ui.screens.HiddenAppsScreen
import com.dzaurov.tv.ui.components.FpsMeter
import com.dzaurov.tv.ui.screens.HomeScreen
import com.dzaurov.tv.ui.screens.PhotosScreen
import com.dzaurov.tv.ui.screens.SearchScreen
import com.dzaurov.tv.ui.screens.SettingsScreen
import com.dzaurov.tv.ui.screens.SleepScreen

@Composable
fun Root(vm: MainViewModel, onBackOnHome: () -> Unit) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val net by vm.network.collectAsStateWithLifecycle()
    val menu by vm.menu.collectAsStateWithLifecycle()
    val prompt by vm.prompt.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val launching by vm.launchAnim.collectAsStateWithLifecycle()
    val controlOpen by vm.controlOpen.collectAsStateWithLifecycle()
    val timerEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val now by rememberNow()
    val backdrop = remember { BackdropState() }

    // "Auto" picks the picture by time of day; any other theme is used as is.
    val theme = vm.backgrounds.resolve(prefs.theme, now.hour)

    // Decode the chosen background once, off the main thread.
    LaunchedEffect(prefs.loaded, theme, prefs.customVersion) {
        if (prefs.loaded) backdrop.backdrop = vm.backgrounds.load(theme, prefs.customVersion)
    }
    LaunchedEffect(screen) {
        if (screen != Screen.Home) backdrop.parallax.animateTo(0f, spring(dampingRatio = 1f, stiffness = 60f))
    }


    BackHandler {
        if (!vm.onBack()) onBackOnHome()
    }

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged {
                    backdrop.screenW = it.width.toFloat()
                    backdrop.screenH = it.height.toFloat()
                }
                .onFocusChanged { vm.rootHasFocus = it.hasFocus }
        ) {
            val ambient = screen == Screen.Ambient
            val lively = prefs.liveBg && !prefs.performance && (screen == Screen.Home || ambient)
            BackdropLayer(
                state = backdrop,
                dim = when (screen) {
                    Screen.Home, Screen.Ambient -> 0f
                    Screen.Sleep -> 1f
                    else -> 0.55f
                },
                atmosphere = lively,
                particles = prefs.particles,
                kenBurns = ambient && !prefs.performance,
            )

            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(280)) + scaleIn(tween(360), initialScale = 0.965f)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(220), targetScale = 1.02f)) using null
                },
                label = "screen",
                modifier = Modifier.fillMaxSize(),
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(vm, prefs.performance, now)
                    Screen.Apps -> AppsScreen(vm, prefs.performance)
                    Screen.Search -> SearchScreen(vm, prefs.performance)
                    Screen.Settings -> SettingsScreen(vm)
                    Screen.HiddenApps -> HiddenAppsScreen(vm, prefs.performance)
                    Screen.Photos -> PhotosScreen(vm)
                    Screen.Ambient -> AmbientScreen(vm, now, weather)
                    Screen.Sleep -> SleepScreen(vm)
                }
            }

            if (screen != Screen.Ambient && screen != Screen.Sleep) {
                Header(
                    screen = screen,
                    weather = weather,
                    net = net,
                    now = now,
                    onTab = { vm.go(it) },
                    onAmbient = { vm.go(Screen.Ambient) },
                    onWifi = { vm.openWifi(context) },
                    onControl = { vm.openControl() },
                    sleepTimerEndsAt = timerEndsAt,
                )
            }

            Toast(message)

            if (prefs.showFps) FpsMeter(Modifier.align(Alignment.BottomStart))

            launching?.let { LaunchOverlay(it) }

            menu?.let { m ->
                AppMenu(
                    state = m,
                    isFavorite = vm.isFavorite(m.app),
                    onDismiss = { vm.menu.value = null },
                    onAction = { vm.onMenuAction(context, it) },
                )
            }
            if (controlOpen) {
                ControlCenter(vm = vm, now = now, onDismiss = { vm.closeControl() })
            }
            prompt?.let { p ->
                PromptDialog(
                    prompt = p,
                    onDismiss = { vm.prompt.value = null },
                    onConfirmReset = { vm.resetLayout() },
                )
            }
        }
    }
}
