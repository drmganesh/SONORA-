package ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.home.animations.AlbumArtworkAnimator
import ui.home.animations.HomeAnimationLevel
import ui.theme.EditorialColors

/**
 * Editorial rounded/squircle album or track card with crisp typography, subtle depth shadow,
 * optional organic corner morphing, floating drift, and bounded slow zoom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EditorialArtworkCard(
    song: Song?,
    title: String,
    subtitle: String,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    artworkSize: Dp = 152.dp,
    cornerRadius: Dp = 24.dp,
    showPlayBadge: Boolean = false,
    badgeText: String? = null,
    animationLevel: HomeAnimationLevel = HomeAnimationLevel.OFF,
    screenVisible: Boolean = true,
    enableFloating: Boolean = false,
    floatingPhaseMs: Int = 0,
    floatingDurationMs: Int = 4800,
    enableSlowZoom: Boolean = false,
    enableMorph: Boolean = false,
    enableParallax: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(cornerRadius)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick?.let { cb ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cb()
                    }
                }
            )
            .padding(4.dp)
    ) {
        AlbumArtworkAnimator(
            song = song,
            contentDescription = "$title artwork",
            level = animationLevel,
            screenVisible = screenVisible,
            shape = shape,
            fallbackTint = colors.surfaceTinted,
            iconTint = colors.accentStrong,
            cornerMorphBaseDp = if (enableMorph && cornerRadius < 50.dp) cornerRadius else null,
            cornerMorphDeltaDp = if (enableMorph && cornerRadius < 50.dp) 6.dp else 0.dp,
            enableFloating = enableFloating,
            floatingAmplitudeDp = 2.6.dp,
            floatingDurationMs = floatingDurationMs,
            floatingPhaseMs = floatingPhaseMs,
            enableSlowZoom = enableSlowZoom,
            zoomRange = 0.036f,
            enableParallax = enableParallax,
            parallaxMaxOffsetDp = 4.dp,
            shadowColor = colors.shadowColor,
            shadowElevation = 10.dp,
            borderWidth = 0.75.dp,
            borderColor = colors.cardBorder,
            fallbackIconSize = 36.dp,
            modifier = Modifier.size(artworkSize),
            overlayContent = {
                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surface.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (showPlayBadge) {
                    Surface(
                        shape = CircleShape,
                        color = colors.accentPill.copy(alpha = 0.94f),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play $title",
                                tint = colors.onAccentPill,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = subtitle,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
