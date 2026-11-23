package ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import kotlin.math.abs
import ui.motion.LocalEffectiveMotionState
import ui.motion.SonoraTransitions
import ui.motion.rememberMotionAwareHaptic
import ui.theme.EditorialColors

/**
 * Multi-variant Floating Mini-Player seamlessly aligned above the Floating Bottom Navigation Bar.
 * Supports FLOATING_PILL, COMPACT_BAR, ARTWORK_FOCUS, and WAVE_MINI while preserving
 * swipe-left/right track skip and swipe-up expansion gestures.
 */
@Composable
fun FloatingMiniPlayer(
    song: Song?,
    playbackInfo: PlaybackInfo,
    colors: EditorialColors,
    onTogglePlayPause: () -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrevious: () -> Unit,
    onClick: () -> Unit,
    onSeekTo: (Long) -> Unit = {},
    onOpenLyrics: (() -> Unit)? = null,
    gesturesEnabled: Boolean = true,
    swipeDownDismissEnabled: Boolean = true,
    onSwipeDownDismiss: () -> Unit = {},
    miniPlayerStyle: MiniPlayerStyle = MiniPlayerStyle.DEFAULT,
    trackDirection: Int = 0,
    swipeThresholdDp: Int = 64,
    frequencies: FloatArray = FloatArray(0),
    queue: List<Song> = emptyList(),
    queueIndex: Int = 0,
    weatherPlan: domain.weather.WeatherAtmospherePlan = domain.weather.WeatherAtmospherePlan.INACTIVE,
    weatherSettings: data.preferences.WeatherAmbientSettings = data.preferences.WeatherAmbientSettings.DEFAULT,
    modifier: Modifier = Modifier
) {
    if (song == null) return

    val dynamicPalette = ui.theme.LocalDynamicColorPalette.current
    val motionState = LocalEffectiveMotionState.current
    val performHaptic = rememberMotionAwareHaptic()

    val progress = if (playbackInfo.durationMs > 0L) {
        (playbackInfo.currentPositionMs.toFloat() / playbackInfo.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val latestOnSeekTo by rememberUpdatedState(onSeekTo)
    var isScrubbingSeekbar by remember { mutableStateOf(false) }
    val miniSeekController = remember(playbackInfo) {
        MiniSeekController(
            playbackInfo = playbackInfo,
            onSeekTo = { targetMs -> latestOnSeekTo(targetMs) },
            onScrubbingChanged = { active -> isScrubbingSeekbar = active }
        )
    }

    val isCompactFloatingStyle = miniPlayerStyle == MiniPlayerStyle.CIRCULAR_PLAYER ||
        miniPlayerStyle == MiniPlayerStyle.FLOATING_CORNER_PLAYER
    val horizontalPad = when (miniPlayerStyle) {
        MiniPlayerStyle.EDGE_TO_EDGE_MINI -> 0.dp
        MiniPlayerStyle.COMPACT_BAR -> 14.dp
        MiniPlayerStyle.FLOATING_CORNER_PLAYER -> 16.dp
        else -> 20.dp
    }
    val shape = when (miniPlayerStyle) {
        MiniPlayerStyle.EDGE_TO_EDGE_MINI -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
        MiniPlayerStyle.COMPACT_BAR -> RoundedCornerShape(16.dp)
        MiniPlayerStyle.ARTWORK_FOCUS, MiniPlayerStyle.BLUR_CARD, MiniPlayerStyle.EXPANDABLE_CARD -> RoundedCornerShape(22.dp)
        MiniPlayerStyle.WAVE_MINI, MiniPlayerStyle.NEON_MINI, MiniPlayerStyle.CASSETTE_MINI -> RoundedCornerShape(20.dp)
        MiniPlayerStyle.CIRCULAR_PLAYER, MiniPlayerStyle.FLOATING_CORNER_PLAYER -> RoundedCornerShape(32.dp)
        MiniPlayerStyle.MATERIAL_YOU_MINI -> RoundedCornerShape(26.dp)
        else -> RoundedCornerShape(24.dp)
    }
    val surfaceColor = when (miniPlayerStyle) {
        MiniPlayerStyle.ARTWORK_FOCUS -> colors.surfaceTinted
        MiniPlayerStyle.WAVE_MINI -> colors.surfaceElevated.copy(alpha = 0.94f)
        MiniPlayerStyle.AMOLED_BAR -> androidx.compose.ui.graphics.Color.Black
        MiniPlayerStyle.NEON_MINI -> androidx.compose.ui.graphics.Color(0xFF080912)
        else -> colors.miniPlayerBackground
    }
    val sourceLabel = if (song.isOnline) "ONLINE" else "LOCAL"
    val badge = remember(song.isOnline, song.streamCodec, song.providerName, song.mimeType, song.dataPath) {
        when {
            song.isOnline -> song.streamCodec.ifBlank { song.providerName.ifBlank { "STREAM" } }.uppercase()
            song.mimeType.contains("flac", ignoreCase = true) ||
                song.dataPath.endsWith(".flac", ignoreCase = true) -> "FLAC"
            else -> "HI-RES"
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = when (miniPlayerStyle) {
            MiniPlayerStyle.FLOATING_CORNER_PLAYER -> Alignment.CenterEnd
            MiniPlayerStyle.CIRCULAR_PLAYER -> Alignment.Center
            else -> Alignment.Center
        }
    ) {
    Surface(
        modifier = Modifier
            .then(if (isCompactFloatingStyle) Modifier else Modifier.fillMaxWidth())
            .padding(horizontal = horizontalPad)
            .universalMiniPlayerGestures(
                miniPlayerStyle = miniPlayerStyle,
                gesturesEnabled = gesturesEnabled,
                swipeDownEnabled = swipeDownDismissEnabled,
                swipeThresholdDp = swipeThresholdDp,
                isScrubbingSeekbar = { isScrubbingSeekbar },
                onPlayNext = onPlayNext,
                onPlayPrevious = onPlayPrevious,
                onExpandNowPlaying = onClick,
                onSwipeDownMinimize = onSwipeDownDismiss
            )
            .shadow(
                elevation = if (miniPlayerStyle == MiniPlayerStyle.COMPACT_BAR) 10.dp else 14.dp,
                shape = shape,
                ambientColor = colors.shadowColor,
                spotColor = colors.shadowColor
            )
            .border(0.75.dp, colors.cardBorder, shape)
            .clip(shape)
            .clickable { onClick() }
            .testTag("mini_player"),
        color = surfaceColor,
        tonalElevation = 4.dp
    ) {
        val showMiniWeatherBackdrop = weatherSettings.enableMiniPlayerEffects &&
            weatherPlan.isActive &&
            domain.weather.WeatherAtmosphereEngine.supportsMiniPlayerAtmosphere(miniPlayerStyle)
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showMiniWeatherBackdrop) {
                ui.components.WeatherAtmosphereOverlay(
                    plan = weatherPlan,
                    settings = weatherSettings,
                    colors = colors,
                    surfaceRole = domain.weather.WeatherSurfaceRole.MiniPlayer(miniPlayerStyle),
                    isActive = true,
                    screenVisible = true,
                    modifier = Modifier.matchParentSize(),
                    testTagName = "mini_player_weather_atmosphere_overlay"
                )
            }
            CompositionLocalProvider(LocalMiniSeekController provides miniSeekController) {
        when (miniPlayerStyle) {
            MiniPlayerStyle.COMPACT_BAR -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                            label = "compactMiniArtwork"
                        ) { _ ->
                            ArtworkAlbumView(
                                song = song,
                                modifier = Modifier.size(38.dp),
                                cornerRadius = 10.dp,
                                isPlaying = playbackInfo.isPlaying
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                            modifier = Modifier.weight(1f),
                            label = "compactMiniMeta"
                        ) { _ ->
                            Text(
                                text = "${song.title} • ${song.artist}",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        MiniPlayerSourceBadge(sourceLabel = sourceLabel, isOnline = song.isOnline, colors = colors)
                        if (onOpenLyrics != null) {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onOpenLyrics()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mini_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Open Lyrics",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("mini_play_pause_button")
                        ) {
                            AnimatedContent(
                                targetState = playbackInfo.isPlaying,
                                transitionSpec = {
                                    if (!motionState.animationsEnabled) {
                                        fadeIn(snap()) togetherWith fadeOut(snap())
                                    } else {
                                        (scaleIn(tween(180), initialScale = 0.72f) + fadeIn(tween(160))) togetherWith
                                            (scaleOut(tween(140), targetScale = 0.72f) + fadeOut(tween(140)))
                                    }
                                },
                                label = "compactMiniPlayIcon"
                            ) { playing ->
                                Icon(
                                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playing) "Pause" else "Play",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onPlayNext()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("mini_skip_next_button")
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
                        touchHeight = 14.dp
                    )
                }
            }

            MiniPlayerStyle.ARTWORK_FOCUS -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                            label = "focusMiniArtwork"
                        ) { _ ->
                            ArtworkAlbumView(
                                song = song,
                                modifier = Modifier
                                    .size(52.dp)
                                    .border(1.dp, colors.cardBorder, RoundedCornerShape(14.dp)),
                                cornerRadius = 14.dp,
                                isPlaying = playbackInfo.isPlaying
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                            modifier = Modifier.weight(1f),
                            label = "focusMiniMeta"
                        ) { _ ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = song.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val focusSubtitle = if (playbackInfo.isBuffering && song.isOnline) {
                                        playbackInfo.statusMessage.ifBlank {
                                            playbackInfo.playbackStage.defaultMessage
                                        }.ifBlank { "Buffering audio..." }
                                    } else {
                                        "${song.artist} • $badge"
                                    }
                                    Text(
                                        text = focusSubtitle,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = if (playbackInfo.isBuffering) colors.accentStrong else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    MiniPlayerSourceBadge(sourceLabel = sourceLabel, isOnline = song.isOnline, colors = colors)
                                }
                            }
                        }
                        if (onOpenLyrics != null) {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onOpenLyrics()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mini_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Open Lyrics",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("mini_play_pause_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(colors.accentStrong, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = playbackInfo.isPlaying,
                                    transitionSpec = {
                                        if (!motionState.animationsEnabled) {
                                            fadeIn(snap()) togetherWith fadeOut(snap())
                                        } else {
                                            (scaleIn(tween(180), initialScale = 0.72f) + fadeIn(tween(160))) togetherWith
                                                (scaleOut(tween(140), targetScale = 0.72f) + fadeOut(tween(140)))
                                        }
                                    },
                                    label = "focusMiniPlayIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = colors.onAccentStrong,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onPlayNext()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("mini_skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    InteractiveMiniSeekbar(
                        activeColor = colors.accentStrong,
                        trackColor = colors.cardBorder.copy(alpha = 0.45f),
                        trackHeight = 4.dp,
                        touchHeight = 16.dp,
                        rounded = true,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 4.dp)
                    )
                }
            }

            MiniPlayerStyle.WAVE_MINI -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                            label = "waveMiniArtwork"
                        ) { _ ->
                            CircularArtwork(
                                song = song,
                                isPlaying = playbackInfo.isPlaying,
                                accentColor = colors.accentStrong,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            AnimatedContent(
                                targetState = song.id,
                                transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                                label = "waveMiniMeta"
                            ) { _ ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = song.title,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    MiniPlayerSourceBadge(sourceLabel = sourceLabel, isOnline = song.isOnline, colors = colors)
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Interactive micro waveform drag-to-seek strip
                            InteractiveMiniWaveformSeekbar(
                                activeColor = colors.accentStrong,
                                trackColor = colors.cardBorder.copy(alpha = 0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        if (onOpenLyrics != null) {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onOpenLyrics()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mini_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Open Lyrics",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("mini_play_pause_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(colors.accentPill, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = playbackInfo.isPlaying,
                                    transitionSpec = {
                                        if (!motionState.animationsEnabled) {
                                            fadeIn(snap()) togetherWith fadeOut(snap())
                                        } else {
                                            (scaleIn(tween(180), initialScale = 0.72f) + fadeIn(tween(160))) togetherWith
                                                (scaleOut(tween(140), targetScale = 0.72f) + fadeOut(tween(140)))
                                        }
                                    },
                                    label = "waveMiniPlayIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = colors.onAccentPill,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onPlayNext()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("mini_skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                }
            }

            MiniPlayerStyle.FLOATING_PILL -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.trackTransition(trackDirection, motionState) },
                            label = "pillMiniArtwork"
                        ) { _ ->
                            CircularArtwork(
                                song = song,
                                isPlaying = playbackInfo.isPlaying,
                                accentColor = colors.accentPill,
                                shadowElevation = 4.dp,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        AnimatedContent(
                            targetState = song.id,
                            transitionSpec = { SonoraTransitions.metadataTransition(trackDirection, motionState) },
                            modifier = Modifier.weight(1f),
                            label = "pillMiniMeta"
                        ) { _ ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = song.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val pillSubtitle = if (playbackInfo.isBuffering && song.isOnline) {
                                        playbackInfo.statusMessage.ifBlank {
                                            playbackInfo.playbackStage.defaultMessage
                                        }.ifBlank { "Buffering audio..." }
                                    } else {
                                        song.artist
                                    }
                                    Text(
                                        text = pillSubtitle,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = if (playbackInfo.isBuffering) colors.accentStrong else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    MiniPlayerSourceBadge(sourceLabel = sourceLabel, isOnline = song.isOnline, colors = colors)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(colors.accentPill, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = badge,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.onAccentPill
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        if (onOpenLyrics != null) {
                            IconButton(
                                onClick = {
                                    performHaptic(HapticFeedbackType.TextHandleMove)
                                    onOpenLyrics()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("mini_lyrics_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lyrics,
                                    contentDescription = "Open Lyrics",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onTogglePlayPause()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .testTag("mini_play_pause_button")
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(colors.accentPill, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                AnimatedContent(
                                    targetState = playbackInfo.isPlaying,
                                    transitionSpec = {
                                        if (!motionState.animationsEnabled) {
                                            fadeIn(snap()) togetherWith fadeOut(snap())
                                        } else {
                                            (scaleIn(tween(180), initialScale = 0.72f) + fadeIn(tween(160))) togetherWith
                                                (scaleOut(tween(140), targetScale = 0.72f) + fadeOut(tween(140)))
                                        }
                                    },
                                    label = "pillMiniPlayIcon"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (playing) "Pause" else "Play",
                                        tint = colors.onAccentPill,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                performHaptic(HapticFeedbackType.TextHandleMove)
                                onPlayNext()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("mini_skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next Track",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Interactive progress bar along bottom edge
                    InteractiveMiniSeekbar(
                        activeColor = colors.accentStrong,
                        trackColor = colors.cardBorder.copy(alpha = 0.5f),
                        trackHeight = 2.5.dp,
                        touchHeight = 14.dp
                    )
                }
            }

            else -> {
                RenderExtendedMiniPlayerStyle(
                    style = miniPlayerStyle,
                    song = song,
                    playbackInfo = playbackInfo,
                    progress = progress,
                    colors = colors,
                    palette = dynamicPalette,
                    frequencies = frequencies,
                    queue = queue,
                    queueIndex = queueIndex,
                    onTogglePlayPause = onTogglePlayPause,
                    onPlayNext = onPlayNext,
                    onPlayPrevious = onPlayPrevious,
                    onOpenLyrics = onOpenLyrics
                )
            }
        }
            }
        }
    }
    }
}

@Composable
private fun MiniPlayerSourceBadge(
    sourceLabel: String,
    isOnline: Boolean,
    colors: EditorialColors
) {
    Box(
        modifier = Modifier
            .testTag("mini_player_source_badge")
            .background(
                color = if (isOnline) colors.accentStrong.copy(alpha = 0.16f) else colors.surfaceTinted,
                shape = RoundedCornerShape(6.dp)
            )
            .border(
                width = 0.5.dp,
                color = if (isOnline) colors.accentStrong.copy(alpha = 0.45f) else colors.cardBorder,
                shape = RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(
            text = sourceLabel,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (isOnline) colors.accentStrong else colors.textSecondary
        )
    }
}
