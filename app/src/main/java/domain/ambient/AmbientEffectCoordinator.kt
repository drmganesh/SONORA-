package domain.ambient

import com.example.model.PlayerStyle
import data.preferences.FestivalAtmosphereSettings
import data.preferences.WeatherAmbientSettings
import domain.festival.ActiveFestivalResolution
import domain.festival.FestivalAtmosphereEngine
import domain.festival.FestivalRenderPlan
import domain.festival.FestivalSurfaceRole
import domain.home.HomeMusicMode
import domain.weather.PrimaryAtmosphereType
import domain.weather.WeatherAtmosphereEngine
import domain.weather.WeatherAtmospherePlan
import domain.weather.WeatherSnapshot
import domain.weather.WeatherSurfaceRole
import java.time.ZonedDateTime

/**
 * Coordinated visual output combining SONORA's Weather Atmosphere Engine and Festival Atmosphere Engine
 * under a shared global intensity & particle budget.
 */
data class CoordinatedAmbientState(
    val weatherPlan: WeatherAtmospherePlan,
    val isWeatherActive: Boolean,
    val weatherParticleScale: Float,
    val festivalPlan: FestivalRenderPlan,
    val isFestivalActive: Boolean,
    val showHomeFestivalIndicator: Boolean
)

/**
 * Coordinates the existing Weather Atmosphere Engine with the Festival Atmosphere Engine:
 * - Preserves existing Weather effects completely when no festival is active
 * - Allows harmonious coexistence (e.g., Diwali fireworks with Clear Night stars)
 * - Enforces a shared global particle budget so two particle engines never overwhelm GPU/battery
 * - Strictly disables ALL music ambient effects and festival indicator in Online Video mode
 * - Respects Online Music active-playback-only gating
 */
object AmbientEffectCoordinator {

    fun coordinateHome(
        homeMusicMode: HomeMusicMode,
        isMusicPlaying: Boolean,
        hasCurrentSong: Boolean,
        weatherSettings: WeatherAmbientSettings,
        weatherSnapshot: WeatherSnapshot?,
        festivalSettings: FestivalAtmosphereSettings,
        festivalResolution: ActiveFestivalResolution,
        isLowRamDevice: Boolean = false,
        globalReduceAnimations: Boolean = false,
        now: ZonedDateTime = ZonedDateTime.now(domain.weather.SolarPositionCalculator.INDIA_ZONE)
    ): CoordinatedAmbientState {
        // Online Video mode must remain 100% free of music ambient effects & festival overlays
        if (homeMusicMode == HomeMusicMode.ONLINE_VIDEO) {
            return CoordinatedAmbientState(
                weatherPlan = WeatherAtmospherePlan.INACTIVE,
                isWeatherActive = false,
                weatherParticleScale = 0f,
                festivalPlan = FestivalRenderPlan.INACTIVE,
                isFestivalActive = false,
                showHomeFestivalIndicator = false
            )
        }

        val showIndicator = festivalSettings.enabled &&
            festivalSettings.automaticDetection &&
            festivalSettings.showHomeIndicator &&
            festivalResolution.hasActiveFestival

        val isWeatherEligible = WeatherAtmosphereEngine.shouldRenderOnHome(
            homeMusicMode = homeMusicMode,
            isMusicPlaying = isMusicPlaying,
            hasCurrentSong = hasCurrentSong,
            settings = weatherSettings,
            hasWeatherSnapshot = weatherSnapshot != null
        )

        val weatherPlan = if (isWeatherEligible) {
            WeatherAtmosphereEngine.resolvePlan(
                settings = weatherSettings,
                snapshot = weatherSnapshot,
                now = now,
                surfaceRole = WeatherSurfaceRole.HomeBackground
            )
        } else {
            WeatherAtmospherePlan.INACTIVE
        }

        val isMusicSurfaceActiveForFestival = when (homeMusicMode) {
            HomeMusicMode.OFFLINE -> true
            HomeMusicMode.ONLINE -> isMusicPlaying && hasCurrentSong
            HomeMusicMode.ONLINE_VIDEO -> false
        }

        return combineLayers(
            weatherPlan = weatherPlan,
            isWeatherActive = isWeatherEligible && weatherPlan.isActive,
            festivalSettings = festivalSettings,
            festivalResolution = festivalResolution,
            festivalSurfaceRole = FestivalSurfaceRole.HomeBackground,
            allowFestivalAnimation = isMusicSurfaceActiveForFestival,
            showHomeIndicator = showIndicator,
            isLowRamDevice = isLowRamDevice,
            globalReduceAnimations = globalReduceAnimations
        )
    }

