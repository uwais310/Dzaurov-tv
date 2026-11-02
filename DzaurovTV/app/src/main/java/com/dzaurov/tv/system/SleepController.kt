package com.dzaurov.tv.system

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Sleep and the Home button, both powered by [DzAccessibilityService].
 *
 * Android only lets ordinary apps turn the screen off through an accessibility service
 * ("lock screen" action), so sleep features need that service switched on once.
 * Without it, Ambient still works and the box falls back to its own sleep timer.
 */
object SleepController {

    private var service: WeakReference<DzAccessibilityService>? = null

    private val _serviceOn = MutableStateFlow(false)
    val serviceOn: StateFlow<Boolean> = _serviceOn

    /** When the sleep timer fires (epoch ms), or 0 when no timer is set. */
    private val _timerEndsAt = MutableStateFlow(0L)
    val timerEndsAt: StateFlow<Long> = _timerEndsAt

    /** Mirrors the "Home button opens Dzaurov TV" setting for the service. */
    @Volatile
    var homeOverride: Boolean = true

    private var timerJob: Job? = null

    internal fun attach(s: DzAccessibilityService) {
        service = WeakReference(s)
        _serviceOn.value = true
    }

    internal fun detach(s: DzAccessibilityService) {
        if (service?.get() === s) {
            service = null
            _serviceOn.value = false
        }
    }

    /** Turns the screen off and puts the box to sleep. Returns false when the service is off. */
    fun lockNow(): Boolean = runCatching { service?.get()?.lockScreen() ?: false }.getOrDefault(false)

    /** Sleep in [minutes] even while another app plays; a toast warns one minute before. */
    fun startTimer(context: Context, scope: CoroutineScope, minutes: Int) {
        timerJob?.cancel()
        if (minutes <= 0) {
            _timerEndsAt.value = 0L
            return
        }
        val ends = System.currentTimeMillis() + minutes * 60_000L
        _timerEndsAt.value = ends
        val app = context.applicationContext
        timerJob = scope.launch {
            val untilWarning = ends - 60_000L - System.currentTimeMillis()
            if (untilWarning > 0) delay(untilWarning)
            runCatching { Toast.makeText(app, "Dzaurov TV: going to sleep in 1 minute", Toast.LENGTH_LONG).show() }
            val rest = ends - System.currentTimeMillis()
            if (rest > 0) delay(rest)
            _timerEndsAt.value = 0L
            lockNow()
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerEndsAt.value = 0L
    }
}
