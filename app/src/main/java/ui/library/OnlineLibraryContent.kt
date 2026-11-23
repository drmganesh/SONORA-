package ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.library.FollowedOnlineArtist
import domain.library.OnlineLibrarySearchResults
import domain.library.OnlineLibrarySortOption
import domain.library.OnlinePlaylist
import domain.library.SavedOnlineAlbum
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import ui.motion.LocalEffectiveMotionState
import ui.motion.SonoraTransitions
import ui.online.OnlineTrackThumbnail
import ui.theme.EditorialColors

enum class OnlineLibraryTab(val label: String) {
    OVERVIEW("Overview"),
    PLAYLISTS("Online Playlists"),
    SAVED_SONGS("Saved Songs"),
    SAVED_ALBUMS("Saved Albums"),
    FOLLOWED_ARTISTS("Followed Artists"),
    RECENTLY_PLAYED("Recently Played"),
    HISTORY("History"),
    FAVOURITES("Favourites")
}

@Composable
fun OnlineLibraryContent(
    searchResults: OnlineLibrarySearchResults,
    recentlyPlayed: List<Song>,
    savedTrackIds: Set<Long>,
    availableProviders: List<OnlineMusicProvider>,
    selectedProviderKey: String?,
    searchQuery: String,
    sortOption: OnlineLibrarySortOption,
    isCollectionLoading: Boolean,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onSearchQueryChange: (String) -> Unit,
    onSelectProviderFilter: (String?) -> Unit,
    onOpenSortSheet: () -> Unit,
    onCreateOnlinePlaylist: () -> Unit,
    onOpenOnlinePlaylist: (OnlinePlaylist) -> Unit,
    onPlaySongs: (List<Song>, Song?, Boolean) -> Unit,
    onToggleSaveTrack: (Song) -> Unit,
    onToggleFavoriteTrack: (Song) -> Unit,
    onOpenSongMore: (Song) -> Unit,
    onOpenAlbum: (SavedOnlineAlbum) -> Unit,
    onRemoveAlbum: (SavedOnlineAlbum) -> Unit,
    onOpenArtist: (FollowedOnlineArtist) -> Unit,
    onUnfollowArtist: (FollowedOnlineArtist) -> Unit,
    onClearHistory: () -> Unit,
    onExploreOnlineMusic: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(OnlineLibraryTab.OVERVIEW) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    val motionState = LocalEffectiveMotionState.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("online_library_content")
    ) {
        // Zero-network local metadata search bar + Sort + Create Playlist
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = "Search saved songs, albums, artists...",
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
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear online library search", tint = colors.textSecondary)
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
                    .testTag("online_library_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = CircleShape,
                color = colors.surface,
                modifier = Modifier
                    .size(48.dp)
                    .border(0.75.dp, colors.cardBorder, CircleShape)
                    .clip(CircleShape)
                    .clickable { onOpenSortSheet() }
                    .testTag("online_library_sort_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Sort Online Library",
                        tint = colors.accentStrong
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Surface(
                shape = CircleShape,
                color = colors.accentPill,
                modifier = Modifier
                    .size(48.dp)
                    .border(0.75.dp, colors.cardBorder, CircleShape)
                    .clip(CircleShape)
                    .clickable { onCreateOnlinePlaylist() }
                    .testTag("online_library_create_playlist_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Create Online Playlist",
                        tint = colors.onAccentPill
                    )
                }
            }
        }

        // Provider filter chips (YouTube Music, YouTube, SoundCloud, Bandcamp)
        if (availableProviders.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ProviderChip(
                    label = "All Services",
                    selected = selectedProviderKey == null,
                    colors = colors,
                    onClick = { onSelectProviderFilter(null) },
                    testTag = "online_lib_provider_all"
                )
                availableProviders.forEach { provider ->
                    ProviderChip(
                        label = provider.shortLabel,
                        selected = selectedProviderKey.equals(provider.key, ignoreCase = true),
                        colors = colors,
                        onClick = {
                            onSelectProviderFilter(
                                if (selectedProviderKey.equals(provider.key, ignoreCase = true)) null else provider.key
                            )
                        },
                        testTag = "online_lib_provider_${provider.key}"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Online Library Category Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OnlineLibraryTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) colors.accentStrong else colors.surface,
                    modifier = Modifier
                        .height(40.dp)
                        .border(
                            0.75.dp,
                            if (selected) colors.accentStrong else colors.cardBorder,
                            RoundedCornerShape(20.dp)
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { selectedTab = tab }
                        .testTag("online_library_tab_${tab.name.lowercase()}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 15.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = if (selected) colors.onAccentStrong else colors.textSecondary
                        )
                    }
                }
            }
        }

        if (isCollectionLoading) {
            OnlineLibraryLoadingSkeleton(colors = colors)
        }

        Spacer(modifier = Modifier.height(6.dp))

        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                SonoraTransitions.tabTransition(
                    forward = targetState.ordinal >= initialState.ordinal,
                    motion = motionState
                )
            },
            label = "online_library_tab_transition",
            modifier = Modifier.weight(1f)
        ) { tab ->
            when (tab) {
                OnlineLibraryTab.OVERVIEW -> OnlineOverviewHub(
                    results = searchResults,
                    recentlyPlayed = recentlyPlayed,
                    savedTrackIds = savedTrackIds,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    colors = colors,
                    onJumpToTab = { selectedTab = it },
                    onCreatePlaylist = onCreateOnlinePlaylist,
                    onOpenPlaylist = onOpenOnlinePlaylist,
                    onPlaySongs = onPlaySongs,
                    onToggleSaveTrack = onToggleSaveTrack,
                    onOpenSongMore = onOpenSongMore,
                    onOpenAlbum = onOpenAlbum,
                    onOpenArtist = onOpenArtist,
                    onExploreOnlineMusic = onExploreOnlineMusic
                )

                OnlineLibraryTab.PLAYLISTS -> OnlinePlaylistsSection(
                    playlists = searchResults.playlists,
                    colors = colors,
                    onCreatePlaylist = onCreateOnlinePlaylist,
                    onOpenPlaylist = onOpenOnlinePlaylist,
                    onPlayPlaylist = { pl ->
                        if (pl.tracks.isNotEmpty()) onPlaySongs(pl.tracks, pl.tracks.first(), false)
                    }
                )

                OnlineLibraryTab.SAVED_SONGS -> OnlineTrackListSection(
                    songs = searchResults.tracks,
                    savedTrackIds = savedTrackIds,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    emptyTitle = if (searchQuery.isNotBlank()) "No Saved Songs Match \"$searchQuery\"" else "No Saved Online Songs Yet",
                    emptySubtitle = "Tap Save to Online Library on any online track in Search, Discover, or Now Playing.",
                    primaryActionLabel = "Explore Online Music",
                    colors = colors,
                    onPrimaryAction = onExploreOnlineMusic,
                    onPlayAll = { shuffle ->
                        if (searchResults.tracks.isNotEmpty()) onPlaySongs(searchResults.tracks, null, shuffle)
                    },
                    onPlaySong = { song -> onPlaySongs(searchResults.tracks, song, false) },
                    onToggleSave = onToggleSaveTrack,
                    onToggleFavorite = onToggleFavoriteTrack,
                    onOpenMore = onOpenSongMore
                )

                OnlineLibraryTab.SAVED_ALBUMS -> SavedAlbumsGridSection(
                    albums = searchResults.albums,
                    colors = colors,
                    onOpenAlbum = onOpenAlbum,
                    onRemoveAlbum = onRemoveAlbum,
                    onExploreOnlineMusic = onExploreOnlineMusic
                )

                OnlineLibraryTab.FOLLOWED_ARTISTS -> FollowedArtistsSection(
                    artists = searchResults.artists,
                    colors = colors,
                    onOpenArtist = onOpenArtist,
                    onUnfollowArtist = onUnfollowArtist,
                    onExploreOnlineMusic = onExploreOnlineMusic
                )

                OnlineLibraryTab.RECENTLY_PLAYED -> OnlineTrackListSection(
                    songs = recentlyPlayed,
                    savedTrackIds = savedTrackIds,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    emptyTitle = "No Recently Played Online Tracks",
                    emptySubtitle = "Songs you stream from YouTube Music, YouTube, SoundCloud, or Bandcamp appear here.",
                    primaryActionLabel = "Explore Online Music",
                    colors = colors,
                    onPrimaryAction = onExploreOnlineMusic,
                    onPlayAll = { shuffle ->
                        if (recentlyPlayed.isNotEmpty()) onPlaySongs(recentlyPlayed, null, shuffle)
                    },
                    onPlaySong = { song -> onPlaySongs(recentlyPlayed, song, false) },
                    onToggleSave = onToggleSaveTrack,
                    onToggleFavorite = onToggleFavoriteTrack,
                    onOpenMore = onOpenSongMore
                )

                OnlineLibraryTab.HISTORY -> OnlineTrackListSection(
                    songs = searchResults.history,
                    savedTrackIds = savedTrackIds,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    emptyTitle = "Online Listening History is Empty",
                    emptySubtitle = "Your online streaming history across supported providers will be tracked here.",
                    primaryActionLabel = "Explore Online Music",
                    showPlayCounts = true,
                    headerExtraAction = if (searchResults.history.isNotEmpty()) {
                        {
                            TextButton(
                                onClick = { showClearHistoryConfirm = true },
                                modifier = Modifier.testTag("clear_online_history_button")
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteSweep,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, color = colors.accentStrong, fontSize = 12.sp)
                            }
                        }
                    } else null,
                    colors = colors,
                    onPrimaryAction = onExploreOnlineMusic,
                    onPlayAll = { shuffle ->
                        if (searchResults.history.isNotEmpty()) onPlaySongs(searchResults.history, null, shuffle)
                    },
                    onPlaySong = { song -> onPlaySongs(searchResults.history, song, false) },
                    onToggleSave = onToggleSaveTrack,
                    onToggleFavorite = onToggleFavoriteTrack,
                    onOpenMore = onOpenSongMore
                )

                OnlineLibraryTab.FAVOURITES -> OnlineTrackListSection(
                    songs = searchResults.favorites,
                    savedTrackIds = savedTrackIds,
                    currentSongId = currentSongId,
                    isPlaying = isPlaying,
                    emptyTitle = "No Online Favourites Yet",
                    emptySubtitle = "Tap the heart icon on any online track to keep your favourites in one place.",
                    primaryActionLabel = "Explore Online Music",
                    colors = colors,
                    onPrimaryAction = onExploreOnlineMusic,
                    onPlayAll = { shuffle ->
                        if (searchResults.favorites.isNotEmpty()) onPlaySongs(searchResults.favorites, null, shuffle)
                    },
                    onPlaySong = { song -> onPlaySongs(searchResults.favorites, song, false) },
                    onToggleSave = onToggleSaveTrack,
                    onToggleFavorite = onToggleFavoriteTrack,
                    onOpenMore = onOpenSongMore
                )
            }
        }
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showClearHistoryConfirm = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Clear Online Listening History?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This resets your online play history. Saved songs, albums, followed artists, online playlists, and downloaded files are kept safe.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearHistoryConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Text("Clear History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryConfirm = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun ProviderChip(
    label: String,
    selected: Boolean,
    colors: EditorialColors,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.accentPill else colors.surface,
        border = BorderStroke(0.75.dp, if (selected) colors.accentStrong else colors.cardBorder),
        modifier = Modifier
            .height(32.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.5.sp,
                color = if (selected) colors.onAccentPill else colors.textSecondary
            )
        }
    }
}

