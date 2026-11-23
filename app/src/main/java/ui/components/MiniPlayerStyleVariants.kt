package ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.DynamicColorPalette
import ui.theme.EditorialColors
import ui.theme.LocalReduceAnimations
import kotlin.math.cos
import kotlin.math.sin

/**
 * Extended Mini Player Style Renderers (16 additional styles complementing the 4 core styles
 * in FloatingMiniPlayer.kt for 20 total distinct Mini Player designs).
 */
@Composable
internal fun RenderExtendedMiniPlayerStyle(
    style: MiniPlayerStyle,
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    palette: DynamicColorPalette,
    frequencies: FloatArray,
    queue: List<Song>,
    queueIndex: Int,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onOpenLyrics: (() -> Unit)?
) {
    when (style) {
        MiniPlayerStyle.GLASS_BAR -> GlassBarMiniContent(
            song, playbackInfo, progress, colors, palette, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.DYNAMIC_ARTWORK_BAR -> DynamicArtworkBarMiniContent(
            song, playbackInfo, progress, palette, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.AMOLED_BAR -> AmoledBarMiniContent(
            song, playbackInfo, progress, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.GRADIENT_BAR -> GradientBarMiniContent(
            song, playbackInfo, progress, palette, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.VINYL_MINI -> VinylMiniContent(
            song, playbackInfo, progress, colors, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.CASSETTE_MINI -> CassetteMiniContent(
            song, playbackInfo, progress, colors, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.CD_MINI -> CdMiniContent(
            song, playbackInfo, progress, colors, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.SPECTRUM_MINI -> SpectrumMiniContent(
            song, playbackInfo, progress, colors, palette, frequencies, onTogglePlayPause, onPlayNext
        )
        MiniPlayerStyle.CIRCULAR_PLAYER -> CircularMiniContent(
            song, playbackInfo, progress, colors, onTogglePlayPause, onPlayNext
        )
        MiniPlayerStyle.EXPANDABLE_CARD -> ExpandableCardMiniContent(
            song, playbackInfo, progress, colors, queue, queueIndex, onTogglePlayPause, onPlayNext, onPlayPrevious, onOpenLyrics
        )
        MiniPlayerStyle.MINIMAL_TEXT -> MinimalTextMiniContent(
            song, playbackInfo, progress, colors, onTogglePlayPause, onPlayNext
        )
        MiniPlayerStyle.BLUR_CARD -> BlurCardMiniContent(
            song, playbackInfo, progress, palette, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.NEON_MINI -> NeonMiniContent(
            song, playbackInfo, progress, palette, onTogglePlayPause, onPlayNext
        )
        MiniPlayerStyle.MATERIAL_YOU_MINI -> MaterialYouMiniContent(
            song, playbackInfo, progress, colors, palette, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.EDGE_TO_EDGE_MINI -> EdgeToEdgeMiniContent(
            song, playbackInfo, progress, colors, onTogglePlayPause, onPlayNext, onPlayPrevious
        )
        MiniPlayerStyle.FLOATING_CORNER_PLAYER -> FloatingCornerMiniContent(
            song, playbackInfo, progress, colors, onTogglePlayPause, onPlayNext
        )
        // Core 4 handled directly in FloatingMiniPlayer
        MiniPlayerStyle.COMPACT_BAR,
        MiniPlayerStyle.FLOATING_PILL,
        MiniPlayerStyle.ARTWORK_FOCUS,
        MiniPlayerStyle.WAVE_MINI -> Unit
    }
}

@Composable
private fun rememberMiniSpinAngle(isPlaying: Boolean, durationMs: Int = 5000): Float {
    val reduceAnim = LocalReduceAnimations.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    if (!isPlaying || reduceAnim || !lifecycleState.isAtLeast(Lifecycle.State.STARTED)) return 0f
    val transition = rememberInfiniteTransition(label = "miniSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "miniSpinAngle"
    )
    return angle
}

@Composable
private fun MiniProgressStrip(progress: Float, activeColor: Color, trackColor: Color, heightDp: Float = 2.5f) {
    InteractiveMiniSeekbar(
        activeColor = activeColor,
        trackColor = trackColor,
        trackHeight = heightDp.dp,
        touchHeight = 14.dp
    )
}

@Composable
private fun MiniTransportButtons(
    isPlaying: Boolean,
    primaryTint: Color,
    secondaryTint: Color,
    showPrevious: Boolean,
    onPlayPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    playContainerColor: Color? = null,
    playContainerShape: androidx.compose.ui.graphics.Shape = CircleShape
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showPrevious) {
            IconButton(
                onClick = onPlayPrevious,
                modifier = Modifier.size(48.dp).testTag("mini_skip_prev_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = secondaryTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        IconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(48.dp).testTag("mini_play_pause_button")
        ) {
            if (playContainerColor != null) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(playContainerColor, playContainerShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = primaryTint,
                        modifier = Modifier.size(21.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = primaryTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        IconButton(
            onClick = onPlayNext,
            modifier = Modifier.size(48.dp).testTag("mini_skip_next_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Next Track",
                tint = secondaryTint,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun GlassBarMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    palette: DynamicColorPalette,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().testTag("mini_style_content_glass_bar")) {
        ArtworkAlbumView(
            song = song,
            modifier = Modifier
                .matchParentSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(16.dp) else Modifier)
                .alpha(if (colors.isDark) 0.28f else 0.18f),
            cornerRadius = 0.dp,
            isPlaying = false
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            palette.primary.copy(alpha = 0.24f),
                            if (colors.isDark) Color(0xAA10131C) else Color(0xB8FFFFFF),
                            palette.secondary.copy(alpha = 0.22f)
                        )
                    )
                )
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.size(44.dp).border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    cornerRadius = 12.dp,
                    isPlaying = playbackInfo.isPlaying
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MiniTransportButtons(
                    isPlaying = playbackInfo.isPlaying,
                    primaryTint = colors.textPrimary,
                    secondaryTint = colors.textPrimary,
                    showPrevious = false,
                    onPlayPrevious = onPlayPrevious,
                    onTogglePlayPause = onTogglePlayPause,
                    onPlayNext = onPlayNext,
                    playContainerColor = Color.White.copy(alpha = if (colors.isDark) 0.16f else 0.65f)
                )
            }
            MiniProgressStrip(progress, colors.accentStrong, Color.White.copy(alpha = 0.2f))
        }
    }
}

@Composable
private fun DynamicArtworkBarMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    palette: DynamicColorPalette,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    val bg = lerp(Color(0xFF151822), palette.primary, 0.72f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .testTag("mini_style_content_dynamic_artwork_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkAlbumView(song = song, modifier = Modifier.size(44.dp), cornerRadius = 10.dp, isPlaying = playbackInfo.isPlaying)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = bg,
                secondaryTint = Color.White,
                showPrevious = false,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = Color.White
            )
        }
        MiniProgressStrip(progress, palette.accent, Color.White.copy(alpha = 0.22f), heightDp = 3f)
    }
}

@Composable
private fun AmoledBarMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .testTag("mini_style_content_amoled_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkAlbumView(
                song = song,
                modifier = Modifier.size(40.dp).border(0.75.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                cornerRadius = 8.dp,
                isPlaying = playbackInfo.isPlaying
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = Color(0xFF9EA4B0),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = Color.White,
                secondaryTint = Color.White,
                showPrevious = false,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext
            )
        }
        MiniProgressStrip(progress, Color.White, Color.White.copy(alpha = 0.16f), heightDp = 2f)
    }
}

@Composable
private fun GradientBarMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    palette: DynamicColorPalette,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            lerp(Color(0xFF141824), palette.primary, 0.78f),
            lerp(Color(0xFF121521), palette.secondary, 0.72f),
            lerp(Color(0xFF1A1426), palette.accent, 0.58f)
        )
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .testTag("mini_style_content_gradient_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkAlbumView(song = song, modifier = Modifier.size(44.dp), cornerRadius = 12.dp, isPlaying = playbackInfo.isPlaying)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = Color.White,
                secondaryTint = Color.White,
                showPrevious = false,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = Color.White.copy(alpha = 0.20f)
            )
        }
        MiniProgressStrip(progress, Color.White, Color.White.copy(alpha = 0.25f), heightDp = 2.5f)
    }
}

@Composable
private fun VinylMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    val spinAngle = rememberMiniSpinAngle(playbackInfo.isPlaying, durationMs = 4200)
    Column(modifier = Modifier.fillMaxWidth().testTag("mini_style_content_vinyl_mini")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF14151A)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    rotate(spinAngle, c) {
                        drawCircle(color = Color.White.copy(alpha = 0.14f), radius = r * 0.86f, center = c, style = Stroke(1.dp.toPx()))
                        drawCircle(color = Color.White.copy(alpha = 0.10f), radius = r * 0.72f, center = c, style = Stroke(1.dp.toPx()))
                    }
                }
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.size(26.dp).clip(CircleShape),
                    cornerRadius = 13.dp,
                    isPlaying = playbackInfo.isPlaying
                )
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.accentStrong))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "33 RPM • ${song.artist}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = colors.onAccentStrong,
                secondaryTint = colors.textPrimary,
                showPrevious = false,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = colors.accentStrong
            )
        }
        MiniProgressStrip(progress, colors.accentStrong, colors.cardBorder.copy(alpha = 0.4f))
    }
}

