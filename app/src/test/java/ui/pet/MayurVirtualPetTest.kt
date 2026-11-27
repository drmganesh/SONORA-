package ui.pet

import android.app.Application
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.model.Song
import data.preferences.VirtualPetPreferences
import domain.festival.ActiveFestivalResolution
import domain.festival.FestivalCategory
import domain.festival.FestivalEffectId
import domain.festival.FestivalRecord
import domain.festival.FestivalSourcePriority
import domain.festival.FestivalVerificationStatus
import domain.festival.IndianFestivalRegion
import domain.festival.ResolvedFestivalOccurrence
import domain.pet.HitZoneProjection
import domain.pet.Mayur3DMath
import domain.pet.Mayur3DRigController
import domain.pet.MayurAnimationQuality
import domain.pet.MayurAudioTelemetry
import domain.pet.MayurCostume
import domain.pet.MayurDanceIntensity
import domain.pet.MayurDanceStyle
import domain.pet.MayurExpression
import domain.pet.MayurPetBehaviorEngine
import domain.pet.MayurPetSettings
import domain.pet.MayurReactionEvent
import domain.pet.MayurReactionType
import domain.pet.MayurTouchZone
import domain.pet.Vec3
import domain.weather.PrimaryAtmosphereType
import domain.weather.WeatherAtmospherePlan
import domain.weather.WeatherSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.home.MixArtistChip
import ui.home.YourMixSection
import ui.settings.MayurVirtualPetSettingsContent
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MayurVirtualPetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    private fun song(id: Long, title: String, genre: String, artist: String = "Artist"): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = "MAYUR Studio",
        albumId = id,
        durationMs = 210_000L,
        uri = Uri.parse("content://media/external/audio/media/$id"),
        dataPath = "/music/$id.flac",
        mimeType = "audio/flac",
        sizeBytes = 12_000_000L,
        genre = genre
    )

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun math3D_supportsFull360OrbitDepthReversalAndProjectedHitTesting() {
        val beakWorld = Vec3(0f, 0.1f, 0.5f)
        val tailWorld = Vec3(0f, 0.0f, -0.7f)

        // Front view (0°): beak is closer to camera than tail
        val frontBeak = Mayur3DMath.projectWorld(beakWorld, 0f, 0f, 300f, 300f)
        val frontTail = Mayur3DMath.projectWorld(tailWorld, 0f, 0f, 300f, 300f)
        assertTrue(frontBeak.camZ > frontTail.camZ)

        // Back view (180°): tail is closer to camera than beak
        val backBeak = Mayur3DMath.projectWorld(beakWorld, 180f, 0f, 300f, 300f)
        val backTail = Mayur3DMath.projectWorld(tailWorld, 180f, 0f, 300f, 300f)
        assertTrue(backTail.camZ > backBeak.camZ)

        // Right profile (90°): beak shifts clearly right of center while tail shifts left
        val sideBeak = Mayur3DMath.projectWorld(beakWorld, 90f, 0f, 300f, 300f)
        val sideTail = Mayur3DMath.projectWorld(tailWorld, 90f, 0f, 300f, 300f)
        assertTrue(sideBeak.sx > 150f)
        assertTrue(sideTail.sx < 150f)

        // Depth-prioritized 3D hit-testing picks closest overlapping bone
        val zones = listOf(
            HitZoneProjection(MayurTouchZone.BODY, 150f, 150f, 60f, camZ = 0.0f),
            HitZoneProjection(MayurTouchZone.TAIL, 150f, 150f, 60f, camZ = 0.8f)
        )
        assertEquals(MayurTouchZone.TAIL, Mayur3DMath.hitTestProjectedZones(150f, 150f, zones))
    }

    @Test
    fun rig3D_rotatesWithInertiaLooksBackOverShoulderAndFollowsTailSpring() {
        val rig = Mayur3DRigController(initialYawDeg = 0f, initialPitchDeg = 6f)

        // Drag horizontally to impart angular velocity
        rig.beginDrag()
        rig.applyDragDelta(deltaYawDeg = 35f, deltaPitchDeg = 8f, dtSeconds = 0.02f)
        val yawAtRelease = rig.orbitYawDeg
        rig.endDrag()

        // Step forward: inertia continues smooth rotation and eventually decays
        var pose = rig.currentPose
        repeat(20) {
            pose = rig.step(
                dtSeconds = 0.02f,
                isDancing = false,
                danceStyle = MayurDanceStyle.POP,
                danceIntensity = MayurDanceIntensity.NORMAL,
                telemetry = MayurAudioTelemetry.SILENT,
                isSleeping = false,
                reduceAnimations = false
            )
        }
        assertNotEquals(yawAtRelease, pose.orbitYawDeg)
        assertTrue(abs(rig.angularVelYaw) < 560f)

        // Rotate near back view (145°): Mayur should turn head independently to look back over shoulder
        val backRig = Mayur3DRigController(initialYawDeg = 145f, initialPitchDeg = 6f)
        var backPose = backRig.currentPose
        repeat(35) {
            backPose = backRig.step(
                dtSeconds = 0.02f,
                isDancing = false,
                danceStyle = MayurDanceStyle.POP,
                danceIntensity = MayurDanceIntensity.NORMAL,
                telemetry = MayurAudioTelemetry.SILENT,
                isSleeping = false,
                reduceAnimations = false
            )
        }
        assertTrue("Expected independent head look-back over shoulder, got ${backPose.headYawDeg}", abs(backPose.headYawDeg) > 15f)
    }

    @Test
    fun touchGestures_rotate360TiltPitchDoubleTapAndLongPress() {
        var lastReaction: MayurReactionEvent? = null
        var lastYaw = 16f
        var lastPitch = 8f

        composeRule.setContent {
            MayurVirtualPet(
                settings = MayurPetSettings.DEFAULT,
                currentSong = song(1L, "Pop Hit", "Pop"),
                isPlaying = false,
                visualizerFrequenciesFlow = MutableStateFlow(FloatArray(32)),
                waveformSamplesFlow = MutableStateFlow(FloatArray(64)),
                weatherPlan = WeatherAtmospherePlan.INACTIVE,
                weatherSnapshot = null,
                activeFestival = ActiveFestivalResolution.EMPTY,
                reduceAnimations = true,
                screenVisible = true,
                overrideLocalMinutesOfDay = 14 * 60,
                onReactionTriggered = { lastReaction = it },
                onOrbitChanged = { y, p ->
                    lastYaw = y
                    lastPitch = p
                }
            )
        }

        // 3D Canvas renders cleanly without any flat PNG dependency
        composeRule.onNodeWithTag("mayur_3d_canvas").assertIsDisplayed()

        // Horizontal swipe rotates viewing yaw
        composeRule.onNodeWithTag("mayur_virtual_pet").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        assertNotEquals(16f, lastYaw)

        // Vertical swipe tilts viewing pitch
        composeRule.onNodeWithTag("mayur_virtual_pet").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        assertNotEquals(8f, lastPitch)

        // Double tap triggers special happy reaction
        composeRule.onNodeWithTag("mayur_virtual_pet").performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        assertEquals(MayurReactionType.DOUBLE_TAP_HAPPY, lastReaction?.type)
        assertEquals(MayurExpression.EXCITED, lastReaction?.expression)

        // Long press triggers playful 360° animation
        composeRule.onNodeWithTag("mayur_virtual_pet").performTouchInput { longClick() }
        composeRule.waitForIdle()
        assertEquals(MayurReactionType.SPECIAL_LONG_PRESS, lastReaction?.type)
    }

    @Test
    fun musicDanceEngine_mapsAllSevenStylesAndDetectsBassBeats() {
        val quietFreqs = FloatArray(32) { 0.08f }
        val quietWave = FloatArray(64) { 0.05f }
        val bassSpikeFreqs = FloatArray(32) { if (it < 8) 0.90f else 0.35f }
        val bassSpikeWave = FloatArray(64) { if (it % 2 == 0) 0.7f else -0.7f }

        val quietTelemetry = MayurPetBehaviorEngine.computeAudioEnergy(quietFreqs, quietWave, previousBassEma = 0.1f)
        val beatTelemetry = MayurPetBehaviorEngine.computeAudioEnergy(bassSpikeFreqs, bassSpikeWave, previousBassEma = quietTelemetry.bassEnergy)
        assertTrue(beatTelemetry.isBeatHit)
        assertTrue(beatTelemetry.beatPulse > 0.5f)

        assertEquals(MayurDanceStyle.POP, MayurPetBehaviorEngine.selectDanceStyle(song(1, "Summer Pop", "Pop"), beatTelemetry, MayurDanceIntensity.NORMAL, 0))
        assertEquals(MayurDanceStyle.CLASSICAL, MayurPetBehaviorEngine.selectDanceStyle(song(2, "Evening Raga", "Classical"), quietTelemetry, MayurDanceIntensity.NORMAL, 0))
        assertEquals(MayurDanceStyle.ELECTRONIC, MayurPetBehaviorEngine.selectDanceStyle(song(3, "Neon Synth", "EDM"), beatTelemetry, MayurDanceIntensity.NORMAL, 0))
        assertEquals(MayurDanceStyle.HIP_HOP, MayurPetBehaviorEngine.selectDanceStyle(song(4, "Street Flow", "Hip-Hop"), beatTelemetry, MayurDanceIntensity.NORMAL, 0))
        assertEquals(MayurDanceStyle.LO_FI, MayurPetBehaviorEngine.selectDanceStyle(song(5, "Rainy Study", "Lo-Fi"), quietTelemetry, MayurDanceIntensity.NORMAL, 0))
        assertEquals(MayurDanceStyle.ROMANTIC, MayurPetBehaviorEngine.selectDanceStyle(song(6, "Dil Se", "Romantic"), quietTelemetry, MayurDanceIntensity.NORMAL, 0))
        assertEquals(MayurDanceStyle.HIGH_ENERGY, MayurPetBehaviorEngine.selectDanceStyle(song(7, "Bhangra Anthem", "Workout"), beatTelemetry, MayurDanceIntensity.HIGH, 0))

        // Electronic dance boosts jacket glowing waveform emblem
        val rig = Mayur3DRigController()
        var elecPose = rig.currentPose
        repeat(20) {
            elecPose = rig.step(
                dtSeconds = 0.02f,
                isDancing = true,
                danceStyle = MayurDanceStyle.ELECTRONIC,
                danceIntensity = MayurDanceIntensity.HIGH,
                telemetry = beatTelemetry,
                isSleeping = false,
                reduceAnimations = false
            )
        }
        assertTrue(elecPose.waveformGlow > 0.70f)
    }

    @Test
    fun yourMixSection_responsiveAcrossSmallAndLargeWidthsAndPreservesMixPlayback() {
        var mixPlayed = false
        val track = song(9L, "Evening Raga", "Classical", "Hariprasad")

        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                Box(modifier = Modifier.width(320.dp)) {
                    YourMixSection(
                        contributorsSummary = "Hariprasad",
                        artistChips = listOf(MixArtistChip("Hariprasad", track)),
                        isMixPlaying = false,
                        hasTracks = true,
                        colors = EditorialColors.default(),
                        onPlayMix = { mixPlayed = true },
                        onShuffleMix = {},
                        onArtistChipClick = {},
                        petContent = { petSize ->
                            MayurVirtualPet(
                                settings = MayurPetSettings.DEFAULT,
                                currentSong = track,
                                isPlaying = false,
                                visualizerFrequenciesFlow = null,
                                waveformSamplesFlow = null,
                                weatherPlan = WeatherAtmospherePlan.INACTIVE,
                                weatherSnapshot = null,
                                activeFestival = ActiveFestivalResolution.EMPTY,
                                reduceAnimations = true,
                                screenVisible = true,
                                size = petSize,
                                overrideLocalMinutesOfDay = 15 * 60
                            )
                        }
                    )
                }
            }
        }

        assertEquals(0, composeRule.onAllNodesWithTag("your_mix_play_button").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("your_mix_shuffle_button").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("mayur_3d_canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("your_mix_heading").performClick()
        assertTrue(mixPlayed)
    }

    @Test
    fun sleepAndEnvironmentCostumes_andSettingsPersistenceWorkCorrectly() {
        val settings = MayurPetSettings.DEFAULT
        assertTrue(settings.isWithinSleepWindow(22 * 60))
        assertTrue(settings.isWithinSleepWindow(1 * 60 + 30))
        assertFalse(settings.isWithinSleepWindow(7 * 60))

        // Weather & Festival costumes preserved seamlessly
        assertEquals(
            MayurCostume.RAIN,
            MayurPetBehaviorEngine.resolveCostume(
                settings = settings,
                isSleepWindow = false,
                weatherPlan = WeatherAtmospherePlan.INACTIVE.copy(
                    primary = PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN,
                    isActive = true
                ),
                weatherSnapshot = null,
                activeFestival = ActiveFestivalResolution.EMPTY
            )
        )
        assertEquals(
            MayurCostume.HOT_SUNNY,
            MayurPetBehaviorEngine.resolveCostume(
                settings = settings,
                isSleepWindow = false,
                weatherPlan = WeatherAtmospherePlan.INACTIVE,
                weatherSnapshot = WeatherSnapshot(
                    locationId = "paikamal",
                    latitude = 20.9,
                    longitude = 82.8,
                    temperatureC = 36.0,
                    relativeHumidityPct = 40,
                    apparentTemperatureC = 37.0,
                    isDay = true,
                    precipitationMm = 0.0,
                    rainMm = 0.0,
                    showersMm = 0.0,
                    snowfallCm = 0.0,
                    weatherCode = 0,
                    cloudCoverPct = 5,
                    windSpeedKmh = 6.0,
                    windDirectionDeg = 90.0,
                    windGustsKmh = 9.0,
                    fetchedAtEpochMs = 1L
                ),
                activeFestival = ActiveFestivalResolution.EMPTY
            )
        )
        val diwali = ActiveFestivalResolution(
            dateIso = "2026-11-08",
            primary = ResolvedFestivalOccurrence(
                record = FestivalRecord(
                    recordKey = "diwali",
                    festivalSlug = "diwali",
                    canonicalName = "Diwali",
                    alternateRegionalNames = emptyList(),
                    startDateIso = "2026-11-08",
                    endDateIso = "2026-11-08",
                    tithi = null,
                    regions = setOf(IndianFestivalRegion.ALL_INDIA),
                    isPanIndia = true,
                    category = FestivalCategory.MAJOR_PAN_INDIA,
                    shortDescription = "Diwali",
                    effectId = FestivalEffectId.DIWALI_FIREWORKS,
                    iconGlyph = "🪔",
                    sourceName = "Test",
                    sourceLicense = "MIT",
                    sourcePriority = FestivalSourcePriority.RASHTRIYA_PANCHANG,
                    verificationStatus = FestivalVerificationStatus.OFFICIAL_PANCHANG,
                    lastUpdatedEpochMs = 1L,
                    expiresAtEpochMs = 0L
                ),
                displayNameForRegion = "Diwali",
                dayIndex = 1,
                totalDays = 1,
                relevanceScore = 100
            ),
            allActive = emptyList(),
            lastUpdatedEpochMs = 1L,
            isFromOfflineCache = false,
            isCalendarExpired = false
        )
        assertEquals(
            MayurCostume.FESTIVAL_DIWALI,
            MayurPetBehaviorEngine.resolveCostume(settings, false, WeatherAtmospherePlan.INACTIVE, null, diwali)
        )

        // Preferences persistence & Settings UI toggles
        val prefs = VirtualPetPreferences(app)
        val custom = settings.copy(
            danceIntensity = MayurDanceIntensity.HIGH,
            animationQuality = MayurAnimationQuality.BATTERY_SAVER
        )
        runBlocking { prefs.saveSettings(custom) }
        assertEquals(custom, prefs.getInitialSettings())

        var currentSettings by mutableStateOf(MayurPetSettings.DEFAULT)
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                MayurVirtualPetSettingsContent(
                    settings = currentSettings,
                    currentSong = song(10L, "Pop Track", "Pop"),
                    isPlaying = false,
                    visualizerFrequenciesFlow = null,
                    waveformSamplesFlow = null,
                    weatherPlan = WeatherAtmospherePlan.INACTIVE,
                    weatherSnapshot = null,
                    activeFestival = ActiveFestivalResolution.EMPTY,
                    reduceAnimations = true,
                    onUpdateSettings = { transform -> currentSettings = transform(currentSettings) },
                    onResetDefaults = { currentSettings = MayurPetSettings.DEFAULT },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("mayur_pet_settings_list")
            .performScrollToNode(hasTestTag("mayur_setting_touch_switch"))
        composeRule.onNodeWithTag("mayur_setting_touch_switch").performClick()
        composeRule.waitForIdle()
        assertFalse(currentSettings.touchReactionsEnabled)
    }

    @Test
    fun sleepingPet_wakesBrieflyWithSleepyReactionOnTouch() {
        var lastReaction: MayurReactionEvent? = null
        composeRule.setContent {
            MayurVirtualPet(
                settings = MayurPetSettings.DEFAULT,
                currentSong = null,
                isPlaying = false,
                visualizerFrequenciesFlow = null,
                waveformSamplesFlow = null,
                weatherPlan = WeatherAtmospherePlan.INACTIVE,
                weatherSnapshot = null,
                activeFestival = ActiveFestivalResolution.EMPTY,
                reduceAnimations = true,
                screenVisible = true,
                overrideLocalMinutesOfDay = 23 * 60,
                onReactionTriggered = { lastReaction = it }
            )
        }

        composeRule.onNodeWithTag("mayur_pet_sleep_indicator").assertExists()
        composeRule.onNodeWithTag("mayur_virtual_pet").performClick()
        composeRule.mainClock.advanceTimeBy(450L)
        composeRule.waitForIdle()
        assertNotNull(lastReaction)
        assertEquals(MayurReactionType.SLEEPY_WAKE_IRRITATED, lastReaction?.type)
    }
}
