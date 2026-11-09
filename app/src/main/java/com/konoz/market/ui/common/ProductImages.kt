package com.konoz.market.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Product pictures are copied into the app's private storage (downscaled, JPEG) so they survive the original
 * being deleted from the gallery. NOTE: these files live outside the database; the backup feature must include
 * the product_images folder. EXIF rotation is not applied.
 */
object ProductImages {
    private const val MAX_SIDE = 800

    /** Blocking: call from a background dispatcher. Returns the saved file path, or null on failure. */
    fun save(context: Context, uri: Uri): String? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE) }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (bitmap == null) {
            null
        } else {
            val dir = File(context.filesDir, "product_images").apply { mkdirs() }
            val file = File(dir, UUID.randomUUID().toString() + ".jpg")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }

    /** Blocking thumbnail decode. */
    fun load(path: String, targetPx: Int): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetPx) }
        BitmapFactory.decodeFile(path, options)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }

    fun sampleSize(width: Int, height: Int, target: Int): Int {
        var s = 1
        while (width / (s * 2) >= target || height / (s * 2) >= target) s *= 2
        return s
    }
}
