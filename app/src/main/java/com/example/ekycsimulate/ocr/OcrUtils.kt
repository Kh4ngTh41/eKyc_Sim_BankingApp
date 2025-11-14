package com.example.ekycsimulate.ocr

import android.util.Log
import com.example.ekycsimulate.ui.auth.IdCardInfo
import com.google.mlkit.vision.text.Text
import java.text.Normalizer
import java.util.*
import kotlin.math.abs
import kotlin.math.max

private const val TAG = "OcrUtils"
private const val MIN_ID_LENGTH = 9
private const val MAX_ID_LENGTH = 12
private const val ID_PLACEHOLDER = 'X'
private val DOB_NORMALIZED_PATTERN = Regex("""\d{2}/\d{2}/\d{4}""")
private val RAW_DOB_PATTERN = Regex("""(\d{1,2})/(\d{1,2})/(\d{2,4})""")

private val STOP_KEYWORDS = setOf(
    "HO VA TEN",
    "HO TEN",
    "TEN",
    "NGAY SINH",
    "SINH NGAY",
    "QUE QUAN",
    "NGUYEN QUAN",
    "NOI THUONG TRU",
    "THUONG TRU",
    "CHO O",
    "NOI CU TRU",
    "DIA CHI",
    "QUOC TICH",
    "GIOI TINH",
    "DAC DIEM",
    "DAC DIEM NHAN DANG",
    "NGAY CAP",
    "NOI CAP"
)

private data class OcrLine(
    val raw: String,
    val normalized: String,
    val accentlessUpper: String,
    val index: Int,
    val top: Int,
    val left: Int,
    val bottom: Int
)

/** Normalize and remove weird characters but keep letters, numbers, comma, slash, hyphen */
fun normalizeTextForParsing(s: String): String {
    var t = s.trim()
    // normalize unicode (NFC)
    t = Normalizer.normalize(t, Normalizer.Form.NFC)
    // Replace weird control characters
    t = t.replace(Regex("[\\u00A0\\u200B\\uFEFF]"), " ")
    // Remove most punctuation except these allowed
    t = t.replace(Regex("[^\\p{L}\\p{N}\\s,./:\\-]"), "")
    t = t.replace(Regex("\\s+"), " ")
    return t.trim()
}

/** Remove Vietnamese accents for keyword comparison */
private fun removeAccents(input: String): String {
    val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
    return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
}

/** Title case (việt hoa chữ đầu từ) */
fun toTitleCase(text: String): String {
    if (text.isBlank()) return ""
    return text.lowercase(Locale("vi", "VN")).split(" ").joinToString(" ") { token ->
        token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("vi", "VN")) else it.toString() }
    }.replace(Regex("\\s+"), " ").trim()
}

/** Normalize DOB to dd/MM/yyyy if possible */
fun formatDob(dobRaw: String): String {
    var d = dobRaw.trim().replace('.', '/').replace('-', '/').replace("\\s+".toRegex(), "")
    // common patterns: dd/MM/yyyy, ddMMyyyy, yyyy/MM/dd
    val p1 = Regex("""\b(\d{2})/(\d{2})/(\d{4})\b""")
    val p2 = Regex("""\b(\d{2})(\d{2})(\d{4})\b""")
    val p3 = Regex("""\b(\d{4})/(\d{2})/(\d{2})\b""")
    p1.find(d)?.let { return "${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}" }
    p2.find(d)?.let { return "${it.groupValues[1]}/${it.groupValues[2]}/${it.groupValues[3]}" }
    p3.find(d)?.let { return "${it.groupValues[3]}/${it.groupValues[2]}/${it.groupValues[1]}" }
    return d
}

