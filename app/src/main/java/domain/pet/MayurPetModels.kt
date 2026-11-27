package domain.pet

import androidx.compose.runtime.Immutable
import java.util.Locale

/**
 * Dance motion intensity options for Mayur 3D virtual pet.
 */
enum class MayurDanceIntensity(
    val id: String,
    val label: String,
    val amplitudeMultiplier: Float,
    val speedMultiplier: Float
) {
    LOW("low", "Low", 0.60f, 0.82f),
    NORMAL("normal", "Normal", 1.0f, 1.0f),
    HIGH("high", "High", 1.32f, 1.18f);

    companion object {
        val DEFAULT = NORMAL
        fun fromId(raw: String?): MayurDanceIntensity =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Animation quality and battery efficiency tiers for Mayur 3D virtual pet.
 */
enum class MayurAnimationQuality(
    val id: String,
    val label: String,
    val subtitle: String,
    val audioSampleIntervalMs: Long,
    val targetFrameIntervalMs: Long,
    val tailFeatherCount: Int,
    val maxAccentParticles: Int,
    val allowAmbientGlow: Boolean
) {
    BATTERY_SAVER(
        id = "battery_saver",
        label = "Battery Saver",
        subtitle = "30fps cap & lightweight feather detail",
        audioSampleIntervalMs = 135L,
        targetFrameIntervalMs = 33L,
        tailFeatherCount = 5,
        maxAccentParticles = 2,
        allowAmbientGlow = false
    ),
    BALANCED(
        id = "balanced",
        label = "Balanced",
        subtitle = "Smooth 360° motion with low battery draw",
        audioSampleIntervalMs = 75L,
        targetFrameIntervalMs = 20L,
        tailFeatherCount = 7,
        maxAccentParticles = 4,
        allowAmbientGlow = true
    ),
    HIGH(
        id = "high",
        label = "High",
        subtitle = "Full 60fps 3D feather & lighting detail",
        audioSampleIntervalMs = 50L,
        targetFrameIntervalMs = 16L,
        tailFeatherCount = 9,
        maxAccentParticles = 6,
        allowAmbientGlow = true
    );

    companion object {
        val DEFAULT = BALANCED
        fun fromId(raw: String?): MayurAnimationQuality =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Persisted configuration for Settings -> Virtual Pet -> Mayur.
 */
@Immutable
data class MayurPetSettings(
    val enabled: Boolean = true,
    val touchReactionsEnabled: Boolean = true,
    val musicDancingEnabled: Boolean = true,
    val danceIntensity: MayurDanceIntensity = MayurDanceIntensity.DEFAULT,
    val nighttimeSleepEnabled: Boolean = true,
    /** Minutes since midnight (0..1439). Default 22:00 (10:00 PM). */
    val bedtimeMinutes: Int = DEFAULT_BEDTIME_MINUTES,
    /** Minutes since midnight (0..1439). Default 06:30 (6:30 AM). */
    val wakeUpMinutes: Int = DEFAULT_WAKEUP_MINUTES,
    val weatherCostumesEnabled: Boolean = true,
    val festivalCostumesEnabled: Boolean = true,
    val animationQuality: MayurAnimationQuality = MayurAnimationQuality.DEFAULT
) {
    fun isWithinSleepWindow(localMinutesOfDay: Int): Boolean {
        if (!nighttimeSleepEnabled) return false
        val minute = ((localMinutesOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val start = bedtimeMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        val end = wakeUpMinutes.coerceIn(0, MINUTES_PER_DAY - 1)
        if (start == end) return false
        return if (start > end) {
            minute >= start || minute < end
        } else {
            minute in start until end
        }
    }

    fun summarySubtitle(): String {
        if (!enabled) return "Off • Hidden on Home screen"
        val parts = mutableListOf<String>()
        parts += "3D 360°"
        if (musicDancingEnabled) parts += "${danceIntensity.label} dance"
        if (weatherCostumesEnabled || festivalCostumesEnabled) parts += "Costumes"
        if (nighttimeSleepEnabled) parts += "Sleep ${formatMinutesAsClock(bedtimeMinutes)}"
        return parts.joinToString(" • ")
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        const val DEFAULT_BEDTIME_MINUTES = 22 * 60 // 10:00 PM
        const val DEFAULT_WAKEUP_MINUTES = 6 * 60 + 30 // 6:30 AM

        val DEFAULT = MayurPetSettings()

        fun formatMinutesAsClock(minutesOfDay: Int): String {
            val normalized = ((minutesOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
            val hour24 = normalized / 60
            val minute = normalized % 60
            val suffix = if (hour24 >= 12) "PM" else "AM"
            val hour12 = when {
                hour24 == 0 -> 12
                hour24 > 12 -> hour24 - 12
                else -> hour24
            }
            return String.format(Locale.US, "%d:%02d %s", hour12, minute, suffix)
        }
    }
}

enum class MayurExpression(val label: String) {
    HAPPY("Happy"),
    CURIOUS("Curious"),
    SLEEPY("Sleepy"),
    EXCITED("Excited"),
    IRRITATED("Sleepy Grump"),
    RELAXED("Relaxed")
}

enum class MayurTouchZone {
    HEAD,
    HEADPHONES,
    TAIL,
    BODY
}

/**
 * Seven genre- and energy-responsive 3D dance styles synchronized to active audio playback.
 */
enum class MayurDanceStyle(val label: String) {
    POP("Pop Groove"),
    CLASSICAL("Classical Grace"),
    ELECTRONIC("Electronic Pulse"),
    HIP_HOP("Hip-Hop Flow"),
    LO_FI("Lo-Fi Chill"),
    ROMANTIC("Romantic Sway"),
    HIGH_ENERGY("High-Energy Rhythm")
}

/**
 * Natural idle sub-behaviors cycled organically when no music is playing.
 */
enum class MayurIdleAction {
    NONE,
    LOOK_AROUND,
    ADJUST_HEADPHONES,
    PLAY_WITH_TAIL,
    PLAYFUL_HOP,
    LOOK_BACK_OVER_SHOULDER
}

enum class MayurCostume(
    val displayLabel: String,
    val isFestival: Boolean = false,
    val isWeather: Boolean = false,
    val isNight: Boolean = false
) {
    DAYTIME("Daytime"),
    NIGHTTIME("Nighttime Cozy", isNight = true),
    RAIN("Raincoat & Umbrella", isWeather = true),
    COLD("Cozy Scarf", isWeather = true),
    HOT_SUNNY("Sunny Shades", isWeather = true),
    WIND("Windy Sway", isWeather = true),
    SNOWFALL("Winter Snow", isWeather = true),
    THUNDERSTORM("Storm Shelter", isWeather = true),
    FESTIVAL_DIWALI("Diwali Diya", isFestival = true),
    FESTIVAL_HOLI("Holi Colors", isFestival = true),
    FESTIVAL_RATH_YATRA("Rath Yatra", isFestival = true),
    FESTIVAL_DUSSEHRA("Dussehra Festive", isFestival = true),
    FESTIVAL_KARTIK_PURNIMA("Kartik Boita", isFestival = true),
    FESTIVAL_GANESH("Ganesh Festive", isFestival = true),
    FESTIVAL_NAVRATRI("Navratri Festive", isFestival = true),
    FESTIVAL_JANMASHTAMI("Janmashtami", isFestival = true),
    FESTIVAL_HARVEST("Harvest Festive", isFestival = true),
    FESTIVAL_REGIONAL("Festive Attire", isFestival = true)
}

enum class MayurReactionType {
    HEAD_PAT_HAPPY,
    TURN_TO_TOUCH,
    ADJUST_HEADPHONES,
    TAIL_FAN_SHAKE,
    DOUBLE_TAP_HAPPY,
    SPECIAL_LONG_PRESS,
    SLEEPY_WAKE_IRRITATED
}

@Immutable
data class MayurReactionEvent(
    val id: Long,
    val type: MayurReactionType,
    val expression: MayurExpression,
    val zone: MayurTouchZone,
    val variantIndex: Int,
    val durationMs: Long,
    /** Normalized touch X (-1f left .. +1f right) so Mayur can turn naturally toward the tap. */
    val touchNormalizedX: Float = 0f,
    /** Normalized touch Y (-1f top .. +1f bottom) so Mayur can tilt gaze toward the tap. */
    val touchNormalizedY: Float = 0f
)

@Immutable
data class MayurAudioTelemetry(
    val overallAmplitude: Float = 0f,
    val bassEnergy: Float = 0f,
    val midEnergy: Float = 0f,
    val trebleEnergy: Float = 0f,
    val beatPulse: Float = 0f,
    val isBeatHit: Boolean = false
) {
    companion object {
        val SILENT = MayurAudioTelemetry()
    }
}

@Immutable
data class MayurSleepResolution(
    val isSleepWindow: Boolean,
    val isSleeping: Boolean,
    val isDrowsy: Boolean
)
