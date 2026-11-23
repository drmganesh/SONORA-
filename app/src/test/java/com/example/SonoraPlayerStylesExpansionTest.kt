package com.example

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraPreferences
import com.example.model.AnimationIntensity
import com.example.model.ArtworkAnimationOption
import com.example.model.ArtworkSizeOption
import com.example.model.ButtonShapeOption
import com.example.model.ControlArrangement
import com.example.model.ControlButtonSizeOption
import com.example.model.CornerRadiusOption
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.PlayerAppearanceConfig
import com.example.model.PlayerBackgroundStyle
import com.example.model.PlayerGestureConfig
import com.example.model.PlayerLayoutOption
import com.example.model.PlayerStyle
import com.example.model.PlayerStyleGroup
import com.example.model.PlayerTypographyOption
import com.example.model.Song
import com.example.model.VisualizerStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.FloatingMiniPlayer
import ui.player.PlayerRenderState
import ui.player.PlayerStyleCallbacks
import ui.player.PlayerStyleRenderer
import ui.player.rememberPlayerGestureController
import ui.player.seekbar.SeekbarStyle
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.LocalDynamicColorPalette
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class SonoraPlayerStylesExpansionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testSong = Song(
        id = 101L,
        title = "Velvet Horizon",
        artist = "SONORA Orchestra",
        album = "Night Sessions",
        albumId = 1L,
        durationMs = 210_000L,
        uri = Uri.EMPTY,
        dataPath = "",
        mimeType = "audio/flac",
        sizeBytes = 12_000_000L,
        bitrateKbps = 920,
        sampleRateHz = 48000,
        bitDepth = 24,
        lyrics = "[00:00.00]First line\n[00:10.00]Second active line\n[00:25.00]Final chorus"
    )

    private val testQueue = listOf(
        testSong.copy(id = 100L, title = "Intro Prelude"),
        testSong,
        testSong.copy(id = 102L, title = "Starlight Echo")
    )

    @Test
    fun testAll30CuratedPlayerStylesAnd20MiniPlayerStylesAndPersistence() {
        val curated = PlayerStyle.entries.filter { it.isFeaturedInExpandedCollection }
        assertEquals(30, curated.size)
        assertEquals(8, curated.count { it.group == PlayerStyleGroup.MODERN_PREMIUM })
        assertEquals(8, curated.count { it.group == PlayerStyleGroup.ARTWORK_FOCUSED })
        assertEquals(8, curated.count { it.group == PlayerStyleGroup.IMMERSIVE_ANIMATED })
        assertEquals(6, curated.count { it.group == PlayerStyleGroup.COMPACT_FUNCTIONAL })

        // Verify all 20 Mini Player styles and ID round-trip
        assertEquals(20, MiniPlayerStyle.entries.size)
        MiniPlayerStyle.entries.forEach { mini ->
            assertEquals(mini, MiniPlayerStyle.fromId(mini.id))
        }
        assertEquals(MiniPlayerStyle.COMPACT_BAR, MiniPlayerStyle.fromId("classic_bar"))

        // Verify SonoraPreferences persistence for separate full, landscape, mini, favorites, and appearance config
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = SonoraPreferences(app)
        prefs.playerStyle = PlayerStyle.CASSETTE
        prefs.landscapePlayerStyle = PlayerStyle.SPLIT_LAYOUT
        prefs.miniPlayerStyle = MiniPlayerStyle.EXPANDABLE_CARD
        prefs.favoritePlayerStyleIds = setOf(PlayerStyle.AURORA.id, PlayerStyle.CD_PLAYER.id)
        prefs.favoriteMiniPlayerStyleIds = setOf(MiniPlayerStyle.VINYL_MINI.id)

        val customAppearance = PlayerAppearanceConfig(
            artworkSize = ArtworkSizeOption.LARGE,
            cornerRadius = CornerRadiusOption.PILL,
            backgroundStyle = PlayerBackgroundStyle.BLURRED_ARTWORK,
            blurIntensity = 0.82f,
            gradientIntensity = 0.74f,
            dynamicColorIntensity = 0.91f,
            controlArrangement = ControlArrangement.SPLIT_WIDE,
            buttonShape = ButtonShapeOption.SQUIRCLE,
            buttonSize = ControlButtonSizeOption.LARGE,
            typographyOption = PlayerTypographyOption.EDITORIAL_SERIF,
            typographyScale = 1.12f,
            seekbarThicknessScale = 1.25f,
            useDynamicAlbumAccent = true,
            animationIntensity = AnimationIntensity.BALANCED,
            artworkAnimation = ArtworkAnimationOption.VINYL_SPIN,
            layoutOption = PlayerLayoutOption.LYRICS_FIRST
        )
        prefs.playerAppearanceConfig = customAppearance

        assertEquals(PlayerStyle.CASSETTE, prefs.playerStyle)
        assertEquals(PlayerStyle.SPLIT_LAYOUT, prefs.landscapePlayerStyle)
        assertEquals(MiniPlayerStyle.EXPANDABLE_CARD, prefs.miniPlayerStyle)
        assertEquals(setOf("aurora", "cd_player"), prefs.favoritePlayerStyleIds)
        assertEquals(setOf("vinyl_mini"), prefs.favoriteMiniPlayerStyleIds)

        val restored = prefs.playerAppearanceConfig
        assertEquals(customAppearance.artworkSize, restored.artworkSize)
        assertEquals(customAppearance.buttonSize, restored.buttonSize)
        assertEquals(customAppearance.typographyOption, restored.typographyOption)
        assertEquals(customAppearance.layoutOption, restored.layoutOption)
        assertEquals(customAppearance.animationIntensity, restored.animationIntensity)
        assertEquals(customAppearance.blurIntensity, restored.blurIntensity, 0.01f)
        assertEquals(customAppearance.gradientIntensity, restored.gradientIntensity, 0.01f)
        assertEquals(customAppearance.dynamicColorIntensity, restored.dynamicColorIntensity, 0.01f)
    }

    @Test
    fun testDistinctNewNowPlayingStylesRenderAndWirePlaybackCallbacks() {
        var playPauseClicks = 0
        var nextClicks = 0
        val sampleStyles = listOf(
            PlayerStyle.AMOLED_MINIMAL to "player_style_amoled_minimal",
            PlayerStyle.CASSETTE to "player_style_cassette",
            PlayerStyle.CD_PLAYER to "player_style_cd_player",
            PlayerStyle.RETRO_WALKMAN to "player_style_retro_walkman",
            PlayerStyle.AURORA to "player_style_aurora",
            PlayerStyle.SPATIAL_3D to "player_style_spatial_3d",
            PlayerStyle.TYPOGRAPHY_PLAYER to "player_style_typography_player",
            PlayerStyle.LANDSCAPE_PLAYER to "player_style_landscape_player",
            PlayerStyle.LYRICS_FOCUS to "player_style_lyrics_focus"
        )

        val activeStyleState = androidx.compose.runtime.mutableStateOf(sampleStyles.first().first)

        val renderState = PlayerRenderState(
            song = testSong,
            playbackInfo = PlaybackInfo(isPlaying = false, currentPositionMs = 12_000L, durationMs = testSong.durationMs),
            seekbarStyle = SeekbarStyle.BLOCKS,
            appearance = PlayerAppearanceConfig.DEFAULT.copy(animationIntensity = AnimationIntensity.OFF),
            gestureConfig = PlayerGestureConfig.DEFAULT,
            dynamicPalette = DynamicColorPalette.default(),
            frequencies = FloatArray(16) { 0.4f },
            waveformSamples = FloatArray(16) { 0.5f },
            isVisualizerActive = false,
            visualizerStyle = VisualizerStyle.SPECTRUM_BARS,
            isNowPlayingActive = true,
            queue = testQueue,
            queueIndex = 1,
            rawLyrics = testSong.lyrics
        )
        val callbacks = PlayerStyleCallbacks(
            onTogglePlayPause = { playPauseClicks++ },
            onPlayNext = { nextClicks++ },
            onPlayPrevious = {},
            onSeekTo = {},
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
        )

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalDynamicColorPalette provides DynamicColorPalette.default(),
                LocalReduceAnimations provides true
            ) {
                PlayerStyleRenderer(
                    style = activeStyleState.value,
                    state = renderState,
                    callbacks = callbacks,
                    gestureController = rememberPlayerGestureController(),
                    respectLayoutOverride = false
                )
            }
        }

        sampleStyles.forEachIndexed { idx, (style, expectedTag) ->
            activeStyleState.value = style
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(expectedTag).assertIsDisplayed()
            composeRule.onNodeWithTag("play_pause_button").performClick()
            composeRule.onNodeWithTag("next_button").performClick()
            assertEquals("play_pause click failed for $style", idx + 1, playPauseClicks)
            assertEquals("next click failed for $style", idx + 1, nextClicks)
        }

        assertEquals(sampleStyles.size, playPauseClicks)
        assertEquals(sampleStyles.size, nextClicks)
    }

    @Test
    fun testDistinctNewMiniPlayerStylesRenderAndControlPlayback() {
        var playToggles = 0
        var nextSkips = 0
        val miniStyles = listOf(
            MiniPlayerStyle.GLASS_BAR to "mini_style_content_glass_bar",
            MiniPlayerStyle.AMOLED_BAR to "mini_style_content_amoled_bar",
            MiniPlayerStyle.VINYL_MINI to "mini_style_content_vinyl_mini",
            MiniPlayerStyle.CASSETTE_MINI to "mini_style_content_cassette_mini",
            MiniPlayerStyle.CD_MINI to "mini_style_content_cd_mini",
            MiniPlayerStyle.SPECTRUM_MINI to "mini_style_content_spectrum_mini",
            MiniPlayerStyle.CIRCULAR_PLAYER to "mini_style_content_circular_player",
            MiniPlayerStyle.EXPANDABLE_CARD to "mini_style_content_expandable_card",
            MiniPlayerStyle.EDGE_TO_EDGE_MINI to "mini_style_content_edge_to_edge_mini",
            MiniPlayerStyle.FLOATING_CORNER_PLAYER to "mini_style_content_floating_corner_player"
        )
        val activeMiniState = androidx.compose.runtime.mutableStateOf(miniStyles.first().first)

        composeRule.setContent {
            CompositionLocalProvider(
                LocalEditorialColors provides EditorialColors.default(),
                LocalDynamicColorPalette provides DynamicColorPalette.default(),
                LocalReduceAnimations provides true
            ) {
                FloatingMiniPlayer(
                    song = testSong,
                    playbackInfo = PlaybackInfo(isPlaying = false, currentPositionMs = 45_000L, durationMs = testSong.durationMs),
                    colors = EditorialColors.default(),
                    onTogglePlayPause = { playToggles++ },
                    onPlayNext = { nextSkips++ },
                    onPlayPrevious = {},
                    onClick = {},
                    miniPlayerStyle = activeMiniState.value,
                    frequencies = FloatArray(16) { 0.35f },
                    queue = testQueue,
                    queueIndex = 1
                )
            }
        }

        miniStyles.forEachIndexed { idx, (miniStyle, contentTag) ->
            activeMiniState.value = miniStyle
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(contentTag, useUnmergedTree = true).assertIsDisplayed()
            composeRule.onNodeWithTag("mini_play_pause_button").performClick()
            composeRule.onNodeWithTag("mini_skip_next_button").performClick()
            assertEquals("mini play toggle failed for $miniStyle", idx + 1, playToggles)
            assertEquals("mini next skip failed for $miniStyle", idx + 1, nextSkips)
            if (miniStyle == MiniPlayerStyle.EXPANDABLE_CARD) {
                composeRule.onNodeWithTag("mini_expand_toggle_button").performClick()
                composeRule.waitForIdle()
            }
        }

        assertEquals(miniStyles.size, playToggles)
        assertEquals(miniStyles.size, nextSkips)
        assertTrue(playToggles == 10)
    }

    @Test
    fun testAll40PlayerStylesIncludingLegacyRemainPreservedAndPersistWithoutReset() {
        assertEquals(40, PlayerStyle.entries.size)
        PlayerStyle.entries.forEach { style ->
            assertEquals(style, PlayerStyle.fromId(style.id))
        }
        val legacyStyles = PlayerStyle.entries.filter { !it.isFeaturedInExpandedCollection }
        assertEquals(10, legacyStyles.size)

        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = SonoraPreferences(app)
        prefs.playerStyle = PlayerStyle.BENTO
        prefs.landscapePlayerStyle = PlayerStyle.ORBIT
        prefs.miniPlayerStyle = MiniPlayerStyle.EXPANDABLE_CARD

        val reloaded = SonoraPreferences(app)
        assertEquals(PlayerStyle.BENTO, reloaded.playerStyle)
        assertEquals(PlayerStyle.ORBIT, reloaded.landscapePlayerStyle)
        assertEquals(MiniPlayerStyle.EXPANDABLE_CARD, reloaded.miniPlayerStyle)
    }
}

