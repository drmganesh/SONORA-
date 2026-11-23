package ui.downloads

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.download.DownloadSortOption
import domain.download.DownloadStorageSummary
import domain.download.DownloadedTrack
import domain.download.formatByteCount
import ui.components.CircularArtwork
import ui.theme.EditorialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DownloadedTrackDisplayItem(
    val track: DownloadedTrack,
    val song: Song,
    val isMissingFile: Boolean
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryDownloadsSection(
    items: List<DownloadedTrackDisplayItem>,
    sortOption: DownloadSortOption,
    storageSummary: DownloadStorageSummary,
    activeDownloadCount: Int,
    isMultiSelectMode: Boolean,
    selectedIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onSelectSort: (DownloadSortOption) -> Unit,
    onOpenDownloadManager: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onTrackClick: (DownloadedTrackDisplayItem) -> Unit,
    onTrackLongClick: (DownloadedTrackDisplayItem) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onRequestDelete: (DownloadedTrackDisplayItem) -> Unit,
    onOpenMore: (Song) -> Unit,
    downloadedVideos: List<DownloadedTrack> = emptyList(),
    onPlayDownloadedVideo: ((DownloadedTrack) -> Unit)? = null,
    onDeleteDownloadedVideo: ((DownloadedTrack) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    var showVideosSubTab by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_downloads_section")
    ) {
        // Storage & Download Manager banner strip
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surfaceTinted,
            border = BorderStroke(0.75.dp, colors.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val countLabel = if (storageSummary.trackCount == 1) {
                        "1 offline track"
                    } else {
                        "${storageSummary.trackCount} offline tracks"
                    }
                    val sizeText = formatByteCount(storageSummary.totalBytes)
                    Text(
                        text = if (sizeText.isNotEmpty()) "$countLabel • $sizeText" else countLabel,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Folder: ${storageSummary.folderLabel}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (activeDownloadCount > 0) colors.accentStrong else colors.accentPill,
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenDownloadManager() }
                        .testTag("library_open_download_manager")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (activeDownloadCount > 0) Icons.Rounded.Downloading else Icons.Rounded.DownloadDone,
                            contentDescription = "Open Download Manager",
                            tint = if (activeDownloadCount > 0) colors.onAccentStrong else colors.onAccentPill,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (activeDownloadCount > 0) "Queue ($activeDownloadCount)" else "Downloads",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (activeDownloadCount > 0) colors.onAccentStrong else colors.onAccentPill
                        )
                    }
                }
            }
        }

        // Offline Library -> Downloads -> Music | Videos Sub-Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("library_downloads_media_tabs"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                onClick = { showVideosSubTab = false },
                shape = RoundedCornerShape(16.dp),
                color = if (!showVideosSubTab) colors.accentStrong else colors.surface,
                border = BorderStroke(0.75.dp, if (!showVideosSubTab) colors.accentStrong else colors.cardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("downloads_subtab_music")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = if (!showVideosSubTab) colors.onAccentStrong else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Music (${items.size})",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = if (!showVideosSubTab) colors.onAccentStrong else colors.textSecondary
                    )
                }
            }
            Surface(
                onClick = { showVideosSubTab = true },
                shape = RoundedCornerShape(16.dp),
                color = if (showVideosSubTab) colors.accentStrong else colors.surface,
                border = BorderStroke(0.75.dp, if (showVideosSubTab) colors.accentStrong else colors.cardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("downloads_subtab_videos")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = if (showVideosSubTab) colors.onAccentStrong else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Videos (${downloadedVideos.size})",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = if (showVideosSubTab) colors.onAccentStrong else colors.textSecondary
                    )
                }
            }
        }

        if (showVideosSubTab) {
            if (downloadedVideos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .background(colors.accentPill, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VideoLibrary,
                                contentDescription = null,
                                tint = colors.onAccentPill,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "No Downloaded Videos Yet",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Download permitted online videos to watch them offline in SONORA.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.5.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("downloaded_videos_list"),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 152.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(downloadedVideos, key = { "dl_vid_${it.id}" }) { vid ->
                        val sizeLabel = formatByteCount(vid.fileSizeBytes)
                        val formattedDate = remember(vid.downloadedAt) {
                            dateFormatter.format(Date(vid.downloadedAt))
                        }
                        Surface(
                            onClick = { onPlayDownloadedVideo?.invoke(vid) },
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surface,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("downloaded_video_card_${vid.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ui.online.OnlineTrackThumbnail(
                                    artworkUrl = vid.localArtworkPath ?: vid.artworkUrl,
                                    title = vid.title,
                                    colors = colors,
                                    size = 56.dp,
                                    cornerRadius = 12.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = vid.title,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.5.sp,
                                        color = colors.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            vid.artist.takeIf { it.isNotBlank() },
                                            vid.resolutionLabel?.takeIf { it.isNotBlank() } ?: vid.formatLabel,
                                            sizeLabel.takeIf { it.isNotBlank() }
                                        ).joinToString(" • "),
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Saved $formattedDate",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 10.5.sp,
                                        color = colors.textMuted
                                    )
                                }
                                if (onDeleteDownloadedVideo != null) {
                                    IconButton(
                                        onClick = { onDeleteDownloadedVideo(vid) },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .testTag("delete_downloaded_video_${vid.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete downloaded video",
                                            tint = colors.textSecondary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return@Column
        }

        // Dedicated Downloads Sort Chips (Music)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DownloadSortOption.entries.forEach { option ->
                val selected = option == sortOption
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) colors.accentStrong else colors.surface,
                    border = BorderStroke(
                        0.75.dp,
                        if (selected) colors.accentStrong else colors.cardBorder
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectSort(option) }
                        .testTag("download_sort_${option.name.lowercase()}")
                ) {
                    Text(
                        text = option.label,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = if (selected) colors.onAccentStrong else colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .background(colors.accentPill, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DownloadDone,
                            contentDescription = null,
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Downloaded Music Yet",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap Download on any online track to save it for 100% offline playback.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.5.sp,
                        color = colors.textSecondary
                    )
                }
            }
            return@Column
        }

        // Play All & Shuffle All bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${items.size} tracks",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = colors.textMuted
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentStrong,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPlayAll() }
                        .testTag("downloads_play_all_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play All Downloads",
                            tint = colors.onAccentStrong,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Play All",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.onAccentStrong
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentPill,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onShuffleAll() }
                        .testTag("downloads_shuffle_all_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle Downloads",
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Shuffle All",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.onAccentPill
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("downloads_list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 152.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.track.id }) { item ->
                val track = item.track
                val song = item.song
                val isCurrent = currentSongId == song.id
                val selected = song.id in selectedIds
                val formattedDate = remember(track.downloadedAt) {
                    dateFormatter.format(Date(track.downloadedAt))
                }
                val sizeLabel = formatByteCount(track.fileSizeBytes)
                val formatDetail = buildString {
                    append(track.formatLabel)
                    if (track.bitrateKbps > 0) append(" • ${track.bitrateKbps} kbps")
                    if (sizeLabel.isNotEmpty()) append(" • $sizeLabel")
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = when {
                        selected -> colors.accentPill.copy(alpha = 0.52f)
                        isCurrent -> colors.accentPill.copy(alpha = 0.36f)
                        else -> colors.surface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            0.75.dp,
                            if (isCurrent || selected) colors.accentStrong.copy(alpha = 0.45f) else colors.cardBorder,
                            RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { onTrackClick(item) },
                            onLongClick = { onTrackLongClick(item) }
                        )
                        .testTag("downloaded_track_card_${track.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isMultiSelectMode) {
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = if (selected) "Selected" else "Not selected",
                                tint = if (selected) colors.accentStrong else colors.textMuted,
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .size(22.dp)
                            )
                        }

                        CircularArtwork(
                            song = song,
                            isPlaying = isCurrent && isPlaying,
                            accentColor = colors.accentPill,
                            shadowElevation = 2.dp,
                            modifier = Modifier.size(50.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} • ${song.durationFormatted}",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$formatDetail • $formattedDate",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 10.5.sp,
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.isMissingFile) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = "Missing file",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Audio file missing from folder",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (!isMultiSelectMode) {
                            IconButton(
                                onClick = { onToggleFavorite(song) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (song.isFavorite) colors.accentStrong else colors.textMuted,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            IconButton(
                                onClick = { onRequestDelete(item) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("delete_download_btn_${track.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete download",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            IconButton(
                                onClick = { onOpenMore(song) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("more_download_btn_${track.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More options",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
