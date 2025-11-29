package com.example.ekycsimulate.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.LiteModuleLoader
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
        // Use LiteModuleLoader for models exported with _save_for_lite_interpreter
        return LiteModuleLoader.load(file.absolutePath)
    }

    private fun preprocessId(idBitmap: Bitmap): FloatArray {
        return bitmapToFloatArrayCHW(idBitmap, 224, 224)
    }

    private fun preprocessFrames(frames: List<Bitmap>): FloatArray {
        // Layout: [T, C, H, W]
        val T = frames.size
        val H = 224
        val W = 224
        val HW = H * W
        val CHW = 3 * HW
        val out = FloatArray(T * CHW)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val pixels = IntArray(HW)

        for (t in 0 until T) {
            val bmp = frames[t]
            val resized = Bitmap.createScaledBitmap(bmp, W, H, true)
            resized.getPixels(pixels, 0, W, 0, 0, W, H)
            if (resized != bmp) {
                // resized.recycle()
            }

            for (i in 0 until HW) {
                val c = pixels[i]
                val r = ((c shr 16) and 0xFF) / 255.0f
                val g = ((c shr 8) and 0xFF) / 255.0f
                val b = (c and 0xFF) / 255.0f

                // Frame t, Channel 0 (R), Pixel i
                out[t * CHW + 0 * HW + i] = (r - mean[0]) / std[0]
                
                // Frame t, Channel 1 (G), Pixel i
                out[t * CHW + 1 * HW + i] = (g - mean[1]) / std[1]
                
                // Frame t, Channel 2 (B), Pixel i
                out[t * CHW + 2 * HW + i] = (b - mean[2]) / std[2]
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

    // ...

    fun runInference(frames: List<Bitmap>, idBitmap: Bitmap): Result<EkycResult> {
        Log.d("EkycModelManager", "runInference called with ${frames.size} frames")
        if (module == null) {
            Log.e("EkycModelManager", "Module is null. Attempting to reload...")
            try {
                module = loadModuleFromAssets("ekyc_mobile.pt")
            } catch (e: Exception) {
                Log.e("EkycModelManager", "Reload failed: ${e.message}")
                return Result.failure(Exception("Failed to load model: ${e.message}"))
            }
            if (module == null) {
                return Result.failure(Exception("Module is null after reload"))
            }
        }
        val mod = module!!
        
        try {
            val T = frames.size
            Log.d("EkycModelManager", "Preprocessing ${frames.size} frames...")
            // Layout: [T, C, H, W]
            val framesArr = preprocessFrames(frames)
            
            Log.d("EkycModelManager", "Preprocessing ID bitmap...")
            val idArr = preprocessId(idBitmap) // length 3*224*224

            Log.d("EkycModelManager", "Creating tensors...")
            // Shape: [1, T, 3, 224, 224] (NTCHW) - Matches export code: torch.randn(1, 16, 3, 224, 224)
            // Note: preprocessFrames produces [T, C, H, W].
            // Tensor expects [1, T, C, H, W].
            // Wait, preprocessFrames produces T*C*H*W.
            // If we use shape [1, T, 3, 224, 224], it expects T*3*224*224.
            // The layout matches.
            val framesTensor = Tensor.fromBlob(framesArr, longArrayOf(1, T.toLong(), 3, 224, 224))
            val idTensor = Tensor.fromBlob(idArr, longArrayOf(1, 3, 224, 224))

            val inputs = arrayOf(IValue.from(framesTensor), IValue.from(idTensor))
            
            Log.d("EkycModelManager", "Running forward pass...")
            val outputs = mod.forward(*inputs)
            Log.d("EkycModelManager", "Forward pass complete. Output: $outputs")
            
            if (outputs.isTuple) {
                val tuple = outputs.toTuple()
                Log.d("EkycModelManager", "Output is tuple of size ${tuple.size}")
                val livenessTensor = tuple[0].toTensor()
                val qualityTensor = tuple[1].toTensor()
                val verificationTensor = tuple[2].toTensor()

                val livenessProb = livenessTensor.dataAsFloatArray[0]
                val quality = qualityTensor.dataAsFloatArray[0]
                val verification = verificationTensor.dataAsFloatArray[0]
                
                Log.d("EkycModelManager", "Inference success: L=$livenessProb, Q=$quality, V=$verification")
                return Result.success(EkycResult(livenessProb, quality, verification))
            } else {
                Log.e("EkycModelManager", "Unexpected model output type: $outputs")
                return Result.failure(Exception("Unexpected model output type"))
            }
        } catch (e: Exception) {
            Log.e("EkycModelManager", "Model inference failed", e)
            e.printStackTrace()
            return Result.failure(e)
        }
    }
}
