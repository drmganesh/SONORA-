package ui.splash

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SonoraSplashPhase {
    DARK_INTRO,
    LOGO_REVEAL,
    AUDIO_WAVE,
    BRAND_REVEAL,
    TRANSITION_HOME,
    COMPLETED
}

private val AmoledBlack = Color(0xFF000000)
private val DeepMidnightTint = Color(0xFF080E14)
private val BrandIvory = Color(0xFFFFF6DE)
private val BrandWarmGold = Color(0xFFE5CA94)

fun isSystemReduceMotionActive(context: Context): Boolean {
    return runCatching {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        scale == 0f
    }.getOrDefault(false)
}

/**
 * Original 5-phase cinematic SONORA launch sequence (~1.8s total) that sits non-blockingly over
 * the live SONORA Home screen, respects system & app Reduce Motion preferences, and hands off
 * smoothly without duplicating splash screens or delaying media/library initialization.
 */
@Composable
fun SonoraSplashScreen(
    appReduceAnimations: Boolean,
    onAnimationFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val reducedMotion = remember(context, appReduceAnimations) {
        appReduceAnimations || isSystemReduceMotionActive(context)
    }
    val latestOnFinished by rememberUpdatedState(onAnimationFinished)

    var phase by remember { mutableStateOf(SonoraSplashPhase.DARK_INTRO) }
    val phaseTransition = updateTransition(targetState = phase, label = "sonora_splash_phase")

    val overlayAlpha by phaseTransition.animateFloat(
        transitionSpec = {
            when {
                reducedMotion -> tween(durationMillis = 220, easing = FastOutSlowInEasing)
                targetState == SonoraSplashPhase.TRANSITION_HOME ||
                    targetState == SonoraSplashPhase.COMPLETED -> tween(durationMillis = 360, easing = FastOutSlowInEasing)
                else -> snap()
            }
        },
        label = "splash_overlay_alpha"
    ) { currentPhase ->
        if (currentPhase == SonoraSplashPhase.TRANSITION_HOME || currentPhase == SonoraSplashPhase.COMPLETED) {
            0f
        } else {
            1f
        }
    }

    val overlayScale by phaseTransition.animateFloat(
        transitionSpec = {
            if (reducedMotion) snap() else tween(durationMillis = 360, easing = FastOutSlowInEasing)
        },
        label = "splash_overlay_scale"
    ) { currentPhase ->
        if (!reducedMotion && (currentPhase == SonoraSplashPhase.TRANSITION_HOME || currentPhase == SonoraSplashPhase.COMPLETED)) {
            1.045f
        } else {
            1f
        }
    }

    val ambientGlowAlpha = remember { Animatable(if (reducedMotion) 0.45f else 0f) }
    val logoScale = remember { Animatable(if (reducedMotion) 1f else 0.84f) }
    val logoAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val ringExpansion = remember { Animatable(0f) }
    val lightSweep = remember { Animatable(-0.35f) }
    val waveAlpha = remember { Animatable(if (reducedMotion) 0f else 0f) }
    val waveAmplitude = remember { Animatable(0.15f) }
    val waveTravel = remember { Animatable(0f) }
    val letterSpacingSp = remember { Animatable(if (reducedMotion) 4.4f else 8.2f) }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            phase = SonoraSplashPhase.BRAND_REVEAL
            delay(280L)
            phase = SonoraSplashPhase.TRANSITION_HOME
            delay(230L)
            phase = SonoraSplashPhase.COMPLETED
            latestOnFinished()
            return@LaunchedEffect
        }

        // Phase 1: Dark Intro & subtle golden aura + faint baseline wave
        phase = SonoraSplashPhase.DARK_INTRO
        val glowJob = launch {
            ambientGlowAlpha.animateTo(
                targetValue = 0.65f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }
        val baselineWaveJob = launch {
            waveAlpha.animateTo(
                targetValue = 0.45f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
        delay(150L)

        // Phase 2: Official SONORA Logo Reveal + light sweep + expanding circular wave ring
        phase = SonoraSplashPhase.LOGO_REVEAL
        launch {
            logoAlpha.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(1f, tween(durationMillis = 620, easing = FastOutSlowInEasing))
        }
        launch {
            delay(140L)
            ringExpansion.animateTo(1f, tween(durationMillis = 920, easing = FastOutSlowInEasing))
        }
        launch {
            delay(180L)
            lightSweep.animateTo(1.35f, tween(durationMillis = 680, easing = FastOutSlowInEasing))
        }
        delay(320L)

        // Phase 3: Smooth Left-to-Right Audio Wave Animation
        phase = SonoraSplashPhase.AUDIO_WAVE
        launch {
            waveAlpha.animateTo(1f, tween(durationMillis = 260, easing = FastOutSlowInEasing))
        }
        launch {
            waveAmplitude.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
        launch {
            waveTravel.animateTo(1f, tween(durationMillis = 980, easing = LinearEasing))
        }
        delay(310L)

        // Phase 4: Brand Reveal ("SONORA" + "Premium Music Experience")
        phase = SonoraSplashPhase.BRAND_REVEAL
        launch {
            letterSpacingSp.animateTo(
                targetValue = 4.4f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
            )
        }
        delay(840L)

        // Phase 5: Smooth transition into live Home screen
        glowJob.cancel()
        baselineWaveJob.cancel()
        phase = SonoraSplashPhase.TRANSITION_HOME
        delay(370L)
        phase = SonoraSplashPhase.COMPLETED
        latestOnFinished()
    }

    if (phase == SonoraSplashPhase.COMPLETED) return

    val brandVisible = reducedMotion ||
        phase == SonoraSplashPhase.BRAND_REVEAL ||
        phase == SonoraSplashPhase.TRANSITION_HOME

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = overlayAlpha
                scaleX = overlayScale
                scaleY = overlayScale
            }
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        DeepMidnightTint,
                        AmoledBlack
                    )
                )
            )
            .testTag("sonora_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SonoraLogoAnimation(
                logoScale = logoScale.value,
                logoAlpha = logoAlpha.value,
                ambientGlowAlpha = ambientGlowAlpha.value,
                ringExpansionProgress = ringExpansion.value,
                lightSweepProgress = lightSweep.value,
                reducedMotion = reducedMotion
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (!reducedMotion) {
                SonoraWaveAnimation(
                    visibilityAlpha = waveAlpha.value,
                    amplitudeProgress = waveAmplitude.value,
                    travelProgress = waveTravel.value
                )
                Spacer(modifier = Modifier.height(22.dp))
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            AnimatedVisibility(
                visible = brandVisible,
                enter = if (reducedMotion) {
                    fadeIn(snap())
                } else {
                    fadeIn(animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                            initialOffsetY = { it / 4 }
                        )
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        letterSpacing = letterSpacingSp.value.sp,
                        color = BrandIvory,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("sonora_splash_brand_title")
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(id = R.string.splash_brand_tagline),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        letterSpacing = 0.8.sp,
                        color = BrandWarmGold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("sonora_splash_brand_subtitle")
                    )
                }
            }
        }
    }
}
