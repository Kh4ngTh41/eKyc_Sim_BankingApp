package com.example.ekycsimulate.ui.auth

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.example.ekycsimulate.ocr.majorityVote
import com.example.ekycsimulate.ocr.parseOcrTextSinglePass
import com.example.ekycsimulate.utils.ImageProcessor
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class IdCardInfo(
    val idNumber: String = "",
    val fullName: String = "",
    val dob: String = "",
    val address: String = "",
    val origin: String = "",
    val source: String = "N/A"
)

@Composable
fun ConfirmInfoScreen(
    croppedBitmap: Bitmap,
    onNextStep: (IdCardInfo) -> Unit
) {
    var idCardInfo by remember { mutableStateOf<IdCardInfo?>(null) }
    var isProcessing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(croppedBitmap) {
        isProcessing = true
        errorMessage = null
        withContext(Dispatchers.Default) {
            try {
                val variants = ImageProcessor.generateVariants(croppedBitmap)
                val qropts = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                val qrScanner = BarcodeScanning.getClient(qropts)
                val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                val candidates = mutableListOf<IdCardInfo>()

                // Try QR first across variants (fast)
                for ((i, bmp) in variants.withIndex()) {
                    try {
                        val input = InputImage.fromBitmap(bmp, 0)
                        val barcodes = qrScanner.process(input).await()
                        val qr = barcodes.firstOrNull()?.rawValue
                        if (!qr.isNullOrBlank()) {
                            // parse QR (implement parseQrData accordingly)
                            val parsed = parseQrData(qr)
                            idCardInfo = parsed
                            isProcessing = false
                            return@withContext
                        }
                    } catch (e: Exception) {
                        Log.w("ConfirmInfo", "QR variant $i fail: ${e.message}")
                    }
                }

                // OCR: run on each variant, parse single-pass, keep candidate list
                for ((i, bmp) in variants.withIndex()) {
                    try {
                        val visionText: Text = textRecognizer.process(InputImage.fromBitmap(bmp, 0)).await()
                        val parsed = parseOcrTextSinglePass(visionText)
                        candidates.add(parsed)
                    } catch (e: Exception) {
                        Log.w("ConfirmInfo", "OCR variant $i fail: ${e.message}")
                    }
                }

                // majority vote
                val voted = majorityVote(candidates)
                // Optionally verify some sanity checks: ID length, name length, DOB non-empty
                val sanityOk = voted.idNumber.length in 9..12 && voted.fullName.length >= 4
                idCardInfo = if (sanityOk) voted else voted.copy(source = voted.source + "/LOW_CONF")

                if (!sanityOk) {
                    errorMessage = "Không chắc chắn về dữ liệu. Vui lòng chụp lại nếu thấy sai."
                }
            } catch (e: Exception) {
                Log.e("ConfirmInfo", "Unexpected error: ${e.message}", e)
                errorMessage = "Lỗi xử lý ảnh. Vui lòng thử lại."
            } finally {
                isProcessing = false
            }
        }
    }

    var editedInfo by remember(idCardInfo) { mutableStateOf(idCardInfo) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Button(
                onClick = { editedInfo?.let(onNextStep) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = editedInfo != null && !isProcessing
            ) { Text("Tiếp tục") }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Vui lòng xác nhận thông tin", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Image(painter = remember(croppedBitmap) { BitmapPainter(croppedBitmap.asImageBitmap()) },
                contentDescription = "Cropped CCCD",
                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                contentScale = ContentScale.Fit)
            Spacer(Modifier.height(12.dp))

            when {
                isProcessing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Đang xử lý ảnh...")
                }
                errorMessage != null -> {
                    Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                editedInfo != null -> {
                    val info = editedInfo!!
                    Text("Nguồn: ${info.source}", style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.idNumber, onValueChange = { editedInfo = info.copy(idNumber = it) }, label = { Text("Số CCCD") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.fullName, onValueChange = { editedInfo = info.copy(fullName = it) }, label = { Text("Họ và tên") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.dob, onValueChange = { editedInfo = info.copy(dob = it) }, label = { Text("Ngày sinh") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.origin, onValueChange = { editedInfo = info.copy(origin = it) }, label = { Text("Quê quán") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.address, onValueChange = { editedInfo = info.copy(address = it) }, label = { Text("Nơi thường trú") }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

// Simple QR parser for Vietnamese CCCD - adjust to your QR format
private fun parseQrData(qrRaw: String): IdCardInfo {
    try {
        val cleaned = qrRaw.trim()
        val parts = when {
            '|' in cleaned -> cleaned.split('|')
            ';' in cleaned -> cleaned.split(';')
            else -> cleaned.split(Regex("\\s+"))
        }
        // heuristics: id at 0, name at 2, dob at 3, address at 5 (varies by issuer)
        val idCandidate = parts.getOrNull(0)?.replace(Regex("[^0-9]"), "") ?: ""
        val nameCandidate = parts.getOrNull(2) ?: parts.getOrNull(1) ?: ""
        val dobRaw = parts.getOrNull(3) ?: ""
        val addressCandidate = parts.getOrNull(5) ?: parts.getOrNull(4) ?: ""

        val formattedDob = if (dobRaw.length == 8 && dobRaw.all { it.isDigit() }) {
            "${dobRaw.substring(0,2)}/${dobRaw.substring(2,4)}/${dobRaw.substring(4)}"
        } else dobRaw

        return IdCardInfo(idNumber = idCandidate, fullName = nameCandidate, dob = formattedDob, address = addressCandidate, origin = "", source = "QR")
    } catch (t: Throwable) {
        Log.w("ConfirmInfo", "parseQrData fail: ${t.message}")
        return IdCardInfo(source = "QR_ERROR")
    }
}
