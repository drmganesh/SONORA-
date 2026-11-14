package com.example.model

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class ShuffleMode {
    OFF,
    ON
}

enum class VisualizerStyle {
    SPECTRUM_BARS,
    WAVEFORM,
    CIRCULAR_SPECTRUM,
    AUDIO_RADAR,
    NEBULA_PARTICLES
}

enum class SpatialMode {
    HEADPHONES,
    SPEAKERS,
    CINEMA_360
}

/**
 * High-level spatial acoustic trajectory and imaging profile for Audio Studio DSP.
 */
enum class SpatialProfile(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    ORBIT(
        id = "orbit",
        displayName = "Orbit",
        subtitle = "Slow smooth stereo movement"
    ),
    DYNAMIC_360(
        id = "dynamic_360",
        displayName = "Dynamic 360°",
        subtitle = "Continuous 360° spatial motion"
    ),
    CINEMATIC(
        id = "cinematic",
        displayName = "Cinematic",
        subtitle = "Wide immersive soundstage"
    );

    companion object {
        fun fromId(id: String?): SpatialProfile {
            if (id.isNullOrBlank()) return DYNAMIC_360
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: DYNAMIC_360
        }
    }
}

/**
 * Overall spatial effect depth scaling preset.
 */
enum class SpatialIntensity(
    val id: String,
    val displayName: String,
    val multiplier: Float
) {
    SUBTLE(id = "subtle", displayName = "Subtle", multiplier = 0.58f),
    BALANCED(id = "balanced", displayName = "Balanced", multiplier = 0.85f),
    IMMERSIVE(id = "immersive", displayName = "Immersive", multiplier = 1.08f);

    companion object {
        fun fromId(id: String?): SpatialIntensity {
            if (id.isNullOrBlank()) return BALANCED
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
                ?: BALANCED
        }
    }
}

enum class ThemeMode {
    AMOLED_BLACK,
    DARK,
    LIGHT,
    DYNAMIC_YOU
}

data class SpatialPreset(
    val name: String,
    val description: String,
    val mode: SpatialMode,
    val intensity: Float,
    val soundstageWidth: Float,
    val stereoWidening: Float,
    val reverbPreset: Short = 0,
    val listenerX: Float = 0f,
    val listenerY: Float = 0f,
    val profile: SpatialProfile = SpatialProfile.DYNAMIC_360
)

data class SpatialAudioConfig(
    val isEnabled: Boolean = true,
    val mode: SpatialMode = SpatialMode.HEADPHONES,
    val intensity: Float = 0.75f, // 0.0 to 1.0
    val soundstageWidth: Float = 0.85f, // 0.0 to 1.0 (narrow to wide 360)
    val listenerX: Float = 0f, // -1.0 to 1.0
    val listenerY: Float = 0f, // -1.0 to 1.0
    val virtualSurround: Boolean = true,
    val stereoWidening: Float = 0.80f,
    val channelBalance: Float = 0.0f, // -1.0 (Left) to +1.0 (Right)
    val twsWideStereoEnabled: Boolean = true, // TWS ultra-wide stereo expansion
    val twsStereoBoost: Float = 0.90f, // 0.0 to 1.5 TWS widening boost factor
    val spatialProfile: SpatialProfile = SpatialProfile.DYNAMIC_360,
    val spatialIntensity: SpatialIntensity = SpatialIntensity.BALANCED
)

data class PlaybackInfo(
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val playbackPitch: Float = 1.0f,
    val sleepTimerMinutesRemaining: Int? = null
)