/** Attempt to extract structured fields from MLKit Text object (single-pass parse). */
fun parseOcrTextSinglePass(visionText: Text): IdCardInfo {
    val lines = visionText.textBlocks.flatMap { it.lines }
    val ocrLines = lines.mapIndexed { idx, line ->
        val norm = normalizeTextForParsing(line.text)
        val accentless = removeAccents(norm).uppercase(Locale.ROOT)
        val box = line.boundingBox
        OcrLine(
            raw = line.text,
            normalized = norm,
            accentlessUpper = accentless,
            index = idx,
            top = box?.top ?: Int.MAX_VALUE,
            left = box?.left ?: Int.MAX_VALUE,
            bottom = box?.bottom ?: Int.MAX_VALUE
        )
    }
    val texts = ocrLines.map { it.normalized }

    // Patterns
    val idPattern = Regex("""(?<!\d)(\d[\d\s]{8,14}\d)(?!\d)""") // CCCD 9-12 digits, allow spaces
    val dobPattern = Regex("""\b\d{1,2}[\/\-\.\s]\d{1,2}[\/\-\.\s]\d{2,4}\b""")

    var id = ""
    var dob = ""
    var name = ""
    var origin = ""
    var address = ""

    // find ID - prefer matches next to field keywords
    val idCandidates = ocrLines.flatMap { line ->
        idPattern.findAll(line.raw).map { match ->
            val cleaned = match.value.replace(Regex("[^0-9]"), "")
            val weight = (cleaned.length * 2) + if (line.accentlessUpper.contains("SO") || line.accentlessUpper.contains("CCCD")) 5 else 0
            cleaned to weight
        }.toList()
    }.filter { it.first.length in 9..12 }
    id = idCandidates.maxByOrNull { it.second }?.first
        ?: texts.firstOrNull { idPattern.containsMatchIn(it) }?.replace(Regex("[^0-9]"), "")
        ?: ""

    // find DOB, prefer lines after keyword
    val dobLine = ocrLines.firstOrNull { it.accentlessUpper.contains("NGAY SINH") || it.accentlessUpper.contains("SINH NGAY") }
    dob = dobLine?.let { extractFieldValue(it, ocrLines, includeFollowing = true) }
        ?.let { normalizeDateCandidate(it) }
        ?.takeIf { it.isNotBlank() }
        ?: texts.mapNotNull { candidate ->
            dobPattern.find(candidate)?.value?.let { normalizeDateCandidate(it) }
        }.firstOrNull().orEmpty()

    // find name using keyword first
    val nameLine = ocrLines.firstOrNull { it.accentlessUpper.contains("HO VA TEN") || it.accentlessUpper.contains("HO TEN") }
    name = nameLine?.let { extractFieldValue(it, ocrLines, includeFollowing = true, maxLines = 2) }
        ?.takeIf { it.isNotBlank() }
        ?: run {
            val keywordGuard = listOf("CONG HOA", "CHU NGHIA", "VIET NAM", "CAN CUOC")
            val topLines = ocrLines.sortedBy { it.top }.take(max(3, ocrLines.size / 4))
            topLines.firstOrNull {
                val text = it.normalized
                text.length > 3 && text.count { c -> c.isLetter() } >= text.length / 2 && keywordGuard.none { kw -> it.accentlessUpper.contains(kw) }
            }?.normalized ?: ""
        }

    // origin and address via keyword-based multi-line extraction
    val originLine = ocrLines.firstOrNull { it.accentlessUpper.contains("QUE QUAN") || it.accentlessUpper.contains("NGUYEN QUAN") }
    origin = originLine?.let { extractFieldValue(it, ocrLines, includeFollowing = true, maxLines = 2) }.orEmpty()

    val addressLine = ocrLines.firstOrNull {
        it.accentlessUpper.contains("NOI THUONG TRU") ||
            it.accentlessUpper.contains("THUONG TRU") ||
            it.accentlessUpper.contains("NOI CU TRU") ||
            it.accentlessUpper.contains("CHO O") ||
            it.accentlessUpper.contains("DIA CHI")
    }
    address = addressLine?.let { extractFieldValue(it, ocrLines, includeFollowing = true, maxLines = 3) }.orEmpty()

    if (origin.isBlank() || address.isBlank()) {
        // fallback: use long lines near bottom
        val longCandidates = ocrLines.sortedBy { it.bottom }.map { it.normalized }.filter { it.length > 8 }
        if (longCandidates.isNotEmpty()) {
            if (origin.isBlank() && longCandidates.size >= 2) origin = longCandidates[longCandidates.size - 2]
            if (address.isBlank()) address = longCandidates.last()
        }
    }

    // final normalization
    id = id.trim()
    name = toTitleCase(name)
    origin = toTitleCase(origin)
    address = toTitleCase(address)
    dob = formatDob(dob)

    val warnings = mutableSetOf<String>()
    if (id.length !in MIN_ID_LENGTH..MAX_ID_LENGTH) warnings += "id_length_out_of_range"
    if (name.isBlank()) warnings += "missing_name"
    if (!DOB_NORMALIZED_PATTERN.matches(dob)) warnings += "missing_dob"
    if (origin.isBlank()) warnings += "missing_origin"
    if (address.isBlank()) warnings += "missing_address"

    var confidence = 0f
    if (id.length in MIN_ID_LENGTH..MAX_ID_LENGTH) confidence += 0.4f
    if (name.isNotBlank()) confidence += 0.2f
    if (DOB_NORMALIZED_PATTERN.matches(dob)) confidence += 0.2f
    if (origin.isNotBlank()) confidence += 0.1f
    if (address.isNotBlank()) confidence += 0.1f

    Log.d(TAG, "parseOcrSingle: id=$id, name=$name, dob=$dob, origin=$origin, address=$address, conf=$confidence")
    return IdCardInfo(
        idNumber = id,
        fullName = name,
        dob = dob,
        address = address,
        origin = origin,
        source = "OCR_SINGLE",
        confidence = confidence,
        warnings = warnings.toList()
    )
}

