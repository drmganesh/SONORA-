package ui.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.viewmodel.SonoraViewModel
import data.preferences.HomeModePreferences
import domain.home.HomeMusicMode
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineTrack
import domain.online.OnlineHomeMixBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.FloatingMiniPlayer
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeModeSwitcherTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    @After
    fun resetPersistedHomeMode() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        HomeModePreferences(app).setHomeMusicMode(HomeMusicMode.OFFLINE)
    }

    private fun localSong(id: Long = 101L, title: String = "Local Midnight Raga"): Song = Song(
        id = id,
        title = title,
        artist = "Prateek Kuhad",
        album = "In Tokens & Charms",
        albumId = 201L,
        durationMs = 210_000L,
        uri = Uri.parse("content://media/external/audio/media/$id"),
        dataPath = "/storage/emulated/0/Music/local_$id.flac",
        mimeType = "audio/flac",
        sizeBytes = 24_000_000L,
        isOnline = false,
        sourceType = "LOCAL"
    )

    private fun onlineSong(id: Long = -901L, title: String = "Online Monsoon Stream"): Song = Song(
        id = id,
        title = title,
        artist = "Arijit Singh",
        album = "Online Stream",
        albumId = 301L,
        durationMs = 240_000L,
        uri = Uri.EMPTY,
        dataPath = "https://music.youtube.com/watch?v=online_$id",
        mimeType = "audio/mp4",
        sizeBytes = 0L,
        isOnline = true,
        sourceType = "ONLINE",
        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName = "YT Music",
        contentUrl = "https://music.youtube.com/watch?v=online_$id",
        streamCodec = "OPUS"
    )

    @Test
    fun `HomeModePreferences defaults to OFFLINE and persists ONLINE roundtrip`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val prefs = HomeModePreferences(app)

        assertEquals(HomeMusicMode.OFFLINE, prefs.getInitialHomeMusicMode())
        assertEquals(HomeMusicMode.OFFLINE, prefs.homeMusicModeFlow.first())

        prefs.setHomeMusicMode(HomeMusicMode.ONLINE)
        assertEquals(HomeMusicMode.ONLINE, prefs.getInitialHomeMusicMode())
        assertEquals(HomeMusicMode.ONLINE, prefs.homeMusicModeFlow.first())

        prefs.setHomeMusicMode(HomeMusicMode.OFFLINE)
        assertEquals(HomeMusicMode.OFFLINE, prefs.getInitialHomeMusicMode())
        assertEquals(HomeMusicMode.OFFLINE, prefs.homeMusicModeFlow.first())
    }

    @Test
    fun `SonoraViewModel mode switch preserves active playback queue and picks mode-appropriate theme song`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        val activeLocal = localSong(id = 404L, title = "Preserved Local Track")
        val queue = listOf(activeLocal, localSong(id = 405L, title = "Next Local Track"))

        vm.playCollection(queue, shuffle = false)
        assertEquals(activeLocal.id, vm.currentSong.value?.id)
        assertEquals(2, vm.queue.value.size)

        vm.setHomeMusicMode(HomeMusicMode.ONLINE)
        assertEquals(HomeMusicMode.ONLINE, vm.homeMusicMode.value)
        // Playback & queue are preserved across Home mode switches
        assertEquals(activeLocal.id, vm.currentSong.value?.id)
        assertEquals(2, vm.queue.value.size)

        // Theme song isolation: Offline picks local track; Online ignores local-only active track when no online fallback exists
        assertEquals(activeLocal.id, vm.resolveThemeSongForMode(HomeMusicMode.OFFLINE, activeLocal)?.id)
        assertNull(vm.resolveThemeSongForMode(HomeMusicMode.ONLINE, activeLocal))

        val activeOnline = onlineSong(id = -808L, title = "Active Stream")
        assertEquals(activeOnline.id, vm.resolveThemeSongForMode(HomeMusicMode.ONLINE, activeOnline)?.id)

        vm.setHomeMusicMode(HomeMusicMode.OFFLINE)
        assertEquals(HomeMusicMode.OFFLINE, vm.homeMusicMode.value)
        assertEquals(activeLocal.id, vm.currentSong.value?.id)
    }

    @Test
    fun `FloatingMiniPlayer displays LOCAL vs ONLINE source badge accurately`() {
        val colors = EditorialColors.default()
        var displayedSong by mutableStateOf(localSong())

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                FloatingMiniPlayer(
                    song = displayedSong,
                    playbackInfo = PlaybackInfo(isPlaying = true, durationMs = 200_000L, currentPositionMs = 50_000L),
                    colors = colors,
                    onTogglePlayPause = {},
                    onPlayNext = {},
                    onPlayPrevious = {},
                    onClick = {},
                    miniPlayerStyle = MiniPlayerStyle.FLOATING_PILL
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player_source_badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("LOCAL", useUnmergedTree = true).assertIsDisplayed()

        displayedSong = onlineSong()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mini_player_source_badge", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("ONLINE", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `SearchScreen isolates Offline and Online scopes and updates when Home mode changes`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        vm.setHomeMusicMode(HomeMusicMode.OFFLINE)
        val colors = EditorialColors.default()

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                SearchScreen(
                    viewModel = vm,
                    colors = colors,
                    onOpenSettings = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("search_local_filters").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("search_online_controls").fetchSemanticsNodes().isEmpty())

        // Switch active mode to ONLINE via ViewModel -> SearchScreen follows automatically
        composeRule.runOnUiThread {
            vm.setHomeMusicMode(HomeMusicMode.ONLINE)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_online_controls").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("search_local_filters").fetchSemanticsNodes().isEmpty())

        // Explicit combined scope chip allows user to view both clearly separated
        composeRule.onNodeWithTag("search_scope_combined").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("search_online_controls").assertIsDisplayed()
        composeRule.onNodeWithTag("search_local_filters").assertIsDisplayed()
        composeRule.onNodeWithTag("search_combined_online_header").assertIsDisplayed()

        composeRule.runOnUiThread {
            vm.setHomeMusicMode(HomeMusicMode.OFFLINE)
        }
    }

    @Test
    fun `OnlineHomeMixBuilder builds mixes only from real sections and avoids fabrication`() {
        val emptyBundle = OnlineHomeMixBuilder.buildOnlineYourMix(
            discoverSections = mapOf(
                DiscoverSectionType.TRENDING_MUSIC to DiscoverSectionState.Loading
            ),
            recentlyPlayedOnline = emptyList(),
            savedOnlineTracks = emptyList(),
            recommendationProfile = OnlineRecommendationProfile(
                seedQuery = "Hindi hits",
                fallbackQuery = "Hindi hits",
                subtitle = "Curated for India"
            )
        )
        assertTrue(emptyBundle.songs.isEmpty())
        assertTrue(emptyBundle.artistChips.isEmpty())

        val emptyDailyMixes = OnlineHomeMixBuilder.buildOnlineDailyMixes(
            discoverSections = emptyMap(),
            recentlyPlayedOnline = emptyList(),
            savedOnlineTracks = emptyList(),
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.HINDI
        )
        assertTrue(emptyDailyMixes.isEmpty())

        val realTrack = OnlineTrack(
            stableId = -7001L,
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = "https://music.youtube.com/watch?v=real_7001",
            title = "Apna Bana Le",
            artist = "Arijit Singh",
            durationMs = 261_000L,
            kind = OnlineItemKind.TRACK
        )
        val realSections = mapOf(
            DiscoverSectionType.TRENDING_MUSIC to DiscoverSectionState.Success(
                items = listOf(realTrack),
                sourceLabel = "YT Music"
            )
        )
        val populatedBundle = OnlineHomeMixBuilder.buildOnlineYourMix(
            discoverSections = realSections,
            recentlyPlayedOnline = emptyList(),
            savedOnlineTracks = emptyList(),
            recommendationProfile = OnlineRecommendationProfile(
                seedQuery = "Arijit Singh",
                fallbackQuery = "Hindi Bollywood",
                subtitle = "Trending in India"
            )
        )
        assertEquals(1, populatedBundle.songs.size)
        assertEquals("Apna Bana Le", populatedBundle.songs.first().title)

        val populatedMixes = OnlineHomeMixBuilder.buildOnlineDailyMixes(
            discoverSections = realSections,
            recentlyPlayedOnline = emptyList(),
            savedOnlineTracks = emptyList(),
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.HINDI
        )
        assertEquals(1, populatedMixes.size)
        assertEquals("India Charts", populatedMixes.first().title)
    }
}
