package domain.pet

import com.example.model.Song
import domain.festival.ActiveFestivalResolution
import domain.festival.FestivalEffectId
import domain.weather.PrimaryAtmosphereType
import domain.weather.WeatherAtmospherePlan
import domain.weather.WeatherSnapshot
import domain.weather.WindIntensityBand
import domain.weather.WmoWeatherCodeInterpreter
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pure, deterministic behavior engine for the 3D MAYUR Virtual Pet.
 * Handles beat-onset audio analysis, 7-style genre-synced dance selection,
 * sleep window evaluation, dynamic weather/festival costume mapping, and touch reaction dispatch.
 */
object MayurPetBehaviorEngine {

    private val CLASSICAL_KEYWORDS = listOf(
        "classical", "raga", "carnatic", "hindustani", "bhajan", "devotional", "mantra",
        "stotra", "aarti", "sitar", "flute", "bansuri", "veena", "odissi", "kirtan",
        "symphony", "orchestra", "violin", "cello", "instrumental", "baroque", "opera"
    )

    private val ELECTRONIC_KEYWORDS = listOf(
        "edm", "electronic", "synth", "synthwave", "techno", "house", "trance", "dubstep",
        "electro", "cyber", "dnb", "drum and bass", "future bass", "club", "dj", "remix", "phonk"
    )

    private val HIP_HOP_KEYWORDS = listOf(
        "hip hop", "hip-hop", "hiphop", "rap", "trap", "drill", "boom bap", "freestyle",
        "cypher", "r&b", "rnb", "gully", "desihop"
    )

    private val LOFI_KEYWORDS = listOf(
        "lofi", "lo-fi", "chill", "chillhop", "study", "ambient", "sleep", "meditation",
        "rain", "cafe", "beats to relax", "calm", "soft", "acoustic", "unplugged"
    )

    private val ROMANTIC_KEYWORDS = listOf(
        "romantic", "love", "ishq", "pyaar", "pyar", "mohabbat", "dil", "ballad",
        "melody", "soul", "slow", "ghazal", "sufi", "heart", "valentine", "serenade"
    )

    private val HIGH_ENERGY_KEYWORDS = listOf(
        "workout", "rock", "metal", "punk", "bhangra", "garba", "dandiya", "mass",
        "party", "fast", "hype", "festival mix", "energetic", "pump", "power", "anthem"
    )

    private val POP_KEYWORDS = listOf(
        "pop", "indie pop", "k-pop", "kpop", "bollywood", "tollywood", "kollywood",
        "filmi", "dance", "chart", "hit", "top", "disco", "funk"
    )

    /**
     * Resolves Mayur's sleep state using device local time and user schedule.
     */
    fun resolveSleepState(
        settings: MayurPetSettings,
        localMinutesOfDay: Int,
        recentlyDisturbedWhileSleeping: Boolean
    ): MayurSleepResolution {
        val inSleepWindow = settings.isWithinSleepWindow(localMinutesOfDay)
        if (!inSleepWindow) {
            return MayurSleepResolution(
                isSleepWindow = false,
                isSleeping = false,
                isDrowsy = false
            )
        }
        return MayurSleepResolution(
            isSleepWindow = true,
            isSleeping = !recentlyDisturbedWhileSleeping,
            isDrowsy = true
        )
    }

    /**
     * Resolves Mayur's dynamic costume using existing app Festival Calendar and Weather data.
     */
    fun resolveCostume(
        settings: MayurPetSettings,
        isSleepWindow: Boolean,
        weatherPlan: WeatherAtmospherePlan,
        weatherSnapshot: WeatherSnapshot?,
        activeFestival: ActiveFestivalResolution
    ): MayurCostume {
        if (settings.festivalCostumesEnabled && activeFestival.hasActiveFestival) {
            val primary = activeFestival.primary
            if (primary != null) {
                return resolveFestivalCostume(
                    effectId = primary.record.effectId,
                    festivalSlug = primary.record.festivalSlug,
                    festivalName = primary.displayNameForRegion
                )
            }
        }

        if (settings.nighttimeSleepEnabled && isSleepWindow) {
            return MayurCostume.NIGHTTIME
        }

        if (settings.weatherCostumesEnabled) {
            resolveWeatherCostume(weatherPlan, weatherSnapshot)?.let { return it }
        }

        return MayurCostume.DAYTIME
    }

