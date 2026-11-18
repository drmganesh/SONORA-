package ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import data.online.OnlineTrackMapper
import domain.online.DiscoverSectionType
import domain.online.OnlineItemKind
import domain.online.OnlineTrack
import ui.components.CircularArtwork
import ui.home.animations.AlbumArtworkAnimator
import ui.home.animations.HomeAnimationLevel
import ui.home.animations.rememberShouldAnimateHomeMotion
import ui.theme.EditorialColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeOnlineTrendingCard(
    track: OnlineTrack,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    colors: EditorialColors,
    animationLevel: HomeAnimationLevel,
    screenVisible: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkSize: Dp = 148.dp,
    cornerRadius: Dp = 24.dp
) {
    val haptic = LocalHapticFeedback.current
    val mappedSong = remember(track) { OnlineTrackMapper.toUnifiedSong(track) }
    val motionEnabled = rememberShouldAnimateHomeMotion(level = animationLevel, screenVisible = screenVisible)
    val playPulseScale = if (motionEnabled && isCurrent && isPlaying) {
        val transition = rememberInfiniteTransition(label = "online_play_pulse_${track.stableId}")
        val pulse by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(950, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "online_play_pulse_scale"
        )
        pulse
    } else {
        1.0f
    }

    Column(
        modifier = modifier
            .width(artworkSize + 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(4.dp)
            .testTag("home_online_card_${track.stableId}")
    ) {
        AlbumArtworkAnimator(
            song = mappedSong,
            contentDescription = "${track.title} artwork",
            level = animationLevel,
            screenVisible = screenVisible,
            shape = RoundedCornerShape(cornerRadius),
            fallbackTint = colors.surfaceTinted,
            iconTint = colors.accentStrong,
            cornerMorphBaseDp = cornerRadius,
            cornerMorphDeltaDp = 5.dp,
            enableFloating = true,
            floatingPhaseMs = (index * 360) % 2400,
            floatingDurationMs = 4400 + (index % 3) * 380,
            enableSlowZoom = true,
            zoomRange = 0.036f,
            enableParallax = true,
            parallaxMaxOffsetDp = 4.dp,
            shadowColor = colors.shadowColor,
            shadowElevation = if (isCurrent) 14.dp else 9.dp,
            borderWidth = if (isCurrent) 1.5.dp else 0.75.dp,
            borderColor = if (isCurrent) colors.accentStrong else colors.cardBorder,
            fallbackLabel = track.title,
            fallbackIconSize = 36.dp,
            modifier = Modifier.size(artworkSize),
            overlayContent = {
                // Provider Badge (Top Start)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.surface.copy(alpha = 0.90f),
                    border = BorderStroke(0.5.dp, colors.cardBorder),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = track.providerName.ifBlank { "Online" },
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.5.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }

                // Duration or Kind Badge (Top End)
                val endTagText = when {
                    track.kind == OnlineItemKind.ALBUM -> "ALBUM"
                    track.kind == OnlineItemKind.PLAYLIST -> "MIX"
                    track.durationMs > 0L || track.isLive -> track.durationFormatted
                    else -> null
                }
                if (endTagText != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Black.copy(alpha = 0.64f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = endTagText,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.5.sp,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                // Animated Play Button (Bottom End)
                Surface(
                    onClick = onClick,
                    shape = CircleShape,
                    color = if (isCurrent) colors.accentStrong else colors.accentPill.copy(alpha = 0.95f),
                    shadowElevation = 5.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(38.dp)
                        .graphicsLayer {
                            scaleX = playPulseScale
                            scaleY = playPulseScale
                        }
                        .testTag("home_online_play_${track.stableId}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCurrent && isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                            contentDescription = "Play ${track.title}",
                            tint = if (isCurrent) colors.onAccentStrong else colors.onAccentPill,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = track.title,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = if (isCurrent) colors.accentStrong else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(2.dp))

        val metaText = buildString {
            append(track.artist.ifBlank { track.providerName })
            if (track.kind == OnlineItemKind.TRACK && (track.durationMs > 0L || track.isLive)) {
                append(" • ")
                append(track.durationFormatted)
            } else if (track.trackCount > 0L) {
                append(" • ")
                append("${track.trackCount} tracks")
            }
        }
        Text(
            text = metaText,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 11.5.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeOnlineArtistMedallion(
    artistTrack: OnlineTrack,
    index: Int,
    colors: EditorialColors,
    animationLevel: HomeAnimationLevel,
    screenVisible: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val mappedSong = remember(artistTrack) { OnlineTrackMapper.toUnifiedSong(artistTrack) }
    Column(
        modifier = modifier
            .width(98.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(4.dp)
            .testTag("home_online_artist_${artistTrack.stableId}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularArtwork(
            song = mappedSong,
            fallbackLabel = artistTrack.title,
            accentColor = colors.accentPill,
            shadowElevation = 6.dp,
            borderWidth = 2.dp,
            borderColor = colors.surface,
            animationLevel = animationLevel,
            screenVisible = screenVisible,
            enableFloating = true,
            floatingAmplitudeDp = 2.4.dp,
            floatingDurationMs = 4200 + (index % 3) * 380,
            floatingPhaseMs = (index * 320) % 2000,
            enableSlowZoom = true,
            decorativeRotation = false,
            modifier = Modifier.size(84.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artistTrack.title,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = artistTrack.providerName,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 11.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeOnlineRecentMedallion(
    song: Song,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    colors: EditorialColors,
    animationLevel: HomeAnimationLevel,
    screenVisible: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = modifier
            .width(106.dp)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(4.dp)
            .testTag("home_online_recent_${song.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularArtwork(
            song = song,
            fallbackLabel = song.title,
            isPlaying = isCurrent && isPlaying,
            accentColor = if (isCurrent) colors.accentStrong else colors.accentPill,
            shadowElevation = if (isCurrent) 12.dp else 7.dp,
            borderWidth = if (isCurrent) 2.5.dp else 0.75.dp,
            borderColor = if (isCurrent) colors.accentStrong else colors.cardBorder,
            animationLevel = animationLevel,
            screenVisible = screenVisible,
            enableFloating = true,
            floatingAmplitudeDp = 2.4.dp,
            floatingDurationMs = 4300 + (index % 3) * 420,
            floatingPhaseMs = (index * 360) % 2100,
            enableSlowZoom = isCurrent,
            decorativeRotation = isCurrent,
            modifier = Modifier.size(94.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (isCurrent) colors.accentStrong else colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "${song.artist} • ${song.providerName.ifBlank { "Online" }}",
            fontFamily = PlusJakartaFontFamily,
            fontSize = 10.5.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HomeOnlineSkeletonRow(
    sectionType: DiscoverSectionType,
    colors: EditorialColors,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val alpha = if (reduceMotion) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "online_skeleton_${sectionType.name}")
        val animatedAlpha by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.78f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "online_skeleton_alpha"
        )
        animatedAlpha
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
        modifier = modifier.testTag("home_online_skeleton_${sectionType.name.lowercase()}")
    ) {
        items(4) {
            if (sectionType == DiscoverSectionType.POPULAR_ARTISTS) {
                Column(
                    modifier = Modifier.width(94.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceTinted.copy(alpha = alpha))
                            .border(0.75.dp, colors.cardBorder, CircleShape)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(68.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceTinted.copy(alpha = alpha))
                    )
                }
            } else {
                Column(modifier = Modifier.width(152.dp)) {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.surfaceTinted.copy(alpha = alpha))
                            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(24.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceTinted.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceTinted.copy(alpha = alpha * 0.85f))
                    )
                }
            }
        }
    }
}

@Composable
fun HomeOnlineLoadMoreCard(
    sectionType: DiscoverSectionType,
    isLoadingMore: Boolean,
    colors: EditorialColors,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { if (!isLoadingMore) onLoadMore() },
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = modifier
            .width(108.dp)
            .height(148.dp)
            .testTag("home_online_load_more_${sectionType.name.lowercase()}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoadingMore) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = colors.accentStrong,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.accentPill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Load more ${sectionType.title}",
                        tint = colors.onAccentPill
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Load More",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun HomeOnlineSectionStatusCard(
    sectionType: DiscoverSectionType,
    title: String,
    message: String,
    showRetry: Boolean,
    colors: EditorialColors,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tag = if (showRetry) {
        "home_online_error_${sectionType.name.lowercase()}"
    } else {
        "home_online_empty_${sectionType.name.lowercase()}"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showRetry) {
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("home_online_retry_${sectionType.name.lowercase()}")
                ) {
                    Text(
                        text = "Retry",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
