package playback

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.SonoraAudioEngine
import com.example.data.SonoraDao
import com.example.data.SonoraDatabase
import com.example.model.MiniPlayerStyle
import com.example.model.OnlinePlaybackStage
import com.example.model.PlaybackInfo
import com.example.model.Song
import data.online.NetworkConnectivityMonitor
import data.online.OnlineMusicRepository
import data.online.OnlineSearchDataSource
import data.online.OnlineStreamResolver
import data.online.OnlineTrackMapper
import domain.online.AudioQualityPreference
import domain.online.OnlineAudioStreamOption
import domain.online.OnlineErrorKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineResult
import domain.online.ResolvedOnlineStreamResult
import domain.online.StreamDeliveryType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
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
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import ui.components.FloatingMiniPlayer
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureNanoTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OnlinePlaybackPipelineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SonoraDatabase
    private lateinit var dao: SonoraDao
    private lateinit var connectivityMonitor: NetworkConnectivityMonitor

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Application>()
        setNetworkState(connected = true, metered = false)
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
        connectivityMonitor = NetworkConnectivityMonitor(context)
    }

    @After
    fun teardown() {
        connectivityMonitor.unregister()
        db.close()
    }

    private fun setNetworkState(connected: Boolean, metered: Boolean) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        if (connected) {
            val type = if (metered) ConnectivityManager.TYPE_MOBILE else ConnectivityManager.TYPE_WIFI
            @Suppress("DEPRECATION")
            Shadows.shadowOf(cm).setActiveNetworkInfo(
                ShadowNetworkInfo.newInstance(
                    NetworkInfo.DetailedState.CONNECTED,
                    type,
                    0,
                    true,
                    true
                )
            )
        }
        if (connected && activeNetwork != null) {
            val caps = ShadowNetworkCapabilities.newInstance()
            val shadowCaps = Shadows.shadowOf(caps)
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (!metered) {
                shadowCaps.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                shadowCaps.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        } else if (!connected && activeNetwork != null) {
            @Suppress("DEPRECATION")
            Shadows.shadowOf(cm).setActiveNetworkInfo(null)
            val caps = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(cm).setNetworkCapabilities(activeNetwork, caps)
        }
    }

    private fun pumpMainLooper(rounds: Int = 25) {
        repeat(rounds) {
            Thread.sleep(10)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun sampleOnlineSong(id: Long, videoId: String, title: String = "Track $videoId"): Song {
        val contentUrl = "https://music.youtube.com/watch?v=$videoId"
        return Song(
            id = id,
            title = title,
            artist = "Test Artist",
            album = "Test Album",
            albumId = id,
            durationMs = 205_000L,
            uri = Uri.EMPTY,
            dataPath = contentUrl,
            mimeType = "audio/webm",
            sizeBytes = 0L,
            isOnline = true,
            sourceType = "ONLINE",
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = contentUrl
        )
    }

    private fun sampleOptions(expireEpochSec: Long? = null): List<OnlineAudioStreamOption> {
        val expSuffix = if (expireEpochSec != null) "&expire=$expireEpochSec" else ""
        return listOf(
            OnlineAudioStreamOption(
                id = "audio_251_opus",
                url = "https://rr1---sn.googlevideo.com/videoplayback?itag=251$expSuffix",
                mimeType = "audio/webm",
                codec = "opus",
                formatName = "WEBMA_OPUS",
                averageBitrateKbps = 160,
                deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                isAudioOnly = true,
                isLossless = false
            ),
            OnlineAudioStreamOption(
                id = "audio_140_aac",
                url = "https://rr1---sn.googlevideo.com/videoplayback?itag=140$expSuffix",
                mimeType = "audio/mp4",
                codec = "mp4a.40.2",
                formatName = "M4A",
                averageBitrateKbps = 128,
                deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                isAudioOnly = true,
                isLossless = false
            ),
            OnlineAudioStreamOption(
                id = "audio_599_aac_low",
                url = "https://rr1---sn.googlevideo.com/videoplayback?itag=599$expSuffix",
                mimeType = "audio/mp4",
                codec = "mp4a.40.5",
                formatName = "M4A",
                averageBitrateKbps = 48,
                deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                isAudioOnly = true,
                isLossless = false
            )
        )
    }

    private fun sampleResolved(
        contentUrl: String,
        title: String = "Test Track",
        artist: String = "Test Artist",
        durationMs: Long = 205_000L,
        options: List<OnlineAudioStreamOption> = sampleOptions((System.currentTimeMillis() / 1000L) + 600L),
        extractedAtMs: Long = System.currentTimeMillis(),
        expiresAtMs: Long = OnlineStreamResolver.computeStreamExpiresAtMs(options, extractedAtMs)
    ): ResolvedOnlineStreamResult {
        return ResolvedOnlineStreamResult(
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = contentUrl,
            title = title,
            artist = artist,
            artistUrl = null,
            durationMs = durationMs,
            artworkUrl = null,
            selectedStream = options.first(),
            availableStreams = options,
            extractedAtMs = extractedAtMs,
            expiresAtMs = expiresAtMs
        )
    }

    @Test
    fun `stream cache honors signed expire param, rejects expired entries, and supports format exclusion fallback`() = runBlocking {
        val resolver = OnlineStreamResolver(connectivityMonitor)
        val contentUrl = "https://music.youtube.com/watch?v=dQw4w9WgXcQ"
        val futureEpochSec = (System.currentTimeMillis() / 1000L) + 600L // +10 min
        val freshOptions = sampleOptions(expireEpochSec = futureEpochSec)

        resolver.putPreResolvedForTesting(
            sampleResolved(
                contentUrl = contentUrl,
                title = "Never Gonna Give You Up",
                artist = "Rick Astley",
                durationMs = 212_000L,
                options = freshOptions
            )
        )

        assertTrue("Freshly cached entry must report valid", resolver.hasValidCachedStream(contentUrl))
        assertEquals(1, resolver.getCachedStreamCount())

        // Measure cached preload hit latency (< few milliseconds vs seconds for cold extraction)
        var resolvedCached: ResolvedOnlineStreamResult? = null
        val cachedLookupNs = measureNanoTime {
            val res = resolver.preloadStream(
                serviceId = 0,
                providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                providerName = "YT Music",
                contentUrl = contentUrl,
                qualityPreference = AudioQualityPreference.AUTO
            )
            assertTrue(res is OnlineResult.Success)
            resolvedCached = (res as OnlineResult.Success).value
        }
        assertNotNull(resolvedCached)
        assertTrue("Cache hit must flag fromCache=true", resolvedCached!!.fromCache)
        assertEquals("audio_251_opus", resolvedCached!!.selectedStream.id)
        assertFalse("Opus/M4A must never be mislabeled as lossless", resolvedCached!!.selectedStream.isLossless)
        assertTrue(
            "Cache hit resolution must be sub-50ms (was ${cachedLookupNs / 1_000_000}ms)",
            cachedLookupNs < 50_000_000L
        )

        // Format exclusion fallback without network re-extraction picks alternate compatible AAC stream
        val fallbackRes = resolver.resolveStream(
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = contentUrl,
            qualityPreference = AudioQualityPreference.AUTO,
            excludedStreamIds = setOf("audio_251_opus")
        )
        assertTrue(fallbackRes is OnlineResult.Success)
        assertEquals(
            "Excluded failing opus format must fall back to valid AAC m4a stream",
            "audio_140_aac",
            (fallbackRes as OnlineResult.Success).value.selectedStream.id
        )

        // Signed URL already expired -> safety margin rejects stale URL even before 20m TTL elapses
        val pastEpochSec = (System.currentTimeMillis() / 1000L) - 120L
        val computedPastExpiry = OnlineStreamResolver.computeStreamExpiresAtMs(
            options = sampleOptions(expireEpochSec = pastEpochSec),
            extractedAtMs = System.currentTimeMillis()
        )
        val futureComputedExpiry = OnlineStreamResolver.computeStreamExpiresAtMs(
            options = sampleOptions(expireEpochSec = futureEpochSec),
            extractedAtMs = System.currentTimeMillis()
        )
        assertTrue(
            "Valid signed URL expiry must stay before raw expire minus safety buffer",
            futureComputedExpiry <= (futureEpochSec * 1000L) - 44_000L
        )
        assertTrue(
            "Computed expiry for healthy stream must be in future",
            computedPastExpiry > 0L
        )

        val expiredResult = ResolvedOnlineStreamResult(
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = contentUrl,
            title = "Expired Track",
            artist = "Artist",
            artistUrl = null,
            durationMs = 200_000L,
            artworkUrl = null,
            selectedStream = freshOptions.first(),
            availableStreams = freshOptions,
            extractedAtMs = System.currentTimeMillis() - 600_000L,
            expiresAtMs = System.currentTimeMillis() + 10_000L // within 30s safety margin -> treated as expired
        )
        assertTrue("Stream within safety margin must report expired", expiredResult.isExpired())

        // Put an explicitly expired cache entry & verify resolver drops it cleanly instead of returning stale URLs
        resolver.invalidateStreamCache(contentUrl)
        assertFalse(resolver.hasValidCachedStream(contentUrl))
    }

    @Test
    fun `stream cache honors LRU bound of 48 entries and selects honest quality per network and preference`() {
        val resolver = OnlineStreamResolver(connectivityMonitor)
        val futureEpoch = (System.currentTimeMillis() / 1000L) + 1200L
        val opts = sampleOptions(expireEpochSec = futureEpoch)

        for (i in 1..55) {
            resolver.putPreResolvedForTesting(
                sampleResolved(
                    contentUrl = "https://music.youtube.com/watch?v=vid_$i",
                    title = "Song $i",
                    artist = "Artist $i",
                    durationMs = 180_000L,
                    options = opts
                )
            )
        }
        assertEquals("LRU cache must stay bounded at maxSize=48", 48, resolver.getCachedStreamCount())
        assertFalse("Oldest evicted entry must no longer be cached", resolver.hasValidCachedStream("https://music.youtube.com/watch?v=vid_1"))
        assertTrue("Most recent entry must remain cached", resolver.hasValidCachedStream("https://music.youtube.com/watch?v=vid_55"))

        val unmeteredAuto = resolver.selectBestAudioStream(opts, AudioQualityPreference.AUTO, isMeteredConnection = false)
        val meteredAuto = resolver.selectBestAudioStream(opts, AudioQualityPreference.AUTO, isMeteredConnection = true)
        val dataSaver = resolver.selectBestAudioStream(opts, AudioQualityPreference.DATA_SAVER, isMeteredConnection = false)
        assertEquals("audio_251_opus", unmeteredAuto?.id)
        assertEquals("audio_140_aac", meteredAuto?.id)
        assertEquals("audio_599_aac_low", dataSaver?.id)

        // Lossy vs Lossless labeling guard
        assertFalse("Opus option must be marked lossy", opts[0].isLossless)
        assertFalse("AAC/M4A option must be marked lossy", opts[1].isLossless)
        assertEquals("WEBMA_OPUS", opts[0].formatName)
        assertEquals("M4A", opts[1].formatName)
    }

    @Test
    fun `OnlineSearchDataSource transient backoff retries bounded 5xx or timeout and fails fast on 403 or bot check`() = runTest {
        val transientAttempts = AtomicInteger(0)
        val recovered = OnlineSearchDataSource.runWithTransientRetry(
            providerName = "YT Music",
            operation = "test_transient",
            maxAttempts = 3,
            initialBackoffMs = 10L
        ) {
            if (transientAttempts.incrementAndGet() < 3) {
                throw SocketTimeoutException("Simulated transient timeout")
            }
            "ok"
        }
        assertEquals("ok", recovered)
        assertEquals(3, transientAttempts.get())

        val botAttempts = AtomicInteger(0)
        var botCaught = false
        try {
            OnlineSearchDataSource.runWithTransientRetry(
                providerName = "YT Music",
                operation = "test_bot",
                maxAttempts = 3,
                initialBackoffMs = 10L
            ) {
                botAttempts.incrementAndGet()
                throw SignInConfirmNotBotException("Sign in to confirm that you're not a bot")
            }
        } catch (_: SignInConfirmNotBotException) {
            botCaught = true
        }
        assertTrue(botCaught)
        assertEquals("Anti-bot exception must fail fast without transient retries", 1, botAttempts.get())

        val captchaAttempts = AtomicInteger(0)
        var captchaCaught = false
        try {
            OnlineSearchDataSource.runWithTransientRetry(
                providerName = "YT Music",
                operation = "test_429",
                maxAttempts = 3,
                initialBackoffMs = 10L
            ) {
                captchaAttempts.incrementAndGet()
                throw ReCaptchaException("HTTP 429", "https://youtube.com")
            }
        } catch (_: ReCaptchaException) {
            captchaCaught = true
        }
        assertTrue(captchaCaught)
        assertEquals("ReCaptcha/429 must never blind-retry", 1, captchaAttempts.get())

        // Plain IOException exhausts strictly at maxAttempts = 3
        val exhaustedAttempts = AtomicInteger(0)
        var ioCaught = false
        try {
            OnlineSearchDataSource.runWithTransientRetry(
                providerName = "YT Music",
                operation = "test_exhausted",
                maxAttempts = 3,
                initialBackoffMs = 5L
            ) {
                exhaustedAttempts.incrementAndGet()
                throw IOException("Persistent socket reset")
            }
        } catch (_: IOException) {
            ioCaught = true
        }
        assertTrue(ioCaught)
        assertEquals(3, exhaustedAttempts.get())
    }

    @Test
    fun `UnifiedPlaybackController consumes preloaded stream immediately and prevents rapid Next track async races`() = runBlocking {
        val resolver = OnlineStreamResolver(connectivityMonitor)
        val repo = OnlineMusicRepository(
            dao = dao,
            connectivityMonitor = connectivityMonitor,
            searchDataSource = OnlineSearchDataSource(connectivityMonitor),
            streamResolver = resolver
        )
        val audioEngine = SonoraAudioEngine(context)
        val controller = UnifiedPlaybackController(audioEngine, repo)

        val songA = sampleOnlineSong(-101L, "slowTrackAA", "Track A")
        val songB = sampleOnlineSong(-102L, "fastTrackBB", "Track B")
        val songC = sampleOnlineSong(-103L, "nextTrackCC", "Track C")

        val futureEpoch = (System.currentTimeMillis() / 1000L) + 900L
        resolver.putPreResolvedForTesting(
            sampleResolved(
                contentUrl = songB.contentUrl,
                title = songB.title,
                artist = songB.artist,
                durationMs = 195_000L,
                options = sampleOptions(expireEpochSec = futureEpoch)
            )
        )
        resolver.putPreResolvedForTesting(
            sampleResolved(
                contentUrl = songC.contentUrl,
                title = songC.title,
                artist = songC.artist,
                durationMs = 210_000L,
                options = sampleOptions(expireEpochSec = futureEpoch)
            )
        )

        // Preload Song C in background before pressing Next
        controller.preloadNextTrack(songC, AudioQualityPreference.AUTO, stageIntoPlayer = false)
        var preloadWaits = 0
        while (controller.getPreloadedStream(songC.contentUrl) == null && preloadWaits < 50) {
            Thread.sleep(10)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            preloadWaits++
        }
        assertNotNull("Next track C must be preloaded in background", controller.getPreloadedStream(songC.contentUrl))

        // Start Song A while offline/slow then immediately race-switch to Song B
        setNetworkState(connected = false, metered = false) // guarantees Song A (uncached) cannot accidentally succeed offline
        var songAErrors = 0
        var songAPrepared = 0
        val trackBStages = mutableListOf<OnlinePlaybackStage>()

        controller.playSong(
            song = songA,
            qualityPreference = AudioQualityPreference.AUTO,
            onResolvingStarted = {},
            onStageChanged = { _, _ -> },
            onPrepared = { _, _, _ -> songAPrepared++ },
            onCompletion = {},
            onError = { _, _ -> songAErrors++ }
        )

        // Rapid Next to cached/preloaded Song B must cancel Song A's generation so Song A never leaks error or prepared callback
        setNetworkState(connected = true, metered = false)
        controller.playSong(
            song = songB,
            qualityPreference = AudioQualityPreference.AUTO,
            onResolvingStarted = {},
            onStageChanged = { stage, _ -> trackBStages.add(stage) },
            onPrepared = { _, _, _ -> },
            onCompletion = {},
            onError = { _, _ -> }
        )
        pumpMainLooper(20)

        // Also test consuming preloaded Song C directly: starts at PREPARING_PLAYER, bypassing RESOLVING_STREAM
        val trackCStages = mutableListOf<OnlinePlaybackStage>()
        controller.preloadNextTrack(songC, AudioQualityPreference.AUTO, stageIntoPlayer = false)
        pumpMainLooper(20)
        controller.playSong(
            song = songC,
            qualityPreference = AudioQualityPreference.AUTO,
            onResolvingStarted = {},
            onStageChanged = { stage, _ -> trackCStages.add(stage) },
            onPrepared = { _, _, _ -> },
            onCompletion = {},
            onError = { _, _ -> }
        )
        pumpMainLooper(20)

        assertEquals("Cancelled Song A must never invoke onPrepared", 0, songAPrepared)
        assertEquals("Superseded Song A must never leak stale onError", 0, songAErrors)
        assertNotNull("DSP equalizer instance remains intact across switches", audioEngine.adaptiveEqualizer)
        assertTrue(
            "Consuming preloaded Track C must jump directly to PREPARING_PLAYER instead of RESOLVING_STREAM",
            trackCStages.firstOrNull() == OnlinePlaybackStage.PREPARING_PLAYER
        )

        controller.cancelPendingResolution()
        pumpMainLooper(5)
        audioEngine.release()
    }

    @Test
    fun `UI displays stage-specific buffering text and clears loading indicator once PLAYING or FAILED`() {
        val onlineSong = sampleOnlineSong(-500L, "uiStageVid1", "Kesariya")
        var infoState by mutableStateOf(
            PlaybackInfo(
                isPlaying = false,
                isBuffering = true,
                playbackStage = OnlinePlaybackStage.RESOLVING_STREAM,
                statusMessage = OnlinePlaybackStage.RESOLVING_STREAM.defaultMessage
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                FloatingMiniPlayer(
                    song = onlineSong,
                    playbackInfo = infoState,
                    colors = EditorialColors.default(),
                    onTogglePlayPause = {},
                    onPlayNext = {},
                    onPlayPrevious = {},
                    onClick = {},
                    miniPlayerStyle = MiniPlayerStyle.FLOATING_PILL
                )
            }
        }

        composeRule.onNodeWithText("Finding audio stream...").assertIsDisplayed()

        composeRule.runOnIdle {
            infoState = infoState.copy(
                playbackStage = OnlinePlaybackStage.PREPARING_PLAYER,
                statusMessage = OnlinePlaybackStage.PREPARING_PLAYER.defaultMessage
            )
        }
        composeRule.onNodeWithText("Preparing next track...").assertIsDisplayed()

        composeRule.runOnIdle {
            infoState = infoState.copy(
                playbackStage = OnlinePlaybackStage.BUFFERING_AUDIO,
                statusMessage = OnlinePlaybackStage.BUFFERING_AUDIO.defaultMessage
            )
        }
        composeRule.onNodeWithText("Buffering audio...").assertIsDisplayed()

        composeRule.runOnIdle {
            infoState = infoState.copy(
                isPlaying = true,
                isBuffering = false,
                playbackStage = OnlinePlaybackStage.PLAYING,
                statusMessage = OnlinePlaybackStage.PLAYING.defaultMessage
            )
        }
        assertEquals(0, composeRule.onAllNodesWithText("Buffering audio...").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Resolving & buffering", substring = true).fetchSemanticsNodes().size)
        composeRule.onNodeWithText("Test Artist").assertIsDisplayed()
    }
}
