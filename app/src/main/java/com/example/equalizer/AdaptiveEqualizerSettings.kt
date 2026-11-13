package com.example.equalizer

data class AdaptiveEqualizerSettings(
    val isEnabled: Boolean = true,
    val mode: EqualizerMode = EqualizerMode.DYNAMIC,
    val tilt: Float = 0f,
    val contour: Float = 0f,
    val macroProfile: MacroProfile = MacroProfile.BROAD,
    val selectedProfileId: String = "balanced",
    val manualBands: FloatArray = FloatArray(15) { 0f },
    val volumeBoostDb: Float = 0f
) {
    /**
     * Resolves the actual effective 15-band gains based on the active mode (Dynamic or Manual).
     */
    fun getEffectiveGains(): FloatArray {
        return if (mode == EqualizerMode.DYNAMIC) {
            DynamicSoundMapper.calculateGains(tilt, contour, macroProfile)
        } else {
            manualBands.copyOf()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AdaptiveEqualizerSettings
        return isEnabled == other.isEnabled &&
                mode == other.mode &&
                tilt == other.tilt &&
                contour == other.contour &&
                macroProfile == other.macroProfile &&
                selectedProfileId == other.selectedProfileId &&
                manualBands.contentEquals(other.manualBands) &&
                volumeBoostDb == other.volumeBoostDb
    }

    override fun hashCode(): Int {
        var result = isEnabled.hashCode()
        result = 31 * result + mode.hashCode()
        result = 31 * result + tilt.hashCode()
        result = 31 * result + contour.hashCode()
        result = 31 * result + macroProfile.hashCode()
        result = 31 * result + selectedProfileId.hashCode()
        result = 31 * result + manualBands.contentHashCode()
        result = 31 * result + volumeBoostDb.hashCode()
        return result
    }
}
