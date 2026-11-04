package com.dzaurov.tv.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "dzaurov")

data class Recent(val pkg: String, val at: Long)

data class UiPrefs(
    val theme: String = DEFAULT_THEME,
    val customVersion: Long = 0L,
    val particles: Boolean = true,
    val performance: Boolean = false,
    /** Drifting fog and floating lights. Off by default: it redraws the whole screen all the time. */
    val liveBg: Boolean = false,
    /** Small frame-rate meter in the corner, to see how smooth the box really is. */
    val showFps: Boolean = false,
    val favorites: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val recents: List<Recent> = emptyList(),
    val seeded: Boolean = false,
    val loaded: Boolean = false,
    /** Minutes idle on Home before the Ambient screensaver; 0 = never. */
    val ambientMin: Int = 5,
    /** Minutes of Ambient before the box goes to sleep; 0 = never. */
    val sleepMin: Int = 30,
    /** Soft system clicks when moving around with the remote. */
    val navSounds: Boolean = true,
    /** With the helper service on: the remote's Home button always opens Dzaurov TV. */
    val homeOverride: Boolean = true,
)

const val DEFAULT_THEME = "auto"

/** Everything you choose is saved here, on the box only. */
class Prefs(context: Context) {
    private val ds = context.applicationContext.store

    private object K {
        val theme = stringPreferencesKey("theme")
        val customVersion = longPreferencesKey("custom_version")
        val particles = booleanPreferencesKey("particles")
        val performance = booleanPreferencesKey("performance")
        val liveBg = booleanPreferencesKey("live_bg")
        val showFps = booleanPreferencesKey("show_fps")
        val favorites = stringPreferencesKey("favorites")
        val hidden = stringSetPreferencesKey("hidden")
        val recents = stringPreferencesKey("recents")
        val seeded = booleanPreferencesKey("seeded")
        val weather = stringPreferencesKey("weather")
        val ambientMin = intPreferencesKey("ambient_min")
        val sleepMin = intPreferencesKey("sleep_min")
        val navSounds = booleanPreferencesKey("nav_sounds")
        val homeOverride = booleanPreferencesKey("home_override")
    }

    val flow: Flow<UiPrefs> = ds.data
        .catch { emit(emptyPreferences()) }
        .map { p ->
            UiPrefs(
                theme = p[K.theme] ?: DEFAULT_THEME,
                customVersion = p[K.customVersion] ?: 0L,
                particles = p[K.particles] ?: true,
                performance = p[K.performance] ?: false,
                liveBg = p[K.liveBg] ?: false,
                showFps = p[K.showFps] ?: false,
                favorites = favs(p),
                hidden = p[K.hidden] ?: emptySet(),
                recents = parseRecents(p[K.recents]),
                seeded = p[K.seeded] ?: false,
                loaded = true,
                ambientMin = p[K.ambientMin] ?: 5,
                sleepMin = p[K.sleepMin] ?: 30,
                navSounds = p[K.navSounds] ?: true,
                homeOverride = p[K.homeOverride] ?: true,
            )
        }
        .distinctUntilChanged()

    val weatherCache: Flow<String?> = ds.data.catch { emit(emptyPreferences()) }.map { it[K.weather] }

    suspend fun setTheme(theme: String) {
        ds.edit { it[K.theme] = theme }
    }

    suspend fun setCustomTheme(version: Long) {
        ds.edit {
            it[K.customVersion] = version
            it[K.theme] = "custom"
        }
    }

    suspend fun setParticles(on: Boolean) {
        ds.edit { it[K.particles] = on }
    }

    suspend fun setPerformance(on: Boolean) {
        ds.edit { it[K.performance] = on }
    }

    suspend fun setLiveBg(on: Boolean) {
        ds.edit { it[K.liveBg] = on }
    }

    suspend fun setShowFps(on: Boolean) {
        ds.edit { it[K.showFps] = on }
    }

    suspend fun setAmbientMin(min: Int) {
        ds.edit { it[K.ambientMin] = min }
    }

    suspend fun setSleepMin(min: Int) {
        ds.edit { it[K.sleepMin] = min }
    }

    suspend fun setNavSounds(on: Boolean) {
        ds.edit { it[K.navSounds] = on }
    }

    suspend fun setHomeOverride(on: Boolean) {
        ds.edit { it[K.homeOverride] = on }
    }

    /** Returns true when the app was added, false when it was removed. */
    suspend fun toggleFavorite(pkg: String): Boolean {
        var added = false
        ds.edit { p ->
            val list = favs(p).toMutableList()
            if (pkg in list) list.remove(pkg) else {
                list.add(pkg)
                added = true
            }
            p[K.favorites] = list.joinToString("|")
        }
        return added
    }

    suspend fun setFavorites(list: List<String>) {
        ds.edit { it[K.favorites] = list.distinct().joinToString("|") }
    }

    suspend fun seedFavorites(list: List<String>) {
        ds.edit { p ->
            if (p[K.seeded] != true) {
                p[K.favorites] = list.distinct().joinToString("|")
                p[K.seeded] = true
            }
        }
    }

    suspend fun hide(pkg: String) {
        ds.edit { p ->
            p[K.hidden] = (p[K.hidden] ?: emptySet()) + pkg
            p[K.favorites] = favs(p).filter { it != pkg }.joinToString("|")
        }
    }

    suspend fun unhide(pkg: String) {
        ds.edit { p -> p[K.hidden] = (p[K.hidden] ?: emptySet()) - pkg }
    }

    suspend fun addRecent(pkg: String) {
        ds.edit { p ->
            val list = parseRecents(p[K.recents]).filter { it.pkg != pkg }.toMutableList()
            list.add(0, Recent(pkg, System.currentTimeMillis()))
            writeRecents(p, list.take(MAX_RECENTS))
        }
    }

    suspend fun removeRecent(pkg: String) {
        ds.edit { p -> writeRecents(p, parseRecents(p[K.recents]).filter { it.pkg != pkg }) }
    }

    suspend fun resetLayout() {
        ds.edit { p ->
            p.remove(K.favorites)
            p.remove(K.recents)
            p.remove(K.hidden)
            p[K.seeded] = false
        }
    }

    suspend fun saveWeather(raw: String) {
        ds.edit { it[K.weather] = raw }
    }

    private fun favs(p: Preferences): List<String> =
        p[K.favorites]?.split('|')?.filter { it.isNotBlank() } ?: emptyList()

    private fun writeRecents(p: MutablePreferences, list: List<Recent>) {
        p[K.recents] = list.joinToString("|") { "${it.pkg}:${it.at}" }
    }

    private fun parseRecents(raw: String?): List<Recent> =
        raw?.split('|')?.mapNotNull { part ->
            val i = part.lastIndexOf(':')
            if (i <= 0) null else part.substring(i + 1).toLongOrNull()?.let { Recent(part.substring(0, i), it) }
        } ?: emptyList()

    companion object {
        const val MAX_RECENTS = 12
    }
}
