# SONORA v1.3.7 — Festival Atmosphere Engine

## What's New

### Pan-India Hindu Festival Atmosphere Engine
- **Coexists with Weather Atmosphere**: Adds an automatic, battery-efficient Hindu festival atmosphere system alongside SONORA's existing Odisha Weather Atmosphere Engine, coordinated under a shared global particle and intensity budget.
- **All 28 States & 8 Union Territories (Zero Personal Permissions)**:
  - Supports **All India**, **Selected State / UT**, and **All India + Preferred State** modes across all 36 Indian states and union territories with zero GPS, location, calendar, or contacts permissions.
  - Preserves regional celebration dates and local names (such as *Kartik Purnima • Boita Bandana* & *Dola Purnima* in Odisha, *Uttarayan* in Gujarat, *Mysuru Dasara* in Karnataka, *Kali Puja • Diwali* in West Bengal, plus Pongal, Onam, Bohag Bihu, Nuakhai, Rath Yatra, Bathukamma, Ugadi, Gudi Padwa, and more).
- **16 Bespoke Respectful Visual Effects**:
  - **Diwali**: Gentle background firework bursts, corner diyas, and golden dust.
  - **Kartik Purnima / Boita Bandana**: Traditional Odisha boita boats with triangular sails, lit diyas, and calm water shimmer.
  - **Dussehra**: Stylized golden bow, luminous arrow streak, and controlled ceremonial flame.
  - **Holi**: Soft drifting gulal color clouds.
  - Dedicated atmospheres for **Ganesh Chaturthi**, **Durga Puja / Navratri**, **Rath Yatra**, **Krishna Janmashtami**, **Makar Sankranti**, **Pongal**, **Onam**, **Bihu**, **Maha Shivaratri**, **Spring Blossoms**, **Sacred River Diyas**, and **Golden Aarti Lamps**.
- **Home Screen Festival Indicator & Details Sheet**:
  - Displays a compact festive pill on Home *only* on recognized festival dates (zero empty space when inactive), including a `+N` counter when multiple festivals overlap.
  - Tap the indicator to view regional names, date / multi-day span, tithi, regional coverage, active atmosphere, calendar source verification, and any regional calendar notes.
- **Battery-First Performance**:
  - Single-`Canvas` rendering with a fixed pre-allocated particle pool (zero per-frame allocations, strict `≤ 40` cap, default Battery Saver `On` at 30 fps / 20 fps on low-RAM).
  - Automatically pauses in background, hidden screens, and **Online Video** mode; **Reduce Motion** displays serene static motifs with zero continuous animation loop.
- **Offline Caching & Public Calendar Sync**:
  - Verified Pan-India & regional Panchang catalog cached locally in Room (`SonoraDatabase` v12) with infrequent 7-day battery-safe WorkManager sync and offline fallback (never fabricates expired dates).
- **Settings → Appearance → Ambient Effects → Festival Atmosphere**:
  - Full region picker, regional observance rule, Animation Intensity, Particle Density, Battery Saver, Reduce Motion, **Preview Only** effect chips, and manual calendar sync.

### Previous Improvements Included
- **Weather-Synced Ambient Effects Engine (v1.3.6)**: Real Open-Meteo weather atmosphere for 5 fixed Odisha locations (Paikamal, Karanjia, Padampur, Kendujhar, Titlagarh) with zero GPS/location permissions.
- **Universal Mini-Player Gestures & Developer Credit**: Two-stage downward swipe gesture across all 20 mini-player styles and developer credit in app introduction & **Settings → About SONORA**.

---

## Release Metadata
- **Version Name**: `1.3.7`
- **Version Code**: `4`
- **Min Android Version**: Android 8.0 (API 26)
- **Privacy**: Zero GPS access, zero device location tracking, zero calendar/contacts access.
- **Data Attributions**:
  - Weather: [Open-Meteo](https://open-meteo.com)
  - Festival Calendar: IMD / PAC Rashtriya Panchang, regional state Panchangs, public Hindu Festivals JSON (`ganeshsp1/hindu-festivals-api`, MIT License), and open Panchang reference (`satmargastro.com`, CC BY 4.0).
