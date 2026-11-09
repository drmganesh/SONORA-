package com.example.util

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AlbumArtHelper {

    // Memory cache for decoded album artwork bitmaps (max 50MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8
    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    suspend fun loadAlbumArt(context: Context, song: Song?): Bitmap? = withContext(Dispatchers.IO) {
        if (song == null) return@withContext null

        val cacheKey = "art_${song.albumId}_${song.id}"
        memoryCache.get(cacheKey)?.let { return@withContext it }

        var bitmap: Bitmap? = null

        // 1. Try extracting genuine embedded picture via MediaMetadataRetriever (ID3 APIC / FLAC Picture)
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
                    // Downsample if needed to prevent OutOfMemory
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
                try { mmr.release() } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            // Ignored
        }

        // 2. Try Android 10+ (API 29+) ContentResolver loadThumbnail
        if (bitmap == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                bitmap = context.contentResolver.loadThumbnail(song.uri, Size(512, 512), null)
            } catch (e: Exception) {
                // Ignored
            }
        }

        // 3. Try legacy content://media/external/audio/albumart/<albumId>
        if (bitmap == null && song.albumId > 0) {
            try {
                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    song.albumId
                )
                context.contentResolver.openInputStream(albumArtUri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                // Ignored
            }
        }

        // Cache the decoded bitmap
        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }

        bitmap
    }
}
