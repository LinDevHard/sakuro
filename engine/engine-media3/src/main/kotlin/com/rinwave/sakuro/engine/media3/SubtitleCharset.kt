package com.rinwave.sakuro.engine.media3

import android.content.Context
import android.net.Uri
import org.mozilla.universalchardet.UniversalDetector
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Locale

/**
 * Media3's text parsers read UTF-8 (or a BOM-marked UTF-16). Plenty of SRT/ASS files in the
 * wild, especially Russian (Windows-1251), Japanese (Shift_JIS) and Chinese (GBK) releases,
 * are in a legacy code page and would show as mojibake. External subtitle files are therefore
 * re-encoded to UTF-8 before Media3 sees them.
 */
internal object SubtitleCharset {

    /** Text formats that carry no reliable encoding declaration of their own. */
    private val textExtensions = setOf("srt", "ass", "ssa", "vtt", "webvtt", "txt", "sub")

    private const val MAX_BYTES = 16 * 1024 * 1024
    private const val BUFFER_SIZE = 64 * 1024
    private const val SUBTITLE_CACHE_DIR = "subtitles-utf8"

    /**
     * Returns a `file://` URI of a UTF-8 copy, or null when the original is already fine (UTF-8,
     * BOM-marked, not a text format, unreadable or undecidable). Runs on an IO thread.
     */
    fun normalizedCopy(context: Context, uri: String, title: String): String? = runCatching {
        val extension = title.substringAfterLast('.', "").ifBlank { uri.substringAfterLast('.', "") }.lowercase()
        if (extension !in textExtensions) return null
        val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            // readNBytes() is API 33+; a bounded copy keeps a huge "subtitle" out of memory.
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (out.size() <= MAX_BYTES) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        } ?: return null
        if (bytes.size > MAX_BYTES || hasUnicodeBom(bytes) || isValidUtf8(bytes)) return null
        val detected = listOfNotNull(detect(bytes)?.let { it to confidenceOf(it) })
        val charset = chooseCharset(detected, Locale.getDefault().language) ?: return null
        val text = String(bytes, Charset.forName(charset))
        val directory = File(context.cacheDir, SUBTITLE_CACHE_DIR).apply { mkdirs() }
        val target = File(directory, "${sha1(bytes)}.$extension")
        if (!target.exists()) target.writeText(text, Charsets.UTF_8)
        Uri.fromFile(target).toString()
    }.getOrNull()

    /** Mozilla's universal charset detector; null when it cannot decide. */
    private fun detect(bytes: ByteArray): String? {
        val detector = UniversalDetector()
        detector.handleData(bytes, 0, bytes.size)
        detector.dataEnd()
        return detector.detectedCharset
    }

    fun hasUnicodeBom(bytes: ByteArray): Boolean = when {
        bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> true
        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> true
        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> true
        else -> false
    }

    fun isValidUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (ignored: CharacterCodingException) {
        false
    }

    /**
     * Picks a charset from detector candidates (name to 0..100 confidence). Short files give
     * the detector little to go on and Latin-1 often "wins" for Cyrillic text, so when the top
     * guess is weak, a plausible candidate typical for the device language is preferred.
     */
    fun chooseCharset(candidates: List<Pair<String, Int>>, deviceLanguage: String): String? {
        val usable = candidates.filter { (name, _) -> name.isSupportedCharset() && !name.isUtf() }
        val top = usable.maxByOrNull { it.second } ?: return localeFallback[deviceLanguage]?.firstOrNull()
        if (top.second >= CONFIDENT) return top.first
        val preferred = localeFallback[deviceLanguage].orEmpty()
        val localeMatch = usable
            .filter { (name, confidence) -> confidence >= PLAUSIBLE && preferred.any { it.equals(name, ignoreCase = true) } }
            .maxByOrNull { it.second }
        // A weak Latin guess on a device whose language uses another script: trust the language.
        val weakLatin = top.first.uppercase() in weakGuesses
        return localeMatch?.first ?: preferred.firstOrNull()?.takeIf { weakLatin } ?: top.first
    }

    private fun String.isUtf(): Boolean = startsWith("UTF", ignoreCase = true)

    private fun String.isSupportedCharset(): Boolean = runCatching { Charset.isSupported(this) }.getOrDefault(false)

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * The detector gives no score, only its pick; a Latin-1 pick is the usual "could not tell"
     * answer, so it is treated as weak and may yield to the device language's code page.
     */
    internal fun confidenceOf(charset: String): Int =
        if (charset.uppercase() in weakGuesses) WEAK else CONFIDENT

    private val weakGuesses = setOf("ISO-8859-1", "WINDOWS-1252", "US-ASCII")
    private const val WEAK = 30
    private const val CONFIDENT = 60
    private const val PLAUSIBLE = 10

    /** Legacy encodings subtitle files in that language usually come in. */
    private val localeFallback = mapOf(
        "ru" to listOf("windows-1251", "KOI8-R"),
        "uk" to listOf("windows-1251", "KOI8-R"),
        "be" to listOf("windows-1251"),
        "bg" to listOf("windows-1251"),
        "sr" to listOf("windows-1251"),
        "ja" to listOf("Shift_JIS", "EUC-JP"),
        "zh" to listOf("GB18030", "Big5"),
        "ko" to listOf("EUC-KR"),
        "pl" to listOf("windows-1250", "ISO-8859-2"),
        "cs" to listOf("windows-1250", "ISO-8859-2"),
        "tr" to listOf("windows-1254", "ISO-8859-9"),
        "el" to listOf("windows-1253", "ISO-8859-7"),
        "ar" to listOf("windows-1256", "ISO-8859-6"),
        "he" to listOf("windows-1255", "ISO-8859-8"),
    )
}
