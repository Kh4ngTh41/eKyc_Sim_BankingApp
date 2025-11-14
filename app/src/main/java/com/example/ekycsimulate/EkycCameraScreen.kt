// In file: app/src/main/java/com/example/ekycsimulate/EkycCameraScreen.kt
package com.example.ekycsimulate

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.InputStream
import java.util.concurrent.Executor
import kotlin.math.max
import kotlin.math.min

@Composable
fun EkycCameraScreen(
    // Callback nhận bitmap đã cắt hoặc chọn
    onImageCropped: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // State để lưu kích thước của PreviewView để tính toán cropRect
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val cropRect: Rect? = remember(previewSize) {
        if (previewSize == IntSize.Zero) return@remember null
        val frameWidth = previewSize.width * 0.9f
        val frameHeight = frameWidth * (54f / 85.6f) // Tỷ lệ thẻ CCCD
        val left = (previewSize.width - frameWidth) / 2
        val top = (previewSize.height - frameHeight) / 2
        Rect(left, top, left + frameWidth, top + frameHeight)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> hasCameraPermission = isGranted }

    // Photo picker (Android 13+ no permission needed). Falls back automatically.
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bitmap = uriToBitmap(context, uri)
            bitmap?.let {
                // Center crop to ID card aspect 85.6:54 ~ 1.585
                val cropped = centerCropToCardAspect(it)
                pickedBitmap = cropped
                onImageCropped(cropped)
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraView(
                onPreviewSizeChanged = { previewSize = it },
                cropRect = cropRect,
                onImageCaptured = { fullImageUri, imageRotation ->
                    cropRect?.let { rect ->
                        Log.d("EkycCameraScreen", "Bắt đầu cắt ảnh...")
                        val croppedBitmap = cropImage(context, fullImageUri, rect, imageRotation, previewSize)
                        onImageCropped(croppedBitmap)
                        pickedBitmap = croppedBitmap
                    } ?: Log.e("EkycCameraScreen", "Không thể cắt ảnh vì cropRect null")
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Cần cấp quyền sử dụng camera")
            }
        }

        // Hiển thị ảnh đã chọn/chụp trong khung (preview overlay)
        pickedBitmap?.let { bmp ->
            cropRect?.let { r ->
                val density = LocalDensity.current
                val leftDp = with(density) { r.left.toDp() }
                val topDp = with(density) { r.top.toDp() }
                val widthDp = with(density) { r.width.toDp() }
                val heightDp = with(density) { r.height.toDp() }
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "CCCD đã chọn",
                    modifier = Modifier
                        .offset(x = leftDp, y = topDp)
                        .width(widthDp)
                        .height(heightDp)
                )
            }
        }

        // Nút chọn ảnh từ thư viện (bổ sung, nút chụp ảnh vẫn ở trong CameraView)
        FilledTonalButton(
            onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) { Text("Chọn ảnh CCCD") }
    }
}

@Composable
private fun CameraView(
    onPreviewSizeChanged: (IntSize) -> Unit,
    cropRect: Rect?,
    // Callback giờ trả về cả Uri và góc xoay của ảnh
    onImageCaptured: (Uri, Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { LifecycleCameraController(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { onPreviewSizeChanged(it) }, // Lấy kích thước của PreviewView
            factory = {
                PreviewView(it).apply {
                    this.controller = cameraController
                    // Đặt chế độ scale để khớp với cách tính toán của chúng ta
                    this.scaleType = PreviewView.ScaleType.FILL_CENTER
                    cameraController.bindToLifecycle(lifecycleOwner)
                    cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                }
            }
        )

        // Chỉ vẽ overlay khi đã có cropRect
        cropRect?.let {
            CameraOverlay(frameRect = it)
        }

        Text(
            text = "Vui lòng đặt CCCD vào trong khung",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
        )

        FloatingActionButton(
            onClick = {
                val executor = ContextCompat.getMainExecutor(context)
                captureImage(context, cameraController, executor, onImageCaptured)
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
        ) {
            Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = "Chụp ảnh")
        }
    }
}

@Composable
fun CameraOverlay(frameRect: Rect) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val outerPath = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
        val innerPath = Path().apply { addRect(frameRect) }

        clipPath(Path.combine(PathOperation.Difference, outerPath, innerPath)) {
            drawRect(Color.Black.copy(alpha = 0.5f))
        }

        drawRect(
            color = Color.Green,
            topLeft = frameRect.topLeft,
            size = frameRect.size,
            style = Stroke(width = 5.dp.toPx())
        )
    }
}

