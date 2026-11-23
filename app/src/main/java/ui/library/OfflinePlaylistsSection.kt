package ui.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.CircularArtwork
import ui.theme.EditorialColors

@Composable
fun OfflinePlaylistsSection(
    playlists: List<Playlist>,
    allLocalSongs: List<Song>,
    playlistSongResolver: (Long) -> List<Song>,
    colors: EditorialColors,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onOpenPlaylist: (Playlist, List<Song>) -> Unit,
    onPlayPlaylist: (List<Song>) -> Unit,
    onOpenFullPlaylistsManager: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("offline_playlists_section"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 152.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "create_local_playlist_card") {
            Surface(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(20.dp),
                color = colors.accentPill.copy(alpha = 0.55f),
                border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_create_playlist_button")
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
                            text = "Create Local Playlist",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Organize your device music and offline downloads",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    if (onOpenFullPlaylistsManager != null) {
                        TextButton(onClick = onOpenFullPlaylistsManager) {
                            Text("Manage", color = colors.accentStrong, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (playlists.isEmpty()) {
            item(key = "empty_offline_playlists") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = colors.accentStrong.copy(alpha = 0.65f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No Local Playlists Yet",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Create a playlist above or add songs via the track menu.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.5.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                val resolvedSongs = remember(playlist.id, allLocalSongs, playlist.songCount) {
                    playlistSongResolver(playlist.id)
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surface,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenPlaylist(playlist, resolvedSongs) }
                        .testTag("offline_playlist_row_${playlist.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularArtwork(
                            song = resolvedSongs.firstOrNull(),
                            fallbackLabel = playlist.name,
                            accentColor = colors.accentPill,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(50.dp)
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
                                text = "${resolvedSongs.size.takeIf { it > 0 } ?: playlist.songCount} tracks",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        if (resolvedSongs.isNotEmpty()) {
                            IconButton(
                                onClick = { onPlayPlaylist(resolvedSongs) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = "Play ${playlist.name}",
                                    tint = colors.accentStrong
                                )
                            }
                        }
                        IconButton(
                            onClick = { playlistPendingDelete = playlist },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete ${playlist.name}",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = colors.surface,
            title = {
                Text("New Local Playlist", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onCreatePlaylist(name.trim())
                            showCreateDialog = false
                        }
                    },
                    enabled = name.isNotBlank(),
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

    playlistPendingDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { playlistPendingDelete = null },
            containerColor = colors.surface,
            title = {
                Text("Delete Playlist?", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            },
            text = {
                Text(
                    "Remove playlist \"${pl.name}\"? Your audio files on the device will not be deleted.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlaylist(pl)
                        playlistPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistPendingDelete = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}
