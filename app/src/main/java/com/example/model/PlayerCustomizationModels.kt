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

enum class AnimationIntensity(val id: String, val label: String, val multiplier: Float) {
    OFF("off", "Static", 0f),
    SUBTLE("subtle", "Subtle", 0.5f),
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

enum class MiniPlayerStyle(val id: String, val displayName: String, val subtitle: String) {
    FLOATING_PILL("floating_pill", "Floating Pill", "Signature rounded pill with circular artwork"),
    COMPACT_BAR("compact_bar", "Compact Dock", "Slim editorial bar with square artwork & inline progress"),
    ARTWORK_FOCUS("artwork_focus", "Artwork Card", "Elevated tonal card spotlighting album cover & audio tag"),
    WAVE_MINI("wave_mini", "Waveform Mini", "Translucent mini deck with micro waveform progress");

    companion object {
        val DEFAULT = FLOATING_PILL
        fun fromId(id: String?): MiniPlayerStyle =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
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
    val controlArrangement: ControlArrangement = ControlArrangement.BALANCED,
    val buttonShape: ButtonShapeOption = ButtonShapeOption.CIRCLE,
    val typographyScale: Float = 1.0f,
    val seekbarThicknessScale: Float = 1.0f,
    val useDynamicAlbumAccent: Boolean = true,
    val animationIntensity: AnimationIntensity = AnimationIntensity.EXPRESSIVE,
    val artworkAnimation: ArtworkAnimationOption = ArtworkAnimationOption.BREATHING,
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
    val miniPlayerGesturesEnabled: Boolean = true,
    val swipeSensitivity: Float = 1.0f,
    val swipeThresholdDp: Int = 64,
    val hapticFeedbackEnabled: Boolean = true
) {
    companion object {
        val DEFAULT = PlayerGestureConfig()
    }
}
