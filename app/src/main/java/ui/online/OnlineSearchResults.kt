package ui.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BookmarkAdded
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import ui.theme.EditorialColors

@Composable
fun OnlineTrackThumbnail(
    artworkUrl: String?,
    title: String,
    kind: OnlineItemKind = OnlineItemKind.TRACK,
    colors: EditorialColors,
    size: Dp = 52.dp,
    cornerRadius: Dp = 14.dp,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    modifier: Modifier = Modifier
) {
    val shape = if (kind == OnlineItemKind.ARTIST) CircleShape else RoundedCornerShape(cornerRadius)
    val context = LocalContext.current

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(colors.accentPill.copy(alpha = 0.65f))
            .border(0.75.dp, colors.cardBorder, shape),
        contentAlignment = Alignment.Center
    ) {
        val fallbackIcon = when (kind) {
            OnlineItemKind.ARTIST -> Icons.Rounded.Person
            OnlineItemKind.ALBUM -> Icons.Rounded.Album
            OnlineItemKind.PLAYLIST -> Icons.AutoMirrored.Rounded.PlaylistPlay
            OnlineItemKind.TRACK -> Icons.Rounded.MusicNote
        }

        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = colors.onAccentPill.copy(alpha = 0.7f),
            modifier = Modifier.size((size.value * 0.45f).dp)
        )

        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = colors.accentStrong,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else if (isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.accentStrong.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Playing",
                    tint = colors.onAccentStrong,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun OnlineProviderPillBar(
    providers: List<OnlineMusicProvider>,
    selectedProvider: OnlineMusicProvider?,
    colors: EditorialColors,
    onSelectProvider: (OnlineMusicProvider) -> Unit,
    modifier: Modifier = Modifier
) {
    if (providers.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(providers, key = { it.key }) { provider ->
            val selected = provider.key == selectedProvider?.key
            Surface(
                onClick = { onSelectProvider(provider) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) colors.accentStrong else colors.surface,
                border = BorderStroke(
                    width = 0.75.dp,
                    color = if (selected) colors.accentStrong else colors.cardBorder
                ),
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .wrapContentWidth()
                    .testTag("online_provider_${provider.key}")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = provider.displayName,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = if (selected) colors.onAccentStrong else colors.textSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineResultRow(
    track: OnlineTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isSaved: Boolean,
    colors: EditorialColors,
    isDownloaded: Boolean = false,
    onDownloadClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isCurrent) colors.accentPill.copy(alpha = 0.55f) else colors.surface,
        border = BorderStroke(
            width = if (isCurrent) 1.dp else 0.75.dp,
            color = if (isCurrent) colors.accentStrong else colors.cardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onMoreClick
            )
            .testTag("online_result_row_${track.stableId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OnlineTrackThumbnail(
                artworkUrl = track.artworkUrl,
                title = track.title,
                kind = track.kind,
                colors = colors,
                size = 50.dp,
                cornerRadius = 12.dp,
                isPlaying = isCurrent && isPlaying,
                isBuffering = isCurrent && isBuffering
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

                Spacer(modifier = Modifier.height(2.dp))

                val subtitle = when (track.kind) {
                    OnlineItemKind.TRACK -> {
                        val dur = if (track.durationMs > 0L || track.isLive) " • ${track.durationFormatted}" else ""
                        "${track.artist}$dur"
                    }
                    OnlineItemKind.ARTIST -> {
                        if (track.subscriberCount > 0) "${track.providerName} Artist • ${formatCount(track.subscriberCount)} subscribers"
                        else "${track.providerName} Artist"
                    }
                    OnlineItemKind.ALBUM -> {
                        val count = if (track.trackCount > 0) " • ${track.trackCount} tracks" else ""
                        "${track.artist}$count"
                    }
                    OnlineItemKind.PLAYLIST -> {
                        val count = if (track.trackCount > 0) " • ${track.trackCount} tracks" else ""
                        "${track.artist}$count"
                    }
                }

                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.surfaceTinted
                    ) {
                        Text(
                            text = track.providerName.ifBlank { "Online" },
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (track.kind != OnlineItemKind.TRACK) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.accentPill.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = track.kind.name,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = colors.onAccentPill,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isSaved) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdded,
                            contentDescription = "Saved to Library",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (track.kind == OnlineItemKind.TRACK && onDownloadClick != null) {
                IconButton(
                    onClick = onDownloadClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("online_download_btn_${track.stableId}")
                ) {
                    Icon(
                        imageVector = if (isDownloaded) Icons.Rounded.DownloadDone else Icons.Rounded.Download,
                        contentDescription = if (isDownloaded) "Downloaded Offline" else "Download ${track.title}",
                        tint = if (isDownloaded) colors.accentStrong else colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options for ${track.title}",
                    tint = colors.textMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnlineDiscoveryCard(
    track: OnlineTrack,
    isCurrent: Boolean,
    isPlaying: Boolean,
    colors: EditorialColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (isCurrent) colors.accentPill.copy(alpha = 0.55f) else colors.surface,
        border = BorderStroke(
            width = if (isCurrent) 1.dp else 0.75.dp,
            color = if (isCurrent) colors.accentStrong else colors.cardBorder
        ),
        modifier = modifier
            .width(160.dp)
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("online_discovery_card_${track.stableId}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(contentAlignment = Alignment.BottomEnd) {
                OnlineTrackThumbnail(
                    artworkUrl = track.artworkUrl,
                    title = track.title,
                    kind = track.kind,
                    colors = colors,
                    size = 140.dp,
                    cornerRadius = 16.dp,
                    isPlaying = isCurrent && isPlaying
                )

                Surface(
                    shape = CircleShape,
                    color = colors.accentStrong,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCurrent && isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = "Play ${track.title}",
                            tint = colors.onAccentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = track.title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = track.artist,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (track.durationMs > 0L || track.isLive) {
                Text(
                    text = "${track.providerName} • ${track.durationFormatted}",
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

private fun formatCount(value: Long): String {
    return when {
        value >= 1_000_000L -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
