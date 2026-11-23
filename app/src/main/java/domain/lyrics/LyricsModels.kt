package domain.lyrics

/**
 * Synchronization precision level of a parsed lyrics document.
 * Word-level highlighting is strictly enabled only when [WORD_SYNCED] timestamps exist.
 */
enum class LyricsSyncType(val displayLabel: String) {
    WORD_SYNCED("Word-Synced"),
    LINE_SYNCED("Line-Synced"),
    UNSYNCED("Plain Text"),
    NONE("No Lyrics")
}

/**
 * Origin of the currently displayed lyrics.
 */
enum class LyricsSourceType(val displayLabel: String) {
    LOCAL_EDITED("Edited & Saved Locally"),
    IMPORTED_LRC("Imported LRC File"),
    LOCAL_SIDECAR_LRC("Local Sidecar LRC"),
    EMBEDDED_TAG("Embedded Audio Metadata"),
    ONLINE_LRCLIB("Online (LRCLIB)"),
    PLAIN_TEXT("Local Plain Text"),
    NONE("No Source")
}

/**
 * Individual word timestamp extracted from Enhanced LRC (<mm:ss.xx>word).
 */
data class LyricWord(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

/**
 * Single lyric line. When [startTimeMs] < 0, the line is unsynchronized plain text.
 * [words] is populated ONLY when authentic word-level timestamps exist in the source.
 */
data class SyncedLyricLine(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList()
) {
    val isTimed: Boolean
        get() = startTimeMs >= 0L

    val hasWordTiming: Boolean
        get() = words.isNotEmpty()
}

/**
 * Parsed, cached lyrics document for a single track.
 */
data class LyricsDocument(
    val trackKey: String,
    val title: String,
    val artist: String,
    val rawText: String,
    val lines: List<SyncedLyricLine>,
    val syncType: LyricsSyncType,
    val sourceType: LyricsSourceType,
    val sourceLabel: String = sourceType.displayLabel,
    val providerMetadata: String? = null,
    val embeddedOffsetMs: Long = 0L,
    val isInstrumental: Boolean = false
) {
    val hasLyrics: Boolean
        get() = lines.isNotEmpty()

    val isSynchronized: Boolean
        get() = syncType == LyricsSyncType.LINE_SYNCED || syncType == LyricsSyncType.WORD_SYNCED
}

/**
 * Search result returned by an online lyrics provider for user selection.
 */
data class LyricsSearchCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val syncedLyrics: String?,
    val plainLyrics: String?,
    val instrumental: Boolean = false,
    val sourceLabel: String = "LRCLIB"
) {
    val hasSyncedLyrics: Boolean
        get() = !syncedLyrics.isNullOrBlank()

    val preferredRawText: String
        get() = syncedLyrics?.takeIf { it.isNotBlank() } ?: plainLyrics.orEmpty()
}

/**
 * Sealed UI loading state for the currently playing track's lyrics.
 */
sealed class LyricsLoadState {
    data object Idle : LyricsLoadState()
    data class Loading(val trackKey: String, val message: String = "Finding synchronized lyrics...") : LyricsLoadState()
    data class Loaded(val document: LyricsDocument) : LyricsLoadState()
    data class NotFound(val trackKey: String, val reason: String) : LyricsLoadState()
    data class Error(val trackKey: String, val message: String) : LyricsLoadState()
}

/**
 * Per-track + global timing offset state.
 * Positive offset shifts lyrics earlier (lyrics ahead of audio).
 * Negative offset shifts lyrics later (lyrics behind audio).
 */
data class LyricsTimingState(
    val trackKey: String = "",
    val trackOffsetMs: Long = 0L,
    val globalOffsetMs: Long = 0L,
    val hasCustomTrackOffset: Boolean = false
) {
    val effectiveOffsetMs: Long
        get() = (if (hasCustomTrackOffset) trackOffsetMs else globalOffsetMs)
            .coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)

    val statusLabel: String
        get() = when {
            effectiveOffsetMs > 0L -> "Lyrics ahead of audio"
            effectiveOffsetMs < 0L -> "Lyrics behind audio"
            else -> "Synchronized"
        }

    fun formattedOffset(): String {
        val ms = effectiveOffsetMs
        return when {
            ms == 0L -> "0 ms"
            ms > 0L -> "+$ms ms"
            else -> "$ms ms"
        }
    }

    companion object {
        const val MIN_OFFSET_MS: Long = -10_000L
        const val MAX_OFFSET_MS: Long = 10_000L
    }
}

