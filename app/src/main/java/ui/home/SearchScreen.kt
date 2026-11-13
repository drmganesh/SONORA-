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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import ui.components.CircularArtwork
import ui.components.CollectionDetailSheet
import ui.components.EditorialPlaylistPickerDialog
import ui.components.MusicContextMenuSheet
import ui.theme.EditorialColors

private data class SearchCollectionModal(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

/**
 * Editorial Search Screen for the Floating Bottom Navigation's Search destination.
 * Searches across real local songs, albums, artists, genres, and playlists cleanly.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    viewModel: SonoraViewModel,
    colors: EditorialColors,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSongs by viewModel.allSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()

    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableIntStateOf(0) } // 0=All, 1=Songs, 2=Albums, 3=Artists, 4=Playlists, 5=Folders
    var activeModal by remember { mutableStateOf<SearchCollectionModal?>(null) }
    var contextMenuSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }

    val haptic = LocalHapticFeedback.current
    val filters = listOf("All", "Songs", "Albums", "Artists", "Playlists", "Folders")

    val smartSuggestions = remember(artists, genres, albums) {
        val topArtists = artists.take(4).map { it.name }
        val topGenres = genres.take(3).map { it.name }
        val topAlbums = albums.take(2).map { it.title }
        (topArtists + topGenres + topAlbums).filter { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }.distinct().take(8)
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .testTag("search_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
        ) {
            // Editorial Title Row + Settings icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 36.sp,
                    letterSpacing = (-0.8).sp,
                    color = colors.textPrimary
                )

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

            // Pill Search Field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        text = "Songs, artists, albums, or genres...",
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
                        IconButton(onClick = { query = "" }) {
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
                    onSearch = {
                        if (query.isNotBlank()) viewModel.addRecentSearch(query)
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
                    .testTag("editorial_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Filter Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filters.indices.toList()) { idx ->
                    val selected = selectedFilter == idx
                    Surface(
                        onClick = { selectedFilter = idx },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) colors.accentPill else colors.surface,
                        border = androidx.compose.foundation.BorderStroke(
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
                                text = filters[idx],
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selected) colors.onAccentPill else colors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 184.dp, top = 6.dp)
            ) {
                if (query.isBlank() && recentSearches.isNotEmpty()) {
                    item(key = "recent_searches_block") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Recent Searches",
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
                                    .clickable { viewModel.clearRecentSearches() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(recentSearches, key = { "rec_$it" }) { term ->
                                Surface(
                                    onClick = { query = term },
                                    shape = RoundedCornerShape(18.dp),
                                    color = colors.surface,
                                    border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = term,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.textPrimary
                                        )
                                        IconButton(
                                            onClick = { viewModel.removeRecentSearch(term) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove search $term",
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                if (query.isBlank() && smartSuggestions.isNotEmpty()) {
                    item(key = "smart_suggestions") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Smart Suggestions",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(smartSuggestions, key = { "sug_$it" }) { term ->
                                Surface(
                                    onClick = {
                                        query = term
                                        viewModel.addRecentSearch(term)
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = colors.accentPill.copy(alpha = 0.65f),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = term,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = colors.onAccentPill
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }

                if (query.isBlank() && genres.isNotEmpty()) {
                    item(key = "browse_genres") {
                        Text(
                            text = "Browse Genres",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(genres.take(12), key = { it.name }) { g ->
                                Surface(
                                    onClick = {
                                        val genreSongs = allSongs.filter { it.genre == g.name }
                                        activeModal = SearchCollectionModal(
                                            title = g.name,
                                            subtitle = "Genre Collection",
                                            songs = genreSongs
                                        )
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    color = colors.surfaceTinted,
                                    border = androidx.compose.foundation.BorderStroke(0.75.dp, colors.cardBorder)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                        Text(
                                            text = g.name,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "${g.songCount} tracks",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Matched Artists
                if ((selectedFilter == 0 || selectedFilter == 3) && matchedArtists.isNotEmpty()) {
                    item(key = "artists_header") {
                        Text(
                            text = "Artists",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                        )
                    }
                    items(matchedArtists, key = { "sr_art_${it.name}" }) { artist ->
                        val artistSongs = allSongs.filter { it.artist == artist.name }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surface)
                                .clickable {
                                    activeModal = SearchCollectionModal(
                                        title = artist.name,
                                        subtitle = "${artist.albumCount} albums",
                                        songs = artistSongs
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colors.accentPill, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.onAccentPill)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = artist.name,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${artist.songCount} songs",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Matched Albums
                if ((selectedFilter == 0 || selectedFilter == 2) && matchedAlbums.isNotEmpty()) {
                    item(key = "albums_header") {
                        Text(
                            text = "Albums",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                        )
                    }
                    items(matchedAlbums, key = { "sr_alb_${it.id}_${it.title}" }) { album ->
                        val albumSongs = allSongs.filter { it.album == album.title }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surface)
                                .clickable {
                                    activeModal = SearchCollectionModal(
                                        title = album.title,
                                        subtitle = album.artist,
                                        songs = albumSongs
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularArtwork(
                                song = albumSongs.firstOrNull(),
                                fallbackLabel = album.title,
                                accentColor = colors.accentPill,
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = album.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
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
                            Icon(Icons.Rounded.Album, contentDescription = null, tint = colors.textMuted)
                        }
                    }
                }

                // Matched Playlists
                if ((selectedFilter == 0 || selectedFilter == 4) && matchedPlaylists.isNotEmpty()) {
                    item(key = "playlists_header") {
                        Text(
                            text = "Playlists",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                        )
                    }
                    items(matchedPlaylists, key = { "sr_pl_${it.id}" }) { playlist ->
                        val playlistSongs by viewModel.getSongsForPlaylistFlow(playlist.id).collectAsState(initial = emptyList())
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surface)
                                .clickable {
                                    if (query.isNotBlank()) viewModel.addRecentSearch(query)
                                    activeModal = SearchCollectionModal(
                                        title = playlist.name,
                                        subtitle = "Custom Playlist",
                                        songs = playlistSongs
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colors.accentPill, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = colors.onAccentPill)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = playlist.name,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "${playlistSongs.size} songs",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // Matched Folders
                if ((selectedFilter == 0 || selectedFilter == 5) && matchedFolders.isNotEmpty()) {
                    item(key = "folders_header") {
                        Text(
                            text = "Folders",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                        )
                    }
                    items(matchedFolders, key = { "sr_fld_${it.path}" }) { folder ->
                        val folderSongs = allSongs.filter { it.dataPath.startsWith(folder.path) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surface)
                                .clickable {
                                    if (query.isNotBlank()) viewModel.addRecentSearch(query)
                                    activeModal = SearchCollectionModal(
                                        title = folder.name,
                                        subtitle = folder.path,
                                        songs = folderSongs
                                    )
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(colors.accentPill, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.onAccentPill)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.name,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${folder.songCount} songs • ${folder.path}",
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

                // Matched Songs
                if ((selectedFilter == 0 || selectedFilter == 1) && matchedSongs.isNotEmpty()) {
                    item(key = "songs_header") {
                        Text(
                            text = if (query.isBlank()) "Quick Library Picks" else "Songs (${matchedSongs.size})",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
                        )
                    }

                    items(matchedSongs, key = { "sr_song_${it.id}" }) { song ->
                        val isCurrent = currentSong?.id == song.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 3.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isCurrent) colors.accentPill.copy(alpha = 0.55f) else colors.surface)
                                .combinedClickable(
                                    onClick = {
                                        if (query.isNotBlank()) viewModel.addRecentSearch(query)
                                        viewModel.playSong(song, matchedSongs)
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        contextMenuSong = song
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularArtwork(
                                song = song,
                                isPlaying = isCurrent && playbackInfo.isPlaying,
                                accentColor = colors.accentPill,
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(44.dp)
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
                                    text = "${song.artist} • ${song.album}",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { contextMenuSong = song },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Options",
                                    tint = colors.textMuted
                                )
                            }
                        }
                    }
                }

                if (query.isNotBlank() && matchedSongs.isEmpty() && matchedAlbums.isEmpty() && matchedArtists.isEmpty() && matchedPlaylists.isEmpty() && matchedFolders.isEmpty()) {
                    item(key = "no_results") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tracks, albums, or artists found for \"$query\"",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 14.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }

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
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) }
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
}
