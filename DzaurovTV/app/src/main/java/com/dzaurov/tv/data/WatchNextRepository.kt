package com.dzaurov.tv.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.BitmapFactory
import android.media.tv.TvContract
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** One "keep watching" entry that an app (Netflix, YouTube, Prime Video…) gave to Android. */
data class WatchNextItem(
    val id: Long,
    val pkg: String,
    val title: String,
    val subtitle: String?,
    val posterUri: String?,
    /** 0..1, or null when the app didn't report a position. */
    val progress: Float?,
    val remainingMs: Long?,
    val intentUri: String?,
    val at: Long,
)

enum class WatchNextStatus {
    /** Not read yet. */
    Unknown,
    /** Read fine and has entries. */
    Ok,
    /** Read fine, nothing to continue. */
    Empty,
    /** Android didn't let this app read the list. */
    Blocked,
}

/**
 * Reads Android TV's system "Watch Next" list (the same one the stock home screen shows).
 * Android only shares it with apps that have the TV-data permission; without it the list
 * comes back empty or throws, and Home falls back to the Recent row.
 */
class WatchNextRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<WatchNextItem>>(emptyList())
    val items: StateFlow<List<WatchNextItem>> = _items

    private val _status = MutableStateFlow(WatchNextStatus.Unknown)
    val status: StateFlow<WatchNextStatus> = _status

    private val reload = Channel<Unit>(Channel.CONFLATED)
    private var started = false
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            reload.trySend(Unit)
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (started) return
        started = true
        scope.launch {
            for (signal in reload) {
                observe()
                val (list, status) = withContext(Dispatchers.IO) { read() }
                _items.value = list
                _status.value = status
                // Apps write several rows in a burst; one read covers them.
                delay(1500)
            }
        }
        reload.trySend(Unit)
    }

    fun refresh() {
        reload.trySend(Unit)
    }

    private fun observe() {
        if (observing) return
        observing = runCatching {
            context.contentResolver.registerContentObserver(URI, true, observer)
        }.isSuccess
    }

    private fun read(): Pair<List<WatchNextItem>, WatchNextStatus> {
        val blocked: Pair<List<WatchNextItem>, WatchNextStatus> = emptyList<WatchNextItem>() to WatchNextStatus.Blocked
        // SecurityException when Android refuses; null when the TV provider is missing.
        val cursor: Cursor = runCatching { context.contentResolver.query(URI, null, null, null, null) }
            .getOrNull() ?: return blocked

        val list = ArrayList<WatchNextItem>()
        cursor.use { c ->
            while (c.moveToNext()) {
                runCatching { row(c) }.getOrNull()?.let { list += it }
            }
        }
        val sorted = list
            .filter { it.pkg != context.packageName }
            .sortedByDescending { it.at }
            .distinctBy { it.pkg + "|" + it.title }
            .take(MAX_ITEMS)
        val status = when {
            sorted.isNotEmpty() -> WatchNextStatus.Ok
            // Without the permission Android quietly returns only our own (zero) rows.
            !hasPermission() -> WatchNextStatus.Blocked
            else -> WatchNextStatus.Empty
        }
        return sorted to status
    }

    private fun row(c: Cursor): WatchNextItem? {
        if (c.int("browsable") == 0) return null
        val pkg = c.str("package_name") ?: return null
        val title = c.str("title")?.trim().orEmpty().ifEmpty { return null }

        val season = c.str("season_display_number")
        val episode = c.str("episode_display_number")
        val episodeTitle = c.str("episode_title")
        val subtitle = buildList {
            if (!season.isNullOrBlank() && !episode.isNullOrBlank()) add("S$season E$episode")
            else if (!episode.isNullOrBlank()) add("E$episode")
            if (!episodeTitle.isNullOrBlank()) add(episodeTitle)
        }.joinToString(" · ").ifEmpty { null }

        val position = c.long("last_playback_position_millis") ?: 0L
        val duration = c.long("duration_millis") ?: 0L
        val progress = if (duration > 0 && position > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else null
        val remaining = if (duration > 0 && position > 0) (duration - position).coerceAtLeast(0L) else null

        return WatchNextItem(
            id = c.long("_id") ?: return null,
            pkg = pkg,
            title = title,
            subtitle = subtitle,
            posterUri = c.str("poster_art_uri") ?: c.str("thumbnail_uri"),
            progress = progress,
            remainingMs = remaining,
            intentUri = c.str("intent_uri"),
            at = c.long("last_engagement_time_utc_millis") ?: 0L,
        )
    }

    private fun Cursor.idx(name: String): Int = getColumnIndex(name)
    private fun Cursor.str(name: String): String? = idx(name).let { if (it < 0 || isNull(it)) null else getString(it) }
    private fun Cursor.long(name: String): Long? = idx(name).let { if (it < 0 || isNull(it)) null else getLong(it) }
    private fun Cursor.int(name: String): Int? = idx(name).let { if (it < 0 || isNull(it)) null else getInt(it) }

    companion object {
        const val PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA"
        private const val MAX_ITEMS = 12
        private val URI: Uri = TvContract.WatchNextPrograms.CONTENT_URI

        const val ADB_GRANT = "adb shell pm grant com.dzaurov.tv $PERMISSION"
    }
}

/**
 * Poster pictures for Continue watching. They can be web links or files inside other
 * apps; both are decoded off the main thread at TV-card size into a 16 MB cache.
 */
class PosterCache(private val context: Context) {

    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    private val failed = HashSet<String>()
    private val gate = Semaphore(3)

    fun peek(uri: String): ImageBitmap? = cache.get(uri)

    suspend fun load(uri: String): ImageBitmap? {
        cache.get(uri)?.let { return it }
        synchronized(failed) { if (uri in failed) return null }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                cache.get(uri) ?: runCatching { decode(uri) }.getOrNull()?.also { cache.put(uri, it) }
                    ?: run {
                        synchronized(failed) { failed += uri }
                        null
                    }
            }
        }
    }

    fun clear() {
        cache.evictAll()
        synchronized(failed) { failed.clear() }
    }

    private fun decode(uri: String): ImageBitmap? {
        val bytes = open(uri)?.use { it.readCapped(MAX_BYTES) } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= TARGET_W && bounds.outHeight / (sample * 2) >= TARGET_H) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
    }

    private fun open(uri: String): InputStream? {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme?.lowercase()) {
            "http", "https" -> {
                val conn = URL(uri).openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                if (conn.responseCode !in 200..299) {
                    conn.disconnect()
                    null
                } else {
                    conn.inputStream
                }
            }
            ContentResolver.SCHEME_CONTENT, ContentResolver.SCHEME_ANDROID_RESOURCE, ContentResolver.SCHEME_FILE ->
                context.contentResolver.openInputStream(parsed)
            else -> null
        }
    }

    private fun InputStream.readCapped(max: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buf)
            if (n < 0) break
            total += n
            if (total > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TARGET_W = 400
        const val TARGET_H = 225
        const val MAX_BYTES = 6 * 1024 * 1024
    }
}
