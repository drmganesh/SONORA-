package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDatabase
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerGestureConfig
import com.example.model.PlayerStyle
import com.example.model.Song
import com.example.model.VisualizerStyle
import data.online.NetworkConnectivityMonitor
import data.preferences.AmbientEffectsPreferences
import data.preferences.SnowfallFlakeSize
import data.preferences.SnowfallIntensity
import data.preferences.SnowfallSettings
import data.preferences.SnowfallSpeed
import data.video.OnlineVideoDataSource
import data.video.OnlineVideoRepository
import domain.home.HomeMusicMode
import domain.video.LikedVideoSortOption
import domain.video.OnlineVideoItem
import domain.video.VideoRatingState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.FloatingMiniPlayer
import ui.components.SnowfallOverlay
import ui.components.SnowfallParticlePool
import ui.player.PlayerRenderState
import ui.player.PlayerStyleCallbacks
import ui.player.PlayerStyleRenderer
import ui.player.rememberPlayerGestureController
import ui.player.seekbar.LocalPlaybackSeekCapability
import ui.player.seekbar.PlaybackSeekCapability
import ui.player.seekbar.SeekbarRenderer
import ui.player.seekbar.SeekbarStyle
import ui.settings.AmbientEffectsSettingsContent
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations
import ui.video.OnlineVideoLibraryContent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class SonoraLikedVideosSnowfallSeekTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: SonoraDatabase
    private lateinit var repository: OnlineVideoRepository

    private val videoA = OnlineVideoItem(
        videoId = "vid_liked_a",
        contentUrl = "https://www.youtube.com/watch?v=vid_liked_a",
        title = "Aurora Piano Live",
        channelName = "Nordic Keys",
        channelUrl = "https://www.youtube.com/channel/UCnordic",
        thumbnailUrl = "https://img.youtube.com/vi/vid_liked_a/hqdefault.jpg",
        durationSeconds = 245L,
        viewCount = 92_500L,
        uploadDateText = "3 days ago"
    )

    private val videoB = OnlineVideoItem(
        videoId = "vid_liked_b",
        contentUrl = "https://www.youtube.com/watch?v=vid_liked_b",
        title = "Midnight Jazz Quartet",
        channelName = "Blue Note Sessions",
        channelUrl = "https://www.youtube.com/channel/UCbluenote",
        thumbnailUrl = "https://img.youtube.com/vi/vid_liked_b/hqdefault.jpg",
        durationSeconds = 318L,
        viewCount = 41_200L,
        uploadDateText = "1 week ago"
    )

    private val offlineSong = Song(
        id = 701L,
        title = "Winter Nocturne",
        artist = "SONORA Ensemble",
        album = "Frost & Vinyl",
        albumId = 10L,
        durationMs = 200_000L,
        uri = Uri.EMPTY,
        dataPath = "/storage/emulated/0/Music/winter_nocturne.flac",
        mimeType = "audio/flac",
        sizeBytes = 15_000_000L,
        isOnline = false
    )

    private val onlineSong = Song(
        id = 702L,
        title = "Starlight Stream (Online)",
        artist = "Cloud Symphony",
        album = "Digital Horizon",
        albumId = 11L,
        durationMs = 240_000L,
        uri = Uri.EMPTY,
        dataPath = "",
        mimeType = "audio/opus",
        sizeBytes = 0L,
        isOnline = true,
        streamCodec = "OPUS",
        providerName = "YouTube"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataSource = OnlineVideoDataSource(NetworkConnectivityMonitor(context))
        repository = OnlineVideoRepository(db.dao(), dataSource)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // PART 1: LIKED VIDEOS IN ONLINE LIBRARY
    // =========================================================================

    @Test
    fun testLikedVideosAppearAndUnlikeOrClearRemovesImmediately() {
        runBlocking {
        repository.setLocalVideoRating(videoA, VideoRatingState.LIKED)
        repository.setLocalVideoRating(videoB, VideoRatingState.LIKED)

        val likedList = repository.likedVideosFlow.first()
        assertEquals(2, likedList.size)
        assertTrue(likedList.all { it.likedAt > 0L })
        assertTrue(likedList.any { it.videoId == videoA.videoId && it.title == videoA.title && it.channelName == videoA.channelName })

        // Render OnlineVideoLibraryContent with reactive state
        var uiLiked by mutableStateOf(likedList)
        var clearedConfirmed = false

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                OnlineVideoLibraryContent(
                    likedVideos = uiLiked,
                    watchLaterVideos = emptyList(),
                    videoHistory = emptyList(),
                    savedVideos = emptyList(),
                    subscribedChannels = emptyList(),
                    videoPlaylists = emptyList(),
                    activeVideoId = null,
                    colors = EditorialColors.default(),
                    onPlayVideo = { _, _ -> },
                    onUnlikeVideo = { videoId ->
                        runBlocking { repository.unlikeVideo(videoId) }
                        uiLiked = uiLiked.filterNot { it.videoId == videoId }
                    },
                    onClearAllLikedVideos = {
                        runBlocking { repository.clearAllLikedVideos() }
                        uiLiked = emptyList()
                        clearedConfirmed = true
                    },
                    onRemoveWatchLater = {},
                    onRemoveFromHistory = {},
                    onClearVideoHistory = {},
                    onRemoveSavedVideo = {},
                    onOpenChannel = {},
                    onRemoveChannelBookmark = {},
                    onCreatePlaylist = {},
                    onRenamePlaylist = { _, _ -> },
                    onDeletePlaylist = {},
                    onRemoveVideoFromPlaylist = { _, _ -> },
                    onVideoMoreClick = {},
                    onExploreOnlineVideos = {}
                )
            }
        }

        // Liked Videos tab is active by default and displays both videos
        composeRule.onNodeWithTag("video_library_tab_liked").assertIsDisplayed()
        composeRule.onNodeWithText("Aurora Piano Live").assertIsDisplayed()
        composeRule.onNodeWithText("Midnight Jazz Quartet").assertIsDisplayed()

        // Unlike videoA directly from Liked Videos
        composeRule.onNodeWithTag("compact_video_trailing_${videoA.videoId}").performClick()
        composeRule.waitForIdle()

        assertEquals(VideoRatingState.NONE, repository.getLocalVideoRating(videoA.videoId))
        assertEquals(1, repository.likedVideosFlow.first().size)
        composeRule.onNodeWithText("Aurora Piano Live").assertDoesNotExist()
        composeRule.onNodeWithText("Midnight Jazz Quartet").assertIsDisplayed()

        // Clear all liked videos with confirmation dialog
        composeRule.onNodeWithTag("clear_all_liked_videos_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_clear_all_liked_videos").performClick()
        composeRule.waitForIdle()

        assertTrue(clearedConfirmed)
        assertTrue(repository.likedVideosFlow.first().isEmpty())
        assertEquals(VideoRatingState.NONE, repository.getLocalVideoRating(videoB.videoId))
        composeRule.onNodeWithText("No Liked Videos yet").assertIsDisplayed()
        }
    }

    @Test
    fun testLikedVideoAndTimestampPersistAcrossRestart() {
        runBlocking {
        val dbName = "sonora_liked_restart_test.db"
        context.deleteDatabase(dbName)

        try {
            val firstDb = Room.databaseBuilder(context, SonoraDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val firstRepo = OnlineVideoRepository(firstDb.dao(), OnlineVideoDataSource(NetworkConnectivityMonitor(context)))
            firstRepo.setLocalVideoRating(videoA, VideoRatingState.LIKED)
            val savedBeforeClose = firstRepo.likedVideosFlow.first()
            assertEquals(1, savedBeforeClose.size)
            val savedLikedAt = savedBeforeClose.first().likedAt
            assertTrue(savedLikedAt > 0L)
            firstDb.close()

            // Reopen database to simulate app restart
            val reopenedDb = Room.databaseBuilder(context, SonoraDatabase::class.java, dbName)
                .allowMainThreadQueries()
                .build()
            val reopenedRepo = OnlineVideoRepository(reopenedDb.dao(), OnlineVideoDataSource(NetworkConnectivityMonitor(context)))

            assertEquals(VideoRatingState.LIKED, reopenedRepo.getLocalVideoRating(videoA.videoId))
            val likedAfterRestart = reopenedRepo.likedVideosFlow.first()
            assertEquals(1, likedAfterRestart.size)
            assertEquals(videoA.videoId, likedAfterRestart.first().videoId)
            assertEquals(videoA.title, likedAfterRestart.first().title)
            assertEquals(videoA.channelName, likedAfterRestart.first().channelName)
            assertEquals(savedLikedAt, likedAfterRestart.first().likedAt)
            reopenedDb.close()
        } finally {
            context.deleteDatabase(dbName)
        }
        }
    }

    @Test
    fun `4 - Liked videos sorting, Play All, and Add to Queue actions work accurately`() {
        val older = videoA.copy(videoId = "v1", title = "Zeta Echo", channelName = "Alpha Channel", likedAt = 1_000L, uploadDateText = "2 weeks ago")
        val newer = videoB.copy(videoId = "v2", title = "Aurora Dawn", channelName = "Zeta Channel", likedAt = 5_000L, uploadDateText = "1 day ago")
        val mid = videoA.copy(videoId = "v3", title = "Middle Groove", channelName = "Beta Channel", likedAt = 3_000L, uploadDateText = "3 hours ago")
        val input = listOf(older, newer, mid)

        assertEquals(listOf("v2", "v3", "v1"), LikedVideoSortOption.sort(input, LikedVideoSortOption.RECENTLY_LIKED).map { it.videoId })
        assertEquals(listOf("v2", "v3", "v1"), LikedVideoSortOption.sort(input, LikedVideoSortOption.TITLE).map { it.videoId })
        assertEquals(listOf("v1", "v3", "v2"), LikedVideoSortOption.sort(input, LikedVideoSortOption.CHANNEL).map { it.videoId })
        assertEquals(listOf("v3", "v2", "v1"), LikedVideoSortOption.sort(input, LikedVideoSortOption.DATE).map { it.videoId })

        var playedAllList: List<OnlineVideoItem> = emptyList()
        var queuedVideo: OnlineVideoItem? = null

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                OnlineVideoLibraryContent(
                    likedVideos = input,
                    watchLaterVideos = emptyList(),
                    videoHistory = emptyList(),
                    savedVideos = emptyList(),
                    subscribedChannels = emptyList(),
                    videoPlaylists = emptyList(),
                    activeVideoId = null,
                    colors = EditorialColors.default(),
                    onPlayVideo = { _, _ -> },
                    onPlayAllLikedVideos = { playedAllList = it },
                    onAddVideoToQueue = { queuedVideo = it },
                    onRemoveWatchLater = {},
                    onRemoveFromHistory = {},
                    onClearVideoHistory = {},
                    onRemoveSavedVideo = {},
                    onOpenChannel = {},
                    onRemoveChannelBookmark = {},
                    onCreatePlaylist = {},
                    onRenamePlaylist = { _, _ -> },
                    onDeletePlaylist = {},
                    onRemoveVideoFromPlaylist = { _, _ -> },
                    onVideoMoreClick = {},
                    onExploreOnlineVideos = {}
                )
            }
        }

        // Switch sort to Channel and verify Play All uses sorted order
        composeRule.onNodeWithTag("liked_sort_channel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_all_liked_videos_button").performClick()
        assertEquals(listOf("v1", "v3", "v2"), playedAllList.map { it.videoId })

        // Add individual video (first item in Channel-sorted list) to queue
        composeRule.onNodeWithTag("compact_video_secondary_v1").performClick()
        assertEquals("v1", queuedVideo?.videoId)
    }

    // =========================================================================
    // PART 2: SNOWFALL EFFECT & AMBIENT EFFECTS SETTINGS
    // =========================================================================

    @Test
    fun `5, 6 & 7 - Snowfall active in Offline Home, Online Home ONLY while playing, and hidden in Online Video`() {
        val enabledSettings = SnowfallSettings(enabled = true, intensity = SnowfallIntensity.LOW)
        val disabledSettings = SnowfallSettings(enabled = false)

        // 5. Offline Music Home: active whenever enabled (even if paused/no song)
        assertTrue(
            SnowfallSettings.shouldShowSnowfall(
                homeMusicMode = HomeMusicMode.OFFLINE,
                isMusicPlaying = false,
                hasCurrentSong = false,
                settings = enabledSettings
            )
        )
        assertFalse(
            SnowfallSettings.shouldShowSnowfall(
                homeMusicMode = HomeMusicMode.OFFLINE,
                isMusicPlaying = true,
                hasCurrentSong = true,
                settings = disabledSettings
            )
        )

        // 6. Online Music Home: active ONLY while music is actively playing
        assertTrue(
            SnowfallSettings.shouldShowSnowfall(
                homeMusicMode = HomeMusicMode.ONLINE,
                isMusicPlaying = true,
                hasCurrentSong = true,
                settings = enabledSettings
            )
        )
        assertFalse(
            SnowfallSettings.shouldShowSnowfall(
                homeMusicMode = HomeMusicMode.ONLINE,
                isMusicPlaying = false,
                hasCurrentSong = true,
                settings = enabledSettings
            )
        )
        assertFalse(
            SnowfallSettings.shouldShowSnowfall(
                homeMusicMode = HomeMusicMode.ONLINE,
                isMusicPlaying = true,
                hasCurrentSong = false,
                settings = enabledSettings
            )
        )

        // 7. Online Video mode: ALWAYS hidden even when playing
        assertFalse(
            SnowfallSettings.shouldShowSnowfall(
                homeMusicMode = HomeMusicMode.ONLINE_VIDEO,
                isMusicPlaying = true,
                hasCurrentSong = true,
                settings = enabledSettings
            )
        )

        // Verify SnowfallOverlay composes in Offline, stays visible in Online while playing,
        // and disappears when paused or switched to Online Video.
        // Control mainClock manually because SnowfallOverlay runs a continuous withFrameNanos loop.
        composeRule.mainClock.autoAdvance = false
        var mode by mutableStateOf(HomeMusicMode.OFFLINE)
        var isPlaying by mutableStateOf(false)

        composeRule.setContent {
            val active = SnowfallSettings.shouldShowSnowfall(
                homeMusicMode = mode,
                isMusicPlaying = isPlaying,
                hasCurrentSong = true,
                settings = enabledSettings
            )
            SnowfallOverlay(
                isActive = active,
                settings = enabledSettings,
                colors = EditorialColors.default(),
                testTagName = "home_snowfall_overlay"
            )
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithTag("home_snowfall_overlay").assertExists()

        // Switch to Online Music (paused) -> fades out and unmounts
        composeRule.runOnUiThread {
            mode = HomeMusicMode.ONLINE
            isPlaying = false
        }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("home_snowfall_overlay").assertDoesNotExist()

        // Start playback in Online Music -> snowfall activates
        composeRule.runOnUiThread {
            isPlaying = true
        }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("home_snowfall_overlay").assertExists()

        // Switch to Online Video -> snowfall disappears even though isPlaying == true
        composeRule.runOnUiThread {
            mode = HomeMusicMode.ONLINE_VIDEO
        }
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(1_200L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("home_snowfall_overlay").assertDoesNotExist()
    }

    @Test
    fun testSnowfallSettingsPersistInDataStoreAndSettingsUiUpdatesAndResets() {
        runBlocking {
        val prefs = AmbientEffectsPreferences(context)
        prefs.resetToDefaults()

        val initial = prefs.snowfallSettingsFlow.first()
        assertEquals(SnowfallSettings.DEFAULT, initial)
        assertEquals(SnowfallIntensity.LOW, initial.intensity)

        // Update every option and verify DataStore round-trip
        val custom = SnowfallSettings(
            enabled = true,
            intensity = SnowfallIntensity.HIGH,
            flakeSize = SnowfallFlakeSize.LARGE,
            speed = SnowfallSpeed.FAST,
            opacity = 0.68f,
            reduceAnimationsForBattery = true
        )
        prefs.saveSettings(custom)
        val persisted = prefs.snowfallSettingsFlow.first()
        assertEquals(custom, persisted)

        // Verify Settings UI updates & resets (pause autoAdvance since the live preview card animates snowfall)
        composeRule.mainClock.autoAdvance = false
        var uiSettings by mutableStateOf(persisted)
        var resetCalled = false

        composeRule.setContent {
            AmbientEffectsSettingsContent(
                settings = uiSettings,
                colors = EditorialColors.default(),
                onUpdateSettings = { transform -> uiSettings = transform(uiSettings) },
                onResetSettings = {
                    resetCalled = true
                    uiSettings = SnowfallSettings.DEFAULT
                },
                onBack = {}
            )
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32L)
        composeRule.onNodeWithTag("ambient_effects_settings_screen").assertIsDisplayed()

        composeRule.onNodeWithTag("snowfall_intensity_medium").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32L)
        assertEquals(SnowfallIntensity.MEDIUM, uiSettings.intensity)

        composeRule.onNodeWithTag("snowfall_size_small").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32L)
        assertEquals(SnowfallFlakeSize.SMALL, uiSettings.flakeSize)

        composeRule.onNodeWithTag("snowfall_speed_slow").performScrollTo().performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32L)
        assertEquals(SnowfallSpeed.SLOW, uiSettings.speed)

        composeRule.onNodeWithTag("ambient_effects_reset_button").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32L)
        assertTrue(resetCalled)
        assertEquals(SnowfallSettings.DEFAULT, uiSettings)

        prefs.resetToDefaults()
        }
    }

    @Test
    fun `13 - Snowfall performance throttling and particle pool stability for low-RAM and battery-saving`() {
        val highSettings = SnowfallSettings(
            enabled = true,
            intensity = SnowfallIntensity.HIGH,
            reduceAnimationsForBattery = false
        )
        val batterySettings = highSettings.copy(reduceAnimationsForBattery = true)

        val normalCount = highSettings.effectiveParticleCount(isLowRamDevice = false, globalReduceAnimations = false)
        val lowRamCount = highSettings.effectiveParticleCount(isLowRamDevice = true, globalReduceAnimations = false)
        val batteryCount = batterySettings.effectiveParticleCount(isLowRamDevice = false, globalReduceAnimations = false)
        val combinedCount = batterySettings.effectiveParticleCount(isLowRamDevice = true, globalReduceAnimations = true)

        assertTrue("Low-RAM device should reduce flake budget", lowRamCount < normalCount)
        assertTrue("Battery saving mode should reduce flake budget", batteryCount < normalCount)
        assertTrue("Combined throttling keeps minimum soft flakes", combinedCount in 8..lowRamCount)
        assertEquals(33_333_333L, batterySettings.minFrameIntervalNanos())
        assertEquals(0L, highSettings.minFrameIntervalNanos())

        // Verify SnowfallParticlePool advances and wraps smoothly over many frames without NaN/out-of-bounds
        val pool = SnowfallParticlePool(count = 24)
        val initialY0 = pool.y[0]
        for (frame in 1..200) {
            pool.step(deltaSec = 0.016f, elapsedSec = frame * 0.016f, speedMultiplier = 1.0f)
        }
        assertTrue(pool.y[0] != initialY0)
        for (i in 0 until pool.count) {
            assertFalse(pool.x[i].isNaN())
            assertFalse(pool.y[i].isNaN())
            assertTrue(pool.x[i] in -0.1f..1.1f)
            assertTrue(pool.y[i] in -0.1f..1.1f)
        }
    }

    // =========================================================================
    // PART 3: DRAG-TO-SEEK MUSIC (OFFLINE, ONLINE, MINI PLAYERS, FULL STYLES, LIVE)
    // =========================================================================

    @Test
    fun `9 - Dragging progress slider forward and backward in offline playback previews and commits once on release`() {
        val seekCalls = mutableListOf<Long>()
        var currentPos by mutableStateOf(60_000L) // 1:00 of 3:20 (30%)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalPlaybackSeekCapability provides PlaybackSeekCapability(isSeekable = true, isLiveStream = false)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    SeekbarRenderer(
                        currentPositionMs = currentPos,
                        durationMs = offlineSong.durationMs,
                        onSeekTo = { target ->
                            seekCalls.add(target)
                            currentPos = target
                        },
                        isPlaying = true,
                        style = SeekbarStyle.CLASSIC,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Drag forward from 30% to 80% while holding down: preview timestamp appears, ZERO seekTo calls until release!
        composeRule.onNodeWithTag("music_seekbar_track").performTouchInput {
            down(Offset(width * 0.30f, centerY))
            moveTo(Offset(width * 0.55f, centerY))
            moveTo(Offset(width * 0.80f, centerY))
        }
        composeRule.waitForIdle()
        assertEquals("Must NOT spam ExoPlayer seekTo while dragging", 0, seekCalls.size)
        composeRule.onNodeWithTag("seekbar_preview_timestamp").assertIsDisplayed()

        // Release pointer -> single final seekTo commit near 80% (160_000ms)
        composeRule.onNodeWithTag("music_seekbar_track").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("Must commit exactly once on release", 1, seekCalls.size)
        assertTrue("Forward seek should land near 160s, got ${seekCalls.first()}", seekCalls.first() in 148_000L..172_000L)

        // Now drag backward from 80% to 20%
        composeRule.onNodeWithTag("music_seekbar_track").performTouchInput {
            down(Offset(width * 0.80f, centerY))
            moveTo(Offset(width * 0.50f, centerY))
            moveTo(Offset(width * 0.20f, centerY))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(2, seekCalls.size)
        assertTrue("Backward seek should land near 40s, got ${seekCalls.last()}", seekCalls.last() in 28_000L..52_000L)
    }

    @Test
    fun `10 - Dragging progress slider forward and backward in online music playback commits accurately`() {
        val seekCalls = mutableListOf<Long>()
        val duration = onlineSong.durationMs // 240_000ms

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalPlaybackSeekCapability provides PlaybackSeekCapability(isSeekable = true, isLiveStream = false)
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    SeekbarRenderer(
                        currentPositionMs = 48_000L,
                        durationMs = duration,
                        onSeekTo = { seekCalls.add(it) },
                        isPlaying = true,
                        style = SeekbarStyle.WAVEFORM,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Drag forward to ~75% (180_000ms)
        composeRule.onNodeWithTag("music_seekbar_track").performTouchInput {
            down(Offset(width * 0.25f, centerY))
            moveTo(Offset(width * 0.75f, centerY))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(1, seekCalls.size)
        assertTrue("Expected ~180s, got ${seekCalls[0]}", seekCalls[0] in 165_000L..195_000L)

        // Drag backward to ~15% (36_000ms)
        composeRule.onNodeWithTag("music_seekbar_track").performTouchInput {
            down(Offset(width * 0.75f, centerY))
            moveTo(Offset(width * 0.15f, centerY))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(2, seekCalls.size)
        assertTrue("Expected ~36s, got ${seekCalls[1]}", seekCalls[1] in 20_000L..50_000L)
    }

    @Test
    fun `11a - Drag-to-seek works across Mini Player styles and Expanded Mini Player without triggering swipe-to-skip`() {
        val miniStylesToVerify = listOf(
            MiniPlayerStyle.FLOATING_PILL,
            MiniPlayerStyle.COMPACT_BAR,
            MiniPlayerStyle.ARTWORK_FOCUS,
            MiniPlayerStyle.WAVE_MINI,
            MiniPlayerStyle.GLASS_BAR,
            MiniPlayerStyle.AMOLED_BAR,
            MiniPlayerStyle.NEON_MINI,
            MiniPlayerStyle.MATERIAL_YOU_MINI,
            MiniPlayerStyle.EXPANDABLE_CARD
        )

        var activeMiniStyle by mutableStateOf(miniStylesToVerify.first())
        val seekCalls = mutableListOf<Long>()
        var skipNextCount = 0
        var skipPrevCount = 0
        var openFullPlayerCount = 0

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalDynamicColorPalette provides DynamicColorPalette.default(),
                LocalReduceAnimations provides true
            ) {
                FloatingMiniPlayer(
                    song = onlineSong,
                    playbackInfo = PlaybackInfo(
                        isPlaying = true,
                        currentPositionMs = 40_000L,
                        durationMs = onlineSong.durationMs,
                        isSeekable = true,
                        isLiveStream = false
                    ),
                    colors = EditorialColors.default(),
                    onTogglePlayPause = {},
                    onPlayNext = { skipNextCount++ },
                    onPlayPrevious = { skipPrevCount++ },
                    onClick = { openFullPlayerCount++ },
                    onSeekTo = { seekCalls.add(it) },
                    miniPlayerStyle = activeMiniStyle,
                    queue = listOf(offlineSong, onlineSong),
                    queueIndex = 0
                )
            }
        }

        miniStylesToVerify.forEach { style ->
            activeMiniStyle = style
            seekCalls.clear()
            composeRule.waitForIdle()

            composeRule.onAllNodesWithTag("mini_player_seekbar").onFirst().performTouchInput {
                down(Offset(width * 0.25f, centerY))
                moveTo(Offset(width * 0.70f, centerY))
                up()
            }
            composeRule.waitForIdle()

            assertEquals("Style ${style.id} must commit seek once", 1, seekCalls.size)
            assertTrue(
                "Style ${style.id} seek target should be near 70% (168s), got ${seekCalls.first()}",
                seekCalls.first() in 145_000L..190_000L
            )
            assertEquals("Scrubbing mini seekbar in ${style.id} must NOT trigger skipNext", 0, skipNextCount)
            assertEquals("Scrubbing mini seekbar in ${style.id} must NOT trigger skipPrev", 0, skipPrevCount)
        }

        // Also expand EXPANDABLE_CARD and verify its expanded seekbar with live timestamps
        activeMiniStyle = MiniPlayerStyle.EXPANDABLE_CARD
        seekCalls.clear()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_expand_toggle_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_seekbar_elapsed_time", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player_seekbar").performTouchInput {
            down(Offset(width * 0.20f, centerY))
            moveTo(Offset(width * 0.60f, centerY))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(1, seekCalls.size)
        assertTrue(seekCalls.first() in 125_000L..165_000L)
    }

    @Test
    fun `11b - Drag-to-seek works across Full-Screen Now Playing Player Styles`() {
        val fullStyles = listOf(
            PlayerStyle.GLASS,
            PlayerStyle.AMOLED_MINIMAL,
            PlayerStyle.VINYL,
            PlayerStyle.MATERIAL_YOU
        )
        var currentStyle by mutableStateOf(fullStyles.first())
        val seekCalls = mutableListOf<Long>()

        composeRule.setContent {
            val gestureController = rememberPlayerGestureController()
            val playback = PlaybackInfo(
                isPlaying = true,
                currentPositionMs = 50_000L,
                durationMs = offlineSong.durationMs,
                isSeekable = true,
                isLiveStream = false
            )
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalDynamicColorPalette provides DynamicColorPalette.default(),
                LocalReduceAnimations provides true,
                LocalPlaybackSeekCapability provides PlaybackSeekCapability(
                    isSeekable = playback.isSeekable,
                    isLiveStream = playback.isLiveStream
                )
            ) {
                PlayerStyleRenderer(
                    style = currentStyle,
                    state = PlayerRenderState(
                        song = offlineSong,
                        playbackInfo = playback,
                        seekbarStyle = SeekbarStyle.CLASSIC,
                        appearance = PlayerAppearanceConfig.DEFAULT,
                        gestureConfig = PlayerGestureConfig.DEFAULT,
                        dynamicPalette = DynamicColorPalette.default(),
                        frequencies = FloatArray(16) { 0.4f },
                        waveformSamples = FloatArray(32) { 0.5f },
                        isVisualizerActive = false,
                        visualizerStyle = VisualizerStyle.SPECTRUM_BARS,
                        isNowPlayingActive = true,
                        queue = listOf(offlineSong, onlineSong),
                        queueIndex = 0
                    ),
                    callbacks = PlayerStyleCallbacks(
                        onTogglePlayPause = {},
                        onPlayNext = {},
                        onPlayPrevious = {},
                        onSeekTo = { seekCalls.add(it) },
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onOpenQueue = {},
                        onOpenLyrics = {},
                        onOpenSleepTimer = {},
                        onClose = {},
                        onArtworkLongPress = {},
                        onToggleFavorite = {},
                        onOpenAudioEffects = {},
                        onOpenEqualizer = {},
                        onOpenOutputDevice = {},
                        onToggleFocusMode = {},
                        onOpenCustomization = {},
                        onCyclePlayerStyle = {},
                        onToggleVisualizer = {},
                        onCycleVisualizerStyle = {}
                    ),
                    gestureController = gestureController,
                    respectLayoutOverride = false
                )
            }
        }

        fullStyles.forEach { style ->
            currentStyle = style
            seekCalls.clear()
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("music_seekbar_track").performTouchInput {
                down(Offset(width * 0.25f, centerY))
                moveTo(Offset(width * 0.65f, centerY))
                up()
            }
            composeRule.waitForIdle()

            assertEquals("Full-screen style ${style.id} must commit seek once", 1, seekCalls.size)
            assertTrue(
                "Full-screen style ${style.id} seek target should be near 130s, got ${seekCalls.first()}",
                seekCalls.first() in 110_000L..150_000L
            )
        }
    }

    @Test
    fun `12 - Non-seekable and live streams disable dragging gracefully and show LIVE indicator`() {
        val livePlayback = PlaybackInfo(
            isPlaying = true,
            currentPositionMs = 15_000L,
            durationMs = 0L,
            isSeekable = false,
            isLiveStream = true
        )
        val nonSeekableVod = PlaybackInfo(
            isPlaying = true,
            currentPositionMs = 30_000L,
            durationMs = 180_000L,
            isSeekable = false,
            isLiveStream = false
        )
        assertFalse(livePlayback.canUserSeek)
        assertFalse(nonSeekableVod.canUserSeek)

        val seekCalls = mutableListOf<Long>()
        var currentCapability by mutableStateOf(PlaybackSeekCapability(isSeekable = false, isLiveStream = true))
        var currentDuration by mutableStateOf(0L)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalPlaybackSeekCapability provides currentCapability
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    SeekbarRenderer(
                        currentPositionMs = 15_000L,
                        durationMs = currentDuration,
                        onSeekTo = { seekCalls.add(it) },
                        isPlaying = true,
                        style = SeekbarStyle.CLASSIC,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Live stream shows LIVE badge and ignores drag gestures without crashing
        composeRule.onNodeWithTag("seekbar_live_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("music_seekbar_track").performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.8f, centerY))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Live stream must not trigger onSeekTo", seekCalls.isEmpty())

        // Non-seekable non-live stream shows non-seekable indicator and ignores drag
        currentCapability = PlaybackSeekCapability(isSeekable = false, isLiveStream = false)
        currentDuration = 180_000L
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("seekbar_non_seekable_label").assertIsDisplayed()
        composeRule.onNodeWithTag("music_seekbar_track").performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.8f, centerY))
            up()
        }
        composeRule.waitForIdle()
        assertTrue("Non-seekable stream must not trigger onSeekTo", seekCalls.isEmpty())
    }
}