    private fun resolveFestivalCostume(
        effectId: FestivalEffectId,
        festivalSlug: String,
        festivalName: String
    ): MayurCostume {
        val lower = "${festivalSlug.lowercase()} ${festivalName.lowercase()}"
        return when (effectId) {
            FestivalEffectId.DIWALI_FIREWORKS -> MayurCostume.FESTIVAL_DIWALI
            FestivalEffectId.HOLI_GULAL -> MayurCostume.FESTIVAL_HOLI
            FestivalEffectId.RATH_YATRA_CHARIOT -> MayurCostume.FESTIVAL_RATH_YATRA
            FestivalEffectId.DUSSEHRA_RAMA_ARROW -> MayurCostume.FESTIVAL_DUSSEHRA
            FestivalEffectId.KARTIK_BOITA_BANDANA -> MayurCostume.FESTIVAL_KARTIK_PURNIMA
            FestivalEffectId.GANESH_DIYA_FLOWERS -> MayurCostume.FESTIVAL_GANESH
            FestivalEffectId.DURGA_NAVRATRI_PETALS -> MayurCostume.FESTIVAL_NAVRATRI
            FestivalEffectId.JANMASHTAMI_FLUTE_PEACOCK -> MayurCostume.FESTIVAL_JANMASHTAMI
            FestivalEffectId.SANKRANTI_KITES,
            FestivalEffectId.PONGAL_KOLAM_STEAM,
            FestivalEffectId.ONAM_POOKALAM_BOAT,
            FestivalEffectId.BIHU_FESTIVE_PATTERN -> MayurCostume.FESTIVAL_HARVEST
            FestivalEffectId.SHIVARATRI_SACRED_NIGHT,
            FestivalEffectId.SPRING_BLOOM_PETALS,
            FestivalEffectId.RIVER_DIYA_GANGA,
            FestivalEffectId.GOLDEN_AARTI_LAMPS -> when {
                "diwali" in lower || "deepavali" in lower -> MayurCostume.FESTIVAL_DIWALI
                "holi" in lower -> MayurCostume.FESTIVAL_HOLI
                "rath" in lower -> MayurCostume.FESTIVAL_RATH_YATRA
                "dussehra" in lower || "vijayadashami" in lower -> MayurCostume.FESTIVAL_DUSSEHRA
                "kartik" in lower || "boita" in lower -> MayurCostume.FESTIVAL_KARTIK_PURNIMA
                else -> MayurCostume.FESTIVAL_REGIONAL
            }
            FestivalEffectId.NONE -> MayurCostume.FESTIVAL_REGIONAL
        }
    }

    private fun resolveWeatherCostume(
        weatherPlan: WeatherAtmospherePlan,
        weatherSnapshot: WeatherSnapshot?
    ): MayurCostume? {
        if (weatherPlan.isActive && weatherPlan.primary != PrimaryAtmosphereType.NONE) {
            return when (weatherPlan.primary) {
                PrimaryAtmosphereType.THUNDERSTORM -> MayurCostume.THUNDERSTORM
                PrimaryAtmosphereType.HEAVY_RAIN,
                PrimaryAtmosphereType.MODERATE_OR_LIGHT_RAIN -> MayurCostume.RAIN
                PrimaryAtmosphereType.SNOWFALL -> MayurCostume.SNOWFALL
                PrimaryAtmosphereType.STRONG_WIND -> MayurCostume.WIND
                PrimaryAtmosphereType.COLD_NIGHT_FIREPLACE -> MayurCostume.COLD
                PrimaryAtmosphereType.SUNNY_DAY -> {
                    val temp = weatherSnapshot?.temperatureC
                    if (temp == null || temp >= 29.0 || weatherPlan.sunlightStrength >= 0.45f) {
                        MayurCostume.HOT_SUNNY
                    } else {
                        null
                    }
                }
                PrimaryAtmosphereType.FOG_OR_MIST,
                PrimaryAtmosphereType.CLOUDY_OVERCAST,
                PrimaryAtmosphereType.CLEAR_NIGHT -> {
                    val temp = weatherSnapshot?.temperatureC
                    when {
                        temp != null && temp <= 15.0 -> MayurCostume.COLD
                        weatherPlan.windBand == WindIntensityBand.STRONG ||
                            weatherPlan.windBand == WindIntensityBand.VERY_STRONG -> MayurCostume.WIND
                        else -> null
                    }
                }
                PrimaryAtmosphereType.NONE -> null
            }
        }

        val snap = weatherSnapshot ?: return null
        val code = snap.weatherCode
        return when {
            WmoWeatherCodeInterpreter.isThunderstorm(code) -> MayurCostume.THUNDERSTORM
            WmoWeatherCodeInterpreter.isHeavyRain(code) ||
                WmoWeatherCodeInterpreter.isModerateOrLightRain(code) ||
                snap.totalLiquidRainMm >= 0.3 -> MayurCostume.RAIN
            snap.snowfallCm > 0.0 || WmoWeatherCodeInterpreter.isSnowCode(code) -> MayurCostume.SNOWFALL
            snap.effectiveWindKmh >= 24.0 -> MayurCostume.WIND
            snap.temperatureC <= 15.0 -> MayurCostume.COLD
            snap.isDay && snap.temperatureC >= 31.0 -> MayurCostume.HOT_SUNNY
            else -> null
        }
    }

