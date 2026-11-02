package com.dzaurov.tv.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Everything an app tile needs:
 * - [icon]: the app's own icon as an iOS-style squircle (anti-aliased, baked once),
 * - [banner]: its TV banner (real wide logo) when it has one,
 * - [color]: its brand colour, for glow and tinted glass.
 * [image] / [isBanner] keep the old "banner, else icon" meaning for older call sites.
 */
class AppVisual(
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
    val color: Color,
) {
    val image: ImageBitmap get() = banner ?: icon
    val isBanner: Boolean get() = banner != null
}

/**
 * Icons and banners are rendered off the main thread, at most 3 at a time, into a
 * size-limited cache (about 24 MB), so scrolling never stutters and memory stays flat.
 */
class IconCache(private val context: Context) {

    private val cache = object : LruCache<String, AppVisual>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppVisual): Int {
            val b = value.banner
            return value.icon.width * value.icon.height * 4 + (if (b != null) b.width * b.height * 4 else 0)
        }
    }
    private val gate = Semaphore(3)

    fun peek(app: AppEntry): AppVisual? = cache.get(app.packageName)

    suspend fun load(app: AppEntry): AppVisual? {
        cache.get(app.packageName)?.let { return it }
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                cache.get(app.packageName) ?: runCatching { render(app) }.getOrNull()?.also {
                    cache.put(app.packageName, it)
                }
            }
        }
    }

    fun invalidate(pkg: String) {
        cache.remove(pkg)
    }

    fun clear() {
        cache.evictAll()
    }

    private fun render(app: AppEntry): AppVisual {
        val pm = context.packageManager
        val info = runCatching { pm.getActivityInfo(ComponentName(app.packageName, app.className), 0) }.getOrNull()
        val bannerDrawable: Drawable? = info?.loadBanner(pm)
            ?: runCatching { pm.getApplicationBanner(app.packageName) }.getOrNull()
        val iconDrawable: Drawable = info?.loadIcon(pm) ?: pm.getApplicationIcon(app.packageName)

        val banner = bannerDrawable?.let { fit(it, 320, 180) }
        val raw = rawIcon(iconDrawable, ICON_PX)
        val color = brandColor(raw) ?: banner?.let { brandColor(it) } ?: FALLBACK
        val icon = squircle(raw, iconDrawable is AdaptiveIconDrawable, color)
        raw.recycle()
        return AppVisual(icon.asImageBitmap(), banner?.asImageBitmap(), color)
    }

    /** Full-bleed square render; adaptive icons are drawn without the system's mask shape. */
    private fun rawIcon(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (d is AdaptiveIconDrawable) {
            // Layers are 108 units with the visible 72 in the middle: extend 1/4 on each side.
            val e = size / 4
            d.background?.let {
                it.setBounds(-e, -e, size + e, size + e)
                it.draw(canvas)
            }
            d.foreground?.let {
                it.setBounds(-e, -e, size + e, size + e)
                it.draw(canvas)
            }
        } else {
            d.setBounds(0, 0, size, size)
            d.draw(canvas)
        }
        return bmp
    }

    /**
     * Masks the icon to a superellipse (the iOS shape). Old-style icons that bring their own
     * shape (transparent corners) sit on a plate in their brand colour instead of being cut.
     */
    private fun squircle(raw: Bitmap, adaptive: Boolean, brand: Color): Bitmap {
        val size = raw.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val path = squirclePath(size.toFloat())
        val fullBleed = adaptive || opaqueCorners(raw)
        canvas.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        if (fullBleed) {
            canvas.drawBitmap(raw, 0f, 0f, paint)
        } else {
            paint.color = plateColor(brand)
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
            val inset = size * 0.14f
            canvas.drawBitmap(
                raw,
                null,
                android.graphics.RectF(inset, inset, size - inset, size - inset),
                paint,
            )
        }
        return out
    }

    private fun opaqueCorners(b: Bitmap): Boolean {
        val m = (b.width * 0.06f).roundToInt().coerceAtLeast(1)
        val pts = listOf(m to m, b.width - 1 - m to m, m to b.height - 1 - m, b.width - 1 - m to b.height - 1 - m)
        return pts.all { (x, y) -> (b.getPixel(x, y) ushr 24) > 230 }
    }

    private fun plateColor(brand: Color): Int {
        val dark = Color(
            red = brand.red * 0.30f + 0.06f,
            green = brand.green * 0.30f + 0.07f,
            blue = brand.blue * 0.30f + 0.10f,
        )
        return dark.toArgb()
    }

    private fun squirclePath(s: Float): Path {
        val p = Path()
        val r = s / 2f
        val n = 5.0
        val steps = 120
        for (i in 0..steps) {
            val t = 2.0 * Math.PI * i / steps
            val c = cos(t)
            val sn = sin(t)
            val x = r + r * (Math.signum(c) * abs(c).pow(2.0 / n)).toFloat()
            val y = r + r * (Math.signum(sn) * abs(sn).pow(2.0 / n)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private fun fit(d: Drawable, maxW: Int, maxH: Int): Bitmap {
        val iw = d.intrinsicWidth.takeIf { it > 0 } ?: maxW
        val ih = d.intrinsicHeight.takeIf { it > 0 } ?: maxH
        val scale = min(maxW.toFloat() / iw, maxH.toFloat() / ih)
        val w = max(1, (iw * scale).roundToInt())
        val h = max(1, (ih * scale).roundToInt())
        return d.toBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun brandColor(bitmap: Bitmap): Color? {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val candidates = listOfNotNull(
            palette.vibrantSwatch, palette.lightVibrantSwatch, palette.dominantSwatch, palette.mutedSwatch,
        )
        for (swatch in candidates) {
            val c = Color(swatch.rgb)
            if (c.luminance() in 0.04f..0.85f) return c
        }
        return null
    }

    companion object {
        val FALLBACK = Color(0xFF5A96FF)
        private const val ICON_PX = 192
    }
}
