package com.example.equalizer

data class SoundProfile(
    val id: String,
    val name: String,
    val description: String,
    val tilt: Float,
    val contour: Float,
    val macroProfile: MacroProfile = MacroProfile.BROAD,
    val manualGains: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SoundProfile
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

object EqualizerPresets {

    val PROFILES: List<SoundProfile> = listOf(
        SoundProfile(
            id = "balanced",
            name = "Balanced",
            description = "Neutral studio reference curve with zero coloration across all frequencies",
            tilt = 0f,
            contour = 0f,
            macroProfile = MacroProfile.BROAD
        ),
        SoundProfile(
            id = "warm",
            name = "Warm",
            description = "Enriched analog low-end warmth with silky, rolled-off high frequencies",
            tilt = -0.55f,
            contour = 0.05f,
            macroProfile = MacroProfile.BROAD
        ),
        SoundProfile(
            id = "forward",
            name = "Forward",
            description = "Brings vocals, guitars, and lead instruments into prominent, direct focus",
            tilt = 0.05f,
            contour = 0.50f,
            macroProfile = MacroProfile.FOCUSED
        ),
        SoundProfile(
            id = "bright",
            name = "Bright",
            description = "Crisp upper treble brilliance and airy acoustic shimmer",
            tilt = 0.60f,
            contour = 0.0f,
            macroProfile = MacroProfile.BROAD
        ),
        SoundProfile(
            id = "broad",
            name = "Broad",
            description = "Panoramic open soundstage with gentle bass warmth and spacious air",
            tilt = 0.0f,
            contour = -0.55f,
            macroProfile = MacroProfile.BROAD
        ),
        SoundProfile(
            id = "focused",
            name = "Focused",
            description = "Controlled midrange definition with heightened musical precision",
            tilt = 0.0f,
            contour = 0.65f,
            macroProfile = MacroProfile.FOCUSED
        ),
        SoundProfile(
            id = "deep",
            name = "Deep",
            description = "Thumping sub-bass impact and resonant depth for electronic and modern beats",
            tilt = -0.45f,
            contour = -0.40f,
            macroProfile = MacroProfile.BROAD
        ),
        SoundProfile(
            id = "natural",
            name = "Natural",
            description = "Gentle acoustic compensation for authentic, fatigue-free audiophile listening",
            tilt = -0.15f,
            contour = -0.10f,
            macroProfile = MacroProfile.BROAD
        )
    )

    fun getById(id: String): SoundProfile {
        return PROFILES.find { it.id == id } ?: PROFILES[0]
    }
}
