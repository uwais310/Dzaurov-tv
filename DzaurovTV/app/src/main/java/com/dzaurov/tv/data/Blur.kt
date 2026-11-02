package com.dzaurov.tv.data

import android.graphics.Bitmap

/** Fast box blur on a small copy. Run once per background, never per frame. */
object Blur {

    fun blurredCopy(
        src: Bitmap,
        width: Int,
        height: Int,
        radius: Int,
        passes: Int,
        saturation: Float = 1f,
        lift: Float = 0f,
    ): Bitmap {
        val small = Bitmap.createScaledBitmap(src, width, height, true)
        val px = IntArray(width * height)
        small.getPixels(px, 0, width, 0, 0, width, height)
        if (small !== src) small.recycle()
        val tmp = IntArray(px.size)
        repeat(passes) {
            horizontal(px, tmp, width, height, radius)
            vertical(tmp, px, width, height, radius)
        }
        if (saturation != 1f || lift != 0f) vibrance(px, saturation, lift)
        return Bitmap.createBitmap(px, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Boosts colour and lifts shadows slightly: the "vibrancy" behind frosted glass. */
    private fun vibrance(px: IntArray, saturation: Float, lift: Float) {
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 255
            val g = (p shr 8) and 255
            val b = p and 255
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            fun ch(c: Int): Int {
                val s = l + (c - l) * saturation
                return (s + (255f - s) * lift).toInt().coerceIn(0, 255)
            }
            px[i] = (255 shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }
    }

    private fun horizontal(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (y in 0 until h) {
            val row = y * w
            var rs = 0
            var gs = 0
            var bs = 0
            for (i in -r..r) {
                val p = src[row + i.coerceIn(0, w - 1)]
                rs += (p shr 16) and 255
                gs += (p shr 8) and 255
                bs += p and 255
            }
            for (x in 0 until w) {
                dst[row + x] = (255 shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = src[row + (x - r).coerceIn(0, w - 1)]
                val inn = src[row + (x + r + 1).coerceIn(0, w - 1)]
                rs += ((inn shr 16) and 255) - ((out shr 16) and 255)
                gs += ((inn shr 8) and 255) - ((out shr 8) and 255)
                bs += (inn and 255) - (out and 255)
            }
        }
    }

    private fun vertical(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int) {
        val div = 2 * r + 1
        for (x in 0 until w) {
            var rs = 0
            var gs = 0
            var bs = 0
            for (i in -r..r) {
                val p = src[i.coerceIn(0, h - 1) * w + x]
                rs += (p shr 16) and 255
                gs += (p shr 8) and 255
                bs += p and 255
            }
            for (y in 0 until h) {
                dst[y * w + x] = (255 shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
                val out = src[(y - r).coerceIn(0, h - 1) * w + x]
                val inn = src[(y + r + 1).coerceIn(0, h - 1) * w + x]
                rs += ((inn shr 16) and 255) - ((out shr 16) and 255)
                gs += ((inn shr 8) and 255) - ((out shr 8) and 255)
                bs += (inn and 255) - (out and 255)
            }
        }
    }
}
