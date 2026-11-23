package domain.weather

import com.example.model.MiniPlayerStyle
import com.example.model.PlayerStyle
import data.preferences.WeatherAmbientSettings
import domain.home.HomeMusicMode
import java.time.ZonedDateTime
import kotlin.math.sin

/**
 * Reusable, deterministic Weather Atmosphere Engine converting real Open-Meteo measurements
 * (or user-selected Manual Atmosphere presets) into structured visual rendering plans.
 *
 * Enforces:
 * - Strict 10-level priority hierarchy so competing effects never clutter the screen
 * - Real precipitation scaling (never heavy rain when light rain is measured)
 * - Actual snowfall requirement (never fake snowfall merely from cold temperature)
 * - Astronomical solar azimuth/elevation positioning for sunlight & sunrise/sunset/night
 * - Cold-night fireplace only when night + apparentTemperature <= threshold + no snowfall
 * - Strict Online Video exclusion and Online Music pause-when-stopped lifecycle policy
 */
object WeatherAtmosphereEngine {

    /**
     * Evaluates whether ambient effects should render on the Home screen.
     * - Off mode: always false
     * - Online Video: always false (never overlaps video player)
     * - Offline Music: active whenever enabled (Automatic with snapshot or Manual)
     * - Online Music: active ONLY while music is actively playing (fades out & pauses when stopped)
     */
    fun shouldRenderOnHome(
        homeMusicMode: HomeMusicMode,
        isMusicPlaying: Boolean,
        hasCurrentSong: Boolean,
        settings: WeatherAmbientSettings,
        hasWeatherSnapshot: Boolean
    ): Boolean {
        if (settings.mode == WeatherEffectMode.OFF) return false
        if (settings.mode == WeatherEffectMode.AUTOMATIC && !hasWeatherSnapshot) return false
        return when (homeMusicMode) {
            HomeMusicMode.OFFLINE -> true
            HomeMusicMode.ONLINE -> isMusicPlaying && hasCurrentSong
            HomeMusicMode.ONLINE_VIDEO -> false
        }
    }

    /**
     * Evaluates whether subtle weather accents are visually appropriate for a given [MiniPlayerStyle].
     * Avoids cluttering ultra-minimal text or compact circular styles.
     */
    fun supportsMiniPlayerAtmosphere(style: MiniPlayerStyle): Boolean = when (style) {
        MiniPlayerStyle.MINIMAL_TEXT,
        MiniPlayerStyle.CIRCULAR_PLAYER -> false
        else -> true
    }

    /**
     * Classifies wind speed/gusts into configurable visual intensity presets.
     */
    fun classifyWindBand(windSpeedKmh: Double, windGustsKmh: Double = 0.0): WindIntensityBand {
        val effective = maxOf(windSpeedKmh, windGustsKmh * 0.80)
        return when {
            effective > 40.0 -> WindIntensityBand.VERY_STRONG
            effective >= 25.0 -> WindIntensityBand.STRONG
            effective >= 10.0 -> WindIntensityBand.BREEZY
            else -> WindIntensityBand.CALM
        }
    }

    /**
     * Converts meteorological wind direction (degrees FROM which wind blows: 0°=N, 90°=E, 180°=S, 270°=W)
     * into a horizontal travel vector (-1f = blowing right-to-left, +1f = blowing left-to-right).
     */
    fun computeWindHorizontalVector(windDirectionDeg: Double, windBand: WindIntensityBand): Float {
        // Meteorological convention: wind FROM East (90°) blows toward West (-X on screen);
        // wind FROM West (270°) blows toward East (+X on screen).
        val blowingTowardDeg = (windDirectionDeg + 180.0) % 360.0
        val sinComponent = sin(Math.toRadians(blowingTowardDeg)).toFloat()
        // Guarantee subtle visible lateral slant even when blowing mostly N/S during strong wind
        val directionalSign = when {
            sinComponent > 0.12f -> sinComponent
            sinComponent < -0.12f -> sinComponent
            else -> if (blowingTowardDeg < 180.0) 0.28f else -0.28f
        }
        return (directionalSign * windBand.speedMultiplier).coerceIn(-1.45f, 1.45f)
    }

