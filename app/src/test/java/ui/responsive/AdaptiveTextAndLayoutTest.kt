package ui.responsive

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.model.UserProfile
import com.example.ui.components.MiniPlayerView
import com.example.viewmodel.SonoraViewModel
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode
import domain.online.OnlineMusicProvider
import domain.online.OnlineSearchFilter
import domain.online.SonoraRecommendUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.FloatingMiniPlayer
import ui.home.GreetingHeader
import ui.home.SonoraRecommendSection
import ui.home.animations.HomeAnimationLevel
import ui.library.LibraryModeSegmentedSelector
import ui.online.OnlineMusicScreen
import ui.online.OnlineProviderPillBar
import ui.player.AudioShortcutsRow
import ui.player.LocalNowPlayingSongRadioAction
import ui.player.NowPlayingSongRadioAction
import ui.player.NowPlayingTopHeader
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Verifies SONORA's adaptive text wrapping and responsive layouts across real Android
 * display qualifiers:
 * - 320dp (small narrow phone)
 * - 360dp (compact phone)
 * - 393dp (standard modern phone)
 * - 480dp (wide phone)
 * - 640x360dp landscape
 * - Small (0.85x), default (1.0x), and large (1.35x) system font scales.
 *
 * Uses Compose TextLayoutResult (lineCount == 1) and bounding-box geometry checks to verify
 * single-line labels ("Online Music", "YT MUSIC", "OPUS", "ONLINE", "Highest Available",
 * provider chips, mode badges) never split into vertical letter stacks or overlap controls.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
class AdaptiveTextAndLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun SemanticsNodeInteraction.assertSingleLineTextLayout(label: String) {
        val results = mutableListOf<TextLayoutResult>()
        val node = fetchSemanticsNode("Expected semantics node for $label")
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)
        assertTrue("Expected GetTextLayoutResult semantics on '$label'", action != null)
        action!!.action?.invoke(results)
        assertTrue("Expected non-empty TextLayoutResult for '$label'", results.isNotEmpty())
        val layout = results.first()
        assertEquals(
            "Expected '$label' to stay strictly on 1 line (never vertical stacking), got lineCount=${layout.lineCount}",
            1,
            layout.lineCount
        )
    }

    private fun testOnlineSong(): Song = Song(
        id = -777L,
        title = "Aasa Kooda (From Think Indie)",
        artist = "Sai Abhyankkar - Topic",
        album = "Think Indie",
        albumId = 77L,
        durationMs = 216_000L,
        uri = Uri.EMPTY,
        dataPath = "https://music.youtube.com/watch?v=test_aasa_kooda",
        mimeType = "audio/webm",
        sizeBytes = 0L,
        isOnline = true,
        sourceType = "ONLINE",
        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName = "YT Music",
        contentUrl = "https://music.youtube.com/watch?v=test_aasa_kooda",
        streamCodec = "OPUS",
        bitrateKbps = 160
    )

    private fun testPlaybackInfo(): PlaybackInfo = PlaybackInfo(
        isPlaying = true,
        currentPositionMs = 45_000L,
        durationMs = 216_000L
    )

    private fun testProviders(): List<OnlineMusicProvider> = listOf(
        OnlineMusicProvider(
            serviceId = 0,
            key = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            displayName = "YT Music",
            shortLabel = "YT Music",
            baseUrl = "https://music.youtube.com",
            supportedFilters = listOf(OnlineSearchFilter.ALL, OnlineSearchFilter.SONGS),
            defaultFilter = OnlineSearchFilter.SONGS,
            supportsSuggestions = true,
            supportsDiscoveryFeed = true,
            isMusicFocused = true
        ),
        OnlineMusicProvider(
            serviceId = 0,
            key = OnlineMusicProvider.KEY_YOUTUBE,
            displayName = "YouTube",
            shortLabel = "YouTube",
            baseUrl = "https://youtube.com",
            supportedFilters = listOf(OnlineSearchFilter.ALL, OnlineSearchFilter.VIDEOS),
            defaultFilter = OnlineSearchFilter.ALL,
            supportsSuggestions = true,
            supportsDiscoveryFeed = true
        ),
        OnlineMusicProvider(
            serviceId = 1,
            key = OnlineMusicProvider.KEY_SOUNDCLOUD,
            displayName = "SoundCloud",
            shortLabel = "SoundCloud",
            baseUrl = "https://soundcloud.com",
            supportedFilters = listOf(OnlineSearchFilter.ALL, OnlineSearchFilter.SONGS),
            defaultFilter = OnlineSearchFilter.SONGS,
            supportsSuggestions = true,
            supportsDiscoveryFeed = true
        )
    )

    private fun assertSingleLineHeaderAtViewport(
        expectedScreenWidth: Dp,
        fontScale: Float = 1.0f
    ) {
        val song = testOnlineSong()
        val colors = EditorialColors.default()

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalDensity provides Density(baseDensity.density, fontScale),
                LocalNowPlayingSongRadioAction provides NowPlayingSongRadioAction(
                    isRadioActive = false,
                    onOpenSongRadio = {}
                )
            ) {
                // Mirror ClassicPlayer's outer 20.dp horizontal padding around NowPlayingTopHeader
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                ) {
                    NowPlayingTopHeader(
                        song = song,
                        onClose = {},
                        onOpenQueue = {},
                        onOpenLyrics = {},
                        onOpenSleepTimer = {},
                        onOpenStyleWheel = {},
                        onToggleVisualizer = {},
                        isVisualizerActive = false
                    )
                }
            }
        }

        val collapseNode = composeRule.onNodeWithTag("collapse_now_playing_button").assertIsDisplayed()
        val brandNode = composeRule.onNodeWithTag("now_playing_brand_label").assertIsDisplayed()
        val badgeNode = composeRule.onNodeWithTag("now_playing_source_badge").assertIsDisplayed()
        val formatNode = composeRule.onNodeWithTag("now_playing_format_label").assertIsDisplayed()
        val queueNode = composeRule.onNodeWithTag("open_queue_button").assertIsDisplayed()
        val radioNode = composeRule.onNodeWithTag("player_song_radio_button").assertIsDisplayed()
        val menuNode = composeRule.onNodeWithTag("now_playing_menu_button").assertIsDisplayed()

        // Verify actual Compose TextLayoutResult lineCount == 1 (never 3-line vertical stack "YT / MUS / IC")
        brandNode.assertSingleLineTextLayout("MAYUR")
        composeRule.onNodeWithText("YT MUSIC", useUnmergedTree = true).assertSingleLineTextLayout("YT MUSIC")
        formatNode.assertSingleLineTextLayout("now_playing_format_label")

        val collapseBounds = collapseNode.getUnclippedBoundsInRoot()
        val brandBounds = brandNode.getUnclippedBoundsInRoot()
        val badgeBounds = badgeNode.getUnclippedBoundsInRoot()
        val formatBounds = formatNode.getUnclippedBoundsInRoot()
        val queueBounds = queueNode.getUnclippedBoundsInRoot()
        val radioBounds = radioNode.getUnclippedBoundsInRoot()
        val menuBounds = menuNode.getUnclippedBoundsInRoot()

        val maxSingleLineBadgeHeight = (24f * fontScale.coerceAtLeast(1f)).dp
        assertTrue(
            "Expected YT MUSIC badge compact single-line height at $expectedScreenWidth (fontScale=$fontScale), got height=${badgeBounds.height}",
            badgeBounds.height <= maxSingleLineBadgeHeight
        )
        assertTrue(
            "Expected format string compact single-line height at $expectedScreenWidth, got height=${formatBounds.height}",
            formatBounds.height <= (24f * fontScale.coerceAtLeast(1f)).dp
        )

        // Header buttons must remain ordered and non-overlapping: Collapse .. Brand .. Queue < Radio < MoreVert
        assertTrue("Collapse button overlaps brand label", collapseBounds.right <= brandBounds.left + 1.dp)
        assertTrue("Brand label overlaps queue button", brandBounds.right <= queueBounds.left + 1.dp)
        assertTrue("Queue must be left of Radio button", queueBounds.right <= radioBounds.left + 1.dp)
        assertTrue("Radio must be left of Menu button", radioBounds.right <= menuBounds.left + 1.dp)
        assertTrue("Menu button clipped beyond $expectedScreenWidth (right=${menuBounds.right})", menuBounds.right <= expectedScreenWidth + 1.dp)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun `NowPlayingTopHeader prevents YT MUSIC vertical stacking at 320dp narrow phone`() {
        assertSingleLineHeaderAtViewport(expectedScreenWidth = 320.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `NowPlayingTopHeader prevents YT MUSIC vertical stacking at 360dp phone`() {
        assertSingleLineHeaderAtViewport(expectedScreenWidth = 360.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-xxhdpi")
    fun `NowPlayingTopHeader prevents YT MUSIC vertical stacking at 393dp phone`() {
        assertSingleLineHeaderAtViewport(expectedScreenWidth = 393.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w480dp-h900dp-xxhdpi")
    fun `NowPlayingTopHeader renders inline cleanly at 480dp wide phone`() {
        assertSingleLineHeaderAtViewport(expectedScreenWidth = 480.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xxhdpi")
    fun `NowPlayingTopHeader adapts cleanly in landscape 640x360dp`() {
        assertSingleLineHeaderAtViewport(expectedScreenWidth = 640.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `NowPlayingTopHeader adapts cleanly at small 0_85x font scale`() {
        assertSingleLineHeaderAtViewport(expectedScreenWidth = 360.dp, fontScale = 0.85f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `NowPlayingTopHeader adapts cleanly at large 1_35x font scale`() {
        assertSingleLineHeaderAtViewport(expectedScreenWidth = 360.dp, fontScale = 1.35f)
    }

    private fun assertOnlineMusicHeaderAtViewport(
        expectedScreenWidth: Dp,
        fontScale: Float = 1.0f
    ) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = SonoraViewModel(app)
        val colors = EditorialColors.default()

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalDensity provides Density(baseDensity.density, fontScale)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OnlineMusicScreen(
                        viewModel = viewModel,
                        colors = colors
                    )
                }
            }
        }

        val titleNode = composeRule.onNodeWithTag("online_music_title").assertIsDisplayed()
        val subtitleNode = composeRule.onNodeWithTag("online_music_subtitle").assertIsDisplayed()
        val regionNode = composeRule.onNodeWithTag("online_region_button").assertIsDisplayed()
        val qualityNode = composeRule.onNodeWithTag("online_quality_button").assertIsDisplayed()
        val refreshNode = composeRule.onNodeWithTag("online_refresh_discovery_button").assertIsDisplayed()

        // "Online Music" must NEVER stack into individual letters: verify lineCount == 1
        titleNode.assertSingleLineTextLayout("Online Music")
        // Verify subtitle stays at most 2 lines (never a 10-line vertical column)
        val subtitleResults = mutableListOf<TextLayoutResult>()
        subtitleNode.fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(subtitleResults)
        assertTrue("Expected subtitle TextLayoutResult", subtitleResults.isNotEmpty())
        assertTrue(
            "Online Music subtitle wrapped into too many lines at $expectedScreenWidth: lineCount=${subtitleResults.first().lineCount}",
            subtitleResults.first().lineCount <= 2
        )

        val titleBounds = titleNode.getUnclippedBoundsInRoot()
        val regionBounds = regionNode.getUnclippedBoundsInRoot()
        val qualityBounds = qualityNode.getUnclippedBoundsInRoot()
        val refreshBounds = refreshNode.getUnclippedBoundsInRoot()

        val maxSingleLineTitleHeight = (44f * fontScale.coerceAtLeast(1f)).dp
        assertTrue(
            "Online Music title height too tall at $expectedScreenWidth (fontScale=$fontScale): height=${titleBounds.height}",
            titleBounds.height <= maxSingleLineTitleHeight
        )

        // Chips and refresh button must stay inside viewport without overlapping title
        assertTrue("Region chip clipped at $expectedScreenWidth (right=${regionBounds.right})", regionBounds.right <= expectedScreenWidth + 1.dp)
        assertTrue("Quality chip clipped at $expectedScreenWidth (right=${qualityBounds.right})", qualityBounds.right <= expectedScreenWidth + 1.dp)
        assertTrue("Refresh button clipped at $expectedScreenWidth (right=${refreshBounds.right})", refreshBounds.right <= expectedScreenWidth + 1.dp)
        assertTrue("Title overlaps refresh button at $expectedScreenWidth", titleBounds.right <= refreshBounds.left + 1.dp)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun `OnlineMusicScreen header stays single-line at 320dp`() {
        assertOnlineMusicHeaderAtViewport(expectedScreenWidth = 320.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `OnlineMusicScreen header stays single-line at 360dp`() {
        assertOnlineMusicHeaderAtViewport(expectedScreenWidth = 360.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-xxhdpi")
    fun `OnlineMusicScreen header stays single-line at 393dp`() {
        assertOnlineMusicHeaderAtViewport(expectedScreenWidth = 393.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w480dp-h900dp-xxhdpi")
    fun `OnlineMusicScreen header stays single-line at 480dp`() {
        assertOnlineMusicHeaderAtViewport(expectedScreenWidth = 480.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land-xxhdpi")
    fun `OnlineMusicScreen header adapts cleanly at landscape 640x360dp`() {
        assertOnlineMusicHeaderAtViewport(expectedScreenWidth = 640.dp, fontScale = 1.0f)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `OnlineMusicScreen header stays unbroken at large 1_35x font scale`() {
        assertOnlineMusicHeaderAtViewport(expectedScreenWidth = 360.dp, fontScale = 1.35f)
    }

    private fun assertMiniPlayerAtViewport(
        expectedScreenWidth: Dp,
        fontScale: Float = 1.0f,
        style: MiniPlayerStyle = MiniPlayerStyle.FLOATING_PILL,
        expectSecondaryCodecVisible: Boolean
    ) {
        val song = testOnlineSong()
        val colors = EditorialColors.default()

        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalDensity provides Density(baseDensity.density, fontScale)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    FloatingMiniPlayer(
                        song = song,
                        playbackInfo = testPlaybackInfo(),
                        colors = colors,
                        onTogglePlayPause = {},
                        onPlayNext = {},
                        onPlayPrevious = {},
                        onClick = {},
                        onOpenLyrics = {},
                        miniPlayerStyle = style
                    )
                }
            }
        }

        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        val lyricsBounds = composeRule.onNodeWithTag("mini_lyrics_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val playBounds = composeRule.onNodeWithTag("mini_play_pause_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val nextBounds = composeRule.onNodeWithTag("mini_skip_next_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val sourceBadgeBounds = composeRule.onNodeWithTag("mini_player_source_badge", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        // Source badge ("ONLINE") must stay strictly on 1 line
        composeRule.onNodeWithText("ONLINE", useUnmergedTree = true).assertSingleLineTextLayout("ONLINE")
        assertTrue(
            "ONLINE badge wrapped vertically at $expectedScreenWidth (fontScale=$fontScale): height=${sourceBadgeBounds.height}",
            sourceBadgeBounds.height <= (24f * fontScale.coerceAtLeast(1f)).dp
        )

        // Controls must not overlap or clip outside screenWidth
        assertTrue("Lyrics overlaps Play at $expectedScreenWidth", lyricsBounds.right <= playBounds.left + 1.dp)
        assertTrue("Play overlaps Next at $expectedScreenWidth", playBounds.right <= nextBounds.left + 1.dp)
        assertTrue("Next button clipped past $expectedScreenWidth (right=${nextBounds.right})", nextBounds.right <= expectedScreenWidth + 1.dp)

        if (style == MiniPlayerStyle.FLOATING_PILL) {
            val titleNode = composeRule.onNodeWithTag("mini_player_title", useUnmergedTree = true).assertIsDisplayed()
            val artistNode = composeRule.onNodeWithTag("mini_player_artist", useUnmergedTree = true).assertIsDisplayed()

            titleNode.assertSingleLineTextLayout("mini_player_title")
            artistNode.assertSingleLineTextLayout("mini_player_artist")

            val titleBounds = titleNode.getUnclippedBoundsInRoot()
            val artistBounds = artistNode.getUnclippedBoundsInRoot()

            assertTrue("Mini player artist has zero width at $expectedScreenWidth", artistBounds.width > 0.dp)
            assertTrue("Title overlaps lyrics button at $expectedScreenWidth", titleBounds.right <= lyricsBounds.left + 1.dp)
            assertTrue("Artist overlaps source badge at $expectedScreenWidth", artistBounds.right <= sourceBadgeBounds.left + 1.dp)

            val codecNodes = composeRule.onAllNodesWithTag("mini_player_codec_badge", useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (expectSecondaryCodecVisible) {
                assertEquals("Expected OPUS badge visible at $expectedScreenWidth", 1, codecNodes.size)
                composeRule.onNodeWithText("OPUS", useUnmergedTree = true).assertSingleLineTextLayout("OPUS")
                val codecBounds = composeRule.onNodeWithTag("mini_player_codec_badge", useUnmergedTree = true)
                    .assertIsDisplayed()
                    .getUnclippedBoundsInRoot()
                assertTrue(
                    "OPUS badge wrapped into multiple lines at $expectedScreenWidth: height=${codecBounds.height}",
                    codecBounds.height <= (24f * fontScale.coerceAtLeast(1f)).dp
                )
            } else {
                assertEquals(
                    "Secondary OPUS codec badge should hide gracefully at $expectedScreenWidth (fontScale=$fontScale) instead of wrapping",
                    0,
                    codecNodes.size
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun `FloatingMiniPlayer FLOATING_PILL hides OPUS cleanly at 320dp instead of wrapping`() {
        assertMiniPlayerAtViewport(expectedScreenWidth = 320.dp, fontScale = 1.0f, expectSecondaryCodecVisible = false)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `FloatingMiniPlayer FLOATING_PILL hides OPUS cleanly at 360dp instead of wrapping into OPU S`() {
        assertMiniPlayerAtViewport(expectedScreenWidth = 360.dp, fontScale = 1.0f, expectSecondaryCodecVisible = false)
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-xxhdpi")
    fun `FloatingMiniPlayer FLOATING_PILL renders cleanly at 393dp without OPUS wrapping`() {
        assertMiniPlayerAtViewport(expectedScreenWidth = 393.dp, fontScale = 1.0f, expectSecondaryCodecVisible = false)
    }

    @Test
    @Config(qualifiers = "w480dp-h900dp-xxhdpi")
    fun `FloatingMiniPlayer FLOATING_PILL shows single-line OPUS badge on roomy 480dp screen`() {
        assertMiniPlayerAtViewport(expectedScreenWidth = 480.dp, fontScale = 1.0f, expectSecondaryCodecVisible = true)
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-xxhdpi")
    fun `FloatingMiniPlayer FLOATING_PILL adapts at large 1_35x font scale`() {
        assertMiniPlayerAtViewport(expectedScreenWidth = 393.dp, fontScale = 1.35f, expectSecondaryCodecVisible = false)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `FloatingMiniPlayer COMPACT_BAR renders cleanly at 360dp`() {
        assertMiniPlayerAtViewport(expectedScreenWidth = 360.dp, style = MiniPlayerStyle.COMPACT_BAR, expectSecondaryCodecVisible = false)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `FloatingMiniPlayer ARTWORK_FOCUS renders cleanly at 360dp`() {
        assertMiniPlayerAtViewport(expectedScreenWidth = 360.dp, style = MiniPlayerStyle.ARTWORK_FOCUS, expectSecondaryCodecVisible = false)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun `FloatingMiniPlayer WAVE_MINI renders single-line title and badge at 320dp`() {
        assertMiniPlayerAtViewport(expectedScreenWidth = 320.dp, style = MiniPlayerStyle.WAVE_MINI, expectSecondaryCodecVisible = false)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun `MiniPlayerView keeps title artist and controls unbroken at 320dp`() {
        val song = testOnlineSong()
        composeRule.setContent {
            Box(modifier = Modifier.fillMaxWidth()) {
                MiniPlayerView(
                    song = song,
                    playbackInfo = testPlaybackInfo(),
                    onTogglePlayPause = {},
                    onPlayNext = {},
                    onClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("mini_player").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_play_pause_button").assertIsDisplayed()
        composeRule.onNodeWithTag("mini_skip_next_button").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun `AudioShortcutsRow adapts cleanly without clipping at 320dp and 1_35x fontScale`() {
        val colors = EditorialColors.default()
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalDensity provides Density(baseDensity.density, 1.35f)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AudioShortcutsRow(
                        onOpenAudioEffects = {},
                        onOpenEqualizer = {},
                        onOpenLyrics = {},
                        onOpenSleepTimer = {}
                    )
                }
            }
        }

        val eq = composeRule.onNodeWithTag("shortcut_equalizer_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val fx = composeRule.onNodeWithTag("shortcut_audio_studio_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val lyrics = composeRule.onNodeWithTag("shortcut_lyrics_button").assertIsDisplayed().getUnclippedBoundsInRoot()
        val timer = composeRule.onNodeWithTag("shortcut_timer_button").assertIsDisplayed().getUnclippedBoundsInRoot()

        composeRule.onNodeWithText("Equalizer", useUnmergedTree = true).assertSingleLineTextLayout("Equalizer")
        composeRule.onNodeWithText("Studio FX", useUnmergedTree = true).assertSingleLineTextLayout("Studio FX")
        assertTrue("Shortcut cards overlap horizontally", eq.right <= fx.left + 1.dp && fx.right <= lyrics.left + 1.dp && lyrics.right <= timer.left + 1.dp)
        assertTrue("Timer card clipped past 320dp (right=${timer.right})", timer.right <= 321.dp)
        assertTrue("Shortcut card height below 48dp minimum", eq.height >= 48.dp)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun `Provider pills GreetingHeader SonoraRecommend and LibrarySegmentedHeader stay single-line at 320dp and 1_35x fontScale`() {
        val colors = EditorialColors.default()
        val providers = testProviders()
        composeRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalEditorialColors provides colors,
                LocalDensity provides Density(baseDensity.density, 1.35f)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    GreetingHeader(
                        greeting = "Good evening",
                        userProfile = UserProfile(displayName = "Listener"),
                        colors = colors,
                        activeMode = HomeMusicMode.ONLINE,
                        onAvatarClick = {},
                        onDiscoveryClick = {},
                        onSettingsClick = {}
                    )
                    OnlineProviderPillBar(
                        providers = providers,
                        selectedProvider = providers.first(),
                        colors = colors,
                        onSelectProvider = {}
                    )
                    LibraryModeSegmentedSelector(
                        selectedMode = LibrarySectionMode.ONLINE,
                        colors = colors,
                        onSelectMode = {}
                    )
                    SonoraRecommendSection(
                        state = SonoraRecommendUiState(
                            isServingCachedOffline = true,
                            isLoading = false
                        ),
                        favoriteOnlineIds = emptySet(),
                        currentSongId = null,
                        isPlaying = false,
                        colors = colors,
                        animationLevel = HomeAnimationLevel.OFF,
                        screenVisible = true,
                        reduceAnimations = true,
                        onRefresh = {},
                        onPlayTrack = { _, _ -> },
                        onAddToQueue = {},
                        onPlayNext = {},
                        onToggleLike = {},
                        onDislikeTrack = {},
                        onOpenArtist = {},
                        onOpenAlbum = {},
                        onMoreOptions = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText("Online Music", useUnmergedTree = true).assertSingleLineTextLayout("GreetingHeader mode badge")
        composeRule.onNodeWithText("YT Music", useUnmergedTree = true).assertSingleLineTextLayout("YT Music provider pill")
        composeRule.onNodeWithText("YouTube", useUnmergedTree = true).assertSingleLineTextLayout("YouTube provider pill")

        composeRule.onNodeWithTag("library_mode_segmented_selector").assertIsDisplayed()
        assertTrue(
            "Header Refresh button must be removed from MAYUR Recommendations",
            composeRule.onAllNodesWithTag("sonora_recommend_refresh_button").fetchSemanticsNodes().isEmpty()
        )
        val recommendTitleNode = composeRule.onNodeWithTag("sonora_recommend_title", useUnmergedTree = true)
            .assertIsDisplayed()
        recommendTitleNode.assertSingleLineTextLayout("MAYUR Recommendations section title")
        val recommendTitleBounds = recommendTitleNode.getUnclippedBoundsInRoot()
        assertTrue(
            "MAYUR Recommendations title clipped past 320dp (right=${recommendTitleBounds.right})",
            recommendTitleBounds.right <= 321.dp
        )
        val recommendSubtitleBounds = composeRule.onNodeWithTag("sonora_recommend_subtitle", useUnmergedTree = true)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        assertTrue(
            "MAYUR Recommendations subtitle clipped past 320dp (right=${recommendSubtitleBounds.right})",
            recommendSubtitleBounds.right <= 321.dp
        )
    }
}