@Composable
private fun CassetteMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    val reelAngle = rememberMiniSpinAngle(playbackInfo.isPlaying, durationMs = 2800)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1F222A))
            .testTag("mini_style_content_cassette_mini")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini cassette window with dual rotating spools
            Canvas(
                modifier = Modifier
                    .size(width = 64.dp, height = 38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF101218))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            ) {
                val cy = size.height / 2f
                val left = Offset(size.width * 0.28f, cy)
                val right = Offset(size.width * 0.72f, cy)
                val r = size.height * 0.26f
                drawLine(Color(0xFF4F3527), Offset(left.x, cy + r), Offset(right.x, cy + r), strokeWidth = 2.dp.toPx())
                listOf(left, right).forEach { c ->
                    drawCircle(Color(0xFFE6ECF5), radius = r, center = c)
                    rotate(reelAngle, c) {
                        for (s in 0..2) {
                            val rad = Math.toRadians(s * 120.0)
                            drawLine(
                                color = colors.accentStrong,
                                start = c,
                                end = Offset(c.x + (cos(rad) * r * 0.85f).toFloat(), c.y + (sin(rad) * r * 0.85f).toFloat()),
                                strokeWidth = 1.6.dp.toPx()
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFF6EFE2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "SIDE A • ${song.artist}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = Color(0xFFB5BAC6),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = Color.White,
                secondaryTint = Color(0xFFE5E9F0),
                showPrevious = false,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = colors.accentStrong,
                playContainerShape = RoundedCornerShape(10.dp)
            )
        }
        MiniProgressStrip(progress, colors.accentStrong, Color.White.copy(alpha = 0.16f))
    }
}

@Composable
private fun CdMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    val spinAngle = rememberMiniSpinAngle(playbackInfo.isPlaying, durationMs = 5600)
    Column(modifier = Modifier.fillMaxWidth().testTag("mini_style_content_cd_mini")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    cornerRadius = 24.dp,
                    isPlaying = playbackInfo.isPlaying
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    rotate(spinAngle, c) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, Color(0x66FFFFFF), Color(0x5580E8FF), Color(0x55F48BFF), Color.Transparent),
                                center = c
                            ),
                            radius = r,
                            center = c
                        )
                    }
                    drawCircle(Color(0xFFDDE3EC), radius = r * 0.28f, center = c)
                    drawCircle(colors.miniPlayerBackground, radius = r * 0.14f, center = c)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "CD DIGITAL • ${song.artist}",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = colors.onAccentStrong,
                secondaryTint = colors.textPrimary,
                showPrevious = false,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = colors.accentStrong
            )
        }
        MiniProgressStrip(progress, colors.accentStrong, colors.cardBorder.copy(alpha = 0.4f))
    }
}

