package ui.settings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.UserProfile
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import data.preferences.ProfileNameValidationResult
import data.preferences.UserProfilePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.UserProfileAvatar
import ui.profile.rememberProfilePhotoPickerLauncher
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Dedicated Settings → Profile screen allowing users to view and edit their local SONORA
 * profile name, change or remove their profile photo, and restore the default SONORA avatar.
 * Entirely device-local with no account, login, email, phone, or cloud sync.
 */
@Composable
fun ProfileSettingsScreen(
    userProfile: UserProfile,
    reduceAnimations: Boolean,
    onUpdateProfileName: suspend (String) -> Result<String>,
    onPickProfilePhoto: (Uri) -> Unit,
    onRemoveProfilePhoto: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    colors: EditorialColors = LocalEditorialColors.current,
    onResetPersonalProfile: (() -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var nameInput by rememberSaveable(userProfile.displayName) { mutableStateOf(userProfile.displayName) }
    var nameError by rememberSaveable { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showSavedTick by remember { mutableStateOf(false) }
    var showResetProfileDialog by remember { mutableStateOf(false) }

    val emptyErrorText = stringResource(id = R.string.onboarding_name_error_empty)
    val tooLongErrorText = stringResource(id = R.string.onboarding_name_error_too_long)

    val launchPhotoPicker = rememberProfilePhotoPickerLauncher(
        onPhotoSelected = { uri ->
            onPickProfilePhoto(uri)
            statusMessage = "Profile photo updated"
        },
        onPickerUnavailable = {
            statusMessage = "No compatible photo picker available on this device."
        }
    )

    fun saveName() {
        when (val validation = UserProfilePreferences.validateProfileName(nameInput)) {
            is ProfileNameValidationResult.Empty -> {
                nameError = emptyErrorText
            }
            is ProfileNameValidationResult.TooLong -> {
                nameError = tooLongErrorText
            }
            is ProfileNameValidationResult.Valid -> {
                nameError = null
                keyboardController?.hide()
                scope.launch {
                    val result = onUpdateProfileName(validation.trimmedName)
                    if (result.isSuccess) {
                        nameInput = validation.trimmedName
                        showSavedTick = true
                        statusMessage = "Profile name saved"
                        delay(1800L)
                        showSavedTick = false
                    } else {
                        nameError = result.exceptionOrNull()?.message ?: emptyErrorText
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("profile_settings_screen"),
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
                        .testTag("profile_settings_back_button")
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
                        text = stringResource(id = R.string.settings_profile_title),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Local ${domain.branding.AppBranding.APP_NAME} identity & avatar",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surface,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            UserProfileAvatar(
                                userProfile = userProfile,
                                colors = colors,
                                size = 104.dp,
                                borderWidth = 2.dp,
                                shadowElevation = 10.dp,
                                reduceAnimations = reduceAnimations,
                                onClick = { launchPhotoPicker() },
                                modifier = Modifier.testTag("profile_settings_avatar")
                            )

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .shadow(4.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(colors.accentStrong)
                                    .border(2.dp, colors.surface, CircleShape)
                                    .clickable { launchPhotoPicker() }
                                    .testTag("profile_settings_avatar_badge"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = "Change profile photo",
                                    tint = colors.onAccentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = userProfile.displayName,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("profile_settings_current_name")
                            )
                            Text(
                                text = if (userProfile.hasCustomPhoto) "Custom profile photo" else "Default ${domain.branding.AppBranding.APP_NAME} avatar",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textSecondary,
                                modifier = Modifier.testTag("profile_settings_avatar_status")
                            )
                        }

                        Button(
                            onClick = { launchPhotoPicker() },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_settings_change_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (userProfile.hasCustomPhoto) "Change Profile Photo" else "Add Profile Photo",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        if (userProfile.hasCustomPhoto) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onRemoveProfilePhoto()
                                        statusMessage = "Profile photo removed • Default avatar restored"
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("profile_settings_remove_photo_button")
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

                                OutlinedButton(
                                    onClick = {
                                        onRemoveProfilePhoto()
                                        statusMessage = "Default ${domain.branding.AppBranding.APP_NAME} avatar restored"
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, colors.cardBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentStrong),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("profile_settings_restore_default_avatar_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Default Avatar",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Edit Profile Name Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colors.surface,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "PROFILE NAME",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            letterSpacing = 1.1.sp,
                            color = colors.accentStrong
                        )

                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { updated ->
                                if (updated.length <= UserProfilePreferences.MAX_DISPLAY_NAME_LENGTH) {
                                    nameInput = updated
                                    if (nameError != null && updated.trim().isNotEmpty()) {
                                        nameError = null
                                    }
                                }
                            },
                            label = { Text(stringResource(id = R.string.onboarding_name_label)) },
                            singleLine = true,
                            isError = nameError != null,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = if (nameError != null) MaterialTheme.colorScheme.error else colors.accentStrong
                                )
                            },
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = nameError ?: "Shown across your ${domain.branding.AppBranding.APP_NAME} home & profile",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = if (nameError != null) MaterialTheme.colorScheme.error else colors.textSecondary,
                                        modifier = Modifier.testTag("profile_settings_name_supporting")
                                    )
                                    Text(
                                        text = "${nameInput.trim().length}/${UserProfilePreferences.MAX_DISPLAY_NAME_LENGTH}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textMuted
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { saveName() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentStrong,
                                unfocusedBorderColor = colors.cardBorder,
                                focusedLabelColor = colors.accentStrong,
                                unfocusedLabelColor = colors.textSecondary,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                cursorColor = colors.accentStrong
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_settings_name_input")
                        )

                        Button(
                            onClick = { saveName() },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentPill,
                                contentColor = colors.onAccentPill
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("profile_settings_save_name_button")
                        ) {
                            if (showSavedTick) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Saved", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Save Profile Name", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                            }
                        }

                        statusMessage?.let { msg ->
                            Text(
                                text = msg,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.accentStrong,
                                modifier = Modifier.testTag("profile_settings_status_message")
                            )
                        }
                    }
                }

                // Local-only privacy card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceTinted,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Your profile name and photo are stored privately on this device only. ${domain.branding.AppBranding.APP_NAME} never requires login, email, phone number, or cloud sync.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                if (onResetPersonalProfile != null) {
                    OutlinedButton(
                        onClick = { showResetProfileDialog = true },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("profile_settings_reset_data_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reset Personal Profile Data",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showResetProfileDialog && onResetPersonalProfile != null) {
        AlertDialog(
            onDismissRequest = { showResetProfileDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Reset Personal Profile Data?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "This removes your saved profile name and custom profile photo from this device and restores the default ${domain.branding.AppBranding.APP_NAME} avatar. Your music library, playlists, and downloads are not affected.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetProfileDialog = false
                        onResetPersonalProfile()
                        nameInput = UserProfile.DEFAULT_DISPLAY_NAME
                        nameError = null
                        statusMessage = "Personal profile data cleared"
                    },
                    modifier = Modifier.testTag("profile_settings_reset_confirm_button")
                ) {
                    Text("Reset Profile", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetProfileDialog = false }) {
                    Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}
