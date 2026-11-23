package domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelDetailLogicTest {

    @Test
    fun canonicalChannelKey_prefersChannelIdOverUrlAndNormalizesCase() {
        assertEquals(
            "uc12345abc",
            ChannelDetailUtils.canonicalChannelKey(
                channelUrl = "https://www.youtube.com/channel/UC12345ABC?sub_confirmation=1",
                channelId = "UC12345ABC"
            )
        )
        assertEquals(
            "@sonoramusic",
            ChannelDetailUtils.canonicalChannelKey(
                channelUrl = "https://www.youtube.com/@SonoraMusic/videos?view=0",
                channelId = null
            ).substringBefore("videos").ifBlank {
                ChannelDetailUtils.canonicalChannelKey("https://www.youtube.com/@SonoraMusic/")
            }
        )
        assertEquals(
            "@sonoramusic",
            ChannelDetailUtils.canonicalChannelKey("https://www.youtube.com/@SonoraMusic/")
        )
    }

    @Test
    fun extractHandleFromUrl_parsesPublicHandleWhenPresent() {
        assertEquals(
            "@IndieAcousticIN",
            ChannelDetailUtils.extractHandleFromUrl("https://www.youtube.com/@IndieAcousticIN/videos")
        )
        assertNull(
            ChannelDetailUtils.extractHandleFromUrl("https://www.youtube.com/channel/UCabc123456")
        )
    }

    @Test
    fun extractExternalLinks_extractsRealDeduplicatedLinksFromDescription() {
        val description = """
            Official live sessions and behind-the-scenes studio films.
            Website: https://www.sonorastudios.in/sessions.
            Instagram: https://instagram.com/sonorastudios,
            Duplicate: https://www.sonorastudios.in/sessions
        """.trimIndent()

        val links = ChannelDetailUtils.extractExternalLinks(description)
        assertEquals(2, links.size)
        assertEquals("sonorastudios.in", links[0].label)
        assertEquals("https://www.sonorastudios.in/sessions", links[0].url)
        assertEquals("instagram.com", links[1].label)
        assertEquals("https://instagram.com/sonorastudios", links[1].url)
    }

    @Test
    fun resolveAvailableTabs_omitsUnsupportedTabsInsteadOfShowingFakeContent() {
        val videosOnly = ChannelDetailUtils.resolveAvailableTabs(setOf(ChannelTabType.VIDEOS))
        assertEquals(listOf(ChannelTabType.VIDEOS, ChannelTabType.ABOUT), videosOnly)

        val allTabs = ChannelDetailUtils.resolveAvailableTabs(
            setOf(ChannelTabType.PLAYLISTS, ChannelTabType.VIDEOS, ChannelTabType.SHORTS)
        )
        assertEquals(
            listOf(
                ChannelTabType.VIDEOS,
                ChannelTabType.SHORTS,
                ChannelTabType.PLAYLISTS,
                ChannelTabType.ABOUT
            ),
            allTabs
        )
    }

    @Test
    fun mergeVideosUnique_deduplicatesPaginatedResultsWhilePreservingOrder() {
        val v1 = OnlineVideoItem(videoId = "vid_1", contentUrl = "https://youtu.be/vid_1", title = "First", channelName = "Ch")
        val v2 = OnlineVideoItem(videoId = "vid_2", contentUrl = "https://youtu.be/vid_2", title = "Second", channelName = "Ch")
        val v3 = OnlineVideoItem(videoId = "vid_3", contentUrl = "https://youtu.be/vid_3", title = "Third", channelName = "Ch")

        val merged = ChannelDetailUtils.mergeVideosUnique(listOf(v1, v2), listOf(v2, v3))
        assertEquals(listOf("vid_1", "vid_2", "vid_3"), merged.map { it.videoId })
    }

    @Test
    fun mergePlaylistsUnique_deduplicatesByExternalUrl() {
        val p1 = OnlineVideoPlaylistSummary(externalUrl = "https://youtube.com/playlist?list=PL1", name = "Live Sets")
        val p2 = OnlineVideoPlaylistSummary(externalUrl = "https://youtube.com/playlist?list=PL2", name = "Acoustic")
        val p1Dup = OnlineVideoPlaylistSummary(externalUrl = "https://youtube.com/playlist?list=PL1", name = "Live Sets Copy")

        val merged = ChannelDetailUtils.mergePlaylistsUnique(listOf(p1), listOf(p1Dup, p2))
        assertEquals(2, merged.size)
        assertEquals("Live Sets", merged[0].name)
        assertEquals("Acoustic", merged[1].name)
    }

    @Test
    fun computeWatchProgress_returnsValidFractionOnlyForPartiallyWatchedVideos() {
        assertNull(ChannelDetailUtils.computeWatchProgress(lastPositionMs = 1_000L, durationSeconds = 300L))
        assertNull(ChannelDetailUtils.computeWatchProgress(lastPositionMs = 45_000L, durationSeconds = 0L))
        val ratio = ChannelDetailUtils.computeWatchProgress(lastPositionMs = 90_000L, durationSeconds = 180L)
        assertTrue(ratio != null && ratio in 0.49f..0.51f)
    }
}
