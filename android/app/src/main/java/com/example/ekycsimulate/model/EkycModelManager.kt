package com.example.ekycsimulate.model

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.LiteModuleLoader
import java.io.File
import java.io.FileOutputStream

data class EkycResult(val livenessProb: Float, val matchingScore: Float) {
    fun isPassed(threshold: Float = 0.7f): Boolean {
        return livenessProb > 0.95 && matchingScore > 0.55
    }
}

class EkycModelManager(private val context: Context) {
    private var module: Module? = null
    private val faceRecognitionManager = FaceRecognitionManager(context)

    init {
        try {
            // Priority: User mentioned ekyc_model_traced_no_opt_mobile.ptl
            module = loadModuleFromAssets("ekyc_model_traced_no_opt_mobile.ptl")
            if (module == null) {
                module = loadModuleFromAssets("ekyc_model.ptl")
            }
            if (module == null) {
                module = loadModuleFromAssets("ekyc_model_mobile.ptl")
            }        } catch (e: Exception) {
            Log.e("EkycModelManager", "Failed to load model: ${e.message}")
        }
    }

    private fun loadModuleFromAssets(assetName: String): Module? {
        val file = File(context.filesDir, assetName)
        if (!file.exists()) {
            try {
                context.assets.open(assetName).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                Log.e("EkycModelManager", "Error copying asset: $assetName", e)
                return null
            }
        }
        return try {
            LiteModuleLoader.load(file.absolutePath)
        } catch (e: Exception) {
            Log.e("EkycModelManager", "Error loading module: ${file.absolutePath}", e)
            null
        }
    }

    private fun preprocessId(idBitmap: Bitmap): FloatArray {
        return bitmapToFloatArrayCHW(idBitmap, 256, 256)
    }

    private fun preprocessFrames(frames: List<Bitmap>): FloatArray {
        // Model expects: [1, T, C, H, W]
        // We return FloatArray in T,C,H,W layout (no batch dim, will add in runInference)
        val T = frames.size
        val H = 256
        val W = 256
        val HW = H * W
        val CHW = 3 * HW
        val out = FloatArray(T * CHW)

        val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
        val std = floatArrayOf(0.229f, 0.224f, 0.225f)

        val pixels = IntArray(HW)

        for (t in 0 until T) {
            val bmp = frames[t]
            // Ensure bitmap is RGB and resized
            val resized = Bitmap.createScaledBitmap(bmp, W, H, true)
            resized.getPixels(pixels, 0, W, 0, 0, W, H)
            
            // Extract pixel data into CHW layout
            var rIdx = t * CHW + 0 * HW
            var gIdx = t * CHW + 1 * HW
            var bIdx = t * CHW + 2 * HW
            
            for (i in 0 until HW) {
                val pixel = pixels[i]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                out[rIdx++] = (r - mean[0]) / std[0]
                out[gIdx++] = (g - mean[1]) / std[1]
                out[bIdx++] = (b - mean[2]) / std[2]
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

    fun runInference(frames: List<Bitmap>, idBitmap: Bitmap): Result<EkycResult> {
        Log.d("EkycModelManager", "runInference called with ${frames.size} frames")
        if (module == null) {
            Log.e("EkycModelManager", "Module is null. Attempting to reload default...")
            // Attempt reload if needed, though init should have tried.
             module = loadModuleFromAssets("ekyc_model_traced_no_opt_mobile.ptl")
            if (module == null) {
                return Result.failure(Exception("Failed to load model PTL"))
            }
        }
        val mod = module!!
        
        try {
            val T = frames.size
            if (T <= 0) {
                return Result.failure(Exception("No frames provided for inference"))
            }
            
            // ---------------------------------------------------------
            // 1. Run PTL Model for Liveness (Ignoring PTL Matching)
            // ---------------------------------------------------------
            
            // Preprocess ID Image (Needed for PTL model input requirement, even if ignoring output)
            val idArr = preprocessId(idBitmap) 
            val idTensor = Tensor.fromBlob(idArr, longArrayOf(1, 3, 256, 256))

            // Preprocess Video Frames
            val framesArr = preprocessFrames(frames)
            val framesTensor = Tensor.fromBlob(framesArr, longArrayOf(1, T.toLong(), 3, 256, 256))

            // Run PTL Inference
            val inputs = arrayOf(IValue.from(idTensor), IValue.from(framesTensor))
            val outputs = mod.forward(*inputs)
            
            var livenessProb = 0f
            
            if (outputs.isTuple) {
                val tuple = outputs.toTuple()
                if (tuple.size >= 1) {
                     val livenessTensor = tuple[0].toTensor()
                     livenessProb = livenessTensor.dataAsFloatArray[0]
                     // Match tensor is tuple[1], but we ignore it as per requirement
                } else {
                     return Result.failure(Exception("Model output tuple too small"))
                }
            } else {
                 return Result.failure(Exception("Unexpected model output type"))
            }

            // ---------------------------------------------------------
            // 2. Run ONNX Model for Matching (InsightFace)
            // ---------------------------------------------------------
            Log.d("EkycModelManager", "Calculating matching score with ONNX...")
            
            var matchingScore = 0f
            
            // Get ID Embedding
            val idEmbedding = faceRecognitionManager.getEmbedding(idBitmap)
            
            if (idEmbedding != null) {
                var maxSim = -1f
                var bestFrameIndex = -1
                
                // Compare with each frame
                // Optimization: Maybe we don't need to check ALL frames if there are many.
                // But usually validation limits frames (e.g., 5-10).
                
                for ((index, frame) in frames.withIndex()) {
                    val frameEmbedding = faceRecognitionManager.getEmbedding(frame)
                    if (frameEmbedding != null) {
                        val sim = faceRecognitionManager.calculateCosineSimilarity(idEmbedding, frameEmbedding)
                        Log.d("EkycModelManager", "Frame $index similarity: $sim")
                        if (sim > maxSim) {
                            maxSim = sim
                            bestFrameIndex = index
                        }
                    }
                }
                
                if (maxSim > -1f) {
                    matchingScore = maxSim
                    Log.d("EkycModelManager", "Best matching score: $matchingScore (Frame $bestFrameIndex)")
                } else {
                    Log.w("EkycModelManager", "Could not calculate similarity (invalid frame embeddings)")
                }
            } else {
                 Log.e("EkycModelManager", "Failed to get ID embedding")
            }

            Log.d("EkycModelManager", "✅ Final Result: Liveness=$livenessProb, Matching=$matchingScore")
            return Result.success(EkycResult(livenessProb, matchingScore))

        } catch (e: Exception) {
            Log.e("EkycModelManager", "❌ Model inference failed", e)
            return Result.failure(e)
        }
    }
}
