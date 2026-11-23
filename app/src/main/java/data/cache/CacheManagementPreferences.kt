package data.cache

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

private val Context.cacheManagementDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_cache_management_prefs"
)

private const val CACHE_PREFS_TAG = "CacheManagementPrefs"

const val BYTES_PER_MB: Long = 1024L * 1024L
const val BYTES_PER_GB: Long = 1024L * 1024L * 1024L

/**
 * Configurable cache settings persisted in Jetpack DataStore.
 * Range for [musicCacheLimitBytes] is 0 MB to 10 GB ([MAX_MUSIC_CACHE_LIMIT_BYTES])
 * and designed so the upper bound can be expanded in future updates without schema changes.
 */
data class CacheSettings(
    val cacheMusicEnabled: Boolean = true,
    val cacheVideosEnabled: Boolean = true,
    val preloadUpcomingEnabled: Boolean = true,
    val musicCacheLimitBytes: Long = DEFAULT_MUSIC_CACHE_LIMIT_BYTES,
    val maxConfigurableMusicLimitBytes: Long = MAX_MUSIC_CACHE_LIMIT_BYTES,
    val videoCacheSoftLimitBytes: Long = DEFAULT_VIDEO_CACHE_SOFT_LIMIT_BYTES,
    val shortsCacheSoftLimitBytes: Long = DEFAULT_SHORTS_CACHE_SOFT_LIMIT_BYTES,
    val musicPreloadWindowBytes: Long = DEFAULT_MUSIC_PRELOAD_WINDOW_BYTES,
    val videoPreloadWindowBytes: Long = DEFAULT_VIDEO_PRELOAD_WINDOW_BYTES,
    val shortsPreloadWindowBytes: Long = DEFAULT_SHORTS_PRELOAD_WINDOW_BYTES,
    val videoCleanupDelayMs: Long = DEFAULT_VIDEO_CLEANUP_DELAY_MS
) {
    val clampedMusicLimitBytes: Long
        get() = musicCacheLimitBytes.coerceIn(0L, maxConfigurableMusicLimitBytes.coerceAtLeast(MAX_MUSIC_CACHE_LIMIT_BYTES))

    val isMusicWriteAllowed: Boolean
        get() = cacheMusicEnabled && clampedMusicLimitBytes > 0L

    companion object {
        const val MIN_MUSIC_CACHE_LIMIT_BYTES: Long = 0L
        const val DEFAULT_MUSIC_CACHE_LIMIT_BYTES: Long = 512L * BYTES_PER_MB // 512 MB
        const val MAX_MUSIC_CACHE_LIMIT_BYTES: Long = 10L * BYTES_PER_GB // 10 GB
        const val DEFAULT_VIDEO_CACHE_SOFT_LIMIT_BYTES: Long = 512L * BYTES_PER_MB // 512 MB
        const val DEFAULT_SHORTS_CACHE_SOFT_LIMIT_BYTES: Long = 192L * BYTES_PER_MB // 192 MB
        const val DEFAULT_MUSIC_PRELOAD_WINDOW_BYTES: Long = 512L * 1024L // 512 KB
        const val METERED_MUSIC_PRELOAD_WINDOW_BYTES: Long = 192L * 1024L // 192 KB
        const val DEFAULT_VIDEO_PRELOAD_WINDOW_BYTES: Long = 1024L * 1024L // 1 MB
        const val DEFAULT_SHORTS_PRELOAD_WINDOW_BYTES: Long = 512L * 1024L // 512 KB
        const val DEFAULT_VIDEO_CLEANUP_DELAY_MS: Long = 5L * 60L * 1000L // 5 minutes
        const val LOW_STORAGE_THRESHOLD_BYTES: Long = 250L * BYTES_PER_MB // 250 MB free minimum

        val DEFAULT = CacheSettings()

        /**
         * Clean slider stops from 0 MB up to 10 GB so the user has fine control at low sizes
         * (0 MB, 128 MB, 256 MB, 512 MB) and smooth GB steps up to 10 GB.
         */
        val MUSIC_LIMIT_STOPS_BYTES: List<Long> = listOf(
            0L,
            128L * BYTES_PER_MB,
            256L * BYTES_PER_MB,
            512L * BYTES_PER_MB,
            1L * BYTES_PER_GB,
            2L * BYTES_PER_GB,
            3L * BYTES_PER_GB,
            4L * BYTES_PER_GB,
            5L * BYTES_PER_GB,
            6L * BYTES_PER_GB,
            8L * BYTES_PER_GB,
            10L * BYTES_PER_GB
        )

        fun nearestSliderIndex(limitBytes: Long): Int {
            val clamped = limitBytes.coerceIn(0L, MAX_MUSIC_CACHE_LIMIT_BYTES)
            var bestIdx = 0
            var bestDiff = Long.MAX_VALUE
            MUSIC_LIMIT_STOPS_BYTES.forEachIndexed { idx, stop ->
                val diff = kotlin.math.abs(stop - clamped)
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestIdx = idx
                }
            }
            return bestIdx
        }

        fun bytesForSliderIndex(index: Int): Long {
            val safe = index.coerceIn(0, MUSIC_LIMIT_STOPS_BYTES.lastIndex)
            return MUSIC_LIMIT_STOPS_BYTES[safe]
        }
    }
}

