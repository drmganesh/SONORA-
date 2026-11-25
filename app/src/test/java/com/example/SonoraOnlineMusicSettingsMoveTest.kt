package com.example

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraPreferences
import com.example.model.Song
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.SonoraViewModel
import data.preferences.HomeModePreferences
import data.preferences.OnlineRegionPreferences
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineRegionCatalog
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.RecommendedSongItem
import domain.online.SonoraRecommendUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.home.HomeScreen
import ui.home.OnlineHomeContent
import ui.home.animations.DailyMixAnimationStyle
import ui.home.animations.HomeAnimationLevel
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * End-to-end regression suite verifying the move of Online Music Discovery controls
 * from Home into Settings → Online Music:
 *  1. Online Music panel no longer appears on Home.
 *  2. No empty space remains where the panel was.
 *  3. Online Music settings appear in Settings.
 *  4. Country and language selections work independently.
 *  5. Provider selection works and persists across restarts.
 *  6. Refresh triggers a real discovery refresh.
 *  7. Existing IN • EN selection is preserved.
 *  8. Sonora Recommend remains visible and functional.
 *  9. Trending Music India remains visible.
 * 10. Offline Music and Online Video modes remain unchanged.
 * 11. Music playback and queue continue working.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraOnlineMusicSettingsMoveTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    private val ytMusicProvider = OnlineMusicProvider(
        serviceId = 0,
        key = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        displayName = "YT Music",
        shortLabel = "YT Music",
        baseUrl = "https://music.youtube.com",
        supportedFilters = listOf(OnlineSearchFilter.SONGS, OnlineSearchFilter.ARTISTS),
        defaultFilter = OnlineSearchFilter.SONGS,
        supportsSuggestions = true,
        supportsDiscoveryFeed = true,
        isMusicFocused = true
    )

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        val regionPrefs = OnlineRegionPreferences(app)
        regionPrefs.setContentCountry(OnlineRegionCatalog.DEFAULT_COUNTRY_CODE)
        regionPrefs.setContentLanguage(OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE)
        regionPrefs.setDiscoverLanguage(DiscoverMusicLanguage.ALL)
        regionPrefs.setSelectedProviderKey(OnlineMusicProvider.KEY_YOUTUBE_MUSIC)
        HomeModePreferences(app).setHomeMusicMode(HomeMusicMode.OFFLINE)
    }

    @After
    fun tearDown() = runBlocking {
        val regionPrefs = OnlineRegionPreferences(app)
        regionPrefs.setContentCountry(OnlineRegionCatalog.DEFAULT_COUNTRY_CODE)
        regionPrefs.setContentLanguage(OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE)
        regionPrefs.setDiscoverLanguage(DiscoverMusicLanguage.ALL)
        regionPrefs.setSelectedProviderKey(OnlineMusicProvider.KEY_YOUTUBE_MUSIC)
        HomeModePreferences(app).setHomeMusicMode(HomeMusicMode.OFFLINE)
    }

    private fun sampleTrack(id: Long, title: String, artist: String): OnlineTrack = OnlineTrack(
        stableId = id,
        serviceId = 0,
        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName = "YT Music",
        contentUrl = "https://music.youtube.com/watch?v=moved_$id",
        title = title,
        artist = artist,
        durationMs = 210_000L,
        kind = OnlineItemKind.TRACK
    )

    @Test
    fun homeOnlineMode_removesDiscoveryPanelWithNoBlankGap_andKeepsYourMixSonoraRecommendAndTrendingIndia() {
        val recTrack = sampleTrack(1001L, "Kesariya", "Arijit Singh")
        val trendTrack = sampleTrack(1002L, "Tauba Tauba", "Karan Aujla")
        var playTriggered = false

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                OnlineHomeContent(
                    discoverSections = mapOf(
                        DiscoverSectionType.TRENDING_MUSIC to DiscoverSectionState.Success(listOf(trendTrack), "YT Music"),
                        DiscoverSectionType.POPULAR_SONGS to DiscoverSectionState.Success(listOf(trendTrack), "YT Music"),
                        DiscoverSectionType.NEW_RELEASES to DiscoverSectionState.Success(listOf(trendTrack), "YT Music"),
                        DiscoverSectionType.MUSIC_RECOMMENDATIONS to DiscoverSectionState.Success(listOf(trendTrack), "YT Music"),
                        DiscoverSectionType.POPULAR_ARTISTS to DiscoverSectionState.Empty("None"),
                        DiscoverSectionType.ONLINE_PLAYLISTS to DiscoverSectionState.Empty("None")
                    ),
                    recentlyPlayedOnline = emptyList(),
                    savedOnlineTracks = emptyList(),
                    playlists = emptyList(),
                    selectedProvider = ytMusicProvider,
                    discoverLanguage = DiscoverMusicLanguage.ALL,
                    countryCode = "IN",
                    recommendationProfile = OnlineRecommendationProfile("hindi", "hindi", "Curated"),
                    isNetworkOnline = true,
                    currentSong = null,
                    isPlaying = false,
                    playbackError = null,
                    playbackErrorKind = null,
                    colors = EditorialColors.default(),
                    animationLevel = HomeAnimationLevel.OFF,
                    dailyMixAnimationStyle = DailyMixAnimationStyle.SLIDE,
                    screenVisible = true,
                    reduceAnimations = true,
                    onSelectDiscoverLanguage = {},
                    onOpenRegionSheet = {},
                    onRetrySection = {},
                    onLoadMoreSection = {},
                    onPlayCollection = { _, _ -> },
                    onOpenLocalCollectionModal = { _, _, _ -> },
                    onTrackClick = { _, _ -> },
                    onCollectionClick = {},
                    onTrackLongClick = {},
                    onRecentOnlineSongClick = {},
                    onRecentOnlineSongLongClick = {},
                    onRetryPlayback = {},
                    onSwitchSoundCloudFallback = {},
                    onDismissPlaybackError = {},
                    onOpenPlaylistsScreen = {},
                    sonoraRecommendState = SonoraRecommendUiState(
                        items = listOf(RecommendedSongItem(recTrack, 9.2, "Followed Artist"))
                    ),
                    onPlayRecommendedTrack = { _, _ -> playTriggered = true }
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Removed Online Music discovery panel controls must not exist on Home
        composeRule.onAllNodesWithTag("home_online_music_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_region_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_view_all_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_refresh_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_provider_youtube_music").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_provider_youtube").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_provider_soundcloud").assertCountEquals(0)
        composeRule.onAllNodesWithText("Discover music from online streaming services.").assertCountEquals(0)
        composeRule.onAllNodesWithText("View All").assertCountEquals(0)
        composeRule.onAllNodesWithText("IN•EN").assertCountEquals(0)

        // 2. Your Mix flows naturally into Sonora Recommend with compact standard spacing (no blank gap)
        val yourMixBounds = composeRule.onNodeWithTag("your_mix_section").getUnclippedBoundsInRoot()
        val recBounds = composeRule.onNodeWithTag("sonora_recommend_section").getUnclippedBoundsInRoot()
        val verticalGap = recBounds.top - yourMixBounds.bottom
        assertTrue(
            "Expected natural spacing (~24dp) between Your Mix and Sonora Recommend with no leftover panel space, got $verticalGap",
            verticalGap in 0.dp..40.dp
        )

        // 8. Sonora Recommend remains visible and playable
        composeRule.onNodeWithTag("sonora_recommend_section").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_recommend_play_1001").performClick()
        assertTrue("Tapping Play on Sonora Recommend card must trigger playback callback", playTriggered)

        // 9. Trending Music in India remains visible directly below Sonora Recommend
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_online_subsection_trending_music"))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Trending Music in India").assertIsDisplayed()
    }

    @Test
    fun settingsOnlineMusicScreen_displaysAllFourSections_updatesRegionLanguageProviderRefreshAndLibrary() {
        val vm = SonoraViewModel(app)
        var openedOnlineLibrary = false
        var openedOnlineExplore = false

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                SettingsScreen(
                    viewModel = vm,
                    onOpenOnlineLibrary = { openedOnlineLibrary = true },
                    onOpenOnlineMusic = { openedOnlineExplore = true }
                )
            }
        }
        composeRule.waitForIdle()

        // 3. Settings → Online Music entry is visible and shows preserved IN • EN configuration
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_online_music_row"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_online_music_row").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_online_music_row").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("online_music_settings_screen").assertIsDisplayed()

        // 7. Existing IN • EN selection is preserved
        composeRule.onNodeWithTag("online_settings_region_badge").assertIsDisplayed()
        composeRule.onNodeWithText("IN • EN").assertIsDisplayed()

        // 4. Country, Language, and Regional Language selections work independently
        composeRule.onNodeWithTag("online_settings_country_us").performClick()
        composeRule.waitUntil(5_000L) { vm.onlineContentCountry.value == "US" }
        composeRule.waitForIdle()
        assertEquals("US", vm.onlineContentCountry.value)
        assertEquals("en", vm.onlineContentLanguage.value)
        composeRule.onNodeWithText("US • EN").assertIsDisplayed()

        composeRule.onNodeWithTag("online_settings_language_hi").performClick()
        composeRule.waitUntil(5_000L) { vm.onlineContentLanguage.value == "hi" }
        composeRule.waitForIdle()
        assertEquals("US", vm.onlineContentCountry.value)
        assertEquals("hi", vm.onlineContentLanguage.value)

        // Restore India + Odia regional filter
        composeRule.onNodeWithTag("online_settings_country_in").performClick()
        composeRule.waitUntil(5_000L) { vm.onlineContentCountry.value == "IN" }
        composeRule.onNodeWithTag("online_settings_language_en").performClick()
        composeRule.waitUntil(5_000L) { vm.onlineContentLanguage.value == "en" }
        composeRule.onNodeWithTag("online_settings_regional_lang_odia").performClick()
        composeRule.waitUntil(5_000L) { vm.discoverMusicLanguage.value == DiscoverMusicLanguage.ODIA }
        composeRule.waitForIdle()
        assertEquals("IN", vm.onlineContentCountry.value)
        assertEquals("en", vm.onlineContentLanguage.value)
        assertEquals(DiscoverMusicLanguage.ODIA, vm.discoverMusicLanguage.value)

        // 5. Streaming Services section shows real providers and switching updates active provider
        composeRule.onNodeWithTag("online_music_settings_list")
            .performScrollToNode(hasTestTag("online_settings_providers_section"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("online_settings_provider_youtube_music").assertIsDisplayed()
        composeRule.onNodeWithTag("online_settings_provider_youtube").assertIsDisplayed()
        composeRule.onNodeWithTag("online_settings_provider_soundcloud").assertIsDisplayed()

        val refreshBeforeProvider = vm.discoverRefreshCount.value
        composeRule.onNodeWithTag("online_settings_provider_soundcloud").performClick()
        composeRule.waitUntil(5_000L) {
            vm.selectedOnlineProvider.value?.key == OnlineMusicProvider.KEY_SOUNDCLOUD &&
                vm.discoverRefreshCount.value > refreshBeforeProvider
        }
        composeRule.waitForIdle()
        assertEquals(OnlineMusicProvider.KEY_SOUNDCLOUD, vm.selectedOnlineProvider.value?.key)
        assertTrue(
            "Switching provider must trigger real discovery update",
            vm.discoverRefreshCount.value > refreshBeforeProvider
        )

        // 6. Discovery Preferences Refresh performs a real refresh
        composeRule.onNodeWithTag("online_music_settings_list")
            .performScrollToNode(hasTestTag("online_settings_discovery_section"))
        composeRule.waitForIdle()
        val refreshBeforeTap = vm.discoverRefreshCount.value
        composeRule.onNodeWithTag("online_settings_refresh_discovery_button").performClick()
        composeRule.waitForIdle()
        assertTrue(
            "Refresh button must perform a real discovery refresh",
            vm.discoverRefreshCount.value > refreshBeforeTap
        )

        // Section D: Online Music Library & Browse Full Online Music actions work
        composeRule.onNodeWithTag("online_music_settings_list")
            .performScrollToNode(hasTestTag("online_settings_library_section"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("online_settings_open_online_library").performClick()
        composeRule.waitForIdle()
        assertTrue("Open Online Music Library must invoke library navigation callback", openedOnlineLibrary)
        assertEquals(LibrarySectionMode.ONLINE, vm.librarySectionMode.value)

        // Re-open Settings → Online Music and verify Browse Full Online Music (former View All)
        composeRule.onNodeWithTag("settings_online_music_row").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("online_music_settings_list")
            .performScrollToNode(hasTestTag("online_settings_library_section"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("online_settings_open_online_explore").performClick()
        composeRule.waitForIdle()
        assertTrue("Browse Full Online Music must invoke online music hub callback", openedOnlineExplore)
    }

    @Test
    fun preferencesMigrationAndPersistence_preservesInEnAndPersistsProviderAcrossRestarts() = runBlocking {
        // Simulate existing user with IN • EN in SonoraPreferences before DataStore write
        val legacyPrefs = SonoraPreferences(app)
        legacyPrefs.onlineContentCountry = "IN"
        legacyPrefs.onlineContentLanguage = "en"

        val regionPrefs = OnlineRegionPreferences(app)
        assertEquals("IN", regionPrefs.contentCountryFlow.first())
        assertEquals("en", regionPrefs.contentLanguageFlow.first())

        // Persist provider and region change and verify fresh DataStore instance reloads them
        regionPrefs.setSelectedProviderKey(OnlineMusicProvider.KEY_YOUTUBE)
        regionPrefs.setContentCountry("IN")
        regionPrefs.setContentLanguage("en")

        val reloaded = OnlineRegionPreferences(app)
        assertEquals("IN", reloaded.contentCountryFlow.first())
        assertEquals("en", reloaded.contentLanguageFlow.first())
        assertEquals(OnlineMusicProvider.KEY_YOUTUBE, reloaded.selectedProviderKeyFlow.first())
        assertEquals(OnlineMusicProvider.KEY_YOUTUBE, reloaded.getInitialSelectedProviderKey())
    }

    @Test
    fun offlineMusicOnlineVideoAndPlaybackQueue_remainUnchangedAndFunctional() {
        val vm = SonoraViewModel(app)
        val colors = EditorialColors.default()

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                HomeScreen(
                    viewModel = vm,
                    colors = colors,
                    onOpenSettings = {},
                    onNavigateToLibrary = {},
                    onOpenOnlineMusic = {},
                    onOpenNowPlaying = {},
                    screenVisible = true
                )
            }
        }
        composeRule.waitForIdle()

        // 10. Offline Music mode remains intact
        composeRule.onNodeWithTag("home_offline_content").assertIsDisplayed()

        // Switch to Online Video mode and verify it remains unchanged
        vm.setHomeMusicMode(HomeMusicMode.ONLINE_VIDEO)
        composeRule.waitUntil(5_000L) { vm.homeMusicMode.value == HomeMusicMode.ONLINE_VIDEO }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_online_video_content").assertIsDisplayed()

        // 11. Music playback queue continues working cleanly
        val s1 = Song(
            id = 9001L,
            title = "Kun Faya Kun",
            artist = "A.R. Rahman",
            album = "Rockstar",
            albumId = 9001L,
            durationMs = 470_000L,
            uri = Uri.parse("https://music.youtube.com/watch?v=kfk_9001"),
            dataPath = "",
            mimeType = "audio/mp4",
            sizeBytes = 5_000_000L,
            isOnline = true,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            contentUrl = "https://music.youtube.com/watch?v=kfk_9001"
        )
        val s2 = s1.copy(
            id = 9002L,
            title = "Tum Se Hi",
            artist = "Mohit Chauhan",
            contentUrl = "https://music.youtube.com/watch?v=tsh_9002"
        )
        vm.addToQueue(s1)
        vm.playNextInQueue(s2)
        val queuedIds = vm.queue.value.map { it.id }
        assertTrue("Queue must contain added track", 9001L in queuedIds)
        assertTrue("Queue must contain play-next track", 9002L in queuedIds)
    }
}
