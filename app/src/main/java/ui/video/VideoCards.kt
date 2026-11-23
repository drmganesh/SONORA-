package ui.video

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import ui.theme.EditorialColors

/**
 * Full-width editorial SONORA video card with 16:9 thumbnail, duration/LIVE badge,
 * optional resume progress bar, channel avatar, title, real views & upload date, and 3-dot menu.
 */
@Composable
fun OnlineVideoCard(
    video: OnlineVideoItem,
    colors: EditorialColors,
    onClick: () -> Unit,
    onChannelClick: (() -> Unit)? = null,
    onMoreClick: () -> Unit,
    isPlayingNow: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (isPlayingNow) 1.5.dp else 0.75.dp,
            color = if (isPlayingNow) colors.accentStrong else colors.cardBorder
        ),
        shadowElevation = if (colors.isDark) 2.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_card_${video.videoId}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            VideoThumbnailBox(
                video = video,
                colors = colors,
                isPlayingNow = isPlayingNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                ChannelAvatarBadge(
                    avatarUrl = video.channelAvatarUrl,
                    channelName = video.channelName,
                    colors = colors,
                    sizeDp = 38,
                    onClick = onChannelClick
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        lineHeight = 19.sp,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = video.channelName,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = if (onChannelClick != null) colors.accentStrong else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (onChannelClick != null) {
                                    Modifier
                                        .clickable(onClick = onChannelClick)
                                        .testTag("video_channel_name_${video.videoId}")
                                } else Modifier
                            )
                    )
                    val metaSuffix = listOfNotNull(
                        video.formattedViewCount(),
                        video.uploadDateText?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (metaSuffix.isNotBlank()) {
                        Text(
                            text = metaSuffix,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("video_more_${video.videoId}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options for ${video.title}",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Horizontal carousel card for Online Video Home sections.
 */
@Composable
fun HorizontalVideoCarouselCard(
    video: OnlineVideoItem,
    colors: EditorialColors,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onChannelClick: (() -> Unit)? = null,
    isPlayingNow: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(
            width = if (isPlayingNow) 1.5.dp else 0.75.dp,
            color = if (isPlayingNow) colors.accentStrong else colors.cardBorder
        ),
        modifier = modifier
            .width(248.dp)
            .testTag("video_carousel_card_${video.videoId}")
    ) {
        Column {
            VideoThumbnailBox(
                video = video,
                colors = colors,
                isPlayingNow = isPlayingNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                ChannelAvatarBadge(
                    avatarUrl = video.channelAvatarUrl,
                    channelName = video.channelName,
                    colors = colors,
                    sizeDp = 30,
                    onClick = onChannelClick
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = video.channelName,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = if (onChannelClick != null) colors.accentStrong else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (onChannelClick != null) {
                                    Modifier
                                        .clickable(onClick = onChannelClick)
                                        .testTag("carousel_channel_name_${video.videoId}")
                                } else Modifier
                            )
                    )
                    val viewsOrDate = listOfNotNull(
                        video.formattedViewCount(),
                        video.uploadDateText?.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    if (viewsOrDate.isNotBlank()) {
                        Text(
                            text = viewsOrDate,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 10.5.sp,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options for ${video.title}",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Compact horizontal row card for Related Videos, Queue, Search results, and Video Library lists.
 */
@Composable
fun CompactVideoRowCard(
    video: OnlineVideoItem,
    colors: EditorialColors,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    onChannelClick: (() -> Unit)? = null,
    watchProgressFraction: Float? = null,
    isPlayingNow: Boolean = false,
    extraMetaText: String? = null,
    secondaryActionIcon: ImageVector? = null,
    secondaryActionDescription: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    trailingActionIcon: ImageVector? = null,
    trailingActionDescription: String? = null,
    onTrailingAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isPlayingNow) colors.accentPill.copy(alpha = 0.4f) else colors.surface,
        border = BorderStroke(
            width = if (isPlayingNow) 1.25.dp else 0.75.dp,
            color = if (isPlayingNow) colors.accentStrong else colors.cardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("compact_video_row_${video.videoId}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoThumbnailBox(
                video = video,
                colors = colors,
                isPlayingNow = isPlayingNow,
                compactBadge = true,
                watchProgressFraction = watchProgressFraction,
                modifier = Modifier
                    .width(136.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    lineHeight = 17.5.sp,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = video.channelName,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.5.sp,
                    color = if (onChannelClick != null) colors.accentStrong else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (onChannelClick != null) {
                                Modifier
                                    .clickable(onClick = onChannelClick)
                                    .testTag("compact_channel_name_${video.videoId}")
                            } else Modifier
                        )
                )
                val viewsOrDate = listOfNotNull(video.formattedViewCount(), video.uploadDateText?.takeIf { it.isNotBlank() })
                    .joinToString(" • ")
                if (viewsOrDate.isNotBlank()) {
                    Text(
                        text = viewsOrDate,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!extraMetaText.isNullOrBlank()) {
                    Text(
                        text = extraMetaText,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.5.sp,
                        color = colors.accentStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (secondaryActionIcon != null && onSecondaryAction != null) {
                IconButton(
                    onClick = onSecondaryAction,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("compact_video_secondary_${video.videoId}")
                ) {
                    Icon(
                        imageVector = secondaryActionIcon,
                        contentDescription = secondaryActionDescription ?: "Add to queue",
                        tint = colors.accentStrong,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            if (trailingActionIcon != null && onTrailingAction != null) {
                IconButton(
                    onClick = onTrailingAction,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("compact_video_trailing_${video.videoId}")
                ) {
                    Icon(
                        imageVector = trailingActionIcon,
                        contentDescription = trailingActionDescription ?: "Action",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More options for ${video.title}",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
fun VideoThumbnailBox(
    video: OnlineVideoItem,
    colors: EditorialColors,
    isPlayingNow: Boolean = false,
    compactBadge: Boolean = false,
    watchProgressFraction: Float? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier.background(colors.surfaceTinted),
        contentAlignment = Alignment.Center
    ) {
        if (!video.thumbnailUrl.isNullOrBlank()) {
            val reqWidth = if (compactBadge) 320 else 640
            val reqHeight = if (compactBadge) 180 else 360
            val imageRequest = remember(video.thumbnailUrl, reqWidth, reqHeight) {
                ImageRequest.Builder(context)
                    .data(video.thumbnailUrl)
                    .size(reqWidth, reqHeight)
                    .precision(Precision.INEXACT)
                    .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = colors.accentStrong.copy(alpha = 0.65f),
                modifier = Modifier.size(if (compactBadge) 28.dp else 44.dp)
            )
        }

        // Now playing overlay badge
        if (isPlayingNow) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.accentStrong,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = colors.onAccentStrong,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "PLAYING",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = colors.onAccentStrong
                    )
                }
            }
        }

        // Duration or LIVE badge
        val durationBadge = video.formattedDuration()
        if (durationBadge.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (video.isLive) Color(0xFFD32F2F) else Color.Black.copy(alpha = 0.80f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = durationBadge,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compactBadge) 9.5.sp else 11.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Watch progress bar at bottom of thumbnail if partially watched
        val resolvedFraction = watchProgressFraction ?: if (video.lastPositionMs > 5_000L && video.durationMs > 0L) {
            (video.lastPositionMs.toFloat() / video.durationMs.toFloat()).coerceIn(0.03f, 1f)
        } else {
            null
        }
        if (resolvedFraction != null && resolvedFraction > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.28f))
                    .testTag("video_progress_bar_${video.videoId}")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(resolvedFraction.coerceIn(0.03f, 1f))
                        .height(3.dp)
                        .background(colors.accentStrong)
                )
            }
        }
    }
}

@Composable
fun ChannelAvatarBadge(
    avatarUrl: String?,
    channelName: String,
    colors: EditorialColors,
    sizeDp: Int = 38,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(colors.accentPill)
            .then(clickMod),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            val pxTarget = (sizeDp * 2).coerceIn(72, 192)
            val avatarRequest = remember(avatarUrl, pxTarget) {
                ImageRequest.Builder(context)
                    .data(avatarUrl)
                    .size(pxTarget, pxTarget)
                    .precision(Precision.INEXACT)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = avatarRequest,
                contentDescription = "$channelName avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = channelName.trim().firstOrNull()?.uppercase() ?: "V",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (sizeDp * 0.42f).sp,
                color = colors.onAccentPill
            )
        }
    }
}

/**
 * Animated shimmer-like skeleton card while real NewPipe video discovery is loading.
 */
@Composable
fun VideoCardSkeleton(
    colors: EditorialColors,
    isCarousel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "video_skeleton")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "video_skeleton_alpha"
    )
    val placeholderColor = colors.surfaceTinted.copy(alpha = alpha)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(0.5.dp, colors.cardBorder),
        modifier = modifier
            .then(if (isCarousel) Modifier.width(248.dp) else Modifier.fillMaxWidth())
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(placeholderColor)
            )
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(placeholderColor)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(placeholderColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(placeholderColor)
                    )
                }
            }
        }
    }
}

/**
 * Overflow menu bottom sheet for any video item across Home, Search, Details, and Library.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsBottomSheet(
    video: OnlineVideoItem,
    isWatchLater: Boolean,
    isSaved: Boolean,
    colors: EditorialColors,
    onPlayNow: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleWatchLater: () -> Unit,
    onToggleSave: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenChannel: (() -> Unit)?,
    onShare: () -> Unit,
    onOpenOfficialApp: () -> Unit,
    onNotInterested: (() -> Unit)? = null,
    onDontRecommendChannel: (() -> Unit)? = null,
    onHideVideo: (() -> Unit)? = null,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("video_options_sheet")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VideoThumbnailBox(
                    video = video,
                    colors = colors,
                    compactBadge = true,
                    modifier = Modifier
                        .width(96.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = video.channelName,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = colors.cardBorder)
            Spacer(modifier = Modifier.height(6.dp))

            VideoOptionRow(Icons.Rounded.PlayArrow, "Play Now", colors) {
                onPlayNow(); onDismiss()
            }
            VideoOptionRow(Icons.AutoMirrored.Rounded.QueueMusic, "Play Next in Video Queue", colors) {
                onPlayNext(); onDismiss()
            }
            VideoOptionRow(
                icon = Icons.Rounded.WatchLater,
                label = if (isWatchLater) "Remove from Watch Later" else "Save to Watch Later",
                colors = colors
            ) {
                onToggleWatchLater(); onDismiss()
            }
            VideoOptionRow(
                icon = if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                label = if (isSaved) "Remove from Saved Videos" else "Save to Video Library",
                colors = colors
            ) {
                onToggleSave(); onDismiss()
            }
            VideoOptionRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to Video Playlist", colors) {
                onDismiss(); onAddToPlaylist()
            }
            if (onOpenChannel != null) {
                VideoOptionRow(Icons.Rounded.AccountCircle, "View Channel (${video.channelName})", colors) {
                    onDismiss(); onOpenChannel()
                }
            }
            VideoOptionRow(Icons.Rounded.Share, "Share Video Link", colors) {
                onShare(); onDismiss()
            }
            VideoOptionRow(Icons.AutoMirrored.Rounded.OpenInNew, "Open in Official YouTube App", colors) {
                onOpenOfficialApp(); onDismiss()
            }
            if (onNotInterested != null || onDontRecommendChannel != null || onHideVideo != null) {
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.cardBorder)
                Spacer(modifier = Modifier.height(6.dp))
                if (onNotInterested != null) {
                    VideoOptionRow(Icons.Rounded.Close, "Not Interested", colors) {
                        onNotInterested(); onDismiss()
                    }
                }
                if (onDontRecommendChannel != null) {
                    VideoOptionRow(Icons.Rounded.Close, "Don't Recommend ${video.channelName.ifBlank { "This Channel" }}", colors) {
                        onDontRecommendChannel(); onDismiss()
                    }
                }
                if (onHideVideo != null) {
                    VideoOptionRow(Icons.Rounded.Close, "Hide Video", colors) {
                        onHideVideo(); onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoOptionRow(
    icon: ImageVector,
    label: String,
    colors: EditorialColors,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accentStrong,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun VideoPlaylistPickerDialog(
    video: OnlineVideoItem,
    playlists: List<OnlineVideoPlaylistSummary>,
    colors: EditorialColors,
    onSelectPlaylist: (OnlineVideoPlaylistSummary) -> Unit,
    onCreatePlaylistWithVideo: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                text = "Add to Video Playlist",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = video.title,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("New video playlist name...", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentStrong,
                            unfocusedBorderColor = colors.cardBorder,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreatePlaylistWithVideo(newName.trim())
                                onDismiss()
                            }
                        },
                        shape = CircleShape,
                        color = colors.accentStrong,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Add, contentDescription = "Create playlist", tint = colors.onAccentStrong)
                        }
                    }
                }

                if (playlists.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        playlists.forEach { playlist ->
                            Surface(
                                onClick = {
                                    onSelectPlaylist(playlist)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(14.dp),
                                color = colors.surfaceTinted,
                                border = BorderStroke(0.5.dp, colors.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = playlist.name,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${playlist.videoCount} videos",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
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
            TextButton(onClick = onDismiss) {
                Text("Close", color = colors.accentStrong)
            }
        }
    )
}
