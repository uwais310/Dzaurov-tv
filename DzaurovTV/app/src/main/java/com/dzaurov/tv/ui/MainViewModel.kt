package com.dzaurov.tv.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dzaurov.tv.DzaurovApp
import com.dzaurov.tv.data.AppEntry
import com.dzaurov.tv.data.Photo
import com.dzaurov.tv.data.Photos
import com.dzaurov.tv.data.UiPrefs
import com.dzaurov.tv.data.WatchNextItem
import com.dzaurov.tv.data.WatchNextRepository
import com.dzaurov.tv.data.WatchNextStatus
import com.dzaurov.tv.system.SleepController
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { Home, Apps, Search, Settings, HiddenApps, Photos, Ambient, Sleep }

enum class MenuSource { Favorites, Recent, Grid, Hidden }

data class MenuState(val app: AppEntry, val source: MenuSource)

sealed interface Prompt {
    data object Reset : Prompt
    data class Info(val title: String, val body: String) : Prompt
}

/**
 * What has focus on Home: a favorite, a Continue watching card ([program] set),
 * a recent app, or the "Add apps" tile (app == null).
 */
data class HomeFocus(
    val key: String,
    val app: AppEntry?,
    val recentAt: Long?,
    val program: WatchNextItem? = null,
)

/** An app opening with a zoom out of its tile (iOS style). */
data class LaunchAnim(val app: AppEntry, val from: Rect, val color: Color, val id: Long)

