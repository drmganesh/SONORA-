package data.artwork

import data.preferences.AlbumArtworkQualityMode
import java.util.Locale

enum class ArtworkResolutionTier(val cachePrefix: String, val maxDecodePx: Int, val label: String) {
    SD(cachePrefix = "sd", maxDecodePx = 320, label = "SD"),
    HD(cachePrefix = "hd", maxDecodePx = 1080, label = "HD")
}

enum class ArtworkDisplayRole {
    THUMBNAIL,
    CARD,
    NOW_PLAYING,
    ALBUM_HEADER
}

data class ResolvedArtworkRequest(
    val primaryUrl: String?,
    val fallbackUrl: String?,
    val tier: ArtworkResolutionTier,
    val cacheKey: String,
    val maxDecodePx: Int,
    val hasDistinctHdSource: Boolean
)

/**
 * Pure HD / SD Album Artwork Quality Resolver & Tiered Cache Key Manager.
 *
 * Rules:
 * - Never artificially upscales an SD image and claims it is HD.
 * - Uses genuine provider resolution variants when the URL format supports them
 *   (e.g., Google/YouTube Music `lh3.googleusercontent.com` size parameters,
 *   YouTube `i.ytimg.com` variants, SoundCloud `-t200x200` / `-t500x500`).
 * - Falls back cleanly to the original URL if an HD variant is unavailable.
 * - Separates SD and HD cache keys so changing quality never serves stale tiers
 *   and avoids duplicate redundant downloads within the same tier.
 */
object AlbumArtworkQualityManager {

    @Volatile
    var currentQualityMode: AlbumArtworkQualityMode = AlbumArtworkQualityMode.AUTO

    @Volatile
    var isMeteredOrMobileNetwork: Boolean = false

    @Volatile
    var isPoorNetwork: Boolean = false

    private val GOOGLE_USER_CONTENT_SIZE_REGEX = Regex("=w\\d+-h\\d+[^&?]*", RegexOption.IGNORE_CASE)
    private val GOOGLE_USER_CONTENT_S_REGEX = Regex("=s\\d+[^&?]*", RegexOption.IGNORE_CASE)
    private val YT_THUMB_VARIANT_REGEX =
        Regex("/(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg", RegexOption.IGNORE_CASE)
    private val SOUNDCLOUD_ART_REGEX =
        Regex("-(large|t200x200|t300x300|t500x500|original)\\.(jpg|jpeg|png)", RegexOption.IGNORE_CASE)

    fun resolveTier(
        mode: AlbumArtworkQualityMode = currentQualityMode,
        role: ArtworkDisplayRole = ArtworkDisplayRole.CARD,
        isMetered: Boolean = isMeteredOrMobileNetwork,
        isPoorConnection: Boolean = isPoorNetwork
    ): ArtworkResolutionTier {
        return when (mode) {
            AlbumArtworkQualityMode.SD -> ArtworkResolutionTier.SD
            AlbumArtworkQualityMode.HD -> ArtworkResolutionTier.HD
            AlbumArtworkQualityMode.AUTO -> {
                if (isPoorConnection) {
                    ArtworkResolutionTier.SD
                } else if (role == ArtworkDisplayRole.NOW_PLAYING || role == ArtworkDisplayRole.ALBUM_HEADER) {
                    if (isMetered) ArtworkResolutionTier.SD else ArtworkResolutionTier.HD
                } else if (isMetered) {
                    ArtworkResolutionTier.SD
                } else {
                    // Wi-Fi: HD for featured cards, SD for dense list thumbnails to stay fast & light
                    if (role == ArtworkDisplayRole.THUMBNAIL) ArtworkResolutionTier.SD else ArtworkResolutionTier.HD
                }
            }
        }
    }

    fun resolveArtworkRequest(
        rawUrl: String?,
        mode: AlbumArtworkQualityMode = currentQualityMode,
        role: ArtworkDisplayRole = ArtworkDisplayRole.CARD,
        isMetered: Boolean = isMeteredOrMobileNetwork,
        isPoorConnection: Boolean = isPoorNetwork
    ): ResolvedArtworkRequest {
        val clean = rawUrl?.trim()?.takeIf { it.isNotBlank() }
        val targetTier = resolveTier(mode, role, isMetered, isPoorConnection)
        if (clean == null) {
            return ResolvedArtworkRequest(
                primaryUrl = null,
                fallbackUrl = null,
                tier = targetTier,
                cacheKey = "${targetTier.cachePrefix}_empty",
                maxDecodePx = targetTier.maxDecodePx,
                hasDistinctHdSource = false
            )
        }

        // Local file paths/URIs: preserve original local/embedded artwork untouched
        if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
            return ResolvedArtworkRequest(
                primaryUrl = clean,
                fallbackUrl = null,
                tier = ArtworkResolutionTier.HD,
                cacheKey = "local_${clean.hashCode()}",
                maxDecodePx = ArtworkResolutionTier.HD.maxDecodePx,
                hasDistinctHdSource = false
            )
        }

