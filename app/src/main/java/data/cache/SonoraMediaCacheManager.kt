package data.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.StatFs
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.SimpleCache
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single shared Media3 cache manager for SONORA.
 *
 * Guarantees:
 * - Single process-wide initialization per cache directory (no two [SimpleCache] instances lock or write to the same dir).
 * - Physically isolated subdirectories under `cacheDir/sonora_media_cache/{music,video,shorts}`,
 *   completely separate from permanent downloaded music and videos.
 * - Dynamic LRU eviction for Music cache (0 MB .. 10 GB) without interrupting active playback.
 * - Real disk storage calculation (never hardcoded).
 * - Turning caching OFF stops new writes on the next media load immediately.
 * - Cancellable 5-minute delayed Video & Shorts cache cleanup once both Music and Video players close.
 * - Network- and storage-aware bounded preloading via Media3 [CacheWriter] (prefers Wi-Fi, respects Data Saver & low storage).
 */
@OptIn(UnstableApi::class)
class SonoraMediaCacheManager internal constructor(
    context: Context,
    private val rootCacheDir: File = File(context.applicationContext.cacheDir, ROOT_DIR_NAME),
    private val preferences: CacheManagementPreferences = CacheManagementPreferences(context.applicationContext),
    private val workManagerEnqueuer: ((delayMs: Long) -> Unit)? = null,
    private val workManagerCanceller: (() -> Unit)? = null,
    private val storageBytesProvider: (() -> Long)? = null,
    private val networkSnapshotProvider: (() -> NetworkConditionSnapshot)? = null
) {

    data class NetworkConditionSnapshot(
        val isConnected: Boolean,
        val isMetered: Boolean,
        val isDataSaverEnabled: Boolean
    )

    enum class CacheNamespace(val dirName: String) {
        MUSIC("music"),
        VIDEO("video"),
        SHORTS("shorts")
    }

    private val tag = "SonoraMediaCacheManager"
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val cacheLock = Any()
    private val released = AtomicBoolean(false)

    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var musicSimpleCache: SimpleCache? = null
    private var videoSimpleCache: SimpleCache? = null
    private var shortsSimpleCache: SimpleCache? = null

    private var musicEvictor: DynamicLeastRecentlyUsedCacheEvictor? = null
    private var videoEvictor: DynamicLeastRecentlyUsedCacheEvictor? = null
    private var shortsEvictor: DynamicLeastRecentlyUsedCacheEvictor? = null

    val musicCacheDir: File get() = File(rootCacheDir, CacheNamespace.MUSIC.dirName)
    val videoCacheDir: File get() = File(rootCacheDir, CacheNamespace.VIDEO.dirName)
    val shortsCacheDir: File get() = File(rootCacheDir, CacheNamespace.SHORTS.dirName)

    private val _settings = MutableStateFlow(CacheSettings.DEFAULT)
    val settings: StateFlow<CacheSettings> = _settings.asStateFlow()

    private val _storageSnapshot = MutableStateFlow(CacheStorageSnapshot())
    val storageSnapshot: StateFlow<CacheStorageSnapshot> = _storageSnapshot.asStateFlow()

    @Volatile
    private var activeMusicCacheKey: String? = null

    @Volatile
    private var activeVideoCacheKeys: Set<String> = emptySet()

    @Volatile
    private var isMusicPlayerOpen: Boolean = false

    @Volatile
    private var isVideoPlayerOpen: Boolean = false

    @Volatile
    private var hasObservedPlayerSession: Boolean = false

    private val cleanupLock = Any()
    private var delayedVideoCleanupJob: Job? = null
    private var debouncedRefreshJob: Job? = null

    private val activePreloadJobs = ConcurrentHashMap<String, Job>()

    init {
        // Observe persisted DataStore settings and synchronize live LRU limits
        scope.launch {
            preferences.settingsFlow.collect { loaded ->
                _settings.value = loaded
                applyMusicLimitToEvictor(loaded.clampedMusicLimitBytes, enforceNow = false)
            }
        }
        // Compute real initial storage snapshot off the main thread
        scope.launch {
            refreshStorageSnapshotInternal()
        }
    }

    private fun ensureDatabaseProviderLocked(): StandaloneDatabaseProvider {
        databaseProvider?.let { return it }
        return StandaloneDatabaseProvider(appContext).also { databaseProvider = it }
    }

    /**
     * Initializes (once) and returns the Music [SimpleCache].
     * Always call off the main thread when possible; safe to call from Media3 loader threads.
     */
    fun getMusicCache(): SimpleCache = synchronized(cacheLock) {
        musicSimpleCache?.let { return it }
        val dir = musicCacheDir.apply { if (!exists()) mkdirs() }
        val evictor = DynamicLeastRecentlyUsedCacheEvictor(
            initialMaxBytes = _settings.value.clampedMusicLimitBytes,
            onCacheMutated = { scheduleDebouncedStorageRefresh() }
        ).also {
            activeMusicCacheKey?.let { key -> it.setProtectedKeys(setOf(key)) }
            musicEvictor = it
        }
        SimpleCache(dir, evictor, ensureDatabaseProviderLocked()).also {
            musicSimpleCache = it
            released.set(false)
        }
    }

    /**
     * Initializes (once) and returns the temporary Video [SimpleCache].
     */
    fun getVideoCache(): SimpleCache = synchronized(cacheLock) {
        videoSimpleCache?.let { return it }
        val dir = videoCacheDir.apply { if (!exists()) mkdirs() }
        val effectiveCap = computeSafeVideoCeilingBytes(_settings.value.videoCacheSoftLimitBytes)
        val evictor = DynamicLeastRecentlyUsedCacheEvictor(
            initialMaxBytes = effectiveCap,
            onCacheMutated = { scheduleDebouncedStorageRefresh() }
        ).also {
            if (activeVideoCacheKeys.isNotEmpty()) it.setProtectedKeys(activeVideoCacheKeys)
            videoEvictor = it
        }
        SimpleCache(dir, evictor, ensureDatabaseProviderLocked()).also {
            videoSimpleCache = it
            released.set(false)
        }
    }

    /**
     * Initializes (once) and returns the temporary Shorts [SimpleCache].
     */
    fun getShortsCache(): SimpleCache = synchronized(cacheLock) {
        shortsSimpleCache?.let { return it }
        val dir = shortsCacheDir.apply { if (!exists()) mkdirs() }
        val effectiveCap = computeSafeVideoCeilingBytes(_settings.value.shortsCacheSoftLimitBytes)
        val evictor = DynamicLeastRecentlyUsedCacheEvictor(
            initialMaxBytes = effectiveCap,
            onCacheMutated = { scheduleDebouncedStorageRefresh() }
        ).also {
            if (activeVideoCacheKeys.isNotEmpty()) it.setProtectedKeys(activeVideoCacheKeys)
            shortsEvictor = it
        }
        SimpleCache(dir, evictor, ensureDatabaseProviderLocked()).also {
            shortsSimpleCache = it
            released.set(false)
        }
    }

    private fun computeSafeVideoCeilingBytes(configuredSoftLimit: Long): Long {
        val available = queryAvailableStorageBytes()
        if (available <= CacheSettings.LOW_STORAGE_THRESHOLD_BYTES) {
            return (32L * BYTES_PER_MB).coerceAtMost(configuredSoftLimit)
        }
        val tenPercentFree = (available / 10L).coerceAtLeast(64L * BYTES_PER_MB)
        return minOf(configuredSoftLimit, tenPercentFree)
    }

    // --- Cache Policy & DataSource Factories ---

    /**
     * Verifies whether caching is permitted for a given stream URL and content properties.
     * Local files, live streams, and DRM-protected streams are never written to temporary cache.
     */
    fun isCachePermitted(
        url: String?,
        isLive: Boolean = false,
        hasDrm: Boolean = false
    ): Boolean {
        if (url.isNullOrBlank() || isLive || hasDrm) return false
        val lower = url.trim().lowercase()
        if (lower.startsWith("file://") || lower.startsWith("content://") || lower.startsWith("asset://")) {
            return false
        }
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    /**
     * Deterministic stable cache key for online music tracks so replays reuse cached audio
     * even when upstream signed stream URLs rotate or expire.
     */
    fun buildMusicCacheKey(contentUrl: String, streamId: String = ""): String {
        val normalizedUrl = contentUrl.trim().lowercase()
        val normalizedStream = streamId.trim().lowercase().ifBlank { "default" }
        return "music_${sha256Short("$normalizedUrl|$normalizedStream")}"
    }

    /**
     * Deterministic stable cache key for online videos & Shorts (separate namespaces).
     */
    fun buildVideoCacheKey(videoId: String, streamTag: String, isShort: Boolean): String {
        val prefix = if (isShort) "shorts" else "video"
        val cleanId = videoId.trim().ifBlank { "unknown" }
        val cleanTag = streamTag.trim().ifBlank { "default" }
        return "${prefix}_${sha256Short("$cleanId|$cleanTag")}"
    }

    /**
     * Creates a Media3 [CacheDataSource.Factory] for online music playback.
     * Evaluated on every new track load so turning Cache Music OFF (or setting 0 MB)
     * stops all new writes on the next load while still allowing smooth playback.
     */
    fun createMusicDataSourceFactory(
        upstreamFactory: DataSource.Factory,
        allowCacheWrite: Boolean = shouldAllowMusicCacheWrites()
    ): DataSource.Factory {
        val cache = getMusicCache()
        val sinkFactory = if (allowCacheWrite) {
            CacheDataSink.Factory()
                .setCache(cache)
                .setFragmentSize(2L * BYTES_PER_MB)
        } else {
            null // Read-only: stops new cache writes on next load
        }
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(sinkFactory)
            .setCacheKeyFactory { dataSpec ->
                dataSpec.key ?: buildFallbackKey("music", dataSpec.uri)
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * Creates a Media3 [CacheDataSource.Factory] for online video or Shorts playback.
     * Evaluated on every new video load so turning Cache Videos OFF stops new writes on the next load.
     */
    fun createVideoDataSourceFactory(
        upstreamFactory: DataSource.Factory,
        isShort: Boolean,
        allowCacheWrite: Boolean = shouldAllowVideoCacheWrites()
    ): DataSource.Factory {
        val cache = if (isShort) getShortsCache() else getVideoCache()
        val sinkFactory = if (allowCacheWrite) {
            CacheDataSink.Factory()
                .setCache(cache)
                .setFragmentSize(2L * BYTES_PER_MB)
        } else {
            null // Read-only: stops new cache writes on next load
        }
        val ns = if (isShort) "shorts" else "video"
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(sinkFactory)
            .setCacheKeyFactory { dataSpec ->
                dataSpec.key ?: buildFallbackKey(ns, dataSpec.uri)
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun shouldAllowMusicCacheWrites(): Boolean {
        val current = _settings.value
        if (!current.isMusicWriteAllowed) return false
        return queryAvailableStorageBytes() > CacheSettings.LOW_STORAGE_THRESHOLD_BYTES
    }

    fun shouldAllowVideoCacheWrites(): Boolean {
        val current = _settings.value
        if (!current.cacheVideosEnabled) return false
        return queryAvailableStorageBytes() > CacheSettings.LOW_STORAGE_THRESHOLD_BYTES
    }

    /**
     * Checks whether a given cache key already has cached bytes in the specified namespace.
     */
    fun getCachedBytesForKey(namespace: CacheNamespace, cacheKey: String): Long {
        if (cacheKey.isBlank()) return 0L
        val cache = when (namespace) {
            CacheNamespace.MUSIC -> getMusicCache()
            CacheNamespace.VIDEO -> getVideoCache()
            CacheNamespace.SHORTS -> getShortsCache()
        }
        return runCatching {
            cache.getCachedSpans(cacheKey).sumOf { it.length.coerceAtLeast(0L) }
        }.getOrDefault(0L)
    }

    fun isKeyCached(namespace: CacheNamespace, cacheKey: String): Boolean {
        return getCachedBytesForKey(namespace, cacheKey) > 0L
    }

    // --- Active Playback Protection & Delayed Video Cleanup ---

    fun setActiveMusicCacheKey(cacheKey: String?) {
        activeMusicCacheKey = cacheKey
        musicEvictor?.setProtectedKeys(if (cacheKey.isNullOrBlank()) emptySet() else setOf(cacheKey))
    }

    fun setActiveVideoCacheKeys(cacheKeys: Set<String>) {
        val cleaned = cacheKeys.filter { it.isNotBlank() }.toSet()
        activeVideoCacheKeys = cleaned
        videoEvictor?.setProtectedKeys(cleaned)
        shortsEvictor?.setProtectedKeys(cleaned)
    }

    fun onMusicPlayerStateChanged(isOpen: Boolean) {
        isMusicPlayerOpen = isOpen
        if (isOpen) {
            hasObservedPlayerSession = true
            cancelScheduledVideoCleanup()
        } else {
            activeMusicCacheKey = null
            musicEvictor?.setProtectedKeys(emptySet())
            evaluateDelayedVideoCleanup()
        }
    }

    fun onVideoPlayerStateChanged(isOpen: Boolean) {
        isVideoPlayerOpen = isOpen
        if (isOpen) {
            hasObservedPlayerSession = true
            cancelScheduledVideoCleanup()
        } else {
            activeVideoCacheKeys = emptySet()
            videoEvictor?.setProtectedKeys(emptySet())
            shortsEvictor?.setProtectedKeys(emptySet())
            cancelPreloadsByPrefix("video_")
            cancelPreloadsByPrefix("shorts_")
            evaluateDelayedVideoCleanup()
        }
    }

    fun areBothPlayersClosed(): Boolean = !isMusicPlayerOpen && !isVideoPlayerOpen

    private fun evaluateDelayedVideoCleanup() {
        if (!areBothPlayersClosed()) {
            cancelScheduledVideoCleanup()
            return
        }
        val delayMs = _settings.value.videoCleanupDelayMs.coerceAtLeast(0L)
        val scheduledAt = System.currentTimeMillis() + delayMs
        synchronized(cleanupLock) {
            delayedVideoCleanupJob?.cancel()
            delayedVideoCleanupJob = scope.launch {
                if (delayMs > 0L) {
                    delay(delayMs)
                }
                executeScheduledVideoCleanupIfPlayersClosed()
            }
        }
        _storageSnapshot.update { it.copy(videoCleanupScheduledAtMs = scheduledAt) }
        enqueueWorkManagerVideoCleanup(delayMs)
    }

    fun cancelScheduledVideoCleanup() {
        synchronized(cleanupLock) {
            delayedVideoCleanupJob?.cancel()
            delayedVideoCleanupJob = null
        }
        _storageSnapshot.update { it.copy(videoCleanupScheduledAtMs = null) }
        cancelWorkManagerVideoCleanup()
    }

    /**
     * Executed either by the cancellable in-process timer or [SonoraVideoCacheCleanupWorker]
     * 5 minutes after both players close. Returns true if temporary video/Shorts cache was cleared.
     */
    suspend fun executeScheduledVideoCleanupIfPlayersClosed(): Boolean {
        if (!areBothPlayersClosed()) {
            Log.d(tag, "Skipped delayed video cleanup because a player reopened")
            return false
        }
        clearVideoAndShortsCache(protectActiveKeys = false)
        _storageSnapshot.update { it.copy(videoCleanupScheduledAtMs = null) }
        return true
    }

    private fun enqueueWorkManagerVideoCleanup(delayMs: Long) {
        if (workManagerEnqueuer != null) {
            workManagerEnqueuer.invoke(delayMs)
            return
        }
        runCatching {
            val request = OneTimeWorkRequestBuilder<SonoraVideoCacheCleanupWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                SonoraVideoCacheCleanupWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }.onFailure {
            Log.w(tag, "WorkManager unavailable for video cleanup scheduling; relying on coroutine timer", it)
        }
    }

    private fun cancelWorkManagerVideoCleanup() {
        if (workManagerCanceller != null) {
            workManagerCanceller.invoke()
            return
        }
        runCatching {
            WorkManager.getInstance(appContext)
                .cancelUniqueWork(SonoraVideoCacheCleanupWorker.UNIQUE_WORK_NAME)
        }
    }

    // --- Bounded Network- & Storage-Aware Preloading ---

    /**
     * Cancels active background preloads so newly initiated foreground playback gets full bandwidth.
     */
    fun cancelBackgroundPreloadsForForegroundPlayback() {
        val entries = activePreloadJobs.entries.toList()
        entries.forEach { (key, job) ->
            job.cancel()
            activePreloadJobs.remove(key, job)
        }
    }

    private fun cancelPreloadsByPrefix(prefix: String) {
        activePreloadJobs.entries.toList().forEach { (key, job) ->
            if (key.startsWith(prefix)) {
                job.cancel()
                activePreloadJobs.remove(key, job)
            }
        }
    }

    /**
     * Evaluates whether preloading should proceed given user settings, storage space, and network state.
     * Returns the permitted byte window (0L means skip preloading).
     */
    fun computeAllowedPreloadWindowBytes(namespace: CacheNamespace): Long {
        val current = _settings.value
        if (!current.preloadUpcomingEnabled) return 0L
        when (namespace) {
            CacheNamespace.MUSIC -> if (!current.isMusicWriteAllowed) return 0L
            CacheNamespace.VIDEO, CacheNamespace.SHORTS -> if (!current.cacheVideosEnabled) return 0L
        }
        val availableBytes = queryAvailableStorageBytes()
        if (availableBytes <= CacheSettings.LOW_STORAGE_THRESHOLD_BYTES) {
            return 0L
        }
        val net = queryNetworkConditions()
        if (!net.isConnected || net.isDataSaverEnabled) {
            return 0L
        }
        return when (namespace) {
            CacheNamespace.MUSIC -> {
                val base = if (net.isMetered) {
                    minOf(current.musicPreloadWindowBytes, CacheSettings.METERED_MUSIC_PRELOAD_WINDOW_BYTES)
                } else {
                    current.musicPreloadWindowBytes
                }
                minOf(base, current.clampedMusicLimitBytes)
            }
            CacheNamespace.VIDEO -> {
                // Prefer Wi-Fi for large video preloads; avoid mobile data waste
                if (net.isMetered) 0L else current.videoPreloadWindowBytes
            }
            CacheNamespace.SHORTS -> {
                if (net.isMetered) {
                    minOf(current.shortsPreloadWindowBytes, CacheSettings.METERED_MUSIC_PRELOAD_WINDOW_BYTES)
                } else {
                    current.shortsPreloadWindowBytes
                }
            }
        }
    }

    /**
     * Preloads a small configurable byte window of an upcoming online music track into the Music [SimpleCache].
     * Deduplicates concurrent/redundant requests and handles network failures safely.
     */
    fun preloadMusicWindow(
        cacheKey: String,
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        upstreamFactory: DataSource.Factory
    ): Job? {
        if (!isCachePermitted(streamUrl)) return null
        val windowBytes = computeAllowedPreloadWindowBytes(CacheNamespace.MUSIC)
        if (windowBytes <= 0L) return null
        return launchBoundedPreload(
            namespace = CacheNamespace.MUSIC,
            cacheKey = cacheKey,
            streamUrl = streamUrl,
            headers = headers,
            windowBytes = windowBytes,
            upstreamFactory = upstreamFactory
        )
    }

    /**
     * Preloads a small configurable byte window of an upcoming Video or Short into its dedicated [SimpleCache].
     */
    fun preloadVideoWindow(
        cacheKey: String,
        streamUrl: String,
        headers: Map<String, String> = emptyMap(),
        upstreamFactory: DataSource.Factory,
        isShort: Boolean
    ): Job? {
        if (!isCachePermitted(streamUrl)) return null
        val ns = if (isShort) CacheNamespace.SHORTS else CacheNamespace.VIDEO
        val windowBytes = computeAllowedPreloadWindowBytes(ns)
        if (windowBytes <= 0L) return null
        return launchBoundedPreload(
            namespace = ns,
            cacheKey = cacheKey,
            streamUrl = streamUrl,
            headers = headers,
            windowBytes = windowBytes,
            upstreamFactory = upstreamFactory
        )
    }

    private fun launchBoundedPreload(
        namespace: CacheNamespace,
        cacheKey: String,
        streamUrl: String,
        headers: Map<String, String>,
        windowBytes: Long,
        upstreamFactory: DataSource.Factory
    ): Job? {
        if (cacheKey.isBlank() || windowBytes <= 0L) return null
        // Avoid duplicate in-flight requests for the same key
        val existing = activePreloadJobs[cacheKey]
        if (existing != null && existing.isActive) return existing

        val job = scope.launch {
            try {
                // Skip if we already cached at least the target preload window
                val alreadyCached = getCachedBytesForKey(namespace, cacheKey)
                if (alreadyCached >= windowBytes) {
                    return@launch
                }

                val dataSourceFactory = when (namespace) {
                    CacheNamespace.MUSIC -> createMusicDataSourceFactory(upstreamFactory, allowCacheWrite = true)
                    CacheNamespace.VIDEO -> createVideoDataSourceFactory(upstreamFactory, isShort = false, allowCacheWrite = true)
                    CacheNamespace.SHORTS -> createVideoDataSourceFactory(upstreamFactory, isShort = true, allowCacheWrite = true)
                }
                val cacheDataSource = dataSourceFactory.createDataSource() as? CacheDataSource
                    ?: return@launch

                val specBuilder = DataSpec.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .setKey(cacheKey)
                    .setPosition(0L)
                    .setLength(windowBytes)
                if (headers.isNotEmpty()) {
                    specBuilder.setHttpRequestHeaders(headers)
                }

                val writer = CacheWriter(
                    cacheDataSource,
                    specBuilder.build(),
                    ByteArray(32 * 1024),
                    null
                )
                writer.cache()
                refreshStorageSnapshotInternal()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                // Best-effort preload: network drops or expired stream URLs fail quietly
                Log.d(tag, "Preload skipped/interrupted for $cacheKey (${t.javaClass.simpleName})")
            } finally {
                activePreloadJobs.remove(cacheKey)
            }
        }
        activePreloadJobs[cacheKey] = job
        return job
    }

    // --- Settings Updates, Limit Enforcement & Safe Clearing ---

    suspend fun setCacheMusicEnabled(enabled: Boolean): CacheSettings {
        val updated = preferences.updateSettings { it.copy(cacheMusicEnabled = enabled) }
        _settings.value = updated
        if (!enabled) {
            cancelPreloadsByPrefix("music_")
        }
        return updated
    }

    suspend fun setCacheVideosEnabled(enabled: Boolean): CacheSettings {
        val updated = preferences.updateSettings { it.copy(cacheVideosEnabled = enabled) }
        _settings.value = updated
        if (!enabled) {
            cancelPreloadsByPrefix("video_")
            cancelPreloadsByPrefix("shorts_")
        }
        return updated
    }

    suspend fun setPreloadUpcomingEnabled(enabled: Boolean): CacheSettings {
        val updated = preferences.updateSettings { it.copy(preloadUpcomingEnabled = enabled) }
        _settings.value = updated
        if (!enabled) {
            cancelBackgroundPreloadsForForegroundPlayback()
        }
        return updated
    }

    /**
     * Updates the Music Cache Limit (0 MB .. 10 GB), persists to DataStore, updates the live
     * LRU evictor, and enforces the new cap immediately on [Dispatchers.IO].
     * When [limitBytes] == 0L and [clearExistingWhenZero] is true, clears existing temporary music cache.
     */
    suspend fun setMusicCacheLimitBytes(
        limitBytes: Long,
        clearExistingWhenZero: Boolean = true
    ): CacheSettings = withContext(Dispatchers.IO) {
        val clamped = limitBytes.coerceIn(0L, _settings.value.maxConfigurableMusicLimitBytes)
        val updated = preferences.updateSettings { it.copy(musicCacheLimitBytes = clamped) }
        _settings.value = updated
        musicEvictor?.updateMaxBytes(clamped)
        val openMusicCache = synchronized(cacheLock) { musicSimpleCache }
        if (openMusicCache != null) {
            runCatching { musicEvictor?.enforceLimitNow(openMusicCache) }
        }
        if (clamped == 0L) {
            cancelPreloadsByPrefix("music_")
            if (clearExistingWhenZero) {
                clearMusicCache(protectActiveKey = true)
            }
        }
        refreshStorageSnapshotInternal()
        updated
    }

    private fun applyMusicLimitToEvictor(newLimitBytes: Long, enforceNow: Boolean) {
        val evictor = musicEvictor ?: return
        evictor.updateMaxBytes(newLimitBytes)
        if (enforceNow) {
            val cache = synchronized(cacheLock) { musicSimpleCache }
            if (cache != null) {
                scope.launch {
                    runCatching { evictor.enforceLimitNow(cache) }
                    refreshStorageSnapshotInternal()
                }
            }
        }
    }

    /**
     * Removes ONLY temporary music cache files inside `cacheDir/sonora_media_cache/music/`.
     * Permanent downloaded songs in SAF / filesDir are NEVER touched.
     * Active playback is protected when [protectActiveKey] is true.
     */
    suspend fun clearMusicCache(protectActiveKey: Boolean = true): Long = withContext(Dispatchers.IO) {
        cancelPreloadsByPrefix("music_")
        val before = calculateNamespaceBytes(CacheNamespace.MUSIC)
        val protectedKey = if (protectActiveKey) activeMusicCacheKey else null
        clearNamespaceInternal(CacheNamespace.MUSIC, protectedKeys = setOfNotNull(protectedKey))
        refreshStorageSnapshotInternal()
        val after = calculateNamespaceBytes(CacheNamespace.MUSIC)
        (before - after).coerceAtLeast(0L)
    }

    /**
     * Removes ONLY temporary online video and Shorts cache files inside
     * `cacheDir/sonora_media_cache/{video,shorts}/`.
     * Permanent authorized video downloads are NEVER touched.
     */
    suspend fun clearVideoAndShortsCache(protectActiveKeys: Boolean = true): Long = withContext(Dispatchers.IO) {
        cancelPreloadsByPrefix("video_")
        cancelPreloadsByPrefix("shorts_")
        val before = calculateNamespaceBytes(CacheNamespace.VIDEO) + calculateNamespaceBytes(CacheNamespace.SHORTS)
        val protected = if (protectActiveKeys) activeVideoCacheKeys else emptySet()
        clearNamespaceInternal(CacheNamespace.VIDEO, protectedKeys = protected)
        clearNamespaceInternal(CacheNamespace.SHORTS, protectedKeys = protected)
        refreshStorageSnapshotInternal()
        val after = calculateNamespaceBytes(CacheNamespace.VIDEO) + calculateNamespaceBytes(CacheNamespace.SHORTS)
        (before - after).coerceAtLeast(0L)
    }

    private fun clearNamespaceInternal(namespace: CacheNamespace, protectedKeys: Set<String>) {
        val cache: SimpleCache? = synchronized(cacheLock) {
            when (namespace) {
                CacheNamespace.MUSIC -> musicSimpleCache
                CacheNamespace.VIDEO -> videoSimpleCache
                CacheNamespace.SHORTS -> shortsSimpleCache
            }
        }
        val dir = when (namespace) {
            CacheNamespace.MUSIC -> musicCacheDir
            CacheNamespace.VIDEO -> videoCacheDir
            CacheNamespace.SHORTS -> shortsCacheDir
        }

        if (cache != null) {
            val keys = runCatching { cache.keys.toList() }.getOrDefault(emptyList())
            keys.forEach { key ->
                if (!protectedKeys.contains(key)) {
                    runCatching { cache.removeResource(key) }
                }
            }
        } else if (dir.exists() && isInsideRootCacheDir(dir)) {
            // Cache has not been opened in this process yet; safely remove temporary chunk files on disk
            deleteDirectoryContents(dir)
        }
    }

    /**
     * Recomputes real storage usage from actual cache files on disk and updates [storageSnapshot].
     */
    suspend fun refreshStorageSnapshot(): CacheStorageSnapshot = withContext(Dispatchers.IO) {
        refreshStorageSnapshotInternal()
    }

    private fun scheduleDebouncedStorageRefresh() {
        debouncedRefreshJob?.cancel()
        debouncedRefreshJob = scope.launch {
            delay(250L)
            refreshStorageSnapshotInternal()
        }
    }

    private fun refreshStorageSnapshotInternal(): CacheStorageSnapshot {
        val musicBytes = calculateNamespaceBytes(CacheNamespace.MUSIC)
        val videoBytes = calculateNamespaceBytes(CacheNamespace.VIDEO)
        val shortsBytes = calculateNamespaceBytes(CacheNamespace.SHORTS)

        val musicCount = synchronized(cacheLock) { musicSimpleCache }
            ?.let { runCatching { it.keys.size }.getOrDefault(0) }
            ?: countCacheContentFiles(musicCacheDir)
        val videoCount = synchronized(cacheLock) { videoSimpleCache }
            ?.let { runCatching { it.keys.size }.getOrDefault(0) }
            ?: countCacheContentFiles(videoCacheDir)
        val shortsCount = synchronized(cacheLock) { shortsSimpleCache }
            ?.let { runCatching { it.keys.size }.getOrDefault(0) }
            ?: countCacheContentFiles(shortsCacheDir)

        val available = queryAvailableStorageBytes()
        val snapshot = CacheStorageSnapshot(
            musicCacheBytes = musicBytes,
            videoCacheBytes = videoBytes,
            shortsCacheBytes = shortsBytes,
            musicCachedEntryCount = musicCount,
            videoCachedEntryCount = videoCount,
            shortsCachedEntryCount = shortsCount,
            availableDeviceStorageBytes = available,
            isLowStorage = available <= CacheSettings.LOW_STORAGE_THRESHOLD_BYTES,
            videoCleanupScheduledAtMs = _storageSnapshot.value.videoCleanupScheduledAtMs,
            lastUpdatedMs = System.currentTimeMillis()
        )
        _storageSnapshot.value = snapshot
        return snapshot
    }

    private fun calculateNamespaceBytes(namespace: CacheNamespace): Long {
        val openedCache = synchronized(cacheLock) {
            when (namespace) {
                CacheNamespace.MUSIC -> musicSimpleCache
                CacheNamespace.VIDEO -> videoSimpleCache
                CacheNamespace.SHORTS -> shortsSimpleCache
            }
        }
        val dir = when (namespace) {
            CacheNamespace.MUSIC -> musicCacheDir
            CacheNamespace.VIDEO -> videoCacheDir
            CacheNamespace.SHORTS -> shortsCacheDir
        }
        val cacheSpace = openedCache?.let { runCatching { it.cacheSpace }.getOrDefault(0L) } ?: -1L
        if (cacheSpace >= 0L) return cacheSpace
        return computeDirectoryContentBytes(dir)
    }

    private fun queryAvailableStorageBytes(): Long {
        storageBytesProvider?.let { return it.invoke().coerceAtLeast(0L) }
        return runCatching {
            val target = rootCacheDir.apply { if (!exists()) mkdirs() }
            StatFs(target.absolutePath).availableBytes.coerceAtLeast(0L)
        }.getOrDefault(1L * BYTES_PER_GB)
    }

    fun queryNetworkConditions(): NetworkConditionSnapshot {
        networkSnapshotProvider?.let { return it.invoke() }
        return runCatching {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return NetworkConditionSnapshot(isConnected = true, isMetered = false, isDataSaverEnabled = false)
            val activeNetwork = cm.activeNetwork
            val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
            val connected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val metered = cm.isActiveNetworkMetered
            val dataSaver = cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
            NetworkConditionSnapshot(
                isConnected = connected,
                isMetered = metered,
                isDataSaverEnabled = dataSaver
            )
        }.getOrDefault(NetworkConditionSnapshot(isConnected = true, isMetered = false, isDataSaverEnabled = false))
    }

    private fun isInsideRootCacheDir(file: File): Boolean {
        return runCatching {
            val rootCanonical = rootCacheDir.canonicalPath
            val targetCanonical = file.canonicalPath
            targetCanonical == rootCanonical || targetCanonical.startsWith(rootCanonical + File.separator)
        }.getOrDefault(false)
    }

    private fun computeDirectoryContentBytes(dir: File): Long {
        if (!dir.exists() || !dir.isDirectory) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) {
                    stack.add(child)
                } else if (child.name.endsWith(".exo") || !child.name.endsWith(".uid") && !child.name.endsWith(".exi")) {
                    total += child.length().coerceAtLeast(0L)
                }
            }
        }
        return total
    }

    private fun countCacheContentFiles(dir: File): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        var count = 0
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            val children = current.listFiles() ?: continue
            for (child in children) {
                if (child.isDirectory) stack.add(child)
                else if (child.name.endsWith(".exo")) count++
            }
        }
        return count
    }

    private fun deleteDirectoryContents(dir: File) {
        if (!dir.exists() || !dir.isDirectory || !isInsideRootCacheDir(dir)) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteDirectoryContents(child)
            }
            runCatching { child.delete() }
        }
    }

    private fun buildFallbackKey(namespace: String, uri: Uri): String {
        return "${namespace}_${sha256Short(uri.toString())}"
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    /**
     * Releases SimpleCache file locks cleanly (used in tests or full process shutdown simulation
     * to verify clean cache recovery across app restarts).
     */
    fun releaseCaches() {
        cancelBackgroundPreloadsForForegroundPlayback()
        cancelScheduledVideoCleanup()
        synchronized(cacheLock) {
            runCatching { musicSimpleCache?.release() }
            runCatching { videoSimpleCache?.release() }
            runCatching { shortsSimpleCache?.release() }
            musicSimpleCache = null
            videoSimpleCache = null
            shortsSimpleCache = null
            musicEvictor = null
            videoEvictor = null
            shortsEvictor = null
            databaseProvider = null
            released.set(true)
        }
    }

    companion object {
        const val ROOT_DIR_NAME = "sonora_media_cache"

        @Volatile
        private var instance: SonoraMediaCacheManager? = null

        fun getInstance(context: Context): SonoraMediaCacheManager {
            return instance ?: synchronized(this) {
                instance ?: SonoraMediaCacheManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
