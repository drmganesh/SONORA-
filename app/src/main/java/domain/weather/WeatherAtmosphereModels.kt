package domain.weather

import com.example.model.MiniPlayerStyle
import com.example.model.PlayerStyle

/**
 * Fixed set of five manually selectable weather locations in Odisha, India.
 * SONORA strictly avoids GPS, device location APIs, IP geolocation, and location permissions.
 */
enum class OdishaWeatherLocation(
    val id: String,
    val townName: String,
    val district: String,
    val regionLabel: String,
    val latitude: Double,
    val longitude: Double
) {
    PAIKAMAL(
        id = "paikamal",
        townName = "Paikamal",
        district = "Bargarh",
        regionLabel = "Odisha, India",
        latitude = 20.917,
        longitude = 82.817
    ),
    KARANJIA(
        id = "karanjia",
        townName = "Karanjia",
        district = "Mayurbhanj",
        regionLabel = "Odisha, India",
        latitude = 21.758,
        longitude = 85.973
    ),
    PADAMPUR(
        id = "padampur",
        townName = "Padampur",
        district = "Bargarh",
        regionLabel = "Odisha, India",
        latitude = 20.999,
        longitude = 83.063
    ),
    KENDUJHAR(
        id = "kendujhar",
        townName = "Kendujhar",
        district = "Kendujhar",
        regionLabel = "Odisha, India",
        latitude = 21.638,
        longitude = 85.609
    ),
    TITLAGARH(
        id = "titlagarh",
        townName = "Titlagarh",
        district = "Balangir",
        regionLabel = "Odisha, India",
        latitude = 20.289,
        longitude = 83.155
    );

    val fullDisplayName: String
        get() = "$townName, $district"

    companion object {
        val DEFAULT = PAIKAMAL

        fun fromId(raw: String?): OdishaWeatherLocation =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class WeatherEffectMode(val id: String, val label: String, val subtitle: String) {
    AUTOMATIC("automatic", "Automatic Weather", "Synced to real weather at selected Odisha town"),
    MANUAL("manual", "Manual Atmosphere", "Simulated visual atmosphere for customization"),
    OFF("off", "Off", "Disable all weather ambient effects");

    companion object {
        val DEFAULT = AUTOMATIC
        fun fromId(raw: String?): WeatherEffectMode =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class ManualAtmospherePreset(val id: String, val label: String) {
    RAIN("rain", "Rain"),
    HEAVY_RAIN("heavy_rain", "Heavy Rain"),
    WIND("wind", "Wind"),
    SUNNY("sunny", "Sunny"),
    CLOUDS("clouds", "Clouds"),
    FOG("fog", "Fog"),
    SNOW("snow", "Snow"),
    FIREPLACE("fireplace", "Fireplace"),
    THUNDERSTORM("thunderstorm", "Thunderstorm");

    companion object {
        val DEFAULT = RAIN
        fun fromId(raw: String?): ManualAtmospherePreset =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class AtmosphereIntensityPreset(
    val id: String,
    val label: String,
    val multiplier: Float
) {
    LOW("low", "Low", 0.58f),
    MEDIUM("medium", "Medium", 0.88f),
    HIGH("high", "High", 1.18f);

    companion object {
        val DEFAULT = MEDIUM
        fun fromId(raw: String?): AtmosphereIntensityPreset =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class RainIntensity(val label: String) {
    NONE("No Rain"),
    LIGHT("Light Rain"),
    MODERATE("Moderate Rain"),
    HEAVY("Heavy Rain")
}

/**
 * Visual-effect wind presets (not official meteorological warnings).
 */
enum class WindIntensityBand(val label: String, val speedMultiplier: Float) {
    CALM("Calm", 0.25f),
    BREEZY("Breezy", 0.62f),
    STRONG("Strong", 1.05f),
    VERY_STRONG("Very Strong", 1.45f)
}

enum class DaylightPhase(val label: String) {
    SUNRISE("Sunrise"),
    MORNING("Morning"),
    MIDDAY("Midday"),
    AFTERNOON("Afternoon"),
    SUNSET("Sunset"),
    NIGHT("Night")
}

data class SolarPosition(
    val azimuthDeg: Double,
    val elevationDeg: Double,
    val isDaylight: Boolean,
    val phase: DaylightPhase,
    val normalizedSunX: Float,
    val normalizedSunY: Float,
    val rayDirectionRadians: Float
)

/**
 * Priority-ordered primary atmospheric states matching SONORA Weather Priority Specification.
 */
enum class PrimaryAtmosphereType(val priority: Int, val label: String) {
    THUNDERSTORM(1, "Thunderstorm"),
    HEAVY_RAIN(2, "Heavy Rain"),
    MODERATE_OR_LIGHT_RAIN(3, "Rain"),
    SNOWFALL(4, "Snowfall"),
    FOG_OR_MIST(5, "Fog & Mist"),
    STRONG_WIND(6, "Windy"),
    SUNNY_DAY(7, "Sunny"),
    CLOUDY_OVERCAST(8, "Cloudy"),
    CLEAR_NIGHT(9, "Clear Night"),
    COLD_NIGHT_FIREPLACE(10, "Cold-Night Fireplace"),
    NONE(99, "Off")
}

data class HourlyWeatherPoint(
    val timeIso: String,
    val temperatureC: Double,
    val weatherCode: Int,
    val precipitationMm: Double,
    val cloudCoverPct: Int
)

/**
 * Real measured weather snapshot fetched from Open-Meteo for one of the five Odisha towns.
 */
data class WeatherSnapshot(
    val locationId: String,
    val latitude: Double,
    val longitude: Double,
    val temperatureC: Double,
    val relativeHumidityPct: Int,
    val apparentTemperatureC: Double,
    val isDay: Boolean,
    val precipitationMm: Double,
    val rainMm: Double,
    val showersMm: Double,
    val snowfallCm: Double,
    val weatherCode: Int,
    val cloudCoverPct: Int,
    val windSpeedKmh: Double,
    val windDirectionDeg: Double,
    val windGustsKmh: Double,
    val sunriseIso: String? = null,
    val sunsetIso: String? = null,
    val hourlyForecast: List<HourlyWeatherPoint> = emptyList(),
    val fetchedAtEpochMs: Long,
    val isFromCache: Boolean = false
) {
    val totalLiquidRainMm: Double
        get() = maxOf(precipitationMm, rainMm + showersMm).coerceAtLeast(0.0)

    val effectiveWindKmh: Double
        get() = maxOf(windSpeedKmh, windGustsKmh * 0.82).coerceAtLeast(0.0)

    val conditionLabel: String
        get() = WmoWeatherCodeInterpreter.describe(weatherCode, isDay)
}

sealed interface WeatherFetchState {
    data object Idle : WeatherFetchState
    data class Loading(val previousSnapshot: WeatherSnapshot? = null) : WeatherFetchState
    data class Success(val snapshot: WeatherSnapshot, val isOfflineCache: Boolean = false) : WeatherFetchState
    data class Error(val message: String, val cachedSnapshot: WeatherSnapshot? = null) : WeatherFetchState

    val latestSnapshot: WeatherSnapshot?
        get() = when (this) {
            is Loading -> previousSnapshot
            is Success -> snapshot
            is Error -> cachedSnapshot
            Idle -> null
        }
}

/**
 * Interprets standard WMO weather interpretation codes returned by Open-Meteo.
 */
object WmoWeatherCodeInterpreter {
    fun isThunderstorm(code: Int): Boolean = code == 95 || code == 96 || code == 99

    fun isHeavyRain(code: Int): Boolean = code == 65 || code == 67 || code == 82

    fun isModerateOrLightRain(code: Int): Boolean =
        code in listOf(51, 53, 55, 56, 57, 61, 63, 66, 80, 81)

    fun isSnowCode(code: Int): Boolean = code in listOf(71, 73, 75, 77, 85, 86)

    fun isFogCode(code: Int): Boolean = code == 45 || code == 48

    fun isCloudyOrOvercast(code: Int): Boolean = code == 2 || code == 3

    fun describe(code: Int, isDay: Boolean): String = when (code) {
        0 -> if (isDay) "Clear Sky" else "Clear Night"
        1 -> if (isDay) "Mainly Clear" else "Mostly Clear Night"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45 -> "Fog"
        48 -> "Depositing Rime Fog"
        51 -> "Light Drizzle"
        53 -> "Moderate Drizzle"
        55 -> "Dense Drizzle"
        56, 57 -> "Freezing Drizzle"
        61 -> "Light Rain"
        63 -> "Moderate Rain"
        65 -> "Heavy Rain"
        66, 67 -> "Freezing Rain"
        71 -> "Light Snow"
        73 -> "Moderate Snow"
        75 -> "Heavy Snow"
        77 -> "Snow Grains"
        80 -> "Light Showers"
        81 -> "Moderate Showers"
        82 -> "Heavy Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with Hail"
        else -> if (isDay) "Fair" else "Night"
    }
}

/**
 * Target UI surface where weather atmosphere is rendered, enabling style-aware visual refinement.
 */
sealed class WeatherSurfaceRole {
    data object HomeBackground : WeatherSurfaceRole()
    data class FullscreenPlayer(val style: PlayerStyle) : WeatherSurfaceRole()
    data class MiniPlayer(val style: MiniPlayerStyle) : WeatherSurfaceRole()
    data object SettingsPreview : WeatherSurfaceRole()
}

/**
 * Resolved visual atmosphere blueprint produced by [WeatherAtmosphereEngine] and consumed
 * directly by the single-Canvas renderer without per-frame allocations.
 */
data class WeatherAtmospherePlan(
    val primary: PrimaryAtmosphereType,
    val isActive: Boolean,
    val isSimulatedManual: Boolean,
    val rainIntensity: RainIntensity,
    val rainDensityFactor: Float,
    val windBand: WindIntensityBand,
    val windDirectionDeg: Float,
    val windSpeedKmh: Float,
    /** Horizontal normalized drift vector (-1f..+1f) computed from actual wind direction & speed. */
    val windVectorX: Float,
    val windParticleFactor: Float,
    val snowDensityFactor: Float,
    val fogDensity: Float,
    val cloudDensity: Float,
    val sunlightStrength: Float,
    val solarPosition: SolarPosition,
    val lightningStrength: Float,
    val fireplaceStrength: Float,
    val clearNightStarStrength: Float,
    val windowDropletsEnabled: Boolean,
    val atmosphericTintArgb: Long,
    val atmosphericTintAlpha: Float,
    val summaryLabel: String
) {
    companion object {
        val INACTIVE = WeatherAtmospherePlan(
            primary = PrimaryAtmosphereType.NONE,
            isActive = false,
            isSimulatedManual = false,
            rainIntensity = RainIntensity.NONE,
            rainDensityFactor = 0f,
            windBand = WindIntensityBand.CALM,
            windDirectionDeg = 0f,
            windSpeedKmh = 0f,
            windVectorX = 0f,
            windParticleFactor = 0f,
            snowDensityFactor = 0f,
            fogDensity = 0f,
            cloudDensity = 0f,
            sunlightStrength = 0f,
            solarPosition = SolarPosition(
                azimuthDeg = 180.0,
                elevationDeg = -15.0,
                isDaylight = false,
                phase = DaylightPhase.NIGHT,
                normalizedSunX = 0.5f,
                normalizedSunY = 0.1f,
                rayDirectionRadians = 1.57f
            ),
            lightningStrength = 0f,
            fireplaceStrength = 0f,
            clearNightStarStrength = 0f,
            windowDropletsEnabled = false,
            atmosphericTintArgb = 0x00000000L,
            atmosphericTintAlpha = 0f,
            summaryLabel = "Off"
        )
    }
}
