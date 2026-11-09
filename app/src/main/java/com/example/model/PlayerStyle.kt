package com.example.model

/**
 * Premium Player Style definitions for Sonora Offline Music Player.
 * Represents nine distinct, complete visual player layouts.
 */
enum class PlayerStyle(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String
) {
    CLASSIC(
        id = "classic",
        displayName = "Classic",
        subtitle = "Timeless Hi-Fi Layout",
        description = "Traditional music player layout with large square album artwork, complete transport controls, seekbar, and tactile buttons."
    ),
    GESTURE(
        id = "gesture",
        displayName = "Gesture",
        subtitle = "Touch & Swipe Driven",
        description = "Large album artwork occupying most of the screen. Swipe left/right to change tracks, double-tap to seek, with minimal floating controls."
    ),
    EDITORIAL(
        id = "editorial",
        displayName = "Editorial",
        subtitle = "Magazine-Inspired Two-Tone",
        description = "High-fashion two-tone layout with bordered artwork in the upper section and contrasting dark charcoal editorial typography beneath."
    ),
    CANVAS(
        id = "canvas",
        displayName = "Canvas",
        subtitle = "Full-Screen Visual Immersive",
        description = "Full-bleed album artwork filling the entire player background with smooth dark gradient scrims and floating frosted-glass controls."
    ),
    BENTO(
        id = "bento",
        displayName = "Bento",
        subtitle = "Modern Modular Grid",
        description = "Modern Bento grid layout with distinct rounded rectangular tiles for artwork, song information, progress tracking, and controls."
    ),
    STICKER(
        id = "sticker",
        displayName = "Sticker",
        subtitle = "Playful Die-Cut Aesthetic",
        description = "Die-cut sticker-inspired artwork presentation with organic rounded borders, soft depth shadows, tactile badges, and spring physics."
    ),
    MORPH(
        id = "morph",
        displayName = "Morph",
        subtitle = "Living Organic Shape",
        description = "Animated organic artwork container inside a living, morphing shape with subtle breathing transformations and music-reactive scaling."
    ),
    DIAL(
        id = "dial",
        displayName = "Dial",
        subtitle = "Rotary Jog-Wheel Ring",
        description = "Circular rotary jog-wheel interface surrounding circular artwork. Rotate or drag the luminous ring to seek with precision tactile feedback."
    ),
    HERO(
        id = "hero",
        displayName = "Hero",
        subtitle = "Cinematic Grand Scale",
        description = "Full-bleed artwork with an oversized glowing Play/Pause centerpiece, cinematic bold typography, and streamlined distraction-free controls."
    );

    companion object {
        val DEFAULT = CLASSIC

        fun fromId(id: String?): PlayerStyle {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
