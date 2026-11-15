package ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DailyMix
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import kotlinx.coroutines.launch
import ui.home.animations.DailyMixAnimatedArtworkStage
import ui.home.animations.DailyMixAnimatedMetadata
import ui.home.animations.DailyMixAnimationStyle
import ui.home.animations.HomeAnimationLevel
import ui.home.animations.rememberShouldAnimateHomeMotion
import ui.theme.EditorialColors
import kotlin.math.absoluteValue

/**
 * Interactive Daily Mixes Carousel featuring swipeable HorizontalPager cards,
 * quick-select mix chips, 5 selectable animation styles (Slide, Mix, Fade, Pulse, Glitch),
 * real local album artwork, animated Play feedback, and Shuffle controls.
 */
@Composable
fun DailyMixSection(
    mixes: List<DailyMix>,
    colors: EditorialColors,
    onMixClick: (DailyMix) -> Unit,
    onPlayMix: (DailyMix) -> Unit,
    onShuffleMix: (DailyMix) -> Unit = onPlayMix,
    animationStyle: DailyMixAnimationStyle = DailyMixAnimationStyle.SLIDE,
    animationLevel: HomeAnimationLevel = HomeAnimationLevel.BALANCED,
    screenVisible: Boolean = true,
    currentPlayingSongId: Long? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (mixes.isEmpty()) return

    val motionEnabled = rememberShouldAnimateHomeMotion(
        level = animationLevel,
        screenVisible = screenVisible
    )
    val pagerState = rememberPagerState(pageCount = { mixes.size })
    val scope = rememberCoroutineScope()
    var lastIndex by remember { mutableIntStateOf(0) }
    var switchDirection by remember { mutableIntStateOf(1) }
    var shuffleRevision by remember { mutableIntStateOf(0) }

    // Synchronize horizontal swipe settle events with MIX tile rearrangement & GLITCH burst
    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        if (settled != lastIndex) {
            switchDirection = if (settled > lastIndex) 1 else -1
            lastIndex = settled
            shuffleRevision++
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("daily_mix_section")
    ) {
        EditorialSectionTitle(
            title = "Daily Mixes",
            subtitle = "Curated from your local library • ${animationStyle.displayName}",
            colors = colors
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick-select mix pills synced bidirectionally with horizontal swipe pager
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("daily_mix_selector_strip")
        ) {
            itemsIndexed(mixes, key = { _, m -> "pill_${m.id}" }) { index, mix ->
                val selected = pagerState.currentPage == index
                Surface(
                    onClick = {
                        if (index != pagerState.currentPage) {
                            switchDirection = if (index > pagerState.currentPage) 1 else -1
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        } else {
                            onMixClick(mix)
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) colors.accentStrong else colors.surfaceTinted,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 0.75.dp,
                        color = if (selected) colors.accentStrong else colors.cardBorder
                    ),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("daily_mix_pill_${mix.id}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mix.title,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = if (selected) colors.onAccentStrong else colors.textSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal swipeable Daily Mix cards
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 22.dp),
            pageSpacing = 14.dp,
            key = { idx -> mixes[idx].id },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("daily_mix_pager")
        ) { pageIndex ->
            val mix = mixes[pageIndex]
            val pageOffset = ((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
            val absOffset = pageOffset.absoluteValue.coerceIn(0f, 1f)

            val isMixPlaying = isPlaying && currentPlayingSongId != null &&
                mix.songs.any { it.id == currentPlayingSongId }

            // Smooth page focus scale & fade curve tailored to the active DailyMixAnimationStyle
            val cardScale = when {
                !motionEnabled -> 1f
                animationStyle == DailyMixAnimationStyle.SLIDE -> 1f - (0.055f * absOffset)
                else -> 1f - (0.03f * absOffset)
            }
            val cardAlpha = when {
                !motionEnabled -> 1f
                animationStyle == DailyMixAnimationStyle.FADE -> 1f - (0.48f * absOffset)
                else -> 1f - (0.14f * absOffset)
            }

            DailyMixEditorialCard(
                mix = mix,
                colors = colors,
                style = animationStyle,
                level = animationLevel,
                motionEnabled = motionEnabled,
                isMixPlaying = isMixPlaying,
                isActivePage = pagerState.currentPage == pageIndex,
                pageOffsetFraction = pageOffset,
                mixSwitchDirection = switchDirection,
                shuffleRevision = shuffleRevision,
                onClick = { onMixClick(mix) },
                onPlayClick = { onPlayMix(mix) },
                onShuffleClick = {
                    shuffleRevision++
                    onShuffleMix(mix)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha
                    }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DailyMixEditorialCard(
    mix: DailyMix,
    colors: EditorialColors,
    style: DailyMixAnimationStyle,
    level: HomeAnimationLevel,
    motionEnabled: Boolean,
    isMixPlaying: Boolean,
    isActivePage: Boolean,
    pageOffsetFraction: Float,
    mixSwitchDirection: Int,
    shuffleRevision: Int,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onShuffleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(
        topStart = 28.dp,
        topEnd = 28.dp,
        bottomStart = 28.dp,
        bottomEnd = 12.dp
    )

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed && motionEnabled) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "daily_mix_play_scale"
    )

    val shuffleInteraction = remember { MutableInteractionSource() }
    val shufflePressed by shuffleInteraction.collectIsPressedAsState()
    val shuffleScale by animateFloatAsState(
        targetValue = if (shufflePressed && motionEnabled) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "daily_mix_shuffle_scale"
    )

    // Subtle text fade separation when FADE animation style is selected
    val textFadeAlpha = if (motionEnabled && style == DailyMixAnimationStyle.FADE) {
        (1f - pageOffsetFraction.absoluteValue * 1.45f).coerceIn(0f, 1f)
    } else {
        1f
    }

    Box(
        modifier = modifier
            .height(228.dp)
            .shadow(14.dp, cardShape, ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .testTag("daily_mix_card_${mix.id}")
    ) {
        // Real album artwork animated according to selected DailyMixAnimationStyle
        DailyMixAnimatedArtworkStage(
            mix = mix,
            style = style,
            level = level,
            motionEnabled = motionEnabled,
            isPlaying = isMixPlaying,
            mixSwitchDirection = mixSwitchDirection,
            shuffleRevision = shuffleRevision,
            pagerOffsetFraction = pageOffsetFraction,
            modifier = Modifier.fillMaxSize()
        )

        // Smooth bottom editorial scrim so metadata stays crisp on any cover art
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.16f),
                        0.42f to Color.Transparent,
                        0.66f to Color.Black.copy(alpha = 0.56f),
                        1.0f to Color.Black.copy(alpha = 0.88f)
                    )
                )
        )

        // Category badge top-left + active style pill top-right
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(14.dp)
                .graphicsLayer { alpha = textFadeAlpha },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.92f)
            ) {
                Text(
                    text = mix.badge,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color(0xFF15151C),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }

            if (isActivePage) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Black.copy(alpha = 0.44f),
                    border = androidx.compose.foundation.BorderStroke(0.75.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "${mix.songs.size} tracks",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Bottom info + Shuffle + Animated Play button
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DailyMixAnimatedMetadata(
                mix = mix,
                style = style,
                motionEnabled = motionEnabled,
                mixSwitchDirection = mixSwitchDirection,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
                    .graphicsLayer { alpha = textFadeAlpha }
            ) { targetMix ->
                Column {
                    Text(
                        text = targetMix.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${targetMix.subtitle} • ${targetMix.description}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.86f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Shuffle button (48dp touch area)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = shuffleScale
                            scaleY = shuffleScale
                        }
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = shuffleInteraction,
                            indication = androidx.compose.material3.ripple(bounded = true),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onShuffleClick()
                            }
                        )
                        .testTag("daily_mix_shuffle_${mix.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.48f))
                            .border(1.dp, Color.White.copy(alpha = 0.50f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle ${mix.title}",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Play button with animated spring feedback (48dp touch area)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            scaleX = playScale
                            scaleY = playScale
                        }
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = playInteraction,
                            indication = androidx.compose.material3.ripple(bounded = true),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPlayClick()
                            }
                        )
                        .testTag("daily_mix_play_${mix.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.accentPill)
                            .border(1.2.dp, Color.White.copy(alpha = 0.65f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMixPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isMixPlaying) "Pause ${mix.title}" else "Play ${mix.title}",
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditorialSectionTitle(
    title: String,
    subtitle: String? = null,
    colors: EditorialColors,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                letterSpacing = (-0.3).sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
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

        if (actionLabel != null && onActionClick != null) {
            Surface(
                onClick = onActionClick,
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceTinted
            ) {
                Text(
                    text = actionLabel,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = colors.accentStrong,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
