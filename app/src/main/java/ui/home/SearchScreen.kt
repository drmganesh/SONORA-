package ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.OnlineSearchUiState
import com.example.viewmodel.SonoraViewModel
import data.online.OnlineTrackMapper
import domain.home.HomeMusicMode
import domain.online.OnlineItemKind
import domain.online.OnlineTrack
import ui.components.CollectionDetailSheet
import ui.components.EditorialPlaylistPickerDialog
import ui.library.OnlinePlaylistPickerDialog
import ui.components.MusicContextMenuSheet
import ui.online.OnlineCollectionDetailsSheet
import ui.online.OnlineQualitySelectorSheet
import ui.online.OnlineTrackOptionsSheet
import ui.online.buildArtistExploreTrack
import ui.theme.EditorialColors
import ui.video.SearchVideoControlsHeader
import ui.video.VideoOptionsBottomSheet
import ui.video.VideoPlaylistPickerDialog
import ui.video.onlineVideoSearchLazyContent
import ui.video.openOfficialExternalUrl
import ui.video.shareVideoLink

/**
 * Mode-aware Search Screen for SONORA.
 * Automatically aligns default scope with [SonoraViewModel.homeMusicMode]:
 * - [HomeMusicMode.OFFLINE]: Searches strictly within local MediaStore tracks/albums/artists/playlists/folders.
 * - [HomeMusicMode.ONLINE]: Searches NewPipe Extractor inline across YT Music, SoundCloud, Bandcamp, Jamendo & Piped.
 * - Users may also explicitly select Combined ("All") scope when they want both in one view.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SonoraViewModel,
    colors: EditorialColors,
    onOpenSettings: () -> Unit,
    onSearchOnline: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val homeMusicMode by viewModel.homeMusicMode.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()

    val onlineProviders by viewModel.onlineProviders.collectAsState()
    val selectedOnlineProvider by viewModel.selectedOnlineProvider.collectAsState()
    val selectedOnlineFilter by viewModel.selectedOnlineFilter.collectAsState()
    val onlineQuery by viewModel.onlineSearchQuery.collectAsState()
    val onlineSuggestions by viewModel.onlineSearchSuggestions.collectAsState()
    val onlineSearchState by viewModel.onlineSearchState.collectAsState()
    val onlineSearchHistory by viewModel.onlineSearchHistory.collectAsState()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsState()
    val isMeteredNetwork by viewModel.isMeteredNetwork.collectAsState()
    val onlineQualityPreference by viewModel.onlineQualityPreference.collectAsState()
    val currentResolvedStream by viewModel.currentResolvedStream.collectAsState()
    val savedOnlineTracks by viewModel.onlineSavedTracks.collectAsState()
    val allOnlineTracks by viewModel.allOnlineTracks.collectAsState()
    val onlineCollectionState by viewModel.onlineCollectionState.collectAsState()
    val downloadedSourceKeys by viewModel.downloadedSourceKeys.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val savedOnlineAlbumIds by viewModel.savedOnlineAlbumIds.collectAsState()
    val followedOnlineArtistIds by viewModel.followedOnlineArtistIds.collectAsState()
    val savedOnlinePlaylistUrls by viewModel.savedOnlinePlaylistUrls.collectAsState()

    val videoController = viewModel.videoController
    val videoSearchFilter by videoController.searchFilter.collectAsState()
    val videoSearchSuggestions by videoController.searchSuggestions.collectAsState()
    val videoSearchState by videoController.searchState.collectAsState()
    val videoSearchHistory by videoController.videoSearchHistory.collectAsState()
    val videoPlayerState by videoController.playerState.collectAsState()
    val watchLaterVideos by videoController.watchLaterVideos.collectAsState()
    val savedVideos by videoController.savedVideos.collectAsState()
    val videoPlaylists by videoController.videoPlaylists.collectAsState()
    val watchLaterIds = remember(watchLaterVideos) { watchLaterVideos.map { it.videoId }.toSet() }
    val savedVideoIds = remember(savedVideos) { savedVideos.map { it.videoId }.toSet() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val savedOnlineIds = remember(savedOnlineTracks) { savedOnlineTracks.map { it.id }.toSet() }
    val favoriteOnlineIds = remember(allOnlineTracks) {
        allOnlineTracks.filter { it.isFavorite }.map { it.id }.toSet()
    }

    var searchScope by remember {
        mutableStateOf(
            when (homeMusicMode) {
                HomeMusicMode.OFFLINE -> SearchSourceScope.LOCAL
                HomeMusicMode.ONLINE -> SearchSourceScope.ONLINE
                HomeMusicMode.ONLINE_VIDEO -> SearchSourceScope.VIDEO
            }
        )
    }

    LaunchedEffect(homeMusicMode) {
        searchScope = when (homeMusicMode) {
            HomeMusicMode.OFFLINE -> SearchSourceScope.LOCAL
            HomeMusicMode.ONLINE -> SearchSourceScope.ONLINE
            HomeMusicMode.ONLINE_VIDEO -> SearchSourceScope.VIDEO
        }
    }

    var query by remember { mutableStateOf(onlineQuery) }
    var selectedLocalFilter by remember { mutableIntStateOf(0) }
    var activeModal by remember { mutableStateOf<SearchCollectionModal?>(null) }
    var contextMenuSong by remember { mutableStateOf<Song?>(null) }
    var onlineOptionsTarget by remember { mutableStateOf<OnlineTrack?>(null) }
    var videoOptionsTarget by remember { mutableStateOf<domain.video.OnlineVideoItem?>(null) }
    var videoPlaylistPickerTarget by remember { mutableStateOf<domain.video.OnlineVideoItem?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var onlinePlaylistPickerSong by remember { mutableStateOf<Song?>(null) }
    var showQualitySheet by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val localFilters = listOf("All", "Songs", "Albums", "Artists", "Playlists", "Folders")

    val smartSuggestions = remember(artists, genres, albums) {
        val topArtists = artists.take(4).map { it.name }
        val topGenres = genres.take(3).map { it.name }
        val topAlbums = albums.take(2).map { it.title }
        (topArtists + topGenres + topAlbums)
            .filter { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            .distinct()
            .take(8)
    }

    val matchedSongs = remember(allSongs, query) {
        if (query.isBlank()) allSongs.take(15)
        else allSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true) ||
                it.genre.contains(query, ignoreCase = true)
        }
    }

    val matchedAlbums = remember(albums, query) {
        if (query.isBlank()) emptyList()
        else albums.filter {
            it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
        }
    }

    val matchedArtists = remember(artists, query) {
        if (query.isBlank()) emptyList()
        else artists.filter { it.name.contains(query, ignoreCase = true) }
    }

    val matchedPlaylists = remember(playlists, query) {
        if (query.isBlank()) emptyList()
        else playlists.filter { it.name.contains(query, ignoreCase = true) }
    }

    val matchedFolders = remember(folders, query) {
        if (query.isBlank()) emptyList()
        else folders.filter {
            it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true)
        }
    }

    val executeSearchForCurrentScope: (String, Boolean) -> Unit = { term, force ->
        query = term
        keyboardController?.hide()
        if (searchScope == SearchSourceScope.VIDEO) {
            videoController.updateSearchQuery(term)
            if (term.isNotBlank()) {
                videoController.executeSearch(term)
            }
        } else {
            if (searchScope == SearchSourceScope.LOCAL || searchScope == SearchSourceScope.COMBINED) {
                if (term.isNotBlank()) viewModel.addRecentSearch(term)
            }
            if (searchScope == SearchSourceScope.ONLINE || searchScope == SearchSourceScope.COMBINED) {
                viewModel.updateOnlineSearchQuery(term)
                if (term.isNotBlank()) {
                    viewModel.executeOnlineSearch(term, forceRefresh = force)
                }
            }
        }
    }

    // Keep reference parameter live for caller compatibility
    @Suppress("UNUSED_EXPRESSION")
    onSearchOnline

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .testTag("search_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Editorial Title Row + Quality/Settings controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Search",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 36.sp,
                        letterSpacing = (-0.8).sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = when (searchScope) {
                            SearchSourceScope.LOCAL -> "Offline library only"
                            SearchSourceScope.ONLINE -> "Streaming via ${selectedOnlineProvider?.displayName ?: "YT Music"}"
                            SearchSourceScope.VIDEO -> "Online Video search via NewPipe Extractor"
                            SearchSourceScope.COMBINED -> "Searching local & online music"
                        },
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.testTag("search_scope_subtitle")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (searchScope == SearchSourceScope.ONLINE || searchScope == SearchSourceScope.COMBINED) {
                        Surface(
                            onClick = { showQualitySheet = true },
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("search_quality_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HighQuality,
                                    contentDescription = "Online Stream Quality",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = onlineQualityPreference.title,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onOpenSettings() },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(colors.accentPill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = colors.onAccentPill,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Explicit Source Scope Toggle (Defaults cleanly to active Home mode)
            SearchScopeSelectorRow(
                selectedScope = searchScope,
                onSelectScope = { newScope ->
                    searchScope = newScope
                    if (newScope == SearchSourceScope.VIDEO) {
                        videoController.updateSearchQuery(query)
                    } else if (newScope != SearchSourceScope.LOCAL && query.isNotBlank()) {
                        viewModel.updateOnlineSearchQuery(query)
                    }
                },
                colors = colors
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Unified Search Field
            OutlinedTextField(
                value = query,
                onValueChange = { updated ->
                    query = updated
                    when (searchScope) {
                        SearchSourceScope.VIDEO -> videoController.updateSearchQuery(updated)
                        SearchSourceScope.ONLINE, SearchSourceScope.COMBINED -> viewModel.updateOnlineSearchQuery(updated)
                        SearchSourceScope.LOCAL -> Unit
                    }
                },
                placeholder = {
                    val hint = when (searchScope) {
                        SearchSourceScope.LOCAL -> "Local songs, artists, albums, or folders..."
                        SearchSourceScope.ONLINE -> "Search songs, artists, albums online..."
                        SearchSourceScope.VIDEO -> "Search videos, channels, playlists..."
                        SearchSourceScope.COMBINED -> "Search offline & online music..."
                    }
                    Text(
                        text = hint,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 14.sp,
                        color = colors.textMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = colors.accentStrong
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                when (searchScope) {
                                    SearchSourceScope.VIDEO -> videoController.updateSearchQuery("")
                                    SearchSourceScope.ONLINE, SearchSourceScope.COMBINED -> viewModel.updateOnlineSearchQuery("")
                                    SearchSourceScope.LOCAL -> Unit
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear search",
                                tint = colors.textSecondary
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { executeSearchForCurrentScope(query, false) }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    focusedBorderColor = colors.accentStrong,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("editorial_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Mode-specific Filter & Provider Strip
            AnimatedVisibility(visible = searchScope == SearchSourceScope.LOCAL || searchScope == SearchSourceScope.COMBINED) {
                Column {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("search_local_filters")
                    ) {
                        items(localFilters.indices.toList(), key = { "loc_flt_$it" }) { idx ->
                            val selected = selectedLocalFilter == idx
                            Surface(
                                onClick = { selectedLocalFilter = idx },
                                shape = RoundedCornerShape(20.dp),
                                color = if (selected) colors.accentPill else colors.surface,
                                border = BorderStroke(
                                    width = 0.75.dp,
                                    color = if (selected) colors.accentStrong else colors.cardBorder
                                ),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = localFilters[idx],
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (selected) colors.onAccentPill else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            AnimatedVisibility(visible = searchScope == SearchSourceScope.ONLINE || searchScope == SearchSourceScope.COMBINED) {
                Column {
                    SearchOnlineControlsHeader(
                        isNetworkOnline = isNetworkOnline,
                        providers = onlineProviders,
                        selectedProvider = selectedOnlineProvider,
                        selectedFilter = selectedOnlineFilter,
                        query = query,
                        suggestions = onlineSuggestions,
                        colors = colors,
                        onSelectProvider = { viewModel.selectOnlineProvider(it.key) },
                        onSelectFilter = { viewModel.selectOnlineFilter(it) },
                        onExecuteSuggestion = { sug -> executeSearchForCurrentScope(sug, false) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            AnimatedVisibility(visible = searchScope == SearchSourceScope.VIDEO) {
                Column {
                    SearchVideoControlsHeader(
                        isNetworkOnline = isNetworkOnline,
                        selectedFilter = videoSearchFilter,
                        query = query,
                        suggestions = videoSearchSuggestions,
                        colors = colors,
                        onSelectFilter = { videoController.selectSearchFilter(it) },
                        onExecuteSuggestion = { sug -> executeSearchForCurrentScope(sug, false) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("search_results_list"),
                contentPadding = PaddingValues(bottom = 184.dp, top = 4.dp)
            ) {
                if (searchScope == SearchSourceScope.VIDEO) {
                    onlineVideoSearchLazyContent(
                        searchState = videoSearchState,
                        searchHistory = videoSearchHistory,
                        query = query,
                        activeVideoId = videoPlayerState.activeVideo?.videoId,
                        colors = colors,
                        onExecuteSearch = { term -> executeSearchForCurrentScope(term, false) },
                        onRemoveHistory = { videoController.removeSearchHistory(it) },
                        onClearHistory = { videoController.clearSearchHistory() },
                        onPlayVideo = { video, pool -> videoController.playVideo(video, pool, expandPlayer = true) },
                        onOpenChannel = { ch ->
                            videoController.openChannelProfile(
                                channelUrl = ch.channelUrl,
                                channelName = ch.name,
                                initialAvatarUrl = ch.avatarUrl
                            )
                        },
                        onOpenPlaylist = { pl -> videoController.openChannelPlaylistDetail(pl) },
                        onVideoMoreClick = { videoOptionsTarget = it },
                        onLoadMore = { videoController.loadMoreSearchResults() }
                    )
                }

                if (searchScope == SearchSourceScope.ONLINE || searchScope == SearchSourceScope.COMBINED) {
                    onlineSearchLazyContent(
                        searchState = onlineSearchState,
                        searchHistory = onlineSearchHistory,
                        selectedProvider = selectedOnlineProvider,
                        selectedFilter = selectedOnlineFilter,
                        query = query,
                        currentSong = currentSong,
                        playbackInfo = playbackInfo,
                        savedIds = savedOnlineIds,
                        downloadedKeys = downloadedSourceKeys,
                        canonicalKeyForTrack = { viewModel.canonicalSourceKeyForTrack(it) },
                        colors = colors,
                        showCombinedHeader = searchScope == SearchSourceScope.COMBINED,
                        onExecuteSearch = { term, force -> executeSearchForCurrentScope(term, force) },
                        onRemoveHistory = { viewModel.removeOnlineSearchHistory(it) },
                        onClearHistory = { viewModel.clearOnlineSearchHistory() },
                        onPlayTrack = { track, pool -> viewModel.playOnlineTrack(track, pool) },
                        onOpenCollection = { viewModel.openOnlineCollection(it) },
                        onMoreClick = { onlineOptionsTarget = it },
                        onDownloadClick = { viewModel.requestDownloadTrack(it) },
                        onLoadMore = { viewModel.loadMoreOnlineSearchResults() },
                        onRetryPlayback = { viewModel.retryCurrentPlayback() },
                        onSwitchSoundCloudFallback = { viewModel.switchToSoundCloudFallback() },
                        onDismissPlaybackError = { viewModel.clearPlaybackError() }
                    )
                }

                if (searchScope == SearchSourceScope.LOCAL || searchScope == SearchSourceScope.COMBINED) {
                    localSearchLazyContent(
                        query = query,
                        selectedFilter = selectedLocalFilter,
                        allSongs = allSongs,
                        genres = genres,
                        recentSearches = recentSearches,
                        smartSuggestions = smartSuggestions,
                        matchedSongs = matchedSongs,
                        matchedAlbums = matchedAlbums,
                        matchedArtists = matchedArtists,
                        matchedPlaylists = matchedPlaylists,
                        matchedFolders = matchedFolders,
                        currentSong = currentSong,
                        playbackInfo = playbackInfo,
                        colors = colors,
                        haptic = haptic,
                        viewModel = viewModel,
                        showCombinedHeader = searchScope == SearchSourceScope.COMBINED,
                        onSelectQuery = { term -> query = term },
                        onOpenModal = { activeModal = it },
                        onOpenContextMenu = { contextMenuSong = it },
                        onSwitchToOnlineSearch = if (searchScope == SearchSourceScope.LOCAL) {
                            {
                                searchScope = SearchSourceScope.ONLINE
                                if (query.isNotBlank()) {
                                    viewModel.updateOnlineSearchQuery(query)
                                    viewModel.executeOnlineSearch(query)
                                }
                            }
                        } else null
                    )
                }
            }
        }
    }

    contextMenuSong?.let { target ->
        MusicContextMenuSheet(
            song = target,
            colors = colors,
            isDownloaded = viewModel.isSongDownloaded(target),
            onDownload = if (target.isOnline) {
                { viewModel.requestDownloadSong(target) }
            } else null,
            onDismiss = { contextMenuSong = null },
            onPlayNow = { viewModel.playSong(target, allSongs) },
            onPlayNext = { viewModel.playNextInQueue(target) },
            onAddToQueue = { viewModel.addToQueue(target) },
            onAddToPlaylist = { playlistPickerSong = target },
            onToggleFavorite = { viewModel.toggleFavorite(target) },
            onViewAlbum = {
                activeModal = SearchCollectionModal(
                    title = target.album,
                    subtitle = target.artist,
                    songs = allSongs.filter { it.album == target.album }
                )
            },
            onViewArtist = {
                activeModal = SearchCollectionModal(
                    title = target.artist,
                    subtitle = "Artist Discography",
                    songs = allSongs.filter { it.artist == target.artist }
                )
            }
        )
    }

    onlineOptionsTarget?.let { target ->
        val artistUrl = if (target.kind == OnlineItemKind.ARTIST) target.contentUrl else target.artistUrl.orEmpty()
        val isArtistFollowed = artistUrl.isNotBlank() &&
            OnlineTrackMapper.computeStableId(target.providerKey.ifBlank { "online" }, artistUrl.trim()) in followedOnlineArtistIds
        val isCollectionSaved = when (target.kind) {
            OnlineItemKind.ALBUM -> OnlineTrackMapper.computeStableId(target.providerKey.ifBlank { "online" }, target.contentUrl.trim()) in savedOnlineAlbumIds
            OnlineItemKind.PLAYLIST -> target.contentUrl.trim() in savedOnlinePlaylistUrls
            else -> false
        }
        OnlineTrackOptionsSheet(
            track = target,
            isSaved = target.stableId in savedOnlineIds,
            isFavorite = target.stableId in favoriteOnlineIds,
            isDownloaded = viewModel.isTrackDownloaded(target),
            isArtistFollowed = isArtistFollowed,
            isCollectionSaved = isCollectionSaved,
            resolvedStream = currentResolvedStream,
            colors = colors,
            onPlayNow = {
                if (target.kind == OnlineItemKind.TRACK) {
                    val pool = (onlineSearchState as? OnlineSearchUiState.Success)?.items.orEmpty()
                    viewModel.playOnlineTrack(target, pool)
                } else {
                    viewModel.openOnlineCollection(target)
                }
            },
            onPlayNext = { viewModel.enqueueOnlineTrackNext(target) },
            onAddToQueue = { viewModel.addOnlineTrackToQueue(target) },
            onToggleSave = { viewModel.toggleSaveOnlineTrack(target) },
            onToggleFavorite = { viewModel.toggleFavorite(OnlineTrackMapper.toUnifiedSong(target)) },
            onAddToPlaylist = { playlistPickerSong = OnlineTrackMapper.toUnifiedSong(target) },
            onAddToOnlinePlaylist = if (target.kind == OnlineItemKind.TRACK) {
                { onlinePlaylistPickerSong = OnlineTrackMapper.toUnifiedSong(target) }
            } else null,
            onToggleFollowArtist = if (artistUrl.isNotBlank()) {
                { viewModel.toggleFollowOnlineArtist(target) }
            } else null,
            onToggleSaveCollection = when (target.kind) {
                OnlineItemKind.ALBUM -> ({ viewModel.toggleSaveOnlineAlbum(target) })
                OnlineItemKind.PLAYLIST -> ({ viewModel.toggleSaveExternalOnlinePlaylist(target) })
                else -> null
            },
            onDownload = { viewModel.requestDownloadTrack(target) },
            onOpenQualitySettings = { showQualitySheet = true },
            onViewArtistChannel = buildArtistExploreTrack(target)?.let { channelItem ->
                { viewModel.openOnlineCollection(channelItem) }
            },
            onDismiss = { onlineOptionsTarget = null }
        )
    }

    playlistPickerSong?.let { target ->
        EditorialPlaylistPickerDialog(
            song = target,
            playlists = playlists,
            colors = colors,
            onDismiss = { playlistPickerSong = null },
            onSelectPlaylist = { pid ->
                if (target.isOnline) {
                    viewModel.addOnlineTrackToPlaylist(pid, target)
                } else {
                    viewModel.addSongToPlaylist(pid, target.id)
                }
                playlistPickerSong = null
            },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) }
        )
    }

    onlinePlaylistPickerSong?.let { songTarget ->
        OnlinePlaylistPickerDialog(
            song = songTarget,
            playlists = onlinePlaylists,
            colors = colors,
            onDismiss = { onlinePlaylistPickerSong = null },
            onSelectPlaylist = { playlist ->
                viewModel.addTrackToOnlinePlaylist(playlist.id, songTarget, playlist.name)
            },
            onCreatePlaylistWithSong = { name ->
                viewModel.createOnlinePlaylist(name, initialTrack = songTarget)
            }
        )
    }

    OnlineCollectionDetailsSheet(
        state = onlineCollectionState,
        currentSongId = currentSong?.id,
        isPlaying = playbackInfo.isPlaying,
        isBuffering = playbackInfo.isBuffering,
        savedIds = savedOnlineIds,
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
        onPlayTrack = { track, tracks -> viewModel.playOnlineTrack(track, tracks) },
        onPlayAll = { tracks, shuffle ->
            val songs = tracks.map { OnlineTrackMapper.toUnifiedSong(it) }
            viewModel.playCollection(songs, shuffle = shuffle)
        },
        onRetry = { item -> viewModel.openOnlineCollection(item) },
        onTrackMoreClick = { track -> onlineOptionsTarget = track },
        onDismiss = { viewModel.dismissOnlineCollection() }
    )

    if (showQualitySheet) {
        OnlineQualitySelectorSheet(
            currentPreference = onlineQualityPreference,
            resolvedStream = currentResolvedStream,
            isMeteredNetwork = isMeteredNetwork,
            isNetworkOnline = isNetworkOnline,
            colors = colors,
            onSelectPreference = { viewModel.selectOnlineQualityPreference(it) },
            onSelectManualStream = { viewModel.selectManualOnlineStreamOption(it) },
            onDismiss = { showQualitySheet = false }
        )
    }

    activeModal?.let { modal ->
        CollectionDetailSheet(
            title = modal.title,
            subtitle = modal.subtitle,
            songs = modal.songs,
            currentPlayingSongId = currentSong?.id,
            isPlaying = playbackInfo.isPlaying,
            colors = colors,
            onDismiss = { activeModal = null },
            onPlayAll = { viewModel.playCollection(modal.songs, shuffle = false) },
            onShufflePlay = { viewModel.playCollection(modal.songs, shuffle = true) },
            onPlaySong = { track -> viewModel.playSong(track, modal.songs) },
            onSongMoreClick = { track -> contextMenuSong = track }
        )
    }

    videoOptionsTarget?.let { target ->
        VideoOptionsBottomSheet(
            video = target,
            isWatchLater = target.videoId in watchLaterIds,
            isSaved = target.videoId in savedVideoIds,
            colors = colors,
            onPlayNow = { videoController.playVideo(target, expandPlayer = true) },
            onPlayNext = { videoController.enqueueVideoNext(target) },
            onToggleWatchLater = { videoController.toggleWatchLater(target) },
            onToggleSave = { videoController.toggleSaveVideo(target) },
            onAddToPlaylist = { videoPlaylistPickerTarget = target },
            onOpenChannel = target.channelUrl?.let { url ->
                { videoController.openChannelProfile(url, target.channelName) }
            },
            onShare = { shareVideoLink(context, target) },
            onOpenOfficialApp = { openOfficialExternalUrl(context, target.contentUrl) },
            onDismiss = { videoOptionsTarget = null }
        )
    }

    videoPlaylistPickerTarget?.let { target ->
        VideoPlaylistPickerDialog(
            video = target,
            playlists = videoPlaylists,
            colors = colors,
            onSelectPlaylist = { pl -> videoController.addVideoToPlaylist(pl.id, pl.name, target) },
            onCreatePlaylistWithVideo = { name -> videoController.createVideoPlaylist(name, target) },
            onDismiss = { videoPlaylistPickerTarget = null }
        )
    }
}
