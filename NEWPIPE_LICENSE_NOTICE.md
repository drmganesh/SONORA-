# Third-Party Open-Source License & Attribution Notice

SONORA integrates open-source libraries to power online audio stream extraction, playback, and artwork loading.

## NewPipe Extractor
- **Project:** TeamNewPipe / NewPipeExtractor
- **Version:** `v0.27.1`
- **Repository:** https://github.com/TeamNewPipe/NewPipeExtractor
- **License:** GNU General Public License v3.0 (GPL-3.0)
- **License Text:** https://www.gnu.org/licenses/gpl-3.0.en.html

NewPipe Extractor is used strictly for on-device metadata search, artist/album/playlist discovery, and resolving playable audio-only stream URLs for direct playback inside SONORA's Media3 ExoPlayer and Audio Studio DSP pipeline. SONORA does not implement DRM circumvention or unauthorized media downloading.

## Other Key Libraries
- **AndroidX Media3 (`exoplayer`, `exoplayer-okhttp`)** — Apache License 2.0
- **OkHttp (`com.squareup.okhttp3:okhttp`)** — Apache License 2.0
- **Coil (`io.coil-kt:coil-compose`)** — Apache License 2.0
- **Jetpack Compose, Material 3, Room, DataStore, Lifecycle** — Apache License 2.0
- **Kotlin Coroutines & Serialization** — Apache License 2.0
