package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.preferences.CircularAnimationIntensity
import data.preferences.CircularFloatingSide
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import java.util.Locale

/**
 * Settings screen for Background Playback, Circular Mini Player, Phone-Call Resume,
 * and Session Management (Restore Queue/Position & Recents App Removal).
 */
@Composable
fun BackgroundPlaybackSettingsScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val colors = LocalEditorialColors.current
    val bgSettings by viewModel.backgroundPlaybackSettings.collectAsState()
    val sessionSettings by viewModel.sessionManagementSettings.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("background_playback_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("background_settings_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Background & Session",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Playback continuity, circular player & restore",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Background Playback Card
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.Headphones,
                        title = "Background Playback",
                        colors = colors
                    ) {
                        LifecycleSwitchRow(
                            title = "Enable background playback",
                            subtitle = "Keep music playing when switching apps or locking screen",
                            checked = bgSettings.backgroundPlaybackEnabled,
                            onCheckedChange = { viewModel.setBackgroundPlaybackEnabled(it) },
                            colors = colors,
                            tag = "setting_background_playback_switch"
                        )
                        LifecycleSwitchRow(
                            title = "Resume after phone call",
                            subtitle = "Automatically resume music after call ends if it was playing",
                            checked = bgSettings.resumeAfterPhoneCall,
                            onCheckedChange = { viewModel.setResumeAfterPhoneCall(it) },
                            colors = colors,
                            tag = "setting_resume_after_call_switch"
                        )
                        LifecycleSwitchRow(
                            title = "Auto Resume on App Launch",
                            subtitle = "Resume interrupted music automatically when reopening SONORA",
                            checked = bgSettings.autoResumeOnAppLaunch,
                            onCheckedChange = { viewModel.setAutoResumeOnAppLaunch(it) },
                            colors = colors,
                            tag = "setting_auto_resume_launch_switch"
                        )
                        LifecycleSwitchRow(
                            title = "Circular Mini Player",
                            subtitle = "Swipe down on the mini player to collapse it into a floating circle",
                            checked = bgSettings.circularMiniPlayerEnabled,
                            onCheckedChange = { viewModel.setCircularMiniPlayerEnabled(it) },
                            colors = colors,
                            tag = "setting_circular_mini_player_switch"
                        )

                        if (bgSettings.circularMiniPlayerEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Floating Position",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularFloatingSide.entries.forEach { side ->
                                    val selected = bgSettings.circularFloatingSide == side
                                    ChoiceChipPill(
                                        label = side.label,
                                        selected = selected,
                                        onClick = { viewModel.setCircularFloatingSide(side) },
                                        colors = colors,
                                        tag = "setting_circular_side_${side.name.lowercase(Locale.ROOT)}"
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Circular Player Animation",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularAnimationIntensity.entries.forEach { intensity ->
                                    val selected = bgSettings.circularAnimationIntensity == intensity
                                    ChoiceChipPill(
                                        label = intensity.label,
                                        selected = selected,
                                        onClick = { viewModel.setCircularAnimationIntensity(intensity) },
                                        colors = colors,
                                        tag = "setting_circular_anim_${intensity.name.lowercase(Locale.ROOT)}"
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Session Management Card
                item {
                    SettingsSectionCard(
                        icon = Icons.Rounded.History,
                        title = "Session Management",
                        colors = colors
                    ) {
                        LifecycleSwitchRow(
                            title = "Restore previous queue",
                            subtitle = "Reload your last music queue and active track on startup",
                            checked = sessionSettings.restorePreviousQueue,
                            onCheckedChange = { viewModel.setRestorePreviousQueue(it) },
                            colors = colors,
                            tag = "setting_restore_queue_switch"
                        )
                        LifecycleSwitchRow(
                            title = "Restore playback position",
                            subtitle = "Remember the exact timestamp where you left off",
                            checked = sessionSettings.restorePlaybackPosition,
                            onCheckedChange = { viewModel.setRestorePlaybackPosition(it) },
                            colors = colors,
                            tag = "setting_restore_position_switch"
                        )
                        LifecycleSwitchRow(
                            title = "Stop playback when removed from Recents",
                            subtitle = "Stop music and remove notification when swiping SONORA away from Recent Apps",
                            checked = sessionSettings.stopPlaybackOnTaskRemoved,
                            onCheckedChange = { viewModel.setStopPlaybackOnTaskRemoved(it) },
                            colors = colors,
                            tag = "setting_stop_on_task_removed_switch"
                        )
                    }
                }

                // 3. Explanatory Info Card
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pressing Home, switching apps, or locking the screen keeps background music running. Swiping SONORA away from Recent Apps or tapping Stop Playback cleanly ends the playback session while saving your queue.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    colors: EditorialColors,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = colors.accentPill,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun LifecycleSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EditorialColors,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
                lineHeight = 16.sp,
                color = colors.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accentStrong,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceTinted
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun ChoiceChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    colors: EditorialColors,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) colors.accentStrong else colors.surfaceTinted,
        border = BorderStroke(1.dp, if (selected) colors.accentStrong else colors.cardBorder),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 12.sp,
            color = if (selected) Color.White else colors.textPrimary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        )
    }
}
