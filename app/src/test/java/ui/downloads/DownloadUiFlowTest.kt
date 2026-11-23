package ui.downloads

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.model.Song
import com.example.viewmodel.SonoraViewModel
import domain.download.DownloadFormatCandidate
import domain.download.DownloadQualityOption
import domain.download.DownloadQualitySheetData
import domain.download.DownloadSortOption
import domain.download.DownloadState
import domain.download.DownloadStorageSummary
import domain.download.DownloadTask
import domain.download.DownloadedTrack
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import domain.online.StreamDeliveryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.MusicContextMenuSheet
import ui.online.OnlineResultRow
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.HomeThemeMode
import ui.theme.LocalEditorialColors
import ui.theme.editorialColorsFor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class DownloadUiFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val colors = editorialColorsFor(
        mode = HomeThemeMode.WARM_CREAM,
        customAccentArgb = null,
        dynamicPalette = DynamicColorPalette.default()
    )

    private fun testOnlineTrack(): OnlineTrack = OnlineTrack(
        stableId = -5050L,
        serviceId = 0,
        providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
        providerName = "YT Music",
        contentUrl = "https://music.youtube.com/watch?v=ui_test",
        title = "Starlight Drift",
        artist = "Kiasmos",
        album = "Blurred",
        durationMs = 225_000L,
        artworkUrl = null,
        kind = OnlineItemKind.TRACK
    )

    @Test
    fun `ChooseDownloadLocationDialog shows exact copy and fires Choose Folder and Cancel`() {
        var chooseClicked = 0
        var cancelClicked = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                ChooseDownloadLocationDialog(
                    colors = colors,
                    onChooseFolder = { chooseClicked++ },
                    onDismiss = { cancelClicked++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Choose Download Location").assertIsDisplayed()
        composeRule.onNodeWithText("Select where SONORA should save your downloaded music.").assertIsDisplayed()

        composeRule.onNodeWithTag("choose_folder_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, chooseClicked)

        composeRule.onNodeWithTag("cancel_folder_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, cancelClicked)
    }

    @Test
    fun `DownloadQualitySelectorSheet displays all 4 presets and genuine candidate metadata`() {
        var selectedPreset by mutableStateOf(DownloadQualityOption.AUTO)
        var confirmedCount = 0
        val opusCandidate = DownloadFormatCandidate(
            streamId = "opus_160",
            codecLabel = "OPUS",
            containerExtension = "webm",
            mimeType = "audio/webm",
            bitrateKbps = 160,
            contentLengthBytes = 4_200_000L,
            streamUrl = "https://example.com/opus.webm",
            isLossless = false,
            isAudioOnly = true,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP
        )
        val aacCandidate = DownloadFormatCandidate(
            streamId = "aac_256",
            codecLabel = "AAC / M4A",
            containerExtension = "m4a",
            mimeType = "audio/mp4",
            bitrateKbps = 256,
            contentLengthBytes = -1L,
            streamUrl = "https://example.com/aac.m4a",
            isLossless = false,
            isAudioOnly = true,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                DownloadQualitySelectorSheet(
                    data = DownloadQualitySheetData(
                        track = testOnlineTrack(),
                        isResolvingFormats = false,
                        candidates = listOf(aacCandidate, opusCandidate),
                        recommendedByPreset = mapOf(
                            DownloadQualityOption.AUTO to aacCandidate,
                            DownloadQualityOption.HIGH_QUALITY to aacCandidate,
                            DownloadQualityOption.BEST_AVAILABLE to aacCandidate,
                            DownloadQualityOption.ORIGINAL_FORMAT to opusCandidate
                        ),
                        selectedPreset = selectedPreset,
                        destinationFolderLabel = "Music"
                    ),
                    colors = colors,
                    onSelectPreset = { selectedPreset = it },
                    onSelectManualCandidate = {},
                    onConfirmDownload = { confirmedCount++ },
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("download_preset_AUTO").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("download_preset_HIGH_QUALITY").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("download_preset_BEST_AVAILABLE").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("download_preset_ORIGINAL_FORMAT").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("download_format_aac_256").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("download_format_opus_160").performScrollTo().assertIsDisplayed()

        // No fake FLAC labels
        assertTrue(composeRule.onAllNodesWithText("FLAC", substring = true).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("download_preset_ORIGINAL_FORMAT").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(DownloadQualityOption.ORIGINAL_FORMAT, selectedPreset)

        composeRule.onNodeWithTag("start_download_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, confirmedCount)
    }

    @Test
    fun `OnlineResultRow exposes Download button when undownloaded and Downloaded indicator when offline`() {
        val track = testOnlineTrack()
        var rowDownloadClicks = 0
        var isDownloaded by mutableStateOf(false)

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                OnlineResultRow(
                    track = track,
                    isCurrent = false,
                    isPlaying = false,
                    isBuffering = false,
                    isSaved = false,
                    isDownloaded = isDownloaded,
                    colors = colors,
                    onClick = {},
                    onMoreClick = {},
                    onDownloadClick = { rowDownloadClicks++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("online_download_btn_${track.stableId}").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, rowDownloadClicks)

        isDownloaded = true
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Downloaded Offline").assertIsDisplayed()
    }

    @Test
    fun `MusicContextMenuSheet exposes Download action and Downloaded Offline plus Delete when downloaded`() {
        val track = testOnlineTrack()
        var sheetDownloadClicks = 0
        var sheetDeleteClicks = 0
        var isDownloaded by mutableStateOf(false)

        val onlineSong = Song(
            id = track.stableId,
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumId = 10L,
            durationMs = track.durationMs,
            uri = Uri.EMPTY,
            dataPath = track.contentUrl,
            mimeType = "audio/mp4",
            sizeBytes = 0L,
            isOnline = true,
            sourceType = "ONLINE",
            providerKey = track.providerKey,
            providerName = track.providerName,
            contentUrl = track.contentUrl
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                MusicContextMenuSheet(
                    song = onlineSong,
                    colors = colors,
                    isDownloaded = isDownloaded,
                    onDownload = { sheetDownloadClicks++ },
                    onDeleteDownload = if (isDownloaded) ({ sheetDeleteClicks++ }) else null,
                    onDismiss = {},
                    onPlayNow = {},
                    onPlayNext = {},
                    onAddToQueue = {},
                    onAddToPlaylist = {},
                    onToggleFavorite = {},
                    onViewAlbum = {},
                    onViewArtist = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("context_menu_download_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Download").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, sheetDownloadClicks)

        isDownloaded = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Downloaded Offline").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("context_menu_delete_download_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, sheetDeleteClicks)
    }

    @Test
    fun `LibraryDownloadsSection renders metadata, sort options, Play All, Shuffle All, and Delete action`() {
        var selectedSort by mutableStateOf(DownloadSortOption.RECENTLY_DOWNLOADED)
        var playAllClicks = 0
        var shuffleAllClicks = 0
        var deleteClicks = 0
        var openManagerClicks = 0

        val dl1 = DownloadedTrack(
            id = -7001L,
            sourceKey = "yt|1",
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = "https://music.youtube.com/watch?v=1",
            title = "Alpha Horizon",
            artist = "Solaris",
            album = "Dawn",
            durationMs = 200_000L,
            artworkUrl = null,
            localArtworkPath = null,
            localFileUri = "file:///tmp/alpha.m4a",
            treeFolderUri = "file:///tmp",
            displayFileName = "Alpha Horizon.m4a",
            formatLabel = "AAC / M4A",
            mimeType = "audio/mp4",
            bitrateKbps = 256,
            sampleRateHz = 44100,
            fileSizeBytes = 6_400_000L,
            downloadedAt = 1_700_000_000_000L,
            isMissingFile = false
        )
        val item1 = DownloadedTrackDisplayItem(track = dl1, song = dl1.toSong(), isMissingFile = false)

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                LibraryDownloadsSection(
                    items = listOf(item1),
                    sortOption = selectedSort,
                    storageSummary = DownloadStorageSummary(
                        trackCount = 1,
                        totalBytes = 6_400_000L,
                        folderLabel = "SavedMusic",
                        hasFolderAccess = true
                    ),
                    activeDownloadCount = 1,
                    isMultiSelectMode = false,
                    selectedIds = emptySet(),
                    currentSongId = null,
                    isPlaying = false,
                    colors = colors,
                    onSelectSort = { selectedSort = it },
                    onOpenDownloadManager = { openManagerClicks++ },
                    onPlayAll = { playAllClicks++ },
                    onShuffleAll = { shuffleAllClicks++ },
                    onTrackClick = {},
                    onTrackLongClick = {},
                    onToggleFavorite = {},
                    onRequestDelete = { deleteClicks++ },
                    onOpenMore = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Alpha Horizon").assertIsDisplayed()
        composeRule.onNodeWithTag("downloads_play_all_button").performClick()
        composeRule.onNodeWithTag("downloads_shuffle_all_button").performClick()
        composeRule.onNodeWithTag("download_sort_artist_asc").performClick()
        composeRule.onNodeWithTag("library_open_download_manager").performClick()
        composeRule.onNodeWithTag("delete_download_btn_-7001").performClick()
        composeRule.waitForIdle()

        assertEquals(1, playAllClicks)
        assertEquals(1, shuffleAllClicks)
        assertEquals(DownloadSortOption.ARTIST_ASC, selectedSort)
        assertEquals(1, openManagerClicks)
        assertEquals(1, deleteClicks)
    }

    @Test
    fun `DownloadManagerScreen mounts cleanly with ViewModel and supports back navigation`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        var backClicks = 0

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                DownloadManagerScreen(
                    viewModel = vm,
                    onBack = { backClicks++ }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("download_manager_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("download_manager_back_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, backClicks)
    }

    @Test
    fun `DownloadTaskCard renders controls for Downloading, Paused, Failed, and Completed states`() {
        var taskState by mutableStateOf(DownloadState.DOWNLOADING)
        var pauseClicks = 0
        var resumeClicks = 0
        var cancelClicks = 0
        var retryClicks = 0
        var removeClicks = 0
        var playOfflineClicks = 0

        val baseTask = DownloadTask(
            taskId = "task_ui_1",
            sourceKey = "yt|ui1",
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = "https://music.youtube.com/watch?v=ui1",
            title = "Glass Harbour",
            artist = "Rival Consoles",
            album = "Now Is",
            durationMs = 240_000L,
            artworkUrl = null,
            qualityOption = DownloadQualityOption.HIGH_QUALITY,
            selectedStreamId = "aac_256",
            formatLabel = "AAC / M4A",
            mimeType = "audio/mp4",
            fileExtension = "m4a",
            bitrateKbps = 256,
            state = DownloadState.DOWNLOADING,
            downloadedBytes = 2_000_000L,
            totalBytes = 4_000_000L,
            speedBytesPerSec = 512_000L,
            etaSeconds = 4L,
            targetFolderUri = "file:///tmp",
            stagingFilePath = null,
            outputFileUri = null,
            failureReason = null,
            retryCount = 0,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                DownloadTaskCard(
                    task = baseTask.copy(
                        state = taskState,
                        failureReason = if (taskState == DownloadState.FAILED) "Network error" else null
                    ),
                    colors = colors,
                    onPause = { pauseClicks++ },
                    onResume = { resumeClicks++ },
                    onCancel = { cancelClicks++ },
                    onRetry = { retryClicks++ },
                    onRemove = { removeClicks++ },
                    onPlayOffline = { playOfflineClicks++ }
                )
            }
        }
        composeRule.waitForIdle()

        // DOWNLOADING -> Pause + Cancel
        composeRule.onNodeWithTag("pause_download_task_ui_1").assertIsDisplayed().performClick()
        assertEquals(1, pauseClicks)

        // PAUSED -> Resume + Cancel
        taskState = DownloadState.PAUSED
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("resume_download_task_ui_1").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("cancel_download_task_ui_1").assertIsDisplayed().performClick()
        assertEquals(1, resumeClicks)
        assertEquals(1, cancelClicks)

        // FAILED -> Retry + Remove
        taskState = DownloadState.FAILED
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Network error").assertIsDisplayed()
        composeRule.onNodeWithTag("retry_download_task_ui_1").assertIsDisplayed().performClick()
        assertEquals(1, retryClicks)

        // COMPLETED -> Play + Clear
        taskState = DownloadState.COMPLETED
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("play_download_task_ui_1").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("remove_download_task_ui_1").assertIsDisplayed().performClick()
        assertEquals(1, playOfflineClicks)
        assertEquals(1, removeClicks)
    }
}
