package ui.home

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.model.Song
import com.example.viewmodel.SonoraViewModel
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import data.preferences.HomeModePreferences
import domain.home.HomeMusicMode
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
import ui.home.animations.HomeAnimationLevel
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeOnlineMusicIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    @After
    fun resetPersistedHomeMode() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        HomeModePreferences(app).setHomeMusicMode(HomeMusicMode.OFFLINE)
    }

    private fun sampleOnlineTrack(
        id: Long,
        title: String,
        artist: String = "Arijit Singh",
        kind: OnlineItemKind = OnlineItemKind.TRACK
    ): OnlineTrack = OnlineTrack(
        stableId = id,
        serviceId = 0,
        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName = "YT Music",
        contentUrl = "https://music.youtube.com/watch?v=test_$id",
        title = title,
        artist = artist,
        durationMs = 215_000L,
        kind = kind
    )

    private fun sampleRecentOnlineSong(id: Long, title: String, artist: String): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Online Stream",
        albumId = id,
        durationMs = 198_000L,
        uri = Uri.EMPTY,
        dataPath = "https://music.youtube.com/watch?v=$id",
        mimeType = "audio/mp4",
        sizeBytes = 0L,
        isOnline = true,
        sourceType = "ONLINE",
        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName = "YT Music",
        contentUrl = "https://music.youtube.com/watch?v=$id"
    )

    @Test
    fun `HomeOnlineMusicSection renders Trending Popular Releases Recommendations Artists and Recently Played Online cleanly`() {
        val colors = EditorialColors.default()
        val ytProvider = OnlineMusicProvider(
            serviceId = 0,
            key = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            displayName = "YT Music",
            shortLabel = "YTM",
            baseUrl = "https://music.youtube.com",
            supportedFilters = listOf(OnlineSearchFilter.SONGS, OnlineSearchFilter.ALL),
            defaultFilter = OnlineSearchFilter.SONGS,
            supportsSuggestions = true,
            supportsDiscoveryFeed = true,
            isMusicFocused = true
        )
        val trendingTrack = sampleOnlineTrack(-5001L, "Kesariya", "Arijit Singh")
        val popularSong = sampleOnlineTrack(-5002L, "Naatu Naatu", "Rahul Sipligunj")
        val newRelease = sampleOnlineTrack(-5003L, "Vande Mataram Mix", "A.R. Rahman", OnlineItemKind.ALBUM)
        val recTrack = sampleOnlineTrack(-5004L, "Tum Hi Ho", "Arijit Singh")
        val artistItem = sampleOnlineTrack(-5005L, "Shreya Ghoshal", "Shreya Ghoshal", OnlineItemKind.ARTIST)
        val recentOnlineSong = sampleRecentOnlineSong(-6001L, "Channa Mereya", "Arijit Singh")

        var clickedTrack: OnlineTrack? = null
        var clickedCollection: OnlineTrack? = null
        var viewAllClicked = false
        var retriedSection: DiscoverSectionType? = null
        var loadMoreSection: DiscoverSectionType? = null
        var selectedLang: DiscoverMusicLanguage? = null

        val sections = mapOf(
            DiscoverSectionType.TRENDING_MUSIC to DiscoverSectionState.Success(
                items = listOf(trendingTrack),
                sourceLabel = "YT Music",
                nextPageToken = "tok_next_1",
                hasMore = true
            ),
            DiscoverSectionType.POPULAR_SONGS to DiscoverSectionState.Success(
                items = listOf(popularSong),
                sourceLabel = "YT Music"
            ),
            DiscoverSectionType.NEW_RELEASES to DiscoverSectionState.Loading,
            DiscoverSectionType.MUSIC_RECOMMENDATIONS to DiscoverSectionState.Error(
                kind = OnlineErrorKind.TIMEOUT,
                message = "Timed out loading recommendations",
                isRetryable = true
            ),
            DiscoverSectionType.POPULAR_ARTISTS to DiscoverSectionState.Success(
                items = listOf(artistItem),
                sourceLabel = "YT Music"
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    HomeOnlineMusicSection(
                        discoverSections = sections,
                        recentlyPlayedOnline = listOf(recentOnlineSong),
                        providers = listOf(ytProvider),
                        selectedProvider = ytProvider,
                        discoverLanguage = DiscoverMusicLanguage.HINDI,
                        countryCode = "IN",
                        languageCode = "en",
                        recommendationProfile = OnlineRecommendationProfile(
                            seedQuery = "Arijit Singh Hindi songs",
                            fallbackQuery = "Hindi Bollywood music mix",
                            subtitle = "Inspired by Arijit Singh • India",
                            primaryArtistSeed = "Arijit Singh"
                        ),
                        isNetworkOnline = false,
                        currentSongId = null,
                        isPlaying = false,
                        colors = colors,
                        animationLevel = HomeAnimationLevel.OFF,
                        screenVisible = true,
                        reduceAnimations = true,
                        onSelectProvider = {},
                        onSelectDiscoverLanguage = { selectedLang = it },
                        onOpenRegionSheet = {},
                        onRefreshAll = {},
                        onRetrySection = { retriedSection = it },
                        onLoadMoreSection = { loadMoreSection = it },
                        onTrackClick = { t, _ -> clickedTrack = t },
                        onCollectionClick = { clickedCollection = it },
                        onTrackLongClick = {},
                        onRecentOnlineSongClick = {},
                        onRecentOnlineSongLongClick = {},
                        onViewAllClick = { viewAllClicked = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Title, subtitle, region pill, View All, offline banner, and language pills
        composeRule.onNodeWithTag("home_online_music_section").assertIsDisplayed()
        composeRule.onNodeWithText("Online Music").assertIsDisplayed()
        composeRule.onNodeWithText("Discover music from online streaming services.").assertIsDisplayed()
        composeRule.onNodeWithText("IN•EN").assertIsDisplayed()
        composeRule.onNodeWithTag("home_online_offline_banner").assertIsDisplayed()

        // Trending card displays metadata and triggers play callback
        composeRule.onNodeWithTag("home_online_card_${trendingTrack.stableId}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Kesariya").assertIsDisplayed()
        composeRule.onNodeWithTag("home_online_play_${trendingTrack.stableId}").performClick()
        assertEquals(trendingTrack.stableId, clickedTrack?.stableId)

        // Pagination Load More triggers callback
        composeRule.onNodeWithTag("home_online_load_more_trending_music").performScrollTo().performClick()
        assertEquals(DiscoverSectionType.TRENDING_MUSIC, loadMoreSection)

        // Loading skeleton for New Releases
        composeRule.onNodeWithTag("home_online_skeleton_new_releases").performScrollTo().assertIsDisplayed()

        // Isolated Retry on Error state for Music Recommendations
        composeRule.onNodeWithTag("home_online_retry_music_recommendations").performScrollTo().performClick()
        assertEquals(DiscoverSectionType.MUSIC_RECOMMENDATIONS, retriedSection)

        // Popular Artists medallion triggers collection exploration
        composeRule.onNodeWithTag("home_online_subsection_popular_artists").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_online_artist_${artistItem.stableId}").performScrollTo().performClick()
        assertEquals(artistItem.stableId, clickedCollection?.stableId)

        // Recently Played Online section renders recent online tracks
        composeRule.onNodeWithTag("home_online_recent_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Channa Mereya").assertIsDisplayed()

        // Regional language filter (Odia) & View All button
        composeRule.onNodeWithTag("home_online_language_filters").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_online_lang_odia").performScrollTo().performClick()
        assertEquals(DiscoverMusicLanguage.ODIA, selectedLang)

        composeRule.onNodeWithTag("home_online_view_all_button").performScrollTo().performClick()
        assertTrue(viewAllClicked)

        // Keep reference active for album kind sanity
        assertEquals(OnlineItemKind.ALBUM, newRelease.kind)
        assertEquals("Tum Hi Ho", recTrack.title)
    }

    @Test
    fun `HomeScreen defaults to Offline Music and switches cleanly to Online Music via header switcher`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        vm.setHomeMusicMode(domain.home.HomeMusicMode.OFFLINE)
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
        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("greeting_header").assertIsDisplayed()
        composeRule.onNodeWithTag("header_mode_indicator").assertIsDisplayed()
        composeRule.onNodeWithText("Offline Music").assertIsDisplayed()
        composeRule.onNodeWithTag("home_offline_content").assertIsDisplayed()

        // Tap top-right music-note mode switcher icon
        composeRule.onNodeWithTag("discovery_icon_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_mode_switcher_sheet").assertIsDisplayed()

        // Switch to Online Music mode
        composeRule.onNodeWithTag("mode_option_online").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Online Music").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("home_online_content").assertIsDisplayed()
        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("home_online_music_section"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_online_music_section").assertIsDisplayed()
        composeRule.onAllNodesWithText("Online Music").assertCountEquals(2)
    }
}
