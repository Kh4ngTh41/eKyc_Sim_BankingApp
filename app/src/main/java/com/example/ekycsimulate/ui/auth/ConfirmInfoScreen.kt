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
    val source: String = "N/A",
    val confidence: Float = 0f,
    val warnings: List<String> = emptyList()
)

@Composable
fun ConfirmInfoScreen(
    croppedBitmap: Bitmap,
    onNextStep: (IdCardInfo) -> Unit,
    onRetake: () -> Unit
) {
    var idCardInfo by remember { mutableStateOf<IdCardInfo?>(null) }
    var isProcessing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var requiresRetake by remember { mutableStateOf(false) }

    LaunchedEffect(croppedBitmap) {
        isProcessing = true
        errorMessage = null
        requiresRetake = false
        withContext(Dispatchers.Default) {
            try {
                val prep = ImageProcessor.prepareForOcr(croppedBitmap)
                val variants = prep.variants
                val globalWarnings = mutableSetOf<String>()

                if (prep.diagnostics.isOverexposed) {
                    globalWarnings += "exposure_overexposed"
                }
                if (prep.diagnostics.isUnderexposed) {
                    globalWarnings += "exposure_underexposed"
                }
                if (prep.diagnostics.shouldRequestRecapture) {
                    globalWarnings += "exposure_request_retake"
                }

                val qropts = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                val qrScanner = BarcodeScanning.getClient(qropts)
                val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                val candidates = mutableListOf<IdCardInfo>()
                var variantProcessed = 0

                // Try QR first across variants (fast)
                for ((i, bmp) in variants.withIndex()) {
                    try {
                        val input = InputImage.fromBitmap(bmp, 0)
                        val barcodes = qrScanner.process(input).await()
                        val qr = barcodes.firstOrNull()?.rawValue
                        if (!qr.isNullOrBlank()) {
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
                        val diag = ImageProcessor.analyzeIllumination(bmp)
                        if (diag.isSeverelyOverexposed) {
                            globalWarnings += "variant_saturated"
                            Log.w("ConfirmInfo", "Skip variant $i due to severe glare (brightRatio=${diag.brightRatio})")
                            continue
                        }
                        val visionText: Text = textRecognizer.process(InputImage.fromBitmap(bmp, 0)).await()
                        val parsed = parseOcrTextSinglePass(visionText)
                        candidates.add(parsed)
                        variantProcessed++
                    } catch (e: Exception) {
                        Log.w("ConfirmInfo", "OCR variant $i fail: ${e.message}")
                    }
                }

                if (variantProcessed == 0 && candidates.isEmpty()) {
                    errorMessage = "Không thể đọc được CCCD do ảnh bị hắt sáng hoặc che khuất. Vui lòng chụp lại."
                    idCardInfo = null
                    requiresRetake = true
                    return@withContext
                }

                // majority vote
                val voted = majorityVote(candidates)
                val finalWarnings = (voted.warnings + globalWarnings).distinct()
                val adjustedConfidence = (if (globalWarnings.isEmpty()) voted.confidence else (voted.confidence * 0.85f)).coerceIn(0f, 1f)
                val finalInfo = voted.copy(warnings = finalWarnings, confidence = adjustedConfidence)

                val needsManualReview = finalInfo.confidence < 0.6f ||
                    finalInfo.idNumber.isBlank() ||
                    finalInfo.idNumber.any { !it.isDigit() } ||
                    finalInfo.fullName.isBlank() ||
                    finalInfo.dob.isBlank()

                val updatedSource = if (needsManualReview && !finalInfo.source.contains("REVIEW")) {
                    finalInfo.source + "/REVIEW"
                } else finalInfo.source

                idCardInfo = finalInfo.copy(source = updatedSource)
                requiresRetake = needsManualReview

                errorMessage = when {
                    needsManualReview && finalWarnings.contains("exposure_overexposed") ->
                        "Ảnh có vùng bị hắt sáng khiến dữ liệu thiếu. Hãy chụp lại ở góc khác."
                    needsManualReview ->
                        "Không chắc chắn về dữ liệu. Vui lòng kiểm tra và chụp lại nếu thấy sai."
                    finalWarnings.contains("exposure_request_retake") ->
                        "Ảnh có thể bị lóa, bạn nên cân nhắc chụp lại nếu thông tin chưa rõ."
                    else -> null
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
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                if (requiresRetake) {
                    Text(
                        "Không thu thập đủ thông tin bắt buộc. Vui lòng chụp lại CCCD.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onRetake,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing
                    ) { Text("Chụp lại CCCD") }
                } else {
                    Button(
                        onClick = { editedInfo?.let(onNextStep) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedInfo != null && !isProcessing
                    ) { Text("Tiếp tục") }
                }
            }
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
                    if (info.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        val messages = info.warnings.map(::mapWarningToMessage).distinct()
                        for (message in messages) {
                            Text("[!] $message", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.idNumber, onValueChange = {
                        errorMessage = null
                        val updatedWarnings = info.warnings.filterNot { code -> code == "id_length_out_of_range" || code == "id_placeholder" }
                        editedInfo = info.copy(idNumber = it, warnings = updatedWarnings)
                    }, label = { Text("Số CCCD") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.fullName, onValueChange = {
                        errorMessage = null
                        val updatedWarnings = info.warnings.filterNot { code -> code == "missing_name" }
                        editedInfo = info.copy(fullName = it, warnings = updatedWarnings)
                    }, label = { Text("Họ và tên") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.dob, onValueChange = {
                        errorMessage = null
                        val updatedWarnings = info.warnings.filterNot { code -> code == "missing_dob" }
                        editedInfo = info.copy(dob = it, warnings = updatedWarnings)
                    }, label = { Text("Ngày sinh") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.origin, onValueChange = {
                        errorMessage = null
                        val updatedWarnings = info.warnings.filterNot { code -> code == "missing_origin" }
                        editedInfo = info.copy(origin = it, warnings = updatedWarnings)
                    }, label = { Text("Quê quán") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = info.address, onValueChange = {
                        errorMessage = null
                        val updatedWarnings = info.warnings.filterNot { code -> code == "missing_address" }
                        editedInfo = info.copy(address = it, warnings = updatedWarnings)
                    }, label = { Text("Nơi thường trú") }, modifier = Modifier.fillMaxWidth())
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

        return IdCardInfo(idNumber = idCandidate, fullName = nameCandidate, dob = formattedDob, address = addressCandidate, origin = "", source = "QR", confidence = 0.95f)
    } catch (t: Throwable) {
        Log.w("ConfirmInfo", "parseQrData fail: ${t.message}")
        return IdCardInfo(source = "QR_ERROR")
    }
}

private fun mapWarningToMessage(code: String): String = when (code) {
    "exposure_overexposed" -> "Ảnh có vùng bị hắt sáng, OCR có thể bỏ sót ký tự."
    "exposure_underexposed" -> "Ảnh hơi tối, hãy đảm bảo đủ ánh sáng."
    "exposure_request_retake" -> "Đề xuất chụp lại ảnh do ánh sáng không phù hợp."
    "variant_saturated" -> "Một số biến thể ảnh bị lóa đã được loại bỏ khỏi xử lý."
    "id_length_out_of_range" -> "Số CCCD chưa đúng định dạng 12 ký tự."
    "id_placeholder" -> "Số CCCD chứa ký tự không rõ, 'X' đánh dấu vị trí cần bổ sung."
    "missing_name" -> "Không nhận diện được Họ và tên."
    "missing_dob" -> "Không nhận diện được Ngày sinh."
    "missing_origin" -> "Không nhận diện được Quê quán."
    "missing_address" -> "Không nhận diện được Nơi thường trú."
    "no_candidates" -> "Không tạo được bản OCR hợp lệ, cần chụp lại."
    else -> "Cần kiểm tra lại thông tin: $code"
}
