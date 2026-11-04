package com.dzaurov.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A background, decoded once: the picture at the screen's own size plus a small pre-blurred copy.
 * The glass panels draw the blurred copy behind themselves, which looks like live blur
 * but costs almost nothing (Android 11 has no live blur anyway).
 */
class Backdrop(val id: String, val full: ImageBitmap, val blurred: ImageBitmap, val fog: ImageBitmap?)

class BackgroundStore(private val context: Context) {

    private val lock = Mutex()
    private var current: Backdrop? = null
    private var fog: ImageBitmap? = null

    /** Pictures shipped with the app, best first. "auto" and "custom" are handled separately. */
    val themes = listOf("forest", "taiga", "alatau", "dusk", "night", "fog", "aurora", "glow")

    /** Themes that look right with drifting fog over them. */
    private val foggy = setOf("alatau", "dusk", "fog")

    /** "auto" follows the time of day: misty morning, forest by day, sunset lake, mountains at night. */
    fun resolve(theme: String, hour: Int): String = if (theme != AUTO) theme else when (hour) {
        in 5..9 -> "fog"
        in 10..16 -> "forest"
        in 17..19 -> "dusk"
        else -> "night"
    }

    fun label(theme: String): String = when (theme) {
        AUTO -> "Auto"
        "custom" -> "My photo"
        "alatau" -> "Alatau"
        "aurora" -> "Aurora"
        "glow" -> "Glow"
        else -> theme.replaceFirstChar { it.uppercase() }
    }

    fun customFile() = File(context.filesDir, "custom_bg.jpg")

    fun hasCustom() = customFile().exists()

    suspend fun load(theme: String, customVersion: Long): Backdrop = lock.withLock {
        val wantCustom = theme == "custom" && hasCustom()
        val id = if (wantCustom) "custom-$customVersion" else theme.takeIf { it in themes } ?: FALLBACK
        current?.let { if (it.id == id) return@withLock it }
        val loaded = withContext(Dispatchers.IO) {
            val full = (if (wantCustom) decodeFile(customFile()) else null) ?: decodeAsset(id.takeIf { it in themes } ?: FALLBACK)
            // A touch more colour and light in the blurred copy makes the glass look "vibrant", like iOS.
            val blurred = Blur.blurredCopy(full, 320, 180, radius = 6, passes = 3, saturation = 1.35f, lift = 0.05f)
            val fogImage = if (id !in foggy) null else fog ?: runCatching {
                context.assets.open("fog_layer.png").use { BitmapFactory.decodeStream(it) }.asImageBitmap()
            }.getOrNull()
            if (fogImage != null) fog = fogImage
            Backdrop(id, full.asImageBitmap(), blurred.asImageBitmap(), fogImage)
        }
        current = loaded
        loaded
    }

    /** Small preview for the theme picker (240×135). */
    suspend fun thumbnail(theme: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bmp = if (theme == "custom") {
                BitmapFactory.decodeFile(customFile().path, opts)
            } else {
                context.assets.open("bg/$theme.jpg").use { BitmapFactory.decodeStream(it, null, opts) }
            }
            bmp?.asImageBitmap()
        }.getOrNull()
    }

    /**
     * The photos are 4K. Decode them at the screen's size (1920 wide on most boxes), not 4K:
     * a quarter of the memory and of the work for the GPU, and it looks the same on a 1080p UI.
     */
    private fun sampleFor(width: Int): Int {
        val target = maxOf(context.resources.displayMetrics.widthPixels, 1280)
        var sample = 1
        while (width / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun options(width: Int) = BitmapFactory.Options().apply {
        inSampleSize = sampleFor(width)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    private fun decodeAsset(name: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("bg/$name.jpg").use { BitmapFactory.decodeStream(it, null, bounds) }
        return context.assets.open("bg/$name.jpg").use { BitmapFactory.decodeStream(it, null, options(bounds.outWidth)) }
            ?: error("Couldn't decode background $name")
    }

    private fun decodeFile(f: File): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        BitmapFactory.decodeFile(f.path, options(bounds.outWidth))
    }.getOrNull()

    companion object {
        const val AUTO = "auto"
        const val FALLBACK = "forest"
    }
}
