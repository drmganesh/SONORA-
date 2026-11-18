package ui.online

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.Public
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.online.OnlineTrackMapper
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineTrack
import ui.components.EditorialPlaylistPickerDialog
import ui.theme.EditorialColors

@Composable
fun OnlineMusicScreen(
    viewModel: SonoraViewModel,
    colors: EditorialColors,
    initialSearchOpen: Boolean = false,
    onInitialSearchConsumed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val providers by viewModel.onlineProviders.collectAsState()
    val selectedProvider by viewModel.selectedOnlineProvider.collectAsState()
    val discoverSections by viewModel.discoverSectionsState.collectAsState()
    val savedTracks by viewModel.onlineSavedTracks.collectAsState()
    val recentOnlineTracks by viewModel.recentlyPlayedOnlineTracks.collectAsState()
    val allOnlineTracks by viewModel.allOnlineTracks.collectAsState()
    val isOnline by viewModel.isNetworkOnline.collectAsState()
    val isMetered by viewModel.isMeteredNetwork.collectAsState()
    val qualityPreference by viewModel.onlineQualityPreference.collectAsState()
    val currentResolvedStream by viewModel.currentResolvedStream.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val collectionState by viewModel.onlineCollectionState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val onlineCountryCode by viewModel.onlineContentCountry.collectAsState()
    val onlineLanguageCode by viewModel.onlineContentLanguage.collectAsState()
    val discoverLanguage by viewModel.discoverMusicLanguage.collectAsState()

    val savedIds = remember(savedTracks) { savedTracks.map { it.id }.toSet() }
    val favoriteIds = remember(allOnlineTracks) { allOnlineTracks.filter { it.isFavorite }.map { it.id }.toSet() }

    var showSearchView by remember { mutableStateOf(initialSearchOpen) }
    LaunchedEffect(initialSearchOpen) {
        if (initialSearchOpen) {
            showSearchView = true
            onInitialSearchConsumed()
        }
    }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showRegionSheet by remember { mutableStateOf(false) }
    var optionsTarget by remember { mutableStateOf<OnlineTrack?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }

    if (showSearchView) {
        BackHandler { showSearchView = false }
        OnlineSearchScreen(
            viewModel = viewModel,
            colors = colors,
            onBack = { showSearchView = false },
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .testTag("online_music_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 184.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Editorial Header
            item(key = "online_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Online Music",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 32.sp,
                            letterSpacing = (-0.6).sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Powered by NewPipe Extractor • Unified Audio Studio",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = { showRegionSheet = true },
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("online_region_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Public,
                                    contentDescription = "Online Region and Language",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${onlineCountryCode.uppercase()}•${onlineLanguageCode.uppercase()}",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = colors.textPrimary
                                )
                            }
                        }

                        Surface(
                            onClick = { showQualitySheet = true },
                            shape = RoundedCornerShape(18.dp),
                            color = colors.accentPill,
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("online_quality_button")
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
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = qualityPreference.title,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.onAccentPill
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.refreshOnlineDiscovery(forceRefresh = true) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("online_refresh_discovery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh online music",
                                tint = colors.textPrimary
                            )
                        }
                    }
                }
            }

            // Quick Search Launcher Pill
            item(key = "online_search_launcher") {
                Surface(
                    onClick = { showSearchView = true },
                    shape = RoundedCornerShape(26.dp),
                    color = colors.surface,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(54.dp)
                        .testTag("open_online_search_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Search songs, artists, albums online...",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 14.sp,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Offline banner
            if (!isOnline) {
                item(key = "online_offline_banner") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WifiOff,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "You're offline. Online search and streaming need an internet connection, while saved track metadata remains visible below.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Active playback error banner if an online stream failed
            val activeErr = playbackInfo.playbackError
            if (!activeErr.isNullOrBlank()) {
                item(key = "playback_error_banner") {
                    val errKind = playbackInfo.playbackErrorKind ?: domain.online.OnlineErrorKind.EXTRACTION_FAILED
                    val canSwitchSc = errKind == domain.online.OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA &&
                        selectedProvider?.key != domain.online.OnlineMusicProvider.KEY_SOUNDCLOUD
                    OnlineErrorCard(
                        kind = errKind,
                        message = activeErr,
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

            // Provider Selector
            item(key = "online_provider_selector") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SUPPORTED SERVICES",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    OnlineProviderPillBar(
                        providers = providers,
                        selectedProvider = selectedProvider,
                        colors = colors,
                        onSelectProvider = { viewModel.selectOnlineProvider(it.key) }
                    )
                }
            }

            // Optional Discover Language & Category Filters
            item(key = "online_discover_language_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (onlineCountryCode.equals("IN", ignoreCase = true)) {
                            "INDIA & REGIONAL MUSIC FILTERS"
                        } else {
                            "LANGUAGE & CATEGORY FILTERS"
                        },
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = colors.textMuted,
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(DiscoverMusicLanguage.entries, key = { "lang_pill_${it.id}" }) { lang ->
                            val selected = lang == discoverLanguage
                            Surface(
                                onClick = { viewModel.setDiscoverMusicLanguage(lang) },
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) colors.accentStrong else colors.surfaceTinted,
                                border = BorderStroke(
                                    width = if (selected) 1.25.dp else 0.75.dp,
                                    color = if (selected) colors.accentStrong else colors.cardBorder
                                ),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("discover_lang_${lang.id}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                ) {
                                    Text(
                                        text = lang.label,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (selected) colors.onAccentStrong else colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Independent Fault-Tolerant Discover Sections
            items(DiscoverSectionType.entries, key = { "discover_sec_${it.name}" }) { sectionType ->
                val secState = discoverSections[sectionType] ?: DiscoverSectionState.Loading
                DiscoverSectionBlock(
                    sectionType = sectionType,
                    state = secState,
                    providerName = selectedProvider?.displayName ?: "Provider",
                    currentSongId = currentSong?.id,
                    isPlaying = playbackInfo.isPlaying,
                    colors = colors,
                    onItemClick = { clicked, allSectionItems ->
                        if (clicked.kind == OnlineItemKind.TRACK) {
                            val playableTracks = allSectionItems.filter { it.kind == OnlineItemKind.TRACK }
                            viewModel.playOnlineTrack(clicked, playableTracks)
                        } else {
                            viewModel.openOnlineCollection(clicked)
                        }
                    },
                    onItemLongClick = { optionsTarget = it },
                    onRetry = { viewModel.retryDiscoverSection(sectionType) },
                    onLoadMore = { viewModel.loadMoreDiscoverSection(sectionType) }
                )
            }

            // Saved Online Tracks
            if (savedTracks.isNotEmpty()) {
                item(key = "online_saved_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdded,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Saved Online Tracks (${savedTracks.size})",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                    }
                }

                items(savedTracks, key = { "saved_onl_${it.id}" }) { song ->
                    val track = remember(song) { OnlineTrackMapper.toOnlineTrack(song) }
                    val isCurrent = currentSong?.id == song.id
                    OnlineResultRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = playbackInfo.isPlaying,
                        isBuffering = playbackInfo.isBuffering,
                        isSaved = true,
                        colors = colors,
                        onClick = { viewModel.playSong(song, savedTracks) },
                        onMoreClick = { optionsTarget = track }
                    )
                }
            }

            // Recently Played Online Tracks
            if (recentOnlineTracks.isNotEmpty()) {
                item(key = "online_recent_header") {
                    Text(
                        text = "Recently Streamed",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 22.dp)
                    )
                }

                items(recentOnlineTracks.take(10), key = { "rec_onl_${it.id}" }) { song ->
                    val track = remember(song) { OnlineTrackMapper.toOnlineTrack(song) }
                    val isCurrent = currentSong?.id == song.id
                    OnlineResultRow(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = playbackInfo.isPlaying,
                        isBuffering = playbackInfo.isBuffering,
                        isSaved = song.id in savedIds,
                        colors = colors,
                        onClick = { viewModel.playSong(song, recentOnlineTracks) },
                        onMoreClick = { optionsTarget = track }
                    )
                }
            }
        }
    }

    optionsTarget?.let { target ->
        OnlineTrackOptionsSheet(
            track = target,
            isSaved = target.stableId in savedIds,
            isFavorite = target.stableId in favoriteIds,
            resolvedStream = currentResolvedStream,
            colors = colors,
            onPlayNow = {
                if (target.kind == OnlineItemKind.TRACK) {
                    val discoveryPool = discoverSections.values
                        .filterIsInstance<DiscoverSectionState.Success>()
                        .flatMap { it.items }
                        .filter { it.kind == OnlineItemKind.TRACK }
                        .distinctBy { it.contentUrl }
                    viewModel.playOnlineTrack(target, discoveryPool)
                } else {
                    viewModel.openOnlineCollection(target)
                }
            },
            onPlayNext = { viewModel.enqueueOnlineTrackNext(target) },
            onAddToQueue = { viewModel.addOnlineTrackToQueue(target) },
            onToggleSave = { viewModel.toggleSaveOnlineTrack(target) },
            onToggleFavorite = { viewModel.toggleFavorite(OnlineTrackMapper.toUnifiedSong(target)) },
            onAddToPlaylist = { playlistPickerSong = OnlineTrackMapper.toUnifiedSong(target) },
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

    OnlineCollectionDetailsSheet(
        state = collectionState,
        currentSongId = currentSong?.id,
        isPlaying = playbackInfo.isPlaying,
        isBuffering = playbackInfo.isBuffering,
        savedIds = savedIds,
        colors = colors,
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

    if (showRegionSheet) {
        OnlineRegionSheet(
            currentCountryCode = onlineCountryCode,
            currentLanguageCode = onlineLanguageCode,
            colors = colors,
            onSelectCountry = { viewModel.setOnlineContentCountry(it) },
            onSelectLanguage = { viewModel.setOnlineContentLanguage(it) },
            onDismiss = { showRegionSheet = false }
        )
    }
}

@Composable
private fun DiscoverSectionBlock(
    sectionType: DiscoverSectionType,
    state: DiscoverSectionState,
    providerName: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onItemClick: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onItemLongClick: (OnlineTrack) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("discover_section_${sectionType.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = sectionType.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                    val subtitleText = when (state) {
                        is DiscoverSectionState.Success ->
                            if (state.sourceLabel.isNotBlank()) "${sectionType.subtitle} • ${state.sourceLabel}"
                            else sectionType.subtitle
                        else -> "${sectionType.subtitle} • $providerName"
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
            }
        }

        when (state) {
            is DiscoverSectionState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accentStrong,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            is DiscoverSectionState.Empty -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = state.message,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            is DiscoverSectionState.Error -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("discover_error_${sectionType.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            Text(
                                text = "${sectionType.title} unavailable",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = state.message,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (state.isRetryable) {
                            Button(
                                onClick = onRetry,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentStrong,
                                    contentColor = colors.onAccentStrong
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("discover_retry_${sectionType.name.lowercase()}")
                            ) {
                                Text(
                                    text = "Retry",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            is DiscoverSectionState.Success -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.items, key = { "disc_${sectionType.name}_${it.stableId}_${it.contentUrl}" }) { item ->
                        val isCurrent = currentSongId == item.stableId
                        OnlineDiscoveryCard(
                            track = item,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            colors = colors,
                            onClick = { onItemClick(item, state.items) },
                            onLongClick = { onItemLongClick(item) }
                        )
                    }
                    if (state.hasMore) {
                        item(key = "disc_load_more_${sectionType.name}") {
                            ui.home.HomeOnlineLoadMoreCard(
                                sectionType = sectionType,
                                isLoadingMore = state.isLoadingMore,
                                colors = colors,
                                onLoadMore = onLoadMore
                            )
                        }
                    }
                }
            }
        }
    }
}
