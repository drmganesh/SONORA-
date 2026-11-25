package ui.splash

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

fun isBatterySaverActive(context: Context): Boolean {
    return runCatching {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        pm?.isPowerSaveMode == true
    }.getOrDefault(false)
}

fun isLowRamDevice(context: Context): Boolean {
    return runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    }.getOrDefault(false)
}

@Stable
private class SonoraSplashAnimationState(reducedMotion: Boolean) {
    val overlayAlpha = Animatable(1f)
    val overlayScale = Animatable(1f)
    val ambientGlowAlpha = Animatable(if (reducedMotion) 0.45f else 0f)
    val logoScale = Animatable(if (reducedMotion) 1f else 0.86f)
    val logoAlpha = Animatable(if (reducedMotion) 1f else 0f)
    val ringExpansion = Animatable(0f)
    val lightSweep = Animatable(-0.35f)
    val waveAlpha = Animatable(0f)
    val waveAmplitude = Animatable(0.15f)
    val waveTravel = Animatable(0f)
    val brandAlpha = Animatable(if (reducedMotion) 1f else 0f)
    val brandSlideProgress = Animatable(if (reducedMotion) 0f else 1f)
    val creditAlpha = Animatable(if (reducedMotion) 1f else 0f)
    val creditSlideProgress = Animatable(if (reducedMotion) 0f else 1f)
}

/**
 * Fluid, GPU-accelerated 5-phase cinematic SONORA launch sequence (~1.85s total; ~0.52s under
 * Reduce Motion or Battery Saver) that sits non-blockingly over the live SONORA Home screen.
 *
 * Performance characteristics:
 * - Zero per-frame Compose recompositions: every [Animatable] value is read exclusively inside
 *   [Modifier.graphicsLayer] or [Modifier.drawWithCache] draw lambdas.
 * - Zero per-frame text re-layout: `"SONORA"` keeps static letterSpacing and uses a subtle
 *   GPU `scaleX` tightening reveal instead of animating `letterSpacing`.
 * - Pre-decoded cached logo bitmap via [SplashLogoPreloader] (decoded off-main-thread on app start).
 * - Staged Home screen warm-up via [onPrepareHomeContent] after initial intro frames settle, and
 *   [onTransitionToHome] right as the fade-out begins so deferred background tasks start seamlessly.
 * - Respects System Reduce Motion, App Reduce Animations, Battery Saver, and Low-RAM devices.
 */
