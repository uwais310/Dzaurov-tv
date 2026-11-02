package com.dzaurov.tv.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class Weather(val tempC: Int, val code: Int, val isDay: Boolean, val fetchedAt: Long)

/**
 * Almaty weather from Open-Meteo (free, no key, no account). Refreshed every 30 minutes,
 * cached so the last reading shows offline and right after a cold start.
 */
class WeatherRepository(private val prefs: Prefs, private val scope: CoroutineScope) {

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    fun start() {
        scope.launch {
            prefs.weatherCache.first()?.let { parse(it) }?.let { _weather.value = it }
            while (isActive) {
                val age = System.currentTimeMillis() - (_weather.value?.fetchedAt ?: 0L)
                if (age < REFRESH_MS) {
                    delay(REFRESH_MS - age)
                    continue
                }
                val fresh = withContext(Dispatchers.IO) { runCatching { fetch() }.getOrNull() }
                if (fresh != null) {
                    _weather.value = fresh
                    prefs.saveWeather(serialize(fresh))
                } else {
                    delay(RETRY_MS)
                }
            }
        }
    }

    private fun fetch(): Weather {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON" +
                "&current=temperature_2m,weather_code,is_day&timezone=Asia%2FAlmaty"
        )
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("User-Agent", "DzaurovTV/1.0")
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(body).getJSONObject("current")
            return Weather(
                tempC = cur.getDouble("temperature_2m").roundToInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.optInt("is_day", 1) == 1,
                fetchedAt = System.currentTimeMillis(),
            )
        } finally {
            c.disconnect()
        }
    }

    private fun serialize(w: Weather) = "${w.tempC};${w.code};${if (w.isDay) 1 else 0};${w.fetchedAt}"

    private fun parse(raw: String): Weather? {
        val p = raw.split(';')
        if (p.size != 4) return null
        return Weather(
            tempC = p[0].toIntOrNull() ?: return null,
            code = p[1].toIntOrNull() ?: return null,
            isDay = p[2] == "1",
            fetchedAt = p[3].toLongOrNull() ?: return null,
        )
    }

    companion object {
        private const val LAT = "43.2567"
        private const val LON = "76.9286"
        private const val REFRESH_MS = 30 * 60 * 1000L
        private const val RETRY_MS = 5 * 60 * 1000L
    }
}
