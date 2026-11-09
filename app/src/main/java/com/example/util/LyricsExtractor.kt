package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

object LyricsExtractor {

    suspend fun getOrExtractLyrics(context: Context, song: Song?): String? = withContext(Dispatchers.IO) {
        if (song == null) return@withContext null

        // 1. Return already cached custom lyrics if available
        if (!song.lyrics.isNullOrBlank()) {
            return@withContext song.lyrics
        }

        // 2. Try looking for accompanying .lrc or .txt sidecar file
        if (song.dataPath.isNotBlank()) {
            try {
                val audioFile = File(song.dataPath)
                if (audioFile.exists()) {
                    val baseName = audioFile.nameWithoutExtension
                    val parentDir = audioFile.parentFile

                    val lrcFile = File(parentDir, "$baseName.lrc")
                    if (lrcFile.exists() && lrcFile.length() > 0) {
                        return@withContext lrcFile.readText()
                    }

                    val txtFile = File(parentDir, "$baseName.txt")
                    if (txtFile.exists() && txtFile.length() > 0) {
                        return@withContext txtFile.readText()
                    }
                }
            } catch (e: Exception) {
                // Ignored
            }
        }

        // 3. Extract embedded ID3v2 USLT / SYLT from MP3 or Vorbis Comments from FLAC
        if (song.dataPath.isNotBlank() && File(song.dataPath).exists()) {
            try {
                val extracted = extractEmbeddedTagLyrics(File(song.dataPath))
                if (!extracted.isNullOrBlank()) {
                    return@withContext extracted
                }
            } catch (e: Exception) {
                // Ignored
            }
        }

        // 4. Try MediaMetadataRetriever
        try {
            val mmr = MediaMetadataRetriever()
            try {
                if (song.dataPath.isNotBlank() && File(song.dataPath).exists()) {
                    mmr.setDataSource(song.dataPath)
                } else {
                    mmr.setDataSource(context, song.uri)
                }
                // Some encoders place lyrics in commentary or writer
                val writer = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
                if (writer != null && writer.contains("\n")) {
                    return@withContext writer
                }
            } finally {
                try { mmr.release() } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            // Ignored
        }

        null
    }

    /**
     * Parses ID3v2 USLT (Unsynchronized lyric/text transcription) frame from MP3
     */
    private fun extractEmbeddedTagLyrics(file: File): String? {
        if (!file.exists() || file.length() < 128) return null

        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            raf.readFully(header)
            // Check for ID3v2
            if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                val majorVersion = header[3].toInt()
                val tagSize = (header[6].toInt() and 0x7F shl 21) or
                              (header[7].toInt() and 0x7F shl 14) or
                              (header[8].toInt() and 0x7F shl 7) or
                              (header[9].toInt() and 0x7F)

                val tagBuffer = ByteArray(tagSize.coerceAtMost(256 * 1024))
                raf.readFully(tagBuffer)

                // Search for "USLT" frame in ID3v2.3 / ID3v2.4 or "ULT" in ID3v2.2
                val usltMarker = "USLT".toByteArray(Charsets.ISO_8859_1)
                val idx = indexOf(tagBuffer, usltMarker)
                if (idx != -1 && idx + 10 < tagBuffer.size) {
                    val frameSize = if (majorVersion == 4) {
                        (tagBuffer[idx + 4].toInt() and 0x7F shl 21) or
                        (tagBuffer[idx + 5].toInt() and 0x7F shl 14) or
                        (tagBuffer[idx + 6].toInt() and 0x7F shl 7) or
                        (tagBuffer[idx + 7].toInt() and 0x7F)
                    } else {
                        (tagBuffer[idx + 4].toInt() and 0xFF shl 24) or
                        (tagBuffer[idx + 5].toInt() and 0xFF shl 16) or
                        (tagBuffer[idx + 6].toInt() and 0xFF shl 8) or
                        (tagBuffer[idx + 7].toInt() and 0xFF)
                    }

                    val contentStart = idx + 10
                    val contentEnd = (contentStart + frameSize).coerceAtMost(tagBuffer.size)
                    if (contentStart < contentEnd) {
                        // Skip encoding byte (1 byte) + language (3 bytes) + description null-terminated
                        var dataPos = contentStart + 4
                        while (dataPos < contentEnd && tagBuffer[dataPos] != 0.toByte()) {
                            dataPos++
                        }
                        dataPos++ // skip null byte

                        if (dataPos < contentEnd) {
                            val lyricBytes = tagBuffer.copyOfRange(dataPos, contentEnd)
                            return String(lyricBytes, Charsets.UTF_8).trim()
                        }
                    }
                }
            }
        }
        return null
    }

    private fun indexOf(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty()) return 0
        outer@ for (i in 0..source.size - target.size) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Parses LRC synchronized timestamps [mm:ss.xx] or [mm:ss] into LyricLine
     */
    fun parseLrcLines(rawLyrics: String?): List<LyricLine> {
        if (rawLyrics.isNullOrBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()
        val lrcRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")

        val metaRegex = Regex("""^\[[a-zA-Z]+:.*\]$""")

        for (line in rawLyrics.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || metaRegex.matches(trimmed)) {
                continue
            }
            val match = lrcRegex.find(trimmed)
            if (match != null) {
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val millis = (match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L)
                val totalMs = (minutes * 60 + seconds) * 1000 + millis
                val text = match.groupValues[4].trim()
                lines.add(LyricLine(timestampMs = totalMs, text = text))
            } else {
                // Plain line without timestamp
                lines.add(LyricLine(timestampMs = -1L, text = trimmed))
            }
        }

        return lines
    }

    fun findActiveLineIndex(parsedLines: List<LyricLine>, currentPositionMs: Long): Int {
        if (parsedLines.isEmpty()) return -1
        val hasTimestamps = parsedLines.any { it.timestampMs >= 0 }
        if (!hasTimestamps) return -1

        var active = -1
        for (i in parsedLines.indices) {
            val ts = parsedLines[i].timestampMs
            if (ts >= 0 && ts <= currentPositionMs) {
                active = i
            } else if (ts > currentPositionMs) {
                break
            }
        }
        return active
    }
}
