package com.example.ekycsimulate.ui.auth

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
// camera-video imports removed; using ImageAnalysis for frames
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ekycsimulate.ui.viewmodel.EkycViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ekycsimulate.zkp.ZKPEnrollmentManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun FaceScanScreen(
    idCardInfo: IdCardInfo,
    onEnrollmentComplete: (String) -> Unit  // Callback with JSON payload
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val ekycViewModel: EkycViewModel = viewModel()
    
    var hasCameraPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        ) 
    }
    
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var capturedFrames by remember { mutableStateOf<List<Bitmap>>(listOf()) }
    var randomDigits by remember { mutableStateOf(generateRandomDigits()) }
    var approvalStatus by remember { mutableStateOf(0) } // 0: None, 1: Approved
    var enrollmentPayload by remember { mutableStateOf<String?>(null) }
    var enrollmentDataObj by remember { mutableStateOf<ZKPEnrollmentManager.EnrollmentData?>(null) }
    var zkpDetails by remember { mutableStateOf<Map<String, String>?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var inferenceResult by remember { mutableStateOf<com.example.ekycsimulate.model.EkycResult?>(null) }
    val modelManager = remember { com.example.ekycsimulate.model.EkycModelManager(context) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Xác thực khuôn mặt", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        when {
            !hasCameraPermission -> {
                Text("Cần quyền truy cập Camera để tiếp tục")
                Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Cấp quyền Camera")
                }
            }
            
            capturedImage == null -> {
                // Camera Preview
                Text("Đặt khuôn mặt vào khung hình", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                CameraPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    onImageCaptured = { bitmap ->
                        capturedImage = bitmap
                        // Start processing
                        isProcessing = true
                        scope.launch {
                            // Modular Face Detection
                            // USE THIS FOR EMULATOR TESTING:
                            val faceDetector: com.example.ekycsimulate.domain.FaceDetector = com.example.ekycsimulate.data.FakeFaceDetector()
                            
                            // USE THIS FOR REAL DEVICE:
                            // val faceDetector: com.example.ekycsimulate.domain.FaceDetector = com.example.ekycsimulate.data.MLKitFaceDetector()
                            
                            val results = faceDetector.detect(bitmap)
                            
                            if (results.isNotEmpty()) {
                                val face = results.first()
                                // Simple Liveness Check: Ensure face is looking somewhat straight
                                val isLookingStraight = kotlin.math.abs(face.headEulerAngleY) < 15 && kotlin.math.abs(face.headEulerAngleZ) < 15
                                
                                if (isLookingStraight) {
                                    approvalStatus = 1 // Approved
                                } else {
                                    // In a real app, you'd show a specific error
                                    approvalStatus = 0
                                    capturedImage = null // Reset to try again
                                }
                            } else {
                                approvalStatus = 0
                                capturedImage = null // Reset
                            }
                            isProcessing = false
                        }
                    },
                    onFrameAnalyzed = { bitmap ->
                        if (isRecording) {
                            val faceDetector: com.example.ekycsimulate.domain.FaceDetector = com.example.ekycsimulate.data.MLKitFaceDetector()
                            scope.launch {
                                val faces = faceDetector.detect(bitmap)
                                if (faces.isNotEmpty()) {
                                    val face = faces[0]
                                    val rect = face.bounds
                                    val crop = Bitmap.createBitmap(bitmap, rect.left.coerceAtLeast(0), rect.top.coerceAtLeast(0), rect.width().coerceAtLeast(1), rect.height().coerceAtLeast(1))
                                    val resized = Bitmap.createScaledBitmap(crop, 224, 224, true)
                                    capturedFrames = capturedFrames + resized
                                    if (capturedFrames.size > 128) capturedFrames = capturedFrames.takeLast(128)
                                }
                            }
                        }
                    }
                )
                // Recording controls and random digits prompt only when image captured
                Spacer(modifier = Modifier.height(8.dp))
                Text("Vui lòng đọc dãy số: $randomDigits", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        isRecording = true
                        capturedFrames = listOf()
                    }) {
                        Text("Bắt đầu quay")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        // stop capturing and start auto-processing
                        isRecording = false
                        scope.launch {
                            if (capturedFrames.isEmpty()) {
                                sendError = "Không có frame nào để xử lý"
                                return@launch
                            }
                            // Prefer ID card bitmap saved in shared ViewModel, fallback to capturedImage if needed
                            val idBmp = ekycViewModel.croppedImage ?: capturedImage
                            if (idBmp == null) {
                                sendError = "Không có ảnh CCCD để ghép với video"
                                return@launch
                            }
                            isProcessing = true
                            val frames32 = sampleOrPadFrames(capturedFrames, 32)
                            val result = modelManager.runInference(frames32, idBmp)
                            isProcessing = false
                            if (result != null) {
                                inferenceResult = result
                                sendError = "Liveness: ${result.livenessProb}, Quality: ${result.quality}, Verif: ${result.verificationScore}"
                                capturedFrames = listOf()
                                randomDigits = generateRandomDigits()
                            } else {
                                sendError = "Chạy mô hình thất bại"
                            }
                        }
                    }) {
                        Text("Dừng quay")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Frames captured: ${capturedFrames.size}", style = MaterialTheme.typography.bodySmall)
                }
                inferenceResult?.let { res ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Kết quả: Liveness=${res.livenessProb}, Quality=${res.quality}, Verif=${res.verificationScore}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            
            isProcessing -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Đang xác thực liveness...")
            }
            
            approvalStatus == 1 && enrollmentPayload == null -> {
                Text("✅ Xác thực thành công!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                Text("Approval Status: 1", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Đang tạo Zero-Knowledge Proof...", style = MaterialTheme.typography.bodyMedium)
                
                LaunchedEffect(Unit) {
                    delay(1000)
                    
                    // Generate ZKP enrollment (binds OCR data + approval)
                    val enrollmentManager = ZKPEnrollmentManager(context)
                    val enrollmentData = enrollmentManager.performEnrollment(
                        idCardInfo = idCardInfo,
                        fullName = idCardInfo.fullName,
                        phoneNumber = "", // You can add input fields for these
                        address = idCardInfo.address,
                        faceImageApproval = approvalStatus
                    )
                    
                    enrollmentDataObj = enrollmentData
                    val payload = enrollmentManager.enrollmentPayloadToJson(enrollmentData.payload)
                    enrollmentPayload = payload
                    
                    // Extract details for display
                    zkpDetails = mapOf(
                        "Public Key" to enrollmentData.payload.publicKey.take(40) + "...",
                        "Commitment" to enrollmentData.payload.commitment,
                        "ID Hash" to enrollmentData.payload.idNumberHash,
                        "Proof R" to enrollmentData.payload.proof.commitmentR.take(40) + "...",
                        "Proof Challenge" to enrollmentData.payload.proof.challenge.take(40) + "...",
                        "Proof Response" to enrollmentData.payload.proof.response.take(40) + "..."
                    )
                }
                
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
            
            enrollmentPayload != null -> {
                Text("✅ ZKP Enrollment Complete!", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Display ZKP Details
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        zkpDetails?.forEach { (key, value) ->
                            Text(key, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            SelectionContainer {
                                Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Show payload preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Server Payload (Ready to Send):", style = MaterialTheme.typography.titleSmall)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        SelectionContainer {
                            Text(
                                enrollmentPayload!!.take(200) + "\n...",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (sendError != null) {
                    Text("Lỗi: $sendError", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = { 
                        if (enrollmentDataObj != null && !isSending) {
                            isSending = true
                            sendError = null
                            scope.launch {
                                val manager = ZKPEnrollmentManager(context)
                                val result = manager.sendEnrollment(enrollmentDataObj!!.payload)
                                result.onSuccess {
                                    isSending = false
                                    onEnrollmentComplete(enrollmentPayload!!)
                                }.onFailure { e ->
                                    isSending = false
                                    sendError = e.message ?: "Unknown error"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSending
                ) {
                    if (isSending) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đang gửi...")
                    } else {
                        Text("Hoàn tất & Gửi lên Server")
                    }
                }
                
                OutlinedButton(
                    onClick = { 
                        capturedImage = null
                        approvalStatus = 0
                        enrollmentPayload = null
                        zkpDetails = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Chụp lại")
                }
                Spacer(modifier = Modifier.height(16.dp))
                // Run model on frames
                Button(onClick = {
                    if (capturedFrames.isEmpty()) { sendError = "Không có frame nào để xử lý"; return@Button }
                    isProcessing = true
                    scope.launch {
                        val frames32 = sampleOrPadFrames(capturedFrames, 32)
                        val idBmp = ekycViewModel.croppedImage ?: capturedImage ?: return@launch
                        val result = modelManager.runInference(frames32, idBmp)
                        isProcessing = false
                        if (result != null) {
                            inferenceResult = result
                            sendError = "Liveness: ${result.livenessProb}, Quality: ${result.quality}, Verif: ${result.verificationScore}"
                            capturedFrames = listOf()
                            randomDigits = generateRandomDigits()
                        } else {
                            sendError = "Chạy mô hình thất bại"
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Chạy mô hình với 32 frames")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onImageCaptured: (Bitmap) -> Unit,
    onFrameAnalyzed: ((Bitmap) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
        
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        
        try {
            cameraProvider.unbindAll()
            if (onFrameAnalyzed != null) {
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { image ->
                    val bitmap = image.toBitmap()
                    val rotated = rotateBitmap(bitmap, image.imageInfo.rotationDegrees.toFloat())
                    onFrameAnalyzed(rotated)
                    image.close()
                }
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    analyzer
                )
            } else {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Button(
        //     onClick = {
        //         val executor = Executors.newSingleThreadExecutor()
        //         imageCapture?.takePicture(
        //             executor,
        //             object : ImageCapture.OnImageCapturedCallback() {
        //                 override fun onCaptureSuccess(image: ImageProxy) {
        //                     val bitmap = image.toBitmap()
        //                     val rotatedBitmap = rotateBitmap(bitmap, image.imageInfo.rotationDegrees.toFloat())
        //                     onImageCaptured(rotatedBitmap)
        //                     image.close()
        //                 }
                        
        //                 override fun onError(exception: ImageCaptureException) {
        //                     exception.printStackTrace()
        //                 }
        //             }
        //         )
        //     },
        //     modifier = Modifier.fillMaxWidth()
        // ) {
        //     Text("Chụp ảnh")
        // }
    }
}

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun generateRandomDigits(): String {
    val rnd = java.util.Random()
    val sb = StringBuilder()
    repeat(5) { sb.append(rnd.nextInt(10)) }
    return sb.toString()
}

private fun sampleOrPadFrames(frames: List<Bitmap>, count: Int): List<Bitmap> {
    if (frames.size == count) return frames
    if (frames.isEmpty()) return List(count) { Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888) }
    if (frames.size > count) {
        // Uniformly sample 'count' frames
        val step = frames.size.toDouble() / count
        val out = mutableListOf<Bitmap>()
        var idx = 0.0
        repeat(count) {
            out.add(frames[(idx).toInt()])
            idx += step
        }
        return out
    }
    // frames.size < count -> pad by repeating last frame
    val out = frames.toMutableList()
    while (out.size < count) out.add(frames.last())
    return out
}
