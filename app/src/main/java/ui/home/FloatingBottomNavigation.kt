package ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ui.motion.SonoraMotionSpecs
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
 * with pastel active indicator pill, single-pass elevation (no duplicate shadow overdraw),
 * and motion-system-aligned selection transitions.
 */
@Composable
fun FloatingBottomNavigation(
    selectedIndex: Int,
    colors: EditorialColors,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pillShape = remember { RoundedCornerShape(36.dp) }
    val itemPillShape = remember { RoundedCornerShape(28.dp) }
    val motionState = LocalEffectiveMotionState.current
    val triggerHaptic = rememberMotionAwareHaptic()
    val labelDuration = motionState.durationMillis(SonoraMotionSpecs.SELECTION_MS, essential = true)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = pillShape,
            color = colors.floatingNavBackground.copy(alpha = 0.96f),
            shadowElevation = 10.dp,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
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
                        animationSpec = SonoraMotionSpecs.colorSpec(motionState, SonoraMotionSpecs.SELECTION_MS),
                        label = "nav_pill_${tab.label}"
                    )
                    val contentTint by animateColorAsState(
                        targetValue = if (isSelected) colors.floatingNavActiveTint else colors.floatingNavInactiveTint,
                        animationSpec = SonoraMotionSpecs.colorSpec(motionState, SonoraMotionSpecs.SELECTION_MS),
                        label = "nav_tint_${tab.label}"
                    )
                    val iconScale = animateFloatAsState(
                        targetValue = if (isSelected && motionState.essentialFeedbackEnabled) 1.10f else 1f,
                        animationSpec = SonoraMotionSpecs.buttonScaleSpec(motionState),
                        label = "nav_icon_scale_${tab.label}"
                    )

                    Box(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(itemPillShape)
                            .background(pillColor, itemPillShape)
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
                                        val s = iconScale.value
                                        scaleX = s
                                        scaleY = s
                                    }
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = if (!motionState.essentialFeedbackEnabled || labelDuration == 0) {
                                    fadeIn(snap())
                                } else {
                                    fadeIn(tween(labelDuration, easing = SonoraMotionSpecs.EmphasizedDecelerate)) +
                                        expandHorizontally(tween(labelDuration, easing = SonoraMotionSpecs.EmphasizedDecelerate))
                                },
                                exit = if (!motionState.essentialFeedbackEnabled || labelDuration == 0) {
                                    fadeOut(snap())
                                } else {
                                    fadeOut(tween((labelDuration * 0.8f).toInt().coerceAtLeast(90), easing = SonoraMotionSpecs.EmphasizedAccelerate)) +
                                        shrinkHorizontally(tween((labelDuration * 0.85f).toInt().coerceAtLeast(100), easing = SonoraMotionSpecs.EmphasizedAccelerate))
                                }
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
