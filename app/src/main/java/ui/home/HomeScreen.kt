package ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SpatialAudio
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import domain.home.HomeDiscoveryEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.CircularArtwork
import ui.components.CollectionDetailSheet
import ui.components.EditorialArtworkCard
import ui.components.EditorialPlaylistPickerDialog
import ui.components.MusicContextMenuSheet
import ui.theme.EditorialAccentSwatches
import ui.theme.EditorialColors
import ui.theme.HomeThemeMode

private data class ActiveCollectionModal(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

/**
 * Redesigned Editorial Music Discovery Home Screen for SONORA.
 * Integrates seamlessly with real local library scanning, playback engine, Room metadata,
 * dynamic album-art theming, and floating bottom navigation.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: SonoraViewModel,
    colors: EditorialColors,
    onOpenSettings: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenEqualizer: () -> Unit = {},
    onOpenAudioStudio: () -> Unit = {},
    onOpenPlaylists: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allSongs by viewModel.allSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val favouriteAlbums by viewModel.favouriteAlbums.collectAsState()
    val favouriteArtists by viewModel.favouriteArtists.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val yourMixSongs by viewModel.yourMixSongs.collectAsState()
    val yourMixContributors by viewModel.yourMixContributorsSummary.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val homeThemeMode by viewModel.homeThemeMode.collectAsState()
    val customAccentArgb by viewModel.homeCustomAccentArgb.collectAsState()

    val greeting = remember { HomeDiscoveryEngine.currentGreeting() }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var contextMenuSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var activeCollection by remember { mutableStateOf<ActiveCollectionModal?>(null) }
    var showDiscoverySheet by remember { mutableStateOf(false) }

    // Derived collage songs (distinct albums for artistic collage variety)
    val collageSongs = remember(allSongs, yourMixSongs) {
        val pool = (yourMixSongs + allSongs).distinctBy { it.albumId }
        if (pool.size >= 3) pool.take(4) else allSongs.take(4)
    }

    // Artist chips for Your Mix strip
    val artistChips = remember(yourMixSongs, allSongs) {
        val source = yourMixSongs.ifEmpty { allSongs }
        source.groupBy { it.artist }
            .entries
            .filter { it.key.isNotBlank() && !it.key.equals("<unknown>", ignoreCase = true) }
            .take(10)
            .map { (name, tracks) -> MixArtistChip(name, tracks.first()) }
    }

    // Quick picks for "Made for You" asymmetric bento
    val quickPicks = remember(allSongs, favorites, mostPlayed) {
        (favorites + mostPlayed + allSongs.shuffled()).distinctBy { it.id }.take(6)
    }

    // Featured spotlight album
    val featuredAlbum = remember(favouriteAlbums, albums) {
        favouriteAlbums.firstOrNull() ?: albums.firstOrNull()
    }
    val featuredAlbumSong = remember(featuredAlbum, allSongs) {
        featuredAlbum?.let { alb -> allSongs.firstOrNull { it.album == alb.title } }
    }

    val isMixActivelyPlaying = playbackInfo.isPlaying && currentSong != null &&
        yourMixSongs.any { it.id == currentSong?.id }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .testTag("home_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isRefreshing = true
                viewModel.loadLocalSongs()
                scope.launch {
                    delay(700)
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 184.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp)
            ) {
                // 1. Top Greeting Header
                item(key = "greeting_header") {
                    GreetingHeader(
                        greeting = greeting,
                        featuredSong = currentSong ?: collageSongs.firstOrNull(),
                        isPlaying = playbackInfo.isPlaying,
                        colors = colors,
                        onAvatarClick = {
                            if (currentSong != null) onOpenNowPlaying()
                            else if (allSongs.isNotEmpty()) viewModel.playSong(allSongs.first(), allSongs)
                        },
                        onDiscoveryClick = { showDiscoverySheet = true },
                        onSettingsClick = onOpenSettings
                    )
                }

                // Empty library graceful fallback
                if (allSongs.isEmpty()) {
                    item(key = "empty_library_state") {
                        EmptyLibraryEditorialCard(
                            colors = colors,
                            onScanLibrary = { viewModel.loadLocalSongs() },
                            onOpenLibrary = onNavigateToLibrary
                        )
                    }
                } else {
                    // 2. Hero Section: Your Mix
                    item(key = "your_mix_hero") {
                        YourMixSection(
                            contributorsSummary = yourMixContributors,
                            artistChips = artistChips,
                            isMixPlaying = isMixActivelyPlaying,
                            hasTracks = yourMixSongs.isNotEmpty(),
                            colors = colors,
                            onPlayMix = {
                                if (isMixActivelyPlaying) {
                                    viewModel.togglePlayPause()
                                } else {
                                    viewModel.playCollection(yourMixSongs, shuffle = false)
                                }
                            },
                            onShuffleMix = {
                                viewModel.playCollection(yourMixSongs, shuffle = true)
                            },
                            onArtistChipClick = { chip ->
                                val artistSongs = allSongs.filter { it.artist.equals(chip.artistName, ignoreCase = true) }
                                activeCollection = ActiveCollectionModal(
                                    title = chip.artistName,
                                    subtitle = "Artist Collection",
                                    songs = artistSongs
                                )
                            }
                        )
                    }

                    // 3. Featured Overlapping Artwork Collage
                    item(key = "featured_collage") {
                        FeaturedArtworkCollage(
                            collageSongs = collageSongs,
                            colors = colors,
                            onArtworkClick = { song ->
                                val albumSongs = allSongs.filter { it.album == song.album }
                                if (albumSongs.isNotEmpty()) {
                                    activeCollection = ActiveCollectionModal(
                                        title = song.album.ifBlank { song.title },
                                        subtitle = song.artist,
                                        songs = albumSongs
                                    )
                                } else {
                                    viewModel.playSong(song, allSongs)
                                }
                            },
                            onArtworkLongClick = { song ->
                                contextMenuSong = song
                            }
                        )
                    }

                    // 4. Continue Listening Card (compact horizontal pill-card)
                    currentSong?.let { activeSong ->
                        item(key = "continue_listening") {
                            ContinueListeningEditorialCard(
                                song = activeSong,
                                isPlaying = playbackInfo.isPlaying,
                                progressFraction = if (playbackInfo.durationMs > 0L) {
                                    (playbackInfo.currentPositionMs.toFloat() / playbackInfo.durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f,
                                colors = colors,
                                onCardClick = onOpenNowPlaying,
                                onPlayPauseClick = { viewModel.togglePlayPause() },
                                onLongClick = { contextMenuSong = activeSong }
                            )
                        }
                    }

                    // 4b. Quick Studio & Queue Pills
                    item(key = "quick_tools_strip") {
                        QuickToolsEditorialStrip(
                            playlistCount = playlists.size,
                            colors = colors,
                            onOpenEqualizer = onOpenEqualizer,
                            onOpenAudioStudio = onOpenAudioStudio,
                            onOpenPlaylists = onOpenPlaylists,
                            onOpenQueue = onOpenQueue
                        )
                    }

                    // 5. Recently Played (Circular artwork medallions)
                    if (recentlyPlayed.isNotEmpty()) {
                        item(key = "recently_played") {
                            RecentlyPlayedSection(
                                songs = recentlyPlayed,
                                currentPlayingSongId = currentSong?.id,
                                isPlaying = playbackInfo.isPlaying,
                                colors = colors,
                                onSongClick = { song -> viewModel.playSong(song, recentlyPlayed) },
                                onSongLongClick = { song -> contextMenuSong = song }
                            )
                        }
                    }

                    // 6. Made for You (Asymmetric compact Quick Picks + Favourites Showcase)
                    item(key = "made_for_you") {
                        MadeForYouEditorialSection(
                            quickPicks = quickPicks,
                            favorites = favorites,
                            currentSongId = currentSong?.id,
                            colors = colors,
                            onSongClick = { song -> viewModel.playSong(song, quickPicks) },
                            onSongLongClick = { song -> contextMenuSong = song },
                            onOpenFavorites = {
                                if (favorites.isNotEmpty()) {
                                    activeCollection = ActiveCollectionModal(
                                        title = "Favourite Tracks",
                                        subtitle = "Your liked collection",
                                        songs = favorites
                                    )
                                }
                            }
                        )
                    }

                    // 7. Daily Mixes (Daily Mix 1..3, Energy, Chill, Acoustic)
                    if (dailyMixes.isNotEmpty()) {
                        item(key = "daily_mixes") {
                            DailyMixSection(
                                mixes = dailyMixes,
                                colors = colors,
                                onMixClick = { mix ->
                                    activeCollection = ActiveCollectionModal(
                                        title = mix.title,
                                        subtitle = mix.subtitle,
                                        songs = mix.songs
                                    )
                                },
                                onPlayMix = { mix ->
                                    viewModel.playCollection(mix.songs, shuffle = false)
                                }
                            )
                        }
                    }

                    // 8. Favourite Albums (Squircle cards)
                    if (favouriteAlbums.isNotEmpty()) {
                        item(key = "favourite_albums") {
                            FavouriteAlbumsSection(
                                albums = favouriteAlbums,
                                allSongs = allSongs,
                                colors = colors,
                                onAlbumClick = { album ->
                                    val albumSongs = allSongs.filter { it.album == album.title }
                                    activeCollection = ActiveCollectionModal(
                                        title = album.title,
                                        subtitle = album.artist,
                                        songs = albumSongs
                                    )
                                },
                                onAlbumLongClick = { song -> contextMenuSong = song }
                            )
                        }
                    }

                    // 9. Favourite Artists (Circular medallions)
                    if (favouriteArtists.isNotEmpty()) {
                        item(key = "favourite_artists") {
                            FavouriteArtistsSection(
                                artists = favouriteArtists,
                                allSongs = allSongs,
                                colors = colors,
                                onArtistClick = { artist ->
                                    val artistSongs = allSongs.filter { it.artist == artist.name }
                                    activeCollection = ActiveCollectionModal(
                                        title = artist.name,
                                        subtitle = "${artist.albumCount} albums",
                                        songs = artistSongs
                                    )
                                }
                            )
                        }
                    }

                    // 10. Recently Added (Compact editorial list rows)
                    if (recentlyAdded.isNotEmpty()) {
                        item(key = "recently_added") {
                            RecentlyAddedSection(
                                songs = recentlyAdded.take(5),
                                currentPlayingSongId = currentSong?.id,
                                colors = colors,
                                onSongClick = { song -> viewModel.playSong(song, recentlyAdded) },
                                onSongLongClick = { song -> contextMenuSong = song },
                                onSeeAllClick = onNavigateToLibrary
                            )
                        }
                    }

                    // 11. Most Played (Arch cards with play counts)
                    val actualMostPlayed = mostPlayed.filter { it.playCount > 0 }.ifEmpty { mostPlayed.take(6) }
                    if (actualMostPlayed.isNotEmpty()) {
                        item(key = "most_played") {
                            MostPlayedArchSection(
                                songs = actualMostPlayed,
                                colors = colors,
                                onSongClick = { song -> viewModel.playSong(song, actualMostPlayed) },
                                onSongLongClick = { song -> contextMenuSong = song }
                            )
                        }
                    }

                    // 12. Featured Music Spotlight Card
                    item(key = "featured_music") {
                        FeaturedMusicSection(
                            featuredAlbum = featuredAlbum,
                            representativeSong = featuredAlbumSong,
                            colors = colors,
                            onOpenAlbum = { album ->
                                val albumSongs = allSongs.filter { it.album == album.title }
                                activeCollection = ActiveCollectionModal(
                                    title = album.title,
                                    subtitle = album.artist,
                                    songs = albumSongs
                                )
                            },
                            onPlayAlbum = { album ->
                                val albumSongs = allSongs.filter { it.album == album.title }
                                viewModel.playCollection(albumSongs, shuffle = false)
                            },
                            onShuffleAlbum = { album ->
                                val albumSongs = allSongs.filter { it.album == album.title }
                                viewModel.playCollection(albumSongs, shuffle = true)
                            },
                            onLongClick = { song -> contextMenuSong = song }
                        )
                    }
                }
            }
        }
    }

    // Long-press Music Context Menu Bottom Sheet
    contextMenuSong?.let { target ->
        MusicContextMenuSheet(
            song = target,
            colors = colors,
            onDismiss = { contextMenuSong = null },
            onPlayNow = { viewModel.playSong(target, allSongs) },
            onPlayNext = { viewModel.playNextInQueue(target) },
            onAddToQueue = { viewModel.addToQueue(target) },
            onAddToPlaylist = { playlistPickerSong = target },
            onToggleFavorite = { viewModel.toggleFavorite(target) },
            onViewAlbum = {
                val albumSongs = allSongs.filter { it.album == target.album }
                activeCollection = ActiveCollectionModal(
                    title = target.album,
                    subtitle = target.artist,
                    songs = albumSongs
                )
            },
            onViewArtist = {
                val artistSongs = allSongs.filter { it.artist == target.artist }
                activeCollection = ActiveCollectionModal(
                    title = target.artist,
                    subtitle = "Artist Discography",
                    songs = artistSongs
                )
            }
        )
    }

    // Add to Playlist Picker
    playlistPickerSong?.let { target ->
        EditorialPlaylistPickerDialog(
            song = target,
            playlists = playlists,
            colors = colors,
            onDismiss = { playlistPickerSong = null },
            onSelectPlaylist = { pid ->
                viewModel.addSongToPlaylist(pid, target.id)
                playlistPickerSong = null
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    // Album / Artist / Daily Mix Collection Detail Sheet
    activeCollection?.let { modal ->
        CollectionDetailSheet(
            title = modal.title,
            subtitle = modal.subtitle,
            songs = modal.songs,
            currentPlayingSongId = currentSong?.id,
            isPlaying = playbackInfo.isPlaying,
            colors = colors,
            onDismiss = { activeCollection = null },
            onPlayAll = {
                viewModel.playCollection(modal.songs, shuffle = false)
            },
            onShufflePlay = {
                viewModel.playCollection(modal.songs, shuffle = true)
            },
            onPlaySong = { track ->
                viewModel.playSong(track, modal.songs)
            },
            onSongMoreClick = { track ->
                contextMenuSong = track
            }
        )
    }

    // Discovery & Editorial Theme Quick Sheet (tapping starburst discovery icon)
    if (showDiscoverySheet) {
        DiscoveryAndThemeSheet(
            allSongs = allSongs,
            currentMode = homeThemeMode,
            customAccentArgb = customAccentArgb,
            colors = colors,
            onDismiss = { showDiscoverySheet = false },
            onDiscoverRandom = {
                val unplayed = allSongs.filter { it.playCount == 0 }.ifEmpty { allSongs }
                viewModel.playCollection(unplayed, shuffle = true)
                showDiscoverySheet = false
            },
            onSelectThemeMode = { mode -> viewModel.setHomeThemeMode(mode) },
            onSelectAccent = { argb -> viewModel.setHomeCustomAccentArgb(argb) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueListeningEditorialCard(
    song: Song,
    isPlaying: Boolean,
    progressFraction: Float,
    colors: EditorialColors,
    onCardClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(24.dp)

    Surface(
        shape = shape,
        color = colors.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .shadow(10.dp, shape, ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            .clip(shape)
            .border(0.75.dp, colors.cardBorder, shape)
            .combinedClickable(
                onClick = onCardClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .testTag("continue_listening_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularArtwork(
                    song = song,
                    isPlaying = isPlaying,
                    accentColor = colors.accentPill,
                    shadowElevation = 4.dp,
                    modifier = Modifier.size(54.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isPlaying) "NOW PLAYING" else "CONTINUE LISTENING",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.8.sp,
                            color = colors.accentStrong
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = song.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.textPrimary,
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
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(colors.accentPill, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Resume",
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colors.surfaceTinted)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .height(3.dp)
                        .background(colors.accentStrong)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MadeForYouEditorialSection(
    quickPicks: List<Song>,
    favorites: List<Song>,
    currentSongId: Long?,
    colors: EditorialColors,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onOpenFavorites: () -> Unit
) {
    if (quickPicks.isEmpty()) return
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("made_for_you_section")
    ) {
        EditorialSectionTitle(
            title = "Made for You",
            subtitle = "Tailored quick picks & favourites",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Highlighted Favourites pill banner if favorites exist
        if (favorites.isNotEmpty()) {
            Surface(
                onClick = onOpenFavorites,
                shape = RoundedCornerShape(22.dp),
                color = colors.accentPill.copy(alpha = 0.72f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(colors.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Liked Songs Mix",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = colors.onAccentPill
                            )
                            Text(
                                text = "${favorites.size} saved favourites ready to play",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.onAccentPill.copy(alpha = 0.75f)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Open Liked Songs",
                        tint = colors.onAccentPill
                    )
                }
            }
        }

        // Compact horizontal 2-per-column quick pick tiles (varied compact row layout)
        val chunked = quickPicks.chunked(2)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chunked) { pair ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.width(248.dp)
                ) {
                    pair.forEach { song ->
                        val isCurrent = currentSongId == song.id
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isCurrent) colors.accentPill.copy(alpha = 0.55f) else colors.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .border(0.75.dp, colors.cardBorder, RoundedCornerShape(18.dp))
                                .combinedClickable(
                                    onClick = { onSongClick(song) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSongLongClick(song)
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularArtwork(
                                    song = song,
                                    accentColor = colors.accentPill,
                                    shadowElevation = 2.dp,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
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
    }
}

@Composable
private fun FavouriteAlbumsSection(
    albums: List<Album>,
    allSongs: List<Song>,
    colors: EditorialColors,
    onAlbumClick: (Album) -> Unit,
    onAlbumLongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("favourite_albums_section")
    ) {
        EditorialSectionTitle(
            title = "Favourite Albums",
            subtitle = "Records you keep returning to",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(albums, key = { "fav_alb_${it.id}_${it.title}" }) { album ->
                val repSong = remember(album.title, allSongs) {
                    allSongs.firstOrNull { it.album == album.title }
                }
                EditorialArtworkCard(
                    song = repSong,
                    title = album.title,
                    subtitle = "${album.artist} • ${album.songCount} songs",
                    colors = colors,
                    artworkSize = 148.dp,
                    cornerRadius = 24.dp,
                    showPlayBadge = true,
                    onClick = { onAlbumClick(album) },
                    onLongClick = repSong?.let { s -> { onAlbumLongClick(s) } },
                    modifier = Modifier.width(156.dp)
                )
            }
        }
    }
}

@Composable
private fun FavouriteArtistsSection(
    artists: List<Artist>,
    allSongs: List<Song>,
    colors: EditorialColors,
    onArtistClick: (Artist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("favourite_artists_section")
    ) {
        EditorialSectionTitle(
            title = "Favourite Artists",
            subtitle = "Voices shaping your rotation",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(artists, key = { "fav_art_${it.name}" }) { artist ->
                val repSong = remember(artist.name, allSongs) {
                    allSongs.firstOrNull { it.artist == artist.name }
                }
                Column(
                    modifier = Modifier
                        .width(96.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onArtistClick(artist) }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularArtwork(
                        song = repSong,
                        fallbackLabel = artist.name,
                        accentColor = colors.accentPill,
                        shadowElevation = 6.dp,
                        borderWidth = 2.dp,
                        borderColor = colors.surface,
                        modifier = Modifier.size(84.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "${artist.songCount} tracks",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyAddedSection(
    songs: List<Song>,
    currentPlayingSongId: Long?,
    colors: EditorialColors,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onSeeAllClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recently_added_section")
    ) {
        EditorialSectionTitle(
            title = "Recently Added",
            subtitle = "Fresh arrivals in your local library",
            colors = colors,
            actionLabel = "See All",
            onActionClick = onSeeAllClick
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = RoundedCornerShape(26.dp),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .border(0.75.dp, colors.cardBorder, RoundedCornerShape(26.dp))
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                songs.forEachIndexed { idx, song ->
                    val isCurrent = currentPlayingSongId == song.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onSongClick(song) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSongLongClick(song)
                                }
                            )
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularArtwork(
                            song = song,
                            isPlaying = isCurrent,
                            accentColor = colors.accentPill,
                            shadowElevation = 3.dp,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
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
                        }
                        IconButton(
                            onClick = { onSongLongClick(song) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More actions for ${song.title}",
                                tint = colors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (idx < songs.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(0.5.dp)
                                .background(colors.cardBorder)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MostPlayedArchSection(
    songs: List<Song>,
    colors: EditorialColors,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("most_played_section")
    ) {
        EditorialSectionTitle(
            title = "Most Played",
            subtitle = "Your top spun tracks",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(songs, key = { _, s -> "most_${s.id}" }) { index, song ->
                EditorialArtworkCard(
                    song = song,
                    title = song.title,
                    subtitle = if (song.playCount > 0) "${song.playCount} plays • ${song.artist}" else song.artist,
                    colors = colors,
                    artworkSize = 136.dp,
                    cornerRadius = 68.dp, // Arch / pill-rounded silhouette for layout variety
                    badgeText = "#${index + 1}",
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) },
                    modifier = Modifier.width(144.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryEditorialCard(
    colors: EditorialColors,
    onScanLibrary: () -> Unit,
    onOpenLibrary: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(28.dp))
            .testTag("empty_library_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    tint = colors.onAccentPill,
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Your Editorial Stage Awaits",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "No local audio files matched yet. Scan your device storage or check your folder filters to populate Your Mix and Daily Mixes.",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onScanLibrary,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Music", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenLibrary,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPill,
                        contentColor = colors.onAccentPill
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Library", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveryAndThemeSheet(
    allSongs: List<Song>,
    currentMode: HomeThemeMode,
    customAccentArgb: Long?,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onDiscoverRandom: () -> Unit,
    onSelectThemeMode: (HomeThemeMode) -> Unit,
    onSelectAccent: (Long?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("discovery_theme_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(colors.accentPill, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Explore, contentDescription = null, tint = colors.onAccentPill)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Discovery & Home Look",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Shuffle fresh tracks or customize your editorial palette",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Discovery instant mix button
            if (allSongs.isNotEmpty()) {
                Button(
                    onClick = onDiscoverRandom,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Start Discovery Shuffle (${allSongs.size} tracks)",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            Text(
                text = "SONORA AESTHETIC THEMES",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = colors.textMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(HomeThemeMode.entries, key = { it.id }) { mode ->
                    val selected = currentMode == mode
                    Surface(
                        onClick = { onSelectThemeMode(mode) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) colors.accentPill.copy(alpha = 0.72f) else colors.surfaceTinted,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (selected) 1.5.dp else 0.75.dp,
                            color = if (selected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier.width(152.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(mode.previewColorArgb))
                                            .border(1.dp, colors.cardBorder, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(mode.previewAccentArgb))
                                            .border(1.dp, colors.surface, CircleShape)
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = colors.accentStrong,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = mode.displayName,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mode.subtitle,
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

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "CUSTOM ACCENT COLOUR",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = colors.textMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val isDefault = customAccentArgb == null
                    Surface(
                        onClick = { onSelectAccent(null) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDefault) colors.accentStrong else colors.surfaceTinted,
                        modifier = Modifier.height(42.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Default",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = if (isDefault) colors.onAccentStrong else colors.textPrimary
                            )
                        }
                    }
                }

                items(EditorialAccentSwatches.presets) { (name, argb) ->
                    val isPicked = customAccentArgb == argb
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(
                                width = if (isPicked) 3.dp else 1.dp,
                                color = if (isPicked) colors.textPrimary else colors.cardBorder,
                                shape = CircleShape
                            )
                            .clickable { onSelectAccent(argb) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPicked) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = name,
                                tint = Color(0xFF15151C),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolsEditorialStrip(
    playlistCount: Int,
    colors: EditorialColors,
    onOpenEqualizer: () -> Unit,
    onOpenAudioStudio: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val entries = listOf(
        Triple("15-Band EQ", Icons.Rounded.Tune, onOpenEqualizer),
        Triple("Audio Studio", Icons.Rounded.SpatialAudio, onOpenAudioStudio),
        Triple("Playlists ($playlistCount)", Icons.Rounded.LibraryMusic, onOpenPlaylists),
        Triple("Up Next Queue", Icons.Rounded.QueueMusic, onOpenQueue)
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag("home_quick_tools_strip")
    ) {
        items(entries) { (label, icon, onClick) ->
            Surface(
                onClick = onClick,
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("quick_tool_${label.lowercase().replace(Regex("[^a-z0-9]+"), "_")}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.accentPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = label,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.textPrimary
                    )
                }
            }
        }
    }
}