    /**
     * Extracts real audio energy bands and detects bass beat onset spikes against a moving baseline.
     * Zero intermediate list allocations; runs in O(N).
     */
    fun computeAudioEnergy(
        frequencies: FloatArray,
        waveform: FloatArray,
        previousBassEma: Float = 0f
    ): MayurAudioTelemetry {
        var bassSum = 0f
        var bassCount = 0
        var midSum = 0f
        var midCount = 0
        var trebleSum = 0f
        var trebleCount = 0
        var freqTotal = 0f

        if (frequencies.isNotEmpty()) {
            val bassEnd = (frequencies.size / 4).coerceAtLeast(1)
            val trebleStart = (frequencies.size * 2 / 3).coerceAtMost(frequencies.size - 1)
            for (i in frequencies.indices) {
                val v = frequencies[i].coerceIn(0f, 1f)
                freqTotal += v
                when {
                    i < bassEnd -> {
                        bassSum += v
                        bassCount++
                    }
                    i >= trebleStart -> {
                        trebleSum += v
                        trebleCount++
                    }
                    else -> {
                        midSum += v
                        midCount++
                    }
                }
            }
        }

        var waveSqSum = 0f
        if (waveform.isNotEmpty()) {
            for (i in waveform.indices) {
                val s = waveform[i].coerceIn(-1f, 1f)
                waveSqSum += s * s
            }
        }

        val freqAvg = if (frequencies.isNotEmpty()) freqTotal / frequencies.size else 0f
        val waveRms = if (waveform.isNotEmpty()) sqrt(waveSqSum / waveform.size) else 0f
        val bass = ((if (bassCount > 0) bassSum / bassCount else waveRms * 0.85f) * 1.25f).coerceIn(0f, 1f)
        val mid = ((if (midCount > 0) midSum / midCount else waveRms * 0.78f) * 1.18f).coerceIn(0f, 1f)
        val treble = ((if (trebleCount > 0) trebleSum / trebleCount else waveRms * 0.65f) * 1.20f).coerceIn(0f, 1f)
        val overall = maxOf(freqAvg * 1.15f, waveRms * 1.35f).coerceIn(0f, 1f)

        val threshold = maxOf(0.18f, previousBassEma * 1.24f)
        val beatHit = bass >= threshold && (bass - previousBassEma) >= 0.05f
        val beatPulse = if (beatHit) {
            ((bass - previousBassEma) * 3.2f + bass * 0.45f).coerceIn(0.35f, 1f)
        } else {
            (bass * 0.55f).coerceIn(0f, 0.75f)
        }

        return MayurAudioTelemetry(
            overallAmplitude = overall,
            bassEnergy = bass,
            midEnergy = mid,
            trebleEnergy = treble,
            beatPulse = beatPulse,
            isBeatHit = beatHit
        )
    }

    /**
     * Selects among all 7 dance styles based on song metadata and real audio energy.
     */
    fun selectDanceStyle(
        song: Song?,
        telemetry: MayurAudioTelemetry,
        intensity: MayurDanceIntensity,
        phaseBucket: Int
    ): MayurDanceStyle {
        val searchable = buildString {
            song?.genre?.let { append(it.lowercase()).append(' ') }
            song?.title?.let { append(it.lowercase()).append(' ') }
            song?.artist?.let { append(it.lowercase()).append(' ') }
            song?.album?.let { append(it.lowercase()) }
        }
        val bucket = abs(phaseBucket)

        if (searchable.isNotBlank()) {
            when {
                CLASSICAL_KEYWORDS.any { it in searchable } -> return MayurDanceStyle.CLASSICAL
                ELECTRONIC_KEYWORDS.any { it in searchable } -> return MayurDanceStyle.ELECTRONIC
                HIP_HOP_KEYWORDS.any { it in searchable } -> return MayurDanceStyle.HIP_HOP
                LOFI_KEYWORDS.any { it in searchable } -> return MayurDanceStyle.LO_FI
                ROMANTIC_KEYWORDS.any { it in searchable } -> return MayurDanceStyle.ROMANTIC
                HIGH_ENERGY_KEYWORDS.any { it in searchable } -> return MayurDanceStyle.HIGH_ENERGY
                POP_KEYWORDS.any { it in searchable } -> return MayurDanceStyle.POP
            }
        }

        // Dynamic fallback driven purely by real measured audio energy characteristics
        return when {
            telemetry.overallAmplitude >= 0.62f ||
                (telemetry.bassEnergy >= 0.64f && intensity == MayurDanceIntensity.HIGH) ->
                if (bucket % 2 == 0) MayurDanceStyle.HIGH_ENERGY else MayurDanceStyle.ELECTRONIC
            telemetry.bassEnergy >= 0.44f && telemetry.trebleEnergy >= 0.36f ->
                MayurDanceStyle.ELECTRONIC
            telemetry.bassEnergy >= 0.38f && telemetry.midEnergy >= 0.25f ->
                MayurDanceStyle.HIP_HOP
            telemetry.overallAmplitude in 0.01f..0.18f ->
                if (bucket % 2 == 0) MayurDanceStyle.LO_FI else MayurDanceStyle.ROMANTIC
            telemetry.overallAmplitude in 0.18f..0.32f && telemetry.bassEnergy < 0.28f ->
                MayurDanceStyle.ROMANTIC
            else -> MayurDanceStyle.POP
        }
    }

