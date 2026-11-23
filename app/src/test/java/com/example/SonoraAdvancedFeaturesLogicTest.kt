package com.example

import android.net.Uri
import com.example.model.Song
import data.artwork.AlbumArtworkQualityManager
import data.artwork.ArtworkDisplayRole
import data.artwork.ArtworkResolutionTier
import data.download.VideoDownloadEligibility
import data.download.VideoDownloadEligibilityEvaluator
import data.online.IndiaMusicCategory
import data.online.MusicDiscoveryAndRadioEngine
import data.online.MusicDiscoveryBadge
import data.online.MusicMoodActivity
import data.online.SongRadioSession
import data.preferences.AlbumArtworkQualityMode
import data.preferences.VideoExperienceSettings
import data.video.VideoFeedbackType
import data.video.VideoRecommendationEngine
import data.video.VideoRecommendationFeedbackEntity
import data.video.VideoRecommendationSignals
import domain.online.DiscoverMusicLanguage
import domain.online.StreamDeliveryType
import domain.video.FullscreenSwipeDecision
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.ResolvedVideoDetails
import domain.video.VideoAspectRatioCalculator
import domain.video.VideoAspectRatioMode
import domain.video.VideoAudioOption
import domain.video.VideoGestureClassifier
import domain.video.VideoHomeSectionType
import domain.video.VideoPinchZoomMath
import domain.video.VideoPinchZoomState
import domain.video.VideoSectionState
import domain.video.VideoStreamOption
import domain.video.VideoTouchZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class SonoraAdvancedFeaturesLogicTest {

    private fun sampleStream(
        height: Int,
        isVideoOnly: Boolean = false,
        deliveryType: StreamDeliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
        mime: String = "video/mp4",
        url: String = "https://cdn.example.com/v_${height}p.mp4"
    ): VideoStreamOption = VideoStreamOption(
        id = "s_${height}_${if (isVideoOnly) "vo" else "mx"}",
        url = url,
        resolutionLabel = "${height}p",
        heightPx = height,
        fps = 30,
        mimeType = mime,
        formatLabel = "MP4",
        isVideoOnly = isVideoOnly,
        bitrateKbps = height * 4,
        deliveryType = deliveryType
    )

    private fun sampleVideo(
        id: String,
        title: String,
        channel: String,
        views: Long = 50_000L,
        durationSec: Long = 300L,
        lastPosMs: Long = 0L,
        isLive: Boolean = false
    ): OnlineVideoItem = OnlineVideoItem(
        videoId = id,
        contentUrl = "https://www.youtube.com/watch?v=$id",
        title = title,
        channelName = channel,
        channelUrl = "https://www.youtube.com/@${channel.lowercase().replace(" ", "")}",
        thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
        durationSeconds = durationSec,
        viewCount = views,
        isLive = isLive,
        lastPositionMs = lastPosMs
    )

    private fun sampleSong(
        id: Long,
        title: String,
        artist: String,
        album: String = "Album $id",
        genre: String = "Pop"
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = id,
        durationMs = 210_000L,
        uri = Uri.EMPTY,
        dataPath = "",
        mimeType = "audio/mp4",
        sizeBytes = 0L,
        genre = genre
    )

    // --- PART 1: Adaptive Quality, Pinch-to-Zoom, Aspect Ratios, Swipe-Down Mini Player, Downloads ---

    @Test
    fun `adaptive quality selector adapts to Wi-Fi, mobile data saver, rebuffers, and never invents tiers`() {
        val available = listOf(
            sampleStream(144),
            sampleStream(360),
            sampleStream(480),
            sampleStream(720),
            sampleStream(1080)
        )

        // Wi-Fi with strong bandwidth picks 1080p
        val wifiDecision = domain.video.VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = available,
            settings = VideoExperienceSettings(autoVideoQualityEnabled = true, preferHighestQualityOnWifi = true),
            isMeteredMobile = false,
            estimatedBandwidthBps = 8_500_000L,
            bufferedAheadMs = 15_000L,
            rebufferCount = 0
        )
        assertTrue(wifiDecision.isAutoMode)
        assertEquals(1080, wifiDecision.selectedStream?.heightPx)

        // Mobile Data Saver caps at 360p even with high bandwidth
        val saverDecision = domain.video.VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = available,
            settings = VideoExperienceSettings(autoVideoQualityEnabled = true, mobileDataSaverEnabled = true),
            isMeteredMobile = true,
            estimatedBandwidthBps = 10_000_000L,
            bufferedAheadMs = 12_000L,
            rebufferCount = 0
        )
        assertEquals(360, saverDecision.selectedStream?.heightPx)

        // Allow HQ on Mobile Data permits 1080p when bandwidth supports it
        val hqMobileDecision = domain.video.VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = available,
            settings = VideoExperienceSettings(
                autoVideoQualityEnabled = true,
                mobileDataSaverEnabled = false,
                allowHighQualityOnMobileData = true
            ),
            isMeteredMobile = true,
            estimatedBandwidthBps = 8_000_000L,
            bufferedAheadMs = 10_000L,
            rebufferCount = 0
        )
        assertEquals(1080, hqMobileDecision.selectedStream?.heightPx)

        // Rebuffer stalls step down quality smoothly
        val rebufferDecision = domain.video.VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = available,
            settings = VideoExperienceSettings(autoVideoQualityEnabled = true),
            isMeteredMobile = false,
            estimatedBandwidthBps = 8_000_000L,
            bufferedAheadMs = 1_500L,
            rebufferCount = 2
        )
        assertEquals(720, rebufferDecision.selectedStream?.heightPx)

        // Manual quality honors user selection
        val manualDecision = domain.video.VideoAdaptiveQualitySelector.selectBestQuality(
            availableStreams = available,
            settings = VideoExperienceSettings(autoVideoQualityEnabled = false, lastSelectedVideoQualityHeight = 480),
            isMeteredMobile = false,
            estimatedBandwidthBps = 12_000_000L,
            bufferedAheadMs = 20_000L,
            rebufferCount = 0
        )
        assertFalse(manualDecision.isAutoMode)
        assertEquals(480, manualDecision.selectedStream?.heightPx)

        // Never displays unavailable resolutions
        val distinct = domain.video.VideoAdaptiveQualitySelector.distinctAvailableQualityOptions(
            listOf(sampleStream(360), sampleStream(360, isVideoOnly = true), sampleStream(720))
        )
        assertEquals(listOf(720, 360), distinct.map { it.heightPx })
        assertFalse(distinct.first { it.heightPx == 360 }.isVideoOnly)
    }

    @Test
    fun `pinch to zoom clamps scale and pan safely and resets cleanly`() {
        val zoomed = VideoPinchZoomMath.applyTransform(
            current = VideoPinchZoomState(),
            zoomChange = 4.5f,
            panX = 2000f,
            panY = -2000f,
            viewportWidthPx = 1000f,
            viewportHeightPx = 560f
        )
        assertEquals(VideoPinchZoomMath.MAX_SCALE, zoomed.scale, 0.001f)
        assertTrue(zoomed.isZoomed)
        // Max pan at 3.0x is viewport * (3 - 1) / 2 == viewport
        assertEquals(1000f, zoomed.offsetX, 0.5f)
        assertEquals(-560f, zoomed.offsetY, 0.5f)

        val reset = VideoPinchZoomMath.reset()
        assertEquals(1f, reset.scale, 0.001f)
        assertEquals(0f, reset.offsetX, 0.001f)
        assertEquals(0f, reset.offsetY, 0.001f)
        assertFalse(reset.isZoomed)
    }

    @Test
    fun `aspect ratio calculator supports all scaling modes without distorting non-stretch modes`() {
        val nonStretchModes = listOf(
            VideoAspectRatioMode.AUTO,
            VideoAspectRatioMode.FIT,
            VideoAspectRatioMode.FILL,
            VideoAspectRatioMode.ZOOM,
            VideoAspectRatioMode.ORIGINAL,
            VideoAspectRatioMode.RATIO_16_9,
            VideoAspectRatioMode.RATIO_4_3,
            VideoAspectRatioMode.RATIO_1_1,
            VideoAspectRatioMode.RATIO_9_16
        )
        nonStretchModes.forEach { mode ->
            val layout = VideoAspectRatioCalculator.computeSurfaceLayout(
                viewportWidthPx = 1080f,
                viewportHeightPx = 608f,
                videoWidth = 1920,
                videoHeight = 1080,
                mode = mode
            )
            assertFalse("Mode $mode must not distort video", layout.isDistorted)
            assertTrue(layout.surfaceWidthPx > 0f && layout.surfaceHeightPx > 0f)
        }
        val zoomLayout = VideoAspectRatioCalculator.computeSurfaceLayout(
            viewportWidthPx = 1080f,
            viewportHeightPx = 608f,
            videoWidth = 1920,
            videoHeight = 1080,
            mode = VideoAspectRatioMode.ZOOM
        )
        assertTrue(zoomLayout.isCropped)
        assertTrue(zoomLayout.surfaceWidthPx > 1080f)
    }

    @Test
    fun `video gesture classifier distinguishes portrait swipe-down mini player from fullscreen and edge guards`() {
        assertEquals(
            VideoTouchZone.CENTER_FULLSCREEN,
            VideoGestureClassifier.classifyTouchZone(500f, 300f, 1000f, 600f)
        )
        assertEquals(
            VideoTouchZone.SYSTEM_EDGE_GUARD,
            VideoGestureClassifier.classifyTouchZone(500f, 580f, 1000f, 600f)
        )
        assertEquals(
            FullscreenSwipeDecision.MINIMIZE_TO_MINI_PLAYER,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = 8f,
                totalDy = 140f,
                thresholdPx = 90f,
                isFullScreenLandscape = false,
                isSwipeEnabled = true,
                isSwipeDownToMiniPlayerEnabled = true
            )
        )
        assertEquals(
            FullscreenSwipeDecision.ENTER_FULLSCREEN,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = 4f,
                totalDy = -140f,
                thresholdPx = 90f,
                isFullScreenLandscape = false,
                isSwipeEnabled = true,
                isSwipeDownToMiniPlayerEnabled = true
            )
        )
        assertEquals(
            FullscreenSwipeDecision.EXIT_FULLSCREEN,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = 5f,
                totalDy = 140f,
                thresholdPx = 90f,
                isFullScreenLandscape = true,
                isSwipeEnabled = true,
                isSwipeDownToMiniPlayerEnabled = true
            )
        )
    }

    @Test
    fun `download eligibility evaluator permits progressive streams and blocks live, DRM, and segmented-only sources`() {
        val normalVideo = sampleVideo("vid1", "Acoustic Session", "Studio Channel")
        val liveVideo = sampleVideo("live1", "Live Concert", "Studio Channel", isLive = true)

        val liveResult = VideoDownloadEligibilityEvaluator.evaluate(liveVideo, ResolvedVideoDetails(video = liveVideo))
        assertTrue(liveResult is VideoDownloadEligibility.NotPermitted)

        val drmDetails = ResolvedVideoDetails(
            video = normalVideo,
            videoStreams = listOf(
                sampleStream(720, mime = "video/mp4; widevine-drm", url = "https://cdn.example.com/drm/720.mp4")
            ),
            audioStreams = emptyList()
        )
        val drmResult = VideoDownloadEligibilityEvaluator.evaluate(normalVideo, drmDetails)
        assertTrue(drmResult is VideoDownloadEligibility.NotPermitted)

        val permittedDetails = ResolvedVideoDetails(
            video = normalVideo,
            videoStreams = listOf(sampleStream(720), sampleStream(1080, isVideoOnly = true), sampleStream(360)),
            audioStreams = listOf(
                VideoAudioOption(
                    id = "a1",
                    url = "https://cdn.example.com/audio.m4a",
                    languageLabel = "Original",
                    mimeType = "audio/mp4",
                    formatLabel = "M4A",
                    bitrateKbps = 160
                )
            )
        )
        val permitted = VideoDownloadEligibilityEvaluator.evaluate(normalVideo, permittedDetails)
        assertTrue(permitted is VideoDownloadEligibility.Permitted)
        permitted as VideoDownloadEligibility.Permitted
        // Only muxed progressive 720p & 360p (video-only 1080p excluded)
        assertEquals(listOf("720p", "360p"), permitted.videoOptions.map { it.resolutionLabel })
        assertEquals(1, permitted.audioOptions.size)
    }

    // --- PART 2: Video Recommendations ---

    @Test
    fun `video recommendation engine respects dismissals, ranks by personal signals, and partitions player suggestions`() {
        val v1 = sampleVideo("v1", "Kotlin Compose Animation Guide", "Android Dev", views = 10_000L)
        val v2 = sampleVideo("v2", "Blocked Video Tutorial", "Spam Channel", views = 900_000L)
        val v3 = sampleVideo("v3", "Hidden Video", "Android Dev", views = 500_000L)
        val v4 = sampleVideo("v4", "More Compose Layouts", "Android Dev", views = 12_000L)
        val v5 = sampleVideo("v5", "Cooking Pasta Fast", "Chef Kitchen", views = 800_000L)

        val feedback = listOf(
            VideoRecommendationFeedbackEntity(
                feedbackKey = "ch_spam",
                feedbackType = VideoFeedbackType.DONT_RECOMMEND_CHANNEL,
                targetValue = "Spam Channel"
            ),
            VideoRecommendationFeedbackEntity(
                feedbackKey = "vid_hide",
                feedbackType = VideoFeedbackType.NOT_INTERESTED_VIDEO,
                targetValue = "v3"
            ),
            VideoRecommendationFeedbackEntity(
                feedbackKey = "interest_compose",
                feedbackType = VideoFeedbackType.USER_INTEREST,
                targetValue = "Compose"
            )
        )

        val signals = VideoRecommendationSignals(
            watchHistory = listOf(sampleVideo("w1", "Jetpack Compose Deep Dive", "Android Dev", lastPosMs = 120_000L)),
            subscribedChannels = listOf(
                OnlineVideoChannel(
                    channelId = "ch_android",
                    stableId = 1L,
                    channelUrl = "https://www.youtube.com/@androiddev",
                    name = "Android Dev"
                )
            ),
            feedbackEntries = feedback
        )

        val ranked = VideoRecommendationEngine.rankVideos(listOf(v5, v2, v3, v1), signals)
        assertEquals(listOf("v1", "v5"), ranked.map { it.videoId })

        val shelves = VideoRecommendationEngine.buildPersonalizedHomeShelves(
            rawSections = mapOf(
                VideoHomeSectionType.TRENDING to VideoSectionState.Success(listOf(v1, v2, v3, v4, v5))
            ),
            signals = signals
        )
        assertTrue(shelves.any { it.id == "continue_watching" })
        assertTrue(shelves.any { it.id == "recommended_for_you" })

        val (moreFromChannel, otherRelated) = VideoRecommendationEngine.partitionPlayerRecommendations(
            activeVideo = v1,
            rawRelated = listOf(v1, v2, v3, v4, v5),
            signals = signals
        )
        assertEquals(listOf("v4"), moreFromChannel.map { it.videoId })
        assertEquals(listOf("v5"), otherRelated.map { it.videoId })
    }

    // --- PART 3: Music Discovery & Continuous Song Radio ---

    @Test
    fun `music discovery and song radio build transparent mixes, avoid repeats, and adapt to skips and likes`() {
        assertEquals(9, MusicMoodActivity.entries.size)
        assertEquals(11, IndiaMusicCategory.entries.size)

        val seed = sampleSong(1L, "Kesariya", "Arijit Singh", album = "Brahmastra", genre = "Bollywood")
        val candA = sampleSong(2L, "Tum Hi Ho", "Arijit Singh", genre = "Bollywood")
        val candB = sampleSong(3L, "Raataan Lambiyan", "Jubin Nautiyal", genre = "Bollywood")
        val candC = sampleSong(4L, "Shayad", "Pritam", genre = "Bollywood")

        val madeForYou = MusicDiscoveryAndRadioEngine.buildMadeForYouCards(
            recentSongs = listOf(seed),
            favoriteSongs = listOf(candA),
            countryCode = "IN",
            discoverLanguage = DiscoverMusicLanguage.HINDI
        )
        assertEquals(4, madeForYou.size)
        assertTrue(madeForYou.all { it.badge == MusicDiscoveryBadge.SONORA_GENERATED })

        var session = SongRadioSession(isActive = true, seedSong = seed, playedSongIds = setOf(seed.id))
        // User likes Pritam and skips Arijit Singh
        session = MusicDiscoveryAndRadioEngine.recordRadioLike(session, candC)
        session = MusicDiscoveryAndRadioEngine.recordRadioSkip(session, candA)
        session = MusicDiscoveryAndRadioEngine.recordRadioSkip(session, candA)

        val ranked = MusicDiscoveryAndRadioEngine.rankRadioCandidates(
            candidates = listOf(seed, candA, candB, candC, candC),
            seedSong = seed,
            session = session,
            existingQueueIds = setOf(seed.id)
        )
        assertFalse("Seed song must not repeat", ranked.any { it.id == seed.id })
        assertEquals("Liked artist should rank first", candC.id, ranked.first().id)
        assertEquals("Repeatedly skipped artist should rank last", candA.id, ranked.last().id)
    }

    // --- PART 6: HD & SD Album Artwork ---

    @Test
    fun `album artwork quality manager resolves genuine CDN tiers, separates cache keys, and avoids fake upscaling`() {
        val ytMusicArt = "https://lh3.googleusercontent.com/abc123=w120-h120-l90-rj"
        val hdReq = AlbumArtworkQualityManager.resolveArtworkRequest(
            rawUrl = ytMusicArt,
            mode = AlbumArtworkQualityMode.AUTO,
            role = ArtworkDisplayRole.NOW_PLAYING,
            isMetered = false,
            isPoorConnection = false
        )
        val sdReq = AlbumArtworkQualityManager.resolveArtworkRequest(
            rawUrl = ytMusicArt,
            mode = AlbumArtworkQualityMode.AUTO,
            role = ArtworkDisplayRole.NOW_PLAYING,
            isMetered = true,
            isPoorConnection = false
        )

        assertEquals(ArtworkResolutionTier.HD, hdReq.tier)
        assertTrue(hdReq.primaryUrl?.contains("w1080-h1080") == true)
        assertTrue(hdReq.hasDistinctHdSource)
        assertNotNull(hdReq.fallbackUrl)

        assertEquals(ArtworkResolutionTier.SD, sdReq.tier)
        assertTrue(sdReq.primaryUrl?.contains("w256-h256") == true)
        assertNotEquals("SD and HD must use separate cache keys", sdReq.cacheKey, hdReq.cacheKey)

        // Unsupported CDN must NOT fabricate a fake HD URL
        val genericUrl = "https://indie.example.org/covers/album1.jpg"
        val genericReq = AlbumArtworkQualityManager.resolveArtworkRequest(
            rawUrl = genericUrl,
            mode = AlbumArtworkQualityMode.HD
        )
        assertEquals(genericUrl, genericReq.primaryUrl)
        assertFalse(genericReq.hasDistinctHdSource)
    }
}
