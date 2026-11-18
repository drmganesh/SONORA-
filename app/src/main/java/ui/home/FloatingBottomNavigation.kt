package ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaFontFamily
import ui.motion.LocalEffectiveMotionState
import ui.motion.rememberMotionAwareHaptic
import ui.theme.EditorialColors

enum class EditorialNavTab(
    val index: Int,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME(0, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    SEARCH(1, "Search", Icons.Rounded.Search, Icons.Rounded.Search),
    LIBRARY(2, "Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    ONLINE(3, "Online", Icons.Rounded.Explore, Icons.Outlined.Explore)
}

/**
 * Floating pill-shaped 4-destination Bottom Navigation Bar (Home, Search, Library, Online)
 * with pastel active indicator pill and subtle drop shadow.
 */
@Composable
fun FloatingBottomNavigation(
    selectedIndex: Int,
    colors: EditorialColors,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = RoundedCornerShape(36.dp)
    val motionState = LocalEffectiveMotionState.current
    val triggerHaptic = rememberMotionAwareHaptic()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = pillShape,
            color = colors.floatingNavBackground.copy(alpha = 0.96f),
            shadowElevation = 16.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .shadow(18.dp, pillShape, ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
                .border(0.75.dp, colors.cardBorder.copy(alpha = 0.85f), pillShape)
                .testTag("floating_bottom_navigation")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorialNavTab.entries.forEach { tab ->
                    val isSelected = selectedIndex == tab.index
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) colors.floatingNavIndicator else Color.Transparent,
                        animationSpec = if (!motionState.animationsEnabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_pill_${tab.label}"
                    )
                    val contentTint by animateColorAsState(
                        targetValue = if (isSelected) colors.floatingNavActiveTint else colors.floatingNavInactiveTint,
                        animationSpec = if (!motionState.animationsEnabled) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                        label = "nav_tint_${tab.label}"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected && motionState.animationsEnabled) 1.12f else 1f,
                        animationSpec = if (!motionState.animationsEnabled) {
                            snap()
                        } else {
                            spring(
                                dampingRatio = motionState.config.intensity.springDampingRatio,
                                stiffness = motionState.config.intensity.springStiffness
                            )
                        },
                        label = "nav_icon_scale_${tab.label}"
                    )

                    Box(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(pillColor, RoundedCornerShape(28.dp))
                            .clickable {
                                if (!isSelected) triggerHaptic(HapticFeedbackType.TextHandleMove)
                                onSelectTab(tab.index)
                            }
                            .padding(horizontal = if (isSelected) 14.dp else 12.dp)
                            .testTag("nav_tab_${tab.label.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.label,
                                tint = contentTint,
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.label,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = contentTint,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
