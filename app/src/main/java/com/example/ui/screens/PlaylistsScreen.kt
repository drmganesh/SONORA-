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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraGoldDark
import com.example.ui.theme.SonoraGoldLight
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraOnGold
import com.example.ui.theme.SonoraSurfaceDark
import com.example.ui.theme.SonoraSurfaceElevated
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import com.example.viewmodel.SonoraViewModel

@Composable
fun PlaylistsScreen(
    viewModel: SonoraViewModel,
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayedSongs.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var selectedPlaylistSongs by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlists_screen")
    ) {
        if (selectedPlaylistSongs != null) {
            // Detailed Playlist View
            val (name, songs) = selectedPlaylistSongs!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { selectedPlaylistSongs = null }) {
                        Text("← Back", color = SonoraGold, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        color = SonoraTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Batch Play / Shuffle buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (songs.isNotEmpty()) viewModel.playSong(songs.first(), songs)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SonoraGold, contentColor = SonoraOnGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All")
                    }

                    Button(
                        onClick = {
                            if (songs.isNotEmpty()) {
                                val shuffled = songs.shuffled()
                                viewModel.playSong(shuffled.first(), shuffled)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SonoraSurfaceElevated, contentColor = SonoraCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }

                if (songs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs in this playlist yet", color = SonoraTextMuted)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(songs) { song ->
                            RowSongItem(
                                song = song,
                                isPlaying = viewModel.playbackInfo.collectAsState().value.isPlaying && viewModel.currentSong.collectAsState().value?.id == song.id,
                                onClick = { viewModel.playSong(song, songs) },
                                onFavoriteToggle = { viewModel.toggleFavorite(song) }
                            )
                        }
                    }
                }
            }
        } else {
            // Main Playlists Overview
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, top = 16.dp)
            ) {
                item {
                    Text(
                        text = "Smart Playlists",
                        color = SonoraTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }

                // Smart Playlists List
                item {
                    SmartPlaylistItem(
                        title = "Favorite Tracks",
                        subtitle = "${favorites.size} songs saved",
                        icon = Icons.Default.Favorite,
                        iconTint = SonoraGold,
                        onClick = { selectedPlaylistSongs = "Favorites" to favorites }
                    )
                }

                item {
                    SmartPlaylistItem(
                        title = "Most Played Hi-Fi",
                        subtitle = "${mostPlayed.size} frequently played",
                        icon = Icons.Default.LocalFireDepartment,
                        iconTint = SonoraCyan,
                        onClick = { selectedPlaylistSongs = "Most Played" to mostPlayed }
                    )
                }

                item {
                    SmartPlaylistItem(
                        title = "Recently Played History",
                        subtitle = "${recentlyPlayed.size} tracks recently listened",
                        icon = Icons.Default.History,
                        iconTint = SonoraGoldLight,
                        onClick = { selectedPlaylistSongs = "Recently Played" to recentlyPlayed }
                    )
                }

                item {
                    SmartPlaylistItem(
                        title = "All Audio Collection",
                        subtitle = "${allSongs.size} tracks available offline",
                        icon = Icons.Default.QueueMusic,
                        iconTint = SonoraCyan,
                        onClick = { selectedPlaylistSongs = "Complete Collection" to allSongs }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Custom Playlists",
                            color = SonoraTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${playlists.size} created",
                            color = SonoraTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                if (playlists.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No custom playlists yet.\nTap '+' below to create your first playlist!",
                                color = SonoraTextMuted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    items(playlists) { playlist ->
                        UserPlaylistItem(
                            playlist = playlist,
                            onClick = {
                                // For custom playlist, we can open all songs or filtered songs
                                selectedPlaylistSongs = playlist.name to allSongs.take(5)
                            },
                            onRename = { playlistToRename = playlist },
                            onDelete = { viewModel.deletePlaylist(playlist.id) }
                        )
                    }
                }
            }

            // Create Playlist Floating Action Button
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = SonoraGold,
                contentColor = SonoraOnGold,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 130.dp)
                    .testTag("create_playlist_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Playlist")
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SonoraSurfaceDark,
            title = { Text("New Playlist", color = SonoraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonoraTextPrimary,
                        unfocusedTextColor = SonoraTextPrimary
                    ),
                    singleLine = true,
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
                    colors = ButtonDefaults.buttonColors(containerColor = SonoraGold, contentColor = SonoraOnGold)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SonoraTextSecondary)
                }
            }
        )
    }

    // Rename Playlist Dialog
    playlistToRename?.let { p ->
        var newName by remember { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            containerColor = SonoraSurfaceDark,
            title = { Text("Rename Playlist", color = SonoraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = SonoraTextPrimary,
                        unfocusedTextColor = SonoraTextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            viewModel.renamePlaylist(p.id, newName.trim())
                            playlistToRename = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SonoraGold, contentColor = SonoraOnGold)
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("Cancel", color = SonoraTextSecondary)
                }
            }
        )
    }
}

@Composable
fun SmartPlaylistItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = SonoraSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = SonoraTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = SonoraTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun UserPlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        color = SonoraSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        Brush.linearGradient(listOf(SonoraSurfaceDark, SonoraObsidian)),
                        RoundedCornerShape(12.dp)
                    )
                    .border(1.dp, SonoraCardBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = SonoraGold, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, color = SonoraTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Custom Local Playlist", color = SonoraTextSecondary, fontSize = 12.sp)
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = SonoraTextSecondary)
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SonoraSurfaceDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = SonoraTextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = SonoraGold) },
                        onClick = {
                            onRename()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = Color(0xFFFF5252)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252)) },
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
