package ui.onboarding

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.UserProfile
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import data.preferences.ProfileNameValidationResult
import data.preferences.UserProfilePreferences
import kotlinx.coroutines.launch
import ui.components.UserProfileAvatar
import ui.profile.rememberProfilePhotoPickerLauncher
import ui.splash.SonoraWaveAnimation
import ui.theme.EditorialColors

private val OnboardingAmoled = Color(0xFF000000)
private val OnboardingDeepMidnight = Color(0xFF0B131E)
private val OnboardingCardSurface = Color(0xFF101722)
private val OnboardingCardBorder = Color(0xFF233043)
private val OnboardingGoldBright = Color(0xFFFDDD6E)
private val OnboardingGoldWarm = Color(0xFFE5A424)
private val OnboardingIvory = Color(0xFFFFF8E8)
private val OnboardingTextSecondary = Color(0xFFB7C2D0)

/**
 * First-launch local profile onboarding screen for SONORA.
 * Displays the glowing SONORA emblem, warm welcome message, optional local photo picker
 * with live preview, required validated profile name field, and "Let's Start Listening 🎵" CTA.
 * Uses rememberSaveable so in-progress inputs survive process death & configuration changes.
 */
@Composable
fun FirstLaunchProfileOnboardingScreen(
    colors: EditorialColors,
    reduceAnimations: Boolean,
    onCompleteProfile: suspend (displayName: String, pickedAvatarUri: Uri?) -> Result<UserProfile>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var profileNameInput by rememberSaveable { mutableStateOf("") }
    var selectedPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var photoSkippedExplicitly by rememberSaveable { mutableStateOf(false) }
    var nameErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pickerNoticeMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var contentVisible by remember { mutableStateOf(reduceAnimations) }

    val emptyNameError = stringResource(id = R.string.onboarding_name_error_empty)
    val tooLongNameError = stringResource(id = R.string.onboarding_name_error_too_long)

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    val launchPhotoPicker = rememberProfilePhotoPickerLauncher(
        onPhotoSelected = { uri ->
            selectedPhotoUriString = uri.toString()
            photoSkippedExplicitly = false
            pickerNoticeMessage = null
        },
        onPickerUnavailable = {
            pickerNoticeMessage = "No photo picker app found. You can continue with the default SONORA avatar."
        }
    )

    val previewProfile = remember(profileNameInput, selectedPhotoUriString) {
        UserProfile(
            displayName = profileNameInput.trim().ifEmpty { UserProfile.DEFAULT_DISPLAY_NAME },
            username = "",
            avatarUri = selectedPhotoUriString
        )
    }

    fun submitProfile() {
        if (isSaving) return
        when (val validation = UserProfilePreferences.validateProfileName(profileNameInput)) {
            is ProfileNameValidationResult.Empty -> {
                nameErrorMessage = emptyNameError
                return
            }
            is ProfileNameValidationResult.TooLong -> {
                nameErrorMessage = tooLongNameError
                return
            }
            is ProfileNameValidationResult.Valid -> {
                nameErrorMessage = null
                keyboardController?.hide()
                isSaving = true
                val parsedUri = selectedPhotoUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
                scope.launch {
                    val result = onCompleteProfile(validation.trimmedName, parsedUri)
                    if (result.isFailure) {
                        isSaving = false
                        nameErrorMessage = result.exceptionOrNull()?.message ?: emptyNameError
                    }
                }
            }
        }
    }

    val waveTravel = if (!reduceAnimations) {
        val transition = rememberInfiniteTransition(label = "onboarding_wave_transition")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "onboarding_wave_travel"
        )
        progress
    } else {
        0.5f
    }

    val glowPulse = if (!reduceAnimations) {
        val transition = rememberInfiniteTransition(label = "onboarding_glow_transition")
        val pulse by transition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "onboarding_glow_pulse"
        )
        pulse
    } else {
        0.85f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        OnboardingDeepMidnight,
                        OnboardingAmoled,
                        OnboardingAmoled
                    )
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            OnboardingGoldBright.copy(alpha = 0.16f * glowPulse),
                            OnboardingGoldWarm.copy(alpha = 0.06f * glowPulse),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.15f),
                        radius = size.maxDimension * 0.45f
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.15f),
                    radius = size.maxDimension * 0.45f
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("first_launch_onboarding_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = if (reduceAnimations) {
                fadeIn(tween(0))
            } else {
                fadeIn(tween(420, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(460, easing = FastOutSlowInEasing)) { it / 16 }
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // Glowing SONORA Emblem + Brand Title + Subtle Wave
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .shadow(
                            elevation = 18.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = OnboardingGoldBright,
                            spotColor = OnboardingGoldWarm
                        )
                        .clip(RoundedCornerShape(24.dp))
                        .border(
                            width = 1.25.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    OnboardingGoldBright.copy(alpha = 0.72f),
                                    OnboardingGoldWarm.copy(alpha = 0.25f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .testTag("onboarding_sonora_logo"),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sonora_official_logo),
                        contentDescription = stringResource(id = R.string.splash_logo_content_desc),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(id = R.string.app_name),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = 4.2.sp,
                    color = OnboardingIvory,
                    textAlign = TextAlign.Center
                )

                if (!reduceAnimations) {
                    Spacer(modifier = Modifier.height(4.dp))
                    SonoraWaveAnimation(
                        visibilityAlpha = 0.85f,
                        amplitudeProgress = 0.85f,
                        travelProgress = waveTravel,
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .height(26.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Friendly Welcome Message Card
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = OnboardingCardSurface.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, OnboardingCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(id = R.string.onboarding_welcome_message),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        color = OnboardingIvory.copy(alpha = 0.95f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                            .testTag("onboarding_welcome_message")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Profile Setup Card: Rounded Avatar Preview + Photo Selector + Name Field
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = OnboardingCardSurface,
                    border = BorderStroke(1.dp, OnboardingCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Rounded Avatar Preview with quick camera badge
                        Box(contentAlignment = Alignment.BottomEnd) {
                            UserProfileAvatar(
                                userProfile = previewProfile,
                                colors = colors,
                                size = 96.dp,
                                borderWidth = 2.dp,
                                shadowElevation = 10.dp,
                                reduceAnimations = reduceAnimations,
                                onClick = { launchPhotoPicker() },
                                modifier = Modifier.testTag("onboarding_avatar_preview")
                            )

                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .shadow(4.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(OnboardingGoldBright)
                                    .border(2.dp, OnboardingCardSurface, CircleShape)
                                    .clickable { launchPhotoPicker() }
                                    .testTag("onboarding_avatar_camera_badge"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CameraAlt,
                                    contentDescription = stringResource(id = R.string.onboarding_add_photo),
                                    tint = OnboardingAmoled,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        // Photo Picker & Skip / Remove Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { launchPhotoPicker() },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, OnboardingGoldBright.copy(alpha = 0.55f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = OnboardingGoldBright
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("onboarding_select_photo_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedPhotoUriString != null) {
                                        stringResource(id = R.string.onboarding_change_photo)
                                    } else {
                                        stringResource(id = R.string.onboarding_add_photo)
                                    },
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }

                            if (selectedPhotoUriString != null) {
                                TextButton(
                                    onClick = {
                                        selectedPhotoUriString = null
                                        photoSkippedExplicitly = true
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("onboarding_remove_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = null,
                                        tint = OnboardingTextSecondary,
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(id = R.string.onboarding_remove_photo),
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = OnboardingTextSecondary
                                    )
                                }
                            } else {
                                TextButton(
                                    onClick = {
                                        selectedPhotoUriString = null
                                        photoSkippedExplicitly = true
                                    },
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("onboarding_skip_photo_button")
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.onboarding_skip_photo),
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (photoSkippedExplicitly) OnboardingGoldBright else OnboardingTextSecondary
                                    )
                                }
                            }
                        }

                        if (photoSkippedExplicitly && selectedPhotoUriString == null) {
                            Text(
                                text = "Using the default SONORA avatar — you can add a photo anytime in Settings.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = OnboardingTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.testTag("onboarding_photo_skipped_hint")
                            )
                        }

                        pickerNoticeMessage?.let { notice ->
                            Text(
                                text = notice,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = OnboardingGoldWarm,
                                textAlign = TextAlign.Center
                            )
                        }

                        // Required Profile Name Input
                        OutlinedTextField(
                            value = profileNameInput,
                            onValueChange = { updated ->
                                if (updated.length <= UserProfilePreferences.MAX_DISPLAY_NAME_LENGTH) {
                                    profileNameInput = updated
                                    if (nameErrorMessage != null && updated.trim().isNotEmpty()) {
                                        nameErrorMessage = null
                                    }
                                }
                            },
                            label = {
                                Text(
                                    text = stringResource(id = R.string.onboarding_name_label),
                                    fontFamily = PlusJakartaFontFamily
                                )
                            },
                            placeholder = {
                                Text(
                                    text = stringResource(id = R.string.onboarding_name_placeholder),
                                    fontFamily = PlusJakartaFontFamily,
                                    color = OnboardingTextSecondary.copy(alpha = 0.65f)
                                )
                            },
                            singleLine = true,
                            isError = nameErrorMessage != null,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = if (nameErrorMessage != null) MaterialTheme.colorScheme.error else OnboardingGoldBright
                                )
                            },
                            supportingText = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = nameErrorMessage ?: "Required • You can edit this anytime",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = if (nameErrorMessage != null) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            OnboardingTextSecondary
                                        },
                                        modifier = Modifier.testTag("onboarding_name_supporting_text")
                                    )
                                    Text(
                                        text = "${profileNameInput.trim().length}/${UserProfilePreferences.MAX_DISPLAY_NAME_LENGTH}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = OnboardingTextSecondary
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { submitProfile() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OnboardingGoldBright,
                                unfocusedBorderColor = OnboardingCardBorder,
                                focusedLabelColor = OnboardingGoldBright,
                                unfocusedLabelColor = OnboardingTextSecondary,
                                focusedTextColor = OnboardingIvory,
                                unfocusedTextColor = OnboardingIvory,
                                cursorColor = OnboardingGoldBright,
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorLabelColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_name_input")
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        // Primary CTA: "Let's Start Listening 🎵"
                        Button(
                            onClick = { submitProfile() },
                            enabled = !isSaving,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OnboardingGoldBright,
                                contentColor = OnboardingAmoled,
                                disabledContainerColor = OnboardingGoldWarm.copy(alpha = 0.5f),
                                disabledContentColor = OnboardingAmoled
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("onboarding_start_listening_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    color = OnboardingAmoled,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    text = stringResource(id = R.string.onboarding_start_listening_cta),
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Local-only privacy reassurance
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = OnboardingTextSecondary.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(id = R.string.onboarding_local_only_hint),
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = OnboardingTextSecondary.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
