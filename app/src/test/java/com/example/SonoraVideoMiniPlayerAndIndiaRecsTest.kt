package com.example

import android.app.Application
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.model.Artist
import com.example.model.Song
import data.online.MusicDiscoveryAndRadioEngine
import data.online.SongRadioSession
import data.preferences.OnlineRegionPreferences
import data.preferences.VideoExperienceSettings
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineHomeMixBuilder
import domain.online.OnlineItemKind
import domain.online.OnlineRecommendationEngine
import domain.online.OnlineTrack
import domain.online.RegionMusicEligibilityFilter
import domain.online.StreamDeliveryType
import domain.player.MusicMiniPlayerVisibilityPolicy
import domain.video.OnlineVideoItem
import domain.video.VideoAdaptiveQualitySelector
import domain.video.VideoAspectRatioCalculator
import domain.video.VideoPlayerUiState
import domain.video.VideoStreamOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraVideoMiniPlayerAndIndiaRecsTest {

    // =========================================================================
    // PART 1: MUSIC MINI PLAYER HIDING IN ONLINE VIDEO & COMPLETE SWIPE DISMISS
    // =========================================================================

    @Test
    fun scenario1_musicMiniPlayerCompletelyHiddenAcrossAllOnlineVideoSurfaces() {
        // 1. Online Video Home (tab 0)
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
                currentTab = 0,
                homeMusicMode = HomeMusicMode.ONLINE_VIDEO,
                librarySectionMode = LibrarySectionMode.OFFLINE,
                isVideoPlayerExpanded = false,
                isVideoMiniPlayerVisible = false,
                isChannelDetailOpen = false
            )
        )
        assertFalse(
            MusicMiniPlayerVisibilityPolicy.shouldShowMusicMiniPlayer(
                hasCurrentSong = true,
                isInOnlineVideoExperience = true
            )
        )

        // 2. Search screen opened while Home is in Online Video mode (tab 1)
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
                currentTab = 1,
                homeMusicMode = HomeMusicMode.ONLINE_VIDEO,
                librarySectionMode = LibrarySectionMode.OFFLINE,
                isVideoPlayerExpanded = false,
                isVideoMiniPlayerVisible = false,
                isChannelDetailOpen = false
            )
        )

        // 3. Online Video Library (tab 2)
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
                currentTab = 2,
                homeMusicMode = HomeMusicMode.OFFLINE,
                librarySectionMode = LibrarySectionMode.ONLINE_VIDEO,
                isVideoPlayerExpanded = false,
                isVideoMiniPlayerVisible = false,
                isChannelDetailOpen = false
            )
        )

        // 4. Video Detail / Fullscreen / Shorts expanded
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
                currentTab = 0,
                homeMusicMode = HomeMusicMode.OFFLINE,
                librarySectionMode = LibrarySectionMode.OFFLINE,
                isVideoPlayerExpanded = true,
                isVideoMiniPlayerVisible = false,
                isChannelDetailOpen = false
            )
        )

        // 5. In-app Video Mini Player active
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
                currentTab = 0,
                homeMusicMode = HomeMusicMode.ONLINE,
                librarySectionMode = LibrarySectionMode.OFFLINE,
                isVideoPlayerExpanded = false,
                isVideoMiniPlayerVisible = true,
                isChannelDetailOpen = false
            )
        )

        // 6. Channel Detail screen open
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
                currentTab = 0,
                homeMusicMode = HomeMusicMode.ONLINE,
                librarySectionMode = LibrarySectionMode.OFFLINE,
                isVideoPlayerExpanded = false,
                isVideoMiniPlayerVisible = false,
                isChannelDetailOpen = true
            )
        )
    }

    @Test
    fun scenario2_noReservedBottomSpaceWhenMiniPlayerHiddenOrDismissed() {
        // Hidden / dismissed: compact bottom padding (only bottom nav clearance, zero mini-player gap)
        assertEquals(
            104.dp,
            MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                isMusicMiniPlayerVisible = false,
                isVideoMiniPlayerVisible = false
            )
        )
        assertEquals(
            84.dp,
            MusicMiniPlayerVisibilityPolicy.floatingBannerBottomPadding(
                showBottomChrome = true,
                isMusicMiniPlayerVisible = false,
                isVideoMiniPlayerVisible = false
            )
        )

        // Visible music mini player OR visible video mini player: reserves clearance
        assertEquals(
            184.dp,
            MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                isMusicMiniPlayerVisible = true,
                isVideoMiniPlayerVisible = false
            )
        )
        assertEquals(
            184.dp,
            MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                isMusicMiniPlayerVisible = false,
                isVideoMiniPlayerVisible = true
            )
        )
        assertEquals(
            144.dp,
            MusicMiniPlayerVisibilityPolicy.floatingBannerBottomPadding(
                showBottomChrome = true,
                isMusicMiniPlayerVisible = true,
                isVideoMiniPlayerVisible = false
            )
        )
    }

    @Test
    fun scenario3And4And5_musicMiniPlayerVisibleInMusicModesAndRestoredOnReturnFromOnlineVideo() {
        // Music mode (Offline or Online) with active song -> visible
        val inMusicMode = MusicMiniPlayerVisibilityPolicy.isInOnlineVideoExperience(
            currentTab = 0,
            homeMusicMode = HomeMusicMode.ONLINE,
            librarySectionMode = LibrarySectionMode.OFFLINE,
            isVideoPlayerExpanded = false,
            isVideoMiniPlayerVisible = false,
            isChannelDetailOpen = false
        )
        assertFalse(inMusicMode)
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.shouldShowMusicMiniPlayer(
                hasCurrentSong = true,
                isInOnlineVideoExperience = inMusicMode
            )
        )

        // No active song -> hidden
        assertFalse(
            MusicMiniPlayerVisibilityPolicy.shouldShowMusicMiniPlayer(
                hasCurrentSong = false,
                isInOnlineVideoExperience = inMusicMode
            )
        )

        // Switch Online Video -> back to Music: mini player is visible again when returning to Music mode
        assertFalse(
            MusicMiniPlayerVisibilityPolicy.shouldShowMusicMiniPlayer(
                hasCurrentSong = true,
                isInOnlineVideoExperience = true
            )
        )
        assertTrue(
            MusicMiniPlayerVisibilityPolicy.shouldShowMusicMiniPlayer(
                hasCurrentSong = true,
                isInOnlineVideoExperience = false
            )
        )
    }

    // =========================================================================
    // PART 2: PRESERVE ORIGINAL ONLINE VIDEO QUALITY & ADAPTIVE/MANUAL SELECTION
    // =========================================================================

    @Test
    fun scenario6_autoBestAvailablePreservesOriginalQualityWithoutArtificialMobileOrWifiCaps() {
        val defaultSettings = VideoExperienceSettings()
        assertTrue(defaultSettings.autoVideoQualityEnabled)
        assertTrue(defaultSettings.allowHighQualityOnMobileData)
        assertEquals(2160, defaultSettings.preferredWifiQualityHeight)

        val streams = listOf(
            streamOption("360_muxed", 360, 640, 30, 500, isVideoOnly = false, codec = "avc1.42001E"),
            streamOption("720_muxed", 720, 1280, 30, 1400, isVideoOnly = false, codec = "avc1.64001F"),
            streamOption("720_vp9_60", 720, 1280, 60, 2800, isVideoOnly = true, codec = "vp09.00.31.08"),
            streamOption("1080_h264", 1080, 1920, 30, 3200, isVideoOnly = true, codec = "avc1.640028"),
            streamOption("1080_vp9_60", 1080, 1920, 60, 5800, isVideoOnly = true, codec = "vp09.00.41.08"),
            streamOption("1440_vp9_60", 1440, 2560, 60, 11500, isVideoOnly = true, codec = "vp09.00.50.08"),
            streamOption("2160_vp9_60", 2160, 3840, 60, 22000, isVideoOnly = true, codec = "vp09.00.51.08")
        )

        // Cold-start on Wi-Fi with conservative initial bandwidth estimate (1.5 Mbps) must NOT downgrade startup
        val startupWifi = VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = streams,
            settings = defaultSettings,
            isMeteredMobile = false,
            estimatedBandwidthBps = 1_500_000L,
            bufferedAheadMs = 0L,
            rebufferCount = 0,
            deviceMaxHeightPx = 2160,
            isInitialStartup = true
        )
        assertTrue(startupWifi.isAutoMode)
        assertEquals(2160, startupWifi.selectedStream?.heightPx)
        assertEquals("2160_vp9_60", startupWifi.selectedStream?.id)

        // Mobile data (without Data Saver) must NOT silently cap to 480p
        val mobileHighQuality = VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = streams,
            settings = defaultSettings,
            isMeteredMobile = true,
            estimatedBandwidthBps = 18_000_000L,
            bufferedAheadMs = 12_000L,
            rebufferCount = 0,
            deviceMaxHeightPx = 1080,
            isInitialStartup = false
        )
        assertEquals(1080, mobileHighQuality.selectedStream?.heightPx)
        // Must pick higher-bitrate 60fps VP9 adaptive over lower-bitrate 30fps H.264
        assertEquals("1080_vp9_60", mobileHighQuality.selectedStream?.id)

        // Genuine bandwidth constraint + rebuffer stalls steps down gracefully
        val stalledStepDown = VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = streams,
            settings = defaultSettings,
            isMeteredMobile = true,
            estimatedBandwidthBps = 1_800_000L,
            bufferedAheadMs = 1_500L,
            rebufferCount = 2,
            deviceMaxHeightPx = 2160,
            isInitialStartup = false
        )
        assertTrue(stalledStepDown.selectedStream!!.heightPx <= 480)

        // Hysteresis allows upward recovery once stable
        assertTrue(
            VideoAdaptiveQualitySelector.shouldSwitchQualityWithHysteresis(
                currentHeightPx = 480,
                candidateHeightPx = 1080,
                elapsedSinceLastSwitchMs = 18_000L,
                bufferedAheadMs = 10_000L,
                rebufferCount = 0,
                networkTypeChanged = false
            )
        )
    }

    @Test
    fun scenario7_manualQualityAndRealPlaybackInfoNeverInventUnavailableResolutions() {
        // Provider only supplies up to 480p -> menu must NOT invent 720p/1080p
        val sdOnlyStreams = listOf(
            streamOption("144", 144, 256, 30, 120, isVideoOnly = true, codec = "avc1"),
            streamOption("360", 360, 640, 30, 450, isVideoOnly = false, codec = "avc1"),
            streamOption("480_low", 480, 854, 30, 600, isVideoOnly = true, codec = "avc1"),
            streamOption("480_high", 480, 854, 30, 1100, isVideoOnly = true, codec = "vp09")
        )
        val distinct = VideoAdaptiveQualitySelector.distinctAvailableQualityOptions(sdOnlyStreams)
        assertEquals(listOf(480, 360, 144), distinct.map { it.heightPx })
        assertEquals("480_high", distinct.first().id)

        // Auto mode on 480p-max video reports actual 480p (never falsely claims 1080p)
        val decision = VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = sdOnlyStreams,
            settings = VideoExperienceSettings(),
            isMeteredMobile = false,
            estimatedBandwidthBps = 25_000_000L,
            bufferedAheadMs = 10_000L,
            rebufferCount = 0,
            deviceMaxHeightPx = 2160,
            isInitialStartup = true
        )
        assertEquals(480, decision.selectedStream?.heightPx)
        assertTrue(decision.reasonLabel.contains("480p"))
        assertFalse(decision.reasonLabel.contains("1080p"))

        // Manual 360p selection honored cleanly
        val manualDecision = VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = sdOnlyStreams,
            settings = VideoExperienceSettings().copy(
                autoVideoQualityEnabled = false,
                lastSelectedVideoQualityHeight = 360
            ),
            isMeteredMobile = false,
            estimatedBandwidthBps = 25_000_000L,
            bufferedAheadMs = 10_000L,
            rebufferCount = 0
        )
        assertFalse(manualDecision.isAutoMode)
        assertEquals(360, manualDecision.selectedStream?.heightPx)
        assertTrue(manualDecision.reasonLabel.startsWith("Manual"))

        // VideoPlayerUiState reflects real decoded resolution & format
        val uiState = VideoPlayerUiState(
            activeVideo = sampleVideo("vid_480"),
            resolvedDetails = domain.video.ResolvedVideoDetails(
                video = sampleVideo("vid_480"),
                videoStreams = distinct
            ),
            selectedVideoStream = decision.selectedStream,
            videoWidth = 854,
            videoHeight = 480,
            actualVideoCodec = "vp09.00.30.08",
            actualVideoBitrateKbps = 1100,
            actualVideoFps = 30f,
            isAutoQualityMode = true
        )
        assertEquals(480, uiState.activePlaybackHeightPx)
        assertEquals("480p (854×480)", uiState.actualResolutionDisplay)
        assertTrue(uiState.actualFormatDisplay.contains("VP9"))
        assertTrue(uiState.actualFormatDisplay.contains("Adaptive Video + Audio"))
    }

    @Test
    fun scenario8And9_nativeAspectRatioPixelRatioRotationAndMiniPlayerStatePreservation() {
        // 16:9 standard video
        val ratio16x9 = VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = 1920,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
            unappliedRotationDegrees = 0
        )
        assertEquals(16f / 9f, ratio16x9, 0.005f)

        // Anamorphic pixelWidthHeightRatio preserved
        val anamorphic = VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = 1440,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1.3333f,
            unappliedRotationDegrees = 0
        )
        assertEquals(16f / 9f, anamorphic, 0.01f)

        // 90-degree rotated portrait Short preserves vertical dimensions
        val rotated = VideoAspectRatioCalculator.resolveEffectiveDimensions(
            videoWidth = 1920,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
            unappliedRotationDegrees = 90
        )
        assertEquals(1080f, rotated.first, 0.01f)
        assertEquals(1920f, rotated.second, 0.01f)

        // Minimizing to in-app mini player or entering PiP preserves active video stream without degrading quality
        val hdStream = streamOption("1080_vp9", 1080, 1920, 60, 5600, isVideoOnly = true, codec = "vp09.00.41.08")
        val expandedState = VideoPlayerUiState(
            activeVideo = sampleVideo("vid_hd"),
            isPlayerExpanded = true,
            isInPictureInPicture = false,
            selectedVideoStream = hdStream,
            videoWidth = 1920,
            videoHeight = 1080,
            actualVideoCodec = "vp09.00.41.08"
        )
        val miniState = expandedState.copy(isPlayerExpanded = false)
        val pipState = expandedState.copy(isInPictureInPicture = true)
        assertTrue(miniState.hasActiveVideo && !miniState.isPlayerExpanded)
        assertEquals(1080, miniState.activePlaybackHeightPx)
        assertEquals(hdStream.id, miniState.selectedVideoStream?.id)
        assertEquals(1080, pipState.activePlaybackHeightPx)
        assertEquals(hdStream.id, pipState.selectedVideoStream?.id)
    }

    // =========================================================================
    // PART 3: STRICT INDIA MUSIC RECOMMENDATIONS & MULTI-REGION SUPPORT
    // =========================================================================

    @Test
    fun scenario10_strictIndiaAcceptsAllMajorIndianLanguagesAndIndianArtistsSingingInEnglish() {
        val verifiedIndianTracks = listOf(
            // Hindi
            onlineTrack("Kesariya", "Arijit Singh", "Brahmastra"),
            // Odia
            onlineTrack("Tu Mo Love Story", "Humane Sagar", "Tarang Music"),
            onlineTrack("ତୁ ମୋ ଜୀବନ", "Diptirekha Padhi"),
            // Bengali
            onlineTrack("Amake Amar Moto Thakte Dao", "Anupam Roy", "SVF Music"),
            // Punjabi
            onlineTrack("Lover", "Diljit Dosanjh"),
            // Tamil
            onlineTrack("Why This Kolaveri Di", "Anirudh Ravichander", "Sony Music South"),
            // Telugu
            onlineTrack("Naatu Naatu", "M.M. Keeravani", "Lahari Music"),
            // Malayalam
            onlineTrack("Illuminati", "Sushin Shyam", "Think Music India"),
            // Kannada
            onlineTrack("Belageddu", "Vijay Prakash", "Anand Audio"),
            // Marathi
            onlineTrack("Zingaat", "Ajay-Atul", "Zee Music Marathi"),
            // Assamese
            onlineTrack("Mayabini", "Zubeen Garg"),
            // Gujarati
            onlineTrack("Khalasi", "Aditya Gadhvi", "Coke Studio Bharat"),
            // English-language song by verified Indian artist
            onlineTrack("Cold/Mess", "Prateek Kuhad"),
            onlineTrack("Big Dawgs", "Hanumankind", "Def Jam India"),
            onlineTrack("Kasoor (English Acoustic)", "When Chai Met Toast")
        )

        verifiedIndianTracks.forEach { track ->
            assertTrue(
                "Expected verified Indian track to pass in IN mode: ${track.title} - ${track.artist}",
                RegionMusicEligibilityFilter.isVerifiedIndianTrack(track)
            )
        }

        val filtered = RegionMusicEligibilityFilter.filterTracksForRegion(verifiedIndianTracks, "IN")
        assertEquals(verifiedIndianTracks.size, filtered.size)
    }

    @Test
    fun scenario11_strictIndiaExcludesForeignAndUnverifiedTracksWithZeroForeignBackfill() {
        val nonEligibleTracks = listOf(
            // Western pop/hip-hop/rock
            onlineTrack("Anti-Hero", "Taylor Swift"),
            onlineTrack("Blinding Lights", "The Weeknd"),
            onlineTrack("God's Plan", "Drake"),
            onlineTrack("Shape of You", "Ed Sheeran"),
            // K-pop
            onlineTrack("Dynamite", "BTS", "HYBE LABELS"),
            onlineTrack("How You Like That", "BLACKPINK"),
            onlineTrack("강남스타일", "PSY"),
            // J-pop
            onlineTrack("Idol", "YOASOBI"),
            onlineTrack("夜に駆ける", "YOASOBI"),
            // Latin / Afrobeats / Arabic
            onlineTrack("Tití Me Preguntó", "Bad Bunny"),
            onlineTrack("Calm Down", "Rema"),
            onlineTrack("Tamally Maak", "Amr Diab"),
            // Unknown / unverified origin (MUST be excluded, never assumed Indian)
            onlineTrack("Midnight Echoes", "Random Indie Band 404"),
            onlineTrack("Sunset Boulevard", "Unknown Creator"),
            onlineTrack("Lo-Fi Study Beats 2026", "Chill Stream Hub")
        )

        nonEligibleTracks.forEach { track ->
            assertFalse(
                "Expected foreign/unverified track to be EXCLUDED in IN mode: ${track.title} - ${track.artist}",
                RegionMusicEligibilityFilter.isVerifiedIndianTrack(track)
            )
        }

        // Filtering an all-foreign/unverified batch MUST return empty list (never backfill)
        val filtered = RegionMusicEligibilityFilter.filterTracksForRegion(nonEligibleTracks, "IN")
        assertTrue("India mode must NEVER backfill with foreign tracks", filtered.isEmpty())
    }

    @Test
    fun scenario12_strictIndiaEnforcedAcrossHomeMixDailyMixDiscoverWeeklyRadioAndRecommendationEngine() {
        val indianTrack1 = onlineTrack("Tum Hi Ho", "Arijit Singh", "Aashiqui 2")
        val indianTrack2 = onlineTrack("Nainowale Ne", "Neeti Mohan", "Padmaavat")
        val indianTrack3 = onlineTrack("Hrudayara Eyi Sunyata", "Humane Sagar", "Odia Hits")
        val foreignTrack1 = onlineTrack("Cruel Summer", "Taylor Swift")
        val foreignTrack2 = onlineTrack("Butter", "BTS")
        val unverifiedTrack = onlineTrack("Neon Drift", "Synthwave Collective")

        val mixedSections: Map<DiscoverSectionType, DiscoverSectionState> = mapOf(
            DiscoverSectionType.MUSIC_RECOMMENDATIONS to DiscoverSectionState.Success(
                items = listOf(indianTrack1, foreignTrack1, unverifiedTrack)
            ),
            DiscoverSectionType.TRENDING_MUSIC to DiscoverSectionState.Success(
                items = listOf(foreignTrack2, indianTrack2)
            ),
            DiscoverSectionType.POPULAR_SONGS to DiscoverSectionState.Success(
                items = listOf(foreignTrack1, indianTrack3)
            ),
            DiscoverSectionType.NEW_RELEASES to DiscoverSectionState.Success(
                items = listOf(foreignTrack1, foreignTrack2) // All foreign -> must produce NO foreign mix
            )
        )

        val foreignSong = testSong(1L, "Cruel Summer", "Taylor Swift")
        val indianSong = testSong(2L, "Agar Tum Saath Ho", "Arijit Singh", genre = "Bollywood")
        val indianSong2 = testSong(3L, "Vande Mataram", "A.R. Rahman", genre = "Indian")
        val unverifiedSong = testSong(4L, "Ambient Horizon", "Mystery Producer")

        // 1. OnlineRecommendationEngine must NOT use foreign artist or foreign search seed in IN mode
        val indiaProfile = OnlineRecommendationEngine.buildDailyRecommendationProfile(
            recentlyPlayedLocal = listOf(foreignSong),
            recentlyPlayedOnline = listOf(foreignSong, unverifiedSong),
            favouriteArtists = listOf(Artist(id = 1L, name = "Taylor Swift", songCount = 10, albumCount = 2)),
            allSongs = listOf(foreignSong),
            onlineTracks = listOf(foreignSong),
            onlineSearchHistory = listOf("Taylor Swift", "BTS"),
            dailyMixes = emptyList(),
            countryCode = "IN",
            languageCode = "en",
            discoverLanguage = DiscoverMusicLanguage.ALL
        )
        assertFalse(indiaProfile.seedQuery.contains("Taylor Swift", ignoreCase = true))
        assertFalse(indiaProfile.seedQuery.contains("BTS", ignoreCase = true))
        assertTrue(
            indiaProfile.seedQuery.contains("Indian", ignoreCase = true) ||
                indiaProfile.seedQuery.contains("Hindi", ignoreCase = true) ||
                indiaProfile.seedQuery.contains("Bollywood", ignoreCase = true)
        )

        // 2. OnlineHomeMixBuilder ("Your Mix" + "Daily Mixes") strictly filters out foreign/unverified items
        val yourMix = OnlineHomeMixBuilder.buildOnlineYourMix(
            discoverSections = mixedSections,
            recentlyPlayedOnline = listOf(foreignSong, indianSong),
            savedOnlineTracks = listOf(unverifiedSong),
            recommendationProfile = indiaProfile,
            countryCode = "IN"
        )
        assertTrue(yourMix.songs.isNotEmpty())
        assertTrue(yourMix.songs.all { RegionMusicEligibilityFilter.isVerifiedIndianSong(it) })
        assertTrue(yourMix.tracks.all { RegionMusicEligibilityFilter.isVerifiedIndianTrack(it) })

        val dailyMixes = OnlineHomeMixBuilder.buildOnlineDailyMixes(
            discoverSections = mixedSections,
            recentlyPlayedOnline = listOf(foreignSong, indianSong),
            savedOnlineTracks = listOf(unverifiedSong),
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.ALL
        )
        assertTrue(dailyMixes.isNotEmpty())
        dailyMixes.forEach { mix ->
            assertTrue(
                "Mix ${mix.title} leaked non-Indian song!",
                mix.songs.all { RegionMusicEligibilityFilter.isVerifiedIndianSong(it) }
            )
        }
        // NEW_RELEASES had only foreign tracks, so Fresh Drops mix must NOT exist
        assertFalse(dailyMixes.any { it.id == "online_daily_mix_4" })

        // 3. MusicDiscoveryAndRadioEngine (Made For You, Based on Listening, Song Radio, Up Next)
        val madeForYou = MusicDiscoveryAndRadioEngine.buildMadeForYouCards(
            recentSongs = listOf(foreignSong, indianSong),
            favoriteSongs = listOf(unverifiedSong),
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.ALL
        )
        madeForYou.forEach { card ->
            assertFalse(card.query.contains("Taylor Swift", ignoreCase = true))
            assertTrue(card.previewSongs.all { RegionMusicEligibilityFilter.isVerifiedIndianSong(it) })
        }

        val basedOnListening = MusicDiscoveryAndRadioEngine.buildBasedOnListeningCards(
            recentSongs = listOf(foreignSong, indianSong),
            favoriteSongs = emptyList(),
            countryCode = "IN"
        )
        basedOnListening.forEach { card ->
            assertFalse(card.title.contains("Cruel Summer", ignoreCase = true))
            assertFalse(card.query.contains("Taylor Swift", ignoreCase = true))
        }

        // Song Radio / Up Next ranking strictly removes foreign & unverified candidates even if seeded from foreign song
        val radioQuery = MusicDiscoveryAndRadioEngine.buildRelatedSongsQuery(
            seedSong = foreignSong,
            session = SongRadioSession(isActive = true, seedSong = foreignSong),
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.ALL
        )
        assertFalse(radioQuery.contains("Taylor Swift", ignoreCase = true))
        assertTrue(radioQuery.contains("Indian", ignoreCase = true))

        val rankedRadio = MusicDiscoveryAndRadioEngine.rankRadioCandidates(
            candidates = listOf(foreignSong.copy(id = 10L), unverifiedSong.copy(id = 11L), indianSong, indianSong2),
            seedSong = foreignSong,
            session = SongRadioSession(isActive = true, seedSong = foreignSong),
            existingQueueIds = emptySet(),
            limit = 10,
            countryCode = "IN"
        )
        assertEquals(2, rankedRadio.size)
        assertTrue(rankedRadio.all { RegionMusicEligibilityFilter.isVerifiedIndianSong(it) })

        // Search suggestions in India filter foreign recommendations while honoring explicit foreign searches
        val genericSuggestions = RegionMusicEligibilityFilter.filterSuggestionsForRegion(
            suggestions = listOf("top songs hindi", "Taylor Swift hits", "BTS dynamite", "Arijit Singh love songs"),
            userTypedQuery = "top songs",
            countryCode = "IN"
        )
        assertFalse(genericSuggestions.any { it.contains("Taylor Swift") || it.contains("BTS") })
        assertTrue(genericSuggestions.any { it.contains("Arijit Singh") })
    }

    @Test
    fun scenario13_regionSwitchingUpdatesEligibilityAndPersistsIndependentlyFromLanguage() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val regionPrefs = OnlineRegionPreferences(app)

        // Switch country to US while keeping language Hindi -> country & language remain independent
        val savedUs = regionPrefs.setContentCountry("US")
        val savedHi = regionPrefs.setContentLanguage("hi")
        assertEquals("US", savedUs)
        assertEquals("hi", savedHi)
        assertEquals("US", regionPrefs.contentCountryFlow.first())
        assertEquals("hi", regionPrefs.contentLanguageFlow.first())

        val usTrack = onlineTrack("Blinding Lights", "The Weeknd")
        val inTrack = onlineTrack("Kesariya", "Arijit Singh")
        val pool = listOf(usTrack, inTrack)

        // In US mode, non-Indian tracks are allowed (India is NOT hardcoded)
        val usFiltered = RegionMusicEligibilityFilter.filterTracksForRegion(pool, "US")
        assertEquals(2, usFiltered.size)

        // Switch back to India -> persists cleanly and immediately re-enforces strict India eligibility
        val savedIn = regionPrefs.setContentCountry("IN")
        assertEquals("IN", savedIn)
        assertEquals("IN", regionPrefs.contentCountryFlow.first())

        val inFiltered = RegionMusicEligibilityFilter.filterTracksForRegion(pool, "IN")
        assertEquals(1, inFiltered.size)
        assertEquals("Kesariya", inFiltered.first().title)

        // Country-safe fallback query for US does not hardcode India
        val usFallback = RegionMusicEligibilityFilter.regionSafeFallbackQuery(
            sectionType = DiscoverSectionType.TRENDING_MUSIC,
            countryCode = "US"
        )
        assertNotNull(usFallback)
        assertTrue(usFallback!!.contains("US"))
        assertFalse(usFallback.contains("India"))
    }

    // --- Test fixture helpers ---

    private fun streamOption(
        id: String,
        heightPx: Int,
        widthPx: Int,
        fps: Int,
        bitrateKbps: Int,
        isVideoOnly: Boolean,
        codec: String
    ): VideoStreamOption {
        return VideoStreamOption(
            id = id,
            url = "https://example.invalid/video/$id.mp4",
            resolutionLabel = "${heightPx}p",
            heightPx = heightPx,
            widthPx = widthPx,
            fps = fps,
            mimeType = if (codec.startsWith("vp")) "video/webm" else "video/mp4",
            formatLabel = if (codec.startsWith("vp")) "WEBM" else "MP4",
            isVideoOnly = isVideoOnly,
            bitrateKbps = bitrateKbps,
            codec = codec,
            deliveryType = if (isVideoOnly) StreamDeliveryType.DASH else StreamDeliveryType.PROGRESSIVE_HTTP
        )
    }

    private fun sampleVideo(id: String): OnlineVideoItem {
        return OnlineVideoItem(
            videoId = id,
            contentUrl = "https://www.youtube.com/watch?v=$id",
            title = "Sample Video $id",
            channelName = "Test Channel",
            durationSeconds = 240L
        )
    }

    private fun onlineTrack(title: String, artist: String, album: String = ""): OnlineTrack {
        val url = "https://music.youtube.com/watch?v=${title.hashCode()}_${artist.hashCode()}"
        return OnlineTrack(
            stableId = url.hashCode().toLong(),
            providerKey = "youtube_music",
            providerName = "YT Music",
            serviceId = 0,
            contentUrl = url,
            title = title,
            artist = artist,
            album = album,
            kind = OnlineItemKind.TRACK
        )
    }

    private fun testSong(
        id: Long,
        title: String,
        artist: String,
        album: String = "",
        genre: String = ""
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = 0L,
            durationMs = 210_000L,
            uri = Uri.parse("https://music.youtube.com/watch?v=$id"),
            dataPath = "",
            mimeType = "audio/mpeg",
            sizeBytes = 0L,
            genre = genre,
            isOnline = true,
            contentUrl = "https://music.youtube.com/watch?v=$id"
        )
    }
}