@Composable
fun SonoraSplashScreen(
    appReduceAnimations: Boolean,
    onAnimationFinished: () -> Unit,
    onPrepareHomeContent: () -> Unit = {},
    onTransitionToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val lowRam = remember(context) { isLowRamDevice(context) }
    val reducedMotion = remember(context, appReduceAnimations) {
        appReduceAnimations || isSystemReduceMotionActive(context) || isBatterySaverActive(context)
    }
    val logoBitmap = remember(context) { SplashLogoPreloader.getCachedOrDecode(context) }
    val backgroundBrush = remember {
        Brush.radialGradient(colors = listOf(DeepMidnightTint, AmoledBlack))
    }

    val latestOnFinished by rememberUpdatedState(onAnimationFinished)
    val latestOnPrepareHome by rememberUpdatedState(onPrepareHomeContent)
    val latestOnTransitionHome by rememberUpdatedState(onTransitionToHome)

    var completed by remember { mutableStateOf(false) }
    val animState = remember(reducedMotion) { SonoraSplashAnimationState(reducedMotion) }

    val brandSlideOffsetPx = remember(density) { with(density) { 16.dp.toPx() } }
    val creditSlideOffsetPx = remember(density) { with(density) { 12.dp.toPx() } }

    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            latestOnPrepareHome()
            delay(320L)
            latestOnTransitionHome()
            animState.overlayAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
            completed = true
            latestOnFinished()
            return@LaunchedEffect
        }

        // Phase 1: Dark Intro & subtle golden aura + faint baseline wave (0 -> 130ms)
        val glowJob = launch {
            animState.ambientGlowAlpha.animateTo(
                targetValue = 0.65f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            )
        }
        val baselineWaveJob = launch {
            animState.waveAlpha.animateTo(
                targetValue = 0.45f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
        }
        delay(130L)

        // Phase 2: Official SONORA Logo Reveal + expanding circular resonance ring + light sweep (130 -> 430ms)
        launch {
            animState.logoAlpha.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
        launch {
            animState.logoScale.animateTo(1f, tween(durationMillis = 540, easing = FastOutSlowInEasing))
        }
        launch {
            delay(110L)
            animState.ringExpansion.animateTo(1f, tween(durationMillis = 820, easing = FastOutSlowInEasing))
        }
        if (!lowRam) {
            launch {
                delay(150L)
                animState.lightSweep.animateTo(1.35f, tween(durationMillis = 620, easing = FastOutSlowInEasing))
            }
        }
        delay(220L)
        // Mount Home screen underneath once initial intro frames are locked in smoothly
        latestOnPrepareHome()
        delay(80L)

        // Phase 3: Smooth Left-to-Right Audio Wave Animation (430 -> 740ms)
        launch {
            animState.waveAlpha.animateTo(1f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
        }
        launch {
            animState.waveAmplitude.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
        }
        launch {
            animState.waveTravel.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
        }
        delay(310L)

        // Phase 4: Brand Reveal ("SONORA" + Tagline + Developer Credit) via pure GPU layer transforms (740 -> 1520ms)
        launch {
            animState.brandAlpha.animateTo(1f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
        }
        launch {
            animState.brandSlideProgress.animateTo(0f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
        }
        delay(180L)
        launch {
            animState.creditAlpha.animateTo(1f, tween(durationMillis = 360, easing = FastOutSlowInEasing))
        }
        launch {
            animState.creditSlideProgress.animateTo(0f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
        }
        delay(600L)

        // Phase 5: Smooth single-pass GPU fade & gentle scale-out directly into prepared Home screen (1520 -> 1850ms)
        glowJob.cancel()
        baselineWaveJob.cancel()
        latestOnTransitionHome()
        val fadeJob = launch {
            animState.overlayAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 330, easing = FastOutSlowInEasing)
            )
        }
        val scaleJob = launch {
            animState.overlayScale.animateTo(
                targetValue = 1.035f,
                animationSpec = tween(durationMillis = 330, easing = FastOutSlowInEasing)
            )
        }
        fadeJob.join()
        scaleJob.join()

        completed = true
        latestOnFinished()
    }

    if (completed) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = animState.overlayAlpha.value
                val scale = animState.overlayScale.value
                scaleX = scale
                scaleY = scale
            }
            .background(backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("sonora_splash_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                SonoraLogoAnimation(
                    logoScaleProvider = { animState.logoScale.value },
                    logoAlphaProvider = { animState.logoAlpha.value },
                    ambientGlowAlphaProvider = { animState.ambientGlowAlpha.value },
                    ringExpansionProvider = { animState.ringExpansion.value },
                    lightSweepProvider = { animState.lightSweep.value },
                    logoBitmap = logoBitmap,
                    reducedMotion = reducedMotion,
                    lowRamDevice = lowRam
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (!reducedMotion) {
                    SonoraWaveAnimation(
                        visibilityAlphaProvider = { animState.waveAlpha.value },
                        amplitudeProgressProvider = { animState.waveAmplitude.value },
                        travelProgressProvider = { animState.waveTravel.value },
                        barCount = if (lowRam) 15 else 23
                    )
                    Spacer(modifier = Modifier.height(22.dp))
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer {
                        alpha = animState.brandAlpha.value
                        val slide = animState.brandSlideProgress.value
                        translationY = slide * brandSlideOffsetPx
                        // Smooth subtle horizontal tightening without per-frame text relayout
                        scaleX = 1f + (0.045f * slide)
                    }
                ) {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 30.sp,
                        letterSpacing = 4.4.sp,
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
                        letterSpacing = 0.6.sp,
                        color = BrandWarmGold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("sonora_splash_brand_subtitle")
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Subtle, minimal developer attribution anchored responsively near bottom safe area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = animState.creditAlpha.value
                            translationY = animState.creditSlideProgress.value * creditSlideOffsetPx
                        }
                        .testTag("splash_developer_credit")
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(1.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(BrandWarmGold.copy(alpha = 0.36f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.developer_credit_label),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = BrandWarmGold.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_developer_label")
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = stringResource(id = R.string.developer_credit_name),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        letterSpacing = 0.8.sp,
                        color = BrandIvory.copy(alpha = 0.94f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_developer_name")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(id = R.string.developer_credit_qualification),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.5.sp,
                        letterSpacing = 0.4.sp,
                        color = BrandWarmGold.copy(alpha = 0.72f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("splash_developer_qualification")
                    )
                }
            }
        }
    }
}