/**
 * Live snapshot of actual temporary cache usage calculated directly from cache files on disk.
 * Permanent downloads are stored outside cache directories and are never included here.
 */
data class CacheStorageSnapshot(
    val musicCacheBytes: Long = 0L,
    val videoCacheBytes: Long = 0L,
    val shortsCacheBytes: Long = 0L,
    val musicCachedEntryCount: Int = 0,
    val videoCachedEntryCount: Int = 0,
    val shortsCachedEntryCount: Int = 0,
    val availableDeviceStorageBytes: Long = 0L,
    val isLowStorage: Boolean = false,
    val videoCleanupScheduledAtMs: Long? = null,
    val lastUpdatedMs: Long = 0L
) {
    val totalVideoAndShortsBytes: Long
        get() = (videoCacheBytes + shortsCacheBytes).coerceAtLeast(0L)

    val totalTemporaryCacheBytes: Long
        get() = (musicCacheBytes + videoCacheBytes + shortsCacheBytes).coerceAtLeast(0L)
}

/**
 * Formats a cache size in bytes into human-readable MB or GB (never hardcoded).
 */
fun formatCacheSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe == 0L -> "0 MB"
        safe >= BYTES_PER_GB -> {
            val gb = safe.toDouble() / BYTES_PER_GB.toDouble()
            if (gb >= 10.0 || gb % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f GB", gb)
            } else {
                String.format(Locale.US, "%.2f GB", gb)
            }
        }
        safe >= BYTES_PER_MB -> {
            val mb = safe.toDouble() / BYTES_PER_MB.toDouble()
            if (mb >= 100.0 || mb % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f MB", mb)
            } else {
                String.format(Locale.US, "%.1f MB", mb)
            }
        }
        else -> {
            val mb = safe.toDouble() / BYTES_PER_MB.toDouble()
            String.format(Locale.US, "%.2f MB", mb.coerceAtLeast(0.01))
        }
    }
}

fun formatCacheLimitLabel(limitBytes: Long): String {
    val safe = limitBytes.coerceAtLeast(0L)
    return when {
        safe == 0L -> "0 MB"
        safe >= BYTES_PER_GB -> {
            val gb = safe.toDouble() / BYTES_PER_GB.toDouble()
            if (gb % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f GB", gb)
            } else {
                String.format(Locale.US, "%.1f GB", gb)
            }
        }
        else -> {
            val mb = (safe / BYTES_PER_MB).coerceAtLeast(1L)
            "$mb MB"
        }
    }
}

