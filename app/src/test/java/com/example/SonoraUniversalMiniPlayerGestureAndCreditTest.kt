package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.model.MiniPlayerStyle
import com.example.model.Song
import com.example.service.SonoraMusicService
import data.preferences.PlaybackLifecyclePreferences
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode
import domain.player.CircularMiniPlayerGestureOutcome
import domain.player.ExpandedMiniPlayerGestureOutcome
import domain.player.MiniPlayerDisplayState
import domain.player.MiniPlayerGestureAxis
import domain.player.MiniPlayerGestureController
import domain.player.MiniPlayerVisualState
import domain.player.MusicMiniPlayerVisibilityPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraUniversalMiniPlayerGestureAndCreditTest {

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
    // PART 1: DEVELOPER CREDIT IN APP INTRO & ABOUT SONORA
    // =========================================================================

    @Test
    fun developerCredit_resourcesMatchExactSpecification() {
        assertEquals("SONORA", context.getString(R.string.app_name))
        assertEquals(
            "A Music Experience Beyond Listening",
            context.getString(R.string.splash_brand_tagline)
        )
        assertEquals("Developed by", context.getString(R.string.developer_credit_label))
        assertEquals("Dr. M. GANESWAR", context.getString(R.string.developer_credit_name))
        assertEquals("BAMS Final Year", context.getString(R.string.developer_credit_qualification))

        val aboutFooter = context.getString(R.string.about_developer_footer)
        assertTrue(aboutFooter.contains("Dr. M. GANESWAR"))
        assertTrue(aboutFooter.contains("BAMS Final Year"))
        assertTrue(context.getString(R.string.about_developer_name).contains("Dr. M. GANESWAR"))
        assertTrue(context.getString(R.string.about_developer_qualification).contains("BAMS Final Year"))

        // Accessibility action strings exist and are non-blank
        assertTrue(context.getString(R.string.mini_player_action_minimize).isNotBlank())
        assertTrue(context.getString(R.string.mini_player_action_dismiss).isNotBlank())
        assertTrue(context.getString(R.string.mini_player_action_expand).isNotBlank())
    }

    // =========================================================================
    // PARTS 2 & 3: UNIVERSAL TWO-STAGE GESTURE ACROSS ALL 20 MINI-PLAYER STYLES
    // =========================================================================

    @Test
    fun universalTwoStageSwipeDown_worksIdenticallyAcrossAll20MiniPlayerStyles() {
        val styles = MiniPlayerStyle.entries
        assertEquals("Expected all 20 MiniPlayerStyle designs", 20, styles.size)

        val touchSlop = 24f
        val swipeThreshold = 160f
        val velocityThreshold = 1200f
        val verticalExpand = 72f
        val verticalDismiss = 104f
        val circularDismissDistance = 220f
        val circularDismissVelocity = 1700f

        for (style in styles) {
            var state = MiniPlayerDisplayState.EXPANDED_MINI_PLAYER

            // Stage 1: Downward swipe on expanded mini player -> Floating Circular Player
            val axis = MiniPlayerGestureController.lockGestureAxis(
                accumX = 6f,
                accumY = 128f,
                touchSlopPx = touchSlop
            )
            assertEquals("Style ${style.name} must lock vertical axis", MiniPlayerGestureAxis.VERTICAL, axis)

            val stage1Outcome = MiniPlayerGestureController.classifyExpandedGesture(
                lockedAxis = axis,
                dragX = 6f,
                dragY = 128f,
                velocityX = 20f,
                velocityY = 1100f,
                swipeThresholdPx = swipeThreshold,
                velocityThresholdPx = velocityThreshold,
                verticalExpandPx = verticalExpand,
                verticalDismissPx = verticalDismiss,
                touchSlopPx = touchSlop,
                horizontalGesturesEnabled = true,
                swipeDownEnabled = true,
                style = style
            )
            assertEquals(
                "Style ${style.name} must trigger Stage 1 SWIPE_DOWN_MINIMIZE",
                ExpandedMiniPlayerGestureOutcome.SWIPE_DOWN_MINIMIZE,
                stage1Outcome
            )

            state = MiniPlayerGestureController.onSwipeDown(
                currentState = state,
                circularMiniPlayerEnabled = true,
                swipeDownDismissEnabled = true
            )
            assertEquals(
                "Style ${style.name} Stage 1 must transition to CIRCULAR_MINI_PLAYER",
                MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER,
                state
            )

            // Stage 2: Downward swipe on circular floating player -> Completely Dismissed
            val stage2Outcome = MiniPlayerGestureController.classifyCircularGesture(
                dragX = 14f,
                dragY = 260f,
                velocityX = 30f,
                velocityY = 1950f,
                dismissDistancePx = circularDismissDistance,
                dismissVelocityPx = circularDismissVelocity,
                touchSlopPx = touchSlop,
                swipeDownDismissEnabled = true
            )
            assertEquals(
                "Style ${style.name} must trigger Stage 2 SWIPE_DOWN_DISMISS",
                CircularMiniPlayerGestureOutcome.SWIPE_DOWN_DISMISS,
                stage2Outcome
            )

            state = MiniPlayerGestureController.onSwipeDown(
                currentState = state,
                circularMiniPlayerEnabled = true,
                swipeDownDismissEnabled = true
            )
            assertEquals(
                "Style ${style.name} Stage 2 must transition to DISMISSED",
                MiniPlayerDisplayState.DISMISSED,
                state
            )

            // Subsequent swipe down while already dismissed stays safely DISMISSED
            state = MiniPlayerGestureController.onSwipeDown(
                currentState = state,
                circularMiniPlayerEnabled = true,
                swipeDownDismissEnabled = true
            )
            assertEquals(MiniPlayerDisplayState.DISMISSED, state)

            // Restoration returns to EXPANDED_MINI_PLAYER
            state = MiniPlayerGestureController.onRestoreMiniPlayer()
            assertEquals(MiniPlayerDisplayState.EXPANDED_MINI_PLAYER, state)
        }
    }

    // =========================================================================
    // PARTS 4 & 5: GESTURE DISAMBIGUATION (HORIZONTAL, UPWARD, TAPS, DRAG VS DISMISS)
    // =========================================================================

    @Test
    fun expandedMiniPlayer_distinguishesHorizontalSwipesUpwardExpandAndAccidentalTaps() {
        val touchSlop = 24f
        val swipeThreshold = 160f
        val velocityThreshold = 1200f
        val verticalExpand = 72f
        val verticalDismiss = 104f

        // 1. Ordinary tap / micro-jitter -> NONE (never minimizes or skips)
        val tapAxis = MiniPlayerGestureController.lockGestureAxis(4f, 6f, touchSlop)
        assertEquals(MiniPlayerGestureAxis.UNDECIDED, tapAxis)
        assertEquals(
            ExpandedMiniPlayerGestureOutcome.NONE,
            MiniPlayerGestureController.classifyExpandedGesture(
                lockedAxis = tapAxis,
                dragX = 4f,
                dragY = 6f,
                velocityX = 0f,
                velocityY = 0f,
                swipeThresholdPx = swipeThreshold,
                velocityThresholdPx = velocityThreshold,
                verticalExpandPx = verticalExpand,
                verticalDismissPx = verticalDismiss,
                touchSlopPx = touchSlop,
                horizontalGesturesEnabled = true,
                swipeDownEnabled = true
            )
        )

        // 2. Horizontal swipe left -> SWIPE_NEXT (does NOT dismiss)
        val leftAxis = MiniPlayerGestureController.lockGestureAxis(-190f, 18f, touchSlop)
        assertEquals(MiniPlayerGestureAxis.HORIZONTAL, leftAxis)
        assertEquals(
            ExpandedMiniPlayerGestureOutcome.SWIPE_NEXT,
            MiniPlayerGestureController.classifyExpandedGesture(
                lockedAxis = leftAxis,
                dragX = -190f,
                dragY = 18f,
                velocityX = -1500f,
                velocityY = 40f,
                swipeThresholdPx = swipeThreshold,
                velocityThresholdPx = velocityThreshold,
                verticalExpandPx = verticalExpand,
                verticalDismissPx = verticalDismiss,
                touchSlopPx = touchSlop,
                horizontalGesturesEnabled = true,
                swipeDownEnabled = true
            )
        )

        // 3. Horizontal swipe right -> SWIPE_PREVIOUS (does NOT dismiss)
        val rightAxis = MiniPlayerGestureController.lockGestureAxis(185f, 22f, touchSlop)
        assertEquals(MiniPlayerGestureAxis.HORIZONTAL, rightAxis)
        assertEquals(
            ExpandedMiniPlayerGestureOutcome.SWIPE_PREVIOUS,
            MiniPlayerGestureController.classifyExpandedGesture(
                lockedAxis = rightAxis,
                dragX = 185f,
                dragY = 22f,
                velocityX = 1400f,
                velocityY = 30f,
                swipeThresholdPx = swipeThreshold,
                velocityThresholdPx = velocityThreshold,
                verticalExpandPx = verticalExpand,
                verticalDismissPx = verticalDismiss,
                touchSlopPx = touchSlop,
                horizontalGesturesEnabled = true,
                swipeDownEnabled = true
            )
        )

        // 4. Upward swipe -> SWIPE_UP_EXPAND
        val upAxis = MiniPlayerGestureController.lockGestureAxis(8f, -110f, touchSlop)
        assertEquals(MiniPlayerGestureAxis.VERTICAL, upAxis)
        assertEquals(
            ExpandedMiniPlayerGestureOutcome.SWIPE_UP_EXPAND,
            MiniPlayerGestureController.classifyExpandedGesture(
                lockedAxis = upAxis,
                dragX = 8f,
                dragY = -110f,
                velocityX = 10f,
                velocityY = -1100f,
                swipeThresholdPx = swipeThreshold,
                velocityThresholdPx = velocityThreshold,
                verticalExpandPx = verticalExpand,
                verticalDismissPx = verticalDismiss,
                touchSlopPx = touchSlop,
                horizontalGesturesEnabled = true,
                swipeDownEnabled = true
            )
        )

        // 5. Short downward nudge below threshold & velocity -> NONE
        val shortDownAxis = MiniPlayerGestureController.lockGestureAxis(2f, 40f, touchSlop)
        assertEquals(
            ExpandedMiniPlayerGestureOutcome.NONE,
            MiniPlayerGestureController.classifyExpandedGesture(
                lockedAxis = shortDownAxis,
                dragX = 2f,
                dragY = 40f,
                velocityX = 0f,
                velocityY = 120f,
                swipeThresholdPx = swipeThreshold,
                velocityThresholdPx = velocityThreshold,
                verticalExpandPx = verticalExpand,
                verticalDismissPx = verticalDismiss,
                touchSlopPx = touchSlop,
                horizontalGesturesEnabled = true,
                swipeDownEnabled = true
            )
        )
    }

    @Test
    fun circularMiniPlayer_distinguishesRepositionDragTapAndStage2DownwardDismiss() {
        val touchSlop = 24f
        val dismissDist = 220f
        val dismissVel = 1700f

        // 1. Ordinary tap (< touchSlop) -> NONE (lets clickable expand the mini player)
        assertEquals(
            CircularMiniPlayerGestureOutcome.NONE,
            MiniPlayerGestureController.classifyCircularGesture(
                dragX = 5f,
                dragY = 8f,
                velocityX = 0f,
                velocityY = 0f,
                dismissDistancePx = dismissDist,
                dismissVelocityPx = dismissVel,
                touchSlopPx = touchSlop
            )
        )

        // 2. Horizontal reposition drag across screen -> REPOSITION_DRAG (never dismisses)
        assertEquals(
            CircularMiniPlayerGestureOutcome.REPOSITION_DRAG,
            MiniPlayerGestureController.classifyCircularGesture(
                dragX = -380f,
                dragY = 40f,
                velocityX = -600f,
                velocityY = 120f,
                dismissDistancePx = dismissDist,
                dismissVelocityPx = dismissVel,
                touchSlopPx = touchSlop
            )
        )
        assertEquals(
            0f,
            MiniPlayerGestureController.circularDismissPreviewProgress(-380f, 40f, dismissDist, touchSlop),
            0.001f
        )

        // 3. Upward reposition drag -> REPOSITION_DRAG (never dismisses)
        assertEquals(
            CircularMiniPlayerGestureOutcome.REPOSITION_DRAG,
            MiniPlayerGestureController.classifyCircularGesture(
                dragX = 12f,
                dragY = -280f,
                velocityX = 20f,
                velocityY = -900f,
                dismissDistancePx = dismissDist,
                dismissVelocityPx = dismissVel,
                touchSlopPx = touchSlop
            )
        )

        // 4. Diagonal side drag -> REPOSITION_DRAG (never dismisses)
        assertEquals(
            CircularMiniPlayerGestureOutcome.REPOSITION_DRAG,
            MiniPlayerGestureController.classifyCircularGesture(
                dragX = 210f,
                dragY = 230f,
                velocityX = 500f,
                velocityY = 500f,
                dismissDistancePx = dismissDist,
                dismissVelocityPx = dismissVel,
                touchSlopPx = touchSlop
            )
        )

        // 5. Downward drag cancelled by pulling back upward at release -> REPOSITION_DRAG
        assertEquals(
            CircularMiniPlayerGestureOutcome.REPOSITION_DRAG,
            MiniPlayerGestureController.classifyCircularGesture(
                dragX = 8f,
                dragY = 240f,
                velocityX = 0f,
                velocityY = -520f,
                dismissDistancePx = dismissDist,
                dismissVelocityPx = dismissVel,
                touchSlopPx = touchSlop
            )
        )

        // 6. Fast downward flick -> SWIPE_DOWN_DISMISS
        assertEquals(
            CircularMiniPlayerGestureOutcome.SWIPE_DOWN_DISMISS,
            MiniPlayerGestureController.classifyCircularGesture(
                dragX = 10f,
                dragY = 110f,
                velocityX = 40f,
                velocityY = 2100f,
                dismissDistancePx = dismissDist,
                dismissVelocityPx = dismissVel,
                touchSlopPx = touchSlop
            )
        )

        // 7. Deliberate downward swipe past dismiss distance -> SWIPE_DOWN_DISMISS
        assertEquals(
            CircularMiniPlayerGestureOutcome.SWIPE_DOWN_DISMISS,
            MiniPlayerGestureController.classifyCircularGesture(
                dragX = 18f,
                dragY = 250f,
                velocityX = 10f,
                velocityY = 420f,
                dismissDistancePx = dismissDist,
                dismissVelocityPx = dismissVel,
                touchSlopPx = touchSlop
            )
        )
        assertTrue(
            MiniPlayerGestureController.circularDismissPreviewProgress(18f, 250f, dismissDist, touchSlop) >= 0.9f
        )
    }

    // =========================================================================
    // PARTS 6 & 7: PLAYBACK CONTINUITY, RESTORATION & ONLINE VIDEO ISOLATION
    // =========================================================================

    @Test
    fun offlineAndOnlinePlayback_continuesUninterruptedThroughStage1AndStage2Dismiss() {
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
            assertTrue(service.notificationManager.isForegroundNotificationActive)
            assertTrue(service.mediaSession?.isActive == true)

            var displayState = MiniPlayerDisplayState.EXPANDED_MINI_PLAYER

            // Stage 1: Minimize to circle
            displayState = MiniPlayerGestureController.onSwipeDown(
                currentState = displayState,
                circularMiniPlayerEnabled = true
            )
            assertEquals(MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER, displayState)
            // Foreground playback service, MediaSession, and queue remain singular and active
            assertSame(initialSession, service.mediaSession)
            assertTrue(service.notificationManager.isForegroundNotificationActive)
            assertEquals(1, service.currentQueueSnapshot.size)
            assertEquals(track.id, service.currentQueueSnapshot.first().id)

            // Stage 2: Dismiss completely
            displayState = MiniPlayerGestureController.onSwipeDown(
                currentState = displayState,
                circularMiniPlayerEnabled = true
            )
            assertEquals(MiniPlayerDisplayState.DISMISSED, displayState)
            assertSame(initialSession, service.mediaSession)
            assertTrue(service.notificationManager.isForegroundNotificationActive)
            assertEquals(1, service.currentQueueSnapshot.size)
            assertEquals(track.id, service.currentQueueSnapshot.first().id)

            // No empty reserved bottom space when dismissed or circular
            assertEquals(
                104.dp,
                MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                    isMusicMiniPlayerVisible = false,
                    isVideoMiniPlayerVisible = false
                )
            )
            // Persistent header restore entry available in Music mode
            assertTrue(
                MusicMiniPlayerVisibilityPolicy.shouldShowHeaderRestoreEntry(
                    hasCurrentSong = true,
                    displayState = displayState,
                    isInOnlineVideoExperience = false
                )
            )
        }

        controller.destroy()
    }

    @Test
    fun onlineVideoMode_hidesBothExpandedAndCircularPlayers_andPreservesExactStateOnReturn() {
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

        // 1. Expanded state hidden in Online Video, restored on return
        assertEquals(
            MiniPlayerVisualState.HIDDEN,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                displayState = MiniPlayerDisplayState.EXPANDED_MINI_PLAYER,
                circularMiniPlayerEnabled = true,
                isInOnlineVideoExperience = inOnlineVideo
            )
        )
        assertEquals(
            MiniPlayerVisualState.EXPANDED_BAR,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                displayState = MiniPlayerDisplayState.EXPANDED_MINI_PLAYER,
                circularMiniPlayerEnabled = true,
                isInOnlineVideoExperience = inOfflineMusic
            )
        )

        // 2. Circular state hidden in Online Video, returns to circular on Music return
        assertEquals(
            MiniPlayerVisualState.HIDDEN,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                displayState = MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER,
                circularMiniPlayerEnabled = true,
                isInOnlineVideoExperience = inOnlineVideo
            )
        )
        assertEquals(
            MiniPlayerVisualState.CIRCULAR_FLOATING,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                displayState = MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER,
                circularMiniPlayerEnabled = true,
                isInOnlineVideoExperience = inOfflineMusic
            )
        )

        // 3. Dismissed state stays hidden in Online Video (no header restore icon) AND stays dismissed on return
        assertEquals(
            MiniPlayerVisualState.HIDDEN,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                displayState = MiniPlayerDisplayState.DISMISSED,
                circularMiniPlayerEnabled = true,
                isInOnlineVideoExperience = inOnlineVideo
            )
        )
        assertFalse(
            MusicMiniPlayerVisibilityPolicy.shouldShowHeaderRestoreEntry(
                hasCurrentSong = true,
                displayState = MiniPlayerDisplayState.DISMISSED,
                isInOnlineVideoExperience = inOnlineVideo
            )
        )
        assertEquals(
            MiniPlayerVisualState.HIDDEN,
            MusicMiniPlayerVisibilityPolicy.resolveVisualState(
                hasCurrentSong = true,
                displayState = MiniPlayerDisplayState.DISMISSED,
                circularMiniPlayerEnabled = true,
                isInOnlineVideoExperience = inOfflineMusic
            )
        )
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.shouldShowHeaderRestoreEntry(
                hasCurrentSong = true,
                displayState = MiniPlayerDisplayState.DISMISSED,
                isInOnlineVideoExperience = inOfflineMusic
            )
        )
    }

    @Test
    fun miniPlayerDisplayState_persistsInDataStore_andReconcilesCircularToggleSafely() = runBlocking {
        val prefs = PlaybackLifecyclePreferences(context)

        // Default is EXPANDED_MINI_PLAYER
        assertEquals(MiniPlayerDisplayState.EXPANDED_MINI_PLAYER, prefs.miniPlayerDisplayStateFlow.first())

        // Persist CIRCULAR_MINI_PLAYER
        prefs.setMiniPlayerDisplayState(MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER)
        assertEquals(MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER, prefs.miniPlayerDisplayStateFlow.first())

        // Tap circular expands back
        assertEquals(
            MiniPlayerDisplayState.EXPANDED_MINI_PLAYER,
            MiniPlayerGestureController.onTapCircularPlayer(MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER)
        )

        // Persist DISMISSED
        prefs.setMiniPlayerDisplayState(MiniPlayerDisplayState.DISMISSED)
        assertEquals(MiniPlayerDisplayState.DISMISSED, prefs.miniPlayerDisplayStateFlow.first())

        // Disabling circular setting must NOT wipe out an intentionally DISMISSED state
        assertEquals(
            MiniPlayerDisplayState.DISMISSED,
            MiniPlayerGestureController.onCircularModeSettingChanged(
                currentState = MiniPlayerDisplayState.DISMISSED,
                circularEnabled = false
            )
        )
        // Disabling circular setting while in CIRCULAR_MINI_PLAYER expands back cleanly
        assertEquals(
            MiniPlayerDisplayState.EXPANDED_MINI_PLAYER,
            MiniPlayerGestureController.onCircularModeSettingChanged(
                currentState = MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER,
                circularEnabled = false
            )
        )

        // Reset back to EXPANDED_MINI_PLAYER
        prefs.setMiniPlayerDisplayState(MiniPlayerDisplayState.EXPANDED_MINI_PLAYER)
        assertEquals(MiniPlayerDisplayState.EXPANDED_MINI_PLAYER, prefs.miniPlayerDisplayStateFlow.first())
    }
}
