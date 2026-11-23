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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.library.OnlineLibrarySortOption
import domain.library.OnlinePlaylist
import domain.online.OnlineItemKind
import ui.online.OnlineTrackThumbnail
import ui.theme.EditorialColors

@Composable
fun CreateOnlinePlaylistDialog(
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = "New Online Playlist",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Organize tracks from YouTube Music, YouTube, SoundCloud, and Bandcamp in your Online Library.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.5.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_online_playlist_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim())
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.onAccentStrong
                ),
                modifier = Modifier.testTag("confirm_create_online_playlist_button")
            ) {
                Text("Create", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
fun RenameOnlinePlaylistDialog(
    currentName: String,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var name by remember(currentName) { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = "Rename Online Playlist",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
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
                        onRename(name.trim())
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.onAccentStrong
                )
            ) {
                Text("Save", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        }
    )
}

@Composable
fun OnlinePlaylistPickerDialog(
    song: Song,
    playlists: List<OnlinePlaylist>,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onSelectPlaylist: (OnlinePlaylist) -> Unit,
    onCreatePlaylistWithSong: (String) -> Unit
) {
    var creatingNew by remember { mutableStateOf(playlists.isEmpty()) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = "Add to Online Playlist",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "${song.title} • ${song.providerName.ifBlank { "Online" }}",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.5.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (creatingNew) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New playlist name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("online_playlist_picker_new_name")
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onSelectPlaylist(playlist)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = colors.accentStrong
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.trackCount} tracks",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (creatingNew) {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylistWithSong(newName.trim())
                            onDismiss()
                        }
                    },
                    enabled = newName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Text("Create & Add")
                }
            } else {
                TextButton(onClick = { creatingNew = true }) {
                    Text("+ New Online Playlist", color = colors.accentStrong)
                }
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
fun OnlinePlaylistDetailSheet(
    playlist: OnlinePlaylist,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    onRename: () -> Unit,
    onDeletePlaylist: () -> Unit
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
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
                .testTag("online_playlist_detail_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnlineTrackThumbnail(
                    artworkUrl = playlist.displayArtworkUrl,
                    title = playlist.name,
                    kind = OnlineItemKind.PLAYLIST,
                    colors = colors,
                    size = 64.dp,
                    cornerRadius = 16.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildString {
                        append("${playlist.trackCount} online tracks")
                        if (playlist.providerName.isNotBlank()) {
                            append(" • ${playlist.providerName}")
                        }
                    }
                    Text(
                        text = subtitle,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.5.sp,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onRename, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename playlist", tint = colors.textPrimary)
                }
                IconButton(onClick = onDeletePlaylist, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete playlist", tint = colors.textSecondary)
                }
            }

            if (playlist.tracks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onPlayAll(false)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.onAccentStrong
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play All", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                    }
                    Surface(
                        onClick = {
                            onPlayAll(true)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = colors.accentPill,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = colors.onAccentPill, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold, color = colors.onAccentPill)
                        }
                    }
                }

                HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(horizontal = 20.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    itemsIndexed(playlist.tracks, key = { idx, s -> "${s.id}_$idx" }) { _, track ->
                        val isCurrent = currentSongId == track.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isCurrent) colors.accentPill.copy(alpha = 0.45f) else Color.Transparent)
                                .clickable { onPlaySong(track) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OnlineTrackThumbnail(
                                artworkUrl = track.artworkUrl,
                                title = track.title,
                                kind = OnlineItemKind.TRACK,
                                colors = colors,
                                size = 44.dp,
                                cornerRadius = 11.dp,
                                isPlaying = isCurrent && isPlaying
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.providerName.ifBlank { "Online" }} • ${track.durationFormatted}",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { onRemoveSong(track) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Remove from playlist",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { onSongMore(track) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More options",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "This online playlist is empty. Add songs from Online Search, Discover, or Now Playing.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineLibrarySortSheet(
    selectedSort: OnlineLibrarySortOption,
    colors: EditorialColors,
    onSelect: (OnlineLibrarySortOption) -> Unit,
    onDismiss: () -> Unit
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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sort Online Library",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.textPrimary
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textSecondary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OnlineLibrarySortOption.entries.forEach { option ->
                val active = option == selectedSort
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (active) colors.accentPill.copy(alpha = 0.55f) else Color.Transparent)
                        .clickable {
                            onSelect(option)
                            onDismiss()
                        }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = option.label,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    if (active) {
                        Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = colors.accentStrong)
                    }
                }
            }
        }
    }
}
