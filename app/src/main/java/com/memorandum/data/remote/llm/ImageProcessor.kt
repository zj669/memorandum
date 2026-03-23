package com.memorandum.data.remote.llm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class ImageProcessor @Inject constructor(
    private val context: Context,
) {

    companion object {
        private const val TAG = "ImageProcessor"
        private const val MAX_LONG_EDGE = 1024
        private const val JPEG_QUALITY = 85
    }

    suspend fun processForLlm(uris: List<String>): List<ImageInput> =
        withContext(Dispatchers.Default) {
            uris.mapNotNull { uriStr ->
                try {
                    processOne(uriStr)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to process image: uri=$uriStr, error=${e.message}")
                    null
                }
            }
        }

    suspend fun processOrSkip(uris: List<String>, supportsImage: Boolean): List<ImageInput> {
        if (!supportsImage || uris.isEmpty()) {
            if (uris.isNotEmpty()) {
                Log.d(TAG, "Model does not support images, skipping ${uris.size} image(s)")
            }
            return emptyList()
        }
        return processForLlm(uris)
    }

    private fun processOne(uriStr: String): ImageInput {
        val uri = Uri.parse(uriStr)
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uriStr")

        val originalBytes = inputStream.use { it.readBytes() }
        val mimeType = detectMimeType(originalBytes)

        // Decode to bitmap for resizing
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size, options)

        val origWidth = options.outWidth
        val origHeight = options.outHeight
        val longEdge = max(origWidth, origHeight)

        val bitmap = if (longEdge > MAX_LONG_EDGE) {
            val scale = MAX_LONG_EDGE.toFloat() / longEdge
            val newWidth = (origWidth * scale).roundToInt()
            val newHeight = (origHeight * scale).roundToInt()
            val full = BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                ?: throw IllegalStateException("Failed to decode image: $uriStr")
            val scaled = Bitmap.createScaledBitmap(full, newWidth, newHeight, true)
            if (scaled !== full) full.recycle()
            scaled
        } else {
            BitmapFactory.decodeByteArray(originalBytes, 0, originalBytes.size)
                ?: throw IllegalStateException("Failed to decode image: $uriStr")
        }

        val outputStream = ByteArrayOutputStream()
        val compressFormat = if (mimeType == "image/png") {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
        bitmap.compress(compressFormat, JPEG_QUALITY, outputStream)
        bitmap.recycle()

        val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        Log.d(TAG, "Processed image: uri=$uriStr, size=${base64.length} chars")

        return ImageInput(
            uri = uriStr,
            base64Data = base64,
            mimeType = mimeType,
        )
    }

    private fun detectMimeType(bytes: ByteArray): String {
        if (bytes.size < 4) return "image/jpeg"
        return when {
            // PNG: 89 50 4E 47
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
            // GIF: 47 49 46
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> "image/gif"
            // WEBP: 52 49 46 46 ... 57 45 42 50
            bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() -> "image/webp"
            else -> "image/jpeg"
        }
    }
}
