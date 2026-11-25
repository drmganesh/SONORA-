package ui.onboarding

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.example.model.Song
import data.analytics.ListeningEventEntity
import data.online.MusicDiscoveryAndRadioEngine
import data.online.MusicTasteDiscoveryCoordinator
import data.online.OnlineTrackMapper
import data.online.SongRadioSession
import data.preferences.MusicTastePreferences
import domain.onboarding.MusicTasteArtist
import domain.onboarding.MusicTasteArtistDiscoveryUiState
import domain.onboarding.MusicTasteCatalog
import domain.onboarding.MusicTasteDiscoverySessionUiState
import domain.onboarding.MusicTasteDiscoveryTrack
import domain.onboarding.MusicTasteEditLaunchMode
import domain.onboarding.MusicTasteOnboardingState
import domain.onboarding.MusicTastePreviewState
import domain.onboarding.MusicTasteProfile
import domain.onboarding.MusicTasteSwipeDecision
import domain.online.DiscoverMusicLanguage
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineRecommendationEngine
import domain.online.OnlineResult
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.SonoraPersonalizedRecommendationEngine
import domain.online.UserMusicInteractionSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.profile.MusicTasteScreen
import ui.theme.EditorialColors
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MusicTasteOnboardingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testProvider = OnlineMusicProvider(
        serviceId = 0,
        key = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        displayName = "YouTube Music",
        shortLabel = "YT Music",
        baseUrl = "https://music.youtube.com",
        supportedFilters = listOf(OnlineSearchFilter.SONGS, OnlineSearchFilter.ARTISTS),
        defaultFilter = OnlineSearchFilter.SONGS,
        supportsSuggestions = true,
        supportsDiscoveryFeed = true,
        isMusicFocused = true
    )

    private fun createIsolatedStore(context: Context): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
        val file = File(context.cacheDir, "music_taste_test_${UUID.randomUUID()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { file }
        )
    }

    @Test
    fun freshInstallShowsOnboarding_andNeverRepeatsAfterCompletionOrRestart() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = createIsolatedStore(context)

        val prefs = MusicTastePreferences(
            context = context,
            dataStoreOverride = store,
            existingInstallationDetector = { false }
        )

        assertEquals(MusicTasteOnboardingState.REQUIRED, prefs.ensureInitialOnboardingStateResolved())

        // Save preferences and finish onboarding
        val artist = MusicTasteArtist(id = "arijit", name = "Arijit Singh")
        prefs.toggleArtist(artist)
        prefs.toggleGenre("Bollywood")
        prefs.toggleGenre("Indie")
        prefs.completeOnboarding()

        assertEquals(MusicTasteOnboardingState.COMPLETED, prefs.onboardingState.value)

        // Simulate app restart reading the same DataStore
        val reloaded = MusicTastePreferences(
            context = context,
            dataStoreOverride = store,
            existingInstallationDetector = { false }
        )
        assertEquals(MusicTasteOnboardingState.COMPLETED, reloaded.ensureInitialOnboardingStateResolved())
        val savedProfile = reloaded.getProfileOnce()
        assertEquals(listOf("Arijit Singh"), savedProfile.selectedArtists.map { it.name })
        assertEquals(listOf("Bollywood", "Indie"), savedProfile.selectedGenres)
        assertTrue(savedProfile.onboardingCompleted)
    }

    @Test
    fun appUpdateOnExistingInstall_neverTriggersMusicTasteOnboarding() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = createIsolatedStore(context)

        // Pre-existing installation upgrading to new app version
        val prefs = MusicTastePreferences(
            context = context,
            dataStoreOverride = store,
            existingInstallationDetector = { true }
        )

        assertEquals(MusicTasteOnboardingState.COMPLETED, prefs.ensureInitialOnboardingStateResolved())
        assertTrue(prefs.getProfileOnce().onboardingCompleted)
    }

    @Test
    fun skipOnboarding_marksCompletedAndNeverReappears() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = createIsolatedStore(context)

        val prefs = MusicTastePreferences(
            context = context,
            dataStoreOverride = store,
            existingInstallationDetector = { false }
        )
        assertEquals(MusicTasteOnboardingState.REQUIRED, prefs.ensureInitialOnboardingStateResolved())

        prefs.skipOnboarding()
        assertEquals(MusicTasteOnboardingState.COMPLETED, prefs.onboardingState.value)

        val restarted = MusicTastePreferences(
            context = context,
            dataStoreOverride = store,
            existingInstallationDetector = { false }
        )
        assertEquals(MusicTasteOnboardingState.COMPLETED, restarted.ensureInitialOnboardingStateResolved())
    }

    @Test
    fun curatedGenreCatalog_containsAllRequired38Genres() {
        val requiredGenres = listOf(
            "Pop", "Hip-hop", "Rock", "R&B", "Electronic", "Indie", "Alternative", "Metal",
            "Jazz", "Classical", "Country", "Folk", "Soul", "Funk", "Blues", "Punk",
            "House", "Techno", "Drum and Bass", "Lo-fi", "Ambient", "Reggae", "K-Pop",
            "Afrobeats", "Reggaeton", "Soundtracks", "Indian Classical", "Bollywood",
            "Odia", "Punjabi", "Bengali", "Tamil", "Telugu", "Malayalam", "Kannada",
            "Marathi", "Assamese", "Gujarati"
        )
        assertEquals(38, requiredGenres.size)
        requiredGenres.forEach { genre ->
            assertTrue("Missing genre: $genre", genre in MusicTasteCatalog.CURATED_GENRES)
        }
    }

    @Test
    fun discoveryCoordinator_noFakeArtistsOrSongs_deduplicatesAndFiltersUnavailableAndLiked() = runBlocking {
        val realSearchTracks = listOf(
            OnlineTrack(
                stableId = 101L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                contentUrl = "https://music.youtube.com/watch?v=realSong1",
                title = "Kesariya",
                artist = "Arijit Singh",
                durationMs = 240_000L,
                artworkUrl = "https://i.ytimg.com/vi/realSong1/hqdefault.jpg",
                kind = OnlineItemKind.TRACK
            ),
            // Duplicate URL
            OnlineTrack(
                stableId = 102L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                contentUrl = "https://music.youtube.com/watch?v=realSong1",
                title = "Kesariya",
                artist = "Arijit Singh",
                durationMs = 240_000L,
                kind = OnlineItemKind.TRACK
            ),
            // Unavailable track
            OnlineTrack(
                stableId = 103L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                contentUrl = "https://music.youtube.com/watch?v=deleted1",
                title = "[Deleted Video]",
                artist = "Arijit Singh",
                durationMs = 200_000L,
                kind = OnlineItemKind.TRACK
            ),
            // Already-liked track
            OnlineTrack(
                stableId = 104L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                contentUrl = "https://music.youtube.com/watch?v=alreadyLiked1",
                title = "Tum Hi Ho",
                artist = "Arijit Singh",
                durationMs = 260_000L,
                kind = OnlineItemKind.TRACK
            ),
            // Valid second track
            OnlineTrack(
                stableId = 105L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                contentUrl = "https://music.youtube.com/watch?v=realSong2",
                title = "Apna Bana Le",
                artist = "Arijit Singh",
                durationMs = 250_000L,
                artworkUrl = "https://i.ytimg.com/vi/realSong2/hqdefault.jpg",
                kind = OnlineItemKind.TRACK
            )
        )

        val coordinator = MusicTasteDiscoveryCoordinator(
            searchOnline = { _, _, filter ->
                when (filter) {
                    OnlineSearchFilter.ARTISTS -> OnlineResult.Success(
                        listOf(
                            OnlineTrack(
                                stableId = 1L,
                                serviceId = 0,
                                providerKey = testProvider.key,
                                providerName = testProvider.displayName,
                                contentUrl = "https://music.youtube.com/channel/UCarijit",
                                title = "Arijit Singh",
                                artist = "Arijit Singh",
                                kind = OnlineItemKind.ARTIST
                            ),
                            OnlineTrack(
                                stableId = 2L,
                                serviceId = 0,
                                providerKey = testProvider.key,
                                providerName = testProvider.displayName,
                                contentUrl = "https://music.youtube.com/channel/UCshreya",
                                title = "Shreya Ghoshal",
                                artist = "Shreya Ghoshal",
                                kind = OnlineItemKind.ARTIST
                            ),
                            // Duplicate artist
                            OnlineTrack(
                                stableId = 3L,
                                serviceId = 0,
                                providerKey = testProvider.key,
                                providerName = testProvider.displayName,
                                contentUrl = "https://music.youtube.com/channel/UCarijit2",
                                title = "Arijit Singh - Topic",
                                artist = "Arijit Singh - Topic",
                                kind = OnlineItemKind.ARTIST
                            )
                        )
                    )
                    else -> OnlineResult.Success(realSearchTracks)
                }
            },
            fetchDiscoveryFeed = { _, _ -> OnlineResult.Success(emptyList()) }
        )

        // Offline with no cache -> MUST return empty (ZERO fake artists or fake songs)
        val offlineArtists = coordinator.loadInitialArtists(
            provider = testProvider,
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            selectedArtists = emptyList(),
            followedArtists = emptyList(),
            cachedOnlineTracks = emptyList(),
            isNetworkOnline = false
        ).getOrThrow()
        assertTrue(offlineArtists.isEmpty())

        val offlineSongs = coordinator.buildDiscoverySession(
            selectedArtists = emptyList(),
            selectedGenres = emptyList(),
            provider = testProvider,
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            existingLikedKeys = emptySet(),
            alreadyDecidedUrls = emptySet(),
            cachedOnlineTracks = emptyList(),
            isNetworkOnline = false
        ).getOrThrow()
        assertTrue(offlineSongs.isEmpty())

        // Online artist search deduplicates cleanly
        val searchedArtists = coordinator.searchArtists(
            query = "Arijit",
            provider = testProvider,
            isNetworkOnline = true
        ).getOrThrow()
        assertEquals(listOf("Arijit Singh", "Shreya Ghoshal"), searchedArtists.map { it.name })

        // Online discovery session filters duplicates, unavailable, and already-liked tracks
        val sessionTracks = coordinator.buildDiscoverySession(
            selectedArtists = listOf(MusicTasteArtist(id = "arijit", name = "Arijit Singh")),
            selectedGenres = listOf("Bollywood"),
            provider = testProvider,
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            existingLikedKeys = setOf("https://music.youtube.com/watch?v=alreadyliked1"),
            alreadyDecidedUrls = emptySet(),
            cachedOnlineTracks = emptyList(),
            isNetworkOnline = true,
            targetCount = 18
        ).getOrThrow()

        assertEquals(2, sessionTracks.size)
        assertEquals(listOf("Kesariya", "Apna Bana Le"), sessionTracks.map { it.title })
        assertTrue(sessionTracks.all { it.reasonLabel.contains("Arijit Singh") })
    }

    @Test
    fun resetMusicTaste_clearsOnlyTastePreferencesAndPreservesOnboardingCompleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = createIsolatedStore(context)
        val prefs = MusicTastePreferences(
            context = context,
            dataStoreOverride = store,
            existingInstallationDetector = { false }
        )

        prefs.toggleArtist(MusicTasteArtist(id = "papon", name = "Papon"))
        prefs.toggleGenre("Assamese")
        prefs.recordDiscoveryDecision(
            track = MusicTasteDiscoveryTrack(
                stableId = 50L,
                contentUrl = "https://music.youtube.com/watch?v=abc50",
                title = "Moh Moh Ke Dhaage",
                artist = "Papon"
            ),
            decision = MusicTasteSwipeDecision.ADD_TO_LIKED_SONGS
        )
        prefs.completeOnboarding()

        val afterReset = prefs.resetMusicTaste()
        assertTrue(afterReset.selectedArtists.isEmpty())
        assertTrue(afterReset.selectedGenres.isEmpty())
        assertTrue(afterReset.likedDiscoveryTracks.isEmpty())
        assertTrue(afterReset.passedDiscoveryTracks.isEmpty())
        assertTrue(afterReset.onboardingCompleted)
        assertEquals(MusicTasteOnboardingState.COMPLETED, prefs.onboardingState.value)
    }

    @Test
    fun recommendationEngines_reflectSelectedArtistsGenresLikedAndPassedDiscoveryTracks() {
        val selectedArtist = MusicTasteArtist(id = "diljit", name = "Diljit Dosanjh")
        val likedDiscovery = MusicTasteDiscoveryTrack(
            stableId = 201L,
            contentUrl = "https://music.youtube.com/watch?v=diljit1",
            title = "Lover",
            artist = "Diljit Dosanjh",
            durationMs = 195_000L
        )
        val passedDiscovery = MusicTasteDiscoveryTrack(
            stableId = 202L,
            contentUrl = "https://music.youtube.com/watch?v=passedTrack1",
            title = "Passed Song",
            artist = "Random Punjabi Artist",
            durationMs = 200_000L
        )
        val tasteProfile = MusicTasteProfile(
            selectedArtists = listOf(selectedArtist),
            selectedGenres = listOf("Punjabi"),
            likedDiscoveryTracks = listOf(likedDiscovery),
            passedDiscoveryTracks = listOf(passedDiscovery),
            onboardingCompleted = true,
            lastUpdatedAtMs = 1_700_000_000_000L
        )

        // 1. SonoraPersonalizedRecommendationEngine incorporates selected artists, genres, liked & passed tracks
        val userTaste = SonoraPersonalizedRecommendationEngine.buildUserTasteProfile(
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
            musicTasteProfile = tasteProfile
        )
        assertTrue(userTaste.hasPersonalizationSignals)
        assertTrue(userTaste.topDisplayArtists.any { it.equals("Diljit Dosanjh", ignoreCase = true) })
        assertTrue("Punjabi" in userTaste.preferredLanguages)

        val candidates = listOf(
            OnlineTrack(
                stableId = 301L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                contentUrl = "https://music.youtube.com/watch?v=diljitNew",
                title = "Kinni Kinni Punjabi Hit",
                artist = "Diljit Dosanjh",
                durationMs = 210_000L,
                kind = OnlineItemKind.TRACK
            ),
            OnlineTrack(
                stableId = 202L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                contentUrl = "https://music.youtube.com/watch?v=passedTrack1",
                title = "Passed Song Punjabi",
                artist = "Random Punjabi Artist",
                durationMs = 200_000L,
                kind = OnlineItemKind.TRACK
            ),
            OnlineTrack(
                stableId = 303L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                contentUrl = "https://music.youtube.com/watch?v=otherHindi",
                title = "Other Hindi Song",
                artist = "Other Singer",
                durationMs = 210_000L,
                kind = OnlineItemKind.TRACK
            )
        )
        val ranked = SonoraPersonalizedRecommendationEngine.rankRecommendations(
            candidates = candidates,
            profile = userTaste
        )
        assertFalse(ranked.any { it.track.stableId == 202L }) // Passed discovery song excluded
        assertEquals(301L, ranked.first().track.stableId) // Chosen artist boosted to #1

        // 2. Verify gradual adaptation: heavy listening history tapers onboarding artist weight
        val heavyHistoryEvents = (1..40).map { idx ->
            ListeningEventEntity(
                songId = 5000L + idx,
                title = "Song $idx",
                artist = "Arijit Singh",
                album = "Bollywood Album",
                genre = "Bollywood",
                isOnline = true,
                providerKey = testProvider.key,
                artworkUrl = null,
                sessionToken = "sess_$idx",
                startedAtMs = 1_700_000_000_000L - idx * 60_000L,
                endedAtMs = 1_700_000_000_000L - idx * 60_000L + 120_000L,
                listenedMs = 120_000L,
                dayEpoch = 19675L,
                isPlayStart = true
            )
        }
        val adaptedTaste = SonoraPersonalizedRecommendationEngine.buildUserTasteProfile(
            likedLocalSongs = emptyList(),
            likedOnlineSongs = emptyList(),
            followedOnlineArtists = emptyList(),
            favouriteLocalArtists = emptyList(),
            localPlaylists = emptyList(),
            localPlaylistSongs = emptyMap(),
            onlinePlaylists = emptyList(),
            listeningEvents = heavyHistoryEvents,
            recentlyPlayedOnline = emptyList(),
            recentlyPlayedLocal = emptyList(),
            interactions = UserMusicInteractionSignal(),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            nowMs = 1_700_000_100_000L,
            musicTasteProfile = tasteProfile
        )
        val initialOnboardingScore = userTaste.onboardingArtistScores["diljit dosanjh"] ?: 0.0
        val adaptedOnboardingScore = adaptedTaste.onboardingArtistScores["diljit dosanjh"] ?: 0.0
        assertTrue(adaptedOnboardingScore < initialOnboardingScore)

        // 3. OnlineRecommendationEngine & Your Mix / Radio reflect taste preferences
        val dailyProfile = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = emptyList(),
            recentlyPlayedOnline = emptyList(),
            favouriteArtists = emptyList(),
            allSongs = emptyList(),
            onlineTracks = emptyList(),
            onlineSearchHistory = emptyList(),
            dailyMixes = emptyList(),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            musicTasteProfile = tasteProfile
        )
        assertEquals("Diljit Dosanjh", dailyProfile.primaryArtistSeed)

        val madeForYou = MusicDiscoveryAndRadioEngine.buildMadeForYouCards(
            recentSongs = emptyList(),
            favoriteSongs = emptyList(),
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.ALL,
            musicTasteProfile = tasteProfile
        )
        assertTrue(madeForYou.first().subtitle.contains("Diljit Dosanjh"))

        val seedSong = OnlineTrackMapper.toUnifiedSong(
            OnlineTrack(
                stableId = 999L,
                serviceId = 0,
                providerKey = testProvider.key,
                providerName = testProvider.displayName,
                title = "Seed Song",
                artist = "Arijit Singh",
                album = "Hindi",
                durationMs = 200_000L,
                contentUrl = "https://music.youtube.com/watch?v=seed"
            )
        )
        val radioCandidates = listOf(
            OnlineTrackMapper.toUnifiedSong(
                OnlineTrack(
                    stableId = 202L,
                    serviceId = 0,
                    providerKey = testProvider.key,
                    providerName = testProvider.displayName,
                    title = "Passed Song Punjabi",
                    artist = "Random Punjabi Artist",
                    album = "Punjabi",
                    durationMs = 200_000L,
                    contentUrl = "https://music.youtube.com/watch?v=passedTrack1"
                )
            ),
            OnlineTrackMapper.toUnifiedSong(
                OnlineTrack(
                    stableId = 888L,
                    serviceId = 0,
                    providerKey = testProvider.key,
                    providerName = testProvider.displayName,
                    title = "Punjabi Hit",
                    artist = "Diljit Dosanjh",
                    album = "Punjabi",
                    durationMs = 200_000L,
                    contentUrl = "https://music.youtube.com/watch?v=diljitRadio"
                )
            )
        )
        val rankedRadio = MusicDiscoveryAndRadioEngine.rankRadioCandidates(
            candidates = radioCandidates,
            seedSong = seedSong,
            session = SongRadioSession(isActive = true, seedSong = seedSong),
            existingQueueIds = emptySet(),
            countryCode = "IN",
            musicTasteProfile = tasteProfile
        )
        assertEquals(listOf(888L), rankedRadio.map { it.id })
    }

    @Test
    fun onboardingFlowUi_stepsAndActionsWorkEndToEnd() {
        val sampleArtist = MusicTasteArtist(id = "shreya", name = "Shreya Ghoshal")
        val sampleTrack = MusicTasteDiscoveryTrack(
            stableId = 777L,
            contentUrl = "https://music.youtube.com/watch?v=song777",
            title = "Sun Raha Hai",
            artist = "Shreya Ghoshal",
            reasonLabel = "Because you picked Shreya Ghoshal"
        )
        val selectedArtists = mutableListOf<MusicTasteArtist>()
        val selectedGenres = mutableListOf<String>()
        val decisions = mutableListOf<MusicTasteSwipeDecision>()
        var completed = false

        composeRule.setContent {
            MusicTasteOnboardingScreen(
                colors = EditorialColors.default(),
                reduceAnimations = true,
                launchMode = MusicTasteEditLaunchMode.FULL_ONBOARDING,
                profile = MusicTasteProfile(
                    selectedArtists = selectedArtists.toList(),
                    selectedGenres = selectedGenres.toList()
                ),
                artistState = MusicTasteArtistDiscoveryUiState(artists = listOf(sampleArtist)),
                discoveryState = MusicTasteDiscoverySessionUiState(tracks = listOf(sampleTrack)),
                previewState = MusicTastePreviewState(),
                onLoadInitialArtists = {},
                onSearchArtists = {},
                onToggleArtist = { selectedArtists.add(it) },
                onClearArtists = { selectedArtists.clear() },
                onToggleGenre = { selectedGenres.add(it) },
                onClearGenres = { selectedGenres.clear() },
                onLoadDiscoverySession = {},
                onDecideTrack = { _, decision -> decisions.add(decision) },
                onTogglePreview = {},
                onStopPreview = {},
                onPreviousTrack = {},
                onNextTrack = {},
                onClearDiscoverySelections = {},
                onSkipOnboarding = {},
                onCompleteOnboarding = { completed = true },
                onDismissEditMode = {}
            )
        }

        // Step 1: Pick artists you love
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pick artists you love").assertIsDisplayed()
        composeRule.onNodeWithTag("music_taste_artist_tile_shreya_ghoshal").performClick()
        assertEquals(1, selectedArtists.size)
        composeRule.onNodeWithTag("music_taste_artists_continue_button").performClick()

        // Step 2: What do you listen to?
        composeRule.waitForIdle()
        composeRule.onNodeWithText("What do you listen to?").assertIsDisplayed()
        composeRule.onNodeWithTag("music_taste_genre_chip_bollywood").performScrollTo().performClick()
        composeRule.onNodeWithTag("music_taste_genre_chip_odia").performScrollTo().performClick()
        assertEquals(listOf("Bollywood", "Odia"), selectedGenres)
        composeRule.onNodeWithTag("music_taste_genres_continue_button").performClick()

        // Step 3: Yes or no?
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Yes or no?").assertIsDisplayed()
        composeRule.onNodeWithText("1 of 1").assertIsDisplayed()
        composeRule.onNodeWithText("Because you picked Shreya Ghoshal").assertIsDisplayed()
        composeRule.onNodeWithTag("music_taste_discover_liked_songs_button").performClick()
        assertEquals(listOf(MusicTasteSwipeDecision.ADD_TO_LIKED_SONGS), decisions)
        composeRule.onNodeWithTag("music_taste_discover_finish_button").performClick()

        // Welcome / Finish screen
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your music journey starts here 🎵").assertIsDisplayed()
        composeRule.onNodeWithTag("music_taste_start_listening_button").performClick()
        assertTrue(completed)
    }

    @Test
    fun profileMusicTasteScreen_displaysPreferencesAndConfirmsReset() {
        var resetCalled = false
        var editArtistsCalled = false
        var editGenresCalled = false
        var restartDiscoveryCalled = false

        val profile = MusicTasteProfile(
            selectedArtists = listOf(MusicTasteArtist(id = "arijit", name = "Arijit Singh")),
            selectedGenres = listOf("Bollywood", "Lo-fi"),
            likedDiscoveryTracks = listOf(
                MusicTasteDiscoveryTrack(
                    stableId = 1L,
                    contentUrl = "https://music.youtube.com/watch?v=1",
                    title = "Kesariya",
                    artist = "Arijit Singh"
                )
            ),
            onboardingCompleted = true
        )

        composeRule.setContent {
            MusicTasteScreen(
                profile = profile,
                colors = EditorialColors.default(),
                onBack = {},
                onEditArtists = { editArtistsCalled = true },
                onEditGenres = { editGenresCalled = true },
                onRestartSongDiscovery = { restartDiscoveryCalled = true },
                onRemoveArtist = {},
                onRemoveGenre = {},
                onRemoveLikedTrack = {},
                onRemovePassedTrack = {},
                onResetMusicTaste = { resetCalled = true }
            )
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("Music Taste").assertIsDisplayed()
        composeRule.onNodeWithTag("music_taste_edit_artists_button").performClick()
        composeRule.onNodeWithTag("music_taste_edit_genres_button").performClick()
        composeRule.onNodeWithTag("music_taste_restart_discovery_button").performClick()
        assertTrue(editArtistsCalled)
        assertTrue(editGenresCalled)
        assertTrue(restartDiscoveryCalled)
        assertFalse(resetCalled)
    }
}
