package ui.home.animations

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.model.DailyMix
import com.example.model.Song
import com.example.util.AlbumArtHelper
import kotlin.math.roundToInt

/**
 * Extracts up to 4 real local tracks with distinct album covers from a DailyMix
 * for multi-tile MIX animations without creating synthetic/fake data.
 */
fun extractMixCoverSongs(mix: DailyMix): List<Song> {
    val distinctAlbums = mix.songs.distinctBy { it.albumId }
    return when {
        distinctAlbums.size >= 4 -> distinctAlbums.take(4)
        mix.songs.isNotEmpty() -> {
            val base = distinctAlbums.ifEmpty { mix.songs }
            List(minOf(4, mix.songs.size)) { idx -> base[idx % base.size] }
        }
        mix.coverSong != null -> listOf(mix.coverSong)
        else -> emptyList()
    }
}

/**
 * Renders the animated visual artwork stage for a Daily Mix card across all 5 selectable styles:
 * - SLIDE: Horizontal card & artwork slide with parallax depth
 * - MIX: Real multi-album cover tiles rearrange smoothly on switch/shuffle
 * - FADE: Smooth crossfade decoupled from staggered typography transitions
 * - PULSE: Gentle rhythmic breathing active only when music is playing
 * - GLITCH: Brief non-looping RGB slice transition on mix change, settling immediately crisp
 */
@Composable
fun DailyMixAnimatedArtworkStage(
    mix: DailyMix,
    style: DailyMixAnimationStyle,
    level: HomeAnimationLevel,
    motionEnabled: Boolean,
    isPlaying: Boolean,
    mixSwitchDirection: Int = 1,
    shuffleRevision: Int = 0,
    pagerOffsetFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    val tintPrimary = Color(mix.gradientColors.firstOrNull() ?: 0xFF4A6FA5)
    val tintSecondary = Color(mix.gradientColors.lastOrNull() ?: 0xFF8AA8E6)

    // Rhythmic PULSE scale (only active when style == PULSE, music is playing, and motionEnabled)
    val pulseTargetScale = if (
        motionEnabled &&
        style == DailyMixAnimationStyle.PULSE &&
        isPlaying &&
        level.amplitudeScale > 0f
    ) {
        val pulseAmp = (0.042f * level.amplitudeScale).coerceIn(0.015f, 0.068f)
        val infinite = rememberInfiniteTransition(label = "daily_mix_pulse_${mix.id}")
        val scale by infinite.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.0f + pulseAmp,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "daily_mix_pulse_scale"
        )
        scale
    } else {
        1.0f
    }

    val settledPulseScale by animateFloatAsState(
        targetValue = pulseTargetScale,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "daily_mix_pulse_settle"
    )
    val effectivePulseScale = if (style == DailyMixAnimationStyle.PULSE && isPlaying && motionEnabled) {
        pulseTargetScale
    } else {
        settledPulseScale
    }

    // Brief one-shot GLITCH burst ONLY on mix change or shuffle trigger; returns cleanly to 0f
    val glitchProgress = remember { Animatable(0f) }
    LaunchedEffect(mix.id, shuffleRevision, style, motionEnabled) {
        if (motionEnabled && style == DailyMixAnimationStyle.GLITCH) {
            glitchProgress.snapTo(1f)
            glitchProgress.animateTo(
                targetValue = 0.45f,
                animationSpec = tween(durationMillis = 75, easing = FastOutSlowInEasing)
            )
            glitchProgress.animateTo(
                targetValue = 0.85f,
                animationSpec = tween(durationMillis = 65, easing = FastOutSlowInEasing)
            )
            glitchProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing)
            )
        } else {
            glitchProgress.snapTo(0f)
        }
    }

    // Subtle parallax translation during SLIDE pager swipe
    val slideArtworkShiftPx = if (motionEnabled && style == DailyMixAnimationStyle.SLIDE) {
        (-pagerOffsetFraction * 36f * level.amplitudeScale).coerceIn(-48f, 48f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(tintPrimary, tintSecondary)))
    ) {
        AnimatedContent(
            targetState = mix,
            transitionSpec = {
                dailyMixStageTransitionSpec(
                    style = style,
                    motionEnabled = motionEnabled,
                    direction = mixSwitchDirection
                )
            },
            contentKey = { it.id },
            label = "daily_mix_artwork_stage",
            modifier = Modifier.fillMaxSize()
        ) { targetMix ->
            when (style) {
                DailyMixAnimationStyle.MIX -> {
                    MixTileRearrangeCollage(
                        mix = targetMix,
                        motionEnabled = motionEnabled,
                        amplitudeScale = level.amplitudeScale,
                        shuffleRevision = shuffleRevision,
                        tintPrimary = tintPrimary,
                        tintSecondary = tintSecondary
                    )
                }

                DailyMixAnimationStyle.GLITCH -> {
                    GlitchArtworkLayer(
                        song = targetMix.coverSong,
                        contentDescription = "${targetMix.title} artwork",
                        glitchStrength = if (motionEnabled) glitchProgress.value * level.amplitudeScale else 0f
                    )
                }

                else -> {
                    MixBitmapCover(
                        song = targetMix.coverSong,
                        contentDescription = "${targetMix.title} artwork",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = slideArtworkShiftPx
                                scaleX = effectivePulseScale
                                scaleY = effectivePulseScale
                            }
                    )
                }
            }
        }
    }
}

