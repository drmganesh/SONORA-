package ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MotionPhotosOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DynamicAppearanceMode
import com.example.model.DynamicColorIntensity
import com.example.model.PlayerStyle
import com.example.model.UserProfile
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.UserProfileAvatar
import ui.theme.EditorialColors

/**
 * Interactive User Profile Bottom Sheet triggered by tapping the Home Screen header avatar.
 * Supports uploading/changing/removing the profile photo via Android Photo Picker,
 * editing display name and optional username, and accessing personalization settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileSheet(
    userProfile: UserProfile,
    colors: EditorialColors,
    appearanceMode: DynamicAppearanceMode,
    colorIntensity: DynamicColorIntensity,
    playerStyle: PlayerStyle,
    reduceAnimations: Boolean,
    onDismiss: () -> Unit,
    onPickPhotoUri: (android.net.Uri) -> Unit,
    onRemovePhoto: () -> Unit,
    onSaveProfileDetails: (displayName: String, username: String) -> Unit,
    onToggleReduceAnimations: (Boolean) -> Unit,
    onOpenDynamicColors: () -> Unit,
    onOpenPlayerStyles: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var displayNameInput by remember(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
    var usernameInput by remember(userProfile.username) { mutableStateOf(userProfile.formattedUsername) }
    var showSavedBanner by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickPhotoUri(uri)
        }
    }

    fun launchPhotoPicker() {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceElevated,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("user_profile_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Your Profile",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Personal identity & SONORA appearance",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("profile_sheet_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close profile",
                        tint = colors.textSecondary
                    )
                }
            }

            // Profile Photo Card + Upload / Change / Remove Controls
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.75.dp, colors.cardBorder, RoundedCornerShape(24.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        UserProfileAvatar(
                            userProfile = userProfile,
                            colors = colors,
                            size = 92.dp,
                            borderWidth = 2.dp,
                            shadowElevation = 10.dp,
                            reduceAnimations = reduceAnimations,
                            onClick = { launchPhotoPicker() }
                        )

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .shadow(4.dp, CircleShape)
                                .clip(CircleShape)
                                .background(colors.accentStrong)
                                .border(2.dp, colors.surface, CircleShape)
                                .clickable { launchPhotoPicker() }
                                .testTag("profile_edit_photo_badge"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Edit profile photo",
                                tint = colors.onAccentStrong,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = userProfile.displayName,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (userProfile.formattedUsername.isNotEmpty()) {
                            Text(
                                text = "@${userProfile.formattedUsername}",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                color = colors.accentStrong
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { launchPhotoPicker() },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("profile_change_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (userProfile.hasCustomPhoto) "Change Photo" else "Edit Photo",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        if (userProfile.hasCustomPhoto) {
                            OutlinedButton(
                                onClick = onRemovePhoto,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.cardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colors.textPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("profile_remove_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Remove Photo",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            // Profile Info Editor (Display Name & Optional Username)
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PROFILE DETAILS",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp,
                        color = colors.textMuted
                    )

                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentStrong,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedLabelColor = colors.accentStrong,
                        unfocusedLabelColor = colors.textSecondary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        cursorColor = colors.accentStrong
                    )

                    OutlinedTextField(
                        value = displayNameInput,
                        onValueChange = { displayNameInput = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = colors.accentStrong
                            )
                        },
                        colors = textFieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_display_name_input")
                    )

                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { usernameInput = it.replace(" ", "") },
                        label = { Text("Username (optional)") },
                        prefix = { Text("@", color = colors.textSecondary) },
                        singleLine = true,
                        colors = textFieldColors,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_username_input")
                    )

                    val hasChanges = displayNameInput.trim().ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME } != userProfile.displayName ||
                        usernameInput.trim().removePrefix("@") != userProfile.formattedUsername

                    Button(
                        onClick = {
                            onSaveProfileDetails(displayNameInput, usernameInput)
                            showSavedBanner = true
                            scope.launch {
                                delay(1800)
                                showSavedBanner = false
                            }
                        },
                        enabled = hasChanges || showSavedBanner,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPill,
                            contentColor = colors.onAccentPill,
                            disabledContainerColor = colors.surfaceTinted,
                            disabledContentColor = colors.textMuted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_save_button")
                    ) {
                        if (showSavedBanner) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Profile Saved", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Save Profile Info", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Personalization Settings Section
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "PERSONALIZATION SETTINGS",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp,
                        color = colors.textMuted
                    )

                    PersonalizationActionRow(
                        icon = Icons.Rounded.Palette,
                        title = "Dynamic Album Art Colours",
                        subtitle = "${appearanceMode.displayName} • ${colorIntensity.displayName}",
                        colors = colors,
                        tag = "profile_dynamic_colors_row",
                        onClick = {
                            onDismiss()
                            onOpenDynamicColors()
                        }
                    )

                    PersonalizationActionRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Now Playing Player Style",
                        subtitle = playerStyle.displayName,
                        colors = colors,
                        tag = "profile_player_style_row",
                        onClick = {
                            onDismiss()
                            onOpenPlayerStyles()
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(colors.surfaceTinted, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MotionPhotosOff,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Reduce Animations",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = colors.textPrimary
                                )
                                Text(
                                    text = "Minimize motion & transition effects",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }

                        Switch(
                            checked = reduceAnimations,
                            onCheckedChange = onToggleReduceAnimations,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.onAccentStrong,
                                checkedTrackColor = colors.accentStrong
                            ),
                            modifier = Modifier.testTag("profile_reduce_animations_switch")
                        )
                    }

                    PersonalizationActionRow(
                        icon = Icons.Rounded.Settings,
                        title = "All App Settings",
                        subtitle = "Audio Studio, Library folders & notifications",
                        colors = colors,
                        tag = "profile_all_settings_row",
                        onClick = {
                            onDismiss()
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalizationActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: EditorialColors,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceTinted.copy(alpha = 0.65f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onAccentPill,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
