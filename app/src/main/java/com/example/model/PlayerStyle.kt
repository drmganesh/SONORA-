package com.example.model

enum class PlayerStyleCategory(val id: String, val displayName: String) {
    ALL("all", "All"),
    MINIMAL("minimal", "Minimal"),
    ARTISTIC("artistic", "Artistic"),
    IMMERSIVE("immersive", "Immersive"),
    CLASSIC("classic", "Classic"),
    EXPERIMENTAL("experimental", "Experimental")
}

/**
 * Complete Player Style definitions for SONORA Music Player.
 * Includes 18 distinct Now Playing visual styles plus preserved Gesture mode.
 */
enum class PlayerStyle(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String,
    val category: PlayerStyleCategory
) {
    // Classic & Minimal
    CLASSIC(
        id = "classic",
        displayName = "Classic",
        subtitle = "Timeless Hi-Fi Layout",
        description = "Traditional square album artwork, full transport controls, shortcuts, and clean seekbar.",
        category = PlayerStyleCategory.CLASSIC
    ),
    MINIMAL(
        id = "minimal",
        displayName = "Minimal",
        subtitle = "Whisper Quiet Space",
        description = "Compact centered artwork, essential track typography, minimal controls, and generous breathing room.",
        category = PlayerStyleCategory.MINIMAL
    ),
    MATERIAL_YOU(
        id = "material_you",
        displayName = "Material You",
        subtitle = "M3 Expressive Tonal",
        description = "Dynamic Material 3 tonal surfaces, morphing play shape, and cohesive expressive controls.",
        category = PlayerStyleCategory.CLASSIC
    ),
    GLASS(
        id = "glass",
        displayName = "Glass",
        subtitle = "Frosted Translucent",
        description = "Layered translucent glass cards floating over a soft album-art-inspired atmospheric backdrop.",
        category = PlayerStyleCategory.IMMERSIVE
    ),

    // Editorial & Artistic
    EDITORIAL(
        id = "editorial",
        displayName = "Editorial",
        subtitle = "Magazine Two-Tone",
        description = "Oversized magazine typography, bordered plate artwork, and structured metadata deck.",
        category = PlayerStyleCategory.ARTISTIC
    ),
    CANVAS(
        id = "canvas",
        displayName = "Canvas",
        subtitle = "Full-Screen Artwork",
        description = "Full-bleed album artwork filling the canvas with dynamic gradient scrims and minimal overlays.",
        category = PlayerStyleCategory.IMMERSIVE
    ),
    BENTO(
        id = "bento",
        displayName = "Bento",
        subtitle = "Modular Grid Tiles",
        description = "Asymmetric bento tiles separating artwork, track details, audio format, and controls.",
        category = PlayerStyleCategory.ARTISTIC
    ),
    POSTER(
        id = "poster",
        displayName = "Poster",
        subtitle = "Cinematic Print",
        description = "Dominant vertical poster artwork paired with bold display typography and theatrical framing.",
        category = PlayerStyleCategory.ARTISTIC
    ),
    POLAROID(
        id = "polaroid",
        displayName = "Polaroid",
        subtitle = "Instant Analog Frame",
        description = "Album artwork framed inside a tilted instant-print Polaroid card with soft shadows and crisp controls.",
        category = PlayerStyleCategory.ARTISTIC
    ),
    VINYL(
        id = "vinyl",
        displayName = "Vinyl",
        subtitle = "Turntable & Tonearm",
        description = "Animated rotating vinyl disc with concentric grooves and responsive tonearm tracking.",
        category = PlayerStyleCategory.ARTISTIC
    ),

    // Interactive & Experimental
    DIAL(
        id = "dial",
        displayName = "Dial",
        subtitle = "Rotary Jog-Wheel",
        description = "Interactive circular jog-wheel ring around circular artwork for tactile rotational seeking.",
        category = PlayerStyleCategory.EXPERIMENTAL
    ),
    MORPH(
        id = "morph",
        displayName = "Morph",
        subtitle = "Living Organic Shape",
        description = "Organic morphing artwork container with fluid shape transitions synchronized with playback.",
        category = PlayerStyleCategory.EXPERIMENTAL
    ),
    STICKER(
        id = "sticker",
        displayName = "Sticker",
        subtitle = "Die-Cut Studio Badge",
        description = "Playful die-cut sticker artwork with crisp borders, tilted depth, and badge tags.",
        category = PlayerStyleCategory.ARTISTIC
    ),
    WAVEFORM(
        id = "waveform",
        displayName = "Waveform",
        subtitle = "Interactive Audio Wave",
        description = "Waveform-centric studio view with tactile real-PCM waveform scrubbing and crisp metadata.",
        category = PlayerStyleCategory.EXPERIMENTAL
    ),
    SPECTRUM(
        id = "spectrum",
        displayName = "Spectrum",
        subtitle = "Real-Time FFT Bars",
        description = "Live frequency spectrum analyzer integrated with compact artwork and studio telemetry.",
        category = PlayerStyleCategory.EXPERIMENTAL
    ),
    ORBIT(
        id = "orbit",
        displayName = "Orbit",
        subtitle = "Planetary Track Ring",
        description = "Orbital progress track circling central artwork with orbiting satellite control nodes.",
        category = PlayerStyleCategory.EXPERIMENTAL
    ),
    HERO(
        id = "hero",
        displayName = "Hero",
        subtitle = "Grand Focal Deck",
        description = "Dramatic focal presentation with an oversized centerpiece Play/Pause button and bold typography.",
        category = PlayerStyleCategory.IMMERSIVE
    ),
    NEON(
        id = "neon",
        displayName = "Neon",
        subtitle = "Luminous Dark Glow",
        description = "High-contrast dark canvas with glowing dynamic accent outlines and electric seekbar clarity.",
        category = PlayerStyleCategory.IMMERSIVE
    ),

    // Preserved legacy mode
    GESTURE(
        id = "gesture",
        displayName = "Gesture",
        subtitle = "Touch & Swipe Driven",
        description = "Gesture-first expansive artwork with swipe track skip, double-tap seek, and minimal dock.",
        category = PlayerStyleCategory.MINIMAL
    );

    fun matchesCategory(filter: PlayerStyleCategory): Boolean {
        return filter == PlayerStyleCategory.ALL || category == filter
    }

    companion object {
        val DEFAULT = CLASSIC

        fun fromId(id: String?): PlayerStyle {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
