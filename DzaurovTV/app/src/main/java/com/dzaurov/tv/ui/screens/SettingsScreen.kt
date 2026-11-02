package com.dzaurov.tv.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.dzaurov.tv.BuildConfig
import com.dzaurov.tv.data.WatchNextStatus
import com.dzaurov.tv.ui.MainViewModel
import com.dzaurov.tv.ui.Screen
import com.dzaurov.tv.ui.components.DzIcons
import com.dzaurov.tv.ui.components.PageTitle
import com.dzaurov.tv.ui.components.focusRing
import com.dzaurov.tv.ui.components.glass
import com.dzaurov.tv.ui.components.safeRequest
import com.dzaurov.tv.ui.components.tvClickable
import com.dzaurov.tv.ui.theme.Dz
import com.dzaurov.tv.ui.theme.dzStyle

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val hidden by vm.hiddenApps.collectAsStateWithLifecycle()
    val net by vm.network.collectAsStateWithLifecycle()
    val cwStatus by vm.watchNextStatus.collectAsStateWithLifecycle()
    val helperOn by vm.sleepServiceOn.collectAsStateWithLifecycle()
    val timerEndsAt by vm.sleepTimerEndsAt.collectAsStateWithLifecycle()
    val hour = remember { java.time.LocalTime.now().hour }
    val first = remember { FocusRequester() }
    val hasCustom = remember(prefs.customVersion) { vm.backgrounds.hasCustom() }

    Column(Modifier.fillMaxSize().padding(top = 78.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle("Settings", null)
        LazyColumn(
            contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 8.dp, bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.width(640.dp),
        ) {
            item {
              Column {
                SectionLabel("Theme")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    (listOf("auto") + vm.backgrounds.themes).forEachIndexed { i, theme ->
                        ThemeSwatch(
                            vm = vm,
                            theme = theme,
                            thumbTheme = vm.backgrounds.resolve(theme, hour),
                            label = vm.backgrounds.label(theme),
                            selected = prefs.theme == theme,
                            version = 0L,
                            onClick = { vm.setTheme(theme) },
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    if (hasCustom) {
                        ThemeSwatch(
                            vm = vm,
                            theme = "custom",
                            thumbTheme = "custom",
                            label = "My photo",
                            selected = prefs.theme == "custom",
                            version = prefs.customVersion,
                            onClick = { vm.setTheme("custom") },
                        )
                    }
                    PickPhotoSwatch(onClick = { vm.go(Screen.Photos) })
                }
              }
            }
            item { SectionLabel("Home screen", Modifier.padding(top = 10.dp)) }
            item {
                SettingRow(
                    icon = DzIcons.Sparkle,
                    title = "Particles",
                    subtitle = "Small floating lights over the background",
                    value = if (prefs.particles) "On" else "Off",
                    onClick = { vm.setParticles(!prefs.particles) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Bolt,
                    title = "Performance mode",
                    subtitle = "Turns off fog, particles and glow. Use it if the box feels slow.",
                    value = if (prefs.performance) "On" else "Off",
                    onClick = { vm.setPerformance(!prefs.performance) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Play,
                    title = "Continue watching",
                    subtitle = when (cwStatus) {
                        WatchNextStatus.Blocked -> "Android hasn't allowed it yet · press OK to fix"
                        WatchNextStatus.Empty -> "Allowed · apps haven't shared anything to continue yet"
                        else -> "Movies and episodes you started, from Netflix, YouTube and others"
                    },
                    value = when (cwStatus) {
                        WatchNextStatus.Ok, WatchNextStatus.Empty -> "On"
                        WatchNextStatus.Blocked -> "Off"
                        WatchNextStatus.Unknown -> null
                    },
                    onClick = { vm.enableWatchNext() },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.EyeOff,
                    title = "Hidden apps",
                    subtitle = "Apps you hid from All apps",
                    value = "${hidden.size}",
                    onClick = { vm.go(Screen.HiddenApps) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Reset,
                    title = "Reset layout",
                    subtitle = "Favorites, order, Recent and hidden apps back to the start",
                    value = null,
                    onClick = { vm.askReset() },
                )
            }
            item { SectionLabel("Screensaver and sleep", Modifier.padding(top = 10.dp)) }
            item {
                SettingRow(
                    icon = DzIcons.Moon,
                    title = "Screensaver after",
                    subtitle = "Big clock and slowly moving background when nobody uses the TV",
                    value = if (prefs.ambientMin == 0) "Off" else "${prefs.ambientMin} min",
                    onClick = { vm.cycleAmbientAfter() },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Sleep,
                    title = "Sleep after screensaver",
                    subtitle = if (helperOn) "Then the box sleeps and the TV switches off with it" else "Then the screen goes dark · turn on the helper below for full sleep",
                    value = if (prefs.sleepMin == 0) "Never" else "${prefs.sleepMin} min",
                    onClick = { vm.cycleSleepAfter() },
                )
            }
            item {
                val left = if (timerEndsAt == 0L) 0L else ((timerEndsAt - System.currentTimeMillis()) / 60_000L) + 1
                SettingRow(
                    icon = DzIcons.Timer,
                    title = "Sleep timer",
                    subtitle = "Sleeps even while a film plays · press again for 15, 30, 60, 90, 120 min",
                    value = if (left == 0L) "Off" else "$left min",
                    onClick = { vm.cycleSleepTimer(context) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Hand,
                    title = "Home button and sleep helper",
                    subtitle = if (helperOn) "On · Home opens Dzaurov TV, sleep switches the box off" else "Off · press OK, then switch on \"Dzaurov TV\" in Accessibility",
                    value = if (helperOn) "On" else "Off",
                    onClick = { if (helperOn) vm.showHelperInfo() else vm.openHelperSettings(context) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Home,
                    title = "Home button opens Dzaurov TV",
                    subtitle = "Instead of the old home screen (needs the helper above)",
                    value = if (prefs.homeOverride) "On" else "Off",
                    onClick = { vm.setHomeOverride(!prefs.homeOverride) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Speaker,
                    title = "Navigation sounds",
                    subtitle = "Soft clicks when you move around, like Apple TV",
                    value = if (prefs.navSounds) "On" else "Off",
                    onClick = { vm.setNavSounds(!prefs.navSounds) },
                )
            }
            item { SectionLabel("System", Modifier.padding(top = 10.dp)) }
            item {
                SettingRow(
                    icon = DzIcons.network(net.kind, net.bars),
                    title = "Wi-Fi & network",
                    subtitle = "Choose a network, enter a password, check the connection",
                    value = net.label,
                    onClick = { vm.openWifi(context) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Cog,
                    title = "Android TV settings",
                    subtitle = "Picture, sound, Bluetooth remotes, apps, storage, updates",
                    value = null,
                    onClick = { vm.openSystemSettings(context) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Home,
                    title = "Make Dzaurov TV the Home app",
                    subtitle = "So the remote's Home button opens it",
                    value = null,
                    onClick = { vm.openHomeSettings(context) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Moon,
                    title = "System screensaver",
                    subtitle = "Use the Ambient clock as Android's own screensaver too",
                    value = null,
                    onClick = { vm.openScreensaverSettings(context) },
                )
            }
            item {
                SettingRow(
                    icon = DzIcons.Info,
                    title = "About",
                    subtitle = "Version ${BuildConfig.VERSION_NAME} · no ads, no tracking, no analytics",
                    value = null,
                    onClick = {
                        vm.showInfo(
                            "Dzaurov TV ${BuildConfig.VERSION_NAME}",
                            "A home screen for your Homatics box.\n\n" +
                                "It only goes online to fetch Almaty weather from Open-Meteo (open-meteo.com, CC BY 4.0). " +
                                "No accounts, ads, tracking or analytics. Everything you set stays on this box.\n\n" +
                                "Font: Geist (SIL Open Font License).",
                        )
                    },
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.safeRequest()
    }
    LaunchedEffect(Unit) {
        vm.focusRestore.collect { first.safeRequest() }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = dzStyle(10.sp, FontWeight.SemiBold, Dz.Ink2, spacing = 2.sp), modifier = modifier)
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.03f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "rowScale")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "rowRing")
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .glass(16.dp)
            .drawBehind { focusRing(ring.value, 16.dp.toPx()) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Dz.Ink, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = dzStyle(14.sp, FontWeight.Medium))
            Text(subtitle, style = dzStyle(11.sp, color = Dz.Ink2), maxLines = 1)
        }
        if (value != null) {
            Box(
                Modifier
                    .background(if (value == "On") Color.White else Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(value, style = dzStyle(12.sp, FontWeight.SemiBold, if (value == "On") Dz.Dark else Dz.Ink))
            }
        }
    }
}

@Composable
private fun ThemeSwatch(
    vm: MainViewModel,
    theme: String,
    thumbTheme: String,
    label: String,
    selected: Boolean,
    version: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumb by produceState<ImageBitmap?>(null, thumbTheme, version) { value = vm.backgrounds.thumbnail(thumbTheme) }
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "swatch")
    val ring = animateFloatAsState(if (focused || selected) 1f else 0f, tween(160), label = "swatchRing")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier
                .size(96.dp, 54.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onClick = onClick)
                .drawBehind {
                    focusRing(ring.value * if (focused) 1f else 0.55f, 10.dp.toPx())
                }
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1F28)),
        ) {
            thumb?.let {
                Image(it, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.Medium, modifier = Modifier.fillMaxSize())
            }
        }
        Text(label + if (selected) " ✓" else "", style = dzStyle(11.sp, if (selected) FontWeight.SemiBold else FontWeight.Normal, if (selected) Dz.Ink else Dz.Ink2))
    }
}

@Composable
private fun PickPhotoSwatch(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = animateFloatAsState(if (focused) 1.08f else 1f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "pick")
    val ring = animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "pickRing")
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(96.dp, 54.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .onFocusChanged { focused = it.isFocused }
                .tvClickable(onClick = onClick)
                .drawBehind { focusRing(ring.value, 10.dp.toPx()) }
                .clip(RoundedCornerShape(10.dp))
                .glass(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(DzIcons.Image, contentDescription = null, tint = Dz.Ink, modifier = Modifier.size(20.dp))
        }
        Text("Choose photo", style = dzStyle(11.sp, color = Dz.Ink2))
    }
}
