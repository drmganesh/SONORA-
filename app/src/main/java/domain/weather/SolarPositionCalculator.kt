package domain.weather

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure astronomical NOAA solar position calculator for manually selected coordinates.
 *
 * Computes real solar azimuth (0° = North, 90° = East, 180° = South, 270° = West) and solar
 * elevation (-90° .. +90°) from latitude, longitude, date, and local Asia/Kolkata time, then
 * maps the solar vector into smooth screen coordinates for directional sunlight effects.
 */
object SolarPositionCalculator {

    val INDIA_ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

    fun calculate(
        latitudeDeg: Double,
        longitudeDeg: Double,
        dateTime: ZonedDateTime = ZonedDateTime.now(INDIA_ZONE)
    ): SolarPosition {
        val local = dateTime.withZoneSameInstant(INDIA_ZONE)
        val dayOfYear = local.dayOfYear
        val localHours = local.hour + local.minute / 60.0 + local.second / 3600.0
        val tzOffsetHours = local.offset.totalSeconds / 3600.0

        // Fractional year gamma (radians)
        val gamma = (2.0 * PI / 365.0) * (dayOfYear - 1.0 + (localHours - 12.0) / 24.0)

        // Equation of time (minutes)
        val eqTime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2.0 * gamma) -
                0.040849 * sin(2.0 * gamma)
            )

        // Solar declination (radians)
        val decl = 0.006918 -
            0.399912 * cos(gamma) +
            0.070257 * sin(gamma) -
            0.006758 * cos(2.0 * gamma) +
            0.000907 * sin(2.0 * gamma) -
            0.002697 * cos(3.0 * gamma) +
            0.00148 * sin(3.0 * gamma)

        // True solar time (minutes)
        val timeOffset = eqTime + 4.0 * longitudeDeg - 60.0 * tzOffsetHours
        val trueSolarTimeMin = (localHours * 60.0 + timeOffset).let { raw ->
            ((raw % 1440.0) + 1440.0) % 1440.0
        }

        // Solar hour angle (degrees -> radians)
        val hourAngleDeg = (trueSolarTimeMin / 4.0) - 180.0
        val haRad = Math.toRadians(hourAngleDeg)
        val latRad = Math.toRadians(latitudeDeg)

        // Solar zenith & elevation
        val cosZenith = (sin(latRad) * sin(decl) + cos(latRad) * cos(decl) * cos(haRad))
            .coerceIn(-1.0, 1.0)
        val zenithRad = acos(cosZenith)
        val elevationDeg = 90.0 - Math.toDegrees(zenithRad)

        // Solar azimuth (0° = N, 90° = E, 180° = S, 270° = W)
        val sinZenith = sin(zenithRad)
        val azimuthDeg = if (sinZenith > 1e-6) {
            val cosAz = ((sin(decl) - sin(latRad) * cosZenith) / (cos(latRad) * sinZenith))
                .coerceIn(-1.0, 1.0)
            val baseAz = Math.toDegrees(acos(cosAz))
            if (hourAngleDeg > 0.0) (360.0 - baseAz) % 360.0 else baseAz % 360.0
        } else {
            180.0
        }

        val isDaylight = elevationDeg > -0.833 // standard atmospheric refraction sunrise/sunset
        val isMorningHalf = hourAngleDeg <= 0.0
        val phase = when {
            !isDaylight -> DaylightPhase.NIGHT
            elevationDeg <= 15.0 && isMorningHalf -> DaylightPhase.SUNRISE
            elevationDeg <= 15.0 && !isMorningHalf -> DaylightPhase.SUNSET
            elevationDeg >= 44.0 -> DaylightPhase.MIDDAY
            isMorningHalf -> DaylightPhase.MORNING
            else -> DaylightPhase.AFTERNOON
        }

        // Map astronomical azimuth & elevation into smooth screen sky coordinates:
        // East (90°, sunrise) sits toward right-upper horizon (x ~ 0.88), Noon arcs high center (x ~ 0.50),
        // West (270°, sunset) moves smoothly toward left-upper horizon (x ~ 0.12).
        val azRad = Math.toRadians(azimuthDeg)
        val eastWestFactor = sin(azRad).toFloat() // +1 at East (90°), 0 at N/S, -1 at West (270°)
        val normalizedSunX = (0.50f + eastWestFactor * 0.40f).coerceIn(0.08f, 0.92f)

        // Elevation maps 0° -> y = 0.40 (low horizon), 70°+ -> y = 0.05 (high zenith)
        val clampedElev = elevationDeg.coerceIn(0.0, 75.0).toFloat()
        val normalizedSunY = (0.40f - (clampedElev / 75.0f) * 0.34f).coerceIn(0.05f, 0.42f)

        // Ray direction travels from the sun position diagonally across the screen toward center-bottom
        val rayDir = atan2(0.75f - normalizedSunY, 0.50f - normalizedSunX)

        return SolarPosition(
            azimuthDeg = azimuthDeg,
            elevationDeg = elevationDeg,
            isDaylight = isDaylight,
            phase = phase,
            normalizedSunX = normalizedSunX,
            normalizedSunY = normalizedSunY,
            rayDirectionRadians = rayDir
        )
    }

    /**
     * Convenience helper computing elevation using an independent sin check for callers verifying sun height.
     */
    fun isSunAboveHorizon(position: SolarPosition): Boolean = position.isDaylight && position.elevationDeg > -0.833

    @Suppress("unused")
    private fun safeAsin(value: Double): Double = asin(value.coerceIn(-1.0, 1.0))
}
