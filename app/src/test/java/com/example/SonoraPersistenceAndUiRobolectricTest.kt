package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.SonoraDatabase
import com.example.model.Song
import data.queue.MusicQueueRepository
import data.video.VideoFeedbackType
import data.video.VideoRecommendationFeedbackEntity
import domain.download.DownloadMediaType
import domain.download.DownloadSortOption
import domain.download.DownloadStorageSummary
import domain.download.DownloadedTrack
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.downloads.LibraryDownloadsSection
import ui.theme.LocalEditorialColors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraPersistenceAndUiRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var db: SonoraDatabase
    private lateinit var queueRepo: MusicQueueRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        queueRepo = MusicQueueRepository(db.dao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `unlimited music queue persists and restores 1250 tracks with duplicate songs and exact playback position`() = runBlocking {
        queueRepo.initializeIdGenerator()

        // Create 1,250 tracks including intentional duplicates of track #1 to verify unique queueItemId handling
        val songs = (1..1250).map { index ->
            val songId = if (index == 1250) 1L else index.toLong()
            Song(
                id = songId,
                title = "Track $songId",
                artist = "Artist ${songId % 25}",
                album = "Album ${songId % 10}",
                albumId = songId,
                durationMs = 240_000L,
                uri = Uri.parse("content://media/external/audio/media/$songId"),
                dataPath = "/storage/emulated/0/Music/track_$songId.flac",
                mimeType = "audio/flac",
                sizeBytes = 12_000_000L
            )
        }

        val wrapped = queueRepo.wrapSongs(songs)
        assertEquals(1250, wrapped.size)
        // First and last share song.id == 1L, but MUST have distinct queueItemIds
        assertEquals(wrapped.first().song.id, wrapped.last().song.id)
        assertNotEquals(wrapped.first().queueItemId, wrapped.last().queueItemId)

        queueRepo.persistQueue(
            items = wrapped,
            currentIndex = 742,
            playbackPositionMs = 98_450L
        )

        val restored = queueRepo.restoreQueue()
        assertEquals(1250, restored.items.size)
        assertEquals(742, restored.currentIndex)
        assertEquals(98_450L, restored.playbackPositionMs)
        assertEquals(wrapped[742].queueItemId, restored.items[742].queueItemId)
        assertEquals("Track 743", restored.items[742].song.title)
    }

    @Test
    fun `video recommendation feedback persists, filters, and clears cleanly in Room v7`() = runBlocking {
        val dao = db.dao()
        dao.upsertVideoRecommendationFeedback(
            VideoRecommendationFeedbackEntity(
                feedbackKey = "not_interested:abc123",
                feedbackType = VideoFeedbackType.NOT_INTERESTED_VIDEO,
                targetValue = "abc123"
            )
        )
        dao.upsertVideoRecommendationFeedback(
            VideoRecommendationFeedbackEntity(
                feedbackKey = "block_channel:spam",
                feedbackType = VideoFeedbackType.DONT_RECOMMEND_CHANNEL,
                targetValue = "Spam Channel"
            )
        )

        val stored = dao.getAllVideoRecommendationFeedbackFlow().first()
        assertEquals(2, stored.size)
        assertTrue(stored.any { it.targetValue == "abc123" })
        assertTrue(stored.any { it.targetValue == "Spam Channel" })

        dao.deleteVideoRecommendationFeedback("not_interested:abc123")
        assertEquals(1, dao.getAllVideoRecommendationFeedbackFlow().first().size)

        dao.clearAllVideoRecommendationFeedback()
        assertEquals(0, dao.getAllVideoRecommendationFeedbackFlow().first().size)
    }

    @Test
    fun `offline library downloads section switches between Music and Videos sub-tabs and triggers video playback`() {
        var playedVideoId: Long? = null
        val downloadedVideo = DownloadedTrack(
            id = 999L,
            sourceKey = "youtube:vid999",
            serviceId = 0,
            providerKey = "youtube",
            providerName = "YouTube",
            contentUrl = "https://www.youtube.com/watch?v=vid999",
            title = "Offline Concert Film 720p",
            artist = "Live Stage Channel",
            album = "Downloads",
            durationMs = 420_000L,
            artworkUrl = null,
            localArtworkPath = null,
            localFileUri = "file:///storage/emulated/0/Movies/concert.mp4",
            treeFolderUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            displayFileName = "concert.mp4",
            formatLabel = "720p MP4",
            mimeType = "video/mp4",
            bitrateKbps = 2400,
            sampleRateHz = 48000,
            fileSizeBytes = 64_000_000L,
            downloadedAt = System.currentTimeMillis(),
            mediaType = DownloadMediaType.VIDEO,
            resolutionLabel = "720p"
        )

        composeRule.setContent {
            LibraryDownloadsSection(
                items = emptyList(),
                sortOption = DownloadSortOption.RECENTLY_DOWNLOADED,
                storageSummary = DownloadStorageSummary(trackCount = 1, totalBytes = 64_000_000L, folderLabel = "Music/SONORA", hasFolderAccess = true),
                activeDownloadCount = 0,
                isMultiSelectMode = false,
                selectedIds = emptySet(),
                currentSongId = null,
                isPlaying = false,
                colors = LocalEditorialColors.current,
                onSelectSort = {},
                onOpenDownloadManager = {},
                onPlayAll = {},
                onShuffleAll = {},
                onTrackClick = {},
                onTrackLongClick = {},
                onToggleFavorite = {},
                onRequestDelete = {},
                onOpenMore = {},
                downloadedVideos = listOf(downloadedVideo),
                onPlayDownloadedVideo = { playedVideoId = it.id },
                onDeleteDownloadedVideo = {}
            )
        }

        composeRule.onNodeWithTag("downloads_subtab_music").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads_subtab_videos").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Offline Concert Film 720p").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(999L, playedVideoId)
    }
}
