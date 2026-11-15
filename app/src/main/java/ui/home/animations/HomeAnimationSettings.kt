package ui.home.animations

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DailyMix
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import kotlinx.coroutines.delay
import ui.components.OrganicHeroLeafShape
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Settings -> Appearance -> Home Animations
 * Lets users select Home Animation Intensity and Daily Mix Animation Style,
 * preview all effects live with real library artwork, and persist settings via DataStore.
 */
@Composable
fun HomeAnimationSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val appliedLevel by viewModel.homeAnimationLevel.collectAsState()
    val appliedMixStyle by viewModel.dailyMixAnimationStyle.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()

    var draftLevel by remember(appliedLevel) { mutableStateOf(appliedLevel) }
    var draftMixStyle by remember(appliedMixStyle) { mutableStateOf(appliedMixStyle) }
    var previewMixIndex by remember { mutableIntStateOf(0) }
    var previewDirection by remember { mutableIntStateOf(1) }
    var previewShuffleRev by remember { mutableIntStateOf(0) }
    var previewPlaying by remember(playbackInfo.isPlaying) { mutableStateOf(playbackInfo.isPlaying) }
    var savedBannerVisible by remember { mutableStateOf(false) }

    val hasUnappliedChanges = draftLevel != appliedLevel || draftMixStyle != appliedMixStyle

    LaunchedEffect(savedBannerVisible) {
        if (savedBannerVisible) {
            delay(2000)
            savedBannerVisible = false
        }
    }

    val previewMixes = remember(dailyMixes, allSongs) {
        if (dailyMixes.isNotEmpty()) {
            dailyMixes
        } else {
            val leadSong = allSongs.firstOrNull()
            val secondSong = allSongs.getOrNull(1) ?: leadSong
            listOf(
                DailyMix(
                    id = "preview_mix_1",
                    title = "Daily Mix 1",
                    subtitle = leadSong?.artist ?: "Local Library",
                    description = "Real tracks from your device",
                    badge = "PREVIEW",
                    songs = allSongs.take(4),
                    coverSong = leadSong,
                    gradientColors = listOf(0xFF4A6FA5, 0xFF8AA8E6)
                ),
                DailyMix(
                    id = "preview_mix_2",
                    title = "Daily Mix 2",
                    subtitle = secondSong?.artist ?: "Discovery",
                    description = "Curated preview deck",
                    badge = "ROTATION",
                    songs = allSongs.drop(1).take(4).ifEmpty { allSongs.take(4) },
                    coverSong = secondSong,
                    gradientColors = listOf(0xFF6B5B95, 0xFFB8A9C9)
                )
            )
        }
    }

    val safeIndex = previewMixIndex.coerceIn(0, (previewMixes.size - 1).coerceAtLeast(0))
    val activePreviewMix = previewMixes[safeIndex]
    val activeHeroSong = activePreviewMix.coverSong ?: allSongs.firstOrNull()

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .testTag("home_animation_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .testTag("home_anim_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = colors.textPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Home Animations",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "Preview & customize artwork & Daily Mix motion",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = colors.surfaceTinted,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            draftLevel = HomeAnimationLevel.DEFAULT
                            draftMixStyle = DailyMixAnimationStyle.DEFAULT
                            previewShuffleRev++
                        }
                        .testTag("home_anim_reset_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset animation defaults",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Live Interactive Preview Card
                item(key = "anim_preview_stage") {
                    AnimationPreviewStage(
                        previewMix = activePreviewMix,
                        heroSong = activeHeroSong,
                        draftLevel = draftLevel,
                        draftStyle = draftMixStyle,
                        previewPlaying = previewPlaying,
                        direction = previewDirection,
                        shuffleRevision = previewShuffleRev,
                        hasUnappliedChanges = hasUnappliedChanges,
                        colors = colors,
                        onNextMix = {
                            if (previewMixes.size > 1) {
                                previewDirection = 1
                                previewMixIndex = (safeIndex + 1) % previewMixes.size
                            } else {
                                previewShuffleRev++
                            }
                        },
                        onTriggerEffect = {
                            previewShuffleRev++
                        },
                        onTogglePreviewPlay = {
                            previewPlaying = !previewPlaying
                        }
                    )
                }

                // 1. Home Animation Intensity Selector
                item(key = "home_anim_level_selector") {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "ARTWORK MOTION INTENSITY",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.1.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = draftLevel.displayName,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = colors.accentStrong
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(HomeAnimationLevel.entries, key = { it.id }) { level ->
                                val selected = draftLevel == level
                                Surface(
                                    onClick = { draftLevel = level },
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) colors.accentPill.copy(alpha = 0.72f) else colors.surface,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = if (selected) 1.5.dp else 0.75.dp,
                                        color = if (selected) colors.accentStrong else colors.cardBorder
                                    ),
                                    modifier = Modifier
                                        .width(148.dp)
                                        .testTag("home_anim_level_${level.id}")
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = level.displayName,
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = colors.textPrimary
                                            )
                                            if (selected) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
                                                    contentDescription = "Selected",
                                                    tint = colors.accentStrong,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = level.subtitle,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = colors.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Daily Mix Animation Selector
                item(key = "daily_mix_style_header") {
                    Text(
                        text = "DAILY MIX ANIMATION STYLE",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp,
                        color = colors.textMuted
                    )
                }

                items(DailyMixAnimationStyle.entries, key = { it.id }) { style ->
                    val selected = draftMixStyle == style
                    val cardScale by animateFloatAsState(
                        targetValue = if (selected) 1.0f else 0.99f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "style_card_scale"
                    )

                    Surface(
                        onClick = {
                            draftMixStyle = style
                            previewShuffleRev++
                        },
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected) colors.accentPill.copy(alpha = 0.56f) else colors.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (selected) 1.5.dp else 0.75.dp,
                            color = if (selected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = cardScale
                                scaleY = cardScale
                            }
                            .testTag("daily_mix_style_${style.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = style.displayName,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = colors.textPrimary
                                    )
                                    if (appliedMixStyle == style) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = colors.accentStrong.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = colors.accentStrong,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = style.subtitle,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) colors.accentStrong else colors.surfaceTinted)
                                    .border(1.dp, if (selected) colors.accentStrong else colors.cardBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "${style.displayName} selected",
                                        tint = colors.onAccentStrong,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "anim_settings_bottom_spacer") {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Bottom Apply Action Dock
            Surface(
                color = colors.surface.copy(alpha = 0.96f),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.75.dp, colors.cardBorder, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    AnimatedVisibility(visible = savedBannerVisible) {
                        Text(
                            text = "Animation preferences saved.",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.accentStrong,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                draftLevel = appliedLevel
                                draftMixStyle = appliedMixStyle
                                onBack()
                            },
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .weight(0.42f)
                        ) {
                            Text(
                                text = if (hasUnappliedChanges) "Discard" else "Close",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.applyHomeAnimationSettings(draftLevel, draftMixStyle)
                                savedBannerVisible = true
                            },
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            ),
                            modifier = Modifier
                                .height(48.dp)
                                .weight(0.58f)
                                .testTag("apply_home_animations_button")
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (hasUnappliedChanges) "Apply Animations" else "Applied",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimationPreviewStage(
    previewMix: DailyMix,
    heroSong: com.example.model.Song?,
    draftLevel: HomeAnimationLevel,
    draftStyle: DailyMixAnimationStyle,
    previewPlaying: Boolean,
    direction: Int,
    shuffleRevision: Int,
    hasUnappliedChanges: Boolean,
    colors: EditorialColors,
    onNextMix: () -> Unit,
    onTriggerEffect: () -> Unit,
    onTogglePreviewPlay: () -> Unit
) {
    val motionEnabled = rememberShouldAnimateHomeMotion(
        level = draftLevel,
        screenVisible = true,
        ignoreVisibilityForPreview = true
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(28.dp))
            .testTag("home_anim_preview_stage")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (hasUnappliedChanges) "LIVE PREVIEW (UNAPPLIED)" else "LIVE PREVIEW",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    color = colors.accentStrong
                )
                Text(
                    text = "${draftLevel.displayName} • ${draftStyle.displayName}",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated Organic Artwork Medallion Preview
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(114.dp)
                ) {
                    AlbumArtworkAnimator(
                        song = heroSong,
                        contentDescription = "Preview artwork motion",
                        level = draftLevel,
                        screenVisible = true,
                        ignoreVisibilityForPreview = true,
                        shape = OrganicHeroLeafShape,
                        fallbackTint = colors.accentPill,
                        iconTint = colors.onAccentPill,
                        floatingAmplitudeDp = 4.5.dp,
                        floatingDurationMs = 3800,
                        enableParallax = true,
                        enableSlowZoom = true,
                        decorativeRotation = previewPlaying,
                        isPlaying = previewPlaying,
                        shadowColor = colors.shadowColor,
                        shadowElevation = 10.dp,
                        modifier = Modifier.size(106.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Touch & Float",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textMuted
                    )
                }

                // Live Daily Mix Card Preview
                val cardShape = RoundedCornerShape(22.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(152.dp)
                        .shadow(10.dp, cardShape, ambientColor = colors.shadowColor)
                        .clip(cardShape)
                        .border(0.75.dp, colors.cardBorder, cardShape)
                ) {
                    DailyMixAnimatedArtworkStage(
                        mix = previewMix,
                        style = draftStyle,
                        level = draftLevel,
                        motionEnabled = motionEnabled,
                        isPlaying = previewPlaying,
                        mixSwitchDirection = direction,
                        shuffleRevision = shuffleRevision,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.14f),
                                    0.55f to Color.Transparent,
                                    1.0f to Color.Black.copy(alpha = 0.80f)
                                )
                            )
                    )

                    DailyMixAnimatedMetadata(
                        mix = previewMix,
                        style = draftStyle,
                        motionEnabled = motionEnabled,
                        mixSwitchDirection = direction,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) { mixData ->
                        Column {
                            Text(
                                text = mixData.title,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${draftStyle.displayName} style • ${mixData.subtitle}",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Preview interactive controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onNextMix,
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceTinted,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Switch Mix",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    onClick = onTriggerEffect,
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceTinted,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Replay FX",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    onClick = onTogglePreviewPlay,
                    shape = RoundedCornerShape(16.dp),
                    color = if (previewPlaying) colors.accentPill else colors.surfaceTinted,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (previewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = if (previewPlaying) colors.onAccentPill else colors.accentStrong,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (previewPlaying) "Playing" else "Paused",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = if (previewPlaying) colors.onAccentPill else colors.textPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
