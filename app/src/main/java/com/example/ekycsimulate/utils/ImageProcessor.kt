package com.example.ekycsimulate.utils

import android.graphics.*
import android.util.Log
import androidx.core.graphics.applyCanvas
import kotlin.math.roundToInt

object ImageProcessor {
    private const val TAG = "ImageProcessor"

    /**
     * Higher-level API: return a cleaned single image for quick OCR, and also
     * generateVariants for multi-pass OCR.
     */
    fun runAdvancedPreprocessing(bitmap: Bitmap): Bitmap {
        // Try OpenCV path if available
        return try {
            preprocessWithOpenCv(bitmap)
        } catch (t: Throwable) {
            Log.w(TAG, "OpenCV not available or preprocessWithOpenCv failed: ${t.message}")
            // fallback: grayscale -> contrast -> sharpen
            val g = toGrayscale(bitmap)
            val c = changeContrastAndBrightness(g, 1.5f, -40f)
            sharpen(c)
        }
    }

    /**
     * Generate multiple image variants used for multi-pass voting.
     */
    fun generateVariants(original: Bitmap): List<Bitmap> {
        val variants = mutableListOf<Bitmap>()
        variants.add(original)
        // main cleaned image
        val cleaned = runAdvancedPreprocessing(original)
        variants.add(cleaned)
        // different contrast/brightness combos
        variants.add(changeContrastAndBrightness(original, 1.6f, -30f))
        variants.add(changeContrastAndBrightness(original, 2.0f, -50f))
        // grayscale and sharpen combos
        variants.add(sharpen(toGrayscale(original)))
        variants.add(binaryThreshold(toGrayscale(original)))
        Log.d(TAG, "Generated ${variants.size} variants for OCR")
        return variants
    }

    // ---------- IMAGE OPS (Kotlin fallback) ----------

    private fun toGrayscale(src: Bitmap): Bitmap {
        val bmpGrayscale = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmpGrayscale)
        val paint = Paint()
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return bmpGrayscale
    }

    private fun binaryThreshold(src: Bitmap, threshold: Int = 130): Bitmap {
        val width = src.width
        val height = src.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = src.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                val value = if (gray > threshold) 255 else 0
                result.setPixel(x, y, Color.rgb(value, value, value))
            }
        }
        return result
    }

    private fun changeContrastAndBrightness(bitmap: Bitmap, contrast: Float, brightness: Float): Bitmap {
        val colorMatrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        val grayscaleMatrix = ColorMatrix().apply { setSaturation(0f) }
        grayscaleMatrix.postConcat(colorMatrix)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(grayscaleMatrix) }
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        resultBitmap.applyCanvas { drawBitmap(bitmap, 0f, 0f, paint) }
        return resultBitmap
    }

    private fun sharpen(src: Bitmap): Bitmap {
        val kernel = floatArrayOf(
            0f, -1f, 0f,
            -1f, 5f, -1f,
            0f, -1f, 0f
        )
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        val resultPixels = IntArray(width * height)
        // copy border pixels
        for (x in 0 until width) {
            resultPixels[x] = pixels[x]
            resultPixels[(height - 1) * width + x] = pixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            resultPixels[y * width] = pixels[y * width]
            resultPixels[y * width + width - 1] = pixels[y * width + width - 1]
        }
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f
                var kernelIndex = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = pixels[(y + ky) * width + (x + kx)]
                        val kv = kernel[kernelIndex++]
                        sumR += Color.red(pixel) * kv
                        sumG += Color.green(pixel) * kv
                        sumB += Color.blue(pixel) * kv
                    }
                }
                val r = sumR.coerceIn(0f, 255f).roundToInt()
                val g = sumG.coerceIn(0f, 255f).roundToInt()
                val b = sumB.coerceIn(0f, 255f).roundToInt()
                resultPixels[y * width + x] = Color.rgb(r, g, b)
            }
        }
        val resultBitmap = Bitmap.createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        resultBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return resultBitmap
    }

    // ---------- OPTIONAL: OpenCV-based preprocess (if OpenCV available) ----------
    private fun preprocessWithOpenCv(bitmap: Bitmap): Bitmap {
        // NOTE: This method requires OpenCV android libs and setup; if not present, it will throw.
        // We'll implement a simple deskew + adaptive threshold-like flow using OpenCV APIs.
        // To keep this code compile-safe even if OpenCV not present, we call it inside try/catch in runAdvancedPreprocessing.
        throw UnsupportedOperationException("OpenCV not initialized in this build. If you want OpenCV path, implement preprocessWithOpenCv using org.opencv.* APIs.")
    }
}
