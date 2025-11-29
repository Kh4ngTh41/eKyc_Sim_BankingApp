package com.example.ekycsimulate.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import kotlin.math.max
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

data class EkycResult(val livenessProb: Float, val quality: Float, val verificationScore: Float)

class EkycModelManager(private val context: Context) {
    private var module: Module? = null

    init {
        try {
            module = loadModuleFromAssets("ekyc_model.pt")
        } catch (e: Exception) {
            Log.e("EkycModelManager", "Failed to load model: ${e.message}")
        }
    }

    private fun loadModuleFromAssets(assetName: String): Module? {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            // Copy from assets
            context.assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return Module.load(file.absolutePath)
    }

    private fun preprocessId(idBitmap: Bitmap): FloatArray {
        return bitmapToFloatArrayCHW(idBitmap, 224, 224)
    }

    private fun preprocessFrames(frames: List<Bitmap>): FloatArray {
        // frames expected size = 32
        val T = frames.size
        val out = FloatArray(T * 3 * 224 * 224)
        var idx = 0
        for (bmp in frames) {
            val resized = Bitmap.createScaledBitmap(bmp, 224, 224, true)
            val data = bitmapToFloatArrayCHW(resized, 224, 224)
            // torchscript expects [B, T, C, H, W] so frames order is per frame channel-major
            for (v in data) {
                out[idx++] = v
            }
        }
        return out
    }

    private fun bitmapToFloatArrayCHW(bitmap: Bitmap, width: Int, height: Int): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        resized.getPixels(pixels, 0, width, 0, 0, width, height)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val out = FloatArray(3 * width * height)
        val wh = width * height

        // channel-first: R-channel then G then B
        var rIndex = 0
        var gIndex = wh
        var bIndex = 2 * wh

        for (i in 0 until wh) {
            val c = pixels[i]
            val r = ((c shr 16) and 0xFF) / 255.0f
            val g = ((c shr 8) and 0xFF) / 255.0f
            val b = (c and 0xFF) / 255.0f

            out[rIndex++] = (r - mean[0]) / std[0]
            out[gIndex++] = (g - mean[1]) / std[1]
            out[bIndex++] = (b - mean[2]) / std[2]
        }

        return out
    }

    fun runInference(frames: List<Bitmap>, idBitmap: Bitmap): EkycResult? {
        val mod = module ?: return null
        val T = frames.size
        val framesArr = preprocessFrames(frames) // length T*3*224*224
        val idArr = preprocessId(idBitmap) // length 3*224*224

        val framesTensor = Tensor.fromBlob(framesArr, longArrayOf(1, T.toLong(), 3, 224, 224))
        val idTensor = Tensor.fromBlob(idArr, longArrayOf(1, 3, 224, 224))

        val inputs = arrayOf(IValue.from(framesTensor), IValue.from(idTensor))
        return try {
            val outputs = mod.forward(*inputs)
            if (outputs.isTuple) {
                val tuple = outputs.toTuple()
                val livenessTensor = tuple[0].toTensor()
                val qualityTensor = tuple[1].toTensor()
                val verificationTensor = tuple[2].toTensor()

                val livenessProb = livenessTensor.dataAsFloatArray[0]
                val quality = qualityTensor.dataAsFloatArray[0]
                val verification = verificationTensor.dataAsFloatArray[0]
                EkycResult(livenessProb, quality, verification)
            } else {
                Log.e("EkycModelManager", "Unexpected model output type")
                null
            }
        } catch (e: Exception) {
            Log.e("EkycModelManager", "Model inference failed: ${e.message}")
            null
        }
    }
}
