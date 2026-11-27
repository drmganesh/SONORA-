package ui.motion

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.model.MiniPlayerStyle
import com.example.model.PlaybackInfo
import com.example.model.PlaybackStatusSummary
import com.example.model.Song
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.SonoraViewModel
import data.preferences.MotionPreferences
import domain.festival.FestivalEffectId
import domain.festival.FestivalRenderPlan
import domain.motion.GlobalMotionConfig
import domain.motion.MotionEffectsLevel
import domain.motion.MotionIntensity
import domain.motion.resolveEffectiveMotionState
import domain.weather.DaylightPhase
import domain.weather.PrimaryAtmosphereType
import domain.weather.WeatherAtmospherePlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.FestivalParticlePool
import ui.components.WeatherParticleSimulationPool
import ui.player.PlayerGestureController
import ui.theme.HomeThemeMode
import ui.theme.LocalEditorialColors
import ui.theme.editorialColorsFor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraMotionAndPerformanceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleSong(id: Long, title: String = "Track $id"): Song = Song(
        id = id,
        title = title,
        artist = "Sonora Artist",
        album = "Sonora Album",
        albumId = 500L + id,
        durationMs = 210_000L,
        uri = Uri.parse("content://media/external/audio/media/$id"),
        dataPath = "/storage/emulated/0/Music/track_$id.mp3",
        mimeType = "audio/mpeg",
        sizeBytes = 6_400_000L
    )

    @Before
    @After
    fun resetMotionPrefs() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        MotionPreferences(app).resetToDefaults()
        com.example.data.SonoraPreferences(app).reduceAnimations = false
    }

    @Test
    fun `MotionEffectsLevel resolves Full, Reduced, Minimal, Battery Saver, Low-RAM, and System Remove Animations`() {
        val full = resolveEffectiveMotionState(
            config = GlobalMotionConfig(motionEffectsLevel = MotionEffectsLevel.FULL_MOTION),
            systemAnimatorScale = 1f
        )
        assertEquals(MotionEffectsLevel.FULL_MOTION, full.effectiveEffectsLevel)
        assertTrue(full.animationsEnabled)
        assertTrue(full.essentialFeedbackEnabled)
        assertTrue(full.decorativeMotionEnabled)
        assertTrue(full.ambientEffectsEnabled)
        assertTrue(full.allowLargeScaleMovement)

        val reduced = resolveEffectiveMotionState(
            config = GlobalMotionConfig(motionEffectsLevel = MotionEffectsLevel.REDUCED_MOTION),
            systemAnimatorScale = 1f
        )
        assertEquals(MotionEffectsLevel.REDUCED_MOTION, reduced.effectiveEffectsLevel)
        assertTrue(reduced.animationsEnabled)
        assertTrue(reduced.essentialFeedbackEnabled)
        assertFalse(reduced.decorativeMotionEnabled)
        assertTrue(reduced.ambientEffectsEnabled)
        assertFalse(reduced.allowLargeScaleMovement)
        assertTrue(reduced.durationMillis(280) < 280)

        val minimal = resolveEffectiveMotionState(
            config = GlobalMotionConfig(motionEffectsLevel = MotionEffectsLevel.MINIMAL_MOTION),
            systemAnimatorScale = 1f
        )
        assertEquals(MotionEffectsLevel.MINIMAL_MOTION, minimal.effectiveEffectsLevel)
        assertFalse(minimal.animationsEnabled)
        assertFalse(minimal.decorativeMotionEnabled)
        assertFalse(minimal.ambientEffectsEnabled)
        assertEquals(0, minimal.durationMillis(280, essential = false))
        assertTrue(minimal.durationMillis(120, essential = true) in 0..110)

        // System Remove Animations (ANIMATOR_DURATION_SCALE = 0) overrides Full Motion
        val systemZero = resolveEffectiveMotionState(
            config = GlobalMotionConfig(motionEffectsLevel = MotionEffectsLevel.FULL_MOTION),
            systemAnimatorScale = 0f
        )
        assertEquals(MotionEffectsLevel.MINIMAL_MOTION, systemZero.effectiveEffectsLevel)
        assertFalse(systemZero.animationsEnabled)
        assertFalse(systemZero.essentialFeedbackEnabled)
        assertFalse(systemZero.ambientEffectsEnabled)
        assertEquals(0, systemZero.durationMillis(280, essential = true))

        // Battery Saver automatically steps Full Motion down to Reduced Motion
        val batterySaver = resolveEffectiveMotionState(
            config = GlobalMotionConfig(motionEffectsLevel = MotionEffectsLevel.FULL_MOTION),
            systemAnimatorScale = 1f,
            isPowerSaveMode = true
        )
        assertEquals(MotionEffectsLevel.REDUCED_MOTION, batterySaver.effectiveEffectsLevel)
        assertFalse(batterySaver.decorativeMotionEnabled)
        assertTrue(batterySaver.isPowerSaveMode)

        // Low-RAM device disables decorative motion while keeping smooth essential feedback
        val lowRam = resolveEffectiveMotionState(
            config = GlobalMotionConfig(motionEffectsLevel = MotionEffectsLevel.FULL_MOTION),
            systemAnimatorScale = 1f,
            isLowRamDevice = true
        )
        assertEquals(MotionEffectsLevel.REDUCED_MOTION, lowRam.effectiveEffectsLevel)
        assertFalse(lowRam.decorativeMotionEnabled)
        assertTrue(lowRam.essentialFeedbackEnabled)

        // Touch Exploration / TalkBack softens large-scale motion
        val talkBack = resolveEffectiveMotionState(
            config = GlobalMotionConfig(motionEffectsLevel = MotionEffectsLevel.FULL_MOTION),
            systemAnimatorScale = 1f,
            isTouchExplorationEnabled = true
        )
        assertEquals(MotionEffectsLevel.REDUCED_MOTION, talkBack.effectiveEffectsLevel)
        assertFalse(talkBack.allowLargeScaleMovement)
    }

    @Test
    fun `SonoraMotionSpecs duration tokens stay within calibrated natural ranges`() {
        assertTrue(SonoraMotionSpecs.BUTTON_FEEDBACK_MS in 100..160)
        assertTrue(SonoraMotionSpecs.SMALL_UI_MS in 150..220)
        assertTrue(SonoraMotionSpecs.SELECTION_MS in 150..220)
        assertTrue(SonoraMotionSpecs.CARD_CONTENT_MS in 200..300)
        assertTrue(SonoraMotionSpecs.SCREEN_ENTER_MS in 250..350)
        assertTrue(SonoraMotionSpecs.PLAYER_EXPAND_MS in 250..350)
    }

    @Test
    fun `NavigationDebounceGuard blocks rapid duplicate taps and allows after cooldown`() {
        var fakeClock = 1_000L
        val guard = NavigationDebounceGuard(cooldownMs = 260L, clockMs = { fakeClock })
        var navigations = 0

        assertTrue(guard.tryNavigate { navigations++ })
        // Duplicate taps within cooldown window are ignored
        fakeClock += 50L
        assertFalse(guard.tryNavigate { navigations++ })
        fakeClock += 150L
        assertFalse(guard.tryNavigate { navigations++ })
        assertEquals(1, navigations)

        // Once cooldown expires, navigation is accepted again
        fakeClock += 100L
        assertTrue(guard.tryNavigate { navigations++ })
        assertEquals(2, navigations)
    }

    @Test
    fun `PlaybackStatusSummary eliminates browsing-screen emissions across 1000 playback progress ticks`() = runBlocking {
        val rawPlaybackFlow = MutableStateFlow(
            PlaybackInfo(isPlaying = true, currentPositionMs = 0L, durationMs = 240_000L)
        )
        val summaryEmissions = mutableListOf<PlaybackStatusSummary>()
        val collectJob = launch(Dispatchers.Unconfined) {
            rawPlaybackFlow
                .map { it.toStatusSummary() }
                .distinctUntilChanged()
                .collect { summaryEmissions.add(it) }
        }

        val startNs = System.nanoTime()
        for (tick in 1..1_000) {
            rawPlaybackFlow.value = rawPlaybackFlow.value.copy(
                currentPositionMs = tick * 200L,
                bufferedPositionMs = tick * 240L
            )
        }
        val elapsedUs = (System.nanoTime() - startNs) / 1_000L

        // 1,000 high-frequency position ticks produce ZERO extra browsing-screen emissions!
        assertEquals(1, summaryEmissions.size)
        assertTrue(summaryEmissions.first().isPlaying)

        // Real play/pause state change still emits immediately
        rawPlaybackFlow.value = rawPlaybackFlow.value.copy(isPlaying = false)
        assertEquals(2, summaryEmissions.size)
        assertFalse(summaryEmissions.last().isPlaying)

        collectJob.cancel()
        println("[PERF_MEASURE] PlaybackStatusSummary projection across 1,000 ticks: ${elapsedUs}us (emissions: 1000 raw -> 1 distinct)")
    }

    @Test
    fun `Weather and Festival zero-allocation particle pools execute 1000 frame steps well under frame budget`() {
        val weatherPool = WeatherParticleSimulationPool(capacity = 64)
        val rainPlan = WeatherAtmospherePlan.INACTIVE.copy(
            isActive = true,
            primary = PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN,
            rainDensityFactor = 0.85f,
            windVectorX = 0.35f,
            windowDropletsEnabled = true,
            summaryLabel = "Rain"
        )
        val t0 = System.nanoTime()
        repeat(1_000) {
            weatherPool.step(deltaSec = 0.016f, plan = rainPlan, speedMultiplier = 1f)
        }
        val weatherAvgUs = (System.nanoTime() - t0) / 1_000_000.0 // total ns / 1000 steps / 1000 ns/us = us/frame

        val festivalPool = FestivalParticlePool(capacity = 40)
        val diwaliPlan = FestivalRenderPlan.INACTIVE.copy(
            isActive = true,
            effectId = FestivalEffectId.DIWALI_FIREWORKS,
            particleLimit = 40,
            speedMultiplier = 1f,
            opacityScale = 0.65f,
            minFrameIntervalNanos = 16_666_666L,
            isStaticReducedMotion = false
        )
        val t1 = System.nanoTime()
        repeat(1_000) {
            festivalPool.step(deltaSec = 0.016f, plan = diwaliPlan)
        }
        val festivalAvgUs = (System.nanoTime() - t1) / 1_000_000.0 // us/frame

        // Each simulated frame step should complete far below 1ms (1000us) on JVM
        assertTrue("Weather step average should be < 1000us/frame, was ${weatherAvgUs}us", weatherAvgUs < 1_000.0)
        assertTrue("Festival step average should be < 1000us/frame, was ${festivalAvgUs}us", festivalAvgUs < 1_000.0)
        println(
            "[PERF_MEASURE] WeatherPool(64) 1000 steps avg=${"%.3f".format(weatherAvgUs)}us/frame | " +
                "FestivalPool(40) 1000 steps avg=${"%.3f".format(festivalAvgUs)}us/frame"
        )
    }

    @Test
    fun `PlayerGestureController isolates multi-touch pinch from single-finger artwork drag offset`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = PlayerGestureController(app, scope)
            controller.updateArtworkDragOffset(64f)
            assertEquals(64f, controller.artworkDragOffsetX, 0.01f)

            controller.setMultiTouchPinchActive(true)
            assertTrue(controller.isMultiTouchPinchActive)
            assertEquals(0f, controller.artworkDragOffsetX, 0.01f)

            controller.updateZoomScale(1.4f)
            assertEquals(1.4f, controller.artworkZoomScale, 0.01f)

            controller.setMultiTouchPinchActive(false)
            assertFalse(controller.isMultiTouchPinchActive)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `Queue removeFromQueue and insertIntoQueueAt restores removed song at exact index for Undo`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        val s1 = sampleSong(1L, "Opening Track")
        val s2 = sampleSong(2L, "Second Track")
        val s3 = sampleSong(3L, "Third Track")

        vm.replaceQueue(listOf(s1, s2, s3), startIndex = 0)
        assertEquals(listOf(1L, 2L, 3L), vm.queue.value.map { it.id })

        vm.removeFromQueue(1)
        assertEquals(listOf(1L, 3L), vm.queue.value.map { it.id })

        vm.insertIntoQueueAt(1, s2)
        assertEquals(listOf(1L, 2L, 3L), vm.queue.value.map { it.id })
    }

    @Test
    fun `Settings Appearance Motion Effects persists Full, Reduced, and Minimal Motion`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)
        val colors = editorialColorsFor(
            mode = HomeThemeMode.WARM_CREAM,
            customAccentArgb = null,
            dynamicPalette = ui.theme.DynamicColorPalette.default()
        )

        assertEquals(MotionEffectsLevel.FULL_MOTION, vm.motionConfig.value.motionEffectsLevel)

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides colors) {
                SettingsScreen(
                    viewModel = vm,
                    onOpenNowPlaying = {},
                    onOpenEqualizer = {},
                    onOpenAudioStudio = {},
                    onOpenFolderManagement = {},
                    onOpenDownloadManager = {},
                    onOpenOnlineMusic = {},
                    onOpenOnlineLibrary = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("settings_motion_effects_group"))
        composeRule.onNodeWithTag("settings_motion_effects_full_motion").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_motion_effects_reduced_motion").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_motion_effects_minimal_motion").assertIsDisplayed()

        composeRule.onNodeWithTag("settings_motion_effects_reduced_motion").performClick()
        composeRule.waitForIdle()
        assertEquals(MotionEffectsLevel.REDUCED_MOTION, vm.motionConfig.value.motionEffectsLevel)

        composeRule.onNodeWithTag("settings_motion_effects_minimal_motion").performClick()
        composeRule.waitForIdle()
        assertEquals(MotionEffectsLevel.MINIMAL_MOTION, vm.motionConfig.value.motionEffectsLevel)
        assertEquals(MotionIntensity.OFF, vm.motionConfig.value.intensity)
        assertTrue(vm.reduceAnimations.value)

        val persisted = runBlocking { MotionPreferences(app).motionConfigFlow.first() }
        assertEquals(MotionEffectsLevel.MINIMAL_MOTION, persisted.motionEffectsLevel)
    }

    @Test
    fun `MotionPreferences default motionEffectsLevel is Full Motion`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val cfg = MotionPreferences(app).motionConfigFlow.first()
        assertEquals(MotionEffectsLevel.FULL_MOTION, cfg.motionEffectsLevel)
        assertNotNull(MiniPlayerStyle.COMPACT_BAR)
    }
}
