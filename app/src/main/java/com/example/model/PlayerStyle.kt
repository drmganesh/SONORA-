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
 * Curated gallery groups for SONORA's expanded Now Playing Style collection.
 */
enum class PlayerStyleGroup(val id: String, val displayName: String) {
    ALL("all", "All"),
    FAVORITES("favorites", "Favorites"),
    MODERN_PREMIUM("modern_premium", "Modern & Premium"),
    ARTWORK_FOCUSED("artwork_focused", "Artwork Focused"),
    IMMERSIVE_ANIMATED("immersive_animated", "Immersive & Animated"),
    COMPACT_FUNCTIONAL("compact_functional", "Compact & Functional")
}

/**
 * Complete Player Style definitions for SONORA Music Player.
 * Includes all 30 curated modern/artwork/immersive/compact Now Playing styles
 * plus preserved legacy SONORA signature layouts.
 */
enum class PlayerStyle(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String,
    val category: PlayerStyleCategory,
    val group: PlayerStyleGroup,
    val isFeaturedInExpandedCollection: Boolean = true
) {
    // ── A. Modern & Premium (8) ───────────────────────────────────────────────────────────────
    GLASS(
        id = "glass",
        displayName = "Glassmorphism",
        subtitle = "Frosted Glass & Blur",
        description = "Frosted glass panels, blurred album artwork atmosphere, and crisp translucent controls.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.MODERN_PREMIUM
    ),
    AMOLED_MINIMAL(
        id = "amoled_minimal",
        displayName = "AMOLED Minimal",
        subtitle = "Pure Black Clarity",
        description = "True #000000 OLED canvas, centered album cover, and razor-sharp white controls.",
        category = PlayerStyleCategory.MINIMAL,
        group = PlayerStyleGroup.MODERN_PREMIUM
    ),
    PREMIUM_GRADIENT(
        id = "premium_gradient",
        displayName = "Premium Gradient",
        subtitle = "Dynamic Silk Backdrop",
        description = "Multi-stop dynamic album-art gradient, refined editorial typography, and gentle motion.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.MODERN_PREMIUM
    ),
    MATERIAL_YOU(
        id = "material_you",
        displayName = "Material You",
        subtitle = "M3 Expressive Tonal",
        description = "Adaptive Material 3 tonal surfaces, rounded squircle geometry, and modern Android polish.",
        category = PlayerStyleCategory.CLASSIC,
        group = PlayerStyleGroup.MODERN_PREMIUM
    ),
    LIQUID_GLASS(
        id = "liquid_glass",
        displayName = "Liquid Glass",
        subtitle = "Refractive Specular Depth",
        description = "Layered translucent refractive surfaces, specular edge reflections, and smooth fluid depth.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.MODERN_PREMIUM
    ),
    NEUMORPHISM(
        id = "neumorphism",
        displayName = "Neumorphism",
        subtitle = "Soft Sculpted Tactile",
        description = "Soft raised and inset sculpted surfaces with dual directional light and shadow relief.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.MODERN_PREMIUM
    ),
    DYNAMIC_COLOR(
        id = "dynamic_color",
        displayName = "Dynamic Color",
        subtitle = "Full Palette Immersion",
        description = "Entire player canvas, surfaces, progress bar, and accents adapt deeply to the album cover.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.MODERN_PREMIUM
    ),
    EDITORIAL(
        id = "editorial",
        displayName = "Editorial",
        subtitle = "Magazine Asymmetric",
        description = "Large expressive magazine typography, asymmetric framing, and curated album plate.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.MODERN_PREMIUM
    ),

    // ── B. Album Artwork Focused (8) ──────────────────────────────────────────────────────────
    CANVAS(
        id = "canvas",
        displayName = "Full Screen Artwork",
        subtitle = "Edge-to-Edge Bleed",
        description = "Album artwork fills the entire screen behind balanced gradient scrims and legible controls.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.ARTWORK_FOCUSED
    ),
    FLOATING_ARTWORK(
        id = "floating_artwork",
        displayName = "Floating Artwork",
        subtitle = "Elevated Cover Shadow",
        description = "Large floating square album cover with rich ambient drop shadow and airy transport deck.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED
    ),
    VINYL(
        id = "vinyl",
        displayName = "Vinyl Record",
        subtitle = "Turntable & Tonearm",
        description = "Animated rotating 12-inch vinyl disc with album artwork as center label and tracking tonearm.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED
    ),
    CASSETTE(
        id = "cassette",
        displayName = "Cassette Player",
        subtitle = "Animated Dual Reels",
        description = "Realistic analog cassette shell with dual rotating tape reels, tape window, and tactile deck.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED
    ),
    CD_PLAYER(
        id = "cd_player",
        displayName = "CD Player",
        subtitle = "Iridescent Optical Disc",
        description = "Spinning compact disc with prismatic holographic sheen, jewel hub, and printed album label.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED
    ),
    RETRO_WALKMAN(
        id = "retro_walkman",
        displayName = "Retro Walkman",
        subtitle = "Vintage Portable Deck",
        description = "Brushed metallic portable player inspired by classic hardware with amber LCD and chunky keys.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED
    ),
    POLAROID(
        id = "polaroid",
        displayName = "Polaroid",
        subtitle = "Instant Photo Frame",
        description = "Album cover presented inside a physical Polaroid photograph frame with caption and soft shadow.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED
    ),
    ARTWORK_CAROUSEL(
        id = "artwork_carousel",
        displayName = "Artwork Carousel",
        subtitle = "Swipeable Queue Covers",
        description = "Swipeable large cover carousel showing previous, current, and upcoming tracks with compact deck.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED
    ),

    // ── C. Immersive & Animated (8) ───────────────────────────────────────────────────────────
    WAVEFORM(
        id = "waveform",
        displayName = "Waveform Player",
        subtitle = "Live Audio Waveform",
        description = "Studio waveform visualization with tactile real-PCM scrubbing and crisp technical metadata.",
        category = PlayerStyleCategory.EXPERIMENTAL,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED
    ),
    SPECTRUM(
        id = "spectrum",
        displayName = "Spectrum Player",
        subtitle = "Animated FFT Spectrum",
        description = "Animated multi-band frequency spectrum rising behind artwork and playback controls.",
        category = PlayerStyleCategory.EXPERIMENTAL,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED
    ),
    AUDIO_VISUALIZER(
        id = "audio_visualizer",
        displayName = "Audio Visualizer",
        subtitle = "Reactive Bars & Sparks",
        description = "Synchronized reactive equalizer bars and rhythmic floating energy particles.",
        category = PlayerStyleCategory.EXPERIMENTAL,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED
    ),
    AURORA(
        id = "aurora",
        displayName = "Aurora",
        subtitle = "Flowing Atmospheric Light",
        description = "Animated multi-ribbon atmospheric aurora gradients drifting softly behind translucent controls.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED
    ),
    LIQUID_MUSIC(
        id = "liquid_music",
        displayName = "Liquid Music",
        subtitle = "Fluid Reactive Shapes",
        description = "Organic fluid morphing blobs undulating smoothly in response to live playback energy.",
        category = PlayerStyleCategory.EXPERIMENTAL,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED
    ),
    PARTICLE_FLOW(
        id = "particle_flow",
        displayName = "Particle Flow",
        subtitle = "Audio-Driven Particles",
        description = "Luminous particle streams drifting and pulsing with live music intensity.",
        category = PlayerStyleCategory.EXPERIMENTAL,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED
    ),
    SPATIAL_3D(
        id = "spatial_3d",
        displayName = "3D Spatial Player",
        subtitle = "Depth & Soundstage Rings",
        description = "Perspective 3D tilted album artwork floating over concentric spatial soundstage depth rings.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED
    ),
    CINEMATIC(
        id = "cinematic",
        displayName = "Cinematic",
        subtitle = "Widescreen Dark Drama",
        description = "Dark cinema vignette, dramatic spotlight illumination, widescreen framing, and refined controls.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED
    ),

    // ── D. Compact & Functional (6) ───────────────────────────────────────────────────────────
    CLASSIC(
        id = "classic",
        displayName = "Classic Player",
        subtitle = "Timeless Hi-Fi Layout",
        description = "Familiar square album artwork, song title, artist, clean seekbar, and standard transport deck.",
        category = PlayerStyleCategory.CLASSIC,
        group = PlayerStyleGroup.COMPACT_FUNCTIONAL
    ),
    MODERN_COMPACT(
        id = "modern_compact",
        displayName = "Modern Compact",
        subtitle = "Sleek Horizontal Deck",
        description = "Compact artwork paired with a crisp horizontal info card and high-efficiency controls.",
        category = PlayerStyleCategory.MINIMAL,
        group = PlayerStyleGroup.COMPACT_FUNCTIONAL
    ),
    TYPOGRAPHY_PLAYER(
        id = "typography_player",
        displayName = "Typography Player",
        subtitle = "Bold Display Focus",
        description = "Oversized kinetic song title and artist typography anchored by a minimal album thumbnail.",
        category = PlayerStyleCategory.MINIMAL,
        group = PlayerStyleGroup.COMPACT_FUNCTIONAL
    ),
    SPLIT_LAYOUT(
        id = "split_layout",
        displayName = "Split Layout",
        subtitle = "Dual-Pane Art & Info",
        description = "Clean split presentation placing album artwork alongside track metadata and transport controls.",
        category = PlayerStyleCategory.CLASSIC,
        group = PlayerStyleGroup.COMPACT_FUNCTIONAL
    ),
    LANDSCAPE_PLAYER(
        id = "landscape_player",
        displayName = "Landscape Player",
        subtitle = "Optimized Horizontal View",
        description = "Dedicated widescreen two-column studio layout built for landscape screens and tablets.",
        category = PlayerStyleCategory.CLASSIC,
        group = PlayerStyleGroup.COMPACT_FUNCTIONAL
    ),
    LYRICS_FOCUS(
        id = "lyrics_focus",
        displayName = "Lyrics Focus",
        subtitle = "Synced Lyrics Primary",
        description = "Real-time synchronized lyrics take center stage with compact artwork header and bottom deck.",
        category = PlayerStyleCategory.MINIMAL,
        group = PlayerStyleGroup.COMPACT_FUNCTIONAL
    ),

    // ── Preserved Legacy SONORA Signature Styles ──────────────────────────────────────────────
    MINIMAL(
        id = "minimal",
        displayName = "Minimal",
        subtitle = "Whisper Quiet Space",
        description = "Compact centered artwork, essential track typography, and generous breathing room.",
        category = PlayerStyleCategory.MINIMAL,
        group = PlayerStyleGroup.COMPACT_FUNCTIONAL,
        isFeaturedInExpandedCollection = false
    ),
    BENTO(
        id = "bento",
        displayName = "Bento",
        subtitle = "Modular Grid Tiles",
        description = "Asymmetric bento tiles separating artwork, track details, audio format, and controls.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.MODERN_PREMIUM,
        isFeaturedInExpandedCollection = false
    ),
    POSTER(
        id = "poster",
        displayName = "Poster",
        subtitle = "Cinematic Print",
        description = "Dominant vertical poster artwork paired with bold display typography and theatrical framing.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED,
        isFeaturedInExpandedCollection = false
    ),
    DIAL(
        id = "dial",
        displayName = "Dial",
        subtitle = "Rotary Jog-Wheel",
        description = "Interactive circular jog-wheel ring around circular artwork for tactile rotational seeking.",
        category = PlayerStyleCategory.EXPERIMENTAL,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED,
        isFeaturedInExpandedCollection = false
    ),
    MORPH(
        id = "morph",
        displayName = "Morph",
        subtitle = "Living Organic Shape",
        description = "Organic morphing artwork container with fluid shape transitions synchronized with playback.",
        category = PlayerStyleCategory.EXPERIMENTAL,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED,
        isFeaturedInExpandedCollection = false
    ),
    STICKER(
        id = "sticker",
        displayName = "Sticker",
        subtitle = "Die-Cut Studio Badge",
        description = "Playful die-cut sticker artwork with crisp borders, tilted depth, and badge tags.",
        category = PlayerStyleCategory.ARTISTIC,
        group = PlayerStyleGroup.ARTWORK_FOCUSED,
        isFeaturedInExpandedCollection = false
    ),
    ORBIT(
        id = "orbit",
        displayName = "Orbit",
        subtitle = "Planetary Track Ring",
        description = "Orbital progress track circling central artwork with orbiting satellite control nodes.",
        category = PlayerStyleCategory.EXPERIMENTAL,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED,
        isFeaturedInExpandedCollection = false
    ),
    HERO(
        id = "hero",
        displayName = "Hero",
        subtitle = "Grand Focal Deck",
        description = "Dramatic focal presentation with an oversized centerpiece Play/Pause button and bold typography.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.MODERN_PREMIUM,
        isFeaturedInExpandedCollection = false
    ),
    NEON(
        id = "neon",
        displayName = "Neon",
        subtitle = "Luminous Dark Glow",
        description = "High-contrast dark canvas with glowing dynamic accent outlines and electric seekbar clarity.",
        category = PlayerStyleCategory.IMMERSIVE,
        group = PlayerStyleGroup.IMMERSIVE_ANIMATED,
        isFeaturedInExpandedCollection = false
    ),
    GESTURE(
        id = "gesture",
        displayName = "Gesture",
        subtitle = "Touch & Swipe Driven",
        description = "Gesture-first expansive artwork with swipe track skip, double-tap seek, and minimal dock.",
        category = PlayerStyleCategory.MINIMAL,
        group = PlayerStyleGroup.COMPACT_FUNCTIONAL,
        isFeaturedInExpandedCollection = false
    );

    fun matchesCategory(filter: PlayerStyleCategory): Boolean {
        return filter == PlayerStyleCategory.ALL || category == filter
    }

    fun matchesGroup(filter: PlayerStyleGroup, favoriteIds: Set<String> = emptySet()): Boolean {
        return when (filter) {
            PlayerStyleGroup.ALL -> true
            PlayerStyleGroup.FAVORITES -> id in favoriteIds
            else -> group == filter
        }
    }

    companion object {
        val DEFAULT = CLASSIC
        val DEFAULT_LANDSCAPE = LANDSCAPE_PLAYER

        fun fromId(id: String?): PlayerStyle {
            if (id.isNullOrBlank()) return DEFAULT
            return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}