@Composable
private fun SpectrumMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    palette: DynamicColorPalette,
    frequencies: FloatArray,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("mini_style_content_spectrum_mini")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkAlbumView(song = song, modifier = Modifier.size(42.dp), cornerRadius = 10.dp, isPlaying = playbackInfo.isPlaying)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                    val bars = 18
                    val gap = 2.dp.toPx()
                    val barW = ((size.width - gap * (bars - 1)) / bars).coerceAtLeast(2f)
                    for (i in 0 until bars) {
                        val level = if (playbackInfo.isPlaying && frequencies.isNotEmpty()) {
                            frequencies[i % frequencies.size].coerceIn(0.12f, 1f)
                        } else if (playbackInfo.isPlaying) {
                            (0.28f + ((i * 37) % 72) / 100f).coerceIn(0.15f, 1f)
                        } else 0.14f
                        val h = size.height * level
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(colors.accentStrong, palette.secondary)),
                            topLeft = Offset(i * (barW + gap), size.height - h),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = colors.accentStrong,
                secondaryTint = colors.textPrimary,
                showPrevious = false,
                onPlayPrevious = {},
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext
            )
        }
        MiniProgressStrip(progress, colors.accentStrong, colors.cardBorder.copy(alpha = 0.4f), heightDp = 2f)
    }
}

