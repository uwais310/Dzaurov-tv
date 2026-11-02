package com.dzaurov.tv.system

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.dzaurov.tv.MainActivity

/**
 * Small helper the user switches on once in Settings → Accessibility. It does two things:
 * 1. Home button: when the stock home screen comes up, Dzaurov TV is opened instead
 *    (the same trick launchers like Projectivy use), so Button Mapper isn't needed.
 * 2. Sleep: Android's "lock screen" action, the only way an app may turn the box off.
 * It reads no screen content: only which app's window just opened.
 */
class DzAccessibilityService : AccessibilityService() {

    private var launchers: Set<String> = emptySet()
    private var lastBring = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        launchers = findStockLaunchers()
        SleepController.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!SleepController.homeOverride) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == packageName || pkg !in launchers) return
        val now = SystemClock.uptimeMillis()
        if (now - lastBring < 700L) return
        lastBring = now
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClassName(this, MainActivity::class.java.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        runCatching { startActivity(home) }
    }

    override fun onInterrupt() {
        // Nothing is spoken or announced, so there is nothing to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        SleepController.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        SleepController.detach(this)
        super.onDestroy()
    }

    fun lockScreen(): Boolean = performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

    /** Other home-screen apps on the box (never Settings or setup screens). */
    private fun findStockLaunchers(): Set<String> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val found = runCatching { packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL) }
            .getOrDefault(emptyList())
            .mapNotNull { it.activityInfo?.packageName }
            .filter { p ->
                p != packageName &&
                    !p.contains("settings", ignoreCase = true) &&
                    !p.contains("setupwizard", ignoreCase = true) &&
                    !p.contains("provision", ignoreCase = true)
            }
        return (found + KNOWN_LAUNCHERS).toSet()
    }

    private companion object {
        val KNOWN_LAUNCHERS = setOf(
            "com.google.android.tvlauncher",
            "com.google.android.apps.tv.launcherx",
            "com.google.android.leanbacklauncher",
        )
    }
}
