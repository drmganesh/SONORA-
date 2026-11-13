package ui.player.seekbar

/**
 * Ten expressive seekbar styles supported in SONORA music player.
 */
enum class SeekbarStyle(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String
) {
    BLOCKS(
        id = "blocks",
        displayName = "Blocks",
        subtitle = "Segmented Rectangular Bars",
        description = "Segmented rectangular blocks with highlighted active segments and crisp gaps."
    ),
    WAVEFORM(
        id = "waveform",
        displayName = "Waveform",
        subtitle = "Dynamic Audio Spectrum",
        description = "Multi-height audio waveform bars reactive to live PCM playback with filled progress."
    ),
    WAVE_LINE(
        id = "wave_line",
        displayName = "Wave Line",
        subtitle = "Sinusoidal Fluid Wave",
        description = "A smooth sinusoidal wave line that subtly undulates during playback."
    ),
    CLASSIC(
        id = "classic",
        displayName = "Classic",
        subtitle = "Traditional Horizontal Bar",
        description = "Clean, minimal horizontal progress bar with rounded edges and tactile thumb."
    ),
    DOTS(
        id = "dots",
        displayName = "Dots",
        subtitle = "Discrete Circular Sequence",
        description = "A sequence of circular dots highlighted across playback duration."
    ),
    GRADIENT(
        id = "gradient",
        displayName = "Gradient",
        subtitle = "Multicolor Vivid Spectrum",
        description = "Modern smooth gradient bar transitioning through dynamic artwork accent tones."
    ),
    NEON_GLOW(
        id = "neon_glow",
        displayName = "Neon Glow",
        subtitle = "Luminous AMOLED Aura",
        description = "Minimalist seekbar with soft neon glow aura around the played section and thumb."
    ),
    MATERIAL3(
        id = "material3",
        displayName = "Material 3",
        subtitle = "Native Design System",
        description = "Native Material Design 3 slider appearance with theme-aware styling."
    ),
    M3_EXPRESSIVE(
        id = "m3_expressive",
        displayName = "M3 Expressive",
        subtitle = "Organic Squiggly Motion",
        description = "Expressive wavy Material 3 track with dynamic squiggly animation."
    ),
    CIRCULAR_PROGRESS(
        id = "circular_progress",
        displayName = "Circular Progress",
        subtitle = "Radial Arc Scrubber",
        description = "Compact radial arc scrubber with interactive circular progress indicator and drag seeking."
    );

    companion object {
        val DEFAULT = BLOCKS

        fun fromId(id: String?): SeekbarStyle {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
