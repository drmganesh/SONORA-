package data.cache

import android.util.LruCache
import domain.color.AlbumArtPalette

/**
 * High-performance thread-safe in-memory cache for extracted album art palettes.
 */
object AlbumColorCache {
    private const val MAX_CACHE_ENTRIES = 120

    private val cache = object : LruCache<String, AlbumArtPalette>(MAX_CACHE_ENTRIES) {}

    fun get(key: String?): AlbumArtPalette? {
        if (key.isNullOrBlank()) return null
        return synchronized(cache) {
            cache.get(key)
        }
    }

    fun put(key: String?, palette: AlbumArtPalette) {
        if (key.isNullOrBlank()) return
        synchronized(cache) {
            cache.put(key, palette)
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.evictAll()
        }
    }
}
