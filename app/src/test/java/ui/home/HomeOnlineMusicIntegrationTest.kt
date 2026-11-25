package ui.home

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.model.Song
import com.example.viewmodel.SonoraViewModel
import data.preferences.HomeModePreferences
import domain.home.HomeMusicMode
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.RecommendedSongItem
import domain.online.SonoraRecommendUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.home.animations.DailyMixAnimationStyle
import ui.home.animations.HomeAnimationLevel
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

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
    fun `OnlineHomeContent renders carousels cleanly without removed Online Music controls panel`() {
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
        val recTrack = sampleOnlineTrack(-5004L, "Tum Hi Ho", "Arijit Singh")
        val artistItem = sampleOnlineTrack(-5005L, "Shreya Ghoshal", "Shreya Ghoshal", OnlineItemKind.ARTIST)
        val recentOnlineSong = sampleRecentOnlineSong(-6001L, "Channa Mereya", "Arijit Singh")

        var clickedTrack: OnlineTrack? = null
        var clickedCollection: OnlineTrack? = null
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
            ),
            DiscoverSectionType.ONLINE_PLAYLISTS to DiscoverSectionState.Empty("None")
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                OnlineHomeContent(
                    discoverSections = sections,
                    recentlyPlayedOnline = listOf(recentOnlineSong),
                    savedOnlineTracks = emptyList(),
                    playlists = emptyList(),
                    selectedProvider = ytProvider,
                    discoverLanguage = DiscoverMusicLanguage.HINDI,
                    countryCode = "IN",
                    recommendationProfile = OnlineRecommendationProfile(
                        seedQuery = "Arijit Singh Hindi songs",
                        fallbackQuery = "Hindi Bollywood music mix",
                        subtitle = "Inspired by Arijit Singh • India",
                        primaryArtistSeed = "Arijit Singh"
                    ),
                    isNetworkOnline = false,
                    currentSong = null,
                    isPlaying = false,
                    playbackError = null,
                    playbackErrorKind = null,
                    colors = colors,
                    animationLevel = HomeAnimationLevel.OFF,
                    dailyMixAnimationStyle = DailyMixAnimationStyle.SLIDE,
                    screenVisible = true,
                    reduceAnimations = true,
                    onSelectDiscoverLanguage = { selectedLang = it },
                    onOpenRegionSheet = {},
                    onRetrySection = { retriedSection = it },
                    onLoadMoreSection = { loadMoreSection = it },
                    onPlayCollection = { _, _ -> },
                    onOpenLocalCollectionModal = { _, _, _ -> },
                    onTrackClick = { t, _ -> clickedTrack = t },
                    onCollectionClick = { clickedCollection = it },
                    onTrackLongClick = {},
                    onRecentOnlineSongClick = {},
                    onRecentOnlineSongLongClick = {},
                    onRetryPlayback = {},
                    onSwitchSoundCloudFallback = {},
                    onDismissPlaybackError = {},
                    onOpenPlaylistsScreen = {},
                    sonoraRecommendState = SonoraRecommendUiState(
                        items = listOf(
                            RecommendedSongItem(
                                track = recTrack,
                                score = 9.0,
                                reasonLabel = "Picked for You"
                            )
                        )
                    )
                )
            }
        }

        composeRule.waitForIdle()

        // Removed Home Online Music discovery controls panel must NOT appear
        composeRule.onAllNodesWithTag("home_online_music_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_region_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_view_all_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_refresh_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_provider_youtube_music").assertCountEquals(0)
        composeRule.onAllNodesWithText("Discover music from online streaming services.").assertCountEquals(0)

        // Conditional offline banner still displays when offline
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_online_offline_banner"))
        composeRule.onNodeWithTag("home_online_offline_banner").assertIsDisplayed()

        // Sonora Recommend & Trending Music in India work seamlessly
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("sonora_recommend_section"))
        composeRule.onNodeWithTag("sonora_recommend_section").assertIsDisplayed()

        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_online_subsection_trending_music"))
        composeRule.onNodeWithTag("home_online_card_${trendingTrack.stableId}").assertIsDisplayed()
        composeRule.onNodeWithTag("home_online_play_${trendingTrack.stableId}").performClick()
        assertEquals(trendingTrack.stableId, clickedTrack?.stableId)

        // Pagination Load More triggers callback
        composeRule.onNodeWithTag("home_online_load_more_trending_music").performScrollTo().performClick()
        assertEquals(DiscoverSectionType.TRENDING_MUSIC, loadMoreSection)

        // Loading skeleton for New Releases
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_online_subsection_new_releases"))
        composeRule.onNodeWithTag("home_online_skeleton_new_releases").assertIsDisplayed()

        // Isolated Retry on Error state for Music Recommendations
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("online_made_for_you_section"))
        composeRule.onNodeWithTag("home_online_retry_music_recommendations").performClick()
        assertEquals(DiscoverSectionType.MUSIC_RECOMMENDATIONS, retriedSection)

        // Popular Artists medallion triggers collection exploration
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_online_subsection_popular_artists"))
        composeRule.onNodeWithTag("home_online_artist_${artistItem.stableId}").performClick()
        assertEquals(artistItem.stableId, clickedCollection?.stableId)

        // Regional language filter (Odia) & Recently Played Online still work
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("online_regional_music_section"))
        composeRule.onNodeWithTag("home_online_lang_odia").performScrollTo().performClick()
        assertEquals(DiscoverMusicLanguage.ODIA, selectedLang)

        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_online_recent_section"))
        composeRule.onNodeWithText("Channa Mereya").assertIsDisplayed()
    }

    @Test
    fun `HomeScreen defaults to Offline Music and switches cleanly to Online Music without discovery panel`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        vm.setHomeMusicMode(HomeMusicMode.OFFLINE)
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

        // Switch to Online Music mode via header mode switcher
        composeRule.onNodeWithTag("discovery_icon_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_mode_switcher_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("mode_option_online").performClick()
        composeRule.waitForIdle()

        // Only the header mode pill says "Online Music" — the removed Home discovery panel is gone
        composeRule.onAllNodesWithText("Online Music").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("home_online_content").assertIsDisplayed()
        composeRule.onAllNodesWithTag("home_online_music_section").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_view_all_button").assertCountEquals(0)
        composeRule.onAllNodesWithTag("home_online_region_button").assertCountEquals(0)
        composeRule.onAllNodesWithText("Online Music").assertCountEquals(1)
    }
}
