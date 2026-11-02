package com.dzaurov.tv.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class Photo(val uri: Uri, val name: String, val folder: String)

/**
 * "Use my own photo". Many Android TV boxes ship without a system file picker, so the
 * launcher lists pictures itself from the media index, which covers Downloads and USB sticks.
 */
object Photos {

    suspend fun list(context: Context): List<Photo> = withContext(Dispatchers.IO) {
        val base = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        val out = ArrayList<Photo>()
        runCatching {
            context.contentResolver.query(base, projection, null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC")?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val name = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val folder = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                while (c.moveToNext() && out.size < 200) {
                    out += Photo(
                        uri = ContentUris.withAppendedId(base, c.getLong(id)),
                        name = c.getString(name) ?: "Photo",
                        folder = c.getString(folder) ?: "",
                    )
                }
            }
        }
        out
    }

    suspend fun thumbnail(context: Context, uri: Uri): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 180), null).asImageBitmap() }.getOrNull()
    }

    /** Crops the photo to 16:9, scales it to 1920×1080 and saves it as a JPEG under 500 KB. */
    suspend fun import(context: Context, uri: Uri, dest: File): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching false
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 1920 && bounds.outHeight / (sample * 2) >= 1080) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false

            val rotation = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val upright = if (rotation != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
            } else decoded

            val scale = max(1920f / upright.width, 1080f / upright.height)
            val sw = max(1920, (upright.width * scale).roundToInt())
            val sh = max(1080, (upright.height * scale).roundToInt())
            val scaled = Bitmap.createScaledBitmap(upright, sw, sh, true)
            val cropped = Bitmap.createBitmap(scaled, (sw - 1920) / 2, (sh - 1080) / 2, 1920, 1080)

            var quality = 88
            var bytes: ByteArray
            do {
                val bos = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                bytes = bos.toByteArray()
                quality -= 8
            } while (bytes.size > 500_000 && quality >= 56)

            val tmp = File(dest.parentFile, dest.name + ".tmp")
            tmp.writeBytes(bytes)
            if (dest.exists()) dest.delete()
            tmp.renameTo(dest)
        }.getOrDefault(false)
    }
}