/**
 * Animates Daily Mix text metadata independently when FADE is active,
 * or glides cleanly with card direction when SLIDE is active.
 */
@Composable
fun DailyMixAnimatedMetadata(
    mix: DailyMix,
    style: DailyMixAnimationStyle,
    motionEnabled: Boolean,
    mixSwitchDirection: Int = 1,
    modifier: Modifier = Modifier,
    content: @Composable (DailyMix) -> Unit
) {
    AnimatedContent(
        targetState = mix,
        transitionSpec = {
            if (!motionEnabled) {
                fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
                when (style) {
                    DailyMixAnimationStyle.FADE -> {
                        // Text fades slightly after artwork for layered editorial rhythm
                        fadeIn(tween(durationMillis = 320, delayMillis = 90, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween(durationMillis = 200))
                    }
                    DailyMixAnimationStyle.SLIDE -> {
                        val dir = if (mixSwitchDirection >= 0) 1 else -1
                        (slideInHorizontally(
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.86f),
                            initialOffsetX = { (it * 0.25f * dir).roundToInt() }
                        ) + fadeIn(tween(260))) togetherWith
                            (slideOutHorizontally(
                                animationSpec = tween(220),
                                targetOffsetX = { (-it * 0.20f * dir).roundToInt() }
                            ) + fadeOut(tween(180)))
                    }
                    else -> {
                        fadeIn(tween(260)) togetherWith fadeOut(tween(200))
                    }
                }
            }
        },
        contentKey = { it.id },
        label = "daily_mix_text_transition",
        modifier = modifier
    ) { targetMix ->
        content(targetMix)
    }
}

private fun dailyMixStageTransitionSpec(
    style: DailyMixAnimationStyle,
    motionEnabled: Boolean,
    direction: Int
): ContentTransform {
    if (!motionEnabled) {
        return fadeIn(snap()) togetherWith fadeOut(snap())
    }
    val dir = if (direction >= 0) 1 else -1
    return when (style) {
        DailyMixAnimationStyle.SLIDE -> {
            (slideInHorizontally(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetX = { fullWidth -> (fullWidth * 0.45f * dir).roundToInt() }
            ) + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    targetOffsetX = { fullWidth -> (-fullWidth * 0.35f * dir).roundToInt() }
                ) + fadeOut(tween(240)))
        }
        DailyMixAnimationStyle.MIX -> {
            (fadeIn(tween(340)) + scaleIn(initialScale = 0.92f, animationSpec = spring(dampingRatio = 0.85f))) togetherWith
                (fadeOut(tween(260)) + scaleOut(targetScale = 1.05f, animationSpec = tween(260)))
        }
        DailyMixAnimationStyle.FADE -> {
            fadeIn(tween(durationMillis = 440, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(durationMillis = 360, easing = FastOutSlowInEasing))
        }
        DailyMixAnimationStyle.PULSE -> {
            (fadeIn(tween(340)) + scaleIn(initialScale = 0.96f, animationSpec = tween(360))) togetherWith
                fadeOut(tween(260))
        }
        DailyMixAnimationStyle.GLITCH -> {
            fadeIn(tween(120)) togetherWith fadeOut(tween(100))
        }
    }
}

/**
 * Style 2 (MIX): Renders up to 4 real album cover tiles from the mix and smoothly rearranges
 * tile positions & subtle scales whenever the mix changes or shuffle is tapped.
 */
