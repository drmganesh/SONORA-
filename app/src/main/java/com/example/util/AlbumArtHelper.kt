package com.example.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import android.util.Size
import com.example.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AlbumArtHelper {

    private const val TAG = "AlbumArtHelper"

    // Memory cache for decoded album artwork bitmaps (max 50MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // Negative cache to prevent repeated disk I/O and JNI calls for tracks without artwork
    private val noArtCache = object : LruCache<String, Boolean>(1000) {}

    suspend fun loadAlbumArt(context: Context, song: Song?): Bitmap? = withContext(Dispatchers.IO) {
        if (song == null) return@withContext null

        // Demo tracks synthesized on device do not contain embedded image tags
        if (song.isDemoTrack || song.id < 0) return@withContext null

        val cacheKey = "art_${song.albumId}_${song.id}"
        memoryCache.get(cacheKey)?.let { return@withContext it }
        if (noArtCache.get(cacheKey) == true) return@withContext null

        var bitmap: Bitmap? = null
        try {

        // WAV files do not contain embedded artwork recognized by MediaMetadataRetriever
        val isWav = song.mimeType.contains("wav", ignoreCase = true) ||
                song.dataPath.endsWith(".wav", ignoreCase = true) ||
                song.uri.path?.endsWith(".wav", ignoreCase = true) == true

        if (isWav) {
            noArtCache.put(cacheKey, true)
            return@withContext null
        }

        // 1. Try Android 10+ (API 29+) ContentResolver loadThumbnail (uses MediaStore OS thumbnail cache directly)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && song.uri.scheme == "content") {
            try {
                bitmap = context.contentResolver.loadThumbnail(song.uri, Size(512, 512), null)
            } catch (_: Exception) {
                // MediaStore thumbnail unavailable
            }
        }

        // 2. Try legacy content://media/external/audio/albumart/<albumId>
        if (bitmap == null && song.albumId > 0 && song.uri.scheme == "content") {
            try {
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
                context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                // Legacy URI unavailable
            }
        }

        // 3. Try extracting genuine embedded picture via MediaMetadataRetriever (ID3 APIC / FLAC Picture)
        // ONLY if pre-check confirms the file actually contains embedded picture metadata
        // to prevent native MediaMetadataRetrieverJNI getEmbeddedPicture error logs.
        if (bitmap == null && mayHaveEmbeddedArt(song)) {
            try {
                val mmr = MediaMetadataRetriever()
                try {
                    if (song.dataPath.isNotBlank() && File(song.dataPath).exists()) {
                        mmr.setDataSource(song.dataPath)
                    } else {
                        mmr.setDataSource(context, song.uri)
                    }
                    val rawBytes = mmr.embeddedPicture
                    if (rawBytes != null && rawBytes.isNotEmpty()) {
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
                        var inSampleSize = 1
                        while (opts.outWidth / inSampleSize > 768 || opts.outHeight / inSampleSize > 768) {
                            inSampleSize *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply {
                            this.inSampleSize = inSampleSize
                            inPreferredConfig = Bitmap.Config.RGB_565
                        }
                        bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
                    }
                } finally {
                    try { mmr.release() } catch (_: Exception) {}
                }
            } catch (_: Exception) {
                // Extraction failed
            }
        }

        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "Failed loading album artwork for song id=${song.id}", t)
            bitmap = null
        }

        // Cache the decoded bitmap or record negative cache
        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        } else {
            noArtCache.put(cacheKey, true)
        }

        bitmap
    }

    /**
     * Efficiently checks whether an audio file has embedded image tags (e.g. ID3v2 APIC or FLAC Picture)
     * without invoking native MediaMetadataRetriever when no artwork exists.
     */
    private fun mayHaveEmbeddedArt(song: Song): Boolean {
        if (song.isDemoTrack || song.id < 0) return false
        val path = song.dataPath
        if (path.isBlank()) {
            return false
        }
        val file = File(path)
        if (!file.exists() || !file.canRead() || file.length() < 1024) {
            return false
        }

        val name = file.name.lowercase()
        if (name.endsWith(".wav") || name.endsWith(".mid") || name.endsWith(".midi") ||
            name.endsWith(".ogg") || name.endsWith(".opus")) {
            return false
        }

        if (name.endsWith(".mp3")) {
            // Fast ID3v2 APIC check: An embedded picture in MP3 is always inside an ID3v2 APIC frame.
            return try {
                file.inputStream().buffered().use { input ->
                    val header = ByteArray(10)
                    if (input.read(header) < 10) return false
                    if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                        return false // No ID3v2 tag at all
                    }
                    val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                            ((header[7].toInt() and 0x7F) shl 14) or
                            ((header[8].toInt() and 0x7F) shl 7) or
                            (header[9].toInt() and 0x7F)
                    if (tagSize <= 0) return false

                    val bytesToScan = tagSize.coerceAtMost(65536)
                    val buffer = ByteArray(bytesToScan)
                    val bytesRead = input.read(buffer)
                    if (bytesRead < 4) return false

                    val apic = "APIC".toByteArray(Charsets.US_ASCII)
                    indexOfSubarray(buffer, bytesRead, apic) != -1
                }
            } catch (_: Exception) {
                false
            }
        }

        if (name.endsWith(".flac")) {
            // Fast FLAC METADATA_BLOCK_PICTURE check (block type 6)
            return try {
                file.inputStream().buffered().use { input ->
                    val magic = ByteArray(4)
                    if (input.read(magic) < 4) return false
                    if (magic[0] != 'f'.code.toByte() || magic[1] != 'L'.code.toByte() ||
                        magic[2] != 'a'.code.toByte() || magic[3] != 'C'.code.toByte()) {
                        return false
                    }
                    var isLast = false
                    while (!isLast) {
                        val blockHeader = ByteArray(4)
                        if (input.read(blockHeader) < 4) break
                        val headerByte = blockHeader[0].toInt() and 0xFF
                        isLast = (headerByte and 0x80) != 0
                        val blockType = headerByte and 0x7F
                        val blockSize = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                                ((blockHeader[2].toInt() and 0xFF) shl 8) or
                                (blockHeader[3].toInt() and 0xFF)
                        if (blockType == 6) { // 6 = METADATA_BLOCK_PICTURE
                            return true
                        }
                        if (blockSize > 0) {
                            input.skip(blockSize.toLong())
                        }
                    }
                    false
                }
            } catch (_: Exception) {
                false
            }
        }

        return true
    }

    private fun indexOfSubarray(source: ByteArray, sourceLength: Int, target: ByteArray): Int {
        if (target.isEmpty() || sourceLength < target.size) return -1
        val max = sourceLength - target.size
        for (i in 0..max) {
            if (source[i] == target[0]) {
                var match = true
                for (j in 1 until target.size) {
                    if (source[i + j] != target[j]) {
                        match = false
                        break
                    }
                }
                if (match) return i
            }
        }
        return -1
    }
}