@Composable
private fun OnlineOverviewHub(
    results: OnlineLibrarySearchResults,
    recentlyPlayed: List<Song>,
    savedTrackIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onJumpToTab: (OnlineLibraryTab) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (OnlinePlaylist) -> Unit,
    onPlaySongs: (List<Song>, Song?, Boolean) -> Unit,
    onToggleSaveTrack: (Song) -> Unit,
    onOpenSongMore: (Song) -> Unit,
    onOpenAlbum: (SavedOnlineAlbum) -> Unit,
    onOpenArtist: (FollowedOnlineArtist) -> Unit,
    onExploreOnlineMusic: () -> Unit
) {
    val completelyEmpty = results.isEmpty && recentlyPlayed.isEmpty()

    if (completelyEmpty) {
        OnlineLibraryEmptyState(
            icon = Icons.Rounded.CloudQueue,
            title = if (results.query.isNotBlank()) "No Saved Online Matches" else "Your Online Library Awaits",
            subtitle = if (results.query.isNotBlank()) {
                "Nothing in your saved online songs, albums, artists, or playlists matched \"${results.query}\"."
            } else {
                "Save songs, follow artists, bookmark albums, or build cross-provider playlists from YouTube Music, YouTube, SoundCloud, and Bandcamp."
            },
            primaryActionLabel = "Explore Online Music",
            secondaryActionLabel = "New Online Playlist",
            colors = colors,
            onPrimaryAction = onExploreOnlineMusic,
            onSecondaryAction = onCreatePlaylist
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 6.dp, bottom = 152.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Online Playlists Carousel
        if (results.playlists.isNotEmpty()) {
            item(key = "overview_playlists") {
                Column {
                    OverviewSectionHeader(
                        title = "Online Playlists",
                        count = results.playlists.size,
                        colors = colors,
                        onSeeAll = { onJumpToTab(OnlineLibraryTab.PLAYLISTS) }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(results.playlists, key = { "ov_pl_${it.id}" }) { playlist ->
                            OnlinePlaylistCarouselCard(
                                playlist = playlist,
                                colors = colors,
                                onClick = { onOpenPlaylist(playlist) }
                            )
                        }
                    }
                }
            }
        }

        // 2. Followed Artists Carousel
        if (results.artists.isNotEmpty()) {
            item(key = "overview_artists") {
                Column {
                    OverviewSectionHeader(
                        title = "Followed Artists",
                        count = results.artists.size,
                        colors = colors,
                        onSeeAll = { onJumpToTab(OnlineLibraryTab.FOLLOWED_ARTISTS) }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(results.artists, key = { "ov_art_${it.stableId}" }) { artist ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(86.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onOpenArtist(artist) }
                                    .padding(vertical = 4.dp)
                            ) {
                                OnlineTrackThumbnail(
                                    artworkUrl = artist.artworkUrl,
                                    title = artist.name,
                                    kind = OnlineItemKind.ARTIST,
                                    colors = colors,
                                    size = 74.dp,
                                    cornerRadius = 37.dp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = artist.name,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = artist.providerName,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 10.sp,
                                    color = colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Saved Albums Carousel
        if (results.albums.isNotEmpty()) {
            item(key = "overview_albums") {
                Column {
                    OverviewSectionHeader(
                        title = "Saved Albums",
                        count = results.albums.size,
                        colors = colors,
                        onSeeAll = { onJumpToTab(OnlineLibraryTab.SAVED_ALBUMS) }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(results.albums, key = { "ov_alb_${it.stableId}" }) { album ->
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = colors.surface,
                                border = BorderStroke(0.75.dp, colors.cardBorder),
                                modifier = Modifier
                                    .width(152.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { onOpenAlbum(album) }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    OnlineTrackThumbnail(
                                        artworkUrl = album.artworkUrl,
                                        title = album.title,
                                        kind = OnlineItemKind.ALBUM,
                                        colors = colors,
                                        size = 132.dp,
                                        cornerRadius = 14.dp
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = album.title,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${album.artist} • ${album.providerName}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Saved Online Songs preview
        if (results.tracks.isNotEmpty()) {
            item(key = "overview_saved_header") {
                OverviewSectionHeader(
                    title = "Saved Online Songs",
                    count = results.tracks.size,
                    colors = colors,
                    onSeeAll = { onJumpToTab(OnlineLibraryTab.SAVED_SONGS) }
                )
            }
            items(results.tracks.take(6), key = { "ov_song_${it.id}" }) { song ->
                OnlineLibraryTrackRow(
                    song = song,
                    isSaved = song.id in savedTrackIds,
                    isCurrent = currentSongId == song.id,
                    isPlaying = isPlaying,
                    showPlayCount = false,
                    colors = colors,
                    onPlayClick = { onPlaySongs(results.tracks, song, false) },
                    onToggleSave = { onToggleSaveTrack(song) },
                    onMoreClick = { onOpenSongMore(song) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // 5. Recently Played Online preview
        if (recentlyPlayed.isNotEmpty()) {
            item(key = "overview_recent_header") {
                OverviewSectionHeader(
                    title = "Recently Played Online",
                    count = recentlyPlayed.size,
                    colors = colors,
                    onSeeAll = { onJumpToTab(OnlineLibraryTab.RECENTLY_PLAYED) }
                )
            }
            items(recentlyPlayed.take(5), key = { "ov_rec_${it.id}" }) { song ->
                OnlineLibraryTrackRow(
                    song = song,
                    isSaved = song.id in savedTrackIds,
                    isCurrent = currentSongId == song.id,
                    isPlaying = isPlaying,
                    showPlayCount = false,
                    colors = colors,
                    onPlayClick = { onPlaySongs(recentlyPlayed, song, false) },
                    onToggleSave = { onToggleSaveTrack(song) },
                    onMoreClick = { onOpenSongMore(song) },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    }
}

@Composable
private fun OverviewSectionHeader(
    title: String,
    count: Int,
    colors: EditorialColors,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$title ($count)",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = colors.textPrimary
        )
        TextButton(onClick = onSeeAll) {
            Text(
                text = "See all",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = colors.accentStrong
            )
        }
    }
}

@Composable
private fun OnlinePlaylistCarouselCard(
    playlist: OnlinePlaylist,
    colors: EditorialColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            OnlineTrackThumbnail(
                artworkUrl = playlist.displayArtworkUrl,
                title = playlist.name,
                kind = OnlineItemKind.PLAYLIST,
                colors = colors,
                size = 136.dp,
                cornerRadius = 14.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = playlist.name,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${playlist.trackCount} tracks" +
                    if (playlist.providerName.isNotBlank()) " • ${playlist.providerName}" else "",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OnlinePlaylistsSection(
    playlists: List<OnlinePlaylist>,
    colors: EditorialColors,
    onCreatePlaylist: () -> Unit,
    onOpenPlaylist: (OnlinePlaylist) -> Unit,
    onPlayPlaylist: (OnlinePlaylist) -> Unit
) {
    if (playlists.isEmpty()) {
        OnlineLibraryEmptyState(
            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
            title = "No Online Playlists Yet",
            subtitle = "Create custom online playlists or save playlists from YouTube Music, YouTube, SoundCloud, and Bandcamp.",
            primaryActionLabel = "Create Online Playlist",
            colors = colors,
            onPrimaryAction = onCreatePlaylist
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 152.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "create_online_playlist_banner") {
            Surface(
                onClick = onCreatePlaylist,
                shape = RoundedCornerShape(20.dp),
                color = colors.accentPill.copy(alpha = 0.55f),
                border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("online_playlists_new_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(colors.accentStrong, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.onAccentStrong)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Create Online Playlist",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Mix tracks across any supported online provider",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpenPlaylist(playlist) }
                    .testTag("online_playlist_card_${playlist.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OnlineTrackThumbnail(
                        artworkUrl = playlist.displayArtworkUrl,
                        title = playlist.name,
                        kind = OnlineItemKind.PLAYLIST,
                        colors = colors,
                        size = 54.dp,
                        cornerRadius = 14.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.name,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${playlist.trackCount} online tracks" +
                                if (playlist.providerName.isNotBlank()) " • ${playlist.providerName}" else "",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (playlist.tracks.isNotEmpty()) {
                        IconButton(
                            onClick = { onPlayPlaylist(playlist) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "Play ${playlist.name}",
                                tint = colors.accentStrong
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedAlbumsGridSection(
    albums: List<SavedOnlineAlbum>,
    colors: EditorialColors,
    onOpenAlbum: (SavedOnlineAlbum) -> Unit,
    onRemoveAlbum: (SavedOnlineAlbum) -> Unit,
    onExploreOnlineMusic: () -> Unit
) {
    if (albums.isEmpty()) {
        OnlineLibraryEmptyState(
            icon = Icons.Rounded.Album,
            title = "No Saved Online Albums",
            subtitle = "Open any album from Online Search or Discover and tap Save Album to bookmark it here.",
            primaryActionLabel = "Explore Online Music",
            colors = colors,
            onPrimaryAction = onExploreOnlineMusic
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 152.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.stableId }) { album ->
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onOpenAlbum(album) }
                    .testTag("saved_online_album_${album.stableId}")
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OnlineTrackThumbnail(
                            artworkUrl = album.artworkUrl,
                            title = album.title,
                            kind = OnlineItemKind.ALBUM,
                            colors = colors,
                            size = 142.dp,
                            cornerRadius = 16.dp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                        Surface(
                            shape = CircleShape,
                            color = colors.surface.copy(alpha = 0.9f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable { onRemoveAlbum(album) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.BookmarkAdded,
                                    contentDescription = "Remove saved album",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = album.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artist,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            append(album.providerName)
                            if (album.trackCount > 0L) append(" • ${album.trackCount} tracks")
                        },
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 10.5.sp,
                        color = colors.accentStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowedArtistsSection(
    artists: List<FollowedOnlineArtist>,
    colors: EditorialColors,
    onOpenArtist: (FollowedOnlineArtist) -> Unit,
    onUnfollowArtist: (FollowedOnlineArtist) -> Unit,
    onExploreOnlineMusic: () -> Unit
) {
    if (artists.isEmpty()) {
        OnlineLibraryEmptyState(
            icon = Icons.Rounded.Explore,
            title = "No Followed Online Artists",
            subtitle = "Follow artists from Online Search results, Artist pages, or track menus to keep them here.",
            primaryActionLabel = "Explore Online Music",
            colors = colors,
            onPrimaryAction = onExploreOnlineMusic
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 152.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(artists, key = { it.stableId }) { artist ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpenArtist(artist) }
                    .testTag("followed_online_artist_${artist.stableId}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OnlineTrackThumbnail(
                        artworkUrl = artist.artworkUrl,
                        title = artist.name,
                        kind = OnlineItemKind.ARTIST,
                        colors = colors,
                        size = 52.dp,
                        cornerRadius = 26.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
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
                            text = artist.providerName,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { onUnfollowArtist(artist) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PersonRemove,
                            contentDescription = "Unfollow ${artist.name}",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTrackListSection(
    songs: List<Song>,
    savedTrackIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    emptyTitle: String,
    emptySubtitle: String,
    primaryActionLabel: String,
    showPlayCounts: Boolean = false,
    headerExtraAction: (@Composable () -> Unit)? = null,
    colors: EditorialColors,
    onPrimaryAction: () -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onPlaySong: (Song) -> Unit,
    onToggleSave: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenMore: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        OnlineLibraryEmptyState(
            icon = if (showPlayCounts) Icons.Rounded.History else Icons.Rounded.CloudQueue,
            title = emptyTitle,
            subtitle = emptySubtitle,
            primaryActionLabel = primaryActionLabel,
            colors = colors,
            onPrimaryAction = onPrimaryAction
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${songs.size} online tracks",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = colors.textMuted
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                headerExtraAction?.invoke()
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentStrong,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPlayAll(false) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play All", tint = colors.onAccentStrong, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play All", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = colors.onAccentStrong)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentPill,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPlayAll(true) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = colors.onAccentPill, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 152.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(songs, key = { "${it.id}_${it.contentUrl}" }) { song ->
                OnlineLibraryTrackRow(
                    song = song,
                    isSaved = song.id in savedTrackIds,
                    isCurrent = currentSongId == song.id,
                    isPlaying = isPlaying,
                    showPlayCount = showPlayCounts,
                    colors = colors,
                    onPlayClick = { onPlaySong(song) },
                    onToggleSave = { onToggleSave(song) },
                    onToggleFavorite = { onToggleFavorite(song) },
                    onMoreClick = { onOpenMore(song) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnlineLibraryTrackRow(
    song: Song,
    isSaved: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    showPlayCount: Boolean,
    colors: EditorialColors,
    onPlayClick: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleFavorite: (() -> Unit)? = null,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrent) colors.accentPill.copy(alpha = 0.42f) else colors.surface,
        border = BorderStroke(
            0.75.dp,
            if (isCurrent) colors.accentStrong.copy(alpha = 0.55f) else colors.cardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onPlayClick,
                onLongClick = onMoreClick
            )
            .testTag("online_library_track_${song.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnlineTrackThumbnail(
                artworkUrl = song.artworkUrl,
                title = song.title,
                kind = OnlineItemKind.TRACK,
                colors = colors,
                size = 50.dp,
                cornerRadius = 12.dp,
                isPlaying = isCurrent && isPlaying
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.durationFormatted}",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceTinted
                    ) {
                        Text(
                            text = song.providerName.ifBlank { "Online" },
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                    if (showPlayCount && song.playCount > 0) {
                        Text(
                            text = "${song.playCount} plays",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 10.5.sp,
                            color = colors.textMuted
                        )
                    }
                }
            }
            if (onToggleFavorite != null) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = if (song.isFavorite) colors.accentStrong else colors.textMuted,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            IconButton(
                onClick = onToggleSave,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("online_lib_save_toggle_${song.id}")
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    contentDescription = if (isSaved) "Remove from Online Library" else "Save to Online Library",
                    tint = if (isSaved) colors.accentStrong else colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options for ${song.title}",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun OnlineLibraryEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    primaryActionLabel: String,
    secondaryActionLabel: String? = null,
    colors: EditorialColors,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onAccentPill,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimaryAction,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(primaryActionLabel, fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                }
                if (secondaryActionLabel != null && onSecondaryAction != null) {
                    Surface(
                        onClick = onSecondaryAction,
                        shape = RoundedCornerShape(18.dp),
                        color = colors.accentPill,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = secondaryActionLabel,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = colors.onAccentPill
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnlineLibraryLoadingSkeleton(colors: EditorialColors) {
    val transition = rememberInfiniteTransition(label = "online_skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "online_skeleton_alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .alpha(pulse),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceTinted)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceTinted)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colors.surfaceTinted)
            )
        }
    }
}