    /**
     * Resolves an interactive 3D reaction for taps, double taps, long presses, and sleep wakes.
     */
    fun resolveTouchReaction(
        zone: MayurTouchZone,
        isDoubleTap: Boolean,
        isLongPress: Boolean,
        isSleeping: Boolean,
        variantSeed: Int,
        eventId: Long,
        touchNormalizedX: Float = 0f,
        touchNormalizedY: Float = 0f
    ): MayurReactionEvent {
        val variant = abs(variantSeed) % 3

        if (isSleeping) {
            return MayurReactionEvent(
                id = eventId,
                type = MayurReactionType.SLEEPY_WAKE_IRRITATED,
                expression = if (isDoubleTap || variant == 1) MayurExpression.IRRITATED else MayurExpression.SLEEPY,
                zone = zone,
                variantIndex = variant,
                durationMs = 2600L,
                touchNormalizedX = touchNormalizedX,
                touchNormalizedY = touchNormalizedY
            )
        }

        if (isLongPress) {
            return MayurReactionEvent(
                id = eventId,
                type = MayurReactionType.SPECIAL_LONG_PRESS,
                expression = MayurExpression.EXCITED,
                zone = zone,
                variantIndex = variant,
                durationMs = 2000L,
                touchNormalizedX = touchNormalizedX,
                touchNormalizedY = touchNormalizedY
            )
        }

        if (isDoubleTap) {
            return MayurReactionEvent(
                id = eventId,
                type = MayurReactionType.DOUBLE_TAP_HAPPY,
                expression = MayurExpression.EXCITED,
                zone = zone,
                variantIndex = variant,
                durationMs = 1650L,
                touchNormalizedX = touchNormalizedX,
                touchNormalizedY = touchNormalizedY
            )
        }

        return when (zone) {
            MayurTouchZone.HEAD -> MayurReactionEvent(
                id = eventId,
                type = MayurReactionType.HEAD_PAT_HAPPY,
                expression = MayurExpression.HAPPY,
                zone = zone,
                variantIndex = variant,
                durationMs = 1200L,
                touchNormalizedX = touchNormalizedX,
                touchNormalizedY = touchNormalizedY
            )
            MayurTouchZone.HEADPHONES -> MayurReactionEvent(
                id = eventId,
                type = MayurReactionType.ADJUST_HEADPHONES,
                expression = if (variant == 2) MayurExpression.HAPPY else MayurExpression.CURIOUS,
                zone = zone,
                variantIndex = variant,
                durationMs = 1300L,
                touchNormalizedX = touchNormalizedX,
                touchNormalizedY = touchNormalizedY
            )
            MayurTouchZone.TAIL -> MayurReactionEvent(
                id = eventId,
                type = MayurReactionType.TAIL_FAN_SHAKE,
                expression = if (variant == 0) MayurExpression.EXCITED else MayurExpression.HAPPY,
                zone = zone,
                variantIndex = variant,
                durationMs = 1450L,
                touchNormalizedX = touchNormalizedX,
                touchNormalizedY = touchNormalizedY
            )
            MayurTouchZone.BODY -> MayurReactionEvent(
                id = eventId,
                type = MayurReactionType.TURN_TO_TOUCH,
                expression = if (variant == 1) MayurExpression.CURIOUS else MayurExpression.HAPPY,
                zone = zone,
                variantIndex = variant,
                durationMs = 1200L,
                touchNormalizedX = touchNormalizedX,
                touchNormalizedY = touchNormalizedY
            )
        }
    }
}
