package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import com.example.viewmodel.SonoraViewModel
import ui.components.CircularArtwork
import ui.components.CollectionDetailSheet
import ui.components.EditorialArtworkCard
import ui.components.EditorialPlaylistPickerDialog
import ui.components.MusicContextMenuSheet
import ui.settings.LibraryFolderManagementScreen
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import java.io.File

private enum class LibraryCategory(val label: String) {
    ALL_SONGS("All Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
    GENRES("Genres"),
    RECENTLY_ADDED("Recently Added"),
    MOST_PLAYED("Most Played"),
    FAVOURITES("Favourites")
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
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val haptic = LocalHapticFeedback.current

    val allLibrarySongs by viewModel.allSongs.collectAsState()
    val filteredSongs by viewModel.filteredSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    var selectedCategory by remember { mutableStateOf(LibraryCategory.ALL_SONGS) }
    var isGridView by remember { mutableStateOf(false) }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    var showSortSheet by remember { mutableStateOf(false) }
    var activeDetailCollection by remember { mutableStateOf<LibraryDetailCollection?>(null) }
    var activeFolderDrillDown by remember { mutableStateOf<MusicFolder?>(null) }
    var contextMenuSong by remember { mutableStateOf<Song?>(null) }
    var songToEdit by remember { mutableStateOf<Song?>(null) }
    var singleSongForPlaylist by remember { mutableStateOf<Song?>(null) }
    var showBatchPlaylistPicker by remember { mutableStateOf(false) }
    var showInternalFolderManagement by remember { mutableStateOf(false) }

    if (showInternalFolderManagement) {
        BackHandler { showInternalFolderManagement = false }
        LibraryFolderManagementScreen(
            viewModel = viewModel,
            onBack = { showInternalFolderManagement = false }
        )
        return
    }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedSongIds = emptySet()
    }
    BackHandler(enabled = activeFolderDrillDown != null && !isMultiSelectMode) {
        activeFolderDrillDown = null
    }

    val openFolderManager: () -> Unit = {
        onOpenFolderManagement?.invoke() ?: run { showInternalFolderManagement = true }
    }

