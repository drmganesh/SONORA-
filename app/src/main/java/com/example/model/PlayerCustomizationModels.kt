package com.example.model

enum class ArtworkSizeOption(val id: String, val label: String, val fraction: Float) {
    COMPACT("compact", "Compact", 0.70f),
    BALANCED("balanced", "Balanced", 0.86f),
    LARGE("large", "Large", 0.94f),
    FULL("full", "Edge-to-Edge", 1.0f);

    companion object {
        fun fromId(id: String?): ArtworkSizeOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BALANCED
    }
}

enum class CornerRadiusOption(val id: String, val label: String, val radiusDp: Int) {
    SHARP("sharp", "Crisp (8dp)", 8),
    SOFT("soft", "Soft (18dp)", 18),
    ROUNDED("rounded", "Editorial (28dp)", 28),
    PILL("pill", "Organic (40dp)", 40);

    companion object {
        fun fromId(id: String?): CornerRadiusOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ROUNDED
    }
}

enum class PlayerBackgroundStyle(val id: String, val label: String) {
    THEME_DEFAULT("theme_default", "Theme Canvas"),
    GRADIENT_GLOW("gradient_glow", "Ambient Radial Glow"),
    BLURRED_ARTWORK("blurred_artwork", "Atmosphere Wash"),
    SOLID_SURFACE("solid_surface", "Matte Surface"),
    AMOLED_PURE("amoled_pure", "Pure Black");

    companion object {
        fun fromId(id: String?): PlayerBackgroundStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: GRADIENT_GLOW
    }
}

enum class ControlArrangement(val id: String, val label: String) {
    BALANCED("balanced", "Balanced Deck"),
    COMPACT_CENTER("compact_center", "Tight Cluster"),
    SPLIT_WIDE("split_wide", "Wide Spread"),
    MINIMAL_DECK("minimal_deck", "Minimal Trio");

    companion object {
        fun fromId(id: String?): ControlArrangement =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BALANCED
    }
}

enum class ButtonShapeOption(val id: String, val label: String) {
    CIRCLE("circle", "Circle"),
    SQUIRCLE("squircle", "Squircle"),
    PILL("pill", "Wide Pill");

    companion object {
        fun fromId(id: String?): ButtonShapeOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CIRCLE
    }
}

enum class ControlButtonSizeOption(val id: String, val label: String, val primaryDp: Int, val iconScale: Float) {
    COMPACT("compact", "Compact", 58, 0.88f),
    MEDIUM("medium", "Balanced", 70, 1.0f),
    LARGE("large", "Large", 80, 1.12f);

    companion object {
        fun fromId(id: String?): ControlButtonSizeOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MEDIUM
    }
}

enum class PlayerTypographyOption(val id: String, val label: String) {
    MODERN_SANS("modern_sans", "Modern Sans"),
    EDITORIAL_SERIF("editorial_serif", "Editorial Serif"),
    MONO_STUDIO("mono_studio", "Studio Mono"),
    BOLD_DISPLAY("bold_display", "Bold Display");

    companion object {
        fun fromId(id: String?): PlayerTypographyOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MODERN_SANS
    }
}

enum class PlayerLayoutOption(val id: String, val label: String, val subtitle: String) {
    STYLE_DEFAULT("style_default", "Style Default", "Use each player style's native composition"),
    ARTWORK_ABOVE("artwork_above", "Artwork Above", "Album art positioned cleanly above controls"),
    ARTWORK_CENTERED("artwork_centered", "Artwork Centered", "Balanced centered cover with generous space"),
    ARTWORK_FULLSCREEN("artwork_fullscreen", "Artwork Full-Screen", "Edge-to-edge background artwork with scrim"),
    ARTWORK_LEFT_CONTROLS_RIGHT("artwork_left_controls_right", "Split Side-by-Side", "Artwork on left, info and controls on right"),
    LYRICS_FIRST("lyrics_first", "Lyrics-First", "Prioritize synced lyrics above compact controls"),
    COMPACT("compact", "Compact Layout", "High-density layout for one-handed reach");

    companion object {
        fun fromId(id: String?): PlayerLayoutOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STYLE_DEFAULT
    }
}

