package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.test.core.app.ApplicationProvider
import com.example.model.MiniPlayerStyle
import com.example.model.PlayerStyle
import data.preferences.AmbientEffectsPreferences
import data.preferences.WeatherAmbientSettings
import data.weather.OpenMeteoWeatherService
import data.weather.WeatherHttpTransport
import data.weather.WeatherRepository
import domain.home.HomeMusicMode
import domain.weather.AtmosphereIntensityPreset
import domain.weather.DaylightPhase
import domain.weather.ManualAtmospherePreset
import domain.weather.OdishaWeatherLocation
import domain.weather.PrimaryAtmosphereType
import domain.weather.RainIntensity
import domain.weather.SolarPositionCalculator
import domain.weather.WeatherAtmosphereEngine
import domain.weather.WeatherEffectMode
import domain.weather.WeatherFetchState
import domain.weather.WeatherSnapshot
import domain.weather.WeatherSurfaceRole
import domain.weather.WindIntensityBand
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
import ui.settings.WeatherAmbientSettingsContent
import ui.theme.EditorialColors
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraWeatherAtmosphereEngineTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    private fun buildOpenMeteoJson(
        lat: Double,
        lon: Double,
        tempC: Double = 28.5,
        humidity: Int = 62,
        apparentTempC: Double = 29.2,
        isDay: Int = 1,
        precipitationMm: Double = 0.0,
        rainMm: Double = 0.0,
        showersMm: Double = 0.0,
        snowfallCm: Double = 0.0,
        weatherCode: Int = 0,
        cloudCover: Int = 12,
        windSpeedKmh: Double = 11.4,
        windDirDeg: Double = 120.0,
        windGustsKmh: Double = 17.0
    ): String = """
        {
          "latitude": $lat,
          "longitude": $lon,
          "timezone": "Asia/Kolkata",
          "current": {
            "time": "2026-11-23T12:00",
            "temperature_2m": $tempC,
            "relative_humidity_2m": $humidity,
            "apparent_temperature": $apparentTempC,
            "is_day": $isDay,
            "precipitation": $precipitationMm,
            "rain": $rainMm,
            "showers": $showersMm,
            "snowfall": $snowfallCm,
            "weather_code": $weatherCode,
            "cloud_cover": $cloudCover,
            "wind_speed_10m": $windSpeedKmh,
            "wind_direction_10m": $windDirDeg,
            "wind_gusts_10m": $windGustsKmh
          },
          "daily": {
            "time": ["2026-11-23"],
            "sunrise": ["2026-11-23T06:08"],
            "sunset": ["2026-11-23T17:16"]
          },
          "hourly": {
            "time": ["2026-11-23T12:00", "2026-11-23T13:00"],
            "temperature_2m": [$tempC, ${tempC + 0.6}],
            "precipitation": [$precipitationMm, $precipitationMm],
            "weather_code": [$weatherCode, $weatherCode],
            "wind_speed_10m": [$windSpeedKmh, $windSpeedKmh]
          }
        }
    """.trimIndent()

    private fun sampleSnapshot(
        location: OdishaWeatherLocation = OdishaWeatherLocation.PAIKAMAL,
        tempC: Double = 27.0,
        apparentTempC: Double = 27.0,
        isDay: Boolean = true,
        precipitationMm: Double = 0.0,
        rainMm: Double = 0.0,
        showersMm: Double = 0.0,
        snowfallCm: Double = 0.0,
        weatherCode: Int = 0,
        cloudCover: Int = 10,
        windSpeedKmh: Double = 8.0,
        windDirDeg: Double = 90.0,
        windGustsKmh: Double = 10.0
    ): WeatherSnapshot = WeatherSnapshot(
        locationId = location.id,
        latitude = location.latitude,
        longitude = location.longitude,
        temperatureC = tempC,
        relativeHumidityPct = 65,
        apparentTemperatureC = apparentTempC,
        isDay = isDay,
        precipitationMm = precipitationMm,
        rainMm = rainMm,
        showersMm = showersMm,
        snowfallCm = snowfallCm,
        weatherCode = weatherCode,
        cloudCoverPct = cloudCover,
        windSpeedKmh = windSpeedKmh,
        windDirectionDeg = windDirDeg,
        windGustsKmh = windGustsKmh,
        sunriseIso = "2026-11-23T06:08",
        sunsetIso = "2026-11-23T17:16",
        fetchedAtEpochMs = 1_760_000_000_000L
    )

    @Test
    fun `01 - All 5 fixed Odisha locations have exact coordinates, default Paikamal, and persist in DataStore`() = runBlocking {
        val all = OdishaWeatherLocation.entries
        assertEquals(5, all.size)
        assertEquals(OdishaWeatherLocation.PAIKAMAL, OdishaWeatherLocation.DEFAULT)
        assertEquals(OdishaWeatherLocation.PAIKAMAL, WeatherAmbientSettings.DEFAULT.selectedLocation)

        val expectedCoords = mapOf(
            OdishaWeatherLocation.PAIKAMAL to Triple("Bargarh", 20.917, 82.817),
            OdishaWeatherLocation.KARANJIA to Triple("Mayurbhanj", 21.758, 85.973),
            OdishaWeatherLocation.PADAMPUR to Triple("Bargarh", 20.999, 83.063),
            OdishaWeatherLocation.KENDUJHAR to Triple("Kendujhar", 21.638, 85.609),
            OdishaWeatherLocation.TITLAGARH to Triple("Balangir", 20.289, 83.155)
        )

        val prefs = AmbientEffectsPreferences(appContext())
        prefs.resetWeatherSettingsToDefaults()
        assertEquals(OdishaWeatherLocation.PAIKAMAL, prefs.weatherAmbientSettingsFlow.first().selectedLocation)

        for ((loc, expected) in expectedCoords) {
            assertEquals(expected.first, loc.district)
            assertEquals(expected.second, loc.latitude, 0.0005)
            assertEquals(expected.third, loc.longitude, 0.0005)

            // Verify Open-Meteo URL uses exact manual coordinates & Asia/Kolkata timezone
            val url = OpenMeteoWeatherService.buildForecastUrl(loc)
            assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?"))
            assertTrue(url.contains("latitude=${loc.latitude}"))
            assertTrue(url.contains("longitude=${loc.longitude}"))
            assertTrue(url.contains("timezone=Asia%2FKolkata"))
            assertTrue(url.contains("temperature_unit=celsius"))
            assertTrue(url.contains("wind_speed_unit=kmh"))

            // Verify DataStore persistence on switching location
            prefs.updateWeatherSettings { it.copy(selectedLocation = loc) }
            val loaded = prefs.weatherAmbientSettingsFlow.first()
            assertEquals(loc, loaded.selectedLocation)
        }
        prefs.resetWeatherSettingsToDefaults()
    }

    @Test
    fun `02 - Zero location permissions declared in manifest and weather fetches without location permissions`() = runBlocking {
        val ctx = appContext()
        val pkgInfo = ctx.packageManager.getPackageInfo(
            ctx.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toList().orEmpty()
        assertFalse("ACCESS_FINE_LOCATION must NEVER be declared", requested.contains("android.permission.ACCESS_FINE_LOCATION"))
        assertFalse("ACCESS_COARSE_LOCATION must NEVER be declared", requested.contains("android.permission.ACCESS_COARSE_LOCATION"))
        assertFalse("ACCESS_BACKGROUND_LOCATION must NEVER be declared", requested.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))

        // Verify weather fetch works cleanly with zero location permissions using only manual coordinates
        val capturedUrls = mutableListOf<String>()
        val fakeTransport = WeatherHttpTransport { url ->
            capturedUrls += url
            buildOpenMeteoJson(
                lat = OdishaWeatherLocation.TITLAGARH.latitude,
                lon = OdishaWeatherLocation.TITLAGARH.longitude,
                tempC = 34.0,
                weatherCode = 0
            )
        }
        val service = OpenMeteoWeatherService(transport = fakeTransport)
        val result = service.fetchCurrentWeather(OdishaWeatherLocation.TITLAGARH, nowEpochMs = 1_760_000_000_000L)
        assertTrue(result.isSuccess)
        val snap = result.getOrThrow()
        assertEquals(OdishaWeatherLocation.TITLAGARH.id, snap.locationId)
        assertEquals(34.0, snap.temperatureC, 0.01)
        assertEquals(1, capturedUrls.size)
        assertTrue(capturedUrls.first().contains("latitude=20.289"))
        assertTrue(capturedUrls.first().contains("longitude=83.155"))
    }

    @Test
    fun `03 - Open-Meteo URL includes all 13 required variables, sunrise sunset, hourly and parses accurately`() {
        val url = OpenMeteoWeatherService.buildForecastUrl(OdishaWeatherLocation.KENDUJHAR)
        val requiredVars = listOf(
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "is_day",
            "precipitation",
            "rain",
            "showers",
            "snowfall",
            "weather_code",
            "cloud_cover",
            "wind_speed_10m",
            "wind_direction_10m",
            "wind_gusts_10m",
            "sunrise",
            "sunset"
        )
        requiredVars.forEach { variable ->
            assertTrue("URL must request $variable", url.contains(variable))
        }

        val rawJson = buildOpenMeteoJson(
            lat = OdishaWeatherLocation.KENDUJHAR.latitude,
            lon = OdishaWeatherLocation.KENDUJHAR.longitude,
            tempC = 24.4,
            humidity = 81,
            apparentTempC = 25.1,
            isDay = 1,
            precipitationMm = 3.6,
            rainMm = 3.1,
            showersMm = 0.5,
            snowfallCm = 0.0,
            weatherCode = 63,
            cloudCover = 88,
            windSpeedKmh = 28.5,
            windDirDeg = 245.0,
            windGustsKmh = 41.0
        )
        val snap = OpenMeteoWeatherService.parseForecastJson(
            location = OdishaWeatherLocation.KENDUJHAR,
            rawJson = rawJson,
            fetchedAtEpochMs = 1_760_000_000_000L
        )
        assertEquals(24.4, snap.temperatureC, 0.01)
        assertEquals(81, snap.relativeHumidityPct)
        assertEquals(25.1, snap.apparentTemperatureC, 0.01)
        assertTrue(snap.isDay)
        assertEquals(3.6, snap.precipitationMm, 0.01)
        assertEquals(3.1, snap.rainMm, 0.01)
        assertEquals(0.5, snap.showersMm, 0.01)
        assertEquals(63, snap.weatherCode)
        assertEquals(88, snap.cloudCoverPct)
        assertEquals(28.5, snap.windSpeedKmh, 0.01)
        assertEquals(245.0, snap.windDirectionDeg, 0.01)
        assertEquals(41.0, snap.windGustsKmh, 0.01)
        assertEquals("2026-11-23T06:08", snap.sunriseIso)
        assertEquals("2026-11-23T17:16", snap.sunsetIso)
        assertEquals(2, snap.hourlyForecast.size)
    }

    @Test
    fun `04 - Rainfall scales strictly with real precipitation and never shows heavy rain for light rain`() {
        val settings = WeatherAmbientSettings.DEFAULT

        val lightRain = sampleSnapshot(
            location = OdishaWeatherLocation.PAIKAMAL,
            precipitationMm = 0.6,
            rainMm = 0.6,
            weatherCode = 61,
            cloudCover = 75
        )
        val heavyRain = sampleSnapshot(
            location = OdishaWeatherLocation.KARANJIA,
            precipitationMm = 14.5,
            rainMm = 14.5,
            weatherCode = 65,
            cloudCover = 100
        )

        val lightPlan = WeatherAtmosphereEngine.resolvePlan(settings, lightRain)
        val heavyPlan = WeatherAtmosphereEngine.resolvePlan(settings, heavyRain)

        assertEquals(PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN, lightPlan.primary)
        assertEquals(RainIntensity.LIGHT, lightPlan.rainIntensity)
        assertEquals(PrimaryAtmosphereType.HEAVY_RAIN, heavyPlan.primary)
        assertEquals(RainIntensity.HEAVY, heavyPlan.rainIntensity)
        assertTrue("Heavy rain must have significantly higher droplet density than light rain",
            heavyPlan.rainDensityFactor > lightPlan.rainDensityFactor + 0.30f)
    }

    @Test
    fun `05 - Strong wind controls particle direction accurately and classifies all wind bands`() {
        assertEquals(WindIntensityBand.CALM, WeatherAtmosphereEngine.classifyWindBand(5.0, 7.0))
        assertEquals(WindIntensityBand.BREEZY, WeatherAtmosphereEngine.classifyWindBand(14.0, 18.0))
        assertEquals(WindIntensityBand.STRONG, WeatherAtmosphereEngine.classifyWindBand(29.0, 32.0))
        assertEquals(WindIntensityBand.VERY_STRONG, WeatherAtmosphereEngine.classifyWindBand(46.0, 58.0))

        // Wind FROM East (90°) blows Westward (-X); wind FROM West (270°) blows Eastward (+X)
        val eastWindVec = WeatherAtmosphereEngine.computeWindHorizontalVector(90.0, WindIntensityBand.STRONG)
        val westWindVec = WeatherAtmosphereEngine.computeWindHorizontalVector(270.0, WindIntensityBand.STRONG)
        assertTrue("Wind from East (90°) must slant westward (negative X), got $eastWindVec", eastWindVec < -0.5f)
        assertTrue("Wind from West (270°) must slant eastward (positive X), got $westWindVec", westWindVec > 0.5f)

        val windySnap = sampleSnapshot(
            location = OdishaWeatherLocation.PADAMPUR,
            weatherCode = 1,
            windSpeedKmh = 36.0,
            windDirDeg = 270.0,
            windGustsKmh = 48.0
        )
        val plan = WeatherAtmosphereEngine.resolvePlan(WeatherAmbientSettings.DEFAULT, windySnap)
        assertEquals(PrimaryAtmosphereType.STRONG_WIND, plan.primary)
        assertTrue(plan.windParticleFactor >= 0.65f)
        assertTrue(plan.windVectorX > 0.5f)
    }

    @Test
    fun `06 and 07 - Astronomical sunlight tracks sunrise midday sunset and cleanly clears at night`() {
        val date = LocalDate.of(2026, 6, 15)
        val loc = OdishaWeatherLocation.PAIKAMAL

        val sunriseTime = ZonedDateTime.of(date, LocalTime.of(6, 10), SolarPositionCalculator.INDIA_ZONE)
        val middayTime = ZonedDateTime.of(date, LocalTime.of(12, 15), SolarPositionCalculator.INDIA_ZONE)
        val sunsetTime = ZonedDateTime.of(date, LocalTime.of(17, 55), SolarPositionCalculator.INDIA_ZONE)
        val nightTime = ZonedDateTime.of(date, LocalTime.of(22, 30), SolarPositionCalculator.INDIA_ZONE)

        val sunriseSolar = SolarPositionCalculator.calculate(loc.latitude, loc.longitude, sunriseTime)
        val middaySolar = SolarPositionCalculator.calculate(loc.latitude, loc.longitude, middayTime)
        val sunsetSolar = SolarPositionCalculator.calculate(loc.latitude, loc.longitude, sunsetTime)
        val nightSolar = SolarPositionCalculator.calculate(loc.latitude, loc.longitude, nightTime)

        assertTrue("Sunrise should be above horizon on East side (azimuth < 130°), got ${sunriseSolar.azimuthDeg}",
            sunriseSolar.isDaylight && sunriseSolar.azimuthDeg < 130.0)
        assertTrue("Midday should have high solar elevation (> 50°), got ${middaySolar.elevationDeg}",
            middaySolar.elevationDeg > 50.0)
        assertTrue("Sunset should be on West side (azimuth > 230°), got ${sunsetSolar.azimuthDeg}",
            sunsetSolar.isDaylight && sunsetSolar.azimuthDeg > 230.0)
        assertFalse("Night sun must be below horizon", nightSolar.isDaylight)
        assertEquals(DaylightPhase.NIGHT, nightSolar.phase)

        // Verify plan: sunny day produces sunlight; clear night produces zero sunlight and starry night
        val clearDaySnap = sampleSnapshot(location = loc, tempC = 32.0, isDay = true, weatherCode = 0, cloudCover = 8)
        val dayPlan = WeatherAtmosphereEngine.resolvePlan(WeatherAmbientSettings.DEFAULT, clearDaySnap, now = middayTime)
        assertEquals(PrimaryAtmosphereType.SUNNY_DAY, dayPlan.primary)
        assertTrue(dayPlan.sunlightStrength > 0.5f)
        assertEquals(0f, dayPlan.clearNightStarStrength, 0.001f)

        val clearNightSnap = sampleSnapshot(location = loc, tempC = 23.0, apparentTempC = 22.0, isDay = false, weatherCode = 0, cloudCover = 10)
        val nightPlan = WeatherAtmosphereEngine.resolvePlan(WeatherAmbientSettings.DEFAULT, clearNightSnap, now = nightTime)
        assertEquals(PrimaryAtmosphereType.CLEAR_NIGHT, nightPlan.primary)
        assertEquals("Sunlight must be completely removed at night", 0f, nightPlan.sunlightStrength, 0.001f)
        assertTrue(nightPlan.clearNightStarStrength > 0.4f)
    }

    @Test
    fun `08 - Actual snowfall triggers snow, while cold temperature without snow NEVER creates fake snowfall`() {
        val nightTime = ZonedDateTime.of(LocalDate.of(2026, 1, 10), LocalTime.of(22, 0), SolarPositionCalculator.INDIA_ZONE)

        // Cold night (6°C) with ZERO snowfall data -> must NOT generate fake snow!
        val coldNoSnowSnap = sampleSnapshot(
            location = OdishaWeatherLocation.KENDUJHAR,
            tempC = 6.0,
            apparentTempC = 5.5,
            isDay = false,
            snowfallCm = 0.0,
            weatherCode = 0,
            cloudCover = 5
        )
        val coldPlan = WeatherAtmosphereEngine.resolvePlan(WeatherAmbientSettings.DEFAULT, coldNoSnowSnap, now = nightTime)
        assertEquals("Cold clear night without snow must produce ZERO snowfall", 0f, coldPlan.snowDensityFactor, 0.001f)
        assertEquals(PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE, coldPlan.primary)

        // Actual snowfall data (snowfallCm > 0 and WMO snow code 73) -> MUST trigger snow and NOT fireplace
        val realSnowSnap = sampleSnapshot(
            location = OdishaWeatherLocation.KENDUJHAR,
            tempC = 0.5,
            apparentTempC = -1.5,
            isDay = false,
            snowfallCm = 1.8,
            weatherCode = 73,
            cloudCover = 95
        )
        val snowPlan = WeatherAtmosphereEngine.resolvePlan(WeatherAmbientSettings.DEFAULT, realSnowSnap, now = nightTime)
        assertEquals(PrimaryAtmosphereType.SNOWFALL, snowPlan.primary)
        assertTrue("Real snowfall must activate snow particle density", snowPlan.snowDensityFactor > 0.3f)
        assertEquals("Fireplace must NOT compete with active snowfall", 0f, snowPlan.fireplaceStrength, 0.001f)
    }

    @Test
    fun `09 - Cold-night fireplace activates only on cold nights, never during warm weather or daytime, and respects threshold`() {
        val nightTime = ZonedDateTime.of(LocalDate.of(2026, 12, 20), LocalTime.of(23, 0), SolarPositionCalculator.INDIA_ZONE)
        val dayTime = ZonedDateTime.of(LocalDate.of(2026, 12, 20), LocalTime.of(13, 0), SolarPositionCalculator.INDIA_ZONE)

        // Night + 10°C apparent temp (below default 12°C) -> Fireplace active
        val coldNight = sampleSnapshot(tempC = 11.0, apparentTempC = 10.0, isDay = false, weatherCode = 0)
        val coldNightPlan = WeatherAtmosphereEngine.resolvePlan(WeatherAmbientSettings.DEFAULT, coldNight, now = nightTime)
        assertEquals(PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE, coldNightPlan.primary)
        assertTrue(coldNightPlan.fireplaceStrength > 0.4f)

        // Warm night (25°C) -> Fireplace strictly OFF
        val warmNight = sampleSnapshot(tempC = 25.0, apparentTempC = 25.5, isDay = false, weatherCode = 0)
        val warmNightPlan = WeatherAtmosphereEngine.resolvePlan(WeatherAmbientSettings.DEFAULT, warmNight, now = nightTime)
        assertEquals(PrimaryAtmosphereType.CLEAR_NIGHT, warmNightPlan.primary)
        assertEquals(0f, warmNightPlan.fireplaceStrength, 0.001f)

        // Cold daytime (9°C, isDay=true) -> Fireplace strictly OFF during daylight
        val coldDay = sampleSnapshot(tempC = 9.0, apparentTempC = 8.5, isDay = true, weatherCode = 0)
        val coldDayPlan = WeatherAtmosphereEngine.resolvePlan(WeatherAmbientSettings.DEFAULT, coldDay, now = dayTime)
        assertEquals(0f, coldDayPlan.fireplaceStrength, 0.001f)

        // Custom higher threshold (18°C) -> 15°C night activates fireplace
        val mildNight = sampleSnapshot(tempC = 16.0, apparentTempC = 15.0, isDay = false, weatherCode = 0)
        val raisedThresholdSettings = WeatherAmbientSettings.DEFAULT.copy(coldFireplaceThresholdC = 18)
        val mildPlan = WeatherAtmosphereEngine.resolvePlan(raisedThresholdSettings, mildNight, now = nightTime)
        assertEquals(PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE, mildPlan.primary)
        assertTrue(mildPlan.fireplaceStrength > 0.25f)
    }

    @Test
    fun `10 and 11 - Thunderstorm has highest priority with safe lightning and all priority levels hold`() {
        val settings = WeatherAmbientSettings.DEFAULT
        val stormSnap = sampleSnapshot(
            location = OdishaWeatherLocation.TITLAGARH,
            precipitationMm = 11.2,
            rainMm = 11.2,
            weatherCode = 95,
            cloudCover = 100,
            windSpeedKmh = 38.0,
            windDirDeg = 210.0,
            windGustsKmh = 52.0
        )
        val stormPlan = WeatherAtmosphereEngine.resolvePlan(settings, stormSnap)
        assertEquals(PrimaryAtmosphereType.THUNDERSTORM, stormPlan.primary)
        assertEquals(RainIntensity.HEAVY, stormPlan.rainIntensity)
        assertTrue(stormPlan.lightningStrength in 0.25f..0.85f)
        assertTrue(stormPlan.atmosphericTintAlpha > 0.12f)

        // Fog beats strong wind (priority 5 > priority 6)
        val fogWindSnap = sampleSnapshot(weatherCode = 45, windSpeedKmh = 32.0, cloudCover = 90)
        val fogPlan = WeatherAtmosphereEngine.resolvePlan(settings, fogWindSnap)
        assertEquals(PrimaryAtmosphereType.FOG_OR_MIST, fogPlan.primary)
        assertTrue(fogPlan.fogDensity > 0.5f)

        // Disabling Thunderstorm individually cleanly falls back to Heavy Rain
        val noThunderSettings = settings.copy(enableThunderstorm = false)
        val fallbackPlan = WeatherAtmosphereEngine.resolvePlan(noThunderSettings, stormSnap)
        assertEquals(PrimaryAtmosphereType.HEAVY_RAIN, fallbackPlan.primary)
        assertEquals(0f, fallbackPlan.lightningStrength, 0.001f)
    }

    @Test
    fun `12 - Offline fallback serves last valid DataStore cache and never fabricates weather without cache`() = runBlocking {
        val prefs = AmbientEffectsPreferences(appContext())
        prefs.resetWeatherSettingsToDefaults()

        // Case A: Offline with NO cached data -> must return Error with null snapshot (NEVER fabricate)
        val failingTransport = WeatherHttpTransport { throw IOException("No internet connection") }
        val offlineRepo = WeatherRepository(
            preferences = prefs,
            service = OpenMeteoWeatherService(failingTransport)
        )
        val stateNoCache = offlineRepo.refreshWeather(OdishaWeatherLocation.PADAMPUR, force = true)
        assertTrue("Offline without cache must be Error", stateNoCache is WeatherFetchState.Error)
        assertNull("Must never fabricate a WeatherSnapshot when offline with no cache", stateNoCache.latestSnapshot)

        val autoPlanNoSnapshot = WeatherAtmosphereEngine.resolvePlan(
            settings = WeatherAmbientSettings.DEFAULT,
            snapshot = stateNoCache.latestSnapshot
        )
        assertFalse("Automatic mode without valid snapshot must stay inactive", autoPlanNoSnapshot.isActive)

        // Case B: Online fetch caches to DataStore -> subsequent offline fetch falls back to cached snapshot
        var isOnline = true
        val toggleTransport = WeatherHttpTransport {
            if (!isOnline) throw IOException("Network offline")
            buildOpenMeteoJson(
                lat = OdishaWeatherLocation.PAIKAMAL.latitude,
                lon = OdishaWeatherLocation.PAIKAMAL.longitude,
                tempC = 31.2,
                weatherCode = 2,
                cloudCover = 42
            )
        }
        val repo = WeatherRepository(
            preferences = prefs,
            service = OpenMeteoWeatherService(toggleTransport)
        )
        val onlineState = repo.refreshWeather(OdishaWeatherLocation.PAIKAMAL, force = true)
        assertTrue(onlineState is WeatherFetchState.Success && !onlineState.isOfflineCache)
        assertEquals(31.2, onlineState.latestSnapshot!!.temperatureC, 0.01)

        // Simulate app restart offline by creating a fresh repository hydrating from DataStore
        isOnline = false
        val restartedRepo = WeatherRepository(
            preferences = prefs,
            service = OpenMeteoWeatherService(toggleTransport)
        )
        restartedRepo.hydrateCacheFromDisk(OdishaWeatherLocation.PAIKAMAL)
        val offlineFallback = restartedRepo.refreshWeather(OdishaWeatherLocation.PAIKAMAL, force = true)
        assertTrue("Offline with valid cache must return Success(isOfflineCache=true)",
            offlineFallback is WeatherFetchState.Success && offlineFallback.isOfflineCache)
        assertEquals(31.2, offlineFallback.latestSnapshot!!.temperatureC, 0.01)
    }

    @Test
    fun `13 - Manual Atmosphere mode works independently of live weather across all 9 presets`() {
        assertEquals(9, ManualAtmospherePreset.entries.size)
        for (preset in ManualAtmospherePreset.entries) {
            val manualSettings = WeatherAmbientSettings.DEFAULT.copy(
                mode = WeatherEffectMode.MANUAL,
                manualPreset = preset
            )
            // Snapshot is null -> Manual Atmosphere must still be fully active & marked simulated
            val plan = WeatherAtmosphereEngine.resolvePlan(
                settings = manualSettings,
                snapshot = null,
                surfaceRole = WeatherSurfaceRole.HomeBackground
            )
            assertTrue("Manual preset $preset must be active without live weather", plan.isActive)
            assertTrue("Manual preset $preset must be clearly flagged as simulated", plan.isSimulatedManual)
        }
    }

    @Test
    fun `14 - Off mode, Online Video exclusion, and Online Music playback-only gating work strictly`() {
        val autoSettings = WeatherAmbientSettings.DEFAULT.copy(mode = WeatherEffectMode.AUTOMATIC)
        val offSettings = WeatherAmbientSettings.DEFAULT.copy(mode = WeatherEffectMode.OFF)

        // Off mode always disables everywhere
        assertFalse(WeatherAtmosphereEngine.resolvePlan(offSettings, sampleSnapshot()).isActive)
        assertFalse(WeatherAtmosphereEngine.shouldRenderOnHome(HomeMusicMode.OFFLINE, true, true, offSettings, true))

        // Online Video ALWAYS disables weather effects even while music plays in background
        assertFalse(WeatherAtmosphereEngine.shouldRenderOnHome(HomeMusicMode.ONLINE_VIDEO, true, true, autoSettings, true))
        assertFalse(WeatherAtmosphereEngine.shouldRenderOnHome(
            HomeMusicMode.ONLINE_VIDEO, true, true,
            autoSettings.copy(mode = WeatherEffectMode.MANUAL), false
        ))

        // Offline Music active even when paused (if snapshot exists)
        assertTrue(WeatherAtmosphereEngine.shouldRenderOnHome(HomeMusicMode.OFFLINE, false, false, autoSettings, true))

        // Online Music active ONLY while actively playing
        assertFalse(WeatherAtmosphereEngine.shouldRenderOnHome(HomeMusicMode.ONLINE, false, true, autoSettings, true))
        assertTrue(WeatherAtmosphereEngine.shouldRenderOnHome(HomeMusicMode.ONLINE, true, true, autoSettings, true))
    }

    @Test
    fun `15 - All PlayerStyles, MiniPlayerStyles, Battery Saver throttling, and Settings UI interactions work cleanly`() {
        val rainSnap = sampleSnapshot(precipitationMm = 6.0, rainMm = 6.0, weatherCode = 63, cloudCover = 90)
        val settings = WeatherAmbientSettings.DEFAULT

        // Every full-screen PlayerStyle resolves cleanly; GLASS & LIQUID_GLASS get window droplets
        for (style in PlayerStyle.entries) {
            val plan = WeatherAtmosphereEngine.resolvePlan(
                settings = settings,
                snapshot = rainSnap,
                surfaceRole = WeatherSurfaceRole.FullscreenPlayer(style)
            )
            assertTrue("PlayerStyle $style must resolve an active rain plan", plan.isActive)
            if (style == PlayerStyle.GLASS || style == PlayerStyle.LIQUID_GLASS) {
                assertTrue("Glass style $style must enable window droplets in rain", plan.windowDropletsEnabled)
            } else {
                assertFalse("Non-glass style $style must not enable window droplets", plan.windowDropletsEnabled)
            }
        }

        // Mini-player compatibility rules
        assertFalse(WeatherAtmosphereEngine.supportsMiniPlayerAtmosphere(MiniPlayerStyle.MINIMAL_TEXT))
        assertFalse(WeatherAtmosphereEngine.supportsMiniPlayerAtmosphere(MiniPlayerStyle.CIRCULAR_PLAYER))
        assertTrue(WeatherAtmosphereEngine.supportsMiniPlayerAtmosphere(MiniPlayerStyle.FLOATING_PILL))
        assertTrue(WeatherAtmosphereEngine.supportsMiniPlayerAtmosphere(MiniPlayerStyle.BLUR_CARD))

        // Performance & Battery Saver budget reduction + 30fps cap
        val normalHigh = settings.copy(effectIntensity = AtmosphereIntensityPreset.HIGH, batterySaverMode = false)
        val batteryLowRam = normalHigh.copy(batterySaverMode = true)
        val normalBudget = normalHigh.effectiveParticleBudget(72, isLowRamDevice = false, globalReduceAnimations = false)
        val saverBudget = batteryLowRam.effectiveParticleBudget(72, isLowRamDevice = true, globalReduceAnimations = false)
        assertTrue("Battery Saver + Low-RAM must significantly reduce particle budget ($saverBudget < $normalBudget)",
            saverBudget < normalBudget / 2)
        assertEquals(33_333_333L, batteryLowRam.minFrameIntervalNanos(isLowRamDevice = false, globalReduceAnimations = false))

        // Compose UI verification of Settings → Appearance → Ambient Effects
        composeRule.mainClock.autoAdvance = false
        var uiSettings by mutableStateOf(WeatherAmbientSettings.DEFAULT)
        var refreshCount = 0
        val summaries = mapOf(
            OdishaWeatherLocation.PAIKAMAL.id to sampleSnapshot(OdishaWeatherLocation.PAIKAMAL, tempC = 29.0),
            OdishaWeatherLocation.TITLAGARH.id to sampleSnapshot(OdishaWeatherLocation.TITLAGARH, tempC = 36.0)
        )

        composeRule.setContent {
            WeatherAmbientSettingsContent(
                settings = uiSettings,
                fetchState = WeatherFetchState.Success(
                    snapshot = summaries[uiSettings.selectedLocation.id] ?: sampleSnapshot(uiSettings.selectedLocation),
                    isOfflineCache = false
                ),
                locationSummaries = summaries,
                colors = EditorialColors.default(),
                onSelectLocation = { loc -> uiSettings = uiSettings.copy(selectedLocation = loc) },
                onUpdateSettings = { transform -> uiSettings = transform(uiSettings) },
                onRefreshWeather = { refreshCount += 1 },
                onResetSettings = { uiSettings = WeatherAmbientSettings.DEFAULT },
                onBack = {}
            )
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32L)
        composeRule.onNodeWithTag("weather_ambient_settings_screen").assertIsDisplayed()

        // Tap refresh button
        composeRule.onNodeWithTag("weather_refresh_button").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1, refreshCount)

        // Switch to Manual Atmosphere & pick Thunderstorm
        composeRule.onNodeWithTag("weather_settings_lazy_column").performScrollToKey("weather_mode_card")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("weather_mode_manual").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(WeatherEffectMode.MANUAL, uiSettings.mode)

        composeRule.onNodeWithTag("weather_settings_lazy_column").performScrollToKey("manual_presets_card")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("manual_preset_thunderstorm").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(ManualAtmospherePreset.THUNDERSTORM, uiSettings.manualPreset)

        // Select Titlagarh from the 5 fixed Odisha location cards
        composeRule.onNodeWithTag("weather_settings_lazy_column").performScrollToKey("weather_location_titlagarh")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("weather_location_card_titlagarh").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(32L)
        assertEquals(OdishaWeatherLocation.TITLAGARH, uiSettings.selectedLocation)

        // Switch to Off
        composeRule.onNodeWithTag("weather_settings_lazy_column").performScrollToKey("weather_mode_card")
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("weather_mode_off").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(WeatherEffectMode.OFF, uiSettings.mode)
    }
}
