package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.SonoraDatabase
import com.example.model.PlayerStyle
import data.festival.FestivalCalendarRepository
import data.festival.FestivalHttpTransport
import data.festival.FestivalSyncStatus
import data.festival.PanIndiaFestivalCatalog
import data.festival.PublicFestivalCalendarDataSource
import data.preferences.FestivalAtmospherePreferences
import data.preferences.FestivalAtmosphereSettings
import data.preferences.WeatherAmbientSettings
import domain.ambient.AmbientEffectCoordinator
import domain.festival.ActiveFestivalResolution
import domain.festival.FestivalAtmosphereEngine
import domain.festival.FestivalDateResolver
import domain.festival.FestivalDensityLevel
import domain.festival.FestivalEffectId
import domain.festival.FestivalIntensityLevel
import domain.festival.FestivalRegionMode
import domain.festival.FestivalSurfaceRole
import domain.festival.FestivalVerificationStatus
import domain.festival.IndianFestivalRegion
import domain.festival.RegionalObservanceRule
import domain.home.HomeMusicMode
import domain.weather.OdishaWeatherLocation
import domain.weather.PrimaryAtmosphereType
import domain.weather.WeatherEffectMode
import domain.weather.WeatherSnapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.components.FestivalDetailsSheetContent
import ui.components.FestivalIndicator
import ui.components.FestivalParticlePool
import ui.settings.FestivalSettingsContent
import ui.theme.EditorialColors
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraFestivalAtmosphereEngineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val seedRecords = PanIndiaFestivalCatalog.verifiedSeedRecords()
    private val validNowMs = PanIndiaFestivalCatalog.CATALOG_SEED_EPOCH_MS + 86_400_000L

    // 1. Diwali fireworks resolution & particle stepping
    @Test
    fun diwaliDate_resolvesFireworksPlan_andStepsCleanly() {
        val resolution = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-11-08"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )

        assertTrue(resolution.hasActiveFestival)
        assertEquals("diwali", resolution.primary!!.record.festivalSlug)
        assertEquals(FestivalEffectId.DIWALI_FIREWORKS, resolution.primary!!.record.effectId)

        val plan = FestivalAtmosphereEngine.resolvePlan(
            settings = FestivalAtmosphereSettings.DEFAULT,
            resolution = resolution,
            surfaceRole = FestivalSurfaceRole.HomeBackground
        )
        assertTrue(plan.isActive)
        assertEquals(FestivalEffectId.DIWALI_FIREWORKS, plan.effectId)
        assertTrue("Particle cap must stay within battery-safe Low limit", plan.particleLimit in 6..15)

        val pool = FestivalParticlePool(capacity = 40)
        repeat(60) { pool.step(1f / 30f, plan) }
        assertTrue(pool.elapsedSec > 0f)
    }

    // 2. Odisha Kartik Purnima / Boita Bandana boat animation
    @Test
    fun kartikPurnimaInOdisha_resolvesBoitaBandanaBoatAnimation() {
        val resolution = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-11-24"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )

        assertTrue(resolution.hasActiveFestival)
        assertEquals("kartik_purnima", resolution.primary!!.record.festivalSlug)
        assertTrue(resolution.primary!!.displayNameForRegion.contains("Boita Bandana"))
        assertEquals(FestivalEffectId.KARTIK_BOITA_BANDANA, resolution.primary!!.record.effectId)
        assertEquals("⛵", resolution.primary!!.record.iconGlyph)
    }

    // 3. Dussehra respectful arrow & flame animation
    @Test
    fun dussehraDate_resolvesStylizedArrowFlamePlan() {
        val resolution = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-10-20"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.UTTAR_PRADESH,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )

        assertTrue(resolution.hasActiveFestival)
        assertEquals("dussehra", resolution.primary!!.record.festivalSlug)
        assertEquals(FestivalEffectId.DUSSEHRA_RAMA_ARROW, resolution.primary!!.record.effectId)

        val plan = FestivalAtmosphereEngine.resolvePlan(
            settings = FestivalAtmosphereSettings.DEFAULT,
            resolution = resolution,
            surfaceRole = FestivalSurfaceRole.FullscreenPlayer
        )
        assertTrue(plan.isActive)
        assertEquals(FestivalEffectId.DUSSEHRA_RAMA_ARROW, plan.effectId)
    }

    // 4. Holi soft gulal clouds animation
    @Test
    fun holiDate_resolvesSoftGulalPlan() {
        val resolution = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-03-04"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.DELHI,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )

        assertTrue(resolution.hasActiveFestival)
        assertEquals("holi", resolution.primary!!.record.festivalSlug)
        assertEquals(FestivalEffectId.HOLI_GULAL, resolution.primary!!.record.effectId)
    }

    // 5. Multiple festivals on the same date (ranking + additionalCount)
    @Test
    fun multipleFestivalsSameDate_preservesAllAndExposesAdditionalCount() {
        // 2026-04-14: Baisakhi, Vishu, Tamil Puthandu, Bohag Bihu
        val resolution = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-04-14"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.ASSAM,
            includeRegionalInAllIndia = true,
            nowEpochMs = validNowMs
        )

        assertTrue(resolution.hasActiveFestival)
        assertTrue("Expected at least 3 festivals on Apr 14, got ${resolution.allActive.size}", resolution.allActive.size >= 3)
        assertEquals(resolution.allActive.size - 1, resolution.additionalCount)

        // 2026-11-24 with Odisha + All India: Kartik Purnima / Boita Bandana + Dev Deepawali
        val kartikRes = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-11-24"),
            regionMode = FestivalRegionMode.ALL_INDIA_PLUS_STATE,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = true,
            nowEpochMs = validNowMs
        )
        assertTrue(kartikRes.allActive.size >= 2)
        assertTrue(kartikRes.additionalCount >= 1)
    }

    // 6. Regional date differences & calendar disagreement preservation
    @Test
    fun regionalDateDifferences_andPublicFeedDisagreement_arePreserved() {
        // Odisha observes Dola Purnima on 2026-03-03
        val odishaMar3 = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-03-03"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertTrue(odishaMar3.hasActiveFestival)
        assertTrue(odishaMar3.primary!!.displayNameForRegion.contains("Dola Purnima"))
        assertNotNull(odishaMar3.primary!!.record.disagreementNote)

        // Delhi / Pan-India All India mode (regional off) does NOT activate Odisha Dola Purnima on Mar 3
        val delhiMar3 = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-03-03"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.DELHI,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertFalse(delhiMar3.hasActiveFestival)

        // Test MIT public JSON feed disagreement preservation against higher-priority Rashtriya Panchang
        val conflictingMitJson = """
            [
              { "name": "Diwali", "date": "2026-11-09", "type": "Major", "description": "Alternate feed date" },
              { "name": "Devotees Special Utsav", "date": "2026-07-05", "type": "Festival", "description": "New public entry" }
            ]
        """.trimIndent()

        val parsed = PublicFestivalCalendarDataSource.parseMitFestivalsJson(conflictingMitJson, validNowMs)
        assertEquals(2, parsed.size)

        val merged = PublicFestivalCalendarDataSource.mergeWithPriorityAndDisagreements(seedRecords, parsed)
        val diwaliEntries = merged.filter { it.festivalSlug == "diwali" && it.startDateIso == "2026-11-08" }
        assertTrue(diwaliEntries.isNotEmpty())
        assertEquals(FestivalVerificationStatus.DISAGREEMENT_PRESERVED, diwaliEntries.first().verificationStatus)
        assertTrue(diwaliEntries.first().disagreementNote?.contains("2026-11-09") == true)
        assertTrue(merged.any { it.canonicalName == "Devotees Special Utsav" })
    }

    // 7. State switching across Indian states + DataStore persistence
    @Test
    fun stateSwitching_matchesStateSpecificFestivals_andPersistsInDataStore() = runBlocking {
        // Odisha -> Nuakhai (2026-09-15)
        val nuakhaiOdisha = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-09-15"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertTrue(nuakhaiOdisha.allActive.any { it.record.festivalSlug == "nuakhai" })

        // Tamil Nadu -> Pongal (2026-01-16, Day 3/4)
        val pongalTn = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-01-16"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.TAMIL_NADU,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertEquals("pongal", pongalTn.primary!!.record.festivalSlug)
        assertEquals(3, pongalTn.primary!!.dayIndex)
        assertEquals(4, pongalTn.primary!!.totalDays)

        // Kerala -> Onam (2026-08-25)
        val onamKerala = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-08-25"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.KERALA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertEquals("onam", onamKerala.primary!!.record.festivalSlug)
        assertEquals(FestivalEffectId.ONAM_POOKALAM_BOAT, onamKerala.primary!!.record.effectId)

        // Assam -> Bohag Bihu (2026-04-16)
        val bihuAssam = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-04-16"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.ASSAM,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertEquals("bohag_bihu", bihuAssam.primary!!.record.festivalSlug)
        assertEquals(FestivalEffectId.BIHU_FESTIVE_PATTERN, bihuAssam.primary!!.record.effectId)

        // Telangana -> Bathukamma (2026-10-10)
        val bathukammaTs = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-10-10"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.TELANGANA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertEquals("bathukamma", bathukammaTs.primary!!.record.festivalSlug)

        // Verify all 28 States + 8 UTs = 36 regions exist
        assertEquals(36, IndianFestivalRegion.STATES_AND_UTS.size)

        // Persist settings via DataStore
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = FestivalAtmospherePreferences(context)
        prefs.updateSettings {
            it.copy(
                regionMode = FestivalRegionMode.ALL_INDIA_PLUS_STATE,
                selectedState = IndianFestivalRegion.WEST_BENGAL,
                animationIntensity = FestivalIntensityLevel.MEDIUM,
                particleDensity = FestivalDensityLevel.MEDIUM,
                batterySaver = true
            )
        }
        val persisted = prefs.settingsFlow.first()
        assertEquals(FestivalRegionMode.ALL_INDIA_PLUS_STATE, persisted.regionMode)
        assertEquals(IndianFestivalRegion.WEST_BENGAL, persisted.selectedState)
        assertEquals(FestivalIntensityLevel.MEDIUM, persisted.animationIntensity)
        assertEquals(FestivalDensityLevel.MEDIUM, persisted.particleDensity)
        prefs.resetToDefaults()
    }

    // 8. Offline Room cache + network failure fallback + WorkManager scheduling
    @Test
    fun offlineRoomCache_survivesNetworkFailure_andSchedulesWorkManager() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val failingTransport = FestivalHttpTransport { _ ->
                throw IOException("Offline simulated")
            }
            val dataSource = PublicFestivalCalendarDataSource(transport = failingTransport)
            val repo = FestivalCalendarRepository(
                dao = db.festivalCalendarDao(),
                dataSource = dataSource,
                clockEpochMs = { validNowMs }
            )

            repo.ensureInitialCacheSeeded()
            val refreshResult = repo.refreshIfStale(force = true)
            assertTrue(refreshResult.isFailure)

            // Cached Room records must still resolve Diwali cleanly while offline!
            val diwaliOffline = repo.resolveForDateOnce(
                date = LocalDate.parse("2026-11-08"),
                regionMode = FestivalRegionMode.ALL_INDIA,
                selectedState = IndianFestivalRegion.ODISHA,
                includeRegionalInAllIndia = false
            )
            assertTrue(diwaliOffline.hasActiveFestival)
            assertTrue(diwaliOffline.isFromOfflineCache)
            assertEquals("diwali", diwaliOffline.primary!!.record.festivalSlug)

            // Verify WorkManager periodic sync schedules cleanly
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().build()
            )
            repo.schedulePeriodicRefresh(context)
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(FestivalCalendarRepository.PERIODIC_SYNC_WORK_NAME)
                .get()
            assertTrue(workInfos.isNotEmpty())
            assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)
        } finally {
            db.close()
        }
    }

    // 9. Expired or unavailable data source never fabricates dates
    @Test
    fun expiredOrUnavailableCalendar_neverFabricatesFestivals() {
        val year2030Ms = PanIndiaFestivalCatalog.CATALOG_EXPIRY_EPOCH_MS + 365L * 86_400_000L
        val expiredResolution = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-11-08"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = year2030Ms
        )
        assertFalse(expiredResolution.hasActiveFestival)
        assertTrue(expiredResolution.isCalendarExpired)
        assertNull(expiredResolution.primary)

        val emptyResolution = FestivalDateResolver.resolveForDate(
            records = emptyList(),
            date = LocalDate.parse("2026-11-08"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertFalse(emptyResolution.hasActiveFestival)
    }

    // 10 & 11. Battery Saver, Low-RAM, and Reduce Motion limits
    @Test
    fun batterySaverAndReduceMotion_enforceStrictParticleAndFrameCaps() {
        val resolution = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-11-08"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )

        // Default Battery Saver = On, Low Density = 8..14 -> scaled further
        val defaultPlan = FestivalAtmosphereEngine.resolvePlan(
            settings = FestivalAtmosphereSettings.DEFAULT,
            resolution = resolution,
            surfaceRole = FestivalSurfaceRole.HomeBackground,
            isLowRamDevice = false
        )
        assertEquals(33_333_333L, defaultPlan.minFrameIntervalNanos) // 30 fps cap
        assertTrue(defaultPlan.particleLimit <= 14)

        // Low-RAM + Battery Saver -> 20 fps cap (50ms)
        val lowRamPlan = FestivalAtmosphereEngine.resolvePlan(
            settings = FestivalAtmosphereSettings.DEFAULT,
            resolution = resolution,
            surfaceRole = FestivalSurfaceRole.HomeBackground,
            isLowRamDevice = true
        )
        assertEquals(50_000_000L, lowRamPlan.minFrameIntervalNanos)
        assertTrue(lowRamPlan.particleLimit <= 10)

        // High Density without Battery Saver still capped strictly <= 40
        val highPlan = FestivalAtmosphereEngine.resolvePlan(
            settings = FestivalAtmosphereSettings(
                particleDensity = FestivalDensityLevel.HIGH,
                animationIntensity = FestivalIntensityLevel.HIGH,
                batterySaver = false
            ),
            resolution = resolution,
            surfaceRole = FestivalSurfaceRole.HomeBackground
        )
        assertTrue("High density must never exceed 40 particles", highPlan.particleLimit in 25..40)

        // Reduce Motion -> Static mode, zero continuous frame loop
        val reducedPlan = FestivalAtmosphereEngine.resolvePlan(
            settings = FestivalAtmosphereSettings(reduceMotion = true),
            resolution = resolution,
            surfaceRole = FestivalSurfaceRole.HomeBackground
        )
        assertTrue(reducedPlan.isStaticReducedMotion)
        assertEquals(Long.MAX_VALUE, reducedPlan.minFrameIntervalNanos)
        assertEquals(0f, reducedPlan.speedMultiplier, 0.0001f)
    }

    // 12 & 14. Online Video exclusion + Weather + Festival coordination
    @Test
    fun ambientCoordinator_disablesInOnlineVideo_andCoexistsWithWeather() {
        val diwaliRes = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-11-08"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        val clearNightWeather = WeatherSnapshot(
            locationId = OdishaWeatherLocation.PAIKAMAL.id,
            latitude = OdishaWeatherLocation.PAIKAMAL.latitude,
            longitude = OdishaWeatherLocation.PAIKAMAL.longitude,
            temperatureC = 23.0,
            relativeHumidityPct = 64,
            apparentTemperatureC = 23.5,
            isDay = false,
            precipitationMm = 0.0,
            rainMm = 0.0,
            showersMm = 0.0,
            snowfallCm = 0.0,
            weatherCode = 0,
            cloudCoverPct = 5,
            windSpeedKmh = 6.0,
            windDirectionDeg = 120.0,
            windGustsKmh = 9.0,
            sunriseIso = "2026-11-08T06:00",
            sunsetIso = "2026-11-08T17:20",
            fetchedAtEpochMs = validNowMs
        )
        val heavyRainWeather = clearNightWeather.copy(
            rainMm = 9.5,
            precipitationMm = 10.2,
            weatherCode = 65,
            cloudCoverPct = 95
        )
        val nightTime = ZonedDateTime.of(2026, 11, 8, 21, 0, 0, 0, ZoneId.of("Asia/Kolkata"))

        // Online Video Mode -> 100% disabled (Weather OFF, Festival OFF, Home Indicator OFF)
        val onlineVideoState = AmbientEffectCoordinator.coordinateHome(
            homeMusicMode = HomeMusicMode.ONLINE_VIDEO,
            isMusicPlaying = true,
            hasCurrentSong = true,
            weatherSettings = WeatherAmbientSettings.DEFAULT,
            weatherSnapshot = clearNightWeather,
            festivalSettings = FestivalAtmosphereSettings.DEFAULT,
            festivalResolution = diwaliRes,
            now = nightTime
        )
        assertFalse(onlineVideoState.isWeatherActive)
        assertFalse(onlineVideoState.isFestivalActive)
        assertFalse(onlineVideoState.showHomeFestivalIndicator)

        // Offline Music + Clear Night + Diwali -> Both active harmoniously at full budget
        val clearNightCoexist = AmbientEffectCoordinator.coordinateHome(
            homeMusicMode = HomeMusicMode.OFFLINE,
            isMusicPlaying = false,
            hasCurrentSong = false,
            weatherSettings = WeatherAmbientSettings.DEFAULT,
            weatherSnapshot = clearNightWeather,
            festivalSettings = FestivalAtmosphereSettings.DEFAULT,
            festivalResolution = diwaliRes,
            now = nightTime
        )
        assertTrue(clearNightCoexist.isWeatherActive)
        assertEquals(PrimaryAtmosphereType.CLEAR_NIGHT, clearNightCoexist.weatherPlan.primary)
        assertTrue(clearNightCoexist.isFestivalActive)
        assertTrue(clearNightCoexist.showHomeFestivalIndicator)
        assertEquals(1.0f, clearNightCoexist.weatherParticleScale, 0.01f)

        // Heavy Rain + Diwali -> Coordinated shared budget scales down both particle layers
        val stormCoexist = AmbientEffectCoordinator.coordinatePlayer(
            playerStyle = PlayerStyle.entries.first(),
            weatherSettings = WeatherAmbientSettings.DEFAULT,
            weatherSnapshot = heavyRainWeather,
            festivalSettings = FestivalAtmosphereSettings(
                particleDensity = FestivalDensityLevel.HIGH,
                batterySaver = false
            ),
            festivalResolution = diwaliRes,
            now = nightTime
        )
        assertTrue(stormCoexist.isWeatherActive)
        assertTrue(stormCoexist.isFestivalActive)
        assertTrue("Shared storm budget should scale down weather scale", stormCoexist.weatherParticleScale < 0.85f)
        assertTrue("Shared storm budget should keep festival particles modest", stormCoexist.festivalPlan.particleLimit <= 22)

        // Preview-only override must NEVER leak onto Home or Player
        val leakedAttempt = FestivalAtmosphereEngine.resolvePlan(
            settings = FestivalAtmosphereSettings(enabled = true, automaticDetection = true),
            resolution = ActiveFestivalResolution.EMPTY,
            surfaceRole = FestivalSurfaceRole.HomeBackground,
            previewOverrideEffect = FestivalEffectId.DIWALI_FIREWORKS
        )
        assertFalse(leakedAttempt.isActive)
    }

    // 13a. Android 8 (API 26) compatibility & zero personal permissions check
    @Test
    @Config(sdk = [26])
    fun android8Api26_compatibilityAndZeroPersonalPermissions() {
        assertZeroPersonalPermissionsDeclared()
        val res = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-11-24"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertEquals(FestivalEffectId.KARTIK_BOITA_BANDANA, res.primary!!.record.effectId)
    }

    // 13b. Android 11 (API 30) compatibility check
    @Test
    @Config(sdk = [30])
    fun android11Api30_compatibilityCheck() {
        assertZeroPersonalPermissionsDeclared()
        val res = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-10-20"),
            regionMode = FestivalRegionMode.ALL_INDIA,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = validNowMs
        )
        assertEquals(FestivalEffectId.DUSSEHRA_RAMA_ARROW, res.primary!!.record.effectId)
    }

    // 15. Compose UI verification: Indicator, Details Bottom Sheet, and Settings Screen
    @Test
    fun composeUi_indicatorDetailsAndSettings_workCorrectly() {
        composeRule.mainClock.autoAdvance = false

        val kartikRes = FestivalDateResolver.resolveForDate(
            records = seedRecords,
            date = LocalDate.parse("2026-11-24"),
            regionMode = FestivalRegionMode.ALL_INDIA_PLUS_STATE,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = true,
            nowEpochMs = validNowMs
        )

        var indicatorTapped = false
        var settingsOpened = false
        var currentSettings = FestivalAtmosphereSettings.DEFAULT

        composeRule.setContent {
            val colors = EditorialColors.default()
            androidx.compose.foundation.layout.Column {
                FestivalIndicator(
                    resolution = kartikRes,
                    colors = colors,
                    onClick = { indicatorTapped = true }
                )
                FestivalDetailsSheetContent(
                    resolution = kartikRes,
                    colors = colors,
                    onOpenFestivalSettings = { settingsOpened = true }
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("home_festival_indicator").assertIsDisplayed().performClick()
        assertTrue(indicatorTapped)
        composeRule.onNodeWithTag("home_festival_indicator_extra_count", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("festival_details_open_settings_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(settingsOpened)
    }

    @Test
    fun composeUi_festivalSettingsScreen_togglesAndPreviewWork() {
        composeRule.mainClock.autoAdvance = false
        var currentSettings by mutableStateOf(FestivalAtmosphereSettings.DEFAULT)
        var refreshClicked = false

        composeRule.setContent {
            FestivalSettingsContent(
                settings = currentSettings,
                resolution = ActiveFestivalResolution.EMPTY,
                syncStatus = FestivalSyncStatus(recordCount = seedRecords.size, lastSyncedEpochMs = validNowMs),
                colors = EditorialColors.default(),
                onUpdateSettings = { transform -> currentSettings = transform(currentSettings) },
                onRefreshCalendar = { refreshClicked = true },
                onResetSettings = { currentSettings = FestivalAtmosphereSettings.DEFAULT },
                onBack = {}
            )
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("festival_settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("festival_preview_card").assertIsDisplayed()

        composeRule.onNodeWithTag("festival_settings_lazy_column")
            .performScrollToKey("festival_region_card")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("festival_region_mode_specific_state")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(FestivalRegionMode.SPECIFIC_STATE, currentSettings.regionMode)

        composeRule.onNodeWithTag("festival_settings_lazy_column")
            .performScrollToKey("festival_performance_card")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("festival_reduce_motion_toggle")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(currentSettings.reduceMotion)

        composeRule.onNodeWithTag("festival_settings_lazy_column")
            .performScrollToKey("festival_sources_card")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("festival_calendar_refresh_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(refreshClicked)
    }

    // 16. Measured CPU frame-step benchmark across key festival effects
    @Test
    fun particlePoolBenchmark_measuresSubMillisecondFrameStepLatency() {
        val effectsToMeasure = listOf(
            FestivalEffectId.DIWALI_FIREWORKS,
            FestivalEffectId.KARTIK_BOITA_BANDANA,
            FestivalEffectId.DUSSEHRA_RAMA_ARROW,
            FestivalEffectId.HOLI_GULAL
        )
        val pool = FestivalParticlePool(capacity = 40)
        val framesPerEffect = 1_000

        effectsToMeasure.forEach { effect ->
            val plan = FestivalAtmosphereEngine.resolvePlan(
                settings = FestivalAtmosphereSettings.DEFAULT,
                resolution = ActiveFestivalResolution.EMPTY,
                surfaceRole = FestivalSurfaceRole.SettingsPreview,
                previewOverrideEffect = effect
            )
            // Warmup
            repeat(50) { pool.step(0.033f, plan) }

            val startNs = System.nanoTime()
            repeat(framesPerEffect) { pool.step(0.033f, plan) }
            val elapsedNs = System.nanoTime() - startNs
            val avgMicros = elapsedNs / (framesPerEffect * 1_000.0)
            println("BENCHMARK FestivalParticlePool [$effect] (${plan.particleLimit} particles): avg=${"%.3f".format(avgMicros)} us/frame")
            assertTrue(
                "Expected sub-250us average particle step for $effect, got $avgMicros us",
                avgMicros < 250.0
            )
        }
    }

    private fun assertZeroPersonalPermissionsDeclared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()
        val forbidden = setOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS"
        )
        val violations = requested.intersect(forbidden)
        assertTrue("Forbidden personal permissions declared: $violations", violations.isEmpty())
    }
}