    /**
     * Classifies rainfall intensity strictly from measured precipitation/rain/showers and WMO code.
     */
    fun classifyRainIntensity(snapshot: WeatherSnapshot): RainIntensity {
        val mm = snapshot.totalLiquidRainMm
        val code = snapshot.weatherCode
        return when {
            WmoWeatherCodeInterpreter.isHeavyRain(code) || mm >= 7.5 -> RainIntensity.HEAVY
            code == 63 || code == 81 || code == 55 || mm >= 2.2 -> RainIntensity.MODERATE
            WmoWeatherCodeInterpreter.isModerateOrLightRain(code) ||
                WmoWeatherCodeInterpreter.isThunderstorm(code) && mm > 0.0 ||
                mm > 0.05 -> RainIntensity.LIGHT
            else -> RainIntensity.NONE
        }
    }

    /**
     * Resolves a complete [WeatherAtmospherePlan] from real weather data or Manual Atmosphere mode.
     */
    fun resolvePlan(
        settings: WeatherAmbientSettings,
        snapshot: WeatherSnapshot?,
        now: ZonedDateTime = ZonedDateTime.now(SolarPositionCalculator.INDIA_ZONE),
        surfaceRole: WeatherSurfaceRole = WeatherSurfaceRole.HomeBackground
    ): WeatherAtmospherePlan {
        if (settings.mode == WeatherEffectMode.OFF) {
            return WeatherAtmospherePlan.INACTIVE
        }

        val location = settings.selectedLocation
        val solar = SolarPositionCalculator.calculate(
            latitudeDeg = location.latitude,
            longitudeDeg = location.longitude,
            dateTime = now
        )

        val windowDropletsStyle = when (surfaceRole) {
            is WeatherSurfaceRole.FullscreenPlayer ->
                surfaceRole.style == PlayerStyle.GLASS || surfaceRole.style == PlayerStyle.LIQUID_GLASS
            is WeatherSurfaceRole.MiniPlayer ->
                surfaceRole.style == MiniPlayerStyle.GLASS_BAR || surfaceRole.style == MiniPlayerStyle.BLUR_CARD
            else -> false
        }

        if (settings.mode == WeatherEffectMode.MANUAL) {
            return buildManualPlan(
                preset = settings.manualPreset,
                settings = settings,
                solar = solar,
                windowDropletsStyle = windowDropletsStyle
            )
        }

        // Automatic Weather mode: never fabricate weather when no real snapshot is available
        if (snapshot == null) {
            return WeatherAtmospherePlan.INACTIVE
        }

        return buildAutomaticPlan(
            snapshot = snapshot,
            settings = settings,
            solar = solar,
            windowDropletsStyle = windowDropletsStyle
        )
    }

