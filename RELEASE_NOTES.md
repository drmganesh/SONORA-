# SONORA v1.4.1 — Personalized Music Taste Onboarding, System PiP Fix & Discovery Reliability

## What's New

### Personalized Music Taste Onboarding & Profile Editor
- **3-Step First-Launch Taste Setup**: Guides new users through **Pick Artists You Love** (real online artist search, artwork, related artist suggestions, multi-select), **What Do You Listen To?** (wide global and Indian regional genre selection), and **Yes or No?** (interactive swipe-based song discovery with audio previews and one-tap Pass, Like, and Add to Liked Songs actions).
- **One-Time Onboarding with Full Control**: Saves preferences locally, never interrupts future app launches or updates once completed or skipped, and lets you revisit or refine your selections anytime from **Profile → Music Taste**.
- **Smarter Personalization Everywhere**: Feeds your preferred artists, genres, and swipe discovery signals directly into **Sonora Recommend**, **Recommend Songs**, **Your Mix**, and **Radio** queues.

### Android System Picture-in-Picture (PiP) Fix
- **Clean Video-Only PiP Window**: Fixed an issue where Android system PiP could display the Home screen, bottom navigation bar, or app chrome instead of the active video. System PiP now switches cleanly to a dedicated edge-to-edge video surface backed by the existing Media3 player without recreating playback.
- **Accurate Landscape & Portrait Aspect Ratios**: Dynamically updates Android PiP aspect ratio parameters from actual video dimensions and preserves native Play/Pause controls and seamless return to the Online Video player.

### Refined In-App Floating PiP
- **Removed Pinch-to-Resize**: Removed two-finger pinch-to-resize gestures from the in-app floating video PiP player for more predictable sizing while preserving floating playback, dragging, aspect ratio, and playback controls.

### Online Music Discovery Reliability
- **YouTube Trending Extraction Fix**: Resolved `YoutubeTrendingExtractor: Could not get Trending name` parsing failures by extracting available kiosk items directly and gracefully falling back to real region-scoped music search whenever YouTube's legacy Trending kiosk is unavailable.

### Previously Included
- **v1.4.0**: In-app floating video PiP, personalized **Sonora Recommend** engine, dedicated **Settings → Online Music** controls, and 60 FPS+ startup optimizations.
- **v1.3.7 & v1.3.6**: Pan-India Festival Atmosphere Engine and privacy-first Open-Meteo Weather Ambient Effects with zero GPS or location permissions.

---

## Release Metadata
- Version Name: 1.4.1
- Version Code: 8
- Min Android Version: Android 8.0 (API 26)
- Target Android Version: Android 16 (API 36)
- Privacy: Zero GPS access, zero device location tracking, zero calendar/contacts access.
- Data Attributions:
  - Weather: [Open-Meteo](https://open-meteo.com)
  - Festival Calendar: IMD / PAC Rashtriya Panchang, regional state Panchangs, public Hindu Festivals JSON (`ganeshsp1/hindu-festivals-api`, MIT License), and open Panchang reference (`satmargastro.com`, CC BY 4.0).