@Composable
private fun CircularMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .testTag("mini_style_content_circular_player")
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    drawCircle(color = colors.cardBorder.copy(alpha = 0.45f), style = Stroke(stroke))
                    drawArc(
                        color = colors.accentStrong,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.size(38.dp).clip(CircleShape),
                    cornerRadius = 19.dp,
                    isPlaying = playbackInfo.isPlaying
                )
            }
            Column(modifier = Modifier.widthIn(min = 64.dp, max = 116.dp).weight(1f, fill = false)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = colors.onAccentStrong,
                secondaryTint = colors.textPrimary,
                showPrevious = false,
                onPlayPrevious = {},
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = colors.accentStrong
            )
        }
        InteractiveMiniSeekbar(
            activeColor = colors.accentStrong,
            trackColor = colors.cardBorder.copy(alpha = 0.4f),
            trackHeight = 2.5.dp,
            touchHeight = 14.dp,
            rounded = true,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun ExpandableCardMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    queue: List<Song>,
    queueIndex: Int,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onOpenLyrics: (() -> Unit)?
) {
    var expanded by remember { mutableStateOf(false) }
    val upNext = remember(queue, queueIndex) {
        if (queue.isEmpty()) emptyList()
        else queue.drop((queueIndex + 1).coerceAtLeast(0)).take(2)
    }

    Column(modifier = Modifier.fillMaxWidth().testTag("mini_style_content_expandable_card")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkAlbumView(song = song, modifier = Modifier.size(46.dp), cornerRadius = 12.dp, isPlaying = playbackInfo.isPlaying)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.size(48.dp).testTag("mini_expand_toggle_button")
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    contentDescription = if (expanded) "Collapse Mini Card" else "Expand Mini Card",
                    tint = colors.accentStrong
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = colors.onAccentStrong,
                secondaryTint = colors.textPrimary,
                showPrevious = false,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = colors.accentStrong
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceTinted.copy(alpha = 0.65f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            onClick = onPlayPrevious,
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surface,
                            border = BorderStroke(0.75.dp, colors.cardBorder)
                        ) {
                            Text(
                                text = "Previous",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        if (onOpenLyrics != null) {
                            Surface(
                                onClick = onOpenLyrics,
                                shape = RoundedCornerShape(10.dp),
                                color = colors.accentPill
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Lyrics, contentDescription = null, tint = colors.onAccentPill, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Lyrics", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.onAccentPill)
                                }
                            }
                        }
                    }
                    Text(
                        text = "UP NEXT (${upNext.size})",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentStrong
                    )
                }
                InteractiveMiniSeekbar(
                    activeColor = colors.accentStrong,
                    trackColor = colors.cardBorder.copy(alpha = 0.45f),
                    trackHeight = 4.dp,
                    touchHeight = 22.dp,
                    rounded = true,
                    showTimestamps = true,
                    timestampColor = colors.textSecondary
                )
                if (upNext.isNotEmpty()) {
                    upNext.forEach { queued ->
                        Text(
                            text = "• ${queued.title} — ${queued.artist}",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "Queue ready — tap to open full player",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        if (!expanded) {
            MiniProgressStrip(progress, colors.accentStrong, colors.cardBorder.copy(alpha = 0.45f), heightDp = 3f)
        }
    }
}

@Composable
private fun MinimalTextMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("mini_style_content_minimal_text")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = colors.accentStrong,
                secondaryTint = colors.textPrimary,
                showPrevious = false,
                onPlayPrevious = {},
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext
            )
        }
        MiniProgressStrip(progress, colors.accentStrong, colors.cardBorder.copy(alpha = 0.35f), heightDp = 1.75f)
    }
}

@Composable
private fun BlurCardMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    palette: DynamicColorPalette,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().testTag("mini_style_content_blur_card")) {
        ArtworkAlbumView(
            song = song,
            modifier = Modifier
                .matchParentSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(20.dp) else Modifier),
            cornerRadius = 0.dp,
            isPlaying = false
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xB80A0C12), Color(0x99101420), palette.primary.copy(alpha = 0.45f))
                    )
                )
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtworkAlbumView(song = song, modifier = Modifier.size(46.dp), cornerRadius = 12.dp, isPlaying = playbackInfo.isPlaying)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = Color(0xFFD7DCEA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MiniTransportButtons(
                    isPlaying = playbackInfo.isPlaying,
                    primaryTint = Color(0xFF0E121D),
                    secondaryTint = Color.White,
                    showPrevious = false,
                    onPlayPrevious = onPlayPrevious,
                    onTogglePlayPause = onTogglePlayPause,
                    onPlayNext = onPlayNext,
                    playContainerColor = Color.White
                )
            }
            MiniProgressStrip(progress, Color.White, Color.White.copy(alpha = 0.24f), heightDp = 2.5f)
        }
    }
}

