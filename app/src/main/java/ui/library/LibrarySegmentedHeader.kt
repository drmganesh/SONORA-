package ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayCircle
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import domain.library.LibrarySectionMode
import ui.theme.EditorialColors

/**
 * Premium animated segmented selector for switching between
 * [LibrarySectionMode.OFFLINE], [LibrarySectionMode.ONLINE], and [LibrarySectionMode.ONLINE_VIDEO].
 *
 * Uses dynamic album-art accent colors, spring-animated sliding pill indicator,
 * tactile haptic feedback, 48.dp+ accessible height, and light/dark theme fidelity.
 */
@Composable
fun LibraryModeSegmentedSelector(
    selectedMode: LibrarySectionMode,
    colors: EditorialColors,
    onSelectMode: (LibrarySectionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val outerShape = RoundedCornerShape(26.dp)
    val pillShape = RoundedCornerShape(22.dp)
    val modes = LibrarySectionMode.entries

    Surface(
        shape = outerShape,
        color = colors.surfaceTinted,
        border = BorderStroke(1.dp, colors.cardBorder),
        shadowElevation = if (colors.isDark) 2.dp else 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("library_mode_segmented_selector")
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            val segmentWidth = maxWidth / modes.size
            val selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0)
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "library_segmented_pill_offset"
            )

            // Animated sliding active pill
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .shadow(
                        elevation = if (colors.isDark) 6.dp else 4.dp,
                        shape = pillShape,
                        clip = false
                    )
                    .clip(pillShape)
                    .background(colors.accentStrong)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibrarySegmentItem(
                    label = LibrarySectionMode.OFFLINE.shortLabel,
                    icon = Icons.Rounded.LibraryMusic,
                    isSelected = selectedMode == LibrarySectionMode.OFFLINE,
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("library_mode_offline"),
                    onClick = {
                        if (selectedMode != LibrarySectionMode.OFFLINE) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectMode(LibrarySectionMode.OFFLINE)
                        }
                    }
                )
                LibrarySegmentItem(
                    label = LibrarySectionMode.ONLINE.shortLabel,
                    icon = Icons.Rounded.Headphones,
                    isSelected = selectedMode == LibrarySectionMode.ONLINE,
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("library_mode_online"),
                    onClick = {
                        if (selectedMode != LibrarySectionMode.ONLINE) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectMode(LibrarySectionMode.ONLINE)
                        }
                    }
                )
                LibrarySegmentItem(
                    label = LibrarySectionMode.ONLINE_VIDEO.shortLabel,
                    icon = Icons.Rounded.PlayCircle,
                    isSelected = selectedMode == LibrarySectionMode.ONLINE_VIDEO,
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("library_mode_online_video"),
                    onClick = {
                        if (selectedMode != LibrarySectionMode.ONLINE_VIDEO) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectMode(LibrarySectionMode.ONLINE_VIDEO)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LibrarySegmentItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.onAccentStrong else colors.textSecondary,
        label = "library_segment_content_color"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .semantics { selected = isSelected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            fontFamily = OutfitFontFamily,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 12.5.sp,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