    fun coordinatePlayer(
        playerStyle: PlayerStyle,
        weatherSettings: WeatherAmbientSettings,
        weatherSnapshot: WeatherSnapshot?,
        festivalSettings: FestivalAtmosphereSettings,
        festivalResolution: ActiveFestivalResolution,
        isLowRamDevice: Boolean = false,
        globalReduceAnimations: Boolean = false,
        now: ZonedDateTime = ZonedDateTime.now(domain.weather.SolarPositionCalculator.INDIA_ZONE)
    ): CoordinatedAmbientState {
        val weatherPlan = WeatherAtmosphereEngine.resolvePlan(
            settings = weatherSettings,
            snapshot = weatherSnapshot,
            now = now,
            surfaceRole = WeatherSurfaceRole.FullscreenPlayer(playerStyle)
        )

        return combineLayers(
            weatherPlan = weatherPlan,
            isWeatherActive = weatherPlan.isActive,
            festivalSettings = festivalSettings,
            festivalResolution = festivalResolution,
            festivalSurfaceRole = FestivalSurfaceRole.FullscreenPlayer,
            allowFestivalAnimation = true,
            showHomeIndicator = false,
            isLowRamDevice = isLowRamDevice,
            globalReduceAnimations = globalReduceAnimations
        )
    }

    private fun combineLayers(
        weatherPlan: WeatherAtmospherePlan,
        isWeatherActive: Boolean,
        festivalSettings: FestivalAtmosphereSettings,
        festivalResolution: ActiveFestivalResolution,
        festivalSurfaceRole: FestivalSurfaceRole,
        allowFestivalAnimation: Boolean,
        showHomeIndicator: Boolean,
        isLowRamDevice: Boolean,
        globalReduceAnimations: Boolean
    ): CoordinatedAmbientState {
        if (!allowFestivalAnimation || !festivalSettings.enabled || !festivalSettings.automaticDetection || !festivalResolution.hasActiveFestival) {
            return CoordinatedAmbientState(
                weatherPlan = weatherPlan,
                isWeatherActive = isWeatherActive,
                weatherParticleScale = 1f,
                festivalPlan = FestivalRenderPlan.INACTIVE,
                isFestivalActive = false,
                showHomeFestivalIndicator = showHomeIndicator
            )
        }

        // Global intensity & particle budget coordination when Weather and Festival coexist
        val weatherHasHeavyParticles = isWeatherActive && (
            weatherPlan.primary == PrimaryAtmosphereType.THUNDERSTORM ||
                weatherPlan.primary == PrimaryAtmosphereType.HEAVY_RAIN ||
                weatherPlan.primary == PrimaryAtmosphereType.SNOWFALL
            )
        val weatherHasModerateParticles = isWeatherActive && (
            weatherPlan.primary == PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN ||
                weatherPlan.primary == PrimaryAtmosphereType.STRONG_WIND
            )

        // Avoid running two dense particle storms simultaneously:
        // - Clear night / sunny / cloudy / fog pair effortlessly with full festival budget (e.g. Diwali + Clear Night)
        // - Moderate rain/wind scales both layers gently so total particle load stays low
        // - Heavy storm/rain reduces festival particles to a tasteful accent budget
        val festivalBudgetScale = when {
            weatherHasHeavyParticles -> 0.55f
            weatherHasModerateParticles -> 0.78f
            else -> 1.0f
        }
        val weatherScale = when {
            weatherHasHeavyParticles -> 0.72f
            weatherHasModerateParticles -> 0.80f
            else -> 1.0f
        }

        val festivalPlan = FestivalAtmosphereEngine.resolvePlan(
            settings = festivalSettings,
            resolution = festivalResolution,
            surfaceRole = festivalSurfaceRole,
            isLowRamDevice = isLowRamDevice,
            globalReduceAnimations = globalReduceAnimations,
            globalBudgetMultiplier = festivalBudgetScale
        )

        // When both are active, subtly attenuate weather density factor so the combined scene stays peaceful & legible
        val balancedWeatherPlan = if (isWeatherActive && festivalPlan.isActive && weatherScale < 0.99f) {
            weatherPlan.copy(
                rainDensityFactor = weatherPlan.rainDensityFactor * weatherScale,
                snowDensityFactor = weatherPlan.snowDensityFactor * weatherScale,
                windParticleFactor = weatherPlan.windParticleFactor * weatherScale
            )
        } else {
            weatherPlan
        }

        return CoordinatedAmbientState(
            weatherPlan = balancedWeatherPlan,
            isWeatherActive = isWeatherActive,
            weatherParticleScale = weatherScale,
            festivalPlan = festivalPlan,
            isFestivalActive = festivalPlan.isActive,
            showHomeFestivalIndicator = showHomeIndicator
        )
    }
}
