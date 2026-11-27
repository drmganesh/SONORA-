package ui.online

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.OnlineSearchUiState
import com.example.viewmodel.SonoraViewModel
import data.online.OnlineTrackMapper
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import ui.components.EditorialPlaylistPickerDialog
import ui.library.OnlinePlaylistPickerDialog
import ui.theme.EditorialColors

@Composable
fun OnlineSearchScreen(
    viewModel: SonoraViewModel,
    colors: EditorialColors,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val providers by viewModel.onlineProviders.collectAsState()
    val selectedProvider by viewModel.selectedOnlineProvider.collectAsState()
    val selectedFilter by viewModel.selectedOnlineFilter.collectAsState()
    val query by viewModel.onlineSearchQuery.collectAsState()
    val suggestions by viewModel.onlineSearchSuggestions.collectAsState()
    val searchState by viewModel.onlineSearchState.collectAsState()
    val searchHistory by viewModel.onlineSearchHistory.collectAsState()
    val isOnline by viewModel.isNetworkOnline.collectAsState()
    val isMetered by viewModel.isMeteredNetwork.collectAsState()
    val qualityPreference by viewModel.onlineQualityPreference.collectAsState()
    val currentResolvedStream by viewModel.currentResolvedStream.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackStatusSummary.collectAsState()
    val savedOnlineTracks by viewModel.onlineSavedTracks.collectAsState()
    val allOnlineTracks by viewModel.allOnlineTracks.collectAsState()
    val collectionState by viewModel.onlineCollectionState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val savedOnlineAlbumIds by viewModel.savedOnlineAlbumIds.collectAsState()
    val followedOnlineArtistIds by viewModel.followedOnlineArtistIds.collectAsState()
    val savedOnlinePlaylistUrls by viewModel.savedOnlinePlaylistUrls.collectAsState()

    val savedIds = remember(savedOnlineTracks) { savedOnlineTracks.map { it.id }.toSet() }
    val favoriteIds = remember(allOnlineTracks) { allOnlineTracks.filter { it.isFavorite }.map { it.id }.toSet() }

    var showQualitySheet by remember { mutableStateOf(false) }
    var optionsTarget by remember { mutableStateOf<OnlineTrack?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var onlinePlaylistPickerSong by remember { mutableStateOf<Song?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .testTag("online_search_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("online_search_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                    }
                    Column {
                        Text(
                            text = "Online Search",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = selectedProvider?.displayName ?: "Music Providers",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Surface(
                    onClick = { showQualitySheet = true },
                    shape = RoundedCornerShape(18.dp),
                    color = colors.accentPill,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("online_search_quality_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = "Stream Quality",
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = qualityPreference.title,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.onAccentPill
                        )
                    }
                }
            }

            if (!isOnline) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "No internet connection. Local library playback remains available.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Search input box
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.updateOnlineSearchQuery(it) },
                placeholder = {
                    Text(
                        text = "Search tracks, artists, albums online...",
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateOnlineSearchQuery("") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear query",
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        viewModel.executeOnlineSearch(query)
                    }
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
                    .testTag("online_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Provider picker
            OnlineProviderPillBar(
                providers = providers,
                selectedProvider = selectedProvider,
                colors = colors,
                onSelectProvider = { viewModel.selectOnlineProvider(it.key) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Filter chips for active provider
            val supportedFilters = selectedProvider?.supportedFilters.orEmpty()
            if (supportedFilters.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(supportedFilters, key = { it.name }) { filter ->
                        val active = filter == selectedFilter
                        Surface(
                            onClick = { viewModel.selectOnlineFilter(filter) },
                            shape = RoundedCornerShape(18.dp),
                            color = if (active) colors.accentPill else colors.surfaceTinted,
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = if (active) colors.accentStrong else colors.cardBorder
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("online_filter_${filter.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = filter.label,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                    color = if (active) colors.onAccentPill else colors.textSecondary
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Live search suggestions strip when typing
            if (suggestions.isNotEmpty() && query.isNotBlank()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(suggestions, key = { "sug_$it" }) { sug ->
                        Surface(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.executeOnlineSearch(sug)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfaceElevated,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = sug,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Results / Loading / Error / Idle History
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 6.dp, bottom = 184.dp)
            ) {
                val activePlayErr = playbackInfo.playbackError
                if (!activePlayErr.isNullOrBlank()) {
                    item(key = "online_search_playback_error") {
                        val errKind = playbackInfo.playbackErrorKind ?: OnlineErrorKind.EXTRACTION_FAILED
                        val canSwitchSc = errKind == OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA &&
                            selectedProvider?.key != OnlineMusicProvider.KEY_SOUNDCLOUD
                        OnlineErrorCard(
                            kind = errKind,
                            message = activePlayErr,
                            showRetry = currentSong != null,
                            colors = colors,
                            onRetry = { viewModel.retryCurrentPlayback() },
                            secondaryActionLabel = if (canSwitchSc) "Try SoundCloud" else null,
                            onSecondaryAction = if (canSwitchSc) {
                                { viewModel.switchToSoundCloudFallback() }
                            } else null,
                            onDismiss = { viewModel.clearPlaybackError() }
                        )
                    }
                }

                when (val state = searchState) {
                    is OnlineSearchUiState.Idle -> {
                        if (searchHistory.isNotEmpty()) {
                            item(key = "online_history_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 22.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.History,
                                            contentDescription = null,
                                            tint = colors.accentStrong,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Recent Online Searches",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = colors.textPrimary
                                        )
                                    }
                                    Text(
                                        text = "Clear All",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = colors.accentStrong,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { viewModel.clearOnlineSearchHistory() }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            items(searchHistory, key = { "oh_$it" }) { term ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 3.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surface)
                                        .clickable {
                                            keyboardController?.hide()
                                            viewModel.executeOnlineSearch(term)
                                        }
                                        .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.History,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = term,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    IconButton(
                                        onClick = { viewModel.removeOnlineSearchHistory(term) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove $term",
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            item(key = "online_search_prompt") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 28.dp, vertical = 48.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(68.dp)
                                            .background(colors.accentPill, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = null,
                                            tint = colors.onAccentPill,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = "Search ${selectedProvider?.displayName ?: "Online Music"}",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = colors.textPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Type a song, artist, album, or playlist and press Search on your keyboard to stream through ${domain.branding.AppBranding.APP_NAME}'s audio engine.",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 13.sp,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    is OnlineSearchUiState.Loading -> {
                        item(key = "online_search_loading") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(44.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = colors.accentStrong)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Searching ${selectedProvider?.displayName ?: "Online"} for \"${state.query}\"...",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 13.sp,
                                    color = colors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    is OnlineSearchUiState.Error -> {
                        item(key = "online_search_error") {
                            val canSwitchSc = state.kind == OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA &&
                                selectedProvider?.key != OnlineMusicProvider.KEY_SOUNDCLOUD
                            OnlineErrorCard(
                                kind = state.kind,
                                message = state.message,
                                showRetry = state.isRetryable,
                                colors = colors,
                                onRetry = { viewModel.executeOnlineSearch(query, forceRefresh = true) },
                                secondaryActionLabel = if (canSwitchSc) "Try SoundCloud" else null,
                                onSecondaryAction = if (canSwitchSc) {
                                    { viewModel.switchToSoundCloudFallback() }
                                } else null
                            )
                        }
                    }

                    is OnlineSearchUiState.Success -> {
                        val correction = state.page.correctedSuggestion
                        if (!correction.isNullOrBlank()) {
                            item(key = "corrected_suggestion") {
                                Surface(
                                    onClick = { viewModel.executeOnlineSearch(correction) },
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceTinted,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Showing results for \"${state.page.query}\". Search instead for: $correction",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.accentStrong,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }

                        if (state.items.isEmpty()) {
                            item(key = "online_no_results") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No ${selectedFilter.label.lowercase()} results found for \"${state.page.query}\" on ${selectedProvider?.displayName ?: "provider"}.",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 14.sp,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            val playableTracks = state.items.filter { it.kind == OnlineItemKind.TRACK }
                            items(state.items, key = { "res_${it.stableId}_${it.contentUrl}" }) { item ->
                                val isCurrent = currentSong?.id == item.stableId
                                val isDownloaded = viewModel.isTrackDownloaded(item)
                                OnlineResultRow(
                                    track = item,
                                    isCurrent = isCurrent,
                                    isPlaying = playbackInfo.isPlaying,
                                    isBuffering = playbackInfo.isBuffering,
                                    isSaved = item.stableId in savedIds,
                                    colors = colors,
                                    isDownloaded = isDownloaded,
                                    onDownloadClick = { viewModel.requestDownloadTrack(item) },
                                    onClick = {
                                        if (item.kind == OnlineItemKind.TRACK) {
                                            viewModel.playOnlineTrack(item, playableTracks)
                                        } else {
                                            viewModel.openOnlineCollection(item)
                                        }
                                    },
                                    onMoreClick = { optionsTarget = item }
                                )
                            }

                            if (state.page.hasNextPage) {
                                item(key = "online_load_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 20.dp, vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.loadMoreOnlineSearchResults() },
                                            enabled = !state.isLoadingMore,
                                            shape = RoundedCornerShape(20.dp),
                                            border = BorderStroke(1.dp, colors.accentStrong),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            if (state.isLoadingMore) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.dp,
                                                    color = colors.accentStrong,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                text = if (state.isLoadingMore) "Loading More..." else "Load More Results",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = colors.accentStrong
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

    optionsTarget?.let { target ->
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
            isSaved = target.stableId in savedIds,
            isFavorite = target.stableId in favoriteIds,
            isDownloaded = viewModel.isTrackDownloaded(target),
            isArtistFollowed = isArtistFollowed,
            isCollectionSaved = isCollectionSaved,
            resolvedStream = currentResolvedStream,
            colors = colors,
            onPlayNow = {
                if (target.kind == OnlineItemKind.TRACK) {
                    val pool = (searchState as? OnlineSearchUiState.Success)?.items.orEmpty()
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
            onDismiss = { optionsTarget = null }
        )
    }

    playlistPickerSong?.let { songTarget ->
        EditorialPlaylistPickerDialog(
            song = songTarget,
            playlists = playlists,
            colors = colors,
            onDismiss = { playlistPickerSong = null },
            onSelectPlaylist = { pid ->
                viewModel.addOnlineTrackToPlaylist(pid, songTarget)
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
        state = collectionState,
        currentSongId = currentSong?.id,
        isPlaying = playbackInfo.isPlaying,
        isBuffering = playbackInfo.isBuffering,
        savedIds = savedIds,
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
        onTrackMoreClick = { track -> optionsTarget = track },
        onDismiss = { viewModel.dismissOnlineCollection() }
    )

    if (showQualitySheet) {
        OnlineQualitySelectorSheet(
            currentPreference = qualityPreference,
            resolvedStream = currentResolvedStream,
            isMeteredNetwork = isMetered,
            isNetworkOnline = isOnline,
            colors = colors,
            onSelectPreference = { viewModel.selectOnlineQualityPreference(it) },
            onSelectManualStream = { viewModel.selectManualOnlineStreamOption(it) },
            onDismiss = { showQualitySheet = false }
        )
    }
}

@Composable
fun OnlineErrorCard(
    kind: OnlineErrorKind,
    message: String,
    showRetry: Boolean,
    colors: EditorialColors,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("online_error_card")
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val title = when (kind) {
                OnlineErrorKind.OFFLINE -> "You're Offline"
                OnlineErrorKind.TIMEOUT -> "Connection Timed Out"
                OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA -> "Provider Verification / Rate Limit"
                OnlineErrorKind.AGE_OR_GEO_RESTRICTED -> "Track Restricted"
                OnlineErrorKind.STREAM_EXPIRED -> "Stream URL Expired"
                OnlineErrorKind.NO_PLAYABLE_AUDIO_STREAM -> "No Playable Audio"
                OnlineErrorKind.UNSUPPORTED -> "Unsupported by Provider"
                OnlineErrorKind.EXTRACTION_FAILED -> "Unable to Load Online Music"
            }
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (showRetry || (!secondaryActionLabel.isNullOrBlank() && onSecondaryAction != null) || onDismiss != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showRetry) {
                        Button(
                            onClick = onRetry,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("online_error_retry_button")
                        ) {
                            Text("Try Again", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!secondaryActionLabel.isNullOrBlank() && onSecondaryAction != null) {
                        Button(
                            onClick = onSecondaryAction,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentPill,
                                contentColor = colors.onAccentPill
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("online_error_secondary_button")
                        ) {
                            Text(
                                text = secondaryActionLabel,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (onDismiss != null) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("online_error_dismiss_button")
                        ) {
                            Text(
                                text = "Dismiss",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