enum class AnimationIntensity(val id: String, val label: String, val multiplier: Float) {
    OFF("off", "Off", 0f),
    SUBTLE("subtle", "Subtle", 0.45f),
    BALANCED("balanced", "Balanced", 0.75f),
    EXPRESSIVE("expressive", "Expressive", 1.0f);

    companion object {
        fun fromId(id: String?): AnimationIntensity =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: EXPRESSIVE
    }
}

enum class ArtworkAnimationOption(val id: String, val label: String) {
    NONE("none", "None"),
    BREATHING("breathing", "Gentle Pulse"),
    VINYL_SPIN("vinyl_spin", "Slow Spin"),
    MORPH_PULSE("morph_pulse", "Reactive Scale");

    companion object {
        fun fromId(id: String?): ArtworkAnimationOption =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BREATHING
    }
}

/**
 * 20 independently customizable Mini-Player visual styles.
 * Preserves legacy IDs (compact_bar, floating_pill, artwork_focus, wave_mini) for seamless migration.
 */
enum class MiniPlayerStyle(val id: String, val displayName: String, val subtitle: String) {
    COMPACT_BAR("compact_bar", "Classic Bar", "Album thumbnail, title, artist, play/pause & next"),
    GLASS_BAR("glass_bar", "Glass Bar", "Translucent frosted glass with soft blurred backdrop"),
    FLOATING_PILL("floating_pill", "Floating Pill", "Rounded floating pill with circular artwork & controls"),
    DYNAMIC_ARTWORK_BAR("dynamic_artwork_bar", "Dynamic Artwork Bar", "Surface tinted directly from current album palette"),
    AMOLED_BAR("amoled_bar", "AMOLED Bar", "Pure black OLED bar with crisp white controls"),
    GRADIENT_BAR("gradient_bar", "Gradient Bar", "Smooth album-art horizontal gradient ribbon"),
    VINYL_MINI("vinyl_mini", "Vinyl Mini", "Small rotating vinyl disc beside song information"),
    CASSETTE_MINI("cassette_mini", "Cassette Mini", "Compact analog cassette tape deck with dual reels"),
    CD_MINI("cd_mini", "CD Mini", "Spinning iridescent CD disc with jewel center ring"),
    WAVE_MINI("wave_mini", "Waveform Mini", "Live audio waveform replacing traditional progress bar"),
    SPECTRUM_MINI("spectrum_mini", "Spectrum Mini", "Animated mini equalizer bars synchronized to playback"),
    ARTWORK_FOCUS("artwork_focus", "Floating Artwork", "Elevated card spotlighting album cover & audio tag"),
    CIRCULAR_PLAYER("circular_player", "Circular Player", "Circular artwork encircled by radial progress ring"),
    EXPANDABLE_CARD("expandable_card", "Expandable Card", "Expandable deck revealing inline queue & extra controls"),
    MINIMAL_TEXT("minimal_text", "Minimal Text", "Clean typography-first bar with compact controls"),
    BLUR_CARD("blur_card", "Blur Card", "Atmospheric blurred artwork cover with sharp overlay text"),
    NEON_MINI("neon_mini", "Neon Mini", "Dark shell with neon outline and luminous progress glow"),
    MATERIAL_YOU_MINI("material_you_mini", "Material You Mini", "Rounded adaptive-color M3 tonal card & squircle play"),
    EDGE_TO_EDGE_MINI("edge_to_edge_mini", "Edge-to-Edge Mini", "Full-width docked bottom bar with top progress line"),
    FLOATING_CORNER_PLAYER("floating_corner_player", "Floating Corner Player", "Movable compact floating music card inside the app");