private fun normalizeDateCandidate(candidate: String): String {
    var cleaned = candidate.trim().replace('.', '/').replace('-', '/').replace(Regex("\\s+"), " ")
    if (cleaned.count { it.isDigit() } == 8 && !cleaned.contains('/')) {
        val digits = cleaned.filter { it.isDigit() }
        cleaned = "${digits.substring(0, 2)}/${digits.substring(2, 4)}/${digits.substring(4)}"
    }
    val match = RAW_DOB_PATTERN.find(cleaned) ?: return cleaned
    val day = match.groupValues[1].padStart(2, '0')
    val month = match.groupValues[2].padStart(2, '0')
    var year = match.groupValues[3]
    if (year.length == 2) {
        val yearInt = year.toInt()
        year = if (yearInt > 30) "19$year" else "20$year"
    }
    return "$day/$month/$year"
}

private fun reconstructId(candidates: List<IdCardInfo>): Pair<String, Boolean> {
    val digitSequences = candidates.map { candidate -> candidate.idNumber.filter(Char::isDigit) }.filter { it.isNotBlank() }
    if (digitSequences.isEmpty()) return "" to false
    val targetLength = digitSequences.maxOf { it.length }
    if (targetLength < MIN_ID_LENGTH) return "" to false

    val builder = StringBuilder()
    var usedPlaceholder = false
    for (index in 0 until targetLength) {
        val digitsAtIndex = digitSequences.mapNotNull { it.getOrNull(index) }
        if (digitsAtIndex.isEmpty()) {
            builder.append(ID_PLACEHOLDER)
            usedPlaceholder = true
        } else {
            val majorityDigit = digitsAtIndex.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: digitsAtIndex.first()
            builder.append(majorityDigit)
        }
    }
    val reconstructed = builder.toString().take(MAX_ID_LENGTH)
    if (reconstructed.length !in MIN_ID_LENGTH..MAX_ID_LENGTH) return "" to false
    return reconstructed to usedPlaceholder
}

