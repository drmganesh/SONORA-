package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import domain.motion.GestureSensitivityLevel
import domain.motion.MotionIntensity
import domain.motion.ScreenTransitionStyle
import ui.motion.SonoraTransitions
import ui.motion.rememberEffectiveMotionState
import ui.motion.rememberMotionAwareHaptic
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

@Composable
fun MotionGesturesSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val colors = LocalEditorialColors.current
    val motionConfig by viewModel.motionConfig.collectAsState()
    val motionState = rememberEffectiveMotionState(motionConfig)
    val triggerHaptic = rememberMotionAwareHaptic()
    var previewCardIndex by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("motion_gestures_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onBack() }
                        .testTag("motion_settings_back_button")
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
                        text = "Motion & Gestures",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Transitions, swipe navigation & tactile physics",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("motion_settings_lazy_column"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (motionState.systemAnimatorScale <= 0.01f) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.accentPill.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = "Accessibility Motion Notice",
                                    tint = colors.accentStrong
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "System Reduce Motion is enabled. SONORA automatically keeps transitions instant to match your device accessibility settings.",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = colors.textPrimary
                                )
                            }
                        }
                    }
                }

                // Live Interactive Transition Preview
                item {
                    MotionSectionCard(
                        title = "LIVE TRANSITION PREVIEW",
                        subtitle = "${motionConfig.transitionStyle.label} • ${motionConfig.intensity.label} (Tap card to preview)",
                        colors = colors
                    ) {
                        AnimatedContent(
                            targetState = previewCardIndex,
                            transitionSpec = {
                                val forward = targetState >= initialState
                                SonoraTransitions.tabTransition(forward = forward, motion = motionState)
                            },
                            label = "motion_live_preview"
                        ) { index ->
                            val isSecond = index % 2 == 1
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = colors.surfaceTinted,
                                border = BorderStroke(0.8.dp, colors.cardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable {
                                        triggerHaptic(HapticFeedbackType.TextHandleMove)
                                        previewCardIndex++
                                    }
                                    .testTag("motion_preview_card")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    if (isSecond) colors.accentStrong.copy(alpha = 0.16f) else colors.accentPill.copy(alpha = 0.45f),
                                                    colors.surfaceTinted
                                                )
                                            )
                                        )
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.accentStrong),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isSecond) Icons.Rounded.AutoAwesome else Icons.Rounded.Animation,
                                            contentDescription = "Preview Icon",
                                            tint = colors.onAccentStrong
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isSecond) "Now Playing • Dynamic Motion" else "Home Discovery • Smooth Glide",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = colors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Tap to test ${motionConfig.transitionStyle.label} + ${motionConfig.intensity.label}",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Animation Intensity
                item {
                    MotionSectionCard(
                        title = "ANIMATION INTENSITY",
                        subtitle = motionConfig.intensity.description,
                        colors = colors
                    ) {
                        MotionOptionGrid(
                            items = MotionIntensity.entries.map { it to it.label },
                            selected = motionConfig.intensity,
                            tagPrefix = "motion_intensity",
                            onSelect = { intensity ->
                                triggerHaptic(HapticFeedbackType.TextHandleMove)
                                viewModel.setMotionIntensity(intensity)
                            },
                            colors = colors
                        )
                    }
                }

                // Screen Transition Style
                item {
                    MotionSectionCard(
                        title = "SCREEN TRANSITION",
                        subtitle = motionConfig.transitionStyle.description,
                        colors = colors
                    ) {
                        MotionOptionGrid(
                            items = ScreenTransitionStyle.entries.map { it to it.label },
                            selected = motionConfig.transitionStyle,
                            tagPrefix = "motion_transition",
                            onSelect = { style ->
                                triggerHaptic(HapticFeedbackType.TextHandleMove)
                                viewModel.setScreenTransitionStyle(style)
                            },
                            colors = colors
                        )
                    }
                }

                // Gesture Sensitivity
                item {
                    MotionSectionCard(
                        title = "GESTURE SENSITIVITY",
                        subtitle = motionConfig.gestureSensitivity.description,
                        colors = colors
                    ) {
                        MotionOptionGrid(
                            items = GestureSensitivityLevel.entries.map { it to it.label },
                            selected = motionConfig.gestureSensitivity,
                            tagPrefix = "motion_sensitivity",
                            onSelect = { level ->
                                triggerHaptic(HapticFeedbackType.TextHandleMove)
                                viewModel.setGestureSensitivityLevel(level)
                            },
                            colors = colors
                        )
                    }
                }

                // Feature Toggles
                item {
                    MotionSectionCard(
                        title = "MOTION & GESTURE CONTROLS",
                        subtitle = "Customize swipe navigation, transitions & haptics",
                        colors = colors
                    ) {
                        MotionToggleRow(
                            title = "Swipe navigation",
                            subtitle = "Swipe cleanly between Home Offline/Online, tabs, and Now Playing",
                            checked = motionConfig.swipeNavigationEnabled,
                            testTag = "motion_toggle_swipe_navigation",
                            onCheckedChange = { enabled ->
                                triggerHaptic(HapticFeedbackType.TextHandleMove)
                                viewModel.updateMotionConfig { it.copy(swipeNavigationEnabled = enabled) }
                            },
                            colors = colors
                        )
                        MotionToggleRow(
                            title = "Album artwork transitions",
                            subtitle = "Animate cover crossfades & title slides on track switches",
                            checked = motionConfig.artworkTransitionsEnabled,
                            testTag = "motion_toggle_artwork_transitions",
                            onCheckedChange = { enabled ->
                                triggerHaptic(HapticFeedbackType.TextHandleMove)
                                viewModel.updateMotionConfig { it.copy(artworkTransitionsEnabled = enabled) }
                            },
                            colors = colors
                        )
                        MotionToggleRow(
                            title = "Haptic feedback",
                            subtitle = "Subtle tactile notches on swipes, Equalizer & Audio Studio sliders",
                            checked = motionConfig.hapticFeedbackEnabled,
                            testTag = "motion_toggle_haptic_feedback",
                            onCheckedChange = { enabled ->
                                viewModel.updateMotionConfig { it.copy(hapticFeedbackEnabled = enabled) }
                            },
                            colors = colors
                        )
                        MotionToggleRow(
                            title = "Parallax effects",
                            subtitle = "Carousel center-focus depth & gentle artwork scroll lift",
                            checked = motionConfig.parallaxEffectsEnabled,
                            testTag = "motion_toggle_parallax_effects",
                            onCheckedChange = { enabled ->
                                triggerHaptic(HapticFeedbackType.TextHandleMove)
                                viewModel.updateMotionConfig { it.copy(parallaxEffectsEnabled = enabled) }
                            },
                            colors = colors
                        )
                        MotionToggleRow(
                            title = "Animated album colours",
                            subtitle = "Smooth palette blending across screens when track artwork changes",
                            checked = motionConfig.animatedAlbumColorsEnabled,
                            testTag = "motion_toggle_animated_colors",
                            onCheckedChange = { enabled ->
                                triggerHaptic(HapticFeedbackType.TextHandleMove)
                                viewModel.updateMotionConfig { it.copy(animatedAlbumColorsEnabled = enabled) }
                            },
                            colors = colors
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            triggerHaptic(HapticFeedbackType.TextHandleMove)
                            viewModel.resetMotionConfigToDefaults()
                        },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("motion_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RestartAlt,
                            contentDescription = "Reset Motion Defaults",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Motion & Gestures to Defaults",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MotionSectionCard(
    title: String,
    subtitle: String,
    colors: EditorialColors,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(22.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = title,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = colors.accentStrong
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun <T : Enum<T>> MotionOptionGrid(
    items: List<Pair<T, String>>,
    selected: T,
    tagPrefix: String,
    onSelect: (T) -> Unit,
    colors: EditorialColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) colors.accentStrong else colors.surfaceTinted,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(
                        width = if (isSelected) 1.5.dp else 0.75.dp,
                        color = if (isSelected) colors.accentStrong else colors.cardBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(value) }
                    .testTag("${tagPrefix}_${value.name.lowercase()}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        fontFamily = OutfitFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isSelected) colors.onAccentStrong else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
    colors: EditorialColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccentStrong,
                checkedTrackColor = colors.accentStrong
            )
        )
    }
}
