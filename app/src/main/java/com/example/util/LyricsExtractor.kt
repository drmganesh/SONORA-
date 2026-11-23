package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.model.Song
import domain.lyrics.LrcParser
import domain.lyrics.LyricsSourceType
import domain.lyrics.LyricsSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class ExtractedLocalLyrics(
    val rawText: String,
    val sourceType: LyricsSourceType,
    val sourceLabel: String
)

object LyricsExtractor {

    private const val MAX_LYRICS_BYTES = 512L * 1024L

    suspend fun getOrExtractLyrics(context: Context, song: Song?): String? =
        extractLocalLyricsWithSource(context, song)?.rawText

    /**
     * Extracts local lyrics along with exact origin metadata:
     * 1. Sidecar .elrc / .lrc next to local audio file
     * 2. Embedded audio metadata tags (ID3v2 SYLT/USLT, FLAC/OGG Vorbis comments, MP4/M4A ©lyr)
     * 3. Sidecar .txt plain text file
     */
    suspend fun extractLocalLyricsWithSource(
        context: Context,
        song: Song?,
        preferredSource: LyricsSourceType? = null
    ): ExtractedLocalLyrics? = withContext(Dispatchers.IO) {
        if (song == null || song.isDemoTrack) return@withContext null

        // Check sidecar LRC / ELRC first unless embedded-only requested
        if (preferredSource == null || preferredSource == LyricsSourceType.LOCAL_SIDECAR_LRC) {
            extractSidecarLrc(song)?.let { return@withContext it }
            if (preferredSource == LyricsSourceType.LOCAL_SIDECAR_LRC) return@withContext null
        }

        // Check embedded tags (ID3v2 SYLT/USLT, Vorbis Comments, MP4 ©lyr, MMR)
        if (preferredSource == null || preferredSource == LyricsSourceType.EMBEDDED_TAG) {
            extractEmbeddedLyrics(context, song)?.let { return@withContext it }
            if (preferredSource == LyricsSourceType.EMBEDDED_TAG) return@withContext null
        }

        // Check sidecar plain .txt file
        if (preferredSource == null || preferredSource == LyricsSourceType.PLAIN_TEXT) {
            extractSidecarPlainText(song)?.let { return@withContext it }
        }

        // Return pre-populated song.lyrics if present
        if (preferredSource == null && !song.lyrics.isNullOrBlank()) {
            return@withContext ExtractedLocalLyrics(
                rawText = song.lyrics,
                sourceType = LyricsSourceType.EMBEDDED_TAG,
                sourceLabel = "Embedded Track Lyrics"
            )
        }

        null
    }

