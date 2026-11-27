package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Genre
import com.example.model.MusicFolder
import com.example.model.Playlist
import com.example.model.Song
import com.example.model.SortOption
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.OnlineCollectionUiState
import com.example.viewmodel.SonoraViewModel
import data.online.OnlineTrackMapper
import domain.download.DownloadSortOption
import domain.library.LibrarySectionMode
import domain.library.OnlinePlaylist
import domain.online.OnlineItemKind
import domain.online.OnlineTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.components.CircularArtwork
import ui.components.CollectionDetailSheet
import ui.components.EditorialArtworkCard
import ui.components.EditorialPlaylistPickerDialog
import ui.components.MusicContextMenuSheet
import ui.downloads.DownloadManagerScreen
import ui.downloads.DownloadedTrackDisplayItem
import ui.downloads.LibraryDownloadsSection
import ui.library.CreateOnlinePlaylistDialog
import ui.library.LibraryModeSegmentedSelector
import ui.library.OfflinePlaylistsSection
import ui.library.OnlineLibraryContent
import ui.library.OnlineLibrarySortSheet
import ui.library.OnlinePlaylistDetailSheet
import ui.library.OnlinePlaylistPickerDialog
import ui.library.RenameOnlinePlaylistDialog
import ui.motion.LocalEffectiveMotionState
import ui.motion.SonoraTransitions
import ui.online.OnlineCollectionDetailsSheet
import ui.online.OnlineTrackOptionsSheet
import ui.settings.LibraryFolderManagementScreen
import domain.video.OnlineVideoItem
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import ui.video.OnlineVideoLibraryContent
import ui.video.VideoOptionsBottomSheet
import ui.video.VideoPlaylistPickerDialog
import ui.video.openOfficialExternalUrl
import ui.video.shareVideoLink
import java.io.File