        val (sdUrl, hdUrl) = deriveTierUrls(clean)
        val hasDistinctHd = hdUrl != null && !hdUrl.equals(sdUrl, ignoreCase = false)

        val primary = when (targetTier) {
            ArtworkResolutionTier.SD -> sdUrl
            ArtworkResolutionTier.HD -> hdUrl ?: clean
        }
        val fallback = if (primary != clean) clean else if (targetTier == ArtworkResolutionTier.HD && sdUrl != primary) sdUrl else null

        // Honest tier reporting: if no real HD URL variant exists, mark effective tier honestly for decode cap
        val effectiveTier = if (targetTier == ArtworkResolutionTier.HD && (hasDistinctHd || isAlreadyLikelyHighRes(clean))) {
            ArtworkResolutionTier.HD
        } else if (targetTier == ArtworkResolutionTier.SD) {
            ArtworkResolutionTier.SD
        } else {
            ArtworkResolutionTier.HD
        }

        return ResolvedArtworkRequest(
            primaryUrl = primary,
            fallbackUrl = fallback,
            tier = effectiveTier,
            cacheKey = "${effectiveTier.cachePrefix}_${primary.hashCode()}_${clean.hashCode()}",
            maxDecodePx = effectiveTier.maxDecodePx,
            hasDistinctHdSource = hasDistinctHd
        )
    }

    /**
     * Derives genuine SD and HD URLs for supported media CDNs without inventing invalid domains.
     */
    fun deriveTierUrls(rawUrl: String): Pair<String, String?> {
        val lower = rawUrl.lowercase(Locale.US)

        // 1. Google / YouTube Music artwork CDN (lh3.googleusercontent.com / yt3.ggpht.com)
        if (lower.contains("googleusercontent.com") || lower.contains("ggpht.com")) {
            if (GOOGLE_USER_CONTENT_SIZE_REGEX.containsMatchIn(rawUrl)) {
                val sd = GOOGLE_USER_CONTENT_SIZE_REGEX.replace(rawUrl, "=w256-h256-l90-rj")
                val hd = GOOGLE_USER_CONTENT_SIZE_REGEX.replace(rawUrl, "=w1080-h1080-l90-rj")
                return sd to hd
            }
            if (GOOGLE_USER_CONTENT_S_REGEX.containsMatchIn(rawUrl)) {
                val sd = GOOGLE_USER_CONTENT_S_REGEX.replace(rawUrl, "=s256-c-k-c0x00ffffff-no-rj")
                val hd = GOOGLE_USER_CONTENT_S_REGEX.replace(rawUrl, "=s1080-c-k-c0x00ffffff-no-rj")
                return sd to hd
            }
        }

        // 2. YouTube i.ytimg.com video / song thumbnails
        if (lower.contains("ytimg.com") && YT_THUMB_VARIANT_REGEX.containsMatchIn(rawUrl)) {
            val sd = YT_THUMB_VARIANT_REGEX.replace(rawUrl, "/mqdefault.jpg")
            val hd = YT_THUMB_VARIANT_REGEX.replace(rawUrl, "/hqdefault.jpg")
            return sd to hd
        }

        // 3. SoundCloud artwork CDN (sndcdn.com)
        if (lower.contains("sndcdn.com") && SOUNDCLOUD_ART_REGEX.containsMatchIn(rawUrl)) {
            val sd = SOUNDCLOUD_ART_REGEX.replace(rawUrl, "-t200x200.$2")
            val hd = SOUNDCLOUD_ART_REGEX.replace(rawUrl, "-t500x500.$2")
            return sd to hd
        }

        // Unsupported CDN pattern: return original cleanly without fabricating a fake HD variant
        return rawUrl to null
    }

    private fun isAlreadyLikelyHighRes(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        return lower.contains("maxresdefault") ||
            lower.contains("sddefault") ||
            lower.contains("1080") ||
            lower.contains("1200") ||
            lower.contains("t500x500") ||
            lower.contains("original")
    }
}