enum class LyricsPreset(val displayName: String, val subtitle: String) {
    MINIMAL("Minimal", "Clean typography & calm focus"),
    CINEMATIC("Cinematic", "Lush blurred art & expressive scale"),
    KARAOKE("Karaoke", "Bold word-by-word stage glow"),
    CLASSIC("Classic", "Balanced traditional lyrics sheet")
}

enum class LyricsFontFamilyOption(val displayName: String) {
    OUTFIT("Outfit"),
    PLUS_JAKARTA("Plus Jakarta"),
    SERIF("Editorial Serif"),
    SANS_SERIF("System Sans"),
    MONOSPACE("Studio Mono")
}

enum class LyricsScrollSpeed(val displayName: String, val animationDurationMs: Int) {
    GENTLE("Gentle", 520),
    BALANCED("Balanced", 320),
    SNAPPY("Snappy", 180)
}

/**
 * Persistent appearance settings for the Full-Screen Lyrics experience.
 */
data class LyricsAppearanceConfig(
    val preset: LyricsPreset = LyricsPreset.CINEMATIC,
    val fontSizeSp: Float = 26f,
    val fontFamily: LyricsFontFamilyOption = LyricsFontFamilyOption.OUTFIT,
    val lineSpacingMultiplier: Float = 1.42f,
    val useDynamicHighlightColor: Boolean = true,
    val customHighlightColorArgb: Long = 0xFFFFD54FL,
    val backgroundBlurDp: Float = 24f,
    val backgroundOverlayOpacity: Float = 0.62f,
    val currentLineScale: Float = 1.08f,
    val scrollSpeed: LyricsScrollSpeed = LyricsScrollSpeed.BALANCED,
    val wordHighlightEnabled: Boolean = true,
    val amoledDarkBackground: Boolean = false,
    val globalTimingOffsetMs: Long = 0L
) {
    fun withPreset(target: LyricsPreset): LyricsAppearanceConfig = when (target) {
        LyricsPreset.MINIMAL -> copy(
            preset = target,
            fontSizeSp = 23f,
            fontFamily = LyricsFontFamilyOption.PLUS_JAKARTA,
            lineSpacingMultiplier = 1.48f,
            backgroundBlurDp = 12f,
            backgroundOverlayOpacity = 0.78f,
            currentLineScale = 1.04f,
            scrollSpeed = LyricsScrollSpeed.GENTLE,
            wordHighlightEnabled = false
        )
        LyricsPreset.CINEMATIC -> copy(
            preset = target,
            fontSizeSp = 26f,
            fontFamily = LyricsFontFamilyOption.OUTFIT,
            lineSpacingMultiplier = 1.42f,
            backgroundBlurDp = 24f,
            backgroundOverlayOpacity = 0.62f,
            currentLineScale = 1.09f,
            scrollSpeed = LyricsScrollSpeed.BALANCED,
            wordHighlightEnabled = true
        )
        LyricsPreset.KARAOKE -> copy(
            preset = target,
            fontSizeSp = 30f,
            fontFamily = LyricsFontFamilyOption.OUTFIT,
            lineSpacingMultiplier = 1.38f,
            backgroundBlurDp = 28f,
            backgroundOverlayOpacity = 0.56f,
            currentLineScale = 1.14f,
            scrollSpeed = LyricsScrollSpeed.SNAPPY,
            wordHighlightEnabled = true
        )
        LyricsPreset.CLASSIC -> copy(
            preset = target,
            fontSizeSp = 22f,
            fontFamily = LyricsFontFamilyOption.SERIF,
            lineSpacingMultiplier = 1.52f,
            backgroundBlurDp = 16f,
            backgroundOverlayOpacity = 0.72f,
            currentLineScale = 1.05f,
            scrollSpeed = LyricsScrollSpeed.BALANCED,
            wordHighlightEnabled = true
        )
    }

    companion object {
        val DEFAULT = LyricsAppearanceConfig()
        const val MIN_FONT_SP = 16f
        const val MAX_FONT_SP = 40f
        const val MIN_LINE_SPACING = 1.15f
        const val MAX_LINE_SPACING = 1.90f
        const val MIN_BLUR_DP = 0f
        const val MAX_BLUR_DP = 40f
        const val MIN_OPACITY = 0.25f
        const val MAX_OPACITY = 0.90f
        const val MIN_SCALE = 1.0f
        const val MAX_SCALE = 1.25f
    }
}

/**
 * Descriptor of an available or selectable lyrics source for the source selector sheet.
 */
data class LyricsSourceOption(
    val sourceType: LyricsSourceType,
    val title: String,
    val subtitle: String,
    val isAvailable: Boolean,
    val isActive: Boolean
)
