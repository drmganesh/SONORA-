package ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Album
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.OrganicArtworkCard
import ui.components.OrganicPebbleShape
import ui.home.animations.HomeAnimationLevel
import ui.home.animations.rememberShouldAnimateHomeMotion
import ui.theme.EditorialColors

/**
 * Large expressive Featured Music showcase card highlighting a real spotlight Album
 * from the user's local library with animated organic artwork, metadata, Play, and Shuffle buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeaturedMusicSection(
    featuredAlbum: Album?,
    representativeSong: Song?,
    colors: EditorialColors,
    onOpenAlbum: (Album) -> Unit,
    onPlayAlbum: (Album) -> Unit,
    onShuffleAlbum: (Album) -> Unit,
    onLongClick: ((Song) -> Unit)? = null,
    animationLevel: HomeAnimationLevel = HomeAnimationLevel.BALANCED,
    screenVisible: Boolean = true,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (featuredAlbum == null || representativeSong == null) return
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(30.dp)
    val motionEnabled = rememberShouldAnimateHomeMotion(level = animationLevel, screenVisible = screenVisible)

    val playInteraction = remember { MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (playPressed && motionEnabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "featured_play_scale"
    )

    val shuffleInteraction = remember { MutableInteractionSource() }
    val shufflePressed by shuffleInteraction.collectIsPressedAsState()
    val shuffleScale by animateFloatAsState(
        targetValue = if (shufflePressed && motionEnabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "featured_shuffle_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("featured_music_section")
    ) {
        EditorialSectionTitle(
            title = "Featured Music",
            subtitle = "Album spotlight from your collection",
            colors = colors
        )

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
            shape = cardShape,
            color = colors.surface,
            shadowElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .shadow(12.dp, cardShape, ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
                .clip(cardShape)
                .border(0.75.dp, colors.cardBorder, cardShape)
                .combinedClickable(
                    onClick = { onOpenAlbum(featuredAlbum) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick?.invoke(representativeSong)
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(colors.surface, colors.surfaceTinted)
                        )
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OrganicArtworkCard(
                        song = representativeSong,
                        contentDescription = "${featuredAlbum.title} artwork",
                        shape = OrganicPebbleShape,
                        fallbackTint = colors.accentPill,
                        iconTint = colors.onAccentPill,
                        shadowColor = colors.shadowColor,
                        shadowElevation = 10.dp,
                        animationLevel = animationLevel,
                        screenVisible = screenVisible,
                        enableFloating = true,
                        floatingAmplitudeDp = 3.2.dp,
                        floatingDurationMs = 5000,
                        floatingPhaseMs = 450,
                        enableSlowZoom = true,
                        enableParallax = true,
                        decorativeRotation = false,
                        isPlaying = isPlaying,
                        onClick = { onOpenAlbum(featuredAlbum) },
                        onLongClick = { onLongClick?.invoke(representativeSong) },
                        modifier = Modifier.size(116.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .background(colors.accentPill, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (featuredAlbum.year > 0) "ALBUM • ${featuredAlbum.year}" else "FEATURED ALBUM",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = colors.onAccentPill
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = featuredAlbum.title,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 21.sp,
                            lineHeight = 25.sp,
                            color = colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${featuredAlbum.artist} • ${featuredAlbum.songCount} tracks",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onPlayAlbum(featuredAlbum) },
                        interactionSource = playInteraction,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.onAccentStrong
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = playScale
                                scaleY = playScale
                            }
                            .testTag("featured_play_button")
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onShuffleAlbum(featuredAlbum) },
                        interactionSource = shuffleInteraction,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPill,
                            contentColor = colors.onAccentPill
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .graphicsLayer {
                                scaleX = shuffleScale
                                scaleY = shuffleScale
                            }
                            .testTag("featured_shuffle_button")
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
