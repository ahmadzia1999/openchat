package com.openchat.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.openchat.app.data.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageUtils {
    private const val MAX_DIMENSION = 1280
    private const val JPEG_QUALITY = 84

    suspend fun loadAttachment(
        context: Context,
        uri: Uri,
        index: Int
    ): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri).orEmpty()
        if (mimeType.isNotBlank() && !mimeType.startsWith("image/")) {
            throw IllegalArgumentException("Only image files can be attached.")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("This image could not be read.")
        }

        val sampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("This image could not be read.")

        val rotated = applyExifRotation(resolver, uri, decoded)
        val scaled = scaleDown(rotated)
        val flattened = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }

        val output = ByteArrayOutputStream()
        if (!flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
            throw IllegalArgumentException("This image could not be prepared.")
        }

        if (flattened !== scaled && !scaled.isRecycled) scaled.recycle()
        if (scaled !== rotated && !rotated.isRecycled) rotated.recycle()
        if (rotated !== decoded && !decoded.isRecycled) decoded.recycle()
        if (!flattened.isRecycled) flattened.recycle()

        val rawName = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
        val baseName = rawName.substringBeforeLast('.', rawName)
            .ifBlank { "image_${index + 1}" }
            .take(40)
        Attachment(
            name = "${baseName}_${index + 1}.jpg",
            mimeType = "image/jpeg",
            base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        )
    }

    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sample = 1
        var largest = maxOf(width, height)
        while (largest / 2 >= MAX_DIMENSION) {
            largest /= 2
            sample *= 2
        }
        return sample
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / largest.toFloat()
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun applyExifRotation(
        resolver: android.content.ContentResolver,
        uri: Uri,
        bitmap: Bitmap
    ): Bitmap {
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                when (
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
