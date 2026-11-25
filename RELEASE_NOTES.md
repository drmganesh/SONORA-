# SONORA v1.4.0 — Pinch-to-Resize Video PiP, Personalized Recommendations & Fluid Startup

## What's New

### Pinch-to-Resize Floating PiP (Online Video)
- **Smooth Two-Finger Pinch Resizing**: Resize SONORA's in-app floating PiP video player continuously with a two-finger pinch gesture (pinch outward to enlarge, pinch inward to shrink) anchored around your pinch focal point.
- **Adaptive Bounds & Safe Margins**: Starts at ~40% of screen width and adapts smoothly between ~20% minimum and ~85% maximum width with safe height and edge clamping across portrait and landscape orientations.
- **True Aspect Ratio Preservation**: Automatically preserves actual video proportions (`16:9`, `9:16`, `4:3`, `1:1`, non-square pixel aspect ratios, and rotated streams) without stretching or distortion.
- **Zero-Interruption Playback**: Keeps the existing single Media3 `ExoPlayer` and live `TextureView` attached continuously during resizing with GPU-accelerated `graphicsLayer` transforms — no restarts, black frames, or rebuffering.
- **Clean Gesture Separation**: Two-finger pinch resizes the floating PiP; one-finger tap toggles compact playback controls (Expand, Play/Pause, Next, Close). Native Android OS Picture-in-Picture remains controlled by Android.

### Personalized "Sonora Recommend" Engine (Online Music)
- **Tailored Home Discovery**: Added the **Sonora Recommend** section directly above **Trending Music India** in Online Music mode (Offline Music and Online Video layouts remain unchanged).
- **Activity-Driven Weighted Ranking**: Learns from liked tracks, followed artists, saved/custom playlists, completed listening sessions, and real-time interactions (Like, Dislike, Skip, Play Next) while avoiding accidental short plays and reducing disliked recommendations.

### Streamlined Online Music Home & Settings
- **Cleaner Home Layout**: Removed the legacy inline Online Music provider/region control panel from the Home screen to keep focus on **Your Mix**, **Sonora Recommend**, **Trending Music India**, and curated carousels.
- **Dedicated Settings → Online Music**: Moved and organized online discovery controls into **Region & Language** (India & regional languages persisted locally with DataStore, preserving `IN • EN`), **Streaming Services** (YouTube Music, YouTube, SoundCloud, and supported sources), **Discovery Preferences**, and **Online Music Library**.

### Performance & Stability Improvements
- **Fluid Startup & Intro Animation**: Preloaded and cached the SONORA intro logo off the main thread, switched splash animations to GPU-accelerated `graphicsLayer` transforms with stable `Animatable` state, and deferred heavy background initialization for smooth 60 FPS+ startup.
- **Weather Atmosphere Crash Fix**: Fixed an `ArrayIndexOutOfBoundsException` in `WeatherAtmosphereOverlay` when rendering clear-night stars.
- **Low-RAM Optimizations**: Reduced elevation shadow and thumbnail overdraw overhead on low-RAM Android devices while actively resizing floating video PiP.

### Previously Included
- **Pan-India Festival Atmosphere Engine (v1.3.7)**: Automatic, battery-efficient Hindu festival atmospheres across all 28 states and 8 union territories with zero GPS or personal permissions.
- **Weather-Synced Ambient Effects Engine (v1.3.6)**: Open-Meteo weather atmosphere for fixed Odisha locations with zero location permissions.

---

## Release Metadata
- **Version Name**: `1.4.0`
- **Version Code**: `7`
- **Min Android Version**: Android 8.0 (API 26)
- **Target Android Version**: Android 16 (API 36)
- **Privacy**: Zero GPS access, zero device location tracking, zero calendar/contacts access.
- **Data Attributions**:
  - Weather: [Open-Meteo](https://open-meteo.com)
  - Festival Calendar: IMD / PAC Rashtriya Panchang, regional state Panchangs, public Hindu Festivals JSON (`ganeshsp1/hindu-festivals-api`, MIT License), and open Panchang reference (`satmargastro.com`, CC BY 4.0).