    val filterListByQueryAndSort: (List<Song>) -> List<Song> = { list ->
        val searched = if (searchQuery.isBlank()) list else {
            list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true) ||
                    it.genre.contains(searchQuery, ignoreCase = true)
            }
        }
        when (sortOption) {
            SortOption.TITLE_ASC -> searched.sortedBy { it.title.lowercase() }
            SortOption.TITLE_DESC -> searched.sortedByDescending { it.title.lowercase() }
            SortOption.ARTIST_ASC -> searched.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM_ASC -> searched.sortedBy { it.album.lowercase() }
            SortOption.DURATION_DESC -> searched.sortedByDescending { it.durationMs }
            SortOption.DATE_ADDED_DESC -> searched.sortedByDescending { it.dateAdded }
        }
    }

    val displayedRecentSongs = remember(recentlyAdded, searchQuery, sortOption) { filterListByQueryAndSort(recentlyAdded) }
    val displayedMostPlayedSongs = remember(mostPlayed, searchQuery, sortOption) { filterListByQueryAndSort(mostPlayed) }
    val displayedFavSongs = remember(favorites, searchQuery, sortOption) { filterListByQueryAndSort(favorites) }
    val drillDownSongs = remember(activeFolderDrillDown, allLibrarySongs, searchQuery, sortOption) {
        val folder = activeFolderDrillDown ?: return@remember emptyList()
        filterListByQueryAndSort(
            allLibrarySongs.filter { (File(it.dataPath).parent ?: "/storage") == folder.path }
        )
    }

    val currentSelectablePool: List<Song> = when (selectedCategory) {
        LibraryCategory.ALL_SONGS -> filteredSongs
        LibraryCategory.RECENTLY_ADDED -> displayedRecentSongs
        LibraryCategory.MOST_PLAYED -> displayedMostPlayedSongs
        LibraryCategory.FAVOURITES -> displayedFavSongs
        LibraryCategory.FOLDERS -> if (activeFolderDrillDown != null) drillDownSongs else emptyList()
        else -> emptyList()
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
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
                    Text(
                        text = "${allLibrarySongs.size} tracks • ${albums.size} albums",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onOpenPlaylists != null) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.accentPill,
                            modifier = Modifier
                                .height(40.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable { onOpenPlaylists() }
                                .testTag("library_playlists_shortcut")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = "Open Playlists",
                                    tint = colors.onAccentPill,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Playlists",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.onAccentPill
                                )
                            }
                        }
                    }

                    // Multi-select toggle
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

                    // Folder Blacklist / Whitelist shortcut
                    HeaderCircleAction(
                        icon = Icons.Rounded.FolderSpecial,
                        contentDescription = "Manage Folder Blacklist & Whitelist",
                        isActive = false,
                        colors = colors,
                        testTag = "library_folder_settings_button",
                        onClick = openFolderManager
                    )

                    // Rescan library
                    HeaderCircleAction(
                        icon = Icons.Rounded.Refresh,
                        contentDescription = "Rescan Library",
                        isActive = false,
                        colors = colors,
                        testTag = "rescan_library_button",
                        onClick = { viewModel.loadLocalSongs() }
                    )
                }
            }

            // Inline Search + Sort + Grid/List Toggle Row
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
                            text = "Filter songs, albums, artists...",
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
                            contentDescription = "Sort Options",
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

            Spacer(modifier = Modifier.height(12.dp))

            // 8 Editorial Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryCategory.entries.forEach { category ->
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
                                if (category != LibraryCategory.FOLDERS) activeFolderDrillDown = null
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

            // Active Category Content
            when (selectedCategory) {
                LibraryCategory.ALL_SONGS -> SongsCollectionContent(
                    songs = filteredSongs,
                    emptyText = "No local audio tracks found",
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

                LibraryCategory.RECENTLY_ADDED -> SongsCollectionContent(
                    songs = displayedRecentSongs,
                    emptyText = "No recently added tracks",
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

                LibraryCategory.MOST_PLAYED -> SongsCollectionContent(
                    songs = displayedMostPlayedSongs,
                    emptyText = "Play some music to populate Most Played",
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

                LibraryCategory.FAVOURITES -> SongsCollectionContent(
                    songs = displayedFavSongs,
                    emptyText = "Tap the heart on any track to save it here",
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

                LibraryCategory.ALBUMS -> {
                    if (albums.isEmpty()) {
                        LibraryEmptyState("No albums match your filter", colors)
                    } else if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(156.dp),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 152.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(albums, key = { "${it.id}_${it.title}" }) { album ->
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
                            items(albums, key = { "${it.id}_${it.title}" }) { album ->
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

                LibraryCategory.ARTISTS -> {
                    if (artists.isEmpty()) {
                        LibraryEmptyState("No artists match your filter", colors)
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 152.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(artists, key = { it.name }) { artist ->
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

                LibraryCategory.GENRES -> {
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

                LibraryCategory.FOLDERS -> {
                    val activeFolder = activeFolderDrillDown
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
                                        modifier = Modifier.size(44.dp)
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
                                                text = "Control which storage folders appear in SONORA",
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

                            items(folders, key = { it.path }) { folder ->
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

        // Multi-Select Contextual Bottom Action Bar
        AnimatedVisibility(
            visible = isMultiSelectMode && selectedSongIds.isNotEmpty(),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 144.dp)
        ) {
            val selectedSongsList = remember(selectedSongIds, allLibrarySongs) {
                allLibrarySongs.filter { it.id in selectedSongIds }
            }
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
                }
            }
        }
    }

    // Sort & Filter ModalBottomSheet
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
                    text = "Sort Library",
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

    // Album / Artist / Genre Detail Sheet
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

    // Song Long-Press / More Menu Sheet
    contextMenuSong?.let { target ->
        MusicContextMenuSheet(
            song = target,
            colors = colors,
            onDismiss = { contextMenuSong = null },
            onPlayNow = { viewModel.playSong(target, allLibrarySongs) },
            onAddToQueue = { viewModel.addToQueue(target) },
            onPlayNext = { viewModel.playNextInQueue(target) },
            onAddToPlaylist = { singleSongForPlaylist = target },
            onToggleFavorite = { viewModel.toggleFavorite(target) },
            onViewAlbum = {
                val albumSongs = allLibrarySongs.filter { it.album == target.album }
                activeDetailCollection = LibraryDetailCollection(
                    title = target.album,
                    subtitle = target.artist,
                    songs = albumSongs
                )
            },
            onViewArtist = {
                val artistSongs = allLibrarySongs.filter { it.artist == target.artist }
                activeDetailCollection = LibraryDetailCollection(
                    title = target.artist,
                    subtitle = "Artist",
                    songs = artistSongs
                )
            }
        )
    }

    // Single song playlist picker
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

    // Batch multi-select playlist picker
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

    // Metadata Editor
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
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
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