    private fun buildAutomaticPlan(
        snapshot: WeatherSnapshot,
        settings: WeatherAmbientSettings,
        solar: SolarPosition,
        windowDropletsStyle: Boolean
    ): WeatherAtmospherePlan {
        val windBand = classifyWindBand(snapshot.windSpeedKmh, snapshot.windGustsKmh)
        val windVecX = computeWindHorizontalVector(snapshot.windDirectionDeg, windBand)
        val rainIntensity = classifyRainIntensity(snapshot)

        val isThunderstorm = settings.enableThunderstorm &&
            WmoWeatherCodeInterpreter.isThunderstorm(snapshot.weatherCode)
        val hasActualSnow = settings.enableSnow &&
            (snapshot.snowfallCm > 0.0 || WmoWeatherCodeInterpreter.isSnowCode(snapshot.weatherCode))
        val hasFog = settings.enableFog &&
            WmoWeatherCodeInterpreter.isFogCode(snapshot.weatherCode)
        val hasStrongWind = settings.enableWind &&
            (windBand == WindIntensityBand.STRONG || windBand == WindIntensityBand.VERY_STRONG)
        val isDayEffective = snapshot.isDay && solar.isDaylight
        val cloudFraction = (snapshot.cloudCoverPct.coerceIn(0, 100) / 100f)
        val isCloudyOrOvercast = settings.enableClouds &&
            (snapshot.cloudCoverPct >= 62 || WmoWeatherCodeInterpreter.isCloudyOrOvercast(snapshot.weatherCode))
        val isSunnyDay = settings.enableSunlight && isDayEffective && snapshot.cloudCoverPct < 65
        val isColdNightFireplace = settings.enableFireplace &&
            !isDayEffective &&
            !hasActualSnow &&
            snapshot.apparentTemperatureC <= settings.coldFireplaceThresholdC.toDouble()

        // Priority 1..10 resolution
        val primary: PrimaryAtmosphereType = when {
            isThunderstorm -> PrimaryAtmosphereType.THUNDERSTORM
            settings.enableRain && rainIntensity == RainIntensity.HEAVY -> PrimaryAtmosphereType.HEAVY_RAIN
            settings.enableRain && (rainIntensity == RainIntensity.MODERATE || rainIntensity == RainIntensity.LIGHT) ->
                PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN
            hasActualSnow -> PrimaryAtmosphereType.SNOWFALL
            hasFog -> PrimaryAtmosphereType.FOG_OR_MIST
            hasStrongWind -> PrimaryAtmosphereType.STRONG_WIND
            isSunnyDay -> PrimaryAtmosphereType.SUNNY_DAY
            isCloudyOrOvercast -> PrimaryAtmosphereType.CLOUDY_OVERCAST
            isColdNightFireplace -> PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE
            !isDayEffective -> PrimaryAtmosphereType.CLEAR_NIGHT
            else -> PrimaryAtmosphereType.CLOUDY_OVERCAST
        }

        val rainDensity = when {
            !settings.enableRain && primary != PrimaryAtmosphereType.THUNDERSTORM -> 0f
            primary == PrimaryAtmosphereType.THUNDERSTORM -> when (rainIntensity) {
                RainIntensity.HEAVY -> 1.0f
                RainIntensity.MODERATE -> 0.72f
                RainIntensity.LIGHT -> 0.45f
                RainIntensity.NONE -> 0.35f
            }
            primary == PrimaryAtmosphereType.HEAVY_RAIN -> 1.0f
            primary == PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN -> when (rainIntensity) {
                RainIntensity.MODERATE -> 0.66f
                RainIntensity.LIGHT -> 0.34f
                else -> 0.34f
            }
            else -> 0f
        }

        // Wind combines naturally with rain, snow, clouds, or stands on its own when strong
        val windFactor = if (settings.enableWind) {
            when (windBand) {
                WindIntensityBand.VERY_STRONG -> 1.0f
                WindIntensityBand.STRONG -> 0.78f
                WindIntensityBand.BREEZY -> if (primary == PrimaryAtmosphereType.SUNNY_DAY || primary == PrimaryAtmosphereType.CLEAR_NIGHT) 0.25f else 0.42f
                WindIntensityBand.CALM -> 0f
            }
        } else 0f

        val snowDensity = if (primary == PrimaryAtmosphereType.SNOWFALL) {
            when {
                snapshot.snowfallCm >= 2.0 || snapshot.weatherCode == 75 -> 1.0f
                snapshot.snowfallCm >= 0.6 || snapshot.weatherCode == 73 -> 0.68f
                else -> 0.42f
            }
        } else 0f

        val fogDensity = when {
            primary == PrimaryAtmosphereType.FOG_OR_MIST -> if (snapshot.weatherCode == 48) 0.92f else 0.74f
            settings.enableFog && primary == PrimaryAtmosphereType.CLOUDY_OVERCAST && snapshot.relativeHumidityPct >= 82 -> 0.28f
            else -> 0f
        }

        val cloudDensity = when {
            !settings.enableClouds -> 0f
            primary == PrimaryAtmosphereType.CLOUDY_OVERCAST -> (0.48f + cloudFraction * 0.52f).coerceIn(0.45f, 1f)
            primary == PrimaryAtmosphereType.THUNDERSTORM || primary == PrimaryAtmosphereType.HEAVY_RAIN -> 0.78f
            primary == PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN -> 0.55f
            primary == PrimaryAtmosphereType.SUNNY_DAY && snapshot.cloudCoverPct >= 25 -> (cloudFraction * 0.55f)
            else -> 0f
        }

        val sunlightStrength = if (settings.enableSunlight && isDayEffective && primary == PrimaryAtmosphereType.SUNNY_DAY) {
            val elevationFactor = (solar.elevationDeg.coerceIn(2.0, 65.0) / 65.0).toFloat()
            val cloudAttenuation = (1f - cloudFraction * 0.72f).coerceIn(0.25f, 1f)
            ((0.55f + elevationFactor * 0.45f) * cloudAttenuation * settings.clampedSunlightIntensity)
                .coerceIn(0.18f, 1.0f)
        } else if (settings.enableSunlight && isDayEffective && primary == PrimaryAtmosphereType.CLOUDY_OVERCAST && snapshot.cloudCoverPct < 80) {
            (0.22f * settings.clampedSunlightIntensity)
        } else {
            0f
        }

        val lightningStrength = if (primary == PrimaryAtmosphereType.THUNDERSTORM && settings.enableThunderstorm) {
            0.78f
        } else 0f

        val fireplaceStrength = if (primary == PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE && settings.enableFireplace) {
            val coldDelta = (settings.coldFireplaceThresholdC - snapshot.apparentTemperatureC).coerceIn(0.0, 12.0)
            (0.55f + (coldDelta / 12.0).toFloat() * 0.45f).coerceIn(0.55f, 1.0f)
        } else 0f

        val clearNightStarStrength = if (primary == PrimaryAtmosphereType.CLEAR_NIGHT && snapshot.cloudCoverPct < 45) {
            (1f - cloudFraction).coerceIn(0.35f, 0.90f)
        } else 0f

        val (tintArgb, tintAlpha) = resolveAtmosphericTint(primary, solar.phase)
        val hasRain = rainDensity > 0.05f

        return WeatherAtmospherePlan(
            primary = primary,
            isActive = true,
            isSimulatedManual = false,
            rainIntensity = if (hasRain) rainIntensity.takeIf { it != RainIntensity.NONE } ?: RainIntensity.LIGHT else RainIntensity.NONE,
            rainDensityFactor = rainDensity,
            windBand = windBand,
            windDirectionDeg = snapshot.windDirectionDeg.toFloat(),
            windSpeedKmh = snapshot.windSpeedKmh.toFloat(),
            windVectorX = windVecX,
            windParticleFactor = windFactor,
            snowDensityFactor = snowDensity,
            fogDensity = fogDensity,
            cloudDensity = cloudDensity,
            sunlightStrength = sunlightStrength,
            solarPosition = solar,
            lightningStrength = lightningStrength,
            fireplaceStrength = fireplaceStrength,
            clearNightStarStrength = clearNightStarStrength,
            windowDropletsEnabled = hasRain && windowDropletsStyle,
            atmosphericTintArgb = tintArgb,
            atmosphericTintAlpha = tintAlpha,
            summaryLabel = snapshot.conditionLabel
        )
    }

