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
    val listenerY: Float = 0f
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
    val twsStereoBoost: Float = 0.90f // 0.0 to 1.0 TWS widening boost factor
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