@Composable
private fun NeonMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    palette: DynamicColorPalette,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit
) {
    val neonCyan = Color(0xFF39F3FF)
    val neonPink = Color(0xFFFF4EDB)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080912))
            .border(1.2.dp, Brush.horizontalGradient(listOf(neonCyan, neonPink, palette.accent)), RoundedCornerShape(20.dp))
            .testTag("mini_style_content_neon_mini")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkAlbumView(
                song = song,
                modifier = Modifier.size(42.dp).border(1.dp, neonCyan, RoundedCornerShape(10.dp)),
                cornerRadius = 10.dp,
                isPlaying = playbackInfo.isPlaying
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = neonCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = Color(0xFF080912),
                secondaryTint = neonCyan,
                showPrevious = false,
                onPlayPrevious = {},
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = neonPink
            )
        }
        InteractiveMiniSeekbar(
            activeColor = neonPink,
            activeBrush = Brush.horizontalGradient(listOf(neonCyan, neonPink)),
            trackColor = Color.White.copy(alpha = 0.14f),
            trackHeight = 3.dp,
            touchHeight = 14.dp
        )
    }
}

@Composable
private fun MaterialYouMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    palette: DynamicColorPalette,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.accentPill.copy(alpha = if (colors.isDark) 0.42f else 0.72f))
            .testTag("mini_style_content_material_you_mini")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkAlbumView(song = song, modifier = Modifier.size(46.dp), cornerRadius = 16.dp, isPlaying = playbackInfo.isPlaying)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = colors.onAccentStrong,
                secondaryTint = colors.textPrimary,
                showPrevious = false,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext,
                playContainerColor = palette.primary,
                playContainerShape = RoundedCornerShape(14.dp)
            )
        }
        InteractiveMiniSeekbar(
            activeColor = palette.primary,
            trackColor = colors.textPrimary.copy(alpha = 0.14f),
            trackHeight = 4.dp,
            touchHeight = 16.dp,
            rounded = true,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp)
        )
    }
}

@Composable
private fun EdgeToEdgeMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().testTag("mini_style_content_edge_to_edge_mini")) {
        MiniProgressStrip(progress, colors.accentStrong, colors.cardBorder.copy(alpha = 0.45f), heightDp = 2.5f)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkAlbumView(song = song, modifier = Modifier.size(44.dp), cornerRadius = 8.dp, isPlaying = playbackInfo.isPlaying)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MiniTransportButtons(
                isPlaying = playbackInfo.isPlaying,
                primaryTint = colors.accentStrong,
                secondaryTint = colors.textPrimary,
                showPrevious = true,
                onPlayPrevious = onPlayPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onPlayNext = onPlayNext
            )
        }
    }
}

@Composable
private fun FloatingCornerMiniContent(
    song: Song,
    playbackInfo: PlaybackInfo,
    progress: Float,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(androidx.compose.foundation.layout.IntrinsicSize.Max)
            .testTag("mini_style_content_floating_corner_player")
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = colors.cardBorder.copy(alpha = 0.45f), style = Stroke(2.5.dp.toPx()))
                    drawArc(
                        color = colors.accentStrong,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.size(34.dp).clip(CircleShape),
                    cornerRadius = 17.dp,
                    isPlaying = playbackInfo.isPlaying
                )
            }
            Column(modifier = Modifier.widthIn(min = 60.dp, max = 104.dp).weight(1f, fill = false)) {
                Text(
                    text = song.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (playbackInfo.isPlaying) "Playing" else "Paused",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 10.sp,
                    color = colors.accentStrong,
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier.size(48.dp).testTag("mini_play_pause_button")
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(colors.accentStrong, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackInfo.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackInfo.isPlaying) "Pause" else "Play",
                        tint = colors.onAccentStrong,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = onPlayNext,
                modifier = Modifier.size(48.dp).testTag("mini_skip_next_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next Track",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        InteractiveMiniSeekbar(
            activeColor = colors.accentStrong,
            trackColor = colors.cardBorder.copy(alpha = 0.4f),
            trackHeight = 2.5.dp,
            touchHeight = 14.dp,
            rounded = true,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 4.dp)
        )
    }
}