private fun extractFieldValue(
    fieldLine: OcrLine,
    allLines: List<OcrLine>,
    includeFollowing: Boolean,
    maxLines: Int = 1
): String {
    val direct = fieldLine.normalized.substringAfter(':', "").trim()
    if (!includeFollowing) {
        return direct
    }
    val builder = StringBuilder()
    if (direct.isNotBlank()) {
        builder.append(direct)
    }
    var lastBottom = fieldLine.bottom
    var appended = 0
    for (offset in 1..maxLines) {
        val candidate = allLines.getOrNull(fieldLine.index + offset) ?: break
        if (candidate.normalized.isBlank()) continue
        if (shouldStopField(candidate)) break
        val verticalGap = if (candidate.top == Int.MAX_VALUE || lastBottom == Int.MAX_VALUE) 0 else abs(candidate.top - lastBottom)
        if (verticalGap > 160) break
        if (builder.isNotEmpty()) builder.append(' ')
        builder.append(candidate.normalized)
        lastBottom = candidate.bottom
        appended++
        if (appended >= maxLines) break
    }
    val combined = builder.toString().trim()
    return if (combined.isNotBlank()) combined else direct
}

private fun shouldStopField(candidate: OcrLine): Boolean {
    if (candidate.normalized.contains(':')) {
        val beforeColon = candidate.normalized.substringBefore(':')
        if (beforeColon.length <= 20 && STOP_KEYWORDS.any { keyword -> candidate.accentlessUpper.contains(keyword) }) {
            return true
        }
    }
    return STOP_KEYWORDS.any { keyword -> candidate.accentlessUpper.contains(keyword) }
}

/** Given a list of IdCardInfo candidates, do majority vote for each field (non-empty preference). */
fun majorityVote(candidates: List<IdCardInfo>): IdCardInfo {
    if (candidates.isEmpty()) return IdCardInfo(source = "EMPTY", warnings = listOf("no_candidates"))
    fun majorityString(values: List<String>): String {
        val filtered = values.filter { it.isNotBlank() }
        if (filtered.isEmpty()) return ""
        return filtered.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: filtered.first()
    }
    var id = majorityString(candidates.map { it.idNumber })
    val name = majorityString(candidates.map { it.fullName })
    var dob = majorityString(candidates.map { it.dob })
    val origin = majorityString(candidates.map { it.origin })
    val address = majorityString(candidates.map { it.address })
    val src = "VOTED(${candidates.map { it.source }.distinct().joinToString(",")})"
    val warnings = candidates.flatMap { it.warnings }.toMutableSet()

    var placeholderUsed = false
    if (id.length !in MIN_ID_LENGTH..MAX_ID_LENGTH) {
        val (reconstructed, usedPlaceholder) = reconstructId(candidates)
        if (reconstructed.isNotBlank()) {
            id = reconstructed
            if (usedPlaceholder) {
                warnings += "id_placeholder"
                placeholderUsed = true
            }
        }
    }
    if (id.length !in MIN_ID_LENGTH..MAX_ID_LENGTH) {
        warnings += "id_length_out_of_range"
    }

    dob = formatDob(dob)
    if (!DOB_NORMALIZED_PATTERN.matches(dob)) warnings += "missing_dob"
    if (name.isBlank()) warnings += "missing_name"
    if (origin.isBlank()) warnings += "missing_origin"
    if (address.isBlank()) warnings += "missing_address"

    var confidence = candidates.map { it.confidence }.average().toFloat()
    if (id.length !in MIN_ID_LENGTH..MAX_ID_LENGTH) confidence *= 0.7f
    if (placeholderUsed) confidence *= 0.85f
    if (name.isBlank()) confidence *= 0.85f
    if (!DOB_NORMALIZED_PATTERN.matches(dob)) confidence *= 0.85f
    if (origin.isBlank()) confidence *= 0.95f
    if (address.isBlank()) confidence *= 0.95f
    confidence = confidence.coerceIn(0f, 1f)

    return IdCardInfo(
        idNumber = id,
        fullName = name,
        dob = dob,
        address = address,
        origin = origin,
        source = src,
        confidence = confidence,
        warnings = warnings.toList()
    )
}
