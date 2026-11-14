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

/**
 * Global appearance modes governing how dynamic album-art colors theme the entire SONORA app.
 */
enum class DynamicAppearanceMode(
    val id: String,
    val displayName: String,
    val subtitle: String
) {
    FOLLOW_ALBUM_ART(
        id = "follow_album_art",
        displayName = "Follow Album Art",
        subtitle = "Adapts light or atmospheric dark surfaces to each cover"
    ),
    FIXED_ACCENT(
        id = "fixed_accent",
        displayName = "Fixed Accent Colour",
        subtitle = "Uses your signature accent hue across every screen"
    ),
    AMOLED_DARK(
        id = "amoled_dark",
        displayName = "AMOLED Dark",
        subtitle = "True OLED black backgrounds with artwork highlights"
    ),
    LIGHT_THEME(
        id = "light_theme",
        displayName = "Light Theme",
        subtitle = "Crisp editorial light surfaces tinted by playing art"
    ),
    AUTO_THEME(
        id = "auto_theme",
        displayName = "Auto Theme",
        subtitle = "Follows system dark/light theme with dynamic accents"
    );

    companion object {
        val DEFAULT = FOLLOW_ALBUM_ART

        fun fromId(id: String?): DynamicAppearanceMode {
            if (id == null) return DEFAULT
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

/**
 * Intensity presets controlling saturation and tonal tint strength of dynamic album colors.
 */
enum class DynamicColorIntensity(
    val id: String,
    val displayName: String,
    val description: String,
    val saturationScale: Float,
    val surfaceTintScale: Float
) {
    SUBTLE(
        id = "subtle",
        displayName = "Subtle",
        description = "Soft pastel washes & gentle accents",
        saturationScale = 0.65f,
        surfaceTintScale = 0.55f
    ),
    BALANCED(
        id = "balanced",
        displayName = "Balanced",
        description = "Rich tonal harmony & crisp readability",
        saturationScale = 1.0f,
        surfaceTintScale = 1.0f
    ),
    VIBRANT(
        id = "vibrant",
        displayName = "Vibrant",
        description = "Expressive saturation & deeper gradients",
        saturationScale = 1.28f,
        surfaceTintScale = 1.40f
    );

    companion object {
        val DEFAULT = BALANCED

        fun fromId(id: String?): DynamicColorIntensity {
            if (id == null) return DEFAULT
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}

