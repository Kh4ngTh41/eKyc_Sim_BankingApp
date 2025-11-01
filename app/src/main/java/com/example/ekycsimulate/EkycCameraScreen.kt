package com.example.ekycsimulate

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
// Import icon PhotoCamera từ gói 'extended'
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.concurrent.Executor

@Composable
fun EkycCameraScreen(
    onImageCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        CameraView(
            context = context,
            onImageCaptured = onImageCaptured
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Cần cấp quyền sử dụng camera")
        }
    }
}

@Composable
private fun CameraView(
    context: Context,
    onImageCaptured: (Uri) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember { LifecycleCameraController(context) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val executor = ContextCompat.getMainExecutor(context)
                // Truyền context vào hàm captureImage
                captureImage(context, cameraController, executor, onImageCaptured)
            }) {
                Icon(imageVector = Icons.Filled.PhotoCamera, contentDescription = "Chụp ảnh")
            }
        }
    ) { paddingValues ->
        AndroidView(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            factory = {
                PreviewView(it).apply {
                    controller = cameraController
                    cameraController.bindToLifecycle(lifecycleOwner)
                    cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                }
            }
        )
    }
}

private fun captureImage(
    // Thêm context vào tham số
    context: Context,
    cameraController: LifecycleCameraController,
    executor: Executor,
    onImageCaptured: (Uri) -> Unit
) {
    // Sử dụng context được truyền vào để tạo file
    val file = File.createTempFile("ekyc-", ".jpg", context.cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    cameraController.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                Log.d("CameraCapture", "Ảnh đã được lưu tại: ${outputFileResults.savedUri}")
                outputFileResults.savedUri?.let(onImageCaptured)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCapture", "Lỗi khi chụp ảnh: ${exception.message}", exception)
            }
        }
    )
}