private enum class OfflineLibraryCategory(val label: String) {
    ALL_SONGS("All Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
    PLAYLISTS("Playlists"),
    RECENTLY_PLAYED("Recently Played"),
    RECENTLY_ADDED("Recently Added"),
    MOST_PLAYED("Most Played"),
    DOWNLOADS("Downloads"),
    FAVOURITES("Favourites"),
    GENRES("Genres")
}

private data class LibraryDetailCollection(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: SonoraViewModel,
    onOpenFolderManagement: (() -> Unit)? = null,
    onOpenPlaylists: (() -> Unit)? = null,
    onOpenDownloadManager: (() -> Unit)? = null,
    onOpenOnlineMusic: (() -> Unit)? = null,
    onOpenOnlineVideo: (() -> Unit)? = null,
    openDownloadsTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val motionState = LocalEffectiveMotionState.current

    // Persisted Library Section Mode (Offline Library vs Online Library)
    val librarySectionMode by viewModel.librarySectionMode.collectAsState()

    // Offline Library state
    val allLibrarySongs by viewModel.allSongs.collectAsState()
    val downloadedTracks by viewModel.downloadedTracks.collectAsState()
    val downloadedSongs by viewModel.downloadedSongs.collectAsState()
    val downloadSortOption by viewModel.downloadSortOption.collectAsState()
    val downloadStorageSummary by viewModel.downloadStorageSummary.collectAsState()
    val activeDownloadTasksCount by viewModel.activeDownloadTasksCount.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val recentlyPlayedLocal by viewModel.recentlyPlayedSongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val playlistSongsMap by viewModel.playlistSongsMap.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    // Online Library state
    val onlineSavedTracks by viewModel.onlineSavedTracks.collectAsState()
    val onlineFavoriteTracks by viewModel.onlineFavoriteTracks.collectAsState()
    val recentlyPlayedOnlineTracks by viewModel.recentlyPlayedOnlineTracks.collectAsState()
    val onlineListeningHistory by viewModel.onlineListeningHistory.collectAsState()
    val savedOnlineAlbums by viewModel.savedOnlineAlbums.collectAsState()
    val followedOnlineArtists by viewModel.followedOnlineArtists.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val onlineLibrarySearchQuery by viewModel.onlineLibrarySearchQuery.collectAsState()
    val onlineLibrarySortOption by viewModel.onlineLibrarySortOption.collectAsState()
    val onlineLibraryProviderFilter by viewModel.onlineLibraryProviderFilter.collectAsState()
    val onlineProviders by viewModel.onlineProviders.collectAsState()
    val onlineCollectionState by viewModel.onlineCollectionState.collectAsState()
    val savedOnlineAlbumIds by viewModel.savedOnlineAlbumIds.collectAsState()
    val followedOnlineArtistIds by viewModel.followedOnlineArtistIds.collectAsState()
    val savedOnlinePlaylistUrls by viewModel.savedOnlinePlaylistUrls.collectAsState()

    // Online Video Library state
    val likedVideos by viewModel.videoController.likedVideos.collectAsState()
    val watchLaterVideos by viewModel.videoController.watchLaterVideos.collectAsState()
    val videoHistory by viewModel.videoController.videoHistory.collectAsState()
    val savedVideos by viewModel.videoController.savedVideos.collectAsState()
    val subscribedVideoChannels by viewModel.videoController.subscribedChannels.collectAsState()
    val videoPlaylists by viewModel.videoController.videoPlaylists.collectAsState()
    val videoPlayerState by viewModel.videoController.playerState.collectAsState()
    val watchLaterVideoIds = remember(watchLaterVideos) { watchLaterVideos.map { it.videoId }.toSet() }
    val savedVideoIds = remember(savedVideos) { savedVideos.map { it.videoId }.toSet() }

    // Shared single-engine playback state
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackStatusSummary.collectAsState()

    var selectedCategory by remember { mutableStateOf(OfflineLibraryCategory.ALL_SONGS) }
    LaunchedEffect(openDownloadsTrigger) {
        if (openDownloadsTrigger > 0) {
            viewModel.setLibrarySectionMode(LibrarySectionMode.OFFLINE)
            selectedCategory = OfflineLibraryCategory.DOWNLOADS
        }
    }

    var isGridView by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    // Exit multi-select cleanly when leaving Offline Library
    LaunchedEffect(librarySectionMode) {
        if (librarySectionMode != LibrarySectionMode.OFFLINE && isMultiSelectMode) {
            isMultiSelectMode = false
            selectedSongIds = emptySet()
        }
    }

    // Offline modal / dialog states
    var showSortSheet by remember { mutableStateOf(false) }
    var activeDetailCollection by remember { mutableStateOf<LibraryDetailCollection?>(null) }
    var activeFolderDrillDown by remember { mutableStateOf<MusicFolder?>(null) }
    var contextMenuSong by remember { mutableStateOf<Song?>(null) }
    var songToEdit by remember { mutableStateOf<Song?>(null) }
    var singleSongForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showBatchPlaylistPicker by remember { mutableStateOf(false) }
    var showInternalFolderManagement by remember { mutableStateOf(false) }
    var showInternalDownloadManager by remember { mutableStateOf(false) }
    var singleDownloadedToDelete by remember { mutableStateOf<Song?>(null) }
    var singleLocalTrackToDelete by remember { mutableStateOf<Song?>(null) }
    var showBatchDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingSystemDeleteIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    // Safe scoped-storage system delete launcher (API 29 RecoverableSecurityException / API 30+ MediaStore.createDeleteRequest)
    val systemDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && pendingSystemDeleteIds.isNotEmpty()) {
            viewModel.onSystemLocalDeleteConfirmed(pendingSystemDeleteIds)
        }
        pendingSystemDeleteIds = emptyList()
    }

    val requestSafeDeleteLocalSongs: (List<Song>) -> Unit = { targets ->
        viewModel.deleteLocalSongs(targets) { intentSender, pendingIds ->
            pendingSystemDeleteIds = pendingIds
            try {
                systemDeleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            } catch (_: Exception) {
                pendingSystemDeleteIds = emptyList()
            }
        }
    }

    // Online Library modal / dialog states
    var showOnlineSortSheet by remember { mutableStateOf(false) }
    var showCreateOnlinePlaylistDialog by remember { mutableStateOf(false) }
    var activeOnlinePlaylistId by remember { mutableStateOf<Long?>(null) }
    var playlistToRename by remember { mutableStateOf<OnlinePlaylist?>(null) }
    var playlistToDelete by remember { mutableStateOf<OnlinePlaylist?>(null) }
    var songForOnlinePlaylistPicker by remember { mutableStateOf<Song?>(null) }
    var onlineTrackForOptions by remember { mutableStateOf<OnlineTrack?>(null) }
    var videoForOptions by remember { mutableStateOf<OnlineVideoItem?>(null) }
    var videoForPlaylistPicker by remember { mutableStateOf<OnlineVideoItem?>(null) }

    var downloadFileExistsMap by remember { mutableStateOf<Map<Long, Boolean>>(emptyMap()) }
    LaunchedEffect(downloadedTracks) {
        downloadFileExistsMap = withContext(Dispatchers.IO) {
            downloadedTracks.associate { track ->
                track.id to viewModel.downloadCoordinator.storageHelper.doesDownloadedFileExist(track.localFileUri)
            }
        }
    }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedSongIds = emptySet()
    }
    BackHandler(enabled = librarySectionMode == LibrarySectionMode.OFFLINE && activeFolderDrillDown != null && !isMultiSelectMode) {
        activeFolderDrillDown = null
    }

    val openFolderManager: () -> Unit = {
        onOpenFolderManagement?.invoke() ?: run { showInternalFolderManagement = true }
    }
    val openDownloadManagerScreen: () -> Unit = {
        onOpenDownloadManager?.invoke() ?: run { showInternalDownloadManager = true }
    }

    // Offline filtering & sorting via OfflineLibraryRepository
    val displayedDownloadItems = remember(downloadedTracks, downloadedSongs, searchQuery, downloadSortOption, downloadFileExistsMap) {
        val songMap = downloadedSongs.associateBy { it.id }
        val filtered = if (searchQuery.isBlank()) downloadedTracks else {
            downloadedTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true) ||
                    it.formatLabel.contains(searchQuery, ignoreCase = true)
            }
        }
        val sorted = when (downloadSortOption) {
            DownloadSortOption.RECENTLY_DOWNLOADED -> filtered.sortedByDescending { it.downloadedAt }
            DownloadSortOption.ARTIST_ASC -> filtered.sortedBy { it.artist.lowercase() }
            DownloadSortOption.ALBUM_ASC -> filtered.sortedBy { it.album.lowercase() }
            DownloadSortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
        }
        sorted.map { track ->
            val resolvedSong = songMap[track.id] ?: track.toSong()
            val exists = downloadFileExistsMap[track.id] ?: true
            DownloadedTrackDisplayItem(
                track = track,
                song = resolvedSong,
                isMissingFile = !exists
            )
        }
    }
    val displayedDownloadedSongs = remember(displayedDownloadItems) {
        displayedDownloadItems.map { it.song }
    }

    val displayedRecentlyPlayedLocal = remember(recentlyPlayedLocal, searchQuery, sortOption) {
        viewModel.offlineLibraryRepository.filterAndSortSongs(recentlyPlayedLocal, searchQuery, sortOption)
    }
    val displayedRecentSongs = remember(recentlyAdded, searchQuery, sortOption) {
        viewModel.offlineLibraryRepository.filterAndSortSongs(recentlyAdded, searchQuery, sortOption)
    }
    val displayedMostPlayedSongs = remember(mostPlayed, searchQuery, sortOption) {
        viewModel.offlineLibraryRepository.filterAndSortSongs(mostPlayed, searchQuery, sortOption)
    }
    val displayedFavSongs = remember(favorites, searchQuery, sortOption) {
        viewModel.offlineLibraryRepository.filterAndSortSongs(favorites, searchQuery, sortOption)
    }
    val displayedAlbums = remember(albums, searchQuery) {
        viewModel.offlineLibraryRepository.filterAlbums(albums, searchQuery)
    }
    val displayedArtists = remember(artists, searchQuery) {
        viewModel.offlineLibraryRepository.filterArtists(artists, searchQuery)
    }
    val displayedFolders = remember(folders, searchQuery) {
        viewModel.offlineLibraryRepository.filterFolders(folders, searchQuery)
    }
    val displayedLocalPlaylists = remember(playlists, searchQuery) {
        viewModel.offlineLibraryRepository.filterPlaylists(playlists, searchQuery)
    }
    val drillDownSongs = remember(activeFolderDrillDown, allLibrarySongs, searchQuery, sortOption) {
        val folder = activeFolderDrillDown ?: return@remember emptyList()
        viewModel.offlineLibraryRepository.filterAndSortSongs(
            allLibrarySongs.filter { (File(it.dataPath).parent ?: "/storage") == folder.path },
            searchQuery,
            sortOption
        )
    }

    // Online Library zero-network local metadata search + filter + sort
    val onlineSavedTrackIds = remember(onlineSavedTracks) {
        onlineSavedTracks.map { it.id }.toSet()
    }
    val onlineSearchResults = remember(
        onlineLibrarySearchQuery,
        onlineLibraryProviderFilter,
        onlineLibrarySortOption,
        onlineSavedTracks,
        savedOnlineAlbums,
        followedOnlineArtists,
        onlinePlaylists,
        onlineListeningHistory,
        onlineFavoriteTracks
    ) {
        viewModel.onlineLibraryRepository.searchSavedContent(
            query = onlineLibrarySearchQuery,
            providerFilterKey = onlineLibraryProviderFilter,
            sortOption = onlineLibrarySortOption,
            savedTracks = onlineSavedTracks,
            savedAlbums = savedOnlineAlbums,
            followedArtists = followedOnlineArtists,
            onlinePlaylists = onlinePlaylists,
            listeningHistory = onlineListeningHistory,
            favorites = onlineFavoriteTracks
        )
    }
    val filteredOnlineRecentlyPlayed = remember(
        recentlyPlayedOnlineTracks,
        onlineLibrarySearchQuery,
        onlineLibraryProviderFilter
    ) {
        val byProvider = if (onlineLibraryProviderFilter.isNullOrBlank()) {
            recentlyPlayedOnlineTracks
        } else {
            recentlyPlayedOnlineTracks.filter { it.providerKey.equals(onlineLibraryProviderFilter, ignoreCase = true) }
        }
        val q = onlineLibrarySearchQuery.trim()
        if (q.isEmpty()) byProvider else {
            byProvider.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true) ||
                    it.providerName.contains(q, ignoreCase = true)
            }
        }
    }

    val currentSelectablePool: List<Song> = if (librarySectionMode == LibrarySectionMode.OFFLINE) {
        when (selectedCategory) {
            OfflineLibraryCategory.ALL_SONGS -> filteredSongs
            OfflineLibraryCategory.DOWNLOADS -> displayedDownloadedSongs
            OfflineLibraryCategory.RECENTLY_PLAYED -> displayedRecentlyPlayedLocal
            OfflineLibraryCategory.RECENTLY_ADDED -> displayedRecentSongs
            OfflineLibraryCategory.MOST_PLAYED -> displayedMostPlayedSongs
            OfflineLibraryCategory.FAVOURITES -> displayedFavSongs
            OfflineLibraryCategory.FOLDERS -> if (activeFolderDrillDown != null) drillDownSongs else emptyList()
            else -> emptyList()
        }
    } else {
        emptyList()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .testTag("library_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Editorial Library Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isMultiSelectMode) "${selectedSongIds.size} selected" else "Library",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitleText = when (librarySectionMode) {
                        LibrarySectionMode.OFFLINE -> buildString {
                            append("${allLibrarySongs.size} device tracks")
                            if (downloadedTracks.isNotEmpty()) {
                                append(" • ${downloadedTracks.size} downloads")
                            } else {
                                append(" • ${albums.size} albums")
                            }
                        }
                        LibrarySectionMode.ONLINE ->
                            "${onlineSavedTracks.size} saved • ${onlinePlaylists.size} playlists • ${followedOnlineArtists.size} artists"
                        LibrarySectionMode.ONLINE_VIDEO ->
                            "${watchLaterVideos.size} watch later • ${videoHistory.size} history • ${videoPlaylists.size} playlists"
                    }
                    Text(
                        text = subtitleText,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (librarySectionMode == LibrarySectionMode.OFFLINE) {
                        HeaderCircleAction(
                            icon = if (isMultiSelectMode) Icons.Rounded.Close else Icons.Rounded.Checklist,
                            contentDescription = if (isMultiSelectMode) "Exit Multi-Select" else "Multi-Select Songs",
                            isActive = isMultiSelectMode,
                            colors = colors,
                            testTag = "library_multiselect_button",
                            onClick = {
                                isMultiSelectMode = !isMultiSelectMode
                                if (!isMultiSelectMode) selectedSongIds = emptySet()
                            }
                        )
                        HeaderCircleAction(
                            icon = Icons.Rounded.FolderSpecial,
                            contentDescription = "Manage Folder Blacklist & Whitelist",
                            isActive = false,
                            colors = colors,
                            testTag = "library_folder_settings_button",
                            onClick = openFolderManager
                        )
                        HeaderCircleAction(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = "Rescan Local Library",
                            isActive = false,
                            colors = colors,
                            testTag = "rescan_library_button",
                            onClick = { viewModel.loadLocalSongs() }
                        )
                    } else if (librarySectionMode == LibrarySectionMode.ONLINE && onOpenOnlineMusic != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.accentPill,
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onOpenOnlineMusic() }
                                .testTag("online_library_discover_shortcut")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Explore Online Music",
                                    tint = colors.onAccentPill,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Discover",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.onAccentPill
                                )
                            }
                        }
                    } else if (librarySectionMode == LibrarySectionMode.ONLINE_VIDEO && onOpenOnlineVideo != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.accentPill,
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onOpenOnlineVideo() }
                                .testTag("online_video_library_discover_shortcut")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Explore Online Videos",
                                    tint = colors.onAccentPill,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Videos",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.onAccentPill
                                )
                            }
                        }
                    }
                }
            }

            // Prominent Three-Option Segmented Selector: [ Offline ] [ Music ] [ Video ]
            LibraryModeSegmentedSelector(
                selectedMode = librarySectionMode,
                colors = colors,
                onSelectMode = { targetMode ->
                    viewModel.setLibrarySectionMode(targetMode)
                },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Smooth section transition between Offline, Online Music & Online Video Library
            AnimatedContent(
                targetState = librarySectionMode,
                transitionSpec = {
                    SonoraTransitions.tabTransition(
                        forward = targetState.ordinal >= initialState.ordinal,
                        motion = motionState
                    )
                },
                label = "library_section_mode_transition",
                modifier = Modifier.weight(1f)
            ) { activeMode ->
                when (activeMode) {
                    LibrarySectionMode.ONLINE -> {
                        OnlineLibraryContent(
                        searchResults = onlineSearchResults,
                        recentlyPlayed = filteredOnlineRecentlyPlayed,
                        savedTrackIds = onlineSavedTrackIds,
                        availableProviders = onlineProviders,
                        selectedProviderKey = onlineLibraryProviderFilter,
                        searchQuery = onlineLibrarySearchQuery,
                        sortOption = onlineLibrarySortOption,
                        isCollectionLoading = onlineCollectionState is OnlineCollectionUiState.Loading,
                        currentSongId = currentSong?.id,
                        isPlaying = playbackInfo.isPlaying,
                        colors = colors,
                        onSearchQueryChange = { viewModel.setOnlineLibrarySearchQuery(it) },
                        onSelectProviderFilter = { viewModel.setOnlineLibraryProviderFilter(it) },
                        onOpenSortSheet = { showOnlineSortSheet = true },
                        onCreateOnlinePlaylist = { showCreateOnlinePlaylistDialog = true },
                        onOpenOnlinePlaylist = { playlist -> activeOnlinePlaylistId = playlist.id },
                        onPlaySongs = { list, song, shuffle ->
                            if (list.isNotEmpty()) {
                                if (shuffle || song == null) {
                                    viewModel.playCollection(list, shuffle = shuffle)
                                } else {
                                    viewModel.playSong(song, list)
                                }
                            }
                        },
                        onToggleSaveTrack = { viewModel.toggleSaveOnlineTrack(it) },
                        onToggleFavoriteTrack = { viewModel.toggleFavorite(it) },
                        onOpenSongMore = { song ->
                            onlineTrackForOptions = OnlineTrackMapper.toOnlineTrack(song)
                        },
                        onOpenAlbum = { album ->
                            viewModel.openOnlineCollection(album.toOnlineTrack())
                        },
                        onRemoveAlbum = { album ->
                            viewModel.removeSavedOnlineAlbum(album.stableId, album.title)
                        },
                        onOpenArtist = { artist ->
                            viewModel.openOnlineCollection(artist.toOnlineTrack())
                        },
                        onUnfollowArtist = { artist ->
                            viewModel.unfollowOnlineArtist(artist.stableId, artist.name)
                        },
                        onClearHistory = { viewModel.clearOnlineListeningHistory() },
                        onExploreOnlineMusic = { onOpenOnlineMusic?.invoke() }
                    )
                    }
                    LibrarySectionMode.ONLINE_VIDEO -> {
                        OnlineVideoLibraryContent(
                            likedVideos = likedVideos,
                            watchLaterVideos = watchLaterVideos,
                            videoHistory = videoHistory,
                            savedVideos = savedVideos,
                            subscribedChannels = subscribedVideoChannels,
                            videoPlaylists = videoPlaylists,
                            activeVideoId = videoPlayerState.activeVideo?.videoId,
                            colors = colors,
                            onPlayVideo = { video, pool ->
                                viewModel.videoController.playVideo(video, pool, expandPlayer = true)
                            },
                            onPlayAllLikedVideos = { list ->
                                viewModel.videoController.playAllLikedVideos(list)
                            },
                            onAddVideoToQueue = { video ->
                                viewModel.videoController.addVideoToQueue(video)
                            },
                            onUnlikeVideo = { videoId ->
                                viewModel.videoController.unlikeVideo(videoId)
                            },
                            onClearAllLikedVideos = {
                                viewModel.videoController.clearAllLikedVideos()
                            },
                            onRemoveWatchLater = { viewModel.videoController.toggleWatchLater(it) },
                            onRemoveFromHistory = { viewModel.videoController.removeFromVideoHistory(it) },
                            onClearVideoHistory = { viewModel.videoController.clearVideoHistory() },
                            onRemoveSavedVideo = { viewModel.videoController.toggleSaveVideo(it) },
                            onOpenChannel = { channel ->
                                viewModel.videoController.openChannelProfile(
                                    channelUrl = channel.channelUrl,
                                    channelName = channel.name,
                                    initialAvatarUrl = channel.avatarUrl
                                )
                            },
                            onRemoveChannelBookmark = { channel ->
                                viewModel.videoController.toggleFollowChannelLocally(channel)
                            },
                            onUpdateChannelNotification = { channel, pref ->
                                viewModel.videoController.setSonoraChannelNotificationPreference(channel, pref)
                            },
                            onCreatePlaylist = { name -> viewModel.videoController.createVideoPlaylist(name) },
                            onRenamePlaylist = { id, name -> viewModel.videoController.renameVideoPlaylist(id, name) },
                            onDeletePlaylist = { id -> viewModel.videoController.deleteVideoPlaylist(id) },
                            onRemoveVideoFromPlaylist = { pid, vid ->
                                viewModel.videoController.removeVideoFromPlaylist(pid, vid)
                            },
                            onVideoMoreClick = { videoForOptions = it },
                            onExploreOnlineVideos = { onOpenOnlineVideo?.invoke() }
                        )
                    }
                    LibrarySectionMode.OFFLINE -> {
                    // OFFLINE LIBRARY
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("offline_library_content")
                    ) {
                        // Offline Search + Sort + Grid/List Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = {
                                    Text(
                                        text = "Search device songs, albums, folders...",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 13.sp,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accentStrong)
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = colors.textSecondary)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accentStrong,
                                    unfocusedBorderColor = colors.cardBorder,
                                    focusedContainerColor = colors.surface,
                                    unfocusedContainerColor = colors.surface,
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("library_search_input")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Sort sheet trigger
                            Surface(
                                shape = CircleShape,
                                color = colors.surface,
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(0.75.dp, colors.cardBorder, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { showSortSheet = true }
                                    .testTag("sort_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = "Sort Offline Library",
                                        tint = colors.accentStrong
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Grid / List toggle
                            Surface(
                                shape = CircleShape,
                                color = if (isGridView) colors.accentPill else colors.surface,
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(0.75.dp, colors.cardBorder, CircleShape)
                                    .clip(CircleShape)
                                    .clickable { isGridView = !isGridView }
                                    .testTag("library_layout_toggle")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = if (isGridView) "Switch to List view" else "Switch to Grid view",
                                        tint = if (isGridView) colors.onAccentPill else colors.textPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Offline Library Category Pills (All Songs, Albums, Artists, Folders, Playlists, Recently Played, Recently Added, Most Played, Downloads, Favourites, Genres)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OfflineLibraryCategory.entries.forEach { category ->
                                val selected = selectedCategory == category
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (selected) colors.accentStrong else colors.surface,
                                    modifier = Modifier
                                        .height(40.dp)
                                        .border(
                                            width = 0.75.dp,
                                            color = if (selected) colors.accentStrong else colors.cardBorder,
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable {
                                            selectedCategory = category
                                            if (category != OfflineLibraryCategory.FOLDERS) activeFolderDrillDown = null
                                        }
                                        .testTag("library_tab_${category.name.lowercase()}")
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = category.label,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (selected) colors.onAccentStrong else colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Multi-select select-all helper bar
                        if (isMultiSelectMode && currentSelectablePool.isNotEmpty()) {
                            val allPoolSelected = currentSelectablePool.all { it.id in selectedSongIds }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${selectedSongIds.size} of ${currentSelectablePool.size} tracks selected",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                                TextButton(
                                    onClick = {
                                        selectedSongIds = if (allPoolSelected) {
                                            emptySet()
                                        } else {
                                            currentSelectablePool.map { it.id }.toSet()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (allPoolSelected) "Clear All" else "Select All",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accentStrong
                                    )
                                }
                            }
                        }

                        // Active Offline Category Content
                        AnimatedContent(
                            targetState = selectedCategory,
                            transitionSpec = {
                                SonoraTransitions.tabTransition(
                                    forward = targetState.ordinal >= initialState.ordinal,
                                    motion = motionState
                                )
                            },
                            label = "offline_library_category_transition",
                            modifier = Modifier.weight(1f)
                        ) { category ->
                            when (category) {
                                OfflineLibraryCategory.ALL_SONGS -> SongsCollectionContent(
                                    songs = filteredSongs,
                                    emptyText = if (searchQuery.isNotBlank()) "No local songs match \"$searchQuery\"" else "No local audio tracks found on device",
                                    isGridView = isGridView,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedIds = selectedSongIds,
                                    currentSongId = currentSong?.id,
                                    isPlaying = playbackInfo.isPlaying,
                                    colors = colors,
                                    onPlayAll = { if (filteredSongs.isNotEmpty()) viewModel.playCollection(filteredSongs, shuffle = false) },
                                    onShuffleAll = { if (filteredSongs.isNotEmpty()) viewModel.playCollection(filteredSongs, shuffle = true) },
                                    onSongClick = { song ->
                                        if (isMultiSelectMode) {
                                            selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                        } else {
                                            viewModel.playSong(song, filteredSongs)
                                        }
                                    },
                                    onSongLongClick = { song ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!isMultiSelectMode) {
                                            isMultiSelectMode = true
                                            selectedSongIds = setOf(song.id)
                                        } else {
                                            selectedSongIds = selectedSongIds + song.id
                                        }
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onOpenMore = { contextMenuSong = it }
                                )

                                OfflineLibraryCategory.DOWNLOADS -> {
                                    val downloadedVideos by viewModel.downloadedVideos.collectAsState()
                                    val playableDownloadSongs = displayedDownloadItems.filter { !it.isMissingFile }.map { it.song }
                                    LibraryDownloadsSection(
                                        items = displayedDownloadItems,
                                        sortOption = downloadSortOption,
                                        storageSummary = downloadStorageSummary,
                                        activeDownloadCount = activeDownloadTasksCount,
                                        isMultiSelectMode = isMultiSelectMode,
                                        selectedIds = selectedSongIds,
                                        currentSongId = currentSong?.id,
                                        isPlaying = playbackInfo.isPlaying,
                                        colors = colors,
                                        onSelectSort = { viewModel.setDownloadSortOption(it) },
                                        onOpenDownloadManager = openDownloadManagerScreen,
                                        onPlayAll = {
                                            if (playableDownloadSongs.isNotEmpty()) {
                                                viewModel.playCollection(playableDownloadSongs, shuffle = false)
                                            }
                                        },
                                        onShuffleAll = {
                                            if (playableDownloadSongs.isNotEmpty()) {
                                                viewModel.playCollection(playableDownloadSongs, shuffle = true)
                                            }
                                        },
                                        onTrackClick = { item ->
                                            if (isMultiSelectMode) {
                                                selectedSongIds = if (item.song.id in selectedSongIds) {
                                                    selectedSongIds - item.song.id
                                                } else {
                                                    selectedSongIds + item.song.id
                                                }
                                            } else if (!item.isMissingFile) {
                                                viewModel.playSong(item.song, playableDownloadSongs.ifEmpty { listOf(item.song) })
                                            } else {
                                                singleDownloadedToDelete = item.song
                                            }
                                        },
                                        onTrackLongClick = { item ->
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!isMultiSelectMode) {
                                                isMultiSelectMode = true
                                                selectedSongIds = setOf(item.song.id)
                                            } else {
                                                selectedSongIds = selectedSongIds + item.song.id
                                            }
                                        },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onRequestDelete = { singleDownloadedToDelete = it.song },
                                        onOpenMore = { contextMenuSong = it },
                                        downloadedVideos = downloadedVideos,
                                        onPlayDownloadedVideo = { viewModel.playDownloadedVideo(it) },
                                        onDeleteDownloadedVideo = { viewModel.deleteDownloadedTracks(listOf(it.id)) }
                                    )
                                }

                                OfflineLibraryCategory.PLAYLISTS -> {
                                    OfflinePlaylistsSection(
                                        playlists = displayedLocalPlaylists,
                                        allLocalSongs = allLibrarySongs,
                                        playlistSongResolver = { pid -> playlistSongsMap[pid].orEmpty() },
                                        colors = colors,
                                        onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                                        onDeletePlaylist = { playlist -> viewModel.deletePlaylist(playlist.id) },
                                        onOpenPlaylist = { playlist, songs ->
                                            activeDetailCollection = LibraryDetailCollection(
                                                title = playlist.name,
                                                subtitle = "Local Playlist • ${songs.size} tracks",
                                                songs = songs
                                            )
                                        },
                                        onPlayPlaylist = { songs ->
                                            if (songs.isNotEmpty()) viewModel.playCollection(songs, shuffle = false)
                                        },
                                        onOpenFullPlaylistsManager = onOpenPlaylists
                                    )
                                }

                                OfflineLibraryCategory.RECENTLY_PLAYED -> SongsCollectionContent(
                                    songs = displayedRecentlyPlayedLocal,
                                    emptyText = "Play local songs to build your Recently Played list",
                                    isGridView = isGridView,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedIds = selectedSongIds,
                                    currentSongId = currentSong?.id,
                                    isPlaying = playbackInfo.isPlaying,
                                    colors = colors,
                                    onPlayAll = { if (displayedRecentlyPlayedLocal.isNotEmpty()) viewModel.playCollection(displayedRecentlyPlayedLocal, shuffle = false) },
                                    onShuffleAll = { if (displayedRecentlyPlayedLocal.isNotEmpty()) viewModel.playCollection(displayedRecentlyPlayedLocal, shuffle = true) },
                                    onSongClick = { song ->
                                        if (isMultiSelectMode) {
                                            selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                        } else {
                                            viewModel.playSong(song, displayedRecentlyPlayedLocal)
                                        }
                                    },
                                    onSongLongClick = { song ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectMode = true
                                        selectedSongIds = selectedSongIds + song.id
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onOpenMore = { contextMenuSong = it }
                                )

                                OfflineLibraryCategory.RECENTLY_ADDED -> SongsCollectionContent(
                                    songs = displayedRecentSongs,
                                    emptyText = "No recently added local tracks",
                                    isGridView = isGridView,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedIds = selectedSongIds,
                                    currentSongId = currentSong?.id,
                                    isPlaying = playbackInfo.isPlaying,
                                    colors = colors,
                                    onPlayAll = { if (displayedRecentSongs.isNotEmpty()) viewModel.playCollection(displayedRecentSongs, shuffle = false) },
                                    onShuffleAll = { if (displayedRecentSongs.isNotEmpty()) viewModel.playCollection(displayedRecentSongs, shuffle = true) },
                                    onSongClick = { song ->
                                        if (isMultiSelectMode) {
                                            selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                        } else {
                                            viewModel.playSong(song, displayedRecentSongs)
                                        }
                                    },
                                    onSongLongClick = { song ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectMode = true
                                        selectedSongIds = selectedSongIds + song.id
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onOpenMore = { contextMenuSong = it }
                                )

                                OfflineLibraryCategory.MOST_PLAYED -> SongsCollectionContent(
                                    songs = displayedMostPlayedSongs,
                                    emptyText = "Play local music to populate Most Played",
                                    isGridView = isGridView,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedIds = selectedSongIds,
                                    currentSongId = currentSong?.id,
                                    isPlaying = playbackInfo.isPlaying,
                                    colors = colors,
                                    onPlayAll = { if (displayedMostPlayedSongs.isNotEmpty()) viewModel.playCollection(displayedMostPlayedSongs, shuffle = false) },
                                    onShuffleAll = { if (displayedMostPlayedSongs.isNotEmpty()) viewModel.playCollection(displayedMostPlayedSongs, shuffle = true) },
                                    onSongClick = { song ->
                                        if (isMultiSelectMode) {
                                            selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                        } else {
                                            viewModel.playSong(song, displayedMostPlayedSongs)
                                        }
                                    },
                                    onSongLongClick = { song ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectMode = true
                                        selectedSongIds = selectedSongIds + song.id
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onOpenMore = { contextMenuSong = it }
                                )

                                OfflineLibraryCategory.FAVOURITES -> SongsCollectionContent(
                                    songs = displayedFavSongs,
                                    emptyText = "Tap the heart on any local track to save it here",
                                    isGridView = isGridView,
                                    isMultiSelectMode = isMultiSelectMode,
                                    selectedIds = selectedSongIds,
                                    currentSongId = currentSong?.id,
                                    isPlaying = playbackInfo.isPlaying,
                                    colors = colors,
                                    onPlayAll = { if (displayedFavSongs.isNotEmpty()) viewModel.playCollection(displayedFavSongs, shuffle = false) },
                                    onShuffleAll = { if (displayedFavSongs.isNotEmpty()) viewModel.playCollection(displayedFavSongs, shuffle = true) },
                                    onSongClick = { song ->
                                        if (isMultiSelectMode) {
                                            selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                        } else {
                                            viewModel.playSong(song, displayedFavSongs)
                                        }
                                    },
                                    onSongLongClick = { song ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isMultiSelectMode = true
                                        selectedSongIds = selectedSongIds + song.id
                                    },
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onOpenMore = { contextMenuSong = it }
                                )

                                OfflineLibraryCategory.ALBUMS -> {
                                    if (displayedAlbums.isEmpty()) {
                                        LibraryEmptyState("No local albums match your filter", colors)
                                    } else if (isGridView) {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(156.dp),
                                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 152.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(displayedAlbums, key = { "${it.id}_${it.title}" }) { album ->
                                                val repSong = remember(album, allLibrarySongs) {
                                                    allLibrarySongs.firstOrNull { it.album == album.title }
                                                }
                                                EditorialArtworkCard(
                                                    song = repSong,
                                                    title = album.title,
                                                    subtitle = "${album.artist} • ${album.songCount} tracks",
                                                    colors = colors,
                                                    artworkSize = 152.dp,
                                                    cornerRadius = 24.dp,
                                                    showPlayBadge = true,
                                                    onClick = {
                                                        val albumSongs = allLibrarySongs.filter { it.album == album.title }
                                                        activeDetailCollection = LibraryDetailCollection(
                                                            title = album.title,
                                                            subtitle = album.artist,
                                                            songs = albumSongs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 152.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(displayedAlbums, key = { "${it.id}_${it.title}" }) { album ->
                                                val repSong = remember(album, allLibrarySongs) {
                                                    allLibrarySongs.firstOrNull { it.album == album.title }
                                                }
                                                EditorialAlbumRow(
                                                    album = album,
                                                    repSong = repSong,
                                                    colors = colors,
                                                    onClick = {
                                                        val albumSongs = allLibrarySongs.filter { it.album == album.title }
                                                        activeDetailCollection = LibraryDetailCollection(
                                                            title = album.title,
                                                            subtitle = album.artist,
                                                            songs = albumSongs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                OfflineLibraryCategory.ARTISTS -> {
                                    if (displayedArtists.isEmpty()) {
                                        LibraryEmptyState("No local artists match your filter", colors)
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 152.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(displayedArtists, key = { it.name }) { artist ->
                                                val repSong = remember(artist, allLibrarySongs) {
                                                    allLibrarySongs.firstOrNull { it.artist == artist.name }
                                                }
                                                EditorialArtistRow(
                                                    artist = artist,
                                                    repSong = repSong,
                                                    colors = colors,
                                                    onClick = {
                                                        val artistSongs = allLibrarySongs.filter { it.artist == artist.name }
                                                        activeDetailCollection = LibraryDetailCollection(
                                                            title = artist.name,
                                                            subtitle = "${artist.albumCount} albums",
                                                            songs = artistSongs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                OfflineLibraryCategory.GENRES -> {
                                    if (genres.isEmpty()) {
                                        LibraryEmptyState("No genres match your filter", colors)
                                    } else {
                                        LazyVerticalGrid(
                                            columns = GridCells.Adaptive(156.dp),
                                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 152.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            items(genres, key = { it.name }) { genre ->
                                                EditorialGenreCard(
                                                    genre = genre,
                                                    colors = colors,
                                                    onClick = {
                                                        val genreSongs = allLibrarySongs.filter { it.genre == genre.name }
                                                        activeDetailCollection = LibraryDetailCollection(
                                                            title = genre.name,
                                                            subtitle = "Genre",
                                                            songs = genreSongs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                OfflineLibraryCategory.FOLDERS -> {
                                    AnimatedContent(
                                        targetState = activeFolderDrillDown,
                                        transitionSpec = {
                                            SonoraTransitions.tabTransition(
                                                forward = targetState != null,
                                                motion = motionState
                                            )
                                        },
                                        label = "library_folder_drilldown",
                                        modifier = Modifier.fillMaxSize()
                                    ) { activeFolder ->
                                        if (activeFolder != null) {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                Surface(
                                                    shape = RoundedCornerShape(18.dp),
                                                    color = colors.surfaceTinted,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 20.dp, vertical = 4.dp)
                                                        .border(0.75.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        IconButton(
                                                            onClick = { activeFolderDrillDown = null },
                                                            modifier = Modifier.size(48.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                                                contentDescription = "All Folders",
                                                                tint = colors.textPrimary
                                                            )
                                                        }
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = activeFolder.name,
                                                                fontFamily = OutfitFontFamily,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 15.sp,
                                                                color = colors.textPrimary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Text(
                                                                text = activeFolder.path,
                                                                fontFamily = PlusJakartaFontFamily,
                                                                fontSize = 11.sp,
                                                                color = colors.textSecondary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }

                                                SongsCollectionContent(
                                                    songs = drillDownSongs,
                                                    emptyText = "No visible audio files in this folder",
                                                    isGridView = isGridView,
                                                    isMultiSelectMode = isMultiSelectMode,
                                                    selectedIds = selectedSongIds,
                                                    currentSongId = currentSong?.id,
                                                    isPlaying = playbackInfo.isPlaying,
                                                    colors = colors,
                                                    onPlayAll = { if (drillDownSongs.isNotEmpty()) viewModel.playCollection(drillDownSongs, shuffle = false) },
                                                    onShuffleAll = { if (drillDownSongs.isNotEmpty()) viewModel.playCollection(drillDownSongs, shuffle = true) },
                                                    onSongClick = { song ->
                                                        if (isMultiSelectMode) {
                                                            selectedSongIds = if (song.id in selectedSongIds) selectedSongIds - song.id else selectedSongIds + song.id
                                                        } else {
                                                            viewModel.playSong(song, drillDownSongs)
                                                        }
                                                    },
                                                    onSongLongClick = { song ->
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        isMultiSelectMode = true
                                                        selectedSongIds = selectedSongIds + song.id
                                                    },
                                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                                    onOpenMore = { contextMenuSong = it }
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 152.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                item {
                                                    Surface(
                                                        shape = RoundedCornerShape(20.dp),
                                                        color = colors.accentPill.copy(alpha = 0.45f),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .border(1.dp, colors.accentStrong.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                                            .clip(RoundedCornerShape(20.dp))
                                                            .clickable { openFolderManager() }
                                                            .testTag("library_manage_folders_button")
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(14.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(42.dp)
                                                                    .background(colors.accentStrong, CircleShape),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Rounded.FolderSpecial,
                                                                    contentDescription = null,
                                                                    tint = colors.onAccentStrong,
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(12.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = "Manage Blacklist & Whitelist",
                                                                    fontFamily = OutfitFontFamily,
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 15.sp,
                                                                    color = colors.textPrimary
                                                                )
                                                                Text(
                                                                    text = "Control which storage folders appear in ${domain.branding.AppBranding.APP_NAME}",
                                                                    fontFamily = PlusJakartaFontFamily,
                                                                    fontSize = 12.sp,
                                                                    color = colors.textSecondary
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = Icons.Rounded.ChevronRight,
                                                                contentDescription = "Open Folder Manager",
                                                                tint = colors.textPrimary
                                                            )
                                                        }
                                                    }
                                                }

                                                items(displayedFolders, key = { it.path }) { folder ->
                                                    EditorialFolderRow(
                                                        folder = folder,
                                                        colors = colors,
                                                        onClick = { activeFolderDrillDown = folder }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        // Offline Library Multi-Select Contextual Bottom Action Bar
        AnimatedVisibility(
            visible = librarySectionMode == LibrarySectionMode.OFFLINE && isMultiSelectMode && selectedSongIds.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 144.dp)
        ) {
            val selectedSongsList = remember(selectedSongIds, allLibrarySongs, downloadedSongs) {
                (allLibrarySongs + downloadedSongs)
                    .distinctBy { it.id }
                    .filter { it.id in selectedSongIds }
            }
            val removableCount = selectedSongsList.count { it.sourceType == "DOWNLOADED" || (!it.isOnline && !it.isDemoTrack) }
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = colors.surface,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .border(1.dp, colors.accentStrong.copy(alpha = 0.35f), RoundedCornerShape(26.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MultiSelectBarAction(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        label = "Play Next",
                        colors = colors,
                        onClick = {
                            selectedSongsList.asReversed().forEach { viewModel.playNextInQueue(it) }
                            isMultiSelectMode = false
                            selectedSongIds = emptySet()
                        }
                    )
                    MultiSelectBarAction(
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        label = "Add Queue",
                        colors = colors,
                        onClick = {
                            selectedSongsList.forEach { viewModel.addToQueue(it) }
                            isMultiSelectMode = false
                            selectedSongIds = emptySet()
                        }
                    )
                    MultiSelectBarAction(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        label = "Playlist",
                        colors = colors,
                        onClick = { showBatchPlaylistPicker = true }
                    )
                    if (removableCount > 0) {
                        MultiSelectBarAction(
                            icon = Icons.Rounded.DeleteOutline,
                            label = "Delete",
                            colors = colors,
                            modifier = Modifier.testTag("multiselect_delete_downloads_button"),
                            onClick = { showBatchDeleteConfirmDialog = true }
                        )
                    }
                }
            }
        }
    }

    // --- Offline Library Sort Bottom Sheet ---
    if (showSortSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            sheetState = sheetState,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Sort Offline Library",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                val options = listOf(
                    SortOption.TITLE_ASC to "Title (A – Z)",
                    SortOption.TITLE_DESC to "Title (Z – A)",
                    SortOption.ARTIST_ASC to "Artist Name",
                    SortOption.ALBUM_ASC to "Album Title",
                    SortOption.DURATION_DESC to "Duration (Longest first)",
                    SortOption.DATE_ADDED_DESC to "Recently Added"
                )
                options.forEach { (opt, label) ->
                    val active = sortOption == opt
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) colors.accentPill.copy(alpha = 0.55f) else Color.Transparent)
                            .clickable {
                                viewModel.setSortOption(opt)
                                showSortSheet = false
                            }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        if (active) {
                            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = colors.accentStrong)
                        }
                    }
                }
            }
        }
    }

    // --- Offline Album / Artist / Genre / Playlist Detail Sheet ---
    activeDetailCollection?.let { detail ->
        CollectionDetailSheet(
            title = detail.title,
            subtitle = detail.subtitle,
            songs = detail.songs,
            currentPlayingSongId = currentSong?.id,
            isPlaying = playbackInfo.isPlaying,
            colors = colors,
            onDismiss = { activeDetailCollection = null },
            onPlayAll = {
                viewModel.playCollection(detail.songs, shuffle = false)
                activeDetailCollection = null
            },
            onShufflePlay = {
                viewModel.playCollection(detail.songs, shuffle = true)
                activeDetailCollection = null
            },
            onPlaySong = { song ->
                viewModel.playSong(song, detail.songs)
            },
            onSongMoreClick = { song ->
                contextMenuSong = song
            }
        )
    }

    // --- Offline Song Long-Press / More Menu Sheet ---
    contextMenuSong?.let { target ->
        MusicContextMenuSheet(
            song = target,
            colors = colors,
            isDownloaded = viewModel.isSongDownloaded(target),
            isSavedOnline = target.id in onlineSavedTrackIds,
            onToggleSaveOnline = if (target.isOnline) {
                { viewModel.toggleSaveOnlineTrack(target) }
            } else null,
            onAddToOnlinePlaylist = if (target.isOnline) {
                { songForOnlinePlaylistPicker = target }
            } else null,
            onDownload = if (target.isOnline) {
                { viewModel.requestDownloadSong(target) }
            } else null,
            onDeleteDownload = if (target.sourceType == "DOWNLOADED") {
                { singleDownloadedToDelete = target }
            } else null,
            onDeleteLocalTrack = if (!target.isOnline && target.sourceType != "DOWNLOADED" && !target.isDemoTrack) {
                { singleLocalTrackToDelete = target }
            } else null,
            onDismiss = { contextMenuSong = null },
            onPlayNow = {
                val pool = when {
                    target.isOnline -> (onlineSavedTracks + recentlyPlayedOnlineTracks).distinctBy { it.id }.ifEmpty { listOf(target) }
                    target.sourceType == "DOWNLOADED" -> downloadedSongs.ifEmpty { listOf(target) }
                    else -> allLibrarySongs
                }
                viewModel.playSong(target, pool)
            },
            onAddToQueue = { viewModel.addToQueue(target) },
            onPlayNext = { viewModel.playNextInQueue(target) },
            onAddToPlaylist = { singleSongForPlaylist = target },
            onToggleFavorite = { viewModel.toggleFavorite(target) },
            onViewAlbum = {
                val albumSongs = (allLibrarySongs + downloadedSongs).filter { it.album == target.album }
                activeDetailCollection = LibraryDetailCollection(
                    title = target.album,
                    subtitle = target.artist,
                    songs = albumSongs
                )
            },
            onViewArtist = {
                val artistSongs = (allLibrarySongs + downloadedSongs).filter { it.artist == target.artist }
                activeDetailCollection = LibraryDetailCollection(
                    title = target.artist,
                    subtitle = "Artist",
                    songs = artistSongs
                )
            }
        )
    }

    // --- Single song local playlist picker ---
    singleSongForPlaylist?.let { s ->
        EditorialPlaylistPickerDialog(
            song = s,
            playlists = playlists,
            colors = colors,
            onDismiss = { singleSongForPlaylist = null },
            onSelectPlaylist = { pid ->
                viewModel.addSongToPlaylist(pid, s.id)
                singleSongForPlaylist = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    // --- Batch multi-select local playlist picker ---
    if (showBatchPlaylistPicker) {
        BatchAddToPlaylistDialog(
            selectedCount = selectedSongIds.size,
            playlists = playlists,
            colors = colors,
            onDismiss = { showBatchPlaylistPicker = false },
            onSelectPlaylist = { pid ->
                viewModel.addSongsToPlaylist(pid, selectedSongIds.toList())
                showBatchPlaylistPicker = false
                isMultiSelectMode = false
                selectedSongIds = emptySet()
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    // --- Metadata Editor ---
    songToEdit?.let { s ->
        EditMetadataEditorialDialog(
            song = s,
            colors = colors,
            onDismiss = { songToEdit = null },
            onSave = { newTitle, newArtist, newAlbum, newGenre, lyrics ->
                viewModel.updateSongMetadata(s.id, newTitle, newArtist, newAlbum, newGenre, lyrics)
                songToEdit = null
            }
        )
    }

    // --- Delete Single Downloaded Track Confirmation ---
    singleDownloadedToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { singleDownloadedToDelete = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Delete Downloaded Track?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Remove \"${song.title}\" from your downloads folder and Offline Library? Original local device files outside ${domain.branding.AppBranding.APP_NAME} downloads are never touched.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDownloadedTracks(listOf(song.id))
                        singleDownloadedToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_single_download")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { singleDownloadedToDelete = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            modifier = Modifier.testTag("confirm_delete_download_dialog")
        )
    }

    // --- Delete Single Local Device Track Confirmation ---
    singleLocalTrackToDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { singleLocalTrackToDelete = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Delete Track From Device?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Remove \"${song.title}\" from device storage? Android may ask you to confirm this action.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        requestSafeDeleteLocalSongs(listOf(song))
                        singleLocalTrackToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_delete_single_local_track")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { singleLocalTrackToDelete = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // --- Batch Delete Selected Offline Tracks Confirmation ---
    if (showBatchDeleteConfirmDialog) {
        val selectedSongsToDelete = remember(selectedSongIds, allLibrarySongs, downloadedSongs) {
            (allLibrarySongs + downloadedSongs)
                .distinctBy { it.id }
                .filter { it.id in selectedSongIds }
                .filter { it.sourceType == "DOWNLOADED" || (!it.isOnline && !it.isDemoTrack) }
        }
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirmDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Delete ${selectedSongsToDelete.size} Selected Tracks?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This removes the selected offline tracks from storage and your Offline Library.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        requestSafeDeleteLocalSongs(selectedSongsToDelete)
                        showBatchDeleteConfirmDialog = false
                        isMultiSelectMode = false
                        selectedSongIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_batch_delete_downloads")
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirmDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            modifier = Modifier.testTag("confirm_batch_delete_downloads_dialog")
        )
    }

    // --- Online Library Dialogs & Sheets ---
    if (showOnlineSortSheet) {
        OnlineLibrarySortSheet(
            selectedSort = onlineLibrarySortOption,
            colors = colors,
            onSelect = { viewModel.setOnlineLibrarySortOption(it) },
            onDismiss = { showOnlineSortSheet = false }
        )
    }

    if (showCreateOnlinePlaylistDialog) {
        CreateOnlinePlaylistDialog(
            colors = colors,
            onDismiss = { showCreateOnlinePlaylistDialog = false },
            onCreate = { name -> viewModel.createOnlinePlaylist(name) }
        )
    }

    val activeOnlinePlaylist = remember(activeOnlinePlaylistId, onlinePlaylists) {
        activeOnlinePlaylistId?.let { id -> onlinePlaylists.firstOrNull { it.id == id } }
    }
    activeOnlinePlaylist?.let { playlist ->
        OnlinePlaylistDetailSheet(
            playlist = playlist,
            currentSongId = currentSong?.id,
            isPlaying = playbackInfo.isPlaying,
            colors = colors,
            onDismiss = { activeOnlinePlaylistId = null },
            onPlayAll = { shuffle ->
                if (playlist.tracks.isNotEmpty()) {
                    viewModel.playCollection(playlist.tracks, shuffle = shuffle)
                }
            },
            onPlaySong = { song ->
                viewModel.playSong(song, playlist.tracks)
            },
            onRemoveSong = { song ->
                viewModel.removeTrackFromOnlinePlaylist(playlist.id, song.id)
            },
            onSongMore = { song ->
                onlineTrackForOptions = OnlineTrackMapper.toOnlineTrack(song)
            },
            onRename = { playlistToRename = playlist },
            onDeletePlaylist = {
                playlistToDelete = playlist
                activeOnlinePlaylistId = null
            }
        )
    }

    playlistToRename?.let { playlist ->
        RenameOnlinePlaylistDialog(
            currentName = playlist.name,
            colors = colors,
            onDismiss = { playlistToRename = null },
            onRename = { newName ->
                viewModel.renameOnlinePlaylist(playlist.id, newName)
            }
        )
    }

    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Delete Online Playlist?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Remove \"${playlist.name}\" from your Online Library? Your saved songs remain untouched.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteOnlinePlaylist(playlist.id, playlist.name)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    songForOnlinePlaylistPicker?.let { song ->
        OnlinePlaylistPickerDialog(
            song = song,
            playlists = onlinePlaylists,
            colors = colors,
            onDismiss = { songForOnlinePlaylistPicker = null },
            onSelectPlaylist = { targetPlaylist ->
                viewModel.addTrackToOnlinePlaylist(targetPlaylist.id, song, targetPlaylist.name)
            },
            onCreatePlaylistWithSong = { name ->
                viewModel.createOnlinePlaylist(name, initialTrack = song)
            }
        )
    }

    videoForOptions?.let { video ->
        VideoOptionsBottomSheet(
            video = video,
            isWatchLater = video.videoId in watchLaterVideoIds,
            isSaved = video.videoId in savedVideoIds,
            colors = colors,
            onPlayNow = { viewModel.videoController.playVideo(video, expandPlayer = true) },
            onPlayNext = { viewModel.videoController.enqueueVideoNext(video) },
            onToggleWatchLater = { viewModel.videoController.toggleWatchLater(video) },
            onToggleSave = { viewModel.videoController.toggleSaveVideo(video) },
            onAddToPlaylist = { videoForPlaylistPicker = video },
            onOpenChannel = video.channelUrl?.takeIf { it.isNotBlank() }?.let { url ->
                { viewModel.videoController.openChannelProfile(url, video.channelName) }
            },
            onShare = { shareVideoLink(context, video) },
            onOpenOfficialApp = { openOfficialExternalUrl(context, video.contentUrl) },
            onDismiss = { videoForOptions = null }
        )
    }

    videoForPlaylistPicker?.let { video ->
        VideoPlaylistPickerDialog(
            video = video,
            playlists = videoPlaylists,
            colors = colors,
            onSelectPlaylist = { playlist ->
                viewModel.videoController.addVideoToPlaylist(playlist.id, playlist.name, video)
            },
            onCreatePlaylistWithVideo = { name ->
                viewModel.videoController.createVideoPlaylist(name, video)
            },
            onDismiss = { videoForPlaylistPicker = null }
        )
    }

    onlineTrackForOptions?.let { track ->
        val stableId = OnlineTrackMapper.computeStableId(track.providerKey, track.contentUrl)
        val matchingSong = remember(track, onlineSavedTracks, onlineFavoriteTracks, recentlyPlayedOnlineTracks) {
            (onlineSavedTracks + onlineFavoriteTracks + recentlyPlayedOnlineTracks)
                .firstOrNull { it.id == stableId }
                ?: OnlineTrackMapper.toUnifiedSong(track)
        }
        val artistUrl = if (track.kind == OnlineItemKind.ARTIST) track.contentUrl else track.artistUrl.orEmpty()
        val isArtistFollowed = remember(track, followedOnlineArtistIds) {
            artistUrl.isNotBlank() &&
                OnlineTrackMapper.computeStableId(track.providerKey.ifBlank { "online" }, artistUrl.trim()) in followedOnlineArtistIds
        }
        val isCollectionSaved = remember(track, savedOnlineAlbumIds, savedOnlinePlaylistUrls) {
            when (track.kind) {
                OnlineItemKind.ALBUM -> stableId in savedOnlineAlbumIds
                OnlineItemKind.PLAYLIST -> track.contentUrl.trim() in savedOnlinePlaylistUrls
                else -> false
            }
        }
        OnlineTrackOptionsSheet(
            track = track,
            isSaved = stableId in onlineSavedTrackIds,
            isFavorite = matchingSong.isFavorite,
            isDownloaded = viewModel.isTrackDownloaded(track),
            isArtistFollowed = isArtistFollowed,
            isCollectionSaved = isCollectionSaved,
            resolvedStream = null,
            colors = colors,
            onPlayNow = {
                if (track.kind == OnlineItemKind.TRACK) {
                    viewModel.playOnlineTrack(track)
                } else {
                    viewModel.openOnlineCollection(track)
                }
            },
            onPlayNext = { viewModel.enqueueOnlineTrackNext(track) },
            onAddToQueue = { viewModel.addOnlineTrackToQueue(track) },
            onToggleSave = { viewModel.toggleSaveOnlineTrack(track) },
            onToggleFavorite = { viewModel.toggleFavorite(matchingSong) },
            onAddToPlaylist = { singleSongForPlaylist = matchingSong },
            onAddToOnlinePlaylist = if (track.kind == OnlineItemKind.TRACK) {
                { songForOnlinePlaylistPicker = matchingSong }
            } else null,
            onToggleFollowArtist = if (artistUrl.isNotBlank()) {
                { viewModel.toggleFollowOnlineArtist(track) }
            } else null,
            onToggleSaveCollection = when (track.kind) {
                OnlineItemKind.ALBUM -> ({ viewModel.toggleSaveOnlineAlbum(track) })
                OnlineItemKind.PLAYLIST -> ({ viewModel.toggleSaveExternalOnlinePlaylist(track) })
                else -> null
            },
            onDownload = if (track.kind == OnlineItemKind.TRACK) {
                { viewModel.requestDownloadTrack(track) }
            } else null,
            onOpenQualitySettings = { /* handled via player / settings */ },
            onViewArtistChannel = if (!track.artistUrl.isNullOrBlank()) {
                {
                    viewModel.openOnlineCollection(
                        OnlineTrack(
                            stableId = OnlineTrackMapper.computeStableId(track.providerKey, track.artistUrl),
                            providerKey = track.providerKey,
                            providerName = track.providerName,
                            serviceId = track.serviceId,
                            contentUrl = track.artistUrl,
                            title = track.artist,
                            artist = track.artist,
                            artworkUrl = track.artworkUrl,
                            kind = OnlineItemKind.ARTIST,
                            artistUrl = track.artistUrl
                        )
                    )
                }
            } else null,
            onDismiss = { onlineTrackForOptions = null }
        )
    }

    // --- Online Album / Artist / Playlist Collection Sheet ---
    OnlineCollectionDetailsSheet(
        state = onlineCollectionState,
        currentSongId = currentSong?.id,
        isPlaying = playbackInfo.isPlaying,
        isBuffering = playbackInfo.isBuffering,
        savedIds = onlineSavedTrackIds,
        colors = colors,
        isTrackDownloaded = { viewModel.isTrackDownloaded(it) },
        onDownloadTrack = { viewModel.requestDownloadTrack(it) },
        isCollectionSavedOrFollowed = { source ->
            when (source.kind) {
                OnlineItemKind.ARTIST -> OnlineTrackMapper.computeStableId(source.providerKey.ifBlank { "online" }, source.contentUrl.trim()) in followedOnlineArtistIds
                OnlineItemKind.ALBUM -> OnlineTrackMapper.computeStableId(source.providerKey.ifBlank { "online" }, source.contentUrl.trim()) in savedOnlineAlbumIds
                OnlineItemKind.PLAYLIST -> source.contentUrl.trim() in savedOnlinePlaylistUrls
                else -> false
            }
        },
        onToggleSaveOrFollowCollection = { source, details ->
            when (source.kind) {
                OnlineItemKind.ARTIST -> viewModel.toggleFollowOnlineArtist(source, details)
                OnlineItemKind.ALBUM -> viewModel.toggleSaveOnlineAlbum(source, details)
                OnlineItemKind.PLAYLIST -> viewModel.toggleSaveExternalOnlinePlaylist(source, details)
                else -> Unit
            }
        },
        onPlayTrack = { track, allTracks ->
            viewModel.playOnlineTrack(track, allTracks)
        },
        onPlayAll = { tracks, shuffle ->
            val songs = tracks.map { OnlineTrackMapper.toUnifiedSong(it) }
            viewModel.playCollection(songs, shuffle = shuffle)
        },
        onRetry = { item -> viewModel.openOnlineCollection(item) },
        onTrackMoreClick = { track -> onlineTrackForOptions = track },
        onDismiss = { viewModel.dismissOnlineCollection() }
    )

    AnimatedVisibility(
        visible = showInternalFolderManagement,
        enter = SonoraTransitions.pushEnter(motionState),
        exit = SonoraTransitions.pushExit(motionState)
    ) {
        BackHandler { showInternalFolderManagement = false }
        LibraryFolderManagementScreen(
            viewModel = viewModel,
            onBack = { showInternalFolderManagement = false }
        )
    }

    AnimatedVisibility(
        visible = showInternalDownloadManager,
        enter = SonoraTransitions.pushEnter(motionState),
        exit = SonoraTransitions.pushExit(motionState)
    ) {
        BackHandler { showInternalDownloadManager = false }
        DownloadManagerScreen(
            viewModel = viewModel,
            onBack = { showInternalDownloadManager = false }
        )
    }
}

@Composable
private fun HeaderCircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    colors: EditorialColors,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = if (isActive) colors.accentStrong else colors.surface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .size(44.dp)
            .border(0.75.dp, colors.cardBorder, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) colors.onAccentStrong else colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MultiSelectBarAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    colors: EditorialColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = colors.accentStrong, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = colors.textPrimary
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongsCollectionContent(
    songs: List<Song>,
    emptyText: String,
    isGridView: Boolean,
    isMultiSelectMode: Boolean,
    selectedIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenMore: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        LibraryEmptyState(emptyText, colors)
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Count + quick Play/Shuffle pill header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${songs.size} tracks",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = colors.textMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentStrong,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPlayAll() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all", tint = colors.onAccentStrong, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.onAccentStrong)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentPill,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onShuffleAll() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle all", tint = colors.onAccentPill, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.onAccentPill)
                    }
                }
            }
        }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(156.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 152.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(songs, key = { it.id }) { song ->
                    val selected = song.id in selectedIds
                    EditorialArtworkCard(
                        song = song,
                        title = song.title,
                        subtitle = song.artist,
                        colors = colors,
                        artworkSize = 152.dp,
                        cornerRadius = 22.dp,
                        showPlayBadge = !isMultiSelectMode,
                        badgeText = if (isMultiSelectMode && selected) "SELECTED" else null,
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongClick(song) }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 152.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    val isCurrent = currentSongId == song.id
                    val selected = song.id in selectedIds
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = when {
                            selected -> colors.accentPill.copy(alpha = 0.52f)
                            isCurrent -> colors.accentPill.copy(alpha = 0.36f)
                            else -> colors.surface
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                0.75.dp,
                                if (isCurrent || selected) colors.accentStrong.copy(alpha = 0.45f) else colors.cardBorder,
                                RoundedCornerShape(18.dp)
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onClick = { onSongClick(song) },
                                onLongClick = { onSongLongClick(song) }
                            )
                            .testTag("song_item_${song.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isMultiSelectMode) {
                                Icon(
                                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                    contentDescription = if (selected) "Selected" else "Not selected",
                                    tint = if (selected) colors.accentStrong else colors.textMuted,
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(22.dp)
                                )
                            }
                            CircularArtwork(
                                song = song,
                                isPlaying = isCurrent && isPlaying,
                                accentColor = colors.accentPill,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${song.artist} • ${song.album}",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.durationFormatted} • ${song.audioFormatInfo}",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 10.sp,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!isMultiSelectMode) {
                                IconButton(
                                    onClick = { onToggleFavorite(song) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (song.isFavorite) colors.accentStrong else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onOpenMore(song) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Track options",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorialAlbumRow(
    album: Album,
    repSong: Song?,
    colors: EditorialColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularArtwork(
                song = repSong,
                fallbackLabel = album.title,
                accentColor = colors.accentPill,
                shadowElevation = 3.dp,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${album.artist} • ${album.songCount} tracks",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted)
        }
    }
}

@Composable
private fun EditorialArtistRow(
    artist: Artist,
    repSong: Song?,
    colors: EditorialColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, RoundedCornerShape(20.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularArtwork(
                song = repSong,
                fallbackLabel = artist.name,
                accentColor = colors.accentPill,
                shadowElevation = 3.dp,
                modifier = Modifier.size(50.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.songCount} tracks • ${artist.albumCount} albums",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted)
        }
    }
}

@Composable
private fun EditorialGenreCard(
    genre: Genre,
    colors: EditorialColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(22.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.onAccentPill, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = genre.name,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${genre.songCount} tracks",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun EditorialFolderRow(
    folder: MusicFolder,
    colors: EditorialColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.onAccentPill, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.songCount} tracks • ${folder.path}",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.textMuted)
        }
    }
}

@Composable
private fun LibraryEmptyState(
    message: String,
    colors: EditorialColors
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = colors.onAccentPill, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = message,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun BatchAddToPlaylistDialog(
    selectedCount: Int,
    playlists: List<Playlist>,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var creatingNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = "Add $selectedCount Tracks",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            if (creatingNew) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (playlists.isEmpty()) {
                Text(
                    text = "No custom playlists yet. Create one below!",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(playlists, key = { it.id }) { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectPlaylist(p.id) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = colors.accentStrong)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = p.name,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName.trim())
                            creatingNew = false
                            newName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong, contentColor = colors.onAccentStrong)
                ) {
                    Text("Create")
                }
            } else {
                TextButton(onClick = { creatingNew = true }) {
                    Text("+ New Playlist", color = colors.accentStrong)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
private fun EditMetadataEditorialDialog(
    song: Song,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String?) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    var genre by remember { mutableStateOf(song.genre) }
    var lyrics by remember { mutableStateOf(song.lyrics ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text("Edit Metadata", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = genre, onValueChange = { genre = it }, label = { Text("Genre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lyrics, onValueChange = { lyrics = it }, label = { Text("Lyrics") }, maxLines = 4, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, artist, album, genre, lyrics.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong, contentColor = colors.onAccentStrong)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}
