package ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.onboarding.MusicTasteArtist
import domain.onboarding.MusicTasteArtistDiscoveryUiState
import domain.onboarding.MusicTasteDiscoverySessionUiState
import domain.onboarding.MusicTasteDiscoveryTrack
import domain.onboarding.MusicTasteEditLaunchMode
import domain.onboarding.MusicTastePreviewState
import domain.onboarding.MusicTasteProfile
import domain.onboarding.MusicTasteStep
import domain.onboarding.MusicTasteSwipeDecision
import ui.theme.EditorialColors

/**
 * Full-screen Personalized Music Taste Onboarding flow (and reusable single-step editor
 * launched from Profile -> Music Taste).
 *
 * Full onboarding flow:
 * 1. Pick Artists You Love
 * 2. Choose Music Genres
 * 3. Yes or No: Discover Songs
 * 4. Welcome / Start Listening
 */
@Composable
fun MusicTasteOnboardingScreen(
    colors: EditorialColors,
    reduceAnimations: Boolean,
    launchMode: MusicTasteEditLaunchMode,
    profile: MusicTasteProfile,
    artistState: MusicTasteArtistDiscoveryUiState,
    discoveryState: MusicTasteDiscoverySessionUiState,
    previewState: MusicTastePreviewState,
    onLoadInitialArtists: (forceRefresh: Boolean) -> Unit,
    onSearchArtists: (String) -> Unit,
    onToggleArtist: (MusicTasteArtist) -> Unit,
    onClearArtists: () -> Unit,
    onToggleGenre: (String) -> Unit,
    onClearGenres: () -> Unit,
    onLoadDiscoverySession: (resetPrevious: Boolean) -> Unit,
    onDecideTrack: (MusicTasteDiscoveryTrack, MusicTasteSwipeDecision) -> Unit,
    onTogglePreview: (MusicTasteDiscoveryTrack) -> Unit,
    onStopPreview: () -> Unit,
    onPreviousTrack: () -> Unit,
    onNextTrack: () -> Unit,
    onClearDiscoverySelections: () -> Unit,
    onSkipOnboarding: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onDismissEditMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditMode = launchMode != MusicTasteEditLaunchMode.FULL_ONBOARDING
    val initialStep = when (launchMode) {
        MusicTasteEditLaunchMode.FULL_ONBOARDING -> MusicTasteStep.ARTISTS
        MusicTasteEditLaunchMode.EDIT_ARTISTS -> MusicTasteStep.ARTISTS
        MusicTasteEditLaunchMode.EDIT_GENRES -> MusicTasteStep.GENRES
        MusicTasteEditLaunchMode.RESTART_DISCOVERY -> MusicTasteStep.DISCOVER_SONGS
    }

    var currentStep by rememberSaveable(launchMode) { mutableStateOf(initialStep) }

    LaunchedEffect(currentStep, launchMode) {
        when (currentStep) {
            MusicTasteStep.ARTISTS -> onLoadInitialArtists(false)
            MusicTasteStep.DISCOVER_SONGS -> {
                onLoadDiscoverySession(launchMode == MusicTasteEditLaunchMode.RESTART_DISCOVERY)
            }
            else -> onStopPreview()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopPreview()
        }
    }

    BackHandler(enabled = true) {
        onStopPreview()
        if (isEditMode) {
            onDismissEditMode()
        } else {
            when (currentStep) {
                MusicTasteStep.ARTISTS -> onSkipOnboarding()
                MusicTasteStep.GENRES -> currentStep = MusicTasteStep.ARTISTS
                MusicTasteStep.DISCOVER_SONGS -> currentStep = MusicTasteStep.GENRES
                MusicTasteStep.WELCOME -> onCompleteOnboarding()
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
            .testTag("music_taste_onboarding_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp)
        ) {
            // Step indicator bar (shown during 3 steps in full onboarding)
            if (!isEditMode && currentStep != MusicTasteStep.WELCOME) {
                val stepIndex = when (currentStep) {
                    MusicTasteStep.ARTISTS -> 0
                    MusicTasteStep.GENRES -> 1
                    MusicTasteStep.DISCOVER_SONGS -> 2
                    MusicTasteStep.WELCOME -> 3
                }
                OnboardingStepIndicatorRow(
                    currentStepIndex = stepIndex,
                    totalSteps = 3,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            val motionState = ui.motion.LocalEffectiveMotionState.current
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (reduceAnimations) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        ui.motion.SonoraTransitions.onboardingStepTransition(
                            forward = targetState.ordinal >= initialState.ordinal,
                            motion = motionState
                        )
                    }
                },
                label = "music_taste_step_transition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { step ->
                when (step) {
                    MusicTasteStep.ARTISTS -> MusicTasteArtistStep(
                        colors = colors,
                        reduceAnimations = reduceAnimations,
                        artistState = artistState,
                        selectedArtists = profile.selectedArtists,
                        isEditMode = isEditMode,
                        onSearchQueryChange = onSearchArtists,
                        onToggleArtist = onToggleArtist,
                        onClearAll = onClearArtists,
                        onRetryLoad = { onLoadInitialArtists(true) },
                        onSkip = {
                            if (isEditMode) onDismissEditMode()
                            else currentStep = MusicTasteStep.GENRES
                        },
                        onContinue = {
                            if (isEditMode) onDismissEditMode()
                            else currentStep = MusicTasteStep.GENRES
                        }
                    )

                    MusicTasteStep.GENRES -> MusicTasteGenreStep(
                        colors = colors,
                        reduceAnimations = reduceAnimations,
                        selectedGenres = profile.selectedGenres,
                        isEditMode = isEditMode,
                        onToggleGenre = onToggleGenre,
                        onClearAll = onClearGenres,
                        onBack = { currentStep = MusicTasteStep.ARTISTS },
                        onSkip = {
                            if (isEditMode) onDismissEditMode()
                            else currentStep = MusicTasteStep.DISCOVER_SONGS
                        },
                        onContinue = {
                            if (isEditMode) onDismissEditMode()
                            else currentStep = MusicTasteStep.DISCOVER_SONGS
                        }
                    )

                    MusicTasteStep.DISCOVER_SONGS -> MusicTasteSwipeDiscoveryStep(
                        colors = colors,
                        reduceAnimations = reduceAnimations,
                        sessionState = discoveryState,
                        previewState = previewState,
                        isEditMode = isEditMode,
                        onDecide = onDecideTrack,
                        onTogglePreview = onTogglePreview,
                        onPreviousTrack = onPreviousTrack,
                        onNextTrack = onNextTrack,
                        onClearSelections = onClearDiscoverySelections,
                        onRetryLoad = { onLoadDiscoverySession(launchMode == MusicTasteEditLaunchMode.RESTART_DISCOVERY) },
                        onBack = {
                            onStopPreview()
                            currentStep = MusicTasteStep.GENRES
                        },
                        onSkip = {
                            onStopPreview()
                            if (isEditMode) onDismissEditMode()
                            else onSkipOnboarding()
                        },
                        onFinish = {
                            onStopPreview()
                            if (isEditMode) onDismissEditMode()
                            else currentStep = MusicTasteStep.WELCOME
                        }
                    )

                    MusicTasteStep.WELCOME -> MusicTasteWelcomeFinishStep(
                        colors = colors,
                        profile = profile,
                        onStartListening = onCompleteOnboarding
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingStepIndicatorRow(
    currentStepIndex: Int,
    totalSteps: Int,
    colors: EditorialColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (idx in 0 until totalSteps) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (idx <= currentStepIndex) colors.accentStrong
                        else colors.cardBorder
                    )
            )
        }
    }
}

@Composable
private fun MusicTasteWelcomeFinishStep(
    colors: EditorialColors,
    profile: MusicTasteProfile,
    onStartListening: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp)
            .testTag("music_taste_welcome_step"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.surfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = domain.branding.AppBranding.LOGO_DRAWABLE_RES),
                contentDescription = stringResource(id = R.string.splash_logo_content_desc),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(id = R.string.app_name),
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            letterSpacing = 3.2.sp,
            color = colors.accentStrong,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = stringResource(id = R.string.splash_brand_tagline),
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            letterSpacing = 0.4.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("music_taste_welcome_tagline")
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(id = R.string.music_taste_welcome_title),
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("music_taste_welcome_title")
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(id = R.string.music_taste_welcome_subtitle),
            fontFamily = PlusJakartaFontFamily,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )

        if (profile.hasAnyPreferences) {
            Spacer(modifier = Modifier.height(22.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = profile.summarySubtitle(),
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = colors.accentStrong,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = onStartListening,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentStrong,
                contentColor = colors.onAccentStrong
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("music_taste_start_listening_button")
        ) {
            Text(
                text = stringResource(id = R.string.music_taste_start_listening),
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
        }
    }
}
