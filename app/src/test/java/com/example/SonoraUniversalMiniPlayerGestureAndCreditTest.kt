package com.example

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.service.SonoraMusicService
import com.example.ui.components.MiniPlayerView
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.SonoraViewModel
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode
import domain.motion.GlobalMotionConfig
import domain.motion.resolveEffectiveMotionState
import domain.player.MiniPlayerVisualState
import domain.player.MusicMiniPlayerVisibilityPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.FloatingMiniPlayer
import ui.motion.LocalEffectiveMotionState
import ui.settings.BackgroundPlaybackSettingsScreen
import ui.settings.MusicExperienceSettingsScreen
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraUniversalMiniPlayerGestureAndCreditTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun sampleSong(id: Long, title: String, isOnline: Boolean): Song = Song(
        id = id,
        title = title,
        artist = if (isOnline) "Shreya Ghoshal" else "A. R. Rahman",
        album = if (isOnline) "Online Singles" else "Local Classics",
        albumId = id * 10,
        durationMs = 245_000L,
        uri = Uri.parse(
            if (isOnline) "https://music.youtube.com/watch?v=track$id"
            else "content://media/external/audio/media/$id"
        ),
        dataPath = if (isOnline) "" else "/storage/emulated/0/Music/$title.flac",
        mimeType = if (isOnline) "audio/mp4" else "audio/flac",
        sizeBytes = 6_200_000L,
        isOnline = isOnline,
        contentUrl = if (isOnline) "https://music.youtube.com/watch?v=track$id" else "",
        providerKey = if (isOnline) "youtube" else "local",
        providerName = if (isOnline) "YouTube Music" else "Local",
        genre = "Bollywood"
    )

    // =========================================================================
    // PART 1: DEVELOPER CREDIT & REMOVED RESOURCES
    // =========================================================================

    @Test
    fun developerCredit_preserved_andRemovedMiniPlayerSwipeAndUpdateStringsAbsent() {
        assertEquals("MAYUR", context.getString(R.string.app_name))
        assertEquals(
            "Where Sound Becomes Art.",
            context.getString(R.string.splash_brand_tagline)
        )
        assertEquals("Developed by", context.getString(R.string.developer_credit_label))
        assertEquals("Dr. M. GANESWAR", context.getString(R.string.developer_credit_name))
        assertEquals("BAMS Final Year", context.getString(R.string.developer_credit_qualification))

        val aboutFooter = context.getString(R.string.about_developer_footer)
        assertTrue(aboutFooter.contains("Dr. M. GANESWAR"))
        assertTrue(aboutFooter.contains("BAMS Final Year"))

        val res = context.resources
        val pkg = context.packageName
        // Removed mini-player swipe / circular / dismiss string resources
        assertEquals(0, res.getIdentifier("mini_player_action_minimize", "string", pkg))
        assertEquals(0, res.getIdentifier("mini_player_action_dismiss", "string", pkg))
        assertEquals(0, res.getIdentifier("mini_player_action_expand", "string", pkg))
        assertEquals(0, res.getIdentifier("mini_player_restore_desc", "string", pkg))
        // Removed app update / Telegram string resources
        assertEquals(0, res.getIdentifier("settings_app_update_section", "string", pkg))
        assertEquals(0, res.getIdentifier("settings_check_for_updates_title", "string", pkg))
        assertEquals(0, res.getIdentifier("settings_check_for_updates_subtitle", "string", pkg))
        assertEquals(0, res.getIdentifier("settings_update_link_unavailable", "string", pkg))
    }

    // =========================================================================
    // PART 2: MINI-PLAYER SWIPES RESTORED; TAP-TO-OPEN & BUTTONS PRESERVED
    // =========================================================================

    @Test
    fun floatingMiniPlayer_swipesTriggerSkipExpandCollapse_whileTapAndButtonsWork() {
        val song = sampleSong(101L, "Kun Faya Kun", isOnline = false)
        var playPauseClicks = 0
        var nextClicks = 0
        var prevClicks = 0
        var openFullPlayerClicks = 0
        var swipeUpExpands = 0
        var swipeDownCollapses = 0
        var activeStyle by mutableStateOf(MiniPlayerStyle.FLOATING_PILL)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalDynamicColorPalette provides DynamicColorPalette.default(),
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT),
                LocalReduceAnimations provides true
            ) {
                FloatingMiniPlayer(
                    song = song,
                    playbackInfo = PlaybackInfo(
                        isPlaying = true,
                        currentPositionMs = 45_000L,
                        durationMs = song.durationMs
                    ),
                    colors = EditorialColors.default(),
                    onTogglePlayPause = { playPauseClicks++ },
                    onPlayNext = { nextClicks++ },
                    onPlayPrevious = { prevClicks++ },
                    onClick = { openFullPlayerClicks++ },
                    onSwipeUpExpand = { swipeUpExpands++ },
                    onSwipeDownCollapse = { swipeDownCollapses++ },
                    miniPlayerStyle = activeStyle,
                    queue = listOf(song),
                    queueIndex = 0
                )
            }
        }

        // Swiping left -> next, right -> prev, up -> expand, down -> collapse Stage 1
        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals("Horizontal swipe left must trigger next once", 1, nextClicks)
        assertEquals("Horizontal swipe right must trigger previous once", 1, prevClicks)
        assertEquals("Vertical swipe up must expand full player once", 1, swipeUpExpands)
        assertEquals("Vertical swipe down must collapse to circular player once", 1, swipeDownCollapses)

        // Explicit buttons and normal tap-to-open must still work
        composeRule.onNodeWithTag("mini_play_pause_button").performClick()
        composeRule.onNodeWithTag("mini_skip_next_button").performClick()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.waitForIdle()

        assertEquals(1, playPauseClicks)
        assertEquals(2, nextClicks)
        assertEquals(1, openFullPlayerClicks)

        // Verify Previous button works on a style that exposes explicit Previous button
        activeStyle = MiniPlayerStyle.EDGE_TO_EDGE_MINI
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_skip_prev_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2, prevClicks)
    }

    @Test
    fun legacyMiniPlayerView_swipesTriggerSkipExpandDismiss_whileTapAndButtonsWork() {
        val song = sampleSong(202L, "Agar Tum Saath Ho", isOnline = true)
        var playPauseClicks = 0
        var nextClicks = 0
        var prevClicks = 0
        var openFullPlayerClicks = 0
        var swipeUpExpands = 0
        var swipeDownDismisses = 0

        composeRule.setContent {
            CompositionLocalProvider(
                LocalDynamicColorPalette provides DynamicColorPalette.default()
            ) {
                MiniPlayerView(
                    song = song,
                    playbackInfo = PlaybackInfo(isPlaying = true, currentPositionMs = 30_000L, durationMs = 200_000L),
                    onTogglePlayPause = { playPauseClicks++ },
                    onPlayNext = { nextClicks++ },
                    onPlayPrevious = { prevClicks++ },
                    onClick = { openFullPlayerClicks++ },
                    onSwipeUpExpand = { swipeUpExpands++ },
                    onSwipeDownDismiss = { swipeDownDismisses++ }
                )
            }
        }

        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(1, nextClicks)
        assertEquals(1, prevClicks)
        assertEquals(1, swipeUpExpands)
        assertEquals(1, swipeDownDismisses)

        composeRule.onNodeWithTag("mini_play_pause_button").performClick()
        composeRule.onNodeWithTag("mini_skip_next_button").performClick()
        composeRule.onNodeWithTag("mini_player").performClick()
        composeRule.waitForIdle()

        assertEquals(1, playPauseClicks)
        assertEquals(2, nextClicks)
        assertEquals(1, openFullPlayerClicks)
    }

    // =========================================================================
    // PART 3: TWO-STAGE VISIBILITY, ONLINE VIDEO ISOLATION & PLAYBACK SERVICE
    // =========================================================================

    @Test
    fun visibilityPolicy_preservesOnlineVideoIsolation_andSupportsTwoStageCircularAndDismissedStates() {
        assertEquals(
            setOf(
                MiniPlayerVisualState.EXPANDED_BAR,
                MiniPlayerVisualState.CIRCULAR_FLOATING,
                MiniPlayerVisualState.DISMISSED,
                MiniPlayerVisualState.HIDDEN
            ),
            MiniPlayerVisualState.entries.toSet()
        )
        assertEquals(20, MiniPlayerStyle.entries.size)

        val inOnlineVideo = MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
            currentTab = 0,
            homeMusicMode = HomeMusicMode.ONLINE_VIDEO,
            librarySectionMode = LibrarySectionMode.OFFLINE,
            isVideoPlayerExpanded = false,
            isVideoMiniPlayerVisible = false,
            isChannelDetailOpen = false
        )
        assertTrue(inOnlineVideo)

        val inOfflineMusic = MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
            currentTab = 0,
            homeMusicMode = HomeMusicMode.OFFLINE,
            librarySectionMode = LibrarySectionMode.OFFLINE,
            isVideoPlayerExpanded = false,
            isVideoMiniPlayerVisible = false,
            isChannelDetailOpen = false
        )
        assertFalse(inOfflineMusic)

        assertEquals(
            MiniPlayerVisualState.EXPANDED_BAR,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                isInOnlineVideoExperience = inOfflineMusic
            )
        )
        assertEquals(
            MiniPlayerVisualState.CIRCULAR_FLOATING,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                isInOnlineVideoExperience = inOfflineMusic,
                isCircularCollapsed = true
            )
        )
        assertEquals(
            MiniPlayerVisualState.DISMISSED,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                isInOnlineVideoExperience = inOfflineMusic,
                isDismissedByUser = true
            )
        )
        assertEquals(
            MiniPlayerVisualState.HIDDEN,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = false,
                isInOnlineVideoExperience = inOfflineMusic
            )
        )
        assertEquals(
            MiniPlayerVisualState.HIDDEN,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                isInOnlineVideoExperience = inOnlineVideo,
                isCircularCollapsed = true
            )
        )
        assertEquals(
            184.dp,
            MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                isMusicMiniPlayerVisible = true,
                isVideoMiniPlayerVisible = false
            )
        )
        assertEquals(
            104.dp,
            MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                isMusicMiniPlayerVisible = false,
                isVideoMiniPlayerVisible = false
            )
        )
    }

    @Test
    fun playbackService_mediaSessionAndForegroundNotificationRemainIntact() {
        val controller = Robolectric.buildService(SonoraMusicService::class.java).create()
        val service = controller.get()
        assertNotNull(service)
        val initialSession = service.mediaSession
        assertNotNull(initialSession)

        val tracks = listOf(
            sampleSong(101L, "Kun Faya Kun", isOnline = false),
            sampleSong(202L, "Agar Tum Saath Ho", isOnline = true)
        )

        for (track in tracks) {
            service.updateQueueSnapshot(listOf(track), 0)
            service.updateForegroundNotification(track, isPlaying = true)
            assertSame(initialSession, service.mediaSession)
            assertTrue(service.notificationManager.isForegroundNotificationActive)
            assertTrue(service.mediaSession?.isActive == true)
            assertEquals(track.id, service.currentQueueSnapshot.first().id)
        }

        controller.destroy()
    }

    // =========================================================================
    // PART 4: SETTINGS UI CONFIRMS ZERO UPDATE/TELEGRAM/CIRCULAR/DISMISS ITEMS
    // =========================================================================

    @Test
    fun settingsScreens_containNoUpdateTelegramCircularOrSwipeDismissControls() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalEffectiveMotionState provides resolveEffectiveMotionState(GlobalMotionConfig.DEFAULT)
            ) {
                SettingsScreen(viewModel = vm)
            }
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithTag("settings_check_for_updates_row").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Check for Updates", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Telegram", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Mini-Player Swipes", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Circular mini player", substring = true).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun backgroundAndMusicExperienceSettings_containNoCircularOrSwipeDismissControls() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        var screenMode by mutableStateOf(0)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default()
            ) {
                if (screenMode == 0) {
                    BackgroundPlaybackSettingsScreen(viewModel = vm, onBack = {})
                } else {
                    MusicExperienceSettingsScreen(viewModel = vm, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()

        // Background playback switches remain, circular mini player switches are gone
        composeRule.onNodeWithTag("setting_background_playback_switch").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("setting_circular_mini_player_switch").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Circular Mini Player", substring = true).fetchSemanticsNodes().isEmpty())

        // Switch to Music Experience settings
        screenMode = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("setting_unlimited_queue_switch").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("setting_swipe_dismiss_mini_switch").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Swipe Down to Dismiss", substring = true).fetchSemanticsNodes().isEmpty())
    }
}
