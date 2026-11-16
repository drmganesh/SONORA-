package ui.online

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.OnlineCollectionUiState
import data.online.OnlineTrackMapper
import domain.online.OnlineItemKind
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import ui.theme.EditorialColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineTrackOptionsSheet(
    track: OnlineTrack,
    isSaved: Boolean,
    isFavorite: Boolean,
    resolvedStream: ResolvedOnlineStreamResult?,
    colors: EditorialColors,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleSave: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenQualitySettings: () -> Unit,
    onViewArtistChannel: (() -> Unit)?,
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("online_track_options_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnlineTrackThumbnail(
                    artworkUrl = track.artworkUrl,
                    title = track.title,
                    kind = track.kind,
                    colors = colors,
                    size = 56.dp,
                    cornerRadius = 14.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${track.artist} • ${track.providerName}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val activeStreamMatch = resolvedStream?.takeIf { it.contentUrl == track.contentUrl }
                    val metaLine = activeStreamMatch?.selectedStream?.qualityLabel
                        ?: if (track.durationMs > 0L || track.isLive) "Duration ${track.durationFormatted}" else track.kind.name
                    Text(
                        text = metaLine,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = colors.accentStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.cardBorder)
            Spacer(modifier = Modifier.height(6.dp))

            if (track.kind == OnlineItemKind.TRACK) {
                OnlineSheetActionRow(
                    icon = Icons.Rounded.PlayArrow,
                    title = "Play Now",
                    colors = colors,
                    onClick = {
                        onPlayNow()
                        onDismiss()
                    }
                )
                OnlineSheetActionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = "Play Next in Queue",
                    colors = colors,
                    onClick = {
                        onPlayNext()
                        onDismiss()
                    }
                )
                OnlineSheetActionRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = "Add to Queue",
                    colors = colors,
                    onClick = {
                        onAddToQueue()
                        onDismiss()
                    }
                )
                OnlineSheetActionRow(
                    icon = if (isSaved) Icons.Rounded.BookmarkAdded else Icons.Rounded.BookmarkAdd,
                    title = if (isSaved) "Saved in Online Library" else "Save to Online Library",
                    colors = colors,
                    highlighted = isSaved,
                    onClick = {
                        onToggleSave()
                        onDismiss()
                    }
                )
                OnlineSheetActionRow(
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    title = if (isFavorite) "Remove from Favourites" else "Add to Favourites",
                    colors = colors,
                    highlighted = isFavorite,
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    }
                )
                OnlineSheetActionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    title = "Add to Playlist",
                    colors = colors,
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    }
                )
            } else {
                OnlineSheetActionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    title = "Open ${track.kind.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    colors = colors,
                    onClick = {
                        onPlayNow()
                        onDismiss()
                    }
                )
            }

            if (onViewArtistChannel != null && !track.artistUrl.isNullOrBlank()) {
                OnlineSheetActionRow(
                    icon = Icons.Rounded.PersonSearch,
                    title = "Explore ${track.artist}",
                    colors = colors,
                    onClick = {
                        onDismiss()
                        onViewArtistChannel()
                    }
                )
            }

            OnlineSheetActionRow(
                icon = Icons.Rounded.HighQuality,
                title = "Stream Quality Settings",
                colors = colors,
                onClick = {
                    onDismiss()
                    onOpenQualitySettings()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun OnlineSheetActionRow(
    icon: ImageVector,
    title: String,
    colors: EditorialColors,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (highlighted) colors.accentStrong else colors.textPrimary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            color = if (highlighted) colors.accentStrong else colors.textPrimary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineCollectionDetailsSheet(
    state: OnlineCollectionUiState,
    currentSongId: Long?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    savedIds: Set<Long>,
    colors: EditorialColors,
    onPlayTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onPlayAll: (List<OnlineTrack>, Boolean) -> Unit,
    onRetry: (OnlineTrack) -> Unit,
    onTrackMoreClick: (OnlineTrack) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is OnlineCollectionUiState.Idle) return
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
                .padding(bottom = 16.dp)
                .testTag("online_collection_details_sheet")
        ) {
            when (state) {
                is OnlineCollectionUiState.Idle -> Unit
                is OnlineCollectionUiState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = colors.accentStrong,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.title,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Fetching tracks via NewPipe Extractor...",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(100.dp))
                }

                is OnlineCollectionUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Unable to Open Collection",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onRetry(state.item) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            )
                        ) {
                            Text("Retry", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is OnlineCollectionUiState.Success -> {
                    val details = state.details
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OnlineTrackThumbnail(
                            artworkUrl = details.artworkUrl,
                            title = details.title,
                            kind = OnlineItemKind.PLAYLIST,
                            colors = colors,
                            size = 64.dp,
                            cornerRadius = 16.dp
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = details.title,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${details.subtitle} • ${details.providerName}",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textPrimary)
                        }
                    }

                    if (details.tracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    onPlayAll(details.tracks, false)
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
                                Text("Play All (${details.tracks.size})", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                            }
                            Surface(
                                onClick = {
                                    onPlayAll(details.tracks, true)
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

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 380.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            items(details.tracks, key = { "col_${it.stableId}_${it.contentUrl}" }) { track ->
                                val isCurrent = currentSongId == track.stableId
                                OnlineResultRow(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isPlaying = isPlaying,
                                    isBuffering = isBuffering,
                                    isSaved = track.stableId in savedIds,
                                    colors = colors,
                                    onClick = { onPlayTrack(track, details.tracks) },
                                    onMoreClick = { onTrackMoreClick(track) }
                                )
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
                                text = "No playable audio tracks found in this collection.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

fun buildArtistExploreTrack(track: OnlineTrack): OnlineTrack? {
    val url = track.artistUrl?.takeIf { it.isNotBlank() } ?: return null
    return OnlineTrack(
        stableId = OnlineTrackMapper.computeStableId(track.providerKey, url),
        serviceId = track.serviceId,
        providerKey = track.providerKey,
        providerName = track.providerName,
        contentUrl = url,
        title = track.artist,
        artist = track.artist,
        artistUrl = url,
        kind = OnlineItemKind.ARTIST
    )
}