    companion object {
        val DEFAULT = FLOATING_PILL
        fun fromId(id: String?): MiniPlayerStyle {
            if (id.isNullOrBlank()) return DEFAULT
            if (id.equals("classic_bar", ignoreCase = true)) return COMPACT_BAR
            return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}

enum class LockScreenStyle(val id: String, val displayName: String, val subtitle: String) {
    MEDIA_COLORED("media_colored", "Colorized Art", "System media card tinted with album accent palette"),
    CLASSIC_DARK("classic_dark", "Clean Classic", "Crisp high-contrast notification without full color wash"),
    MINIMAL_PRIVATE("minimal_private", "Minimal Clean", "Streamlined track title & artist without codec tags");

    companion object {
        val DEFAULT = MEDIA_COLORED
        fun fromId(id: String?): LockScreenStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}

data class PlayerAppearanceConfig(
    val artworkSize: ArtworkSizeOption = ArtworkSizeOption.BALANCED,
    val cornerRadius: CornerRadiusOption = CornerRadiusOption.ROUNDED,
    val backgroundStyle: PlayerBackgroundStyle = PlayerBackgroundStyle.GRADIENT_GLOW,
    val blurIntensity: Float = 0.55f,
    val gradientIntensity: Float = 0.65f,
    val dynamicColorIntensity: Float = 0.80f,
    val controlArrangement: ControlArrangement = ControlArrangement.BALANCED,
    val buttonShape: ButtonShapeOption = ButtonShapeOption.CIRCLE,
    val buttonSize: ControlButtonSizeOption = ControlButtonSizeOption.MEDIUM,
    val typographyOption: PlayerTypographyOption = PlayerTypographyOption.MODERN_SANS,
    val typographyScale: Float = 1.0f,
    val seekbarThicknessScale: Float = 1.0f,
    val useDynamicAlbumAccent: Boolean = true,
    val animationIntensity: AnimationIntensity = AnimationIntensity.EXPRESSIVE,
    val artworkAnimation: ArtworkAnimationOption = ArtworkAnimationOption.BREATHING,
    val layoutOption: PlayerLayoutOption = PlayerLayoutOption.STYLE_DEFAULT,
    val showAudioFormatMetadata: Boolean = true,
    val showAlbumMetadata: Boolean = true,
    val showLyricsShortcut: Boolean = true,
    val showQueueShortcut: Boolean = true
) {
    companion object {
        val DEFAULT = PlayerAppearanceConfig()
    }
}

enum class DoubleTapAction(val id: String, val label: String) {
    SEEK_10S("seek_10s", "Left/Right Seek ±10s"),
    PLAY_PAUSE("play_pause", "Toggle Play / Pause"),
    TOGGLE_FAVORITE("toggle_favorite", "Toggle Favorite"),
    NONE("none", "Disabled");

    companion object {
        fun fromId(id: String?): DoubleTapAction =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SEEK_10S
    }
}

enum class SwipeAction(val id: String, val label: String) {
    SKIP_TRACK("skip_track", "Next / Previous Track"),
    SEEK_15S("seek_15s", "Seek ±15s"),
    CYCLE_STYLE("cycle_style", "Cycle Player Style"),
    OPEN_QUEUE("open_queue", "Open Queue"),
    NONE("none", "Disabled");

    companion object {
        fun fromId(id: String?): SwipeAction =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: SKIP_TRACK
    }
}

enum class SingleTapAction(val id: String, val label: String) {
    TOGGLE_PLAY_PAUSE("toggle_play_pause", "Play / Pause"),
    TOGGLE_CONTROLS_VISIBILITY("toggle_controls", "Show / Hide Controls"),
    NONE("none", "Disabled");

    companion object {
        fun fromId(id: String?): SingleTapAction =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: TOGGLE_PLAY_PAUSE
    }
}

data class PlayerGestureConfig(
    val gesturesEnabled: Boolean = true,
    val swipeArtworkEnabled: Boolean = true,
    val swipeLeftAction: SwipeAction = SwipeAction.SKIP_TRACK,
    val swipeRightAction: SwipeAction = SwipeAction.SKIP_TRACK,
    val doubleTapEnabled: Boolean = true,
    val doubleTapAction: DoubleTapAction = DoubleTapAction.SEEK_10S,
    val singleTapAction: SingleTapAction = SingleTapAction.TOGGLE_PLAY_PAUSE,
    val longPressStyleWheelEnabled: Boolean = true,
    val pinchZoomArtworkEnabled: Boolean = true,
    val swipeDownToMinimizeEnabled: Boolean = true,
    val swipeUpToOpenQueueEnabled: Boolean = true,
    val rightEdgeVolumeEnabled: Boolean = true,
    val leftEdgeBrightnessEnabled: Boolean = true,
    val swipeSensitivity: Float = 1.0f,
    val swipeThresholdDp: Int = 64,
    val hapticFeedbackEnabled: Boolean = true
) {
    companion object {
        val DEFAULT = PlayerGestureConfig()
    }
}