    private fun buildManualPlan(
        preset: ManualAtmospherePreset,
        settings: WeatherAmbientSettings,
        solar: SolarPosition,
        windowDropletsStyle: Boolean
    ): WeatherAtmospherePlan {
        // For Manual Sunny preview even at night, compute a pleasant daylight solar position
        val manualSolar = if (preset == ManualAtmospherePreset.SUNNY && !solar.isDaylight) {
            SolarPosition(
                azimuthDeg = 145.0,
                elevationDeg = 42.0,
                isDaylight = true,
                phase = DaylightPhase.MORNING,
                normalizedSunX = 0.72f,
                normalizedSunY = 0.16f,
                rayDirectionRadians = 1.92f
            )
        } else {
            solar
        }

        val primary = when (preset) {
            ManualAtmospherePreset.RAIN -> PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN
            ManualAtmospherePreset.HEAVY_RAIN -> PrimaryAtmosphereType.HEAVY_RAIN
            ManualAtmospherePreset.WIND -> PrimaryAtmosphereType.STRONG_WIND
            ManualAtmospherePreset.SUNNY -> PrimaryAtmosphereType.SUNNY_DAY
            ManualAtmospherePreset.CLOUDS -> PrimaryAtmosphereType.CLOUDY_OVERCAST
            ManualAtmospherePreset.FOG -> PrimaryAtmosphereType.FOG_OR_MIST
            ManualAtmospherePreset.SNOW -> PrimaryAtmosphereType.SNOWFALL
            ManualAtmospherePreset.FIREPLACE -> PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE
            ManualAtmospherePreset.THUNDERSTORM -> PrimaryAtmosphereType.THUNDERSTORM
        }

        val (tintArgb, tintAlpha) = resolveAtmosphericTint(primary, manualSolar.phase)

        return WeatherAtmospherePlan(
            primary = primary,
            isActive = true,
            isSimulatedManual = true,
            rainIntensity = when (preset) {
                ManualAtmospherePreset.RAIN -> RainIntensity.MODERATE
                ManualAtmospherePreset.HEAVY_RAIN, ManualAtmospherePreset.THUNDERSTORM -> RainIntensity.HEAVY
                else -> RainIntensity.NONE
            },
            rainDensityFactor = when (preset) {
                ManualAtmospherePreset.RAIN -> 0.62f
                ManualAtmospherePreset.HEAVY_RAIN -> 1.0f
                ManualAtmospherePreset.THUNDERSTORM -> 0.88f
                else -> 0f
            },
            windBand = when (preset) {
                ManualAtmospherePreset.WIND -> WindIntensityBand.STRONG
                ManualAtmospherePreset.THUNDERSTORM, ManualAtmospherePreset.HEAVY_RAIN -> WindIntensityBand.STRONG
                ManualAtmospherePreset.RAIN, ManualAtmospherePreset.SNOW -> WindIntensityBand.BREEZY
                else -> WindIntensityBand.CALM
            },
            windDirectionDeg = 245f,
            windSpeedKmh = when (preset) {
                ManualAtmospherePreset.WIND -> 34f
                ManualAtmospherePreset.THUNDERSTORM -> 36f
                ManualAtmospherePreset.HEAVY_RAIN -> 28f
                ManualAtmospherePreset.RAIN -> 15f
                else -> 6f
            },
            windVectorX = when (preset) {
                ManualAtmospherePreset.WIND -> 1.05f
                ManualAtmospherePreset.THUNDERSTORM -> 0.95f
                ManualAtmospherePreset.HEAVY_RAIN -> 0.72f
                ManualAtmospherePreset.RAIN -> 0.35f
                ManualAtmospherePreset.SNOW -> 0.25f
                else -> 0.1f
            },
            windParticleFactor = when (preset) {
                ManualAtmospherePreset.WIND -> 0.95f
                ManualAtmospherePreset.THUNDERSTORM -> 0.65f
                ManualAtmospherePreset.HEAVY_RAIN -> 0.45f
                else -> 0f
            },
            snowDensityFactor = if (preset == ManualAtmospherePreset.SNOW) 0.85f else 0f,
            fogDensity = if (preset == ManualAtmospherePreset.FOG) 0.82f else 0f,
            cloudDensity = when (preset) {
                ManualAtmospherePreset.CLOUDS -> 0.85f
                ManualAtmospherePreset.THUNDERSTORM, ManualAtmospherePreset.HEAVY_RAIN -> 0.75f
                ManualAtmospherePreset.RAIN -> 0.48f
                else -> 0f
            },
            sunlightStrength = if (preset == ManualAtmospherePreset.SUNNY) {
                (0.88f * settings.clampedSunlightIntensity).coerceIn(0.25f, 1.0f)
            } else 0f,
            solarPosition = manualSolar,
            lightningStrength = if (preset == ManualAtmospherePreset.THUNDERSTORM) 0.80f else 0f,
            fireplaceStrength = if (preset == ManualAtmospherePreset.FIREPLACE) 0.88f else 0f,
            clearNightStarStrength = 0f,
            windowDropletsEnabled = (preset == ManualAtmospherePreset.RAIN ||
                preset == ManualAtmospherePreset.HEAVY_RAIN ||
                preset == ManualAtmospherePreset.THUNDERSTORM) && windowDropletsStyle,
            atmosphericTintArgb = tintArgb,
            atmosphericTintAlpha = tintAlpha,
            summaryLabel = "Simulated • ${preset.label}"
        )
    }