    fun extractSidecarLrc(song: Song): ExtractedLocalLyrics? {
        if (song.dataPath.isBlank()) return null
        return try {
            val audioFile = File(song.dataPath)
            if (!audioFile.exists()) return null
            val baseName = audioFile.nameWithoutExtension
            val parentDir = audioFile.parentFile ?: return null

            val candidates = listOf(
                File(parentDir, "$baseName.elrc") to "Local Sidecar ELRC (${baseName}.elrc)",
                File(parentDir, "$baseName.lrc") to "Local Sidecar LRC (${baseName}.lrc)",
                File(parentDir, "$baseName.LRC") to "Local Sidecar LRC (${baseName}.LRC)"
            )
            for ((file, label) in candidates) {
                if (file.exists() && file.isFile && file.length() in 1L..MAX_LYRICS_BYTES) {
                    val content = readTextAutoEncoding(file)
                    if (content.isNotBlank()) {
                        return ExtractedLocalLyrics(
                            rawText = content,
                            sourceType = LyricsSourceType.LOCAL_SIDECAR_LRC,
                            sourceLabel = label
                        )
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun extractSidecarPlainText(song: Song): ExtractedLocalLyrics? {
        if (song.dataPath.isBlank()) return null
        return try {
            val audioFile = File(song.dataPath)
            if (!audioFile.exists()) return null
            val baseName = audioFile.nameWithoutExtension
            val parentDir = audioFile.parentFile ?: return null
            val txtFile = File(parentDir, "$baseName.txt")
            if (txtFile.exists() && txtFile.isFile && txtFile.length() in 1L..MAX_LYRICS_BYTES) {
                val content = readTextAutoEncoding(txtFile)
                if (content.isNotBlank()) {
                    return ExtractedLocalLyrics(
                        rawText = content,
                        sourceType = LyricsSourceType.PLAIN_TEXT,
                        sourceLabel = "Local Text File (${baseName}.txt)"
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun extractEmbeddedLyrics(context: Context, song: Song): ExtractedLocalLyrics? {
        if (song.isOnline) return null

        if (song.dataPath.isNotBlank()) {
            val file = File(song.dataPath)
            if (file.exists() && file.isFile && file.length() >= 32) {
                // 1. ID3v2 SYLT (synced) or USLT (unsynced) for MP3 / ID3-wrapped files
                try {
                    extractId3v2Lyrics(file)?.let { (text, tagName) ->
                        if (text.isNotBlank()) {
                            return ExtractedLocalLyrics(
                                rawText = text,
                                sourceType = LyricsSourceType.EMBEDDED_TAG,
                                sourceLabel = "Embedded $tagName Metadata"
                            )
                        }
                    }
                } catch (_: Exception) {
                }

                // 2. FLAC / OGG Vorbis Comment LYRICS / SYNCEDLYRICS / UNSYNCEDLYRICS
                val lowerPath = song.dataPath.lowercase()
                if (lowerPath.endsWith(".flac") || lowerPath.endsWith(".ogg") || lowerPath.endsWith(".opus")) {
                    try {
                        extractVorbisLyrics(file)?.let { text ->
                            if (text.isNotBlank()) {
                                return ExtractedLocalLyrics(
                                    rawText = text,
                                    sourceType = LyricsSourceType.EMBEDDED_TAG,
                                    sourceLabel = "Embedded Vorbis Lyrics"
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                // 3. MP4 / M4A ©lyr atom
                if (lowerPath.endsWith(".m4a") || lowerPath.endsWith(".mp4") || lowerPath.endsWith(".aac")) {
                    try {
                        extractMp4LyricsAtom(file)?.let { text ->
                            if (text.isNotBlank()) {
                                return ExtractedLocalLyrics(
                                    rawText = text,
                                    sourceType = LyricsSourceType.EMBEDDED_TAG,
                                    sourceLabel = "Embedded M4A Lyrics"
                                )
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        // 4. MediaMetadataRetriever fallback (skip WAV which lacks lyric text frames)
        val isWav = song.mimeType.contains("wav", ignoreCase = true) || song.dataPath.endsWith(".wav", ignoreCase = true)
        if (!isWav) {
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    if (song.dataPath.isNotBlank() && File(song.dataPath).exists()) {
                        mmr.setDataSource(song.dataPath)
                    } else {
                        mmr.setDataSource(context, song.uri)
                    }
                    val writer = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
                    if (writer != null && writer.contains("\n")) {
                        return ExtractedLocalLyrics(
                            rawText = writer.trim(),
                            sourceType = LyricsSourceType.EMBEDDED_TAG,
                            sourceLabel = "Embedded Audio Metadata"
                        )
                    }
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    private fun readTextAutoEncoding(file: File): String {
        val bytes = file.readBytes()
        if (bytes.isEmpty()) return ""
        return when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() ->
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            else -> String(bytes, Charsets.UTF_8)
        }.trim()
    }

    /**
     * Parses ID3v2 SYLT (synchronized) and USLT (unsynchronized) frames with proper UTF-8/UTF-16/ISO-8859-1 decoding.
     */
    private fun extractId3v2Lyrics(file: File): Pair<String, String>? {
        if (!file.exists() || file.length() < 32) return null

        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return null
            }

            val majorVersion = header[3].toInt() and 0xFF
            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)

            val remainingBytes = (file.length() - 10L).coerceAtLeast(0L)
            val safeSize = tagSize.toLong().coerceAtMost(remainingBytes).coerceAtMost(MAX_LYRICS_BYTES).toInt()
            if (safeSize <= 10) return null

            val tagBuffer = ByteArray(safeSize)
            raf.readFully(tagBuffer)

            // Prefer SYLT (binary synchronized lyrics) if available
            extractId3SyltFrame(tagBuffer, majorVersion)?.let { syltLrc ->
                if (syltLrc.isNotBlank()) return syltLrc to "ID3v2 SYLT"
            }

            // Fallback to USLT (unsynchronized or LRC-formatted text inside USLT)
            extractId3UsltFrame(tagBuffer, majorVersion)?.let { usltText ->
                if (usltText.isNotBlank()) return usltText to "ID3v2 USLT"
            }
        }
        return null
    }

    private fun extractId3UsltFrame(tagBuffer: ByteArray, majorVersion: Int): String? {
        val marker = "USLT".toByteArray(Charsets.ISO_8859_1)
        val idx = indexOf(tagBuffer, marker)
        if (idx == -1 || idx + 10 >= tagBuffer.size) return null

        val frameSize = readId3FrameSize(tagBuffer, idx + 4, majorVersion)
        val contentStart = idx + 10
        val contentEnd = (contentStart + frameSize).coerceAtMost(tagBuffer.size)
        if (contentStart + 5 >= contentEnd) return null

        val encodingByte = tagBuffer[contentStart].toInt() and 0xFF
        val isTwoByteNull = encodingByte == 1 || encodingByte == 2
        // Skip encoding (1) + language (3)
        var dataPos = contentStart + 4
        if (isTwoByteNull) {
            while (dataPos + 1 < contentEnd && !(tagBuffer[dataPos] == 0.toByte() && tagBuffer[dataPos + 1] == 0.toByte())) {
                dataPos += 2
            }
            dataPos += 2
        } else {
            while (dataPos < contentEnd && tagBuffer[dataPos] != 0.toByte()) {
                dataPos++
            }
            dataPos += 1
        }

        if (dataPos >= contentEnd) return null
        return decodeId3Text(tagBuffer, dataPos, contentEnd, encodingByte).trim().ifBlank { null }
    }

    private fun extractId3SyltFrame(tagBuffer: ByteArray, majorVersion: Int): String? {
        val marker = "SYLT".toByteArray(Charsets.ISO_8859_1)
        val idx = indexOf(tagBuffer, marker)
        if (idx == -1 || idx + 10 >= tagBuffer.size) return null

        val frameSize = readId3FrameSize(tagBuffer, idx + 4, majorVersion)
        val contentStart = idx + 10
        val contentEnd = (contentStart + frameSize).coerceAtMost(tagBuffer.size)
        if (contentStart + 7 >= contentEnd) return null

        val encodingByte = tagBuffer[contentStart].toInt() and 0xFF
        // encoding(1) + lang(3) + timeStampFormat(1) + contentType(1)
        val timeStampFormat = tagBuffer[contentStart + 4].toInt() and 0xFF
        if (timeStampFormat != 2) return null // 2 = milliseconds

        val isTwoByteNull = encodingByte == 1 || encodingByte == 2
        var pos = contentStart + 6
        // Skip content descriptor
        if (isTwoByteNull) {
            while (pos + 1 < contentEnd && !(tagBuffer[pos] == 0.toByte() && tagBuffer[pos + 1] == 0.toByte())) {
                pos += 2
            }
            pos += 2
        } else {
            while (pos < contentEnd && tagBuffer[pos] != 0.toByte()) {
                pos++
            }
            pos += 1
        }

        val lrcBuilder = StringBuilder()
        while (pos < contentEnd) {
            val textStart = pos
            if (isTwoByteNull) {
                while (pos + 1 < contentEnd && !(tagBuffer[pos] == 0.toByte() && tagBuffer[pos + 1] == 0.toByte())) {
                    pos += 2
                }
                val textEnd = pos
                pos += 2
                if (pos + 4 > contentEnd) break
                val tsMs = readUnsignedInt32BE(tagBuffer, pos)
                pos += 4
                val text = decodeId3Text(tagBuffer, textStart, textEnd, encodingByte).replace("\r", "").replace("\n", " ").trim()
                if (text.isNotBlank()) {
                    lrcBuilder.append(LrcParser.formatLrcTimestamp(tsMs)).append(text).append('\n')
                }
            } else {
                while (pos < contentEnd && tagBuffer[pos] != 0.toByte()) {
                    pos++
                }
                val textEnd = pos
                pos += 1
                if (pos + 4 > contentEnd) break
                val tsMs = readUnsignedInt32BE(tagBuffer, pos)
                pos += 4
                val text = decodeId3Text(tagBuffer, textStart, textEnd, encodingByte).replace("\r", "").replace("\n", " ").trim()
                if (text.isNotBlank()) {
                    lrcBuilder.append(LrcParser.formatLrcTimestamp(tsMs)).append(text).append('\n')
                }
            }
        }
        return lrcBuilder.toString().trim().ifBlank { null }
    }

    private fun readId3FrameSize(buffer: ByteArray, offset: Int, majorVersion: Int): Int {
        return if (majorVersion == 4) {
            ((buffer[offset].toInt() and 0x7F) shl 21) or
                ((buffer[offset + 1].toInt() and 0x7F) shl 14) or
                ((buffer[offset + 2].toInt() and 0x7F) shl 7) or
                (buffer[offset + 3].toInt() and 0x7F)
        } else {
            ((buffer[offset].toInt() and 0xFF) shl 24) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                (buffer[offset + 3].toInt() and 0xFF)
        }
    }

    private fun readUnsignedInt32BE(buffer: ByteArray, offset: Int): Long {
        return ((buffer[offset].toLong() and 0xFF) shl 24) or
            ((buffer[offset + 1].toLong() and 0xFF) shl 16) or
            ((buffer[offset + 2].toLong() and 0xFF) shl 8) or
            (buffer[offset + 3].toLong() and 0xFF)
    }

    private fun decodeId3Text(buffer: ByteArray, start: Int, end: Int, encodingByte: Int): String {
        val len = (end - start).coerceAtLeast(0)
        if (len == 0) return ""
        return when (encodingByte) {
            0 -> String(buffer, start, len, Charsets.ISO_8859_1)
            1 -> {
                if (len >= 2 && buffer[start] == 0xFF.toByte() && buffer[start + 1] == 0xFE.toByte()) {
                    String(buffer, start + 2, len - 2, Charsets.UTF_16LE)
                } else if (len >= 2 && buffer[start] == 0xFE.toByte() && buffer[start + 1] == 0xFF.toByte()) {
                    String(buffer, start + 2, len - 2, Charsets.UTF_16BE)
                } else {
                    String(buffer, start, len, Charset.forName("UTF-16"))
                }
            }
            2 -> String(buffer, start, len, Charsets.UTF_16BE)
            else -> String(buffer, start, len, Charsets.UTF_8)
        }
    }

    /**
     * Extracts FLAC Vorbis comment fields (SYNCEDLYRICS / LYRICS / UNSYNCEDLYRICS).
     */
    private fun extractVorbisLyrics(file: File): String? {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic, Charsets.ISO_8859_1) != "fLaC") return null

            var isLast = false
            while (!isLast && raf.filePointer + 4 <= raf.length()) {
                val blockHeader = ByteArray(4)
                raf.readFully(blockHeader)
                val first = blockHeader[0].toInt() and 0xFF
                isLast = (first and 0x80) != 0
                val blockType = first and 0x7F
                val blockLen = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                    ((blockHeader[2].toInt() and 0xFF) shl 8) or
                    (blockHeader[3].toInt() and 0xFF)

                if (blockType == 4 && blockLen in 8..MAX_LYRICS_BYTES.toInt()) {
                    val data = ByteArray(blockLen)
                    raf.readFully(data)
                    val bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    val vendorLen = bb.int
                    if (vendorLen < 0 || bb.position() + vendorLen + 4 > data.size) return null
                    bb.position(bb.position() + vendorLen)
                    val commentCount = bb.int.coerceAtMost(512)
                    var unsyncedCandidate: String? = null
                    repeat(commentCount) {
                        if (bb.remaining() < 4) return unsyncedCandidate
                        val entryLen = bb.int
                        if (entryLen <= 0 || entryLen > bb.remaining()) return unsyncedCandidate
                        val entryBytes = ByteArray(entryLen)
                        bb.get(entryBytes)
                        val entry = String(entryBytes, Charsets.UTF_8)
                        val eq = entry.indexOf('=')
                        if (eq > 0) {
                            val key = entry.substring(0, eq).uppercase()
                            val value = entry.substring(eq + 1).trim()
                            if (key == "SYNCEDLYRICS" && value.isNotBlank()) return value
                            if ((key == "LYRICS" || key == "UNSYNCEDLYRICS") && value.isNotBlank()) {
                                unsyncedCandidate = value
                            }
                        }
                    }
                    return unsyncedCandidate
                } else {
                    val nextPos = raf.filePointer + blockLen
                    if (nextPos > raf.length()) break
                    raf.seek(nextPos)
                }
            }
        }
        return null
    }

    /**
     * Scans MP4 / M4A header window for the standard `©lyr` metadata atom.
     */
    private fun extractMp4LyricsAtom(file: File): String? {
        val scanLimit = file.length().coerceAtMost(MAX_LYRICS_BYTES).toInt()
        if (scanLimit < 32) return null
        val buffer = ByteArray(scanLimit)
        RandomAccessFile(file, "r").use { raf ->
            raf.readFully(buffer)
        }
        val marker = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val idx = indexOf(buffer, marker)
        if (idx == -1) return null

        val dataMarker = "data".toByteArray(Charsets.ISO_8859_1)
        val dataIdx = indexOfFrom(buffer, dataMarker, idx + 4, (idx + 64).coerceAtMost(buffer.size))
        if (dataIdx == -1 || dataIdx < 4) return null

        val dataAtomSize = readUnsignedInt32BE(buffer, dataIdx - 4).toInt()
        val payloadStart = dataIdx + 12 // 'data'(4) + type(4) + locale(4)
        val payloadEnd = (dataIdx - 4 + dataAtomSize).coerceAtMost(buffer.size)
        if (payloadStart >= payloadEnd) return null
        return String(buffer, payloadStart, payloadEnd - payloadStart, Charsets.UTF_8).trim().ifBlank { null }
    }

    private fun indexOf(source: ByteArray, target: ByteArray): Int =
        indexOfFrom(source, target, 0, source.size)

    private fun indexOfFrom(source: ByteArray, target: ByteArray, fromIndex: Int, limitIndex: Int): Int {
        if (target.isEmpty()) return fromIndex
        val end = (limitIndex - target.size).coerceAtMost(source.size - target.size)
        outer@ for (i in fromIndex..end) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Backward-compatible LRC parser delegating to [LrcParser].
     */
    fun parseLrcLines(rawLyrics: String?): List<LyricLine> {
        return LrcParser.parse(rawLyrics).lines.map {
            LyricLine(timestampMs = it.startTimeMs, text = it.text)
        }
    }

    /**
     * Backward-compatible active line finder delegating to [LyricsSyncEngine].
     */
    fun findActiveLineIndex(parsedLines: List<LyricLine>, currentPositionMs: Long): Int {
        if (parsedLines.isEmpty()) return -1
        val synced = parsedLines.mapIndexed { idx, line ->
            domain.lyrics.SyncedLyricLine(
                index = idx,
                startTimeMs = line.timestampMs,
                endTimeMs = line.timestampMs + 5000L,
                text = line.text
            )
        }
        return LyricsSyncEngine.findActiveLineIndex(synced, currentPositionMs)
    }
}
