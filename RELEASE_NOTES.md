# SONORA v1.3.6 — Weather-Synced Ambient Effects Engine

## What's New

### Weather-Synced Ambient Effects Engine
- **Real Odisha Weather Atmosphere**: Upgrades Ambient Effects into an immersive weather-reactive atmosphere powered by public Open-Meteo weather data (`Asia/Kolkata`, metric units, no API key required).
- **Five Fixed Odisha Locations (Zero GPS & Zero Location Permissions)**:
  - **Paikamal**, Bargarh *(Default)*
  - **Karanjia**, Mayurbhanj
  - **Padampur**, Bargarh
  - **Kendujhar**, Kendujhar
  - **Titlagarh**, Balangir
- **10-Level Priority Weather Effects**:
  - Thunderstorm (storm tint + brief, photosensitivity-safe distant lightning)
  - Heavy Rain & Moderate/Light Rain scaled strictly to real measured precipitation
  - Actual Snowfall (triggered only by real snowfall data — never fabricated from cold temperature alone)
  - Fog & Mist
  - Strong Wind with real meteorological wind-direction particle slant
  - Astronomical Sunlight tracking real solar azimuth and elevation from sunrise to sunset
  - Cloudy / Overcast & Clear Night starlight
  - Cold-Night Fireplace (activates only at night below your configurable temperature threshold)
- **Full Surface Integration**:
  - **Home Screen**: Active in Offline Music, active in Online Music only while playing, and completely disabled in Online Video mode.
  - **Full-Screen Now Playing**: Renders cleanly behind artwork, visualizers, lyrics, and controls across all 30+ player styles (including glass window droplets on Glass & Liquid Glass styles).
  - **Mini Player**: Subtle clipped weather backdrop on compatible mini-player styles (automatically disabled in Online Video mode).
- **Redesigned Settings → Appearance → Ambient Effects**:
  - Live preview card with selected location, current weather, temperature, wind speed, IST update timestamp, offline cache badge, and manual refresh button.
  - Switch between **Automatic Weather**, **Manual Atmosphere** (9 clearly marked simulated presets), and **Off**.
  - Granular sliders for intensity, particle density, animation speed, opacity, sunlight intensity, cold-night fireplace threshold, individual effect toggles, and **Battery Saver Mode** (30 fps cap + reduced particle budget).
  - Offline cache fallback via DataStore without ever fabricating weather data.

### Previous Improvements Included
- Universal two-stage downward swipe gesture across all 20 mini-player styles (Normal Mini Player → Floating Circular Player → Completely Dismissed while audio continues uninterrupted).
- Developer credit in SONORA introduction and **Settings → About SONORA**.

---

## Release Metadata
- **Version Name**: `1.3.6`
- **Version Code**: `3`
- **Min Android Version**: Android 8.0 (API 26)
- **Privacy**: Zero GPS access, zero device location tracking, and zero location permissions.
- **Weather Attribution**: Public weather data provided by [Open-Meteo](https://open-meteo.com).