    private fun resolveAtmosphericTint(
        primary: PrimaryAtmosphereType,
        phase: DaylightPhase
    ): Pair<Long, Float> = when (primary) {
        PrimaryAtmosphereType.THUNDERSTORM -> 0xFF0A1224L to 0.30f
        PrimaryAtmosphereType.HEAVY_RAIN -> 0xFF0E1A2BL to 0.24f
        PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN -> 0xFF142438L to 0.16f
        PrimaryAtmosphereType.SNOWFALL -> 0xFF1A283DL to 0.14f
        PrimaryAtmosphereType.FOG_OR_MIST -> 0xFF8EA0B5L to 0.14f
        PrimaryAtmosphereType.STRONG_WIND -> 0xFF283A4CL to 0.10f
        PrimaryAtmosphereType.SUNNY_DAY -> when (phase) {
            DaylightPhase.SUNRISE -> 0xFFFF9A44L to 0.16f
            DaylightPhase.SUNSET -> 0xFFFF6E40L to 0.18f
            DaylightPhase.MIDDAY -> 0xFFFFE082L to 0.12f
            else -> 0xFFFFCA64L to 0.14f
        }
        PrimaryAtmosphereType.CLOUDY_OVERCAST -> 0xFF455A64L to 0.14f
        PrimaryAtmosphereType.CLEAR_NIGHT -> 0xFF090F22L to 0.16f
        PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE -> 0xFFFF7A26L to 0.16f
        PrimaryAtmosphereType.NONE -> 0x00000000L to 0f
    }
}