class CacheManagementPreferences(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val CACHE_MUSIC_ENABLED = booleanPreferencesKey("cache_music_enabled")
        val CACHE_VIDEOS_ENABLED = booleanPreferencesKey("cache_videos_enabled")
        val PRELOAD_UPCOMING_ENABLED = booleanPreferencesKey("preload_upcoming_enabled")
        val MUSIC_CACHE_LIMIT_BYTES = longPreferencesKey("music_cache_limit_bytes")
        val MUSIC_PRELOAD_WINDOW_BYTES = longPreferencesKey("music_preload_window_bytes")
        val VIDEO_PRELOAD_WINDOW_BYTES = longPreferencesKey("video_preload_window_bytes")
        val SHORTS_PRELOAD_WINDOW_BYTES = longPreferencesKey("shorts_preload_window_bytes")
    }

    private val safeData: Flow<Preferences> = appContext.cacheManagementDataStore.data.catch { ex ->
        if (ex is IOException) {
            Log.e(CACHE_PREFS_TAG, "IO error reading cache management DataStore, falling back to defaults", ex)
            emit(emptyPreferences())
        } else {
            throw ex
        }
    }

    val settingsFlow: Flow<CacheSettings> = safeData.map { prefs ->
        val limitBytes = (prefs[Keys.MUSIC_CACHE_LIMIT_BYTES] ?: CacheSettings.DEFAULT_MUSIC_CACHE_LIMIT_BYTES)
            .coerceIn(0L, CacheSettings.MAX_MUSIC_CACHE_LIMIT_BYTES)
        CacheSettings(
            cacheMusicEnabled = prefs[Keys.CACHE_MUSIC_ENABLED] ?: true,
            cacheVideosEnabled = prefs[Keys.CACHE_VIDEOS_ENABLED] ?: true,
            preloadUpcomingEnabled = prefs[Keys.PRELOAD_UPCOMING_ENABLED] ?: true,
            musicCacheLimitBytes = limitBytes,
            musicPreloadWindowBytes = (prefs[Keys.MUSIC_PRELOAD_WINDOW_BYTES]
                ?: CacheSettings.DEFAULT_MUSIC_PRELOAD_WINDOW_BYTES).coerceIn(64L * 1024L, 4L * BYTES_PER_MB),
            videoPreloadWindowBytes = (prefs[Keys.VIDEO_PRELOAD_WINDOW_BYTES]
                ?: CacheSettings.DEFAULT_VIDEO_PRELOAD_WINDOW_BYTES).coerceIn(128L * 1024L, 8L * BYTES_PER_MB),
            shortsPreloadWindowBytes = (prefs[Keys.SHORTS_PRELOAD_WINDOW_BYTES]
                ?: CacheSettings.DEFAULT_SHORTS_PRELOAD_WINDOW_BYTES).coerceIn(128L * 1024L, 4L * BYTES_PER_MB)
        )
    }

    suspend fun readCurrent(): CacheSettings = runCatching { settingsFlow.first() }.getOrDefault(CacheSettings.DEFAULT)

    suspend fun saveSettings(settings: CacheSettings) {
        try {
            appContext.cacheManagementDataStore.edit { prefs ->
                prefs[Keys.CACHE_MUSIC_ENABLED] = settings.cacheMusicEnabled
                prefs[Keys.CACHE_VIDEOS_ENABLED] = settings.cacheVideosEnabled
                prefs[Keys.PRELOAD_UPCOMING_ENABLED] = settings.preloadUpcomingEnabled
                prefs[Keys.MUSIC_CACHE_LIMIT_BYTES] = settings.clampedMusicLimitBytes
                prefs[Keys.MUSIC_PRELOAD_WINDOW_BYTES] = settings.musicPreloadWindowBytes
                prefs[Keys.VIDEO_PRELOAD_WINDOW_BYTES] = settings.videoPreloadWindowBytes
                prefs[Keys.SHORTS_PRELOAD_WINDOW_BYTES] = settings.shortsPreloadWindowBytes
            }
        } catch (e: Exception) {
            Log.e(CACHE_PREFS_TAG, "Failed persisting cache settings", e)
        }
    }

    suspend fun updateSettings(transform: (CacheSettings) -> CacheSettings): CacheSettings {
        var updated = CacheSettings.DEFAULT
        try {
            appContext.cacheManagementDataStore.edit { prefs ->
                val current = CacheSettings(
                    cacheMusicEnabled = prefs[Keys.CACHE_MUSIC_ENABLED] ?: true,
                    cacheVideosEnabled = prefs[Keys.CACHE_VIDEOS_ENABLED] ?: true,
                    preloadUpcomingEnabled = prefs[Keys.PRELOAD_UPCOMING_ENABLED] ?: true,
                    musicCacheLimitBytes = (prefs[Keys.MUSIC_CACHE_LIMIT_BYTES]
                        ?: CacheSettings.DEFAULT_MUSIC_CACHE_LIMIT_BYTES)
                        .coerceIn(0L, CacheSettings.MAX_MUSIC_CACHE_LIMIT_BYTES),
                    musicPreloadWindowBytes = prefs[Keys.MUSIC_PRELOAD_WINDOW_BYTES]
                        ?: CacheSettings.DEFAULT_MUSIC_PRELOAD_WINDOW_BYTES,
                    videoPreloadWindowBytes = prefs[Keys.VIDEO_PRELOAD_WINDOW_BYTES]
                        ?: CacheSettings.DEFAULT_VIDEO_PRELOAD_WINDOW_BYTES,
                    shortsPreloadWindowBytes = prefs[Keys.SHORTS_PRELOAD_WINDOW_BYTES]
                        ?: CacheSettings.DEFAULT_SHORTS_PRELOAD_WINDOW_BYTES
                )
                updated = transform(current)
                prefs[Keys.CACHE_MUSIC_ENABLED] = updated.cacheMusicEnabled
                prefs[Keys.CACHE_VIDEOS_ENABLED] = updated.cacheVideosEnabled
                prefs[Keys.PRELOAD_UPCOMING_ENABLED] = updated.preloadUpcomingEnabled
                prefs[Keys.MUSIC_CACHE_LIMIT_BYTES] = updated.clampedMusicLimitBytes
                prefs[Keys.MUSIC_PRELOAD_WINDOW_BYTES] = updated.musicPreloadWindowBytes
                prefs[Keys.VIDEO_PRELOAD_WINDOW_BYTES] = updated.videoPreloadWindowBytes
                prefs[Keys.SHORTS_PRELOAD_WINDOW_BYTES] = updated.shortsPreloadWindowBytes
            }
        } catch (e: Exception) {
            Log.e(CACHE_PREFS_TAG, "Failed updating cache settings", e)
        }
        return updated
    }
}