private fun captureImage(
    context: Context,
    cameraController: LifecycleCameraController,
    executor: Executor,
    onImageCaptured: (Uri, Int) -> Unit
) {
    val file = File.createTempFile("ekyc-full-", ".jpg", context.cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    cameraController.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                val savedUri = outputFileResults.savedUri
                if (savedUri != null) {
                    val image = try {
                        context.contentResolver.openInputStream(savedUri)?.use {
                            androidx.exifinterface.media.ExifInterface(it)
                        }
                    } catch (e: Exception) {
                        null
                    }
                    val rotation = image?.rotationDegrees ?: 0
                    Log.d("CameraCapture", "Ảnh đã lưu tại: $savedUri, Góc xoay: $rotation")
                    onImageCaptured(savedUri, rotation)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCapture", "Lỗi khi chụp ảnh: ${exception.message}", exception)
            }
        }
    )
}

// Decode uri to bitmap
private fun uriToBitmap(context: Context, uri: Uri): Bitmap? = try {
    val stream: InputStream? = context.contentResolver.openInputStream(uri)
    stream.use { BitmapFactory.decodeStream(it) }
} catch (e: Exception) {
    Log.e("uriToBitmap", "Decode failed: ${e.message}")
    null
}

// Center crop bitmap to ID card aspect ratio (85.6:54 ≈ 1.585)
private fun centerCropToCardAspect(src: Bitmap): Bitmap {
    val targetRatio = 85.6f / 54f
    val srcRatio = src.width.toFloat() / src.height.toFloat()
    return if (srcRatio > targetRatio) {
        // too wide -> crop sides
        val newWidth = (src.height * targetRatio).toInt()
        val x = (src.width - newWidth) / 2
        Bitmap.createBitmap(src, x, 0, newWidth, src.height)
    } else {
        // too tall -> crop top/bottom
        val newHeight = (src.width / targetRatio).toInt()
        val y = (src.height - newHeight) / 2
        Bitmap.createBitmap(src, 0, y, src.width, newHeight)
    }
}

// HÀM CẮT ẢNH ĐÃ ĐƯỢC SỬA LẠI HOÀN CHỈNH
private fun cropImage(
    context: Context,
    uri: Uri,
    cropRect: Rect,
    imageRotation: Int,
    previewSize: IntSize
): Bitmap {
    val inputStream = context.contentResolver.openInputStream(uri)
    val originalBitmap = BitmapFactory.decodeStream(inputStream)

    // 1. Xoay ảnh gốc về đúng hướng hiển thị
    val matrix = Matrix().apply { postRotate(imageRotation.toFloat()) }
    val rotatedBitmap = Bitmap.createBitmap(
        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
    )

    // 2. Tính toán tỷ lệ giữa PreviewView và ảnh thật
    // Do dùng scaleType = FILL_CENTER, một trong hai chiều của ảnh sẽ bị cắt bớt
    // để lấp đầy PreviewView mà không làm méo ảnh.
    val scaleX = rotatedBitmap.width.toFloat() / previewSize.width
    val scaleY = rotatedBitmap.height.toFloat() / previewSize.height

    // Tỷ lệ scale cuối cùng là tỷ lệ nhỏ hơn, vì đó là chiều được lấp đầy hoàn toàn
    val scale = min(scaleX, scaleY)

    // Tọa độ bắt đầu của vùng ảnh được hiển thị trên PreviewView
    val offsetX = (rotatedBitmap.width - previewSize.width * scale) / 2f
    val offsetY = (rotatedBitmap.height - previewSize.height * scale) / 2f

    // 3. Tính tọa độ của khung cắt (màu xanh) trên ảnh thật
    val cropXOnBitmap = cropRect.left * scale + offsetX
    val cropYOnBitmap = cropRect.top * scale + offsetY
    val cropWidthOnBitmap = cropRect.width * scale
    val cropHeightOnBitmap = cropRect.height * scale

    // 4. Đảm bảo tọa độ cắt không nằm ngoài ảnh thật
    val finalCropX = max(0f, cropXOnBitmap).toInt()
    val finalCropY = max(0f, cropYOnBitmap).toInt()

    val finalCropWidth = if (finalCropX + cropWidthOnBitmap > rotatedBitmap.width) rotatedBitmap.width - finalCropX else cropWidthOnBitmap.toInt()
    val finalCropHeight = if (finalCropY + cropHeightOnBitmap > rotatedBitmap.height) rotatedBitmap.height - finalCropY else cropHeightOnBitmap.toInt()

    // 5. Kiểm tra lần cuối để tránh crash
    if (finalCropWidth <= 0 || finalCropHeight <= 0) {
        Log.e("CropImage", "Kích thước cắt không hợp lệ! Trả về ảnh gốc.")
        return rotatedBitmap
    }

    Log.d("CropImage", "Thực hiện cắt tại (x,y,w,h): ($finalCropX, $finalCropY, $finalCropWidth, $finalCropHeight) trên ảnh kích thước ${rotatedBitmap.width}x${rotatedBitmap.height}")

    // 6. Thực hiện cắt và trả về
    return Bitmap.createBitmap(
        rotatedBitmap,
        finalCropX,
        finalCropY,
        finalCropWidth,
        finalCropHeight
    )
}
