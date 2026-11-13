package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import kotlinx.coroutines.flow.flowOf
import ui.components.CircularArtwork
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

private data class SmartCollectionTarget(
    val title: String,
    val subtitle: String,
    val badgeTag: String,
    val songs: List<Song>
)

@Composable
fun PlaylistsScreen(
    viewModel: SonoraViewModel,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val playlists by viewModel.playlists.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<Playlist?>(null) }
    var selectedSmartTarget by remember { mutableStateOf<SmartCollectionTarget?>(null) }
    var selectedCustomPlaylistId by remember { mutableStateOf<Long?>(null) }
    var showAddSongsSheetForPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val selectedCustomPlaylist = remember(playlists, selectedCustomPlaylistId) {
        playlists.firstOrNull { it.id == selectedCustomPlaylistId }
    }

    val activePlaylistSongs by (selectedCustomPlaylist?.let {
        viewModel.getSongsForPlaylistFlow(it.id)
    } ?: flowOf(emptyList())).collectAsState(initial = emptyList())

    BackHandler(enabled = selectedCustomPlaylist != null || selectedSmartTarget != null) {
        selectedCustomPlaylistId = null
        selectedSmartTarget = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .let { if (onBack != null) it.statusBarsPadding() else it }
            .testTag("playlists_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            if (selectedCustomPlaylist != null || selectedSmartTarget != null) {
                val detailTitle = selectedCustomPlaylist?.name ?: selectedSmartTarget!!.title
                val detailSongs = if (selectedCustomPlaylist != null) activePlaylistSongs else selectedSmartTarget!!.songs
                val isCustom = selectedCustomPlaylist != null
                val totalDurationLabel = remember(detailSongs) { formatTotalDuration(detailSongs) }

                PlaylistDetailView(
                    title = detailTitle,
                    subtitle = "${detailSongs.size} tracks • $totalDurationLabel",
                    coverTag = selectedCustomPlaylist?.coverArtUri ?: selectedSmartTarget?.badgeTag,
                    songs = detailSongs,
                    allLibrarySongs = allSongs,
                    isCustom = isCustom,
                    currentSongId = currentSong?.id,
                    isPlaying = playbackInfo.isPlaying,
                    colors = colors,
                    onBack = {
                        selectedCustomPlaylistId = null
                        selectedSmartTarget = null
                    },
                    onPlayAll = {
                        if (detailSongs.isNotEmpty()) {
                            viewModel.playCollection(detailSongs, shuffle = false)
                        }
                    },
                    onShuffleAll = {
                        if (detailSongs.isNotEmpty()) {
                            viewModel.playCollection(detailSongs, shuffle = true)
                        }
                    },
                    onPlaySong = { song ->
                        viewModel.playSong(song, detailSongs)
                    },
                    onToggleFavorite = { song ->
                        viewModel.toggleFavorite(song)
                    },
                    onEditPlaylist = selectedCustomPlaylist?.let { pl -> { playlistToEdit = pl } },
                    onAddSongs = selectedCustomPlaylist?.let { pl -> { showAddSongsSheetForPlaylist = pl } },
                    onMoveUp = selectedCustomPlaylist?.let { pl ->
                        { idx ->
                            if (idx > 0 && idx in detailSongs.indices) {
                                val mutable = detailSongs.toMutableList()
                                val item = mutable.removeAt(idx)
                                mutable.add(idx - 1, item)
                                viewModel.reorderPlaylistSongs(pl.id, mutable.map { it.id })
                            }
                        }
                    },
                    onMoveDown = selectedCustomPlaylist?.let { pl ->
                        { idx ->
                            if (idx in 0 until detailSongs.lastIndex) {
                                val mutable = detailSongs.toMutableList()
                                val item = mutable.removeAt(idx)
                                mutable.add(idx + 1, item)
                                viewModel.reorderPlaylistSongs(pl.id, mutable.map { it.id })
                            }
                        }
                    },
                    onRemoveSong = selectedCustomPlaylist?.let { pl ->
                        { song ->
                            viewModel.removeSongFromPlaylist(pl.id, song.id)
                        }
                    }
                )
            } else {
                // Playlists Overview Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (onBack != null) {
                            Surface(
                                shape = CircleShape,
                                color = colors.surface,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable { onBack() }
                                    .testTag("playlists_back_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Back",
                                        tint = colors.textPrimary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                        }
                        Column {
                            Text(
                                text = "Playlists",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${playlists.size} custom • 4 smart mixes",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Button(
                        onClick = { showCreateDialog = true },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.onAccentStrong
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("create_playlist_fab")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp).let {
                        PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 148.dp)
                    },
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            text = "SMART MIXES",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.1.sp,
                            color = colors.textMuted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                        )
                    }

                    item {
                        SmartPlaylistEditorialCard(
                            title = "Favorite Tracks",
                            subtitle = "${favorites.size} tracks • ${formatTotalDuration(favorites)}",
                            icon = Icons.Rounded.Favorite,
                            colors = colors,
                            onClick = {
                                selectedSmartTarget = SmartCollectionTarget(
                                    title = "Favorite Tracks",
                                    subtitle = "Saved favourites",
                                    badgeTag = "badge:heart",
                                    songs = favorites
                                )
                            }
                        )
                    }

                    item {
                        SmartPlaylistEditorialCard(
                            title = "Most Played Hi-Fi",
                            subtitle = "${mostPlayed.size} tracks • ${formatTotalDuration(mostPlayed)}",
                            icon = Icons.Rounded.LocalFireDepartment,
                            colors = colors,
                            onClick = {
                                selectedSmartTarget = SmartCollectionTarget(
                                    title = "Most Played Hi-Fi",
                                    subtitle = "Top rotation",
                                    badgeTag = "badge:spark",
                                    songs = mostPlayed
                                )
                            }
                        )
                    }

                    item {
                        SmartPlaylistEditorialCard(
                            title = "Recently Played",
                            subtitle = "${recentlyPlayed.size} tracks • ${formatTotalDuration(recentlyPlayed)}",
                            icon = Icons.Rounded.History,
                            colors = colors,
                            onClick = {
                                selectedSmartTarget = SmartCollectionTarget(
                                    title = "Recently Played",
                                    subtitle = "Listening history",
                                    badgeTag = "badge:wave",
                                    songs = recentlyPlayed
                                )
                            }
                        )
                    }

                    item {
                        SmartPlaylistEditorialCard(
                            title = "All Offline Audio",
                            subtitle = "${allSongs.size} tracks • ${formatTotalDuration(allSongs)}",
                            icon = Icons.Rounded.LibraryMusic,
                            colors = colors,
                            onClick = {
                                selectedSmartTarget = SmartCollectionTarget(
                                    title = "All Offline Audio",
                                    subtitle = "Full collection",
                                    badgeTag = "badge:vinyl",
                                    songs = allSongs
                                )
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "YOUR PLAYLISTS",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.1.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = "${playlists.size} created",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                color = colors.textMuted
                            )
                        }
                    }

                    if (playlists.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = colors.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .background(colors.accentPill, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = colors.onAccentPill,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No custom playlists yet",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap + New above to curate your own local mix.",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 13.sp,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(playlists, key = { _, p -> p.id }) { _, playlist ->
                            UserPlaylistEditorialCard(
                                playlist = playlist,
                                allSongs = allSongs,
                                colors = colors,
                                onClick = { selectedCustomPlaylistId = playlist.id },
                                onEdit = { playlistToEdit = playlist },
                                onAddSongs = { showAddSongsSheetForPlaylist = playlist },
                                onDelete = { viewModel.deletePlaylist(playlist.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "New Playlist",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentStrong,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            viewModel.createPlaylist(playlistName.trim())
                            showCreateDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    playlistToEdit?.let { target ->
        EditPlaylistDialog(
            playlist = target,
            colors = colors,
            onDismiss = { playlistToEdit = null },
            onSave = { newName, newCoverTag ->
                viewModel.updatePlaylistDetails(target.id, newName, newCoverTag)
                playlistToEdit = null
            }
        )
    }

    showAddSongsSheetForPlaylist?.let { target ->
        val existingSongs by viewModel.getSongsForPlaylistFlow(target.id).collectAsState(initial = emptyList())
        AddSongsMultiPickerSheet(
            playlistName = target.name,
            allSongs = allSongs,
            existingSongIds = existingSongs.map { it.id }.toSet(),
            colors = colors,
            onDismiss = { showAddSongsSheetForPlaylist = null },
            onConfirmAdd = { chosenIds ->
                viewModel.addSongsToPlaylist(target.id, chosenIds)
                showAddSongsSheetForPlaylist = null
            }
        )
    }
}

@Composable
private fun PlaylistDetailView(
    title: String,
    subtitle: String,
    coverTag: String?,
    songs: List<Song>,
    allLibrarySongs: List<Song>,
    isCustom: Boolean,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onEditPlaylist: (() -> Unit)?,
    onAddSongs: (() -> Unit)?,
    onMoveUp: ((Int) -> Unit)?,
    onMoveDown: ((Int) -> Unit)?,
    onRemoveSong: ((Song) -> Unit)?
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Detail Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = colors.surface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .testTag("playlist_detail_back")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to Playlists",
                        tint = colors.textPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            PlaylistCoverAvatar(
                coverTag = coverTag,
                fallbackSong = songs.firstOrNull() ?: allLibrarySongs.firstOrNull(),
                colors = colors,
                sizeDp = 50
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
            if (isCustom && onEditPlaylist != null) {
                IconButton(
                    onClick = onEditPlaylist,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit playlist details",
                        tint = colors.accentStrong
                    )
                }
            }
        }

        // Play / Shuffle / Add Songs controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onPlayAll,
                enabled = songs.isNotEmpty(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.onAccentStrong
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play All", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onShuffleAll,
                enabled = songs.isNotEmpty(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPill,
                    contentColor = colors.onAccentPill
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Shuffle", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
            }

            if (isCustom && onAddSongs != null) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceTinted,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onAddSongs() }
                        .testTag("playlist_add_songs_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = "Add songs to playlist",
                            tint = colors.accentStrong
                        )
                    }
                }
            }
        }

        if (songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "This playlist is empty",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isCustom) "Tap the + Playlist icon above to pick tracks from your library." else "Tracks matching this smart mix will appear automatically.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    if (isCustom && onAddSongs != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onAddSongs,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            )
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Songs")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 148.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(songs, key = { idx, s -> "${s.id}_$idx" }) { idx, song ->
                    val isCurrent = currentSongId == song.id
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isCurrent) colors.accentPill.copy(alpha = 0.42f) else colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                0.75.dp,
                                if (isCurrent) colors.accentStrong.copy(alpha = 0.45f) else colors.cardBorder,
                                RoundedCornerShape(18.dp)
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onPlaySong(song) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularArtwork(
                                song = song,
                                isPlaying = isCurrent && isPlaying,
                                accentColor = colors.accentPill,
                                shadowElevation = 2.dp,
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

                            if (isCustom && onMoveUp != null && onMoveDown != null && onRemoveSong != null) {
                                IconButton(
                                    onClick = { onMoveUp(idx) },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = "Move up",
                                        tint = if (idx > 0) colors.textPrimary else colors.textMuted.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onMoveDown(idx) },
                                    enabled = idx < songs.lastIndex,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDownward,
                                        contentDescription = "Move down",
                                        tint = if (idx < songs.lastIndex) colors.textPrimary else colors.textMuted.copy(alpha = 0.35f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveSong(song) },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RemoveCircleOutline,
                                        contentDescription = "Remove from playlist",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { onToggleFavorite(song) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Toggle favorite",
                                        tint = if (song.isFavorite) colors.accentStrong else colors.textMuted
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
private fun SmartPlaylistEditorialCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: EditorialColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
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
                    .size(48.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.onAccentPill, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
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
    }
}

@Composable
private fun UserPlaylistEditorialCard(
    playlist: Playlist,
    allSongs: List<Song>,
    colors: EditorialColors,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onAddSongs: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
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
            PlaylistCoverAvatar(
                coverTag = playlist.coverArtUri,
                fallbackSong = allSongs.firstOrNull(),
                colors = colors,
                sizeDp = 50
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val countLabel = if (playlist.songCount == 1) "1 track" else "${playlist.songCount} tracks"
                Text(
                    text = "$countLabel • Custom playlist",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist options", tint = colors.textSecondary)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(colors.surface)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add Songs", color = colors.textPrimary) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null, tint = colors.accentStrong) },
                        onClick = {
                            onAddSongs()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit & Customize Cover", color = colors.textPrimary) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = colors.accentStrong) },
                        onClick = {
                            onEdit()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFD9534F)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFD9534F)) },
                        onClick = {
                            onDelete()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverAvatar(
    coverTag: String?,
    fallbackSong: Song?,
    colors: EditorialColors,
    sizeDp: Int
) {
    val badgeIcon: ImageVector? = when (coverTag) {
        "badge:heart" -> Icons.Rounded.Favorite
        "badge:spark" -> Icons.Rounded.AutoAwesome
        "badge:wave" -> Icons.Rounded.GraphicEq
        "badge:vinyl" -> Icons.Rounded.Album
        "badge:queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> null
    }

    if (badgeIcon != null || fallbackSong == null) {
        Box(
            modifier = Modifier
                .size(sizeDp.dp)
                .background(colors.accentPill, CircleShape)
                .border(1.dp, colors.cardBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = badgeIcon ?: Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                tint = colors.onAccentPill,
                modifier = Modifier.size((sizeDp * 0.48f).dp)
            )
        }
    } else {
        CircularArtwork(
            song = fallbackSong,
            accentColor = colors.accentPill,
            shadowElevation = 3.dp,
            modifier = Modifier.size(sizeDp.dp)
        )
    }
}

@Composable
private fun EditPlaylistDialog(
    playlist: Playlist,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onSave: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf(playlist.name) }
    var selectedBadge by remember { mutableStateOf(playlist.coverArtUri ?: "auto") }

    val badgeOptions = listOf(
        "auto" to ("Artwork" to Icons.Rounded.Album),
        "badge:vinyl" to ("Vinyl" to Icons.Rounded.Album),
        "badge:wave" to ("Wave" to Icons.Rounded.GraphicEq),
        "badge:spark" to ("Spark" to Icons.Rounded.AutoAwesome),
        "badge:heart" to ("Heart" to Icons.Rounded.Favorite),
        "badge:queue" to ("Mix" to Icons.AutoMirrored.Rounded.QueueMusic)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = "Edit Playlist",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentStrong,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Cover Badge",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    badgeOptions.forEach { (tag, meta) ->
                        val isSelected = selectedBadge == tag
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedBadge = tag }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isSelected) colors.accentStrong else colors.surfaceTinted,
                                        CircleShape
                                    )
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) colors.accentStrong else colors.cardBorder,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = meta.second,
                                    contentDescription = meta.first,
                                    tint = if (isSelected) colors.onAccentStrong else colors.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = meta.first,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 10.sp,
                                color = if (isSelected) colors.accentStrong else colors.textSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        val coverToSave = if (selectedBadge == "auto") null else selectedBadge
                        onSave(trimmed, coverToSave)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.onAccentStrong
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSongsMultiPickerSheet(
    playlistName: String,
    allSongs: List<Song>,
    existingSongIds: Set<Long>,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onConfirmAdd: (List<Long>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterText by remember { mutableStateOf("") }
    var chosenIds by remember { mutableStateOf(setOf<Long>()) }

    val availableSongs = remember(allSongs, filterText) {
        if (filterText.isBlank()) allSongs else {
            allSongs.filter {
                it.title.contains(filterText, ignoreCase = true) ||
                    it.artist.contains(filterText, ignoreCase = true) ||
                    it.album.contains(filterText, ignoreCase = true)
            }
        }
    }

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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add to $playlistName",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${chosenIds.size} selected",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }

                Button(
                    onClick = { onConfirmAdd(chosenIds.toList()) },
                    enabled = chosenIds.isNotEmpty(),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add (${chosenIds.size})")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("Search tracks...", color = colors.textMuted) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.accentStrong) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentStrong,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.cardBorder)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                itemsIndexed(availableSongs, key = { _, s -> s.id }) { _, song ->
                    val alreadyIn = song.id in existingSongIds
                    val isChecked = song.id in chosenIds || alreadyIn
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = !alreadyIn) {
                                chosenIds = if (song.id in chosenIds) chosenIds - song.id else chosenIds + song.id
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            enabled = !alreadyIn,
                            onCheckedChange = { checked ->
                                if (!alreadyIn) {
                                    chosenIds = if (checked) chosenIds + song.id else chosenIds - song.id
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = colors.accentStrong)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        CircularArtwork(
                            song = song,
                            accentColor = colors.accentPill,
                            shadowElevation = 1.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (alreadyIn) colors.textMuted else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (alreadyIn) "${song.artist} • Already in playlist" else "${song.artist} • ${song.durationFormatted}",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
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

private fun formatTotalDuration(songs: List<Song>): String {
    val totalSec = (songs.sumOf { it.durationMs } / 1000L).coerceAtLeast(0L)
    val hours = totalSec / 3600L
    val mins = (totalSec % 3600L) / 60L
    val secs = totalSec % 60L
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}
