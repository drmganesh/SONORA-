package ui.online

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.online.IndiaMusicCategory
import data.online.MusicMoodActivity
import data.online.OnlineTrackMapper
import data.online.PersonalizedMusicMixCard
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineTrack
import ui.components.EditorialPlaylistPickerDialog
import ui.library.OnlinePlaylistPickerDialog
import ui.motion.LocalEffectiveMotionState
import ui.motion.SonoraTransitions
import ui.theme.EditorialColors

@Composable
fun OnlineMusicScreen(
    viewModel: SonoraViewModel,
    colors: EditorialColors,
    initialSearchOpen: Boolean = false,
    onInitialSearchConsumed: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenNowPlaying: () -> Unit = {},
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
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsState()
    val savedOnlineAlbumIds by viewModel.savedOnlineAlbumIds.collectAsState()
    val followedOnlineArtistIds by viewModel.followedOnlineArtistIds.collectAsState()
    val savedOnlinePlaylistUrls by viewModel.savedOnlinePlaylistUrls.collectAsState()
    val onlineCountryCode by viewModel.onlineContentCountry.collectAsState()
    val onlineLanguageCode by viewModel.onlineContentLanguage.collectAsState()
    val discoverLanguage by viewModel.discoverMusicLanguage.collectAsState()
    val musicExperienceSettings by viewModel.musicExperienceSettings.collectAsState()
    val madeForYouCards by viewModel.madeForYouCards.collectAsState()
    val basedOnListeningCards by viewModel.basedOnListeningCards.collectAsState()
    val selectedMoodActivity by viewModel.selectedMoodActivity.collectAsState()
    val moodDiscoveryTracks by viewModel.moodDiscoveryTracks.collectAsState()
    val isLoadingMoodDiscovery by viewModel.isLoadingMoodDiscovery.collectAsState()
    val selectedIndiaCategory by viewModel.selectedIndiaCategory.collectAsState()
    val indiaDiscoveryTracks by viewModel.indiaDiscoveryTracks.collectAsState()
    val isLoadingIndiaDiscovery by viewModel.isLoadingIndiaDiscovery.collectAsState()

    LaunchedEffect(selectedProvider?.key, musicExperienceSettings.musicRecommendationsEnabled) {
        if (musicExperienceSettings.musicRecommendationsEnabled) {
            viewModel.ensureAdvancedMusicDiscoveryLoaded()
        }
    }

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
    var onlinePlaylistPickerSong by remember { mutableStateOf<Song?>(null) }

    val motionState = LocalEffectiveMotionState.current

    Box(modifier = Modifier.fillMaxSize()) {
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
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = domain.player.MusicMiniPlayerVisibilityPolicy.listBottomContentPadding(
                    isMusicMiniPlayerVisible = currentSong != null,
                    isVideoMiniPlayerVisible = false
                )
            ),
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

            // Personalized SONORA Music Discovery (Made For You, Based on Your Listening, Moods, India Music)
            if (musicExperienceSettings.musicRecommendationsEnabled) {
                item(key = "online_made_for_you_section") {
                    PersonalizedMixCardShelf(
                        title = "Made For You",
                        subtitle = "Transparent SONORA Mixes tailored to your local listening profile",
                        cards = madeForYouCards,
                        colors = colors,
                        testTagPrefix = "made_for_you",
                        onCardClick = { viewModel.playPersonalizedMixCard(it) }
                    )
                }

                item(key = "online_based_on_listening_section") {
                    PersonalizedMixCardShelf(
                        title = "Based on Your Listening",
                        subtitle = "Similar artists, songs, albums & fresh discoveries",
                        cards = basedOnListeningCards,
                        colors = colors,
                        testTagPrefix = "based_listening",
                        onCardClick = { viewModel.playPersonalizedMixCard(it) }
                    )
                }

                item(key = "online_mood_activity_section") {
                    MoodAndActivityDiscoverySection(
                        selectedMood = selectedMoodActivity,
                        tracks = moodDiscoveryTracks,
                        isLoading = isLoadingMoodDiscovery,
                        countryCode = onlineCountryCode,
                        providerName = selectedProvider?.displayName ?: "Provider",
                        currentSongId = currentSong?.id,
                        isPlaying = playbackInfo.isPlaying,
                        colors = colors,
                        onSelectMood = { viewModel.loadMoodDiscovery(it) },
                        onPlayTrack = { track, pool -> viewModel.playOnlineTrack(track, pool) },
                        onTrackLongClick = { optionsTarget = it }
                    )
                }

                if (domain.online.RegionMusicEligibilityFilter.isIndiaRegion(onlineCountryCode)) {
                    item(key = "online_india_music_section") {
                        IndiaMusicDiscoverySection(
                            selectedCategory = selectedIndiaCategory,
                            tracks = indiaDiscoveryTracks,
                            isLoading = isLoadingIndiaDiscovery,
                            providerName = selectedProvider?.displayName ?: "Provider",
                            currentSongId = currentSong?.id,
                            isPlaying = playbackInfo.isPlaying,
                            colors = colors,
                            onSelectCategory = { viewModel.loadIndiaMusicCategory(it) },
                            onPlayTrack = { track, pool -> viewModel.playOnlineTrack(track, pool) },
                            onTrackLongClick = { optionsTarget = it }
                        )
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
                        isDownloaded = viewModel.isTrackDownloaded(track),
                        onDownloadClick = { viewModel.requestDownloadTrack(track) },
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
                        isDownloaded = viewModel.isTrackDownloaded(track),
                        onDownloadClick = { viewModel.requestDownloadTrack(track) },
                        onClick = { viewModel.playSong(song, recentOnlineTracks) },
                        onMoreClick = { optionsTarget = track }
                    )
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
            onStartRadio = if (target.kind == OnlineItemKind.TRACK) {
                { viewModel.startSongRadio(OnlineTrackMapper.toUnifiedSong(target), autoPlayFirstAdded = false) }
            } else null,
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

        AnimatedVisibility(
            visible = showSearchView,
            enter = SonoraTransitions.pushEnter(motionState),
            exit = SonoraTransitions.pushExit(motionState)
        ) {
            BackHandler { showSearchView = false }
            OnlineSearchScreen(
                viewModel = viewModel,
                colors = colors,
                onBack = { showSearchView = false },
                modifier = modifier
            )
        }
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

@Composable
private fun PersonalizedMixCardShelf(
    title: String,
    subtitle: String,
    cards: List<PersonalizedMusicMixCard>,
    colors: EditorialColors,
    testTagPrefix: String,
    onCardClick: (PersonalizedMusicMixCard) -> Unit
) {
    if (cards.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("${testTagPrefix}_shelf")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cards, key = { "${testTagPrefix}_${it.id}" }) { card ->
                val gradient = remember(card.gradientColors) {
                    val palette = card.gradientColors.map { Color(it) }
                    if (palette.size >= 2) Brush.linearGradient(palette)
                    else Brush.linearGradient(listOf(colors.accentStrong, colors.surfaceElevated))
                }
                Surface(
                    onClick = { onCardClick(card) },
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .width(214.dp)
                        .height(132.dp)
                        .testTag("${testTagPrefix}_card_${card.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradient)
                            .padding(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.Black.copy(alpha = 0.34f)
                                ) {
                                    Text(
                                        text = card.badge.label,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (card.id.contains("radio")) Icons.Rounded.Radio else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play ${card.title}",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = card.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = card.subtitle,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.5.sp,
                                    color = Color.White.copy(alpha = 0.86f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodAndActivityDiscoverySection(
    selectedMood: MusicMoodActivity,
    tracks: List<OnlineTrack>,
    isLoading: Boolean,
    countryCode: String,
    providerName: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onSelectMood: (MusicMoodActivity) -> Unit,
    onPlayTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mood_activity_discovery_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Mood & Activity",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = selectedMood.subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(MusicMoodActivity.entries, key = { "mood_${it.id}" }) { mood ->
                val selected = mood == selectedMood
                Surface(
                    onClick = { onSelectMood(mood) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) colors.accentStrong else colors.surfaceTinted,
                    border = BorderStroke(
                        width = if (selected) 1.25.dp else 0.75.dp,
                        color = if (selected) colors.accentStrong else colors.cardBorder
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("mood_chip_${mood.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = mood.label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = if (selected) colors.onAccentStrong else colors.textPrimary
                        )
                    }
                }
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accentStrong,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            tracks.isNotEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tracks, key = { "mood_tr_${selectedMood.id}_${it.stableId}_${it.contentUrl}" }) { item ->
                        OnlineDiscoveryCard(
                            track = item,
                            isCurrent = currentSongId == item.stableId,
                            isPlaying = isPlaying,
                            colors = colors,
                            onClick = { onPlayTrack(item, tracks) },
                            onLongClick = { onTrackLongClick(item) }
                        )
                    }
                }
            }
            else -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("mood_discovery_empty_state")
                ) {
                    Text(
                        text = domain.online.RegionMusicEligibilityFilter.emptyRegionRecommendationsMessage(
                            sectionTitle = "${selectedMood.label} recommendations",
                            countryCode = countryCode,
                            providerName = providerName
                        ),
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun IndiaMusicDiscoverySection(
    selectedCategory: IndiaMusicCategory,
    tracks: List<OnlineTrack>,
    isLoading: Boolean,
    providerName: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onSelectCategory: (IndiaMusicCategory) -> Unit,
    onPlayTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("india_music_discovery_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Public,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "India Music Discovery",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = selectedCategory.subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(IndiaMusicCategory.entries, key = { "india_${it.id}" }) { cat ->
                val selected = cat == selectedCategory
                Surface(
                    onClick = { onSelectCategory(cat) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) colors.accentStrong else colors.surfaceTinted,
                    border = BorderStroke(
                        width = if (selected) 1.25.dp else 0.75.dp,
                        color = if (selected) colors.accentStrong else colors.cardBorder
                    ),
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("india_chip_${cat.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = cat.label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = if (selected) colors.onAccentStrong else colors.textPrimary
                        )
                    }
                }
            }
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = colors.accentStrong,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            tracks.isNotEmpty() -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tracks, key = { "india_tr_${selectedCategory.id}_${it.stableId}_${it.contentUrl}" }) { item ->
                        OnlineDiscoveryCard(
                            track = item,
                            isCurrent = currentSongId == item.stableId,
                            isPlaying = isPlaying,
                            colors = colors,
                            onClick = { onPlayTrack(item, tracks) },
                            onLongClick = { onTrackLongClick(item) }
                        )
                    }
                }
            }
            else -> {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("india_discovery_empty_state")
                ) {
                    Text(
                        text = domain.online.RegionMusicEligibilityFilter.emptyRegionRecommendationsMessage(
                            sectionTitle = selectedCategory.label,
                            countryCode = domain.online.RegionMusicEligibilityFilter.REGION_INDIA,
                            providerName = providerName
                        ),
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
