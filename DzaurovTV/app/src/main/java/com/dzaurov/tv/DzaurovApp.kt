package com.dzaurov.tv

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import com.dzaurov.tv.data.AppsRepository
import com.dzaurov.tv.data.BackgroundStore
import com.dzaurov.tv.data.IconCache
import com.dzaurov.tv.data.NetworkRepository
import com.dzaurov.tv.data.PosterCache
import com.dzaurov.tv.data.Prefs
import com.dzaurov.tv.data.WatchNextRepository
import com.dzaurov.tv.data.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.dzaurov.tv.system.SleepController

/**
 * Holds the few long-lived pieces. They live as long as the process does, so the
 * launcher comes back instantly after an app closes, and nothing leaks per screen.
 */
class DzaurovApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var prefs: Prefs
        private set
    lateinit var icons: IconCache
        private set
    lateinit var apps: AppsRepository
        private set
    lateinit var backgrounds: BackgroundStore
        private set
    lateinit var weather: WeatherRepository
        private set
    lateinit var watchNext: WatchNextRepository
        private set
    lateinit var posters: PosterCache
        private set
    lateinit var network: NetworkRepository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        icons = IconCache(this)
        apps = AppsRepository(this, appScope, icons)
        backgrounds = BackgroundStore(this)
        weather = WeatherRepository(prefs, appScope)
        watchNext = WatchNextRepository(this, appScope)
        posters = PosterCache(this)
        network = NetworkRepository(this)
        apps.start()
        weather.start()
        watchNext.start()
        network.start()
        // The Home-button helper runs in this process too; keep it in step with Settings.
        appScope.launch { prefs.flow.collect { SleepController.homeOverride = it.homeOverride } }
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_MODERATE ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            icons.clear()
            posters.clear()
        }
    }
}

val Context.dz: DzaurovApp get() = applicationContext as DzaurovApp
