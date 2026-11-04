package com.dzaurov.tv

import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.dzaurov.tv.data.WatchNextRepository
import com.dzaurov.tv.ui.Screen
import com.dzaurov.tv.ui.components.NavDir
import kotlinx.coroutines.launch
import com.dzaurov.tv.ui.MainViewModel
import com.dzaurov.tv.ui.Root
import com.dzaurov.tv.ui.theme.DzaurovTheme

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val askPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onWatchNextPermission(granted)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Frame timings for the optional FPS meter (Settings → Show FPS). Cheap: Android
        // reports them anyway, on a background thread.
        val statsThread = android.os.HandlerThread("frame-stats").apply { start() }
        window.addOnFrameMetricsAvailableListener({ _, m, _ ->
            com.dzaurov.tv.ui.components.FrameStats.add(
                m.getMetric(android.view.FrameMetrics.INTENDED_VSYNC_TIMESTAMP),
                m.getMetric(android.view.FrameMetrics.TOTAL_DURATION),
            )
        }, android.os.Handler(statsThread.looper))
        setContent {
            DzaurovTheme {
                Root(vm = vm, onBackOnHome = ::onBackOnHome)
            }
        }
        lifecycleScope.launch {
            vm.permissionRequests.collect { permission -> runCatching { askPermission.launch(permission) } }
        }
        lifecycleScope.launch {
            vm.keepAwake.collect { on ->
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        askForWatchNextOnce()
    }

    /** First start only: offer Continue watching. Later the Settings row asks again. */
    private fun askForWatchNextOnce() {
        val once = getSharedPreferences("dz_once", MODE_PRIVATE)
        if (once.getBoolean("asked_watch_next", false)) return
        once.edit().putBoolean("asked_watch_next", true).apply()
        if (checkSelfPermission(WatchNextRepository.PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            runCatching { askPermission.launch(WatchNextRepository.PERMISSION) }
        }
    }

    /** Home button pressed while we're already open: go to Home and reset everything. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
    }

    override fun onResume() {
        super.onResume()
        vm.resumed = true
        vm.onResumed()
        // Coming back from Netflix/YouTube: pick up the episode you just watched.
        vm.refreshWatchNext()
    }

    override fun onPause() {
        vm.resumed = false
        super.onPause()
    }

    private val audio: AudioManager? by lazy { getSystemService(AudioManager::class.java) }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        vm.onUserActivity()
        if (event.action == KeyEvent.ACTION_DOWN) {
            val dir = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> NavDir.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> NavDir.RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> NavDir.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> NavDir.DOWN
                else -> NavDir.NONE
            }
            if (dir != NavDir.NONE) NavDir.last = dir
            // Holding an arrow ticks like a scroll wheel; holding OK must not click repeatedly.
            if (dir != NavDir.NONE || event.repeatCount == 0) playNavSound(event.keyCode)
        }
        // If focus was ever lost (a tile disappeared, an app was uninstalled), the next
        // D-pad press brings it back to a sensible place instead of doing nothing.
        if (event.action == KeyEvent.ACTION_DOWN && !vm.rootHasFocus && event.keyCode in NAV_KEYS) {
            vm.requestFocusRestore()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** Soft system clicks like Apple TV (follows the box's "System sounds" switch too). */
    private fun playNavSound(keyCode: Int) {
        if (!vm.prefs.value.navSounds) return
        val s = vm.screen.value
        if (s == Screen.Ambient || s == Screen.Sleep) return
        val fx = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> AudioManager.FX_FOCUS_NAVIGATION_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> AudioManager.FX_KEY_CLICK
            else -> return
        }
        runCatching { audio?.playSoundEffect(fx) }
    }

    /**
     * Back on Home with nothing open. As the home app we stay (there is nothing behind us).
     * Opened as a normal app, Back returns to whatever launcher opened us.
     */
    private fun onBackOnHome() {
        if (!isDefaultHome()) finish()
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == packageName
    }

    private companion object {
        val NAV_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
        )
    }
}