const val ADD_KEY = "__add"
fun recentKey(pkg: String) = "r:$pkg"
fun watchKey(id: Long) = "w:$id"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val dz = application as DzaurovApp
    val backgrounds = dz.backgrounds
    val weather = dz.weather.weather
    val network = dz.network.state
    val watchNextStatus: StateFlow<WatchNextStatus> = dz.watchNext.status

    /** Asks the activity to show Android's permission dialog for Continue watching. */
    val permissionRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)

    val prefs: StateFlow<UiPrefs> = dz.prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UiPrefs())
    private val apps = dz.apps.apps

    /** While reordering, the new order lives here so every press moves the tile instantly. */
    private val editOrder = MutableStateFlow<List<String>?>(null)

    val ready: StateFlow<Boolean> = combine(apps, prefs) { a, p -> a != null && p.loaded && p.seeded }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val allApps: StateFlow<List<AppEntry>> = apps.map { it ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleApps: StateFlow<List<AppEntry>> = combine(allApps, prefs) { a, p -> a.filter { it.packageName !in p.hidden } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenApps: StateFlow<List<AppEntry>> = combine(allApps, prefs) { a, p -> a.filter { it.packageName in p.hidden } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val favorites: StateFlow<List<AppEntry>> = combine(allApps, prefs, editOrder) { a, p, eo ->
        val byPkg = a.associateBy { it.packageName }
        (eo ?: p.favorites).mapNotNull { byPkg[it] }.filter { it.packageName !in p.hidden }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recents: StateFlow<List<Pair<AppEntry, Long>>> = combine(allApps, prefs) { a, p ->
        val byPkg = a.associateBy { it.packageName }
        p.recents.mapNotNull { r -> byPkg[r.pkg]?.takeIf { it.packageName !in p.hidden }?.let { it to r.at } }.take(10)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Continue watching: system Watch Next entries for installed, non-hidden apps. */
    val watchNext: StateFlow<List<Pair<WatchNextItem, AppEntry?>>> = combine(dz.watchNext.items, allApps, prefs) { w, a, p ->
        val byPkg = a.associateBy { it.packageName }
        w.filter { it.pkg !in p.hidden }.map { it to byPkg[it.pkg] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val query = MutableStateFlow("")
    val searchResults: StateFlow<List<AppEntry>> = combine(allApps, query) { a, q ->
        val t = q.trim()
        if (t.isEmpty()) a else a.filter { it.label.contains(t, ignoreCase = true) || it.packageName.contains(t, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val screen = MutableStateFlow(Screen.Home)

    /** Sleep helper (accessibility service) state and the sleep timer. */
    val sleepServiceOn: StateFlow<Boolean> = SleepController.serviceOn
    val sleepTimerEndsAt: StateFlow<Long> = SleepController.timerEndsAt

    /**
     * Keep the screen on during Ambient only when we can also switch it off ourselves;
     * otherwise the box's own screensaver and sleep timers stay in charge.
     */
    val keepAwake: StateFlow<Boolean> = combine(screen, SleepController.serviceOn) { s, on -> on && s == Screen.Ambient }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val launchAnim = MutableStateFlow<LaunchAnim?>(null)
    val controlOpen = MutableStateFlow(false)
    val homeFocus = MutableStateFlow<HomeFocus?>(null)
    val editing = MutableStateFlow<String?>(null)
    val menu = MutableStateFlow<MenuState?>(null)
    val prompt = MutableStateFlow<Prompt?>(null)
    val message = MutableStateFlow<String?>(null)
    val importing = MutableStateFlow(false)

    /** Key of the Home tile that should take focus next (consumed by HomeScreen). */
    val pendingHomeFocus = MutableStateFlow<String?>(null)

    /** Fired when a key arrives while nothing has focus; the visible screen re-focuses itself. */
    val focusRestore = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile var rootHasFocus = false
    @Volatile var resumed = false
    private var lastInput = SystemClock.uptimeMillis()
    private var lastHomeKey: String? = null
    private var ambientExitAt = 0L
    private var ambientSince = 0L
    private var messageJob: Job? = null

    init {
        viewModelScope.launch {
            if (!dz.prefs.flow.first().seeded) {
                val list = apps.filterNotNull().first()
                dz.prefs.seedFavorites(seedList(list))
            }
        }
        // Idle: screen -> Ambient screensaver after a while, then sleep.
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                val p = prefs.value
                val now = SystemClock.uptimeMillis()
                val idle = now - lastInput
                val calm = resumed && menu.value == null && editing.value == null && prompt.value == null &&
                    !controlOpen.value && launchAnim.value == null
                if (!calm) continue
                when (screen.value) {
                    Screen.Ambient ->
                        if (p.sleepMin > 0 && now - ambientSince >= p.sleepMin * 60_000L) sleepNow(auto = true)
                    Screen.Sleep, Screen.Photos -> Unit
                    else -> when {
                        p.ambientMin > 0 && idle >= p.ambientMin * 60_000L -> go(Screen.Ambient)
                        p.ambientMin == 0 && p.sleepMin > 0 && idle >= p.sleepMin * 60_000L -> sleepNow(auto = true)
                    }
                }
            }
        }
    }

    // ---------- navigation & focus ----------

    fun onUserActivity() {
        lastInput = SystemClock.uptimeMillis()
    }

    fun go(target: Screen) {
        menu.value = null
        if (editing.value != null) endEdit()
        if (target == Screen.Home) pendingHomeFocus.value = defaultHomeKey()
        if (target == Screen.Ambient) ambientSince = SystemClock.uptimeMillis()
        screen.value = target
    }

    /** Back from another app or from sleep: drop leftovers so Home looks fresh. */
    fun onResumed() {
        launchAnim.value = null
        onUserActivity()
        if (screen.value == Screen.Sleep) go(Screen.Home)
    }

    fun goHome() {
        prompt.value = null
        go(Screen.Home)
    }

    fun exitAmbient() {
        ambientExitAt = SystemClock.uptimeMillis()
        onUserActivity()
        go(Screen.Home)
    }

    /** Returns false only on Home with nothing open: the activity then decides what Back means. */
    fun onBack(): Boolean {
        if (SystemClock.uptimeMillis() - ambientExitAt < 700) return true
        when {
            prompt.value != null -> prompt.value = null
            controlOpen.value -> controlOpen.value = false
            menu.value != null -> menu.value = null
            editing.value != null -> endEdit()
            screen.value == Screen.Photos || screen.value == Screen.HiddenApps -> screen.value = Screen.Settings
            screen.value != Screen.Home -> go(Screen.Home)
            else -> {
                pendingHomeFocus.value = favorites.value.firstOrNull()?.packageName ?: ADD_KEY
                return false
            }
        }
        return true
    }

    fun onHomeFocus(f: HomeFocus) {
        homeFocus.value = f
        lastHomeKey = f.key
    }

    fun defaultHomeKey(): String {
        val favs = favorites.value
        val last = lastHomeKey
        val valid = when {
            last == null -> false
            last == ADD_KEY -> true
            last.startsWith("r:") -> recents.value.any { recentKey(it.first.packageName) == last }
            last.startsWith("w:") -> watchNext.value.any { watchKey(it.first.id) == last }
            else -> favs.any { it.packageName == last }
        }
        return if (valid && last != null) last else favs.firstOrNull()?.packageName ?: ADD_KEY
    }

    fun focusHomeDefault() {
        pendingHomeFocus.value = defaultHomeKey()
    }

    fun consumePendingHomeFocus() {
        pendingHomeFocus.value = null
    }

    fun requestFocusRestore() {
        focusRestore.tryEmit(Unit)
    }

    // ---------- apps ----------

    fun open(context: Context, app: AppEntry): Boolean {
        val ok = dz.apps.launch(context, app)
        if (ok) {
            viewModelScope.launch { dz.prefs.addRecent(app.packageName) }
        } else {
            launchAnim.value = null
            say("Couldn't open ${app.label}")
        }
        return ok
    }

    /** Opens an app with the tile zooming to full screen first (skipped in Performance mode). */
    fun launchFrom(context: Context, app: AppEntry, from: Rect, color: Color) {
        if (prefs.value.performance || launchAnim.value != null) {
            open(context, app)
            return
        }
        val id = SystemClock.uptimeMillis()
        launchAnim.value = LaunchAnim(app, from, color, id)
        viewModelScope.launch {
            delay(LAUNCH_DELAY_MS)
            open(context, app)
            delay(1500)
            if (launchAnim.value?.id == id) launchAnim.value = null
        }
    }

    /** Opens the exact episode/movie when the app gave a link, otherwise just the app. */
    fun openWatchNext(context: Context, item: WatchNextItem, app: AppEntry?) {
        val deep = item.intentUri?.let { uri ->
            runCatching { Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull()
        }
        if (deep != null && runCatching { context.startActivity(deep) }.isSuccess) {
            viewModelScope.launch { dz.prefs.addRecent(item.pkg) }
            return
        }
        if (app != null) open(context, app) else say("Couldn't open ${item.title}")
    }

    fun refreshWatchNext() {
        dz.watchNext.refresh()
    }

    /** True only when the user asked from Settings, so the first-start dialog never nags. */
    private var watchNextAskedByUser = false

    fun onWatchNextPermission(granted: Boolean) {
        dz.watchNext.refresh()
        val explain = watchNextAskedByUser
        watchNextAskedByUser = false
        if (explain && !granted && !dz.watchNext.hasPermission()) {
            prompt.value = Prompt.Info("Turn on Continue watching", WATCH_NEXT_HELP)
        }
    }

    /** Settings row: ask Android first; if it can't ask, explain the one-time ADB command. */
    fun enableWatchNext() {
        if (dz.watchNext.hasPermission()) {
            dz.watchNext.refresh()
            say(if (watchNext.value.isEmpty()) "Nothing to continue yet" else "Continue watching is on")
        } else {
            watchNextAskedByUser = true
            permissionRequests.tryEmit(WatchNextRepository.PERMISSION)
        }
    }

    // ---------- sleep ----------

    /**
     * Puts the box to sleep through the helper service (the TV follows over HDMI-CEC).
     * Without the service: a black screen, and the box's own sleep timer takes over.
     */
    fun sleepNow(auto: Boolean = false) {
        menu.value = null
        controlOpen.value = false
        prompt.value = null
        if (editing.value != null) endEdit()
        pendingHomeFocus.value = defaultHomeKey()
        if (SleepController.lockNow()) {
            // Wake up on Home, not on the screensaver.
            screen.value = Screen.Home
        } else {
            screen.value = Screen.Sleep
            if (!auto) say("Turn on the sleep helper so the box can switch off")
        }
    }

    /** Minutes left before the screensaver puts the box to sleep, or null when it won't. */
    fun minutesUntilAutoSleep(): Int? {
        val p = prefs.value
        if (screen.value != Screen.Ambient || p.sleepMin <= 0) return null
        val passed = (SystemClock.uptimeMillis() - ambientSince) / 60_000L
        return (p.sleepMin - passed).toInt().coerceAtLeast(1)
    }

    /** Off -> 15 -> 30 -> 60 -> 90 -> 120 min -> Off. Works while you watch other apps. */
    fun cycleSleepTimer(context: Context) {
        if (!SleepController.serviceOn.value) {
            prompt.value = Prompt.Info("Turn on the sleep helper", HELPER_HELP)
            return
        }
        val ends = SleepController.timerEndsAt.value
        val left = if (ends == 0L) 0L else ((ends - System.currentTimeMillis()) / 60_000L) + 1
        val next = TIMER_STEPS.firstOrNull { it > left } ?: 0
        if (next == 0) {
            SleepController.cancelTimer()
            say("Sleep timer off")
        } else {
            SleepController.startTimer(context, dz.appScope, next)
            say("Going to sleep in $next min")
        }
    }

    fun cycleAmbientAfter() {
        val next = AMBIENT_STEPS.after(prefs.value.ambientMin)
        viewModelScope.launch { dz.prefs.setAmbientMin(next) }
        say(if (next == 0) "Screensaver off" else "Screensaver after $next min")
    }

    fun cycleSleepAfter() {
        val next = SLEEP_STEPS.after(prefs.value.sleepMin)
        viewModelScope.launch { dz.prefs.setSleepMin(next) }
        say(if (next == 0) "The box won't sleep on its own" else "Sleep after $next min of screensaver")
    }

    fun setNavSounds(on: Boolean) {
        viewModelScope.launch { dz.prefs.setNavSounds(on) }
    }

    fun setHomeOverride(on: Boolean) {
        viewModelScope.launch { dz.prefs.setHomeOverride(on) }
        if (on && !SleepController.serviceOn.value) prompt.value = Prompt.Info("Turn on the helper", HELPER_HELP)
    }

    /** Opens Android's accessibility list, where the helper is switched on. */
    fun openHelperSettings(context: Context) {
        val ok = startFirst(context, listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
        if (!ok) prompt.value = Prompt.Info("Turn on the helper", HELPER_HELP)
    }

    fun showHelperInfo() {
        prompt.value = Prompt.Info("Home button and sleep", HELPER_HELP)
    }

    // ---------- control center ----------

    fun openControl() {
        menu.value = null
        controlOpen.value = true
    }

    fun closeControl() {
        controlOpen.value = false
    }

    /** Theme order for quick switching: Auto, every picture, then your own photo if set. */
    fun cycleTheme() {
        val order = buildList {
            add("auto")
            addAll(backgrounds.themes)
            if (backgrounds.hasCustom()) add("custom")
        }
        val next = order.after(prefs.value.theme)
        setTheme(next)
        say("Theme: ${backgrounds.label(next)}")
    }

    fun openBluetooth(context: Context) = openSystem(context, Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")

    fun openDisplay(context: Context) = openSystem(context, Settings.ACTION_DISPLAY_SETTINGS, "Picture")

    fun openSound(context: Context) = openSystem(context, Settings.ACTION_SOUND_SETTINGS, "Sound")

    private fun openSystem(context: Context, action: String, name: String) {
        val ok = startFirst(context, listOf(Intent(action), Intent(Settings.ACTION_SETTINGS)))
        if (!ok) say("$name settings aren't available on this box")
    }

    // ---------- system ----------

    fun openWifi(context: Context) {
        val tries = listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        if (!startFirst(context, tries)) say("Wi-Fi settings aren't available on this box")
    }

    fun openSystemSettings(context: Context) {
        val tries = buildList {
            add(Intent(Settings.ACTION_SETTINGS))
            context.packageManager.getLaunchIntentForPackage(TV_SETTINGS_PKG)?.let { add(it) }
            context.packageManager.getLeanbackLaunchIntentForPackage(TV_SETTINGS_PKG)?.let { add(it) }
        }
        if (!startFirst(context, tries)) say("System settings aren't available on this box")
    }

    private fun startFirst(context: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            if (runCatching { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return false
    }

    fun showMenu(app: AppEntry, source: MenuSource) {
        menu.value = MenuState(app, source)
    }

    fun isFavorite(app: AppEntry) = app.packageName in prefs.value.favorites

    fun onMenuAction(context: Context, action: MenuAction) {
        val m = menu.value ?: return
        menu.value = null
        val app = m.app
        when (action) {
            MenuAction.Open -> open(context, app)
            MenuAction.Move -> startEdit(app)
            MenuAction.AddHome -> addFavorite(app)
            MenuAction.RemoveHome -> removeFavorite(app)
            MenuAction.RemoveRecent -> removeRecent(app)
            MenuAction.Hide -> hide(app)
            MenuAction.Unhide -> unhide(app)
            MenuAction.Info -> appInfo(context, app)
        }
    }

    private fun addFavorite(app: AppEntry) {
        viewModelScope.launch {
            val current = prefs.value.favorites
            if (app.packageName !in current) dz.prefs.setFavorites(current + app.packageName)
            say("${app.label} added to Home")
        }
    }

    private fun removeFavorite(app: AppEntry) {
        val favs = favorites.value
        val i = favs.indexOfFirst { it.packageName == app.packageName }
        val neighbor = favs.getOrNull(i + 1)?.packageName ?: favs.getOrNull(i - 1)?.packageName ?: ADD_KEY
        viewModelScope.launch {
            dz.prefs.setFavorites(prefs.value.favorites.filter { it != app.packageName })
            if (screen.value == Screen.Home) pendingHomeFocus.value = neighbor
            say("${app.label} removed from Home")
        }
    }

    private fun removeRecent(app: AppEntry) {
        viewModelScope.launch {
            dz.prefs.removeRecent(app.packageName)
            if (screen.value == Screen.Home) pendingHomeFocus.value = favorites.value.firstOrNull()?.packageName ?: ADD_KEY
        }
    }

    private fun hide(app: AppEntry) {
        viewModelScope.launch {
            dz.prefs.hide(app.packageName)
            say("${app.label} hidden · Settings → Hidden apps brings it back")
        }
    }

    fun unhide(app: AppEntry) {
        viewModelScope.launch {
            dz.prefs.unhide(app.packageName)
            say("${app.label} is visible again")
        }
    }

    private fun appInfo(context: Context, app: AppEntry) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isFailure) say("App info isn't available on this box")
    }

    // ---------- reordering ----------

    private fun startEdit(app: AppEntry) {
        editOrder.value = favorites.value.map { it.packageName }
        editing.value = app.packageName
        say("◀ ▶ to move · OK to drop")
    }

    fun moveEditing(delta: Int) {
        val key = editing.value ?: return
        val order = editOrder.value ?: return
        val i = order.indexOf(key)
        if (i < 0) return
        val j = (i + delta).coerceIn(0, order.lastIndex)
        if (i == j) return
        editOrder.value = order.toMutableList().apply {
            removeAt(i)
            add(j, key)
        }
    }

    fun endEdit() {
        val order = editOrder.value
        editing.value = null
        if (order == null) return
        viewModelScope.launch {
            val rest = prefs.value.favorites.filter { it !in order }
            dz.prefs.setFavorites(order + rest)
            editOrder.value = null
            say("Order saved")
        }
    }

    // ---------- settings ----------

    fun setTheme(theme: String) {
        viewModelScope.launch { dz.prefs.setTheme(theme) }
    }

    fun setParticles(on: Boolean) {
        viewModelScope.launch { dz.prefs.setParticles(on) }
    }

    fun setPerformance(on: Boolean) {
        viewModelScope.launch { dz.prefs.setPerformance(on) }
    }

    fun askReset() {
        prompt.value = Prompt.Reset
    }

    fun resetLayout() {
        prompt.value = null
        viewModelScope.launch {
            dz.prefs.resetLayout()
            dz.prefs.seedFavorites(seedList(allApps.value))
            lastHomeKey = null
            say("Layout reset")
        }
    }

    fun openHomeSettings(context: Context) {
        val ok = runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok) prompt.value = Prompt.Info("Make Dzaurov TV the Home app", HOME_HELP)
    }

    fun openScreensaverSettings(context: Context) {
        val ok = runCatching { context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok) prompt.value = Prompt.Info("Use Ambient as the screensaver", DREAM_HELP)
    }

    fun showInfo(title: String, body: String) {
        prompt.value = Prompt.Info(title, body)
    }

    fun importPhoto(context: Context, photo: Photo) {
        if (importing.value) return
        importing.value = true
        viewModelScope.launch {
            val ok = Photos.import(context.applicationContext, photo.uri, backgrounds.customFile())
            importing.value = false
            if (ok) {
                dz.prefs.setCustomTheme(System.currentTimeMillis())
                say("Background updated")
                screen.value = Screen.Settings
            } else {
                say("Couldn't open that photo")
            }
        }
    }

    fun say(text: String) {
        message.value = text
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            delay(2400)
            message.value = null
        }
    }

    private fun seedList(list: List<AppEntry>): List<String> {
        val installed = list.map { it.packageName }.toSet()
        val known = KNOWN_APPS.filter { it in installed }
        if (known.isNotEmpty()) return known.take(6)
        return list.filter { it.leanback && it.packageName !in SYSTEM_APPS }.take(5).map { it.packageName }
    }

    companion object {
        const val IDLE_MS = 10 * 60 * 1000L
        const val LAUNCH_DELAY_MS = 260L
        val TIMER_STEPS = listOf(15, 30, 60, 90, 120)
        val AMBIENT_STEPS = listOf(2, 5, 10, 15, 30, 0)
        val SLEEP_STEPS = listOf(15, 30, 60, 120, 0)

        /** Next item after [current], wrapping around; the first one if [current] isn't listed. */
        fun <T> List<T>.after(current: T): T = this[(indexOf(current) + 1) % size]

        const val HELPER_HELP = "One switch lets Dzaurov TV (1) open when you press the remote's Home button " +
            "and (2) put the box to sleep: after the screensaver, from the sleep timer, or with Sleep now.\n\n" +
            "On the box: Settings → Device Preferences → Accessibility → \"Dzaurov TV · Home button and sleep\" → On.\n\n" +
            "Or once from a computer with ADB:\n" +
            "adb shell settings put secure enabled_accessibility_services " +
            "com.dzaurov.tv/com.dzaurov.tv.system.DzAccessibilityService\n" +
            "adb shell settings put secure accessibility_enabled 1\n\n" +
            "It only notices which app opens; it never reads what's on the screen. " +
            "The TV switches off with the box when HDMI-CEC (Anynet+) is on."
        private const val TV_SETTINGS_PKG = "com.android.tv.settings"

        val WATCH_NEXT_HELP = "Android only shares the \"keep watching\" list with apps it trusts.\n\n" +
            "One-time fix from a computer with ADB (same Wi-Fi as the box):\n" +
            WatchNextRepository.ADB_GRANT + "\n\n" +
            "Undo: replace \"grant\" with \"revoke\".\n\n" +
            "Until then Home shows your Recent apps instead. Some apps (Netflix) only share the list with " +
            "Google's own home screen, so they may stay missing."

        private val KNOWN_APPS = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "ru.kinopoisk.tv",
            "ru.kinopoisk",
            "ru.ivi.client",
            "ru.more.play",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.youtube.tvmusic",
        )
        private val SYSTEM_APPS = setOf(
            "com.android.tv.settings",
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
        )

        const val HOME_HELP = "Android TV has no menu for choosing a home app.\n\n" +
            "Easiest: install Button Mapper from the Play Store and set Home → Dzaurov TV.\n\n" +
            "Or from a computer with ADB:\n" +
            "adb shell cmd package set-home-activity com.dzaurov.tv/.MainActivity\n\n" +
            "The install guide has the undo command for each step."

        const val DREAM_HELP = "Open Settings → Device Preferences → Screen saver and pick Dzaurov Ambient.\n\n" +
            "If it isn't listed, from a computer with ADB:\n" +
            "adb shell settings put secure screensaver_components com.dzaurov.tv/.dream.AmbientDreamService"
    }
}

enum class MenuAction { Open, Move, AddHome, RemoveHome, RemoveRecent, Hide, Unhide, Info }
