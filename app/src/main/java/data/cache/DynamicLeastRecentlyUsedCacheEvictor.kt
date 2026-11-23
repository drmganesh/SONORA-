package data.cache

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

/**
 * Dynamic, thread-safe Least-Recently-Used [CacheEvictor] for Media3 [androidx.media3.datasource.cache.SimpleCache].
 *
 * Advantages over Media3's static `LeastRecentlyUsedCacheEvictor`:
 * - Supports live [updateMaxBytes] changes (e.g., slider adjustment from 0 MB to 10 GB) without
 *   releasing or recreating the underlying [androidx.media3.datasource.cache.SimpleCache] instance.
 * - Supports [enforceLimitNow] to immediately trim oldest spans when the user lowers the storage cap.
 * - Protects currently playing cache keys ([setProtectedKeys]) so active playback is never interrupted
 *   during eviction or background cleanup.
 * - Fires [onCacheMutated] so storage telemetry updates accurately from actual cache spans.
 */
@OptIn(UnstableApi::class)
class DynamicLeastRecentlyUsedCacheEvictor(
    initialMaxBytes: Long,
    private val onCacheMutated: () -> Unit = {}
) : CacheEvictor {

    private val maxBytesRef = AtomicLong(initialMaxBytes.coerceAtLeast(0L))
    private val protectedKeys = CopyOnWriteArraySet<String>()
    private val lock = Any()

    private val leastRecentlyUsed = TreeSet<CacheSpan> { lhs, rhs ->
        val d = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
        when {
            d < 0L -> -1
            d > 0L -> 1
            else -> lhs.compareTo(rhs)
        }
    }

    private var currentSize: Long = 0L

    fun getMaxBytes(): Long = maxBytesRef.get()

    fun updateMaxBytes(newMaxBytes: Long) {
        maxBytesRef.set(newMaxBytes.coerceAtLeast(0L))
    }

    fun setProtectedKeys(keys: Set<String>) {
        protectedKeys.clear()
        keys.forEach { if (it.isNotBlank()) protectedKeys.add(it) }
    }

    fun getTrackedSizeBytes(): Long = synchronized(lock) { currentSize.coerceAtLeast(0L) }

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {
        onCacheMutated()
    }

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != androidx.media3.common.C.LENGTH_UNSET.toLong() && length > 0L) {
            evictCache(cache, length)
        } else {
            evictCache(cache, 0L)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            if (leastRecentlyUsed.add(span)) {
                currentSize += span.length.coerceAtLeast(0L)
            }
        }
        evictCache(cache, 0L)
        onCacheMutated()
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        synchronized(lock) {
            if (leastRecentlyUsed.remove(span)) {
                currentSize = (currentSize - span.length.coerceAtLeast(0L)).coerceAtLeast(0L)
            }
        }
        onCacheMutated()
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        synchronized(lock) {
            if (leastRecentlyUsed.remove(oldSpan)) {
                currentSize = (currentSize - oldSpan.length.coerceAtLeast(0L)).coerceAtLeast(0L)
            }
            if (leastRecentlyUsed.add(newSpan)) {
                currentSize += newSpan.length.coerceAtLeast(0L)
            }
        }
    }

    /**
     * Immediately enforces the current [maxBytesRef] limit against [cache], evicting
     * least-recently-used non-protected spans until disk usage is within budget.
     */
    fun enforceLimitNow(cache: Cache) {
        evictCache(cache, 0L)
        onCacheMutated()
    }

    private fun evictCache(cache: Cache, requiredSpace: Long) {
        val cap = maxBytesRef.get()
        while (true) {
            val candidateToRemove: CacheSpan = synchronized(lock) {
                if (currentSize + requiredSpace <= cap || leastRecentlyUsed.isEmpty()) {
                    return
                }
                // Pick the oldest span that is NOT actively protected by ongoing playback
                val candidate = leastRecentlyUsed.firstOrNull { !protectedKeys.contains(it.key) }
                    ?: return
                candidate
            }
            try {
                cache.removeSpan(candidateToRemove)
            } catch (_: Throwable) {
                // If a span is locked by a reader, break out cleanly without interrupting playback
                return
            }
        }
    }
}
