package com.example.model

/**
 * Customization modes for Dynamic Album Art-Based Theming in SONORA.
 */
enum class DynamicThemeMode(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String
) {
    FULL_DYNAMIC(
        id = "full_dynamic",
        displayName = "Full Dynamic Theme",
        subtitle = "Adapt all player & UI colors",
        description = "Harmoniously adapts backgrounds, surfaces, controls, seekbars, waveforms, and ambient glow to match the album artwork."
    ),
    AMOLED_DYNAMIC(
        id = "amoled_dynamic",
        displayName = "AMOLED Dynamic",
        subtitle = "Pure black with artwork highlights",
        description = "Preserves pitch-black AMOLED surfaces while dynamically styling controls, glowing accents, and seekbars with artwork tones."
    ),
    SUBTLE_DYNAMIC(
        id = "subtle_dynamic",
        displayName = "Subtle Dynamic Theme",
        subtitle = "Soft muted artwork tones",
        description = "Uses muted and dark-muted tones extracted from the artwork for an understated, elegant aesthetic."
    ),
    BACKGROUND_ONLY(
        id = "background_only",
        displayName = "Dynamic Background Only",
        subtitle = "Change only background & glow",
        description = "Applies dynamic gradients and ambient glow behind artwork while keeping controls in standard SONORA palette."
    ),
    STATIC_THEME(
        id = "static_theme",
        displayName = "Static Theme",
        subtitle = "Keep manually selected theme",
        description = "Disables album art adaptation and uses the fixed SONORA studio theme."
    );

    companion object {
        val DEFAULT = FULL_DYNAMIC

        fun fromId(id: String?): DynamicThemeMode {
            if (id == null) return DEFAULT
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
