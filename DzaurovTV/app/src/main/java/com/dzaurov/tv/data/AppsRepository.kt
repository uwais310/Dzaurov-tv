package com.dzaurov.tv.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One launchable app. Keyed by package: a package shows up once, with its TV entry preferred. */
data class AppEntry(
    val packageName: String,
    val className: String,
    val label: String,
    val leanback: Boolean,
    val category: Int,
    /** When the app was first installed (ms), for the "New" badge. */
    val installedAt: Long = 0L,
) {
    fun isNew(now: Long = System.currentTimeMillis()): Boolean =
        installedAt > 0L && now - installedAt in 0L..NEW_FOR_MS

    companion object {
        const val NEW_FOR_MS = 3L * 24 * 60 * 60 * 1000
    }
}

class AppsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val icons: IconCache,
) {
    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** Null until the first scan finishes. */
    val apps: StateFlow<List<AppEntry>?> = _apps

    private val reload = Channel<Unit>(Channel.CONFLATED)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            intent.data?.schemeSpecificPart?.let { icons.invalidate(it) }
            reload.trySend(Unit)
        }
    }

    private val externalReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            reload.trySend(Unit)
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val external = IntentFilter().apply {
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
        }
        ContextCompat.registerReceiver(context, externalReceiver, external, ContextCompat.RECEIVER_EXPORTED)

        scope.launch {
            reload.trySend(Unit)
            for (signal in reload) {
                _apps.value = withContext(Dispatchers.IO) { runCatching { query() }.getOrDefault(_apps.value ?: emptyList()) }
                // Installs fire several broadcasts in a row; one scan covers them.
                delay(400)
            }
        }
    }

    fun refresh() {
        reload.trySend(Unit)
    }

    private fun query(): List<AppEntry> {
        val pm = context.packageManager
        val result = LinkedHashMap<String, AppEntry>()
        fun collect(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == context.packageName) continue
                if (result.containsKey(ai.packageName)) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { ai.packageName }
                @Suppress("DEPRECATION")
                val installed = runCatching { pm.getPackageInfo(ai.packageName, 0).firstInstallTime }.getOrDefault(0L)
                result[ai.packageName] = AppEntry(
                    packageName = ai.packageName,
                    className = ai.name,
                    label = label,
                    leanback = leanback,
                    category = ai.applicationInfo.category,
                    installedAt = installed,
                )
            }
        }
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, leanback = true)
        collect(Intent.CATEGORY_LAUNCHER, leanback = false)
        return result.values.sortedBy { it.label.lowercase() }
    }

    /** Starts the app. Returns false if it could not be opened. */
    fun launch(from: Context, app: AppEntry): Boolean {
        val direct = Intent(Intent.ACTION_MAIN).apply {
            addCategory(if (app.leanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        if (runCatching { from.startActivity(direct) }.isSuccess) return true
        val pm = context.packageManager
        val fallback = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
            ?: return false
        fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { from.startActivity(fallback) }.isSuccess
    }
}
