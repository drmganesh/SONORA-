package ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.onboarding.MusicTasteDiscoverySessionUiState
import domain.onboarding.MusicTasteDiscoveryTrack
import domain.onboarding.MusicTastePreviewState
import domain.onboarding.MusicTasteSwipeDecision
import kotlinx.coroutines.launch
import ui.theme.EditorialColors
import kotlin.math.abs
import kotlin.math.roundToInt

private const val SWIPE_HORIZONTAL_THRESHOLD_PX = 180f
private const val SWIPE_UP_THRESHOLD_PX = -170f

@Composable
fun MusicTasteSwipeDiscoveryStep(
    colors: EditorialColors,
    reduceAnimations: Boolean,
    sessionState: MusicTasteDiscoverySessionUiState,
    previewState: MusicTastePreviewState,
    isEditMode: Boolean,
    onDecide: (MusicTasteDiscoveryTrack, MusicTasteSwipeDecision) -> Unit,
    onTogglePreview: (MusicTasteDiscoveryTrack) -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onClearSelections: () -> Unit,
    onRetryLoad: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("music_taste_discovery_step")
    ) {
        // Header section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = R.string.music_taste_discover_title),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 28.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.testTag("music_taste_discover_title")
                )

                if (sessionState.totalCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.accentPill,
                        border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = if (sessionState.isSessionComplete) {
                                "${sessionState.totalCount} of ${sessionState.totalCount}"
                            } else {
                                "${sessionState.displayPosition} of ${sessionState.totalCount}"
                            },
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = colors.onAccentPill,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("music_taste_discover_progress_label")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(id = R.string.music_taste_discover_subtitle),
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = colors.textSecondary
            )

            if (sessionState.sessionDecisions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${sessionState.sessionDecisions.size} rated",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = colors.accentStrong
                    )
                    TextButton(
                        onClick = onClearSelections,
                        modifier = Modifier.testTag("music_taste_discover_clear_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.music_taste_clear_selections),
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main swipe card / loading / complete / empty area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                sessionState.isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = colors.accentStrong)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Curating real songs for your taste…",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                sessionState.tracks.isEmpty() -> {
                    DiscoverySessionEmptyState(
                        colors = colors,
                        isOffline = sessionState.isOffline,
                        errorMessage = sessionState.errorMessage,
                        onRetry = onRetryLoad
                    )
                }

                sessionState.isSessionComplete -> {
                    DiscoverySessionDoneCard(
                        colors = colors,
                        ratedCount = sessionState.sessionDecisions.size,
                        onClearAndReplay = onClearSelections,
                        onFinish = onFinish
                    )
                }

                else -> {
                    val activeTrack = sessionState.currentTrack
                    if (activeTrack != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 420.dp)
                        ) {
                            SwipeableDiscoverySongCard(
                                track = activeTrack,
                                previewState = previewState,
                                colors = colors,
                                reduceAnimations = reduceAnimations,
                                onTogglePreview = { onTogglePreview(activeTrack) },
                                onSwipeDecision = { decision -> onDecide(activeTrack, decision) },
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Action controls: Previous, Pass, Add to Liked Songs, Like, Next
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                IconButton(
                                    onClick = onPreviousTrack,
                                    enabled = sessionState.currentIndex > 0,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("music_taste_discover_prev_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Previous song",
                                        tint = if (sessionState.currentIndex > 0) colors.textPrimary else colors.textMuted
                                    )
                                }

                                // Pass button
                                Surface(
                                    onClick = { onDecide(activeTrack, MusicTasteSwipeDecision.PASS) },
                                    shape = CircleShape,
                                    color = colors.surfaceElevated,
                                    border = BorderStroke(1.5.dp, Color(0xFFE05263).copy(alpha = 0.65f)),
                                    modifier = Modifier
                                        .size(56.dp)
                                        .testTag("music_taste_discover_pass_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(id = R.string.music_taste_pass),
                                            tint = Color(0xFFE05263),
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                // Add to Liked Songs button (Swipe Up equivalent)
                                Surface(
                                    onClick = { onDecide(activeTrack, MusicTasteSwipeDecision.ADD_TO_LIKED_SONGS) },
                                    shape = CircleShape,
                                    color = colors.accentStrong,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .testTag("music_taste_discover_liked_songs_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Favorite,
                                            contentDescription = stringResource(id = R.string.music_taste_add_to_liked_songs),
                                            tint = colors.onAccentStrong,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                // Like / Keep button (Swipe Right equivalent)
                                Surface(
                                    onClick = { onDecide(activeTrack, MusicTasteSwipeDecision.LIKE) },
                                    shape = CircleShape,
                                    color = colors.surfaceElevated,
                                    border = BorderStroke(1.5.dp, Color(0xFF2ECC71).copy(alpha = 0.7f)),
                                    modifier = Modifier
                                        .size(56.dp)
                                        .testTag("music_taste_discover_like_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.ThumbUp,
                                            contentDescription = stringResource(id = R.string.music_taste_like),
                                            tint = Color(0xFF2ECC71),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onNextTrack,
                                    enabled = sessionState.currentIndex < sessionState.tracks.lastIndex,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("music_taste_discover_next_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = "Next song",
                                        tint = if (sessionState.currentIndex < sessionState.tracks.lastIndex) colors.textPrimary else colors.textMuted
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(0.76f),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Pass",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.5.sp,
                                    color = colors.textMuted
                                )
                                Text(
                                    text = "Add to Liked Songs",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    color = colors.accentStrong
                                )
                                Text(
                                    text = "Keep",
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

        // Bottom CTA footer: Back, Skip, Finish
        Surface(
            color = colors.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isEditMode) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("music_taste_discover_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.music_taste_back),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("music_taste_discover_skip_button")
                ) {
                    Text(
                        text = if (isEditMode) stringResource(id = R.string.music_taste_close)
                        else stringResource(id = R.string.music_taste_skip),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                }

                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("music_taste_discover_finish_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.music_taste_finish),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeableDiscoverySongCard(
    track: MusicTasteDiscoveryTrack,
    previewState: MusicTastePreviewState,
    colors: EditorialColors,
    reduceAnimations: Boolean,
    onTogglePreview: () -> Unit,
    onSwipeDecision: (MusicTasteSwipeDecision) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(track.normalizedUrl) { Animatable(0f) }
    val offsetY = remember(track.normalizedUrl) { Animatable(0f) }
    var isCommittingDecision by remember(track.normalizedUrl) { mutableStateOf(false) }

    LaunchedEffect(track.normalizedUrl) {
        isCommittingDecision = false
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
    }

    val isActivePreview = previewState.activeTrackUrl == track.normalizedUrl
    val isPlayingPreview = isActivePreview && previewState.isPlaying
    val isLoadingPreview = isActivePreview && previewState.isLoading

    // Derive badge state so dragging doesn't recompose the entire card on every 1px delta
    val swipeBadgeLabel by remember(track.normalizedUrl) {
        androidx.compose.runtime.derivedStateOf {
            val x = offsetX.value
            val y = offsetY.value
            when {
                y < -90f && abs(y) > abs(x) -> "ADD TO LIKED SONGS"
                x > 85f -> "KEEP"
                x < -85f -> "PASS"
                else -> null
            }
        }
    }
    val swipeBadgeColor = when (swipeBadgeLabel) {
        "ADD TO LIKED SONGS" -> colors.accentStrong
        "KEEP" -> Color(0xFF2ECC71)
        "PASS" -> Color(0xFFE05263)
        else -> Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.25.dp, colors.cardBorder),
        shadowElevation = 8.dp,
        modifier = modifier
            .graphicsLayer {
                val x = offsetX.value
                val y = offsetY.value
                translationX = x
                translationY = y
                rotationZ = if (reduceAnimations) 0f else (x / 32f).coerceIn(-12f, 12f)
            }
            .pointerInput(track.normalizedUrl) {
                detectDragGestures(
                    onDragEnd = {
                        if (isCommittingDecision) return@detectDragGestures
                        scope.launch {
                            val x = offsetX.value
                            val y = offsetY.value
                            when {
                                y <= SWIPE_UP_THRESHOLD_PX && abs(y) > abs(x) -> {
                                    isCommittingDecision = true
                                    if (!reduceAnimations) {
                                        offsetY.animateTo(-700f, tween(180))
                                    }
                                    onSwipeDecision(MusicTasteSwipeDecision.ADD_TO_LIKED_SONGS)
                                }
                                x >= SWIPE_HORIZONTAL_THRESHOLD_PX -> {
                                    isCommittingDecision = true
                                    if (!reduceAnimations) {
                                        offsetX.animateTo(800f, tween(180))
                                    }
                                    onSwipeDecision(MusicTasteSwipeDecision.LIKE)
                                }
                                x <= -SWIPE_HORIZONTAL_THRESHOLD_PX -> {
                                    isCommittingDecision = true
                                    if (!reduceAnimations) {
                                        offsetX.animateTo(-800f, tween(180))
                                    }
                                    onSwipeDecision(MusicTasteSwipeDecision.PASS)
                                }
                                else -> {
                                    if (reduceAnimations) {
                                        offsetX.snapTo(0f)
                                        offsetY.snapTo(0f)
                                    } else {
                                        launch { offsetX.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)) }
                                        launch { offsetY.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)) }
                                    }
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        if (isCommittingDecision) return@detectDragGestures
                        scope.launch {
                            offsetX.snapTo(0f)
                            offsetY.snapTo(0f)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (isCommittingDecision) return@detectDragGestures
                        change.consume()
                        scope.launch {
                            offsetX.snapTo(offsetX.value + dragAmount.x)
                            offsetY.snapTo((offsetY.value + dragAmount.y).coerceAtMost(90f))
                        }
                    }
                )
            }
            .testTag("music_taste_discover_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Reason pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.accentPill,
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text(
                    text = track.reasonLabel,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp,
                    color = colors.onAccentPill,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("music_taste_discover_reason_label")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Album artwork + tap-to-preview play/pause overlay + swipe indicator stamp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.12f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceTinted)
                    .clickable(onClick = onTogglePreview)
                    .testTag("music_taste_discover_artwork_preview_button"),
                contentAlignment = Alignment.Center
            ) {
                if (!track.artworkUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = track.artworkUrl,
                        contentDescription = "${track.title} album artwork",
                        contentScale = ContentScale.Crop,
                        loading = { TrackArtworkPlaceholder(colors) },
                        error = { TrackArtworkPlaceholder(colors) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    TrackArtworkPlaceholder(colors)
                }

                // Subtle bottom scrim for play button contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.45f)
                                )
                            )
                        )
                )

                // Play/Pause Preview center pill
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.62f),
                    border = BorderStroke(1.25.dp, Color.White.copy(alpha = 0.7f)),
                    modifier = Modifier.size(62.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoadingPreview) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(26.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (isPlayingPreview) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlayingPreview) "Pause preview" else "Play preview",
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                // Swipe directional badge
                val activeBadgeLabel = swipeBadgeLabel
                if (activeBadgeLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = swipeBadgeColor,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 14.dp)
                    ) {
                        Text(
                            text = activeBadgeLabel,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            letterSpacing = 1.2.sp,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Song title & artist names
            Text(
                text = track.title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 25.sp,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("music_taste_discover_song_title")
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = track.artist,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("music_taste_discover_song_artist")
            )

            // Preview progress bar when playing / active
            if (isActivePreview) {
                Spacer(modifier = Modifier.height(10.dp))
                if (!previewState.errorMessage.isNullOrBlank()) {
                    Text(
                        text = previewState.errorMessage,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = Color(0xFFE05263)
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { previewState.progressFraction },
                        color = colors.accentStrong,
                        trackColor = colors.cardBorder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .testTag("music_taste_discover_preview_progress")
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackArtworkPlaceholder(colors: EditorialColors) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(colors.surfaceTinted, colors.surface)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = colors.accentStrong,
            modifier = Modifier.size(56.dp)
        )
    }
}

@Composable
private fun DiscoverySessionDoneCard(
    colors: EditorialColors,
    ratedCount: Int,
    onClearAndReplay: () -> Unit,
    onFinish: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp)
            .testTag("music_taste_discover_complete_card")
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = colors.accentStrong,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Discovery round complete",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "You rated $ratedCount songs. ${domain.branding.AppBranding.APP_NAME} will tune your recommendations right away.",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onClearAndReplay,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Rate Again",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onFinish,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Continue",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverySessionEmptyState(
    colors: EditorialColors,
    isOffline: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .testTag("music_taste_discover_empty_state")
    ) {
        Icon(
            imageVector = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isOffline) "You're offline" else "No discovery songs available right now",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = errorMessage ?: "Tap Finish to start listening — you can run Song Discovery anytime from Profile → Music Taste.",
            fontFamily = PlusJakartaFontFamily,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentStrong),
            modifier = Modifier
                .height(48.dp)
                .testTag("music_taste_discover_retry_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Try Again",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
