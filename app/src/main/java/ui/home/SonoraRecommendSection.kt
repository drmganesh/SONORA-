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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.ThumbDownOffAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import data.online.OnlineTrackMapper
import domain.online.DiscoverSectionType
import domain.online.OnlineTrack
import domain.online.RecommendedSongItem
import domain.online.SonoraPersonalizedRecommendationEngine
import domain.online.SonoraRecommendUiState
import ui.home.animations.AlbumArtworkAnimator
import ui.home.animations.HomeAnimationLevel
import ui.home.animations.rememberShouldAnimateHomeMotion
import ui.motion.LocalEffectiveMotionState
import ui.motion.carouselItemDepthEffect
import ui.theme.EditorialColors

/**
 * Personalized "Sonora Recommend" horizontal carousel placed immediately above
 * Trending Music India on the Online Music Home screen.
 */
@Composable
fun SonoraRecommendSection(
    state: SonoraRecommendUiState,
    favoriteOnlineIds: Set<Long>,
    currentSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    animationLevel: HomeAnimationLevel,
    screenVisible: Boolean,
    reduceAnimations: Boolean,
    onRefresh: () -> Unit,
    onPlayTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onAddToQueue: (OnlineTrack) -> Unit,
    onPlayNext: (OnlineTrack) -> Unit,
    onToggleLike: (OnlineTrack) -> Unit,
    onDislikeTrack: (OnlineTrack) -> Unit,
    onOpenArtist: (OnlineTrack) -> Unit,
    onOpenAlbum: (OnlineTrack) -> Unit,
    onMoreOptions: (OnlineTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    val allRecommendedTracks = remember(state.items) { state.items.map { it.track } }
    val subtitleText = remember(state.isColdStartDiscovery, state.profileSummary) {
        when {
            state.isColdStartDiscovery -> SonoraPersonalizedRecommendationEngine.COLD_START_MESSAGE
            state.profileSummary.isNotBlank() -> state.profileSummary
            else -> "Curated from your likes, artists, playlists & listening"
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("sonora_recommend_section")
    ) {
        // Editorial Section Header (full-width title + description)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(colors.accentStrong, colors.accentPill)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.onAccentStrong,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.sonora_recommend_title),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    letterSpacing = (-0.25).sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .testTag("sonora_recommend_title")
                )
                if (state.isServingCachedOffline) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.6.dp, colors.cardBorder),
                        modifier = Modifier
                            .wrapContentWidth()
                            .testTag("sonora_recommend_offline_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudOff,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = stringResource(R.string.sonora_recommend_cached_offline),
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp,
                                color = colors.textSecondary,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Text(
                text = subtitleText,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = if (state.isColdStartDiscovery) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 12.sp,
                color = if (state.isColdStartDiscovery) colors.accentStrong else colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sonora_recommend_subtitle")
            )
        }

        // Body States: Loading / Items Carousel / Error-Offline Card
        when {
            state.isLoading && state.items.isEmpty() -> {
                HomeOnlineSkeletonRow(
                    sectionType = DiscoverSectionType.MUSIC_RECOMMENDATIONS,
                    colors = colors,
                    reduceMotion = reduceAnimations || animationLevel == HomeAnimationLevel.OFF,
                    modifier = Modifier.testTag("sonora_recommend_loading")
                )
            }

            state.items.isNotEmpty() -> {
                val listState = rememberLazyListState()
                val snapFling = rememberSnapFlingBehavior(lazyListState = listState)
                val motionState = LocalEffectiveMotionState.current

                LazyRow(
                    state = listState,
                    flingBehavior = snapFling,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.testTag("sonora_recommend_carousel")
                ) {
                    itemsIndexed(
                        items = state.items,
                        key = { _, item -> "sonora_rec_${item.track.stableId}_${item.track.contentUrl}" }
                    ) { idx, recItem ->
                        val track = recItem.track
                        val isCurrent = currentSongId == track.stableId
                        val isLiked = track.isFavorite || track.stableId in favoriteOnlineIds

                        SonoraRecommendSongCard(
                            item = recItem,
                            index = idx,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            isLiked = isLiked,
                            colors = colors,
                            animationLevel = animationLevel,
                            screenVisible = screenVisible,
                            modifier = Modifier.carouselItemDepthEffect(listState, idx, motionState),
                            onPlay = { onPlayTrack(track, allRecommendedTracks) },
                            onAddToQueue = { onAddToQueue(track) },
                            onPlayNext = { onPlayNext(track) },
                            onToggleLike = { onToggleLike(track) },
                            onDislike = { onDislikeTrack(track) },
                            onOpenArtist = { onOpenArtist(track) },
                            onOpenAlbum = { onOpenAlbum(track) },
                            onMoreOptions = { onMoreOptions(track) }
                        )
                    }
                }
            }

            else -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("sonora_recommend_empty_or_error")
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
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = if (state.isColdStartDiscovery) {
                                    stringResource(R.string.sonora_recommend_cold_start)
                                } else {
                                    stringResource(R.string.sonora_recommend_title)
                                },
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = state.errorMessage
                                    ?: "Like songs, follow artists, or pull down to refresh personalized picks.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Button(
                            onClick = onRefresh,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("sonora_recommend_retry_button")
                        ) {
                            Text(
                                text = stringResource(R.string.sonora_recommend_refresh),
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SonoraRecommendSongCard(
    item: RecommendedSongItem,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    colors: EditorialColors,
    animationLevel: HomeAnimationLevel,
    screenVisible: Boolean,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onToggleLike: () -> Unit,
    onDislike: () -> Unit,
    onOpenArtist: () -> Unit,
    onOpenAlbum: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val track = item.track
    val haptic = LocalHapticFeedback.current
    val mappedSong = remember(track) { OnlineTrackMapper.toUnifiedSong(track) }
    val hasValidAlbum = remember(track.album) {
        SonoraPersonalizedRecommendationEngine.isValidAlbumName(track.album)
    }
    var menuExpanded by remember { mutableStateOf(false) }

    val motionEnabled = rememberShouldAnimateHomeMotion(level = animationLevel, screenVisible = screenVisible)
    val playPulseScale = if (motionEnabled && isCurrent && isPlaying) {
        val transition = rememberInfiniteTransition(label = "rec_play_pulse_${track.stableId}")
        val pulse by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                animation = tween(950, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "rec_play_pulse_scale"
        )
        pulse
    } else {
        1.0f
    }

    val cardWidth = 166.dp
    val artworkSize = 156.dp

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface.copy(alpha = 0.88f),
        border = BorderStroke(
            width = if (isCurrent) 1.5.dp else 0.75.dp,
            color = if (isCurrent) colors.accentStrong else colors.cardBorder
        ),
        shadowElevation = if (isCurrent) 10.dp else 4.dp,
        modifier = modifier
            .width(cardWidth)
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                onClick = onPlay,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuExpanded = true
                }
            )
            .testTag("sonora_recommend_card_${track.stableId}")
    ) {
        Column(
            modifier = Modifier.padding(5.dp)
        ) {
            Box(modifier = Modifier.size(artworkSize)) {
                AlbumArtworkAnimator(
                    song = mappedSong,
                    contentDescription = "${track.title} album artwork",
                    level = animationLevel,
                    screenVisible = screenVisible,
                    shape = RoundedCornerShape(18.dp),
                    fallbackTint = colors.surfaceTinted,
                    iconTint = colors.accentStrong,
                    cornerMorphBaseDp = 18.dp,
                    cornerMorphDeltaDp = 4.dp,
                    enableFloating = true,
                    floatingPhaseMs = (index * 340) % 2400,
                    floatingDurationMs = 4500 + (index % 3) * 350,
                    enableSlowZoom = true,
                    zoomRange = 0.032f,
                    enableParallax = true,
                    parallaxMaxOffsetDp = 3.5.dp,
                    shadowColor = colors.shadowColor,
                    shadowElevation = 6.dp,
                    borderWidth = 0.5.dp,
                    borderColor = colors.cardBorder,
                    fallbackLabel = track.title,
                    fallbackIconSize = 34.dp,
                    modifier = Modifier.size(artworkSize),
                    overlayContent = {
                        // Top-Start Reason Pill
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.66f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(7.dp)
                        ) {
                            Text(
                                text = item.reasonLabel,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 9.5.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        // Top-End Quick Like Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleLike()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(48.dp)
                                .testTag("sonora_recommend_like_${track.stableId}")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.52f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (isLiked) {
                                        stringResource(R.string.sonora_recommend_action_unlike)
                                    } else {
                                        stringResource(R.string.sonora_recommend_action_like)
                                    },
                                    tint = if (isLiked) colors.accentStrong else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Bottom-End Play Button (48dp hit target)
                        IconButton(
                            onClick = onPlay,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(48.dp)
                                .testTag("sonora_recommend_play_${track.stableId}")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isCurrent) colors.accentStrong else colors.accentPill.copy(alpha = 0.96f),
                                shadowElevation = 5.dp,
                                modifier = Modifier
                                    .size(38.dp)
                                    .graphicsLayer {
                                        scaleX = playPulseScale
                                        scaleY = playPulseScale
                                    }
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
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 0.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = track.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist.ifBlank { track.providerName },
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasValidAlbum) {
                        Text(
                            text = track.album,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 10.5.sp,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("sonora_recommend_more_${track.stableId}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Actions for ${track.title}",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(colors.surface)
                            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(14.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sonora_recommend_action_play_now), fontFamily = PlusJakartaFontFamily) },
                            leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.accentStrong) },
                            onClick = {
                                menuExpanded = false
                                onPlay()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sonora_recommend_action_play_next), fontFamily = PlusJakartaFontFamily) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null, tint = colors.textPrimary) },
                            modifier = Modifier.testTag("sonora_recommend_menu_play_next"),
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sonora_recommend_action_add_queue), fontFamily = PlusJakartaFontFamily) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = colors.textPrimary) },
                            modifier = Modifier.testTag("sonora_recommend_menu_add_queue"),
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isLiked) stringResource(R.string.sonora_recommend_action_unlike) else stringResource(R.string.sonora_recommend_action_like),
                                    fontFamily = PlusJakartaFontFamily
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = null,
                                    tint = if (isLiked) colors.accentStrong else colors.textPrimary
                                )
                            },
                            modifier = Modifier.testTag("sonora_recommend_menu_like"),
                            onClick = {
                                menuExpanded = false
                                onToggleLike()
                            }
                        )
                        if (SonoraPersonalizedRecommendationEngine.isValidArtistName(track.artist)) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sonora_recommend_action_open_artist), fontFamily = PlusJakartaFontFamily) },
                                leadingIcon = { Icon(Icons.Rounded.PersonSearch, contentDescription = null, tint = colors.textPrimary) },
                                modifier = Modifier.testTag("sonora_recommend_menu_open_artist"),
                                onClick = {
                                    menuExpanded = false
                                    onOpenArtist()
                                }
                            )
                        }
                        if (hasValidAlbum) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sonora_recommend_action_open_album), fontFamily = PlusJakartaFontFamily) },
                                leadingIcon = { Icon(Icons.Rounded.Album, contentDescription = null, tint = colors.textPrimary) },
                                modifier = Modifier.testTag("sonora_recommend_menu_open_album"),
                                onClick = {
                                    menuExpanded = false
                                    onOpenAlbum()
                                }
                            )
                        }
                        HorizontalDivider(color = colors.cardBorder)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sonora_recommend_action_dislike), fontFamily = PlusJakartaFontFamily) },
                            leadingIcon = { Icon(Icons.Rounded.ThumbDownOffAlt, contentDescription = null, tint = colors.textSecondary) },
                            modifier = Modifier.testTag("sonora_recommend_menu_dislike"),
                            onClick = {
                                menuExpanded = false
                                onDislike()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.sonora_recommend_action_more), fontFamily = PlusJakartaFontFamily) },
                            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null, tint = colors.textSecondary) },
                            onClick = {
                                menuExpanded = false
                                onMoreOptions()
                            }
                        )
                    }
                }
            }
        }
    }
}
