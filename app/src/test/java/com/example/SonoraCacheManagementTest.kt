package com.example

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.test.core.app.ApplicationProvider
import data.cache.CacheManagementPreferences
import data.cache.CacheSettings
import data.cache.CacheStorageSnapshot
import data.cache.SonoraMediaCacheManager
import data.cache.formatCacheLimitLabel
import data.cache.formatCacheSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.settings.CACHE_MANAGEMENT_INFO_MESSAGE
import ui.settings.CacheManagementContent
import ui.theme.EditorialColors
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * Comprehensive verification suite for SONORA's Advanced Cache Management System:
 * - Real Media3 SimpleCache + CacheDataSource + CacheWriter behavior
 * - Music / Video / Shorts namespace isolation & stable cache keys across URL refreshes
 * - Dynamic LRU limit enforcement (0 MB .. 10 GB) & active playback protection
 * - Independent cache clearing while keeping permanent downloads untouched
 * - Cancellable 5-minute delayed Video & Shorts cleanup
 * - Bounded network- & storage-aware preloading + edge-case resilience
 * - Simulated app restart recovery
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraCacheManagementTest {

    private lateinit var app: Application
    private lateinit var testRootDir: File
    private lateinit var testDownloadsDir: File
    private val createdManagers = mutableListOf<SonoraMediaCacheManager>()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        val uniqueId = UUID.randomUUID().toString().take(8)
        testRootDir = File(app.cacheDir, "sonora_test_cache_$uniqueId").apply { mkdirs() }
        testDownloadsDir = File(app.filesDir, "sonora_test_downloads_$uniqueId").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        createdManagers.forEach { runCatching { it.releaseCaches() } }
        createdManagers.clear()
        runCatching { testRootDir.deleteRecursively() }
        runCatching { testDownloadsDir.deleteRecursively() }
    }

    private fun createTestManager(
        rootDir: File = testRootDir,
        availableBytes: () -> Long = { 8L * 1024L * 1024L * 1024L },
        network: () -> SonoraMediaCacheManager.NetworkConditionSnapshot = {
            SonoraMediaCacheManager.NetworkConditionSnapshot(
                isConnected = true,
                isMetered = false,
                isDataSaverEnabled = false
            )
        },
        onEnqueueWork: (Long) -> Unit = {},
        onCancelWork: () -> Unit = {}
    ): SonoraMediaCacheManager {
        return SonoraMediaCacheManager(
            context = app,
            rootCacheDir = rootDir,
            preferences = CacheManagementPreferences(app),
            workManagerEnqueuer = onEnqueueWork,
            workManagerCanceller = onCancelWork,
            storageBytesProvider = availableBytes,
            networkSnapshotProvider = network
        ).also { createdManagers.add(it) }
    }

    private fun readThroughFactory(
        factory: DataSource.Factory,
        url: String,
        cacheKey: String,
        maxBytes: Int = Int.MAX_VALUE
    ): ByteArray {
        val ds = factory.createDataSource()
        val spec = DataSpec.Builder()
            .setUri(Uri.parse(url))
            .setKey(cacheKey)
            .build()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        try {
            ds.open(spec)
            var total = 0
            while (total < maxBytes) {
                val toRead = minOf(buf.size, maxBytes - total)
                val read = ds.read(buf, 0, toRead)
                if (read == C.RESULT_END_OF_INPUT) break
                out.write(buf, 0, read)
                total += read
            }
        } finally {
            ds.close()
        }
        return out.toByteArray()
    }

    private fun makeDeterministicBytes(size: Int, seed: Int): ByteArray =
        ByteArray(size) { i -> ((i * 31 + seed) and 0xFF).toByte() }

    /**
     * In-memory upstream [DataSource.Factory] that counts network opens and optionally simulates
     * interrupted network streams or expired URLs (HTTP 403 / IOException).
     */
    private class CountingInMemoryDataSourceFactory(
        private val payloadsByUrl: Map<String, ByteArray>,
        private val failUrls: Set<String> = emptySet(),
        private val interruptAfterBytes: Int? = null
    ) : DataSource.Factory {
        val openCount = AtomicInteger(0)
        val totalBytesServedFromNetwork = AtomicInteger(0)

        override fun createDataSource(): DataSource = object : BaseDataSource(/* isNetwork = */ true) {
            private var activeUri: Uri? = null
            private var payload: ByteArray = ByteArray(0)
            private var cursor: Int = 0
            private var remaining: Int = 0
            private var servedThisOpen: Int = 0

            override fun open(dataSpec: DataSpec): Long {
                val url = dataSpec.uri.toString()
                openCount.incrementAndGet()
                if (failUrls.contains(url)) {
                    throw IOException("Simulated HTTP 403 expired stream URL for $url")
                }
                val bytes = payloadsByUrl[url]
                    ?: throw IOException("No upstream payload for $url")
                activeUri = dataSpec.uri
                payload = bytes
                val pos = dataSpec.position.toInt().coerceIn(0, bytes.size)
                cursor = pos
                val avail = bytes.size - pos
                remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    minOf(avail, dataSpec.length.toInt())
                } else {
                    avail
                }
                servedThisOpen = 0
                transferStarted(dataSpec)
                return remaining.toLong()
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (remaining <= 0) return C.RESULT_END_OF_INPUT
                if (interruptAfterBytes != null && servedThisOpen >= interruptAfterBytes) {
                    throw IOException("Simulated interrupted connection after $servedThisOpen bytes")
                }
                val toCopy = minOf(length, remaining)
                System.arraycopy(payload, cursor, buffer, offset, toCopy)
                cursor += toCopy
                remaining -= toCopy
                servedThisOpen += toCopy
                totalBytesServedFromNetwork.addAndGet(toCopy)
                bytesTransferred(toCopy)
                return toCopy
            }

            override fun getUri(): Uri? = activeUri

            override fun close() {
                if (activeUri != null) {
                    activeUri = null
                    transferEnded()
                }
            }
        }
    }

    @Test
    fun test01_toggleSwitches_andTurningCachingOffStopsNewWritesOnNextLoad() = runBlocking {
        val manager = createTestManager()
        manager.setCacheMusicEnabled(true)
        manager.setCacheVideosEnabled(true)
        manager.setPreloadUpcomingEnabled(true)
        manager.setMusicCacheLimitBytes(512L * 1024L * 1024L, clearExistingWhenZero = false)

        val payloadA = makeDeterministicBytes(64 * 1024, seed = 11)
        val payloadB = makeDeterministicBytes(64 * 1024, seed = 22)
        val urlA = "https://stream.sonora.test/music/a.m4a"
        val urlB = "https://stream.sonora.test/music/b.m4a"
        val upstream = CountingInMemoryDataSourceFactory(mapOf(urlA to payloadA, urlB to payloadB))

        // 1. With Cache Music ON, song A is written to cache
        val keyA = manager.buildMusicCacheKey("https://provider.test/watch?v=songA", "opus_160")
        readThroughFactory(manager.createMusicDataSourceFactory(upstream), urlA, keyA)
        val cachedAfterA = manager.refreshStorageSnapshot().musicCacheBytes
        assertTrue("Expected song A to be cached when switch is ON", cachedAfterA >= payloadA.size.toLong())

        // 2. Turn Cache Music OFF -> next load (song B) must NOT write to cache
        manager.setCacheMusicEnabled(false)
        assertFalse(manager.settings.value.cacheMusicEnabled)
        assertFalse(manager.shouldAllowMusicCacheWrites())

        val keyB = manager.buildMusicCacheKey("https://provider.test/watch?v=songB", "opus_160")
        val readB = readThroughFactory(manager.createMusicDataSourceFactory(upstream), urlB, keyB)
        assertArrayEquals(payloadB, readB)
        assertEquals(
            "Song B must not be written to cache after Cache Music is turned OFF",
            0L,
            manager.getCachedBytesForKey(SonoraMediaCacheManager.CacheNamespace.MUSIC, keyB)
        )

        // 3. Toggle Cache Videos & Preload switches and verify live policy flags
        manager.setCacheVideosEnabled(false)
        assertFalse(manager.shouldAllowVideoCacheWrites())
        manager.setPreloadUpcomingEnabled(false)
        assertEquals(
            0L,
            manager.computeAllowedPreloadWindowBytes(SonoraMediaCacheManager.CacheNamespace.MUSIC)
        )
    }

    @Test
    fun test02_playSameOnlineSongTwice_reusesCachedAudioEvenAcrossUrlRotation() = runBlocking {
        val manager = createTestManager()
        manager.setCacheMusicEnabled(true)
        manager.setMusicCacheLimitBytes(512L * 1024L * 1024L, clearExistingWhenZero = false)

        val audioBytes = makeDeterministicBytes(96 * 1024, seed = 7)
        val signedUrl1 = "https://cdn1.sonora.test/audio?sig=token_1"
        val signedUrl2 = "https://cdn2.sonora.test/audio?sig=token_2_rotated"
        val upstream = CountingInMemoryDataSourceFactory(
            mapOf(signedUrl1 to audioBytes, signedUrl2 to audioBytes)
        )

        // Stable key derived from track identity, independent of rotating CDN signature
        val stableKey = manager.buildMusicCacheKey("https://provider.test/watch?v=track_replay", "251")

        // First play fetches from upstream and caches
        val firstPass = readThroughFactory(manager.createMusicDataSourceFactory(upstream), signedUrl1, stableKey)
        assertArrayEquals(audioBytes, firstPass)
        assertEquals("First play should hit upstream once", 1, upstream.openCount.get())

        // Second play (even with rotated URL) reads 100% from SimpleCache without touching network
        val secondPass = readThroughFactory(manager.createMusicDataSourceFactory(upstream), signedUrl2, stableKey)
        assertArrayEquals(audioBytes, secondPass)
        assertEquals("Second play must reuse cache with zero new upstream requests", 1, upstream.openCount.get())
    }

    @Test
    fun test03_playSameVideoAndShortTwice_reusesSeparateNamespaces() = runBlocking {
        val manager = createTestManager()
        manager.setCacheVideosEnabled(true)

        val videoBytes = makeDeterministicBytes(128 * 1024, seed = 33)
        val shortBytes = makeDeterministicBytes(80 * 1024, seed = 44)
        val videoUrl = "https://cdn.sonora.test/video/v1.mp4"
        val shortUrl = "https://cdn.sonora.test/shorts/s1.mp4"
        val upstream = CountingInMemoryDataSourceFactory(mapOf(videoUrl to videoBytes, shortUrl to shortBytes))

        val videoKey = manager.buildVideoCacheKey("vid_101", "m_720p", isShort = false)
        val shortKey = manager.buildVideoCacheKey("short_202", "m_480p", isShort = true)

        // Play video twice
        val v1 = readThroughFactory(manager.createVideoDataSourceFactory(upstream, isShort = false), videoUrl, videoKey)
        val v2 = readThroughFactory(manager.createVideoDataSourceFactory(upstream, isShort = false), videoUrl, videoKey)
        assertArrayEquals(videoBytes, v1)
        assertArrayEquals(videoBytes, v2)
        assertEquals("Video replay must reuse cache", 1, upstream.openCount.get())

        // Play Short twice
        val s1 = readThroughFactory(manager.createVideoDataSourceFactory(upstream, isShort = true), shortUrl, shortKey)
        val s2 = readThroughFactory(manager.createVideoDataSourceFactory(upstream, isShort = true), shortUrl, shortKey)
        assertArrayEquals(shortBytes, s1)
        assertArrayEquals(shortBytes, s2)
        assertEquals("Short replay must reuse cache", 2, upstream.openCount.get())

        // Verify separate namespace accounting
        val snap = manager.refreshStorageSnapshot()
        assertTrue("Video cache size should be tracked", snap.videoCacheBytes >= videoBytes.size.toLong())
        assertTrue("Shorts cache size should be tracked separately", snap.shortsCacheBytes >= shortBytes.size.toLong())
        assertEquals(snap.videoCacheBytes + snap.shortsCacheBytes, snap.totalVideoAndShortsBytes)
    }

    @Test
    fun test04_preloadUpcomingMedia_usesSmallBoundedWindowAndDeduplicates() = runBlocking {
        val manager = createTestManager()
        manager.setCacheMusicEnabled(true)
        manager.setCacheVideosEnabled(true)
        manager.setPreloadUpcomingEnabled(true)
        manager.setMusicCacheLimitBytes(512L * 1024L * 1024L, clearExistingWhenZero = false)

        // 2 MB song and 3 MB video — preloading must NOT fetch the entire file!
        val bigSong = makeDeterministicBytes(2 * 1024 * 1024, seed = 55)
        val bigVideo = makeDeterministicBytes(3 * 1024 * 1024, seed = 66)
        val songUrl = "https://cdn.sonora.test/music/next_queue.m4a"
        val videoUrl = "https://cdn.sonora.test/video/next_video.mp4"
        val upstream = CountingInMemoryDataSourceFactory(mapOf(songUrl to bigSong, videoUrl to bigVideo))

        val musicKey = manager.buildMusicCacheKey("https://provider.test/watch?v=nextSong", "251")
        val job1 = manager.preloadMusicWindow(musicKey, songUrl, upstreamFactory = upstream)
        assertNotNull(job1)
        job1?.join()

        val preloadedMusicBytes = manager.getCachedBytesForKey(SonoraMediaCacheManager.CacheNamespace.MUSIC, musicKey)
        assertEquals(
            "Music preload must fetch only the configured small preload window, not full track",
            CacheSettings.DEFAULT_MUSIC_PRELOAD_WINDOW_BYTES,
            preloadedMusicBytes
        )
        assertTrue(preloadedMusicBytes < bigSong.size.toLong())

        // Re-requesting preload for same key must skip network fetch (deduplication / already cached)
        val opensBeforeDup = upstream.openCount.get()
        manager.preloadMusicWindow(musicKey, songUrl, upstreamFactory = upstream)?.join()
        assertEquals("Already-preloaded track must not trigger duplicate network fetch", opensBeforeDup, upstream.openCount.get())

        // Video bounded preload on Wi-Fi
        val videoKey = manager.buildVideoCacheKey("next_vid", "m_720p", isShort = false)
        manager.preloadVideoWindow(videoKey, videoUrl, upstreamFactory = upstream, isShort = false)?.join()
        val preloadedVideoBytes = manager.getCachedBytesForKey(SonoraMediaCacheManager.CacheNamespace.VIDEO, videoKey)
        assertEquals(
            "Video preload must fetch only the small video preload window",
            CacheSettings.DEFAULT_VIDEO_PRELOAD_WINDOW_BYTES,
            preloadedVideoBytes
        )
    }

    @Test
    fun test05_musicCacheLimit_enforcesLruEviction_andZeroMbClearsAndStopsWrites() = runBlocking {
        val manager = createTestManager()
        manager.setCacheMusicEnabled(true)
        manager.setMusicCacheLimitBytes(10L * 1024L * 1024L, clearExistingWhenZero = false)

        val trackSize = 128 * 1024 // 128 KB each
        val track1 = makeDeterministicBytes(trackSize, seed = 1)
        val track2 = makeDeterministicBytes(trackSize, seed = 2)
        val track3 = makeDeterministicBytes(trackSize, seed = 3)
        val u1 = "https://cdn.sonora.test/t1.m4a"
        val u2 = "https://cdn.sonora.test/t2.m4a"
        val u3 = "https://cdn.sonora.test/t3.m4a"
        val upstream = CountingInMemoryDataSourceFactory(mapOf(u1 to track1, u2 to track2, u3 to track3))

        val k1 = manager.buildMusicCacheKey("track1")
        val k2 = manager.buildMusicCacheKey("track2")
        val k3 = manager.buildMusicCacheKey("track3")

        readThroughFactory(manager.createMusicDataSourceFactory(upstream), u1, k1)
        delay(20L)
        readThroughFactory(manager.createMusicDataSourceFactory(upstream), u2, k2)
        delay(20L)
        readThroughFactory(manager.createMusicDataSourceFactory(upstream), u3, k3)

        val beforeLimit = manager.refreshStorageSnapshot().musicCacheBytes
        assertTrue("Expected ~384 KB cached before lowering limit", beforeLimit >= (trackSize * 3).toLong())

        // Lower limit to ~200 KB -> LRU must evict oldest tracks so usage <= limit while keeping newest (k3)
        manager.setMusicCacheLimitBytes(200L * 1024L, clearExistingWhenZero = false)
        val afterLower = manager.refreshStorageSnapshot().musicCacheBytes
        assertTrue(
            "Music cache ($afterLower bytes) must be evicted down to <= 200 KB limit",
            afterLower <= 200L * 1024L
        )
        assertEquals("Oldest track (k1) should be evicted first", 0L, manager.getCachedBytesForKey(SonoraMediaCacheManager.CacheNamespace.MUSIC, k1))
        assertTrue("Most recent track (k3) should remain cached", manager.getCachedBytesForKey(SonoraMediaCacheManager.CacheNamespace.MUSIC, k3) > 0L)

        // Setting limit to 0 MB clears existing temporary music cache and stops new writes
        manager.setMusicCacheLimitBytes(0L, clearExistingWhenZero = true)
        assertEquals(0L, manager.settings.value.clampedMusicLimitBytes)
        assertFalse("0 MB limit must disable new music cache writes", manager.shouldAllowMusicCacheWrites())
        assertEquals(0L, manager.refreshStorageSnapshot().musicCacheBytes)

        // Next load with 0 MB limit must NOT cache anything
        readThroughFactory(manager.createMusicDataSourceFactory(upstream), u1, k1)
        assertEquals(0L, manager.refreshStorageSnapshot().musicCacheBytes)
    }

    @Test
    fun test06_and_07_clearMusicAndVideoIndependently_neverDeletesPermanentDownloads() = runBlocking {
        val manager = createTestManager()
        manager.setCacheMusicEnabled(true)
        manager.setCacheVideosEnabled(true)
        manager.setMusicCacheLimitBytes(512L * 1024L * 1024L, clearExistingWhenZero = false)

        // Create simulated permanent downloaded music & video files outside cacheDir
        val downloadedSong = File(testDownloadsDir, "permanent_song.m4a").apply {
            writeBytes(makeDeterministicBytes(32 * 1024, seed = 91))
        }
        val downloadedVideo = File(testDownloadsDir, "permanent_video.mp4").apply {
            writeBytes(makeDeterministicBytes(48 * 1024, seed = 92))
        }

        val mBytes = makeDeterministicBytes(64 * 1024, seed = 10)
        val vBytes = makeDeterministicBytes(64 * 1024, seed = 20)
        val mUrl = "https://cdn.sonora.test/m.m4a"
        val vUrl = "https://cdn.sonora.test/v.mp4"
        val upstream = CountingInMemoryDataSourceFactory(mapOf(mUrl to mBytes, vUrl to vBytes))

        readThroughFactory(manager.createMusicDataSourceFactory(upstream), mUrl, manager.buildMusicCacheKey("m1"))
        readThroughFactory(manager.createVideoDataSourceFactory(upstream, isShort = false), vUrl, manager.buildVideoCacheKey("v1", "m", false))

        val snapInitial = manager.refreshStorageSnapshot()
        assertTrue(snapInitial.musicCacheBytes > 0L)
        assertTrue(snapInitial.videoCacheBytes > 0L)

        // Clear ONLY Music Cache -> Video Cache & permanent downloads remain intact
        manager.clearMusicCache()
        val snapAfterMusicClear = manager.storageSnapshot.value
        assertEquals(0L, snapAfterMusicClear.musicCacheBytes)
        assertTrue("Clearing music cache must NOT delete video cache", snapAfterMusicClear.videoCacheBytes > 0L)
        assertTrue("Permanent downloaded song must remain intact", downloadedSong.exists() && downloadedSong.length() == 32 * 1024L)
        assertTrue("Permanent downloaded video must remain intact", downloadedVideo.exists() && downloadedVideo.length() == 48 * 1024L)

        // Re-populate music cache, then clear ONLY Video Cache -> Music Cache & downloads remain intact
        readThroughFactory(manager.createMusicDataSourceFactory(upstream), mUrl, manager.buildMusicCacheKey("m2"))
        assertTrue(manager.refreshStorageSnapshot().musicCacheBytes > 0L)

        manager.clearVideoAndShortsCache()
        val snapAfterVideoClear = manager.storageSnapshot.value
        assertEquals(0L, snapAfterVideoClear.totalVideoAndShortsBytes)
        assertTrue("Clearing video cache must NOT delete music cache", snapAfterVideoClear.musicCacheBytes > 0L)
        assertTrue("Permanent downloaded song must remain intact", downloadedSong.exists())
        assertTrue("Permanent downloaded video must remain intact", downloadedVideo.exists())
    }

    @Test
    fun test08_delayedVideoAndShortsCleanup_runsWhenBothClosed_andCancelsIfPlayerReopens() = runBlocking {
        var scheduledDelayMs: Long? = null
        var cancelCount = 0
        val manager = createTestManager(
            onEnqueueWork = { scheduledDelayMs = it },
            onCancelWork = { cancelCount++ }
        )
        manager.setCacheMusicEnabled(true)
        manager.setCacheVideosEnabled(true)
        manager.setMusicCacheLimitBytes(512L * 1024L * 1024L, clearExistingWhenZero = false)

        val mBytes = makeDeterministicBytes(32 * 1024, seed = 1)
        val vBytes = makeDeterministicBytes(48 * 1024, seed = 2)
        val sBytes = makeDeterministicBytes(24 * 1024, seed = 3)
        val upstream = CountingInMemoryDataSourceFactory(
            mapOf(
                "https://cdn.sonora.test/m.m4a" to mBytes,
                "https://cdn.sonora.test/v.mp4" to vBytes,
                "https://cdn.sonora.test/s.mp4" to sBytes
            )
        )
        readThroughFactory(manager.createMusicDataSourceFactory(upstream), "https://cdn.sonora.test/m.m4a", manager.buildMusicCacheKey("m_keep"))
        readThroughFactory(manager.createVideoDataSourceFactory(upstream, false), "https://cdn.sonora.test/v.mp4", manager.buildVideoCacheKey("v_tmp", "m", false))
        readThroughFactory(manager.createVideoDataSourceFactory(upstream, true), "https://cdn.sonora.test/s.mp4", manager.buildVideoCacheKey("s_tmp", "m", true))

        // Open both players -> closing only video player while music player is open must NOT schedule cleanup
        manager.onMusicPlayerStateChanged(isOpen = true)
        manager.onVideoPlayerStateChanged(isOpen = true)
        manager.onVideoPlayerStateChanged(isOpen = false)
        assertNull("Cleanup must NOT be scheduled while music player is still open", scheduledDelayMs)
        assertFalse(manager.areBothPlayersClosed())

        // Close music player too -> both closed -> schedules 5-minute (300,000 ms) delayed cleanup
        manager.onMusicPlayerStateChanged(isOpen = false)
        assertTrue(manager.areBothPlayersClosed())
        assertEquals(CacheSettings.DEFAULT_VIDEO_CLEANUP_DELAY_MS, scheduledDelayMs)
        assertNotNull(manager.storageSnapshot.value.videoCleanupScheduledAtMs)

        // Reopen video player before 5 minutes elapse -> MUST cancel cleanup & keep video cache intact
        val cancelsBefore = cancelCount
        manager.onVideoPlayerStateChanged(isOpen = true)
        assertTrue("Reopening player must cancel scheduled WorkManager cleanup", cancelCount > cancelsBefore)
        assertNull(manager.storageSnapshot.value.videoCleanupScheduledAtMs)
        val executedWhileOpen = manager.executeScheduledVideoCleanupIfPlayersClosed()
        assertFalse("Delayed cleanup worker must abort if a player is open", executedWhileOpen)
        assertTrue("Video/Shorts cache must remain intact after reopen", manager.refreshStorageSnapshot().totalVideoAndShortsBytes > 0L)

        // Close video player again -> both closed -> simulate 5-minute worker firing -> clears Video/Shorts but preserves Music
        manager.onVideoPlayerStateChanged(isOpen = false)
        val executedWhenClosed = manager.executeScheduledVideoCleanupIfPlayersClosed()
        assertTrue("Delayed cleanup must execute when both players remain closed", executedWhenClosed)
        val finalSnap = manager.refreshStorageSnapshot()
        assertEquals(0L, finalSnap.totalVideoAndShortsBytes)
        assertTrue("Delayed video cleanup must preserve Music cache", finalSnap.musicCacheBytes > 0L)
    }

    @Test
    fun test09_appRestartRecovery_preservesCachedAudioAndAccurateStorageCalculation() = runBlocking {
        val mBytes = makeDeterministicBytes(72 * 1024, seed = 77)
        val url = "https://cdn.sonora.test/restart_song.m4a"
        val stableKey = "music_restart_test_key"

        // Session 1: stream & cache track, then release caches (simulating app process termination)
        val session1 = createTestManager()
        session1.setCacheMusicEnabled(true)
        session1.setMusicCacheLimitBytes(512L * 1024L * 1024L, clearExistingWhenZero = false)
        val upstream1 = CountingInMemoryDataSourceFactory(mapOf(url to mBytes))
        readThroughFactory(session1.createMusicDataSourceFactory(upstream1), url, stableKey)
        assertEquals(1, upstream1.openCount.get())
        session1.releaseCaches()

        // Session 2: brand new SonoraMediaCacheManager opening same cache dir recovers cached bytes cleanly
        val session2 = createTestManager()
        session2.setCacheMusicEnabled(true)
        val snap2 = session2.refreshStorageSnapshot()
        assertTrue("Recovered storage size after restart must match cached data", snap2.musicCacheBytes >= mBytes.size.toLong())

        val upstream2 = CountingInMemoryDataSourceFactory(mapOf(url to mBytes))
        val replayed = readThroughFactory(session2.createMusicDataSourceFactory(upstream2), url, stableKey)
        assertArrayEquals(mBytes, replayed)
        assertEquals("Replay after app restart must read from persisted cache with zero upstream fetches", 0, upstream2.openCount.get())
    }

    @Test
    fun test10_lowStorage_dataSaver_meteredNetwork_interruptedStream_andInvalidSources() = runBlocking {
        // 1. Low storage pauses new cache writes & preloading cleanly
        val lowStorageManager = createTestManager(
            availableBytes = { 64L * 1024L * 1024L } // below 250 MB threshold
        )
        lowStorageManager.setCacheMusicEnabled(true)
        lowStorageManager.setPreloadUpcomingEnabled(true)
        assertFalse("Low storage must pause music cache writes", lowStorageManager.shouldAllowMusicCacheWrites())
        assertEquals(0L, lowStorageManager.computeAllowedPreloadWindowBytes(SonoraMediaCacheManager.CacheNamespace.MUSIC))
        assertTrue(lowStorageManager.refreshStorageSnapshot().isLowStorage)

        // 2. Data Saver blocks all background preloading
        val dataSaverManager = createTestManager(
            rootDir = File(testRootDir, "ds_test").apply { mkdirs() },
            network = {
                SonoraMediaCacheManager.NetworkConditionSnapshot(
                    isConnected = true,
                    isMetered = true,
                    isDataSaverEnabled = true
                )
            }
        )
        assertEquals(0L, dataSaverManager.computeAllowedPreloadWindowBytes(SonoraMediaCacheManager.CacheNamespace.MUSIC))
        assertEquals(0L, dataSaverManager.computeAllowedPreloadWindowBytes(SonoraMediaCacheManager.CacheNamespace.VIDEO))

        // 3. Metered mobile data skips large Video preloads and reduces Music preload window
        val meteredManager = createTestManager(
            rootDir = File(testRootDir, "metered_test").apply { mkdirs() },
            network = {
                SonoraMediaCacheManager.NetworkConditionSnapshot(
                    isConnected = true,
                    isMetered = true,
                    isDataSaverEnabled = false
                )
            }
        )
        meteredManager.setCacheMusicEnabled(true)
        meteredManager.setMusicCacheLimitBytes(512L * 1024L * 1024L, clearExistingWhenZero = false)
        meteredManager.setPreloadUpcomingEnabled(true)
        assertEquals(0L, meteredManager.computeAllowedPreloadWindowBytes(SonoraMediaCacheManager.CacheNamespace.VIDEO))
        assertEquals(
            CacheSettings.METERED_MUSIC_PRELOAD_WINDOW_BYTES,
            meteredManager.computeAllowedPreloadWindowBytes(SonoraMediaCacheManager.CacheNamespace.MUSIC)
        )

        // 4. DRM, Live streams, and local files are never permitted in temporary cache
        assertFalse(meteredManager.isCachePermitted("https://cdn.sonora.test/live.m3u8", isLive = true))
        assertFalse(meteredManager.isCachePermitted("https://cdn.sonora.test/drm.mpd", hasDrm = true))
        assertFalse(meteredManager.isCachePermitted("file:///storage/emulated/0/Music/local.mp3"))
        assertFalse(meteredManager.isCachePermitted("content://media/external/audio/media/42"))

        // 5. Expired URL (HTTP 403) and interrupted stream during preload fail gracefully without crashing
        val healthyManager = createTestManager(rootDir = File(testRootDir, "err_test").apply { mkdirs() })
        healthyManager.setCacheMusicEnabled(true)
        healthyManager.setPreloadUpcomingEnabled(true)
        healthyManager.setMusicCacheLimitBytes(512L * 1024L * 1024L, clearExistingWhenZero = false)

        val expiredUrl = "https://cdn.sonora.test/expired_403.m4a"
        val dropUrl = "https://cdn.sonora.test/dropped.m4a"
        val errUpstream = CountingInMemoryDataSourceFactory(
            payloadsByUrl = mapOf(dropUrl to makeDeterministicBytes(128 * 1024, seed = 9)),
            failUrls = setOf(expiredUrl),
            interruptAfterBytes = 16 * 1024
        )
        healthyManager.preloadMusicWindow("music_expired", expiredUrl, upstreamFactory = errUpstream)?.join()
        healthyManager.preloadMusicWindow("music_dropped", dropUrl, upstreamFactory = errUpstream)?.join()
        // Should remain completely healthy and queryable after failed preloads
        assertNotNull(healthyManager.refreshStorageSnapshot())
    }

    @Test
    fun test11_activePlaybackIsNeverInterruptedByClearOrLruEviction() = runBlocking {
        val manager = createTestManager()
        manager.setCacheMusicEnabled(true)
        manager.setMusicCacheLimitBytes(10L * 1024L * 1024L, clearExistingWhenZero = false)

        val size = 96 * 1024
        val activeBytes = makeDeterministicBytes(size, seed = 88)
        val idleBytes = makeDeterministicBytes(size, seed = 89)
        val activeUrl = "https://cdn.sonora.test/active.m4a"
        val idleUrl = "https://cdn.sonora.test/idle.m4a"
        val upstream = CountingInMemoryDataSourceFactory(mapOf(activeUrl to activeBytes, idleUrl to idleBytes))

        val activeKey = manager.buildMusicCacheKey("active_song")
        val idleKey = manager.buildMusicCacheKey("idle_song")

        readThroughFactory(manager.createMusicDataSourceFactory(upstream), activeUrl, activeKey)
        delay(20L)
        readThroughFactory(manager.createMusicDataSourceFactory(upstream), idleUrl, idleKey)

        // Mark activeKey as currently playing
        manager.setActiveMusicCacheKey(activeKey)

        // Lower limit aggressively AND invoke clearMusicCache(protectActiveKey = true)
        manager.setMusicCacheLimitBytes(64L * 1024L, clearExistingWhenZero = false)
        assertEquals("Idle track should be evicted", 0L, manager.getCachedBytesForKey(SonoraMediaCacheManager.CacheNamespace.MUSIC, idleKey))
        assertTrue(
            "Currently playing track must remain protected from LRU eviction",
            manager.getCachedBytesForKey(SonoraMediaCacheManager.CacheNamespace.MUSIC, activeKey) >= size.toLong()
        )

        manager.clearMusicCache(protectActiveKey = true)
        assertTrue(
            "Currently playing track must remain protected during cache clear",
            manager.getCachedBytesForKey(SonoraMediaCacheManager.CacheNamespace.MUSIC, activeKey) >= size.toLong()
        )
    }

    @Test
    fun test12_sizeFormatting_formatsMbAndGbAccurately() {
        assertEquals("0 MB", formatCacheSize(0L))
        assertEquals("512 MB", formatCacheLimitLabel(512L * 1024L * 1024L))
        assertEquals("10 GB", formatCacheLimitLabel(10L * 1024L * 1024L * 1024L))
        assertEquals("0 MB", formatCacheLimitLabel(0L))
        assertEquals("1.50 GB", formatCacheSize((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}

/**
 * Compose UI test verifying SONORA's Cache Management screen cards, exact copy,
 * live storage display, toggles, and confirmation dialogs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraCacheManagementUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cacheManagementUi_displaysAllRequiredControlsExactMessageAndConfirmationDialogs() {
        var settingsState by mutableStateOf(CacheSettings.DEFAULT)
        var snapshotState by mutableStateOf(
            CacheStorageSnapshot(
                musicCacheBytes = 128L * 1024L * 1024L,
                videoCacheBytes = 42L * 1024L * 1024L,
                shortsCacheBytes = 18L * 1024L * 1024L,
                musicCachedEntryCount = 14,
                videoCachedEntryCount = 3,
                shortsCachedEntryCount = 5,
                availableDeviceStorageBytes = 16L * 1024L * 1024L * 1024L,
                isLowStorage = false
            )
        )
        var musicCleared = false
        var videoCleared = false

        composeRule.setContent {
            CacheManagementContent(
                settings = settingsState,
                snapshot = snapshotState,
                colors = EditorialColors.default(),
                onCacheMusicChanged = { settingsState = settingsState.copy(cacheMusicEnabled = it) },
                onCacheVideosChanged = { settingsState = settingsState.copy(cacheVideosEnabled = it) },
                onPreloadUpcomingChanged = { settingsState = settingsState.copy(preloadUpcomingEnabled = it) },
                onMusicCacheLimitChanged = { bytes, _ ->
                    settingsState = settingsState.copy(musicCacheLimitBytes = bytes)
                },
                onClearMusicCache = {
                    musicCleared = true
                    snapshotState = snapshotState.copy(musicCacheBytes = 0L, musicCachedEntryCount = 0)
                },
                onClearVideoCache = {
                    videoCleared = true
                    snapshotState = snapshotState.copy(videoCacheBytes = 0L, shortsCacheBytes = 0L)
                },
                onRefreshStorage = {},
                onBack = {}
            )
        }

        // 1. Storage info card shows live actual size (128 MB), not hardcoded 0
        composeRule.onNodeWithTag("music_cache_size_text").assertIsDisplayed().assertTextContains("128 MB")
        composeRule.onNodeWithTag("video_cache_size_text").assertIsDisplayed().assertTextContains("60 MB")

        // 2. Toggle all 3 switches & verify descriptions
        composeRule.onNodeWithText("Cache Music").assertIsDisplayed()
        composeRule.onNodeWithText("Keep streamed songs for faster replay and offline listening.").assertIsDisplayed()
        composeRule.onNodeWithTag("cache_music_switch").assertIsOn().performClick()
        composeRule.onNodeWithTag("cache_music_switch").assertIsOff()

        composeRule.onNodeWithTag("cache_management_list")
            .performScrollToNode(hasTestTag("cache_videos_card"))
        composeRule.onNodeWithText("Keep temporary video data for seeking and replay.").assertIsDisplayed()
        composeRule.onNodeWithTag("cache_videos_switch").assertIsOn().performClick()
        composeRule.onNodeWithTag("cache_videos_switch").assertIsOff()

        composeRule.onNodeWithTag("cache_management_list")
            .performScrollToNode(hasTestTag("cache_preload_card"))
        composeRule.onNodeWithText("Prepare upcoming music and video before you play them.").assertIsDisplayed()
        composeRule.onNodeWithTag("cache_preload_switch").assertIsOn().performClick()
        composeRule.onNodeWithTag("cache_preload_switch").assertIsOff()

        // 3. Music Cache Limit card & exact informational policy message
        composeRule.onNodeWithTag("cache_management_list")
            .performScrollToNode(hasTestTag("music_cache_limit_card"))
        composeRule.onNodeWithText("Music Cache Limit").assertIsDisplayed()
        composeRule.onNodeWithTag("music_cache_limit_value").assertTextContains("512 MB")

        composeRule.onNodeWithTag("cache_management_list")
            .performScrollToNode(hasTestTag("cache_info_message_card"))
        composeRule.onNodeWithTag("cache_info_message_text")
            .assertIsDisplayed()
            .assertTextContains(CACHE_MANAGEMENT_INFO_MESSAGE)

        // 4. Clear Music Cache confirmation dialog clears only music cache and updates display to 0 MB
        composeRule.onNodeWithTag("cache_management_list")
            .performScrollToNode(hasTestTag("clear_music_cache_button"))
        composeRule.onNodeWithText("Remove temporarily stored songs; keep downloads.").assertIsDisplayed()
        composeRule.onNodeWithTag("clear_music_cache_button").performClick()
        composeRule.onNodeWithTag("confirm_clear_music_cache_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_clear_music_cache_button").performClick()
        assertTrue(musicCleared)

        // 5. Clear Video Cache confirmation dialog clears temporary video & Shorts cache
        composeRule.onNodeWithTag("cache_management_list")
            .performScrollToNode(hasTestTag("clear_video_cache_button"))
        composeRule.onNodeWithText("Remove temporary video data; keep downloads.").assertIsDisplayed()
        composeRule.onNodeWithTag("clear_video_cache_button").performClick()
        composeRule.onNodeWithTag("confirm_clear_video_cache_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm_clear_video_cache_button").performClick()
        assertTrue(videoCleared)

        // Scroll back to top and confirm updated live sizes
        composeRule.onNodeWithTag("cache_management_list")
            .performScrollToNode(hasTestTag("music_cache_storage_card"))
        composeRule.onNodeWithTag("music_cache_size_text").assertTextContains("0 MB")
        composeRule.onNodeWithTag("video_cache_size_text").assertTextContains("0 MB")
    }
}
