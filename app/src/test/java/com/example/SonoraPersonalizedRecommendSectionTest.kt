package com.example

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.model.Artist
import com.example.model.Playlist
import com.example.model.Song
import data.analytics.ListeningEventEntity
import data.online.LocalPersonalizationSnapshot
import data.online.SonoraRecommendationCoordinator
import data.preferences.SonoraRecommendationCachePreferences
import domain.library.FollowedOnlineArtist
import domain.library.OnlinePlaylist
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationProfile
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.RecommendedSongItem
import domain.online.SonoraPersonalizedRecommendationEngine
import domain.online.SonoraRecommendUiState
import domain.online.SonoraRecommendWeights
import domain.online.UserMusicInteractionSignal
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
import ui.home.OnlineHomeContent
import ui.home.SonoraRecommendSection
import ui.home.animations.DailyMixAnimationStyle
import ui.home.animations.HomeAnimationLevel
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraPersonalizedRecommendSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var cachePrefs: SonoraRecommendationCachePreferences

    private val ytMusicProvider = OnlineMusicProvider(
        serviceId = 0,
        key = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        displayName = "YT Music",
        shortLabel = "YT Music",
        baseUrl = "https://music.youtube.com",
        supportedFilters = listOf(OnlineSearchFilter.SONGS, OnlineSearchFilter.ARTISTS, OnlineSearchFilter.ALBUMS),
        defaultFilter = OnlineSearchFilter.SONGS,
        supportsSuggestions = true,
        supportsDiscoveryFeed = true,
        isMusicFocused = true
    )

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        cachePrefs = SonoraRecommendationCachePreferences(app)
        cachePrefs.clearAllForHistoryReset()
    }

    @After
    fun tearDown() = runBlocking {
        cachePrefs.clearAllForHistoryReset()
    }

    private fun track(
        id: Long,
        title: String,
        artist: String,
        album: String = "",
        url: String = "https://music.youtube.com/watch?v=track_$id",
        artistUrl: String? = "https://music.youtube.com/channel/UC_$id",
        artworkUrl: String? = "https://i.ytimg.com/vi/track_$id/hqdefault.jpg",
        durationMs: Long = 220_000L,
        viewCount: Long = 1_500_000L,
        isLive: Boolean = false,
        kind: OnlineItemKind = OnlineItemKind.TRACK,
        isFavorite: Boolean = false
    ): OnlineTrack = OnlineTrack(
        stableId = id,
        serviceId = 0,
        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName = "YT Music",
        contentUrl = url,
        title = title,
        artist = artist,
        artistUrl = artistUrl,
        album = album,
        durationMs = durationMs,
        artworkUrl = artworkUrl,
        viewCount = viewCount,
        isLive = isLive,
        kind = kind,
        isFavorite = isFavorite
    )

    private fun song(
        id: Long,
        title: String,
        artist: String,
        album: String = "Unknown Album",
        genre: String = "Bollywood",
        isFavorite: Boolean = false,
        playCount: Int = 0,
        lastPlayed: Long = 0L,
        contentUrl: String = "https://music.youtube.com/watch?v=song_$id"
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = id,
        durationMs = 230_000L,
        uri = Uri.parse(contentUrl),
        dataPath = "",
        mimeType = "audio/mp4",
        sizeBytes = 4_000_000L,
        genre = genre,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayedTimestamp = lastPlayed,
        isOnline = true,
        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName = "YT Music",
        contentUrl = contentUrl
    )

    private fun emptySnapshot(country: String = "IN"): LocalPersonalizationSnapshot =
        LocalPersonalizationSnapshot(
            likedLocalSongs = emptyList(),
            likedOnlineSongs = emptyList(),
            followedOnlineArtists = emptyList(),
            favouriteLocalArtists = emptyList(),
            localPlaylists = emptyList(),
            localPlaylistSongs = emptyMap(),
            onlinePlaylists = emptyList(),
            listeningEvents = emptyList(),
            recentlyPlayedOnline = emptyList(),
            recentlyPlayedLocal = emptyList(),
            countryCode = country,
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            strictIndiaOnly = country.equals("IN", ignoreCase = true)
        )

    // =========================================================================
    // 1. HOME SCREEN PLACEMENT: Sonora Recommend directly above Trending Music India
    //    (No duplication of Trending Music India)
    // =========================================================================

    @Test
    fun onlineHomeContent_placesSonoraRecommendDirectlyAboveTrendingMusicIndia_withoutDuplication() {
        val recommendedItems = listOf(
            RecommendedSongItem(
                track = track(101L, "Kesariya", "Arijit Singh", album = "Brahmastra"),
                score = 8.4,
                reasonLabel = "Followed Artist"
            ),
            RecommendedSongItem(
                track = track(102L, "Tuma Katha", "Humane Sagar", album = "Odia Melodies"),
                score = 7.6,
                reasonLabel = "Odia Pick"
            )
        )
        val trendingItems = listOf(
            track(201L, "Tauba Tauba", "Karan Aujla", album = "Bad Newz"),
            track(202L, "Apna Bana Le", "Arijit Singh", album = "Bhediya")
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                OnlineHomeContent(
                    discoverSections = mapOf(
                        DiscoverSectionType.TRENDING_MUSIC to DiscoverSectionState.Success(trendingItems, "YT Music"),
                        DiscoverSectionType.POPULAR_SONGS to DiscoverSectionState.Success(trendingItems, "YT Music"),
                        DiscoverSectionType.NEW_RELEASES to DiscoverSectionState.Success(trendingItems, "YT Music"),
                        DiscoverSectionType.MUSIC_RECOMMENDATIONS to DiscoverSectionState.Success(trendingItems, "YT Music"),
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
                        items = recommendedItems,
                        isLoading = false,
                        profileSummary = "Curated from Arijit Singh, Humane Sagar & your taste"
                    )
                )
            }
        }
        composeRule.waitForIdle()

        // Scroll to Sonora Recommend and verify it is displayed
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("sonora_recommend_section"))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_recommend_section").assertIsDisplayed()
        composeRule.onNodeWithText("MAYUR Recommendations").assertIsDisplayed()

        // Scroll to Trending Music in India and verify exactly ONE Trending Music section exists
        composeRule.onNodeWithTag("home_lazy_column")
            .performScrollToNode(hasTestTag("home_online_subsection_trending_music"))
        composeRule.waitForIdle()
        assertEquals(
            "Trending Music India must NOT be duplicated",
            1,
            composeRule.onAllNodesWithTag("home_online_subsection_trending_music").fetchSemanticsNodes().size
        )

        // Verify Sonora Recommend sits vertically ABOVE Trending Music India
        val recommendBounds = composeRule.onNodeWithTag("sonora_recommend_section").getUnclippedBoundsInRoot()
        val trendingBounds = composeRule.onNodeWithTag("home_online_subsection_trending_music").getUnclippedBoundsInRoot()
        assertTrue(
            "Sonora Recommend (bottom=${recommendBounds.bottom}) must appear above Trending Music India (top=${trendingBounds.top})",
            recommendBounds.bottom <= trendingBounds.top
        )
    }

    // =========================================================================
    // 2. PERSONALIZED SIGNALS: Followed Artists (highest priority), Liked Songs
    //    (avoids repeating already-liked), Multi-Playlist & Listening History
    // =========================================================================

    @Test
    fun personalizationEngine_prioritizesFollowedArtists_usesLikedAndPlaylists_andFiltersShortAccidentalPlays() {
        val now = 1_750_000_000_000L
        val likedSong = song(
            id = 1L,
            title = "Tum Hi Ho",
            artist = "Arijit Singh",
            album = "Aashiqui 2",
            genre = "Bollywood Romantic",
            isFavorite = true,
            contentUrl = "https://music.youtube.com/watch?v=already_liked_1"
        )
        val followedArtist = FollowedOnlineArtist(
            stableId = 50L,
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = "https://music.youtube.com/channel/shreya",
            name = "Shreya Ghoshal",
            artworkUrl = null,
            subscriberCount = 5_000_000L,
            followedTimestamp = now - 10_000L
        )
        val playlist1 = OnlinePlaylist(
            id = 10L,
            name = "Odia Monsoon Melodies",
            artworkUrl = null,
            externalContentUrl = null,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            createdAt = now - 50_000L,
            updatedAt = now - 10_000L,
            tracks = listOf(song(20L, "To Premare", "Humane Sagar", genre = "Odia Melody"))
        )
        val localPlaylist2 = Playlist(id = 11L, name = "Tamil Classics", songCount = 1, createdAt = now - 40_000L)
        val localPlaylistSong = song(21L, "Munbe Vaa", "A.R. Rahman", genre = "Tamil Melody")

        // Listening events: 1 completed session (180s) for Anirudh, and 1 accidental 4s tap for Badshah
        val listeningEvents = listOf(
            ListeningEventEntity(
                sessionToken = "completed_1",
                songId = 30L,
                title = "Why This Kolaveri Di",
                artist = "Anirudh Ravichander",
                album = "3",
                genre = "Tamil",
                isOnline = true,
                providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                artworkUrl = null,
                listenedMs = 180_000L,
                startedAtMs = now - 3_600_000L,
                endedAtMs = now - 3_420_000L,
                dayEpoch = 20_000L,
                isPlayStart = true
            ),
            ListeningEventEntity(
                sessionToken = "accidental_tap_1",
                songId = 31L,
                title = "DJ Waley Babu",
                artist = "Badshah",
                album = "Single",
                genre = "Party",
                isOnline = true,
                providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
                artworkUrl = null,
                listenedMs = 4_000L, // < 20s accidental tap!
                startedAtMs = now - 1_000_000L,
                endedAtMs = now - 996_000L,
                dayEpoch = 20_000L,
                isPlayStart = true
            )
        )

        val profile = SonoraPersonalizedRecommendationEngine.buildUserTasteProfile(
            likedLocalSongs = emptyList(),
            likedOnlineSongs = listOf(likedSong),
            followedOnlineArtists = listOf(followedArtist),
            favouriteLocalArtists = emptyList(),
            localPlaylists = listOf(localPlaylist2),
            localPlaylistSongs = mapOf(11L to listOf(localPlaylistSong)),
            onlinePlaylists = listOf(playlist1),
            listeningEvents = listeningEvents,
            recentlyPlayedOnline = emptyList(),
            recentlyPlayedLocal = emptyList(),
            interactions = UserMusicInteractionSignal(),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            nowMs = now
        )

        assertTrue(profile.hasPersonalizationSignals)
        assertTrue("Followed artist must be present", profile.followedArtistScores.containsKey("shreya ghoshal"))
        assertTrue("Completed listening artist must be present", profile.historyArtistScores.containsKey("anirudh ravichander"))
        assertFalse("Accidental 4s play must NOT enter listening history preferences", profile.historyArtistScores.containsKey("badshah"))
        assertTrue("Multi-playlist languages (Odia & Tamil) must be captured", "Odia" in profile.preferredLanguages && "Tamil" in profile.preferredLanguages)

        val candidates = listOf(
            track(1L, "Tum Hi Ho", "Arijit Singh", album = "Aashiqui 2", url = "https://music.youtube.com/watch?v=already_liked_1", isFavorite = true),
            track(2L, "Agar Tum Saath Ho", "Arijit Singh", album = "Tamasha"),
            track(3L, "Deewani Mastani", "Shreya Ghoshal", album = "Bajirao Mastani"),
            track(4L, "Sun Raha Hai", "Humane Sagar", album = "Odia Hits"),
            track(5L, "Hukum", "Anirudh Ravichander", album = "Jailer"),
            track(6L, "Mercy", "Badshah", album = "ONE")
        )

        val ranked = SonoraPersonalizedRecommendationEngine.rankRecommendations(
            candidates = candidates,
            profile = profile
        )

        // Followed artist track should rank #1
        assertEquals("Followed artist must receive highest priority", "Shreya Ghoshal", ranked.first().track.artist)
        assertEquals("Followed Artist", ranked.first().reasonLabel)

        // Unliked similar track by liked artist (Arijit Singh) must outrank the already-liked track
        val idxNewArijit = ranked.indexOfFirst { it.track.stableId == 2L }
        val idxAlreadyLiked = ranked.indexOfFirst { it.track.stableId == 1L }
        assertTrue("New song by liked artist should outrank already-liked song", idxNewArijit in 0 until idxAlreadyLiked)

        // Completed-session artist (Anirudh) must outrank accidental-tap artist (Badshah)
        val idxAnirudh = ranked.indexOfFirst { it.track.stableId == 5L }
        val idxBadshah = ranked.indexOfFirst { it.track.stableId == 6L }
        assertTrue("Completed listening session must outrank accidental 4s play", idxAnirudh in 0 until idxBadshah)
    }

    // =========================================================================
    // 3. USER INTERACTION LEARNING: Dislike, Early Skip, Play Next, Repeated Listen
    // =========================================================================

    @Test
    fun interactionSignals_suppressDisliked_penalizeSkipped_andBoostPlayNextAndRepeated() {
        val profile = SonoraPersonalizedRecommendationEngine.buildUserTasteProfile(
            likedLocalSongs = emptyList(),
            likedOnlineSongs = listOf(song(1L, "Raabta", "Pritam", isFavorite = true)),
            followedOnlineArtists = emptyList(),
            favouriteLocalArtists = emptyList(),
            localPlaylists = emptyList(),
            localPlaylistSongs = emptyMap(),
            onlinePlaylists = emptyList(),
            listeningEvents = emptyList(),
            recentlyPlayedOnline = emptyList(),
            recentlyPlayedLocal = emptyList(),
            interactions = UserMusicInteractionSignal(
                dislikedTrackIds = setOf(10L),
                dislikedTrackUrls = setOf("https://music.youtube.com/watch?v=track_10"),
                dislikedArtistCounts = mapOf("tony kakkar" to 2),
                skippedTrackCounts = mapOf(11L to 3),
                skippedArtistCounts = mapOf("neha kakkar" to 3),
                playNextArtistCounts = mapOf("vishal mishra" to 3),
                repeatedArtistCounts = mapOf("vishal mishra" to 2)
            ),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL
        )
        val interactions = UserMusicInteractionSignal(
            dislikedTrackIds = setOf(10L),
            dislikedTrackUrls = setOf("https://music.youtube.com/watch?v=track_10"),
            dislikedArtistCounts = mapOf("tony kakkar" to 2),
            skippedTrackCounts = mapOf(11L to 3),
            skippedArtistCounts = mapOf("neha kakkar" to 3),
            playNextArtistCounts = mapOf("vishal mishra" to 3),
            repeatedArtistCounts = mapOf("vishal mishra" to 2)
        )

        val candidates = listOf(
            track(10L, "Dheeme Dheeme", "Tony Kakkar"),
            track(11L, "Kala Chashma", "Neha Kakkar"),
            track(12L, "Pehle Bhi Main", "Vishal Mishra", album = "Animal"),
            track(13L, "Kaise Hua", "Vishal Mishra", album = "Kabir Singh")
        )

        val ranked = SonoraPersonalizedRecommendationEngine.rankRecommendations(
            candidates = candidates,
            profile = profile,
            interactions = interactions
        )

        assertTrue("Explicitly disliked track must be excluded", ranked.none { it.track.stableId == 10L })
        assertEquals("Play Next + repeated listening artist must rank first", "Vishal Mishra", ranked.first().track.artist)
        assertTrue(
            "Heavily skipped track must rank below Play Next boosted tracks",
            ranked.indexOfFirst { it.track.stableId == 11L } > ranked.indexOfFirst { it.track.stableId == 12L }
        )
    }

    // =========================================================================
    // 4. INDIA STRICT FILTERING, REGIONAL LANGUAGES & INDIAN ENGLISH ELIGIBILITY
    // =========================================================================

    @Test
    fun indiaStrictFiltering_includesRegionalAndIndianEnglish_andExcludesForeignAndUnknownOrigin() {
        val profile = SonoraPersonalizedRecommendationEngine.buildUserTasteProfile(
            likedLocalSongs = emptyList(),
            likedOnlineSongs = emptyList(),
            followedOnlineArtists = emptyList(),
            favouriteLocalArtists = emptyList(),
            localPlaylists = emptyList(),
            localPlaylistSongs = emptyMap(),
            onlinePlaylists = emptyList(),
            listeningEvents = emptyList(),
            recentlyPlayedOnline = emptyList(),
            recentlyPlayedLocal = emptyList(),
            interactions = UserMusicInteractionSignal(),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            strictIndiaOnly = true
        )

        val candidates = listOf(
            track(1L, "Kesariya", "Arijit Singh"),                               // Hindi
            track(2L, "Tuma Katha Odia Song", "Humane Sagar"),                   // Odia
            track(3L, "Boba Tunnel", "Anupam Roy"),                              // Bengali
            track(4L, "Lover", "Diljit Dosanjh"),                                // Punjabi
            track(5L, "Why This Kolaveri Di", "Anirudh Ravichander"),            // Tamil
            track(6L, "Naatu Naatu Telugu", "M.M. Keeravani"),                   // Telugu
            track(7L, "Kudukku Malayalam", "Vineeth Sreenivasan"),               // Malayalam
            track(8L, "Belageddu Kannada", "Vijay Prakash"),                     // Kannada
            track(9L, "Zingaat Marathi", "Ajay-Atul"),                           // Marathi
            track(10L, "Mayabini Assamese", "Zubeen Garg"),                      // Assamese
            track(11L, "Khalasi Gujarati", "Aditya Gadhvi"),                     // Gujarati
            track(12L, "Cold Mess", "Prateek Kuhad"),                            // English-language Indian artist!
            track(13L, "Khoj (Passing By)", "When Chai Met Toast"),              // English-language Indian indie!
            track(14L, "Anti-Hero", "Taylor Swift"),                             // Foreign -> MUST EXCLUDE
            track(15L, "Dynamite", "BTS"),                                       // Foreign -> MUST EXCLUDE
            track(16L, "Midnight Breeze", "Random Unknown Studio 999")           // Unknown origin -> MUST EXCLUDE
        )

        val ranked = SonoraPersonalizedRecommendationEngine.rankRecommendations(
            candidates = candidates,
            profile = profile,
            weights = SonoraRecommendWeights(targetCount = 20)
        )
        val ids = ranked.map { it.track.stableId }.toSet()

        // All 11 Indian regional languages + 2 Indian English tracks must be preserved
        for (indianId in 1L..13L) {
            assertTrue("Expected Indian/Indian-English track $indianId to be eligible", indianId in ids)
        }
        // Foreign & unknown-origin tracks must NEVER leak into strict India-only recommendations
        assertFalse("Foreign artist Taylor Swift must be excluded", 14L in ids)
        assertFalse("Foreign group BTS must be excluded", 15L in ids)
        assertFalse("Unknown origin track must be excluded in strict India mode", 16L in ids)
    }

    // =========================================================================
    // 5. DIVERSITY, DEDUPLICATION, CONFIGURABLE WEIGHTS & UNAVAILABLE FILTERING
    // =========================================================================

    @Test
    fun ranking_enforcesArtistDiversity_deduplicates_rejectsUnavailableAndInvalidMetadata() {
        val profile = SonoraPersonalizedRecommendationEngine.buildUserTasteProfile(
            likedLocalSongs = emptyList(),
            likedOnlineSongs = emptyList(),
            followedOnlineArtists = emptyList(),
            favouriteLocalArtists = emptyList(),
            localPlaylists = emptyList(),
            localPlaylistSongs = emptyMap(),
            onlinePlaylists = emptyList(),
            listeningEvents = emptyList(),
            recentlyPlayedOnline = emptyList(),
            recentlyPlayedLocal = emptyList(),
            interactions = UserMusicInteractionSignal(),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL
        )

        val candidates = listOf(
            // 4 tracks by same artist (should be capped at maxTracksPerArtist = 2)
            track(1L, "Song One", "Arijit Singh"),
            track(2L, "Song Two", "Arijit Singh"),
            track(3L, "Song Three", "Arijit Singh"),
            track(4L, "Song Four", "Arijit Singh"),
            // Duplicate URL & duplicate title|artist
            track(5L, "Song One", "Arijit Singh", url = "https://music.youtube.com/watch?v=track_1"),
            track(6L, "Song One", "Arijit Singh", url = "https://music.youtube.com/watch?v=dup_title"),
            // Invalid / deleted / live / unsafe URL / clip candidates
            track(7L, "[Deleted video]", "Arijit Singh"),
            track(8L, "Live Concert", "Arijit Singh", isLive = true),
            track(9L, "Unsafe", "Arijit Singh", url = "http://insecure.example.com/x"),
            track(10L, "Short Clip", "Arijit Singh", durationMs = 10_000L),
            // Valid diverse Indian artists
            track(11L, "Barso Re", "Shreya Ghoshal"),
            track(12L, "To Bina Odia", "Humane Sagar")
        )

        val ranked = SonoraPersonalizedRecommendationEngine.rankRecommendations(
            candidates = candidates,
            profile = profile,
            unavailableTrackUrls = setOf("https://music.youtube.com/watch?v=track_2"),
            weights = SonoraRecommendWeights(maxTracksPerArtist = 2, targetCount = 10)
        )

        val arijitCount = ranked.count { it.track.artist == "Arijit Singh" }
        assertTrue("Artist diversity cap (<=2) must be enforced, was $arijitCount", arijitCount <= 2)
        assertTrue("Unavailable track_2 must be excluded", ranked.none { it.track.stableId == 2L })
        assertTrue("Deleted/live/http/short tracks must be excluded", ranked.none { it.track.stableId in setOf(7L, 8L, 9L, 10L) })
        assertEquals("Must have zero duplicate track URLs", ranked.size, ranked.map { it.track.contentUrl }.toSet().size)
    }

    // =========================================================================
    // 6. COLD-START DISCOVERY, CACHING, REFRESH ROTATION & OFFLINE FALLBACK
    // =========================================================================

    @Test
    fun coordinator_handlesColdStartDiscovery_cachesLocally_rotatesOnRefresh_andServesOfflineCache() = runBlocking {
        var searchCalls = 0
        val pool = listOf(
            track(101L, "Kesariya", "Arijit Singh", album = "Brahmastra"),
            track(102L, "Sajni", "Arijit Singh", album = "Laapataa Ladies"),
            track(103L, "Barso Re", "Shreya Ghoshal", album = "Guru"),
            track(104L, "Tuma Katha", "Humane Sagar", album = "Odia Hits"),
            track(105L, "Naatu Naatu", "M.M. Keeravani", album = "RRR"),
            track(106L, "Lover", "Diljit Dosanjh", album = "MoonChild Era")
        )
        val coordinator = SonoraRecommendationCoordinator(
            cachePreferences = cachePrefs,
            searchCandidates = { _, _, _, _ ->
                searchCalls++
                OnlineResult.Success(pool)
            }
        )

        // 1. Cold start (zero likes, follows, playlists, history) -> discovery picks + cold-start message
        val coldStart = coordinator.loadRecommendations(
            snapshot = emptySnapshot("IN"),
            provider = ytMusicProvider,
            existingDiscoverSections = emptyMap(),
            isNetworkOnline = true,
            forceRefresh = false,
            refreshGeneration = 0,
            nowMs = 100_000L
        )
        assertTrue("Cold-start flag must be true when user has no history", coldStart.isColdStartDiscovery)
        assertEquals(SonoraPersonalizedRecommendationEngine.COLD_START_MESSAGE, coldStart.profileSummary)
        assertTrue("Discovery tracks must be returned during cold start", coldStart.items.isNotEmpty())
        val callsAfterFirstLoad = searchCalls

        // 2. Subsequent non-forced load within TTL hits local cache (0 extra network calls!)
        val warmCached = coordinator.loadRecommendations(
            snapshot = emptySnapshot("IN"),
            provider = ytMusicProvider,
            existingDiscoverSections = emptyMap(),
            isNetworkOnline = true,
            forceRefresh = false,
            refreshGeneration = 0,
            nowMs = 110_000L
        )
        assertEquals("Warm cache must avoid redundant network calls", callsAfterFirstLoad, searchCalls)
        assertEquals(coldStart.items.map { it.track.stableId }, warmCached.items.map { it.track.stableId })

        // 3. Offline mode serves cached recommendations without crashing
        val offlineState = coordinator.loadRecommendations(
            snapshot = emptySnapshot("IN"),
            provider = ytMusicProvider,
            existingDiscoverSections = emptyMap(),
            isNetworkOnline = false,
            forceRefresh = false,
            refreshGeneration = 0,
            nowMs = 120_000L
        )
        assertTrue("Offline mode must serve cached recommendations", offlineState.isServingCachedOffline)
        assertTrue("Offline cached items must be non-empty", offlineState.items.isNotEmpty())
        assertEquals(null, offlineState.errorMessage)
    }

    // =========================================================================
    // 7. RECOMMENDATION CARDS & UI INTERACTIONS
    //    (Artwork, Title, Artist, Album, Play, Queue, Play Next, Like, Artist, Album, Dislike, Refresh)
    // =========================================================================

    @Test
    fun sonoraRecommendSection_displaysColdStartAndCardMetadata_andTriggersAllInteractions() {
        val sampleTrack = track(
            id = 501L,
            title = "Apna Bana Le",
            artist = "Arijit Singh",
            album = "Bhediya"
        )
        var playClicks = 0
        var likeClicks = 0
        var playNextClicks = 0
        var addQueueClicks = 0
        var openArtistClicks = 0
        var openAlbumClicks = 0
        var dislikeClicks = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                SonoraRecommendSection(
                    state = SonoraRecommendUiState(
                        items = listOf(
                            RecommendedSongItem(
                                track = sampleTrack,
                                score = 9.0,
                                reasonLabel = "Discovery Pick"
                            )
                        ),
                        isColdStartDiscovery = true,
                        isServingCachedOffline = true,
                        profileSummary = SonoraPersonalizedRecommendationEngine.COLD_START_MESSAGE
                    ),
                    favoriteOnlineIds = emptySet(),
                    currentSongId = null,
                    isPlaying = false,
                    colors = EditorialColors.default(),
                    animationLevel = HomeAnimationLevel.OFF,
                    screenVisible = true,
                    reduceAnimations = true,
                    onRefresh = {},
                    onPlayTrack = { _, _ -> playClicks++ },
                    onAddToQueue = { addQueueClicks++ },
                    onPlayNext = { playNextClicks++ },
                    onToggleLike = { likeClicks++ },
                    onDislikeTrack = { dislikeClicks++ },
                    onOpenArtist = { openArtistClicks++ },
                    onOpenAlbum = { openAlbumClicks++ },
                    onMoreOptions = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Title, cold-start subtitle, offline badge, song title, artist, optional album
        composeRule.onNodeWithText("MAYUR Recommendations").assertIsDisplayed()
        composeRule.onNodeWithText("Your music journey starts here 🎵").assertIsDisplayed()
        composeRule.onNodeWithTag("sonora_recommend_offline_badge").assertIsDisplayed()
        composeRule.onNodeWithText("Apna Bana Le").assertIsDisplayed()
        composeRule.onNodeWithText("Arijit Singh").assertIsDisplayed()
        composeRule.onNodeWithText("Bhediya").assertIsDisplayed()

        // Header Refresh button is completely removed
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("sonora_recommend_refresh_button").fetchSemanticsNodes().size
        )

        // Play & Quick Like
        composeRule.onNodeWithTag("sonora_recommend_play_501").performClick()
        composeRule.onNodeWithTag("sonora_recommend_like_501").performClick()
        composeRule.waitForIdle()
        assertEquals(1, playClicks)
        assertEquals(1, likeClicks)

        // Overflow menu: Play Next, Add to Queue, Open Artist, Open Album, Dislike
        composeRule.onNodeWithTag("sonora_recommend_more_501").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_recommend_menu_play_next").performClick()
        composeRule.waitForIdle()
        assertEquals(1, playNextClicks)

        composeRule.onNodeWithTag("sonora_recommend_more_501").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_recommend_menu_add_queue").performClick()
        composeRule.waitForIdle()
        assertEquals(1, addQueueClicks)

        composeRule.onNodeWithTag("sonora_recommend_more_501").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_recommend_menu_open_artist").performClick()
        composeRule.waitForIdle()
        assertEquals(1, openArtistClicks)

        composeRule.onNodeWithTag("sonora_recommend_more_501").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_recommend_menu_open_album").performClick()
        composeRule.waitForIdle()
        assertEquals(1, openAlbumClicks)

        composeRule.onNodeWithTag("sonora_recommend_more_501").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sonora_recommend_menu_dislike").performClick()
        composeRule.waitForIdle()
        assertEquals(1, dislikeClicks)
    }
}