@Composable
private fun MixTileRearrangeCollage(
    mix: DailyMix,
    motionEnabled: Boolean,
    amplitudeScale: Float,
    shuffleRevision: Int,
    tintPrimary: Color,
    tintSecondary: Color
) {
    val coverSongs = remember(mix.id, mix.songs) { extractMixCoverSongs(mix) }
    val density = LocalDensity.current

    if (coverSongs.size <= 1) {
        MixBitmapCover(
            song = coverSongs.firstOrNull(),
            contentDescription = "${mix.title} artwork",
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // Background blurred-like wash using lead cover
    Box(modifier = Modifier.fillMaxSize()) {
        MixBitmapCover(
            song = coverSongs.firstOrNull(),
            contentDescription = "${mix.title} backdrop",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.38f }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            tintPrimary.copy(alpha = 0.45f),
                            tintSecondary.copy(alpha = 0.45f)
                        )
                    )
                )
        )

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            val wPx = with(density) { maxWidth.toPx() }
            val hPx = with(density) { maxHeight.toPx() }
            val tileSize = (minOf(maxWidth, maxHeight) * 0.46f).coerceIn(58.dp, 128.dp)
            val tileSizePx = with(density) { tileSize.toPx() }
            val gapX = ((wPx - tileSizePx * 2f) / 3f).coerceAtLeast(4f)
            val gapY = ((hPx - tileSizePx * 2f) / 3f).coerceAtLeast(4f)

            val slots = remember(wPx, hPx, tileSizePx) {
                listOf(
                    IntOffset(gapX.roundToInt(), gapY.roundToInt()),
                    IntOffset((gapX * 2f + tileSizePx).roundToInt(), (gapY * 0.7f).roundToInt()),
                    IntOffset((gapX * 0.8f).roundToInt(), (gapY * 2f + tileSizePx).roundToInt()),
                    IntOffset((gapX * 2f + tileSizePx).roundToInt(), (gapY * 2.15f + tileSizePx).roundToInt())
                )
            }

            // Deterministic artistic permutation based on mix hash + shuffleRevision
            val stepSeed = (mix.id.hashCode() and 0x7FFF) + shuffleRevision

            coverSongs.forEachIndexed { index, song ->
                val targetSlotIdx = (index + stepSeed) % slots.size
                val targetOffset = slots[targetSlotIdx]
                val animatedOffset by animateIntOffsetAsState(
                    targetValue = targetOffset,
                    animationSpec = if (motionEnabled) {
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    } else {
                        snap()
                    },
                    label = "mix_tile_pos_$index"
                )

                val targetTilt = if (motionEnabled) {
                    val sign = if ((targetSlotIdx + index) % 2 == 0) 1f else -1f
                    sign * (2.5f * amplitudeScale.coerceIn(0f, 1.3f))
                } else 0f

                val animTilt by animateFloatAsState(
                    targetValue = targetTilt,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "mix_tile_tilt_$index"
                )

                val leadBoost = if (targetSlotIdx == 0 && motionEnabled) 1.05f else 0.97f
                val animScale by animateFloatAsState(
                    targetValue = leadBoost,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "mix_tile_scale_$index"
                )

                val tileShape = RoundedCornerShape(16.dp)
                Box(
                    modifier = Modifier
                        .offset { animatedOffset }
                        .size(tileSize)
                        .graphicsLayer {
                            rotationZ = animTilt
                            scaleX = animScale
                            scaleY = animScale
                        }
                        .shadow(8.dp, tileShape)
                        .clip(tileShape)
                        .border(1.2.dp, Color.White.copy(alpha = 0.45f), tileShape)
                ) {
                    MixBitmapCover(
                        song = song,
                        contentDescription = "${song.album} tile artwork",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Style 5 (GLITCH): Brief artistic RGB displacement + horizontal slice clip
 * that executes once on transition and immediately settles to a crisp clean image.
 */
@Composable
private fun GlitchArtworkLayer(
    song: Song?,
    contentDescription: String,
    glitchStrength: Float
) {
    val s = glitchStrength.coerceIn(0f, 1.5f)
    if (s <= 0.02f) {
        MixBitmapCover(
            song = song,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val density = LocalDensity.current
    val shiftPx = with(density) { (7.dp * s).toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // Base clean image
        MixBitmapCover(
            song = song,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )

        // Cyan horizontal RGB ghost slice
        MixBitmapCover(
            song = song,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFF00E5FF).copy(alpha = 0.42f * s.coerceAtMost(1f)), BlendMode.Screen),
            modifier = Modifier
                .fillMaxSize()
                .clip(HorizontalBandShape(topFraction = 0.18f, bottomFraction = 0.56f))
                .graphicsLayer {
                    translationX = -shiftPx
                    alpha = 0.65f * s.coerceAtMost(1f)
                }
        )

        // Magenta horizontal RGB ghost slice
        MixBitmapCover(
            song = song,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color(0xFFFF2E7A).copy(alpha = 0.42f * s.coerceAtMost(1f)), BlendMode.Screen),
            modifier = Modifier
                .fillMaxSize()
                .clip(HorizontalBandShape(topFraction = 0.52f, bottomFraction = 0.84f))
                .graphicsLayer {
                    translationX = shiftPx
                    alpha = 0.65f * s.coerceAtMost(1f)
                }
        )
    }
}

private class HorizontalBandShape(
    private val topFraction: Float,
    private val bottomFraction: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Rectangle(
            Rect(
                left = 0f,
                top = size.height * topFraction,
                right = size.width,
                bottom = size.height * bottomFraction
            )
        )
    }
}

@Composable
internal fun MixBitmapCover(
    song: Song?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null
) {
    val context = LocalContext.current
    val cached = AlbumArtHelper.getCachedBitmap(song)
    val bitmap by produceState<Bitmap?>(
        initialValue = cached,
        key1 = song?.id,
        key2 = song?.albumId
    ) {
        if (value == null) {
            value = AlbumArtHelper.loadAlbumArt(context, song)
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.size(52.dp)
            )
        }
    }
}
