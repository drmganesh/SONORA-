package domain.update

import okhttp3.HttpUrl
import util.SecureUrlValidator
import java.util.Locale

/**
 * Enforces strict repository ownership, HTTPS transport, and trusted GitHub CDN
 * redirect allowlisting for SONORA's update pipeline.
 */
object GitHubReleaseUrlValidator {

    private val OFFICIAL_OWNER_LOWER = SonoraGitHubUpdateConfig.REPOSITORY_OWNER.lowercase(Locale.ROOT)
    private val OFFICIAL_REPO_LOWER = SonoraGitHubUpdateConfig.REPOSITORY_NAME.lowercase(Locale.ROOT)

    private val TRUSTED_REDIRECT_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com",
        "codeload.github.com" // never allowed for APK assets unless path validated; excluded below
    )

    private val ALLOWED_ASSET_CDN_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    /**
     * Validates that the GitHub Releases API endpoint targets the official drmganesh/SONORA- repository.
     */
    fun isValidOfficialApiUrl(rawUrl: String?): Boolean {
        val parsed = SecureUrlValidator.parseSafeHttpUrl(rawUrl, allowHttp = false, allowPrivateHosts = false)
            ?: return false
        if (!parsed.host.equals("api.github.com", ignoreCase = true)) return false
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 4) return false
        return segments[0].equals("repos", ignoreCase = true) &&
            segments[1].equals(OFFICIAL_OWNER_LOWER, ignoreCase = true) &&
            segments[2].equals(OFFICIAL_REPO_LOWER, ignoreCase = true) &&
            segments[3].equals("releases", ignoreCase = true)
    }

    /**
     * Validates that a release's `html_url` belongs strictly to https://github.com/drmganesh/SONORA-/releases/...
     */
    fun isValidOfficialReleaseHtmlUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val parsed = SecureUrlValidator.parseSafeHttpUrl(rawUrl, allowHttp = false, allowPrivateHosts = false)
            ?: return false
        if (!parsed.host.equals("github.com", ignoreCase = true)) return false
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 3) return false
        return segments[0].equals(OFFICIAL_OWNER_LOWER, ignoreCase = true) &&
            segments[1].equals(OFFICIAL_REPO_LOWER, ignoreCase = true) &&
            segments[2].equals("releases", ignoreCase = true)
    }

    /**
     * Validates that a release asset's `browser_download_url` belongs strictly to
     * `https://github.com/drmganesh/SONORA-/releases/download/<tag>/<asset>` or an official
     * GitHub API asset endpoint for the same repository.
     *
     * Rejects:
     * - Plain HTTP or non-HTTPS schemes
     * - Private/loopback/link-local addresses or embedded credentials
     * - Any asset URL belonging to a different repository or arbitrary third-party host
     * - Source archive endpoints (`/archive/`, `/zipball/`, `/tarball/`)
     * - Path traversal segments (`..`)
     */
    fun isValidOfficialReleaseAssetUrl(rawUrl: String?): Boolean {
        val parsed = SecureUrlValidator.parseSafeHttpUrl(rawUrl, allowHttp = false, allowPrivateHosts = false)
            ?: return false
        val segments = parsed.pathSegments.filter { it.isNotEmpty() }
        if (segments.any { it == "." || it == ".." }) return false

        val hostLower = parsed.host.lowercase(Locale.ROOT)
        if (hostLower == "github.com") {
            // Expected: /drmganesh/SONORA-/releases/download/<tag>/<filename>
            if (segments.size < 6) return false
            return segments[0].equals(OFFICIAL_OWNER_LOWER, ignoreCase = true) &&
                segments[1].equals(OFFICIAL_REPO_LOWER, ignoreCase = true) &&
                segments[2].equals("releases", ignoreCase = true) &&
                segments[3].equals("download", ignoreCase = true)
        }

        if (hostLower == "api.github.com") {
            // Expected: /repos/drmganesh/SONORA-/releases/assets/<id>
            if (segments.size < 6) return false
            return segments[0].equals("repos", ignoreCase = true) &&
                segments[1].equals(OFFICIAL_OWNER_LOWER, ignoreCase = true) &&
                segments[2].equals(OFFICIAL_REPO_LOWER, ignoreCase = true) &&
                segments[3].equals("releases", ignoreCase = true) &&
                segments[4].equals("assets", ignoreCase = true)
        }

        return false
    }

    /**
     * Validates an HTTP redirect hop encountered while downloading an already-verified official
     * GitHub release asset. GitHub serves release binaries via signed redirects to
     * `objects.githubusercontent.com` / `release-assets.githubusercontent.com`.
     */
    fun isValidAssetRedirectUrl(rawUrl: String?): Boolean {
        val parsed = SecureUrlValidator.parseSafeHttpUrl(rawUrl, allowHttp = false, allowPrivateHosts = false)
            ?: return false
        val hostLower = parsed.host.lowercase(Locale.ROOT)
        if (hostLower == "github.com") {
            return isValidOfficialReleaseAssetUrl(parsed.toString())
        }
        return hostLower in ALLOWED_ASSET_CDN_HOSTS
    }

    /**
     * Resolves a relative or absolute Location redirect header against [baseUrl] and validates it.
     */
    fun resolveAndValidateRedirect(baseUrl: HttpUrl, locationHeader: String?): HttpUrl? {
        if (locationHeader.isNullOrBlank()) return null
        val resolved = baseUrl.resolve(locationHeader.trim()) ?: return null
        return if (isValidAssetRedirectUrl(resolved.toString())) resolved else null
    }
}
