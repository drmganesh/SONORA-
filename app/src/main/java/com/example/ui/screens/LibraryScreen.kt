package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Genre
import com.example.model.MusicFolder
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraOnGold
import com.example.ui.theme.SonoraSurfaceDark
import com.example.ui.theme.SonoraSurfaceElevated
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import com.example.model.SortOption
import com.example.viewmodel.SonoraViewModel

@Composable
fun LibraryScreen(
    viewModel: SonoraViewModel,
    modifier: Modifier = Modifier
) {
    val songs by viewModel.filteredSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Songs", "Albums", "Artists", "Genres", "Folders")

    var showSortMenu by remember { mutableStateOf(false) }
    var songToEdit by remember { mutableStateOf<Song?>(null) }
    var songForPlaylistPicker by remember { mutableStateOf<Song?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        // Search & Filter Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search songs, artists, albums...", color = SonoraTextMuted, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = SonoraGold)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = SonoraTextSecondary)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SonoraGold,
                    unfocusedBorderColor = SonoraCardBorder,
                    focusedTextColor = SonoraTextPrimary,
                    unfocusedTextColor = SonoraTextPrimary,
                    focusedContainerColor = SonoraSurfaceDark,
                    unfocusedContainerColor = SonoraSurfaceDark
                ),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("library_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Sort Menu Button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(SonoraSurfaceElevated, RoundedCornerShape(14.dp))
                        .border(1.dp, SonoraCardBorder, RoundedCornerShape(14.dp))
                        .testTag("sort_button")
                ) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort Options", tint = SonoraGold)
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(SonoraSurfaceDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Title (A-Z)", color = SonoraTextPrimary) },
                        onClick = {
                            viewModel.setSortOption(SortOption.TITLE_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Title (Z-A)", color = SonoraTextPrimary) },
                        onClick = {
                            viewModel.setSortOption(SortOption.TITLE_DESC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Artist", color = SonoraTextPrimary) },
                        onClick = {
                            viewModel.setSortOption(SortOption.ARTIST_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Album", color = SonoraTextPrimary) },
                        onClick = {
                            viewModel.setSortOption(SortOption.ALBUM_ASC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Duration (Longest)", color = SonoraTextPrimary) },
                        onClick = {
                            viewModel.setSortOption(SortOption.DURATION_DESC)
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Date Added", color = SonoraTextPrimary) },
                        onClick = {
                            viewModel.setSortOption(SortOption.DATE_ADDED_DESC)
                            showSortMenu = false
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Rescan Library Button
            IconButton(
                onClick = { viewModel.loadLocalSongs() },
                modifier = Modifier
                    .size(48.dp)
                    .background(SonoraSurfaceElevated, RoundedCornerShape(14.dp))
                    .border(1.dp, SonoraCardBorder, RoundedCornerShape(14.dp))
                    .testTag("rescan_library_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Rescan Library", tint = SonoraCyan)
            }
        }

        // Library Category Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.Transparent,
            contentColor = SonoraGold,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                    color = SonoraGold,
                    height = 3.dp
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = tabTitle,
                            color = if (selectedTabIndex == index) SonoraGold else SonoraTextSecondary,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        // Tab Content
        when (selectedTabIndex) {
            0 -> {
                // Songs List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${songs.size} Audio Tracks",
                                color = SonoraTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    items(songs, key = { it.id }) { song ->
                        SongListItem(
                            song = song,
                            isPlaying = playbackInfo.isPlaying && currentSong?.id == song.id,
                            onClick = { viewModel.playSong(song, songs) },
                            onFavoriteToggle = { viewModel.toggleFavorite(song) },
                            onPlayNext = { viewModel.playNextInQueue(song) },
                            onAddToQueue = { viewModel.addToQueue(song) },
                            onAddToPlaylist = { songForPlaylistPicker = song },
                            onEditMetadata = { songToEdit = song }
                        )
                    }
                }
            }

            1 -> {
                // Albums Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(albums) { album ->
                        AlbumCard(
                            album = album,
                            onClick = {
                                val albumSongs = songs.filter { it.album == album.title }
                                if (albumSongs.isNotEmpty()) {
                                    viewModel.playSong(albumSongs.first(), albumSongs)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            2 -> {
                // Artists List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(artists) { artist ->
                        ArtistRow(
                            artist = artist,
                            onClick = {
                                val artistSongs = songs.filter { it.artist == artist.name }
                                if (artistSongs.isNotEmpty()) {
                                    viewModel.playSong(artistSongs.first(), artistSongs)
                                }
                            }
                        )
                    }
                }
            }

            3 -> {
                // Genres Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp, top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(genres) { genre ->
                        GenreCard(
                            genre = genre,
                            onClick = {
                                val genreSongs = songs.filter { it.genre == genre.name }
                                if (genreSongs.isNotEmpty()) {
                                    viewModel.playSong(genreSongs.first(), genreSongs)
                                }
                            }
                        )
                    }
                }
            }

            4 -> {
                // Folders List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(folders) { folder ->
                        FolderRow(
                            folder = folder,
                            onClick = {
                                val folderSongs = songs.filter { java.io.File(it.dataPath).parent == folder.path }
                                if (folderSongs.isNotEmpty()) {
                                    viewModel.playSong(folderSongs.first(), folderSongs)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Metadata Edit Dialog
    songToEdit?.let { s ->
        EditMetadataDialog(
            song = s,
            onDismiss = { songToEdit = null },
            onSave = { newTitle, newArtist, newAlbum, newGenre, lyrics ->
                viewModel.updateSongMetadata(s.id, newTitle, newArtist, newAlbum, newGenre, lyrics)
                songToEdit = null
            }
        )
    }

    // Add to Playlist Dialog
    songForPlaylistPicker?.let { s ->
        val playlists by viewModel.playlists.collectAsState()
        AddToPlaylistDialog(
            song = s,
            playlists = playlists,
            onDismiss = { songForPlaylistPicker = null },
            onSelectPlaylist = { playlistId ->
                viewModel.addSongToPlaylist(playlistId, s.id)
                songForPlaylistPicker = null
            },
            onCreateNewPlaylist = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }
}

@Composable
fun SongListItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditMetadata: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPlaying) SonoraSurfaceElevated else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag("song_item_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkAlbumView(
            song = song,
            modifier = Modifier.size(48.dp),
            cornerRadius = 10.dp,
            isPlaying = isPlaying
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isPlaying) SonoraGold else SonoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.album}",
                color = SonoraTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.audioFormatInfo,
                    color = SonoraCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = song.durationFormatted,
                    color = SonoraTextMuted,
                    fontSize = 10.sp
                )
            }
        }

        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) SonoraGold else SonoraTextMuted.copy(alpha = 0.35f),
                modifier = Modifier.size(20.dp)
            )
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = SonoraTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SonoraSurfaceDark)
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = SonoraTextPrimary) },
                    onClick = {
                        onPlayNext()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = SonoraTextPrimary) },
                    onClick = {
                        onAddToQueue()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = SonoraTextPrimary) },
                    onClick = {
                        onAddToPlaylist()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Edit Song Metadata", color = SonoraGold) },
                    onClick = {
                        onEditMetadata()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SonoraSurfaceElevated)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(SonoraSurfaceDark, CircleShape)
                .border(1.dp, SonoraGold.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = SonoraGold, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                color = SonoraTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${artist.songCount} tracks • ${artist.albumCount} albums",
                color = SonoraTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun GenreCard(
    genre: Genre,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = SonoraSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SonoraCyan.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = SonoraCyan, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = genre.name,
                color = SonoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${genre.songCount} tracks",
                color = SonoraTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun FolderRow(
    folder: MusicFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SonoraSurfaceElevated)
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = SonoraGold, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                color = SonoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${folder.songCount} tracks • ${folder.path}",
                color = SonoraTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EditMetadataDialog(
    song: Song,
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
        containerColor = SonoraSurfaceDark,
        title = {
            Text("Edit Song Metadata", color = SonoraTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Track Title") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonoraTextPrimary,
                        unfocusedTextColor = SonoraTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonoraTextPrimary,
                        unfocusedTextColor = SonoraTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonoraTextPrimary,
                        unfocusedTextColor = SonoraTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonoraTextPrimary,
                        unfocusedTextColor = SonoraTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = lyrics,
                    onValueChange = { lyrics = it },
                    label = { Text("Lyrics (Synced / Plain)") },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonoraTextPrimary,
                        unfocusedTextColor = SonoraTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, artist, album, genre, lyrics.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = SonoraGold, contentColor = SonoraOnGold)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SonoraTextSecondary)
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<com.example.model.Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Long) -> Unit,
    onCreateNewPlaylist: (String) -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SonoraSurfaceDark,
        title = {
            Text("Add to Playlist", color = SonoraTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                if (isCreatingNew) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Playlist Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SonoraTextPrimary,
                            unfocusedTextColor = SonoraTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    if (playlists.isEmpty()) {
                        Text("No playlists yet. Create your first playlist below:", color = SonoraTextSecondary)
                    } else {
                        LazyColumn(modifier = Modifier.height(180.dp)) {
                            items(playlists) { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectPlaylist(p.id) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SonoraGold)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(p.name, color = SonoraTextPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCreatingNew) {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            onCreateNewPlaylist(newPlaylistName.trim())
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SonoraGold, contentColor = SonoraOnGold)
                ) {
                    Text("Create & Add")
                }
            } else {
                TextButton(onClick = { isCreatingNew = true }) {
                    Text("+ New Playlist", color = SonoraGold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SonoraTextSecondary)
            }
        }
    )
}
