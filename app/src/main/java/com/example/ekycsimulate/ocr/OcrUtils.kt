package com.example.ekycsimulate.ocr

import android.util.Log
import com.example.ekycsimulate.ui.auth.IdCardInfo
import com.google.mlkit.vision.text.Text
import java.text.Normalizer
import java.util.*
import kotlin.math.max

private const val TAG = "OcrUtils"

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
    val rawLines = lines.map { it.text.trim() }
    val texts = rawLines.map { normalizeTextForParsing(it) }

    // Patterns
    val idPattern = Regex("""\b\d{9,12}\b""") // CCCD 9-12 digits
    val dobPattern = Regex("""\b\d{1,2}[\/\-\.\s]\d{1,2}[\/\-\.\s]\d{2,4}\b""")

    var id = ""
    var dob = ""
    var name = ""
    var origin = ""
    var address = ""

    // find ID
    id = texts.firstOrNull { idPattern.containsMatchIn(it) }?.let {
        it.replace(Regex("[^0-9]"), "")
    } ?: ""

    // find DOB
    dob = texts.mapNotNull { dobPattern.find(it)?.value }.firstOrNull() ?: ""

    // find name heuristically: top half lines that are letter heavy and not keywords
    val keywords = listOf("CỘNG HÒA", "CHỦ NGHĨA", "VIỆT NAM", "CĂN CƯỚC", "HỌ VÀ TÊN", "NGÀY SINH", "QUÊ QUÁN", "NƠI THƯỜNG TRÚ")
    val annotated = lines.mapIndexed { idx, line -> idx to normalizeTextForParsing(line.text) }
    val maxTop = lines.maxOfOrNull { it.boundingBox?.top ?: 0 } ?: 0
    // try top-of-card lines first
    val topLines = annotated.map { it.second }.filterIndexed { idx, _ -> idx < max(3, annotated.size / 4) }
    name = topLines.firstOrNull {
        it.length > 3 && it.count { c -> c.isLetter() } >= it.length / 2 && keywords.none { kw -> it.uppercase().contains(kw) }
    } ?: texts.firstOrNull { it.length > 4 && keywords.none { kw -> it.uppercase().contains(kw) } } ?: ""

    // find origin / address by keywords; if not found, use heuristic candidates (long lines near bottom)
    for ((i, t) in texts.withIndex()) {
        val low = t.lowercase()
        if (low.contains("quê quán") || low.contains("que quan")) {
            origin = texts.getOrNull(i + 1) ?: t.substringAfter(':', "").trim()
        }
        if (low.contains("nơi thường trú") || low.contains("noi thuong tru")) {
            address = texts.getOrNull(i + 1) ?: t.substringAfter(':', "").trim()
        }
    }

    if (origin.isBlank() || address.isBlank()) {
        // fallback: use long lines near bottom
        val longCandidates = texts.filter { it.length > 8 }
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

    Log.d(TAG, "parseOcrSingle: id=$id, name=$name, dob=$dob, origin=$origin, address=$address")
    return IdCardInfo(idNumber = id, fullName = name, dob = dob, address = address, origin = origin, source = "OCR_SINGLE")
}

/** Given a list of IdCardInfo candidates, do majority vote for each field (non-empty preference). */
fun majorityVote(candidates: List<IdCardInfo>): IdCardInfo {
    if (candidates.isEmpty()) return IdCardInfo(source = "EMPTY")
    fun majorityString(values: List<String>): String {
        val filtered = values.filter { it.isNotBlank() }
        if (filtered.isEmpty()) return ""
        return filtered.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: filtered.first()
    }
    val id = majorityString(candidates.map { it.idNumber })
    val name = majorityString(candidates.map { it.fullName })
    val dob = majorityString(candidates.map { it.dob })
    val origin = majorityString(candidates.map { it.origin })
    val address = majorityString(candidates.map { it.address })
    val src = "VOTED(${candidates.map { it.source }.distinct().joinToString(",")})"
    return IdCardInfo(idNumber = id, fullName = name, dob = dob, address = address, origin = origin, source = src)
}
