package domain.update

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses official GitHub Releases API JSON responses, extracts numeric versionCode metadata
 * without lexicographic version-name comparisons, resolves optional companion checksum/metadata
 * assets, and selects the best universal or ABI-compatible APK asset.
 */
object GitHubReleaseParser {

    private val SHA256_HEX_REGEX = Regex("(?i)\\b([a-f0-9]{64})\\b")
    private val VERSION_CODE_PATTERNS = listOf(
        Regex("(?i)(?:version[_\\s-]*code|versioncode)\\s*[:=]\\s*(\\d{1,10})"),
        Regex("(?i)\\(\\s*build\\s+(\\d{1,10})\\s*\\)"),
        Regex("(?i)\\bbuild\\s*[:=#-]\\s*(\\d{1,10})\\b"),
        Regex("(?i)\\bbuild\\s+(\\d{1,10})\\b"),
        Regex("(?i)\\+(\\d{1,10})(?:\\b|$)"),
        Regex("(?i)(?:^|[-_.])vc(\\d{1,10})(?:[-_.]|$)")
    )
    private val SEMVER_NUMERIC_REGEX = Regex("(\\d+)\\.(\\d+)(?:\\.(\\d+))?")
    private val SOURCE_ARCHIVE_EXTENSIONS = listOf(".zip", ".tar.gz", ".tgz", ".tar", ".aab")

    /**
     * Parses a GitHub `/releases/latest` JSON payload into [GitHubReleaseInfo].
     * Returns null if JSON is malformed or does not belong to `drmganesh/SONORA-`.
     */
    fun parseReleaseJson(rawJson: String): GitHubReleaseInfo? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null

        return runCatching {
            val obj = JSONObject(trimmed)
            val htmlUrl = obj.optString("html_url", "").trim()
            if (htmlUrl.isNotEmpty() && !GitHubReleaseUrlValidator.isValidOfficialReleaseHtmlUrl(htmlUrl)) {
                return null
            }

            val releaseId = obj.optLong("id", 0L)
            val tagName = obj.optString("tag_name", "").trim()
            val releaseName = obj.optString("name", "").trim().ifBlank { tagName }
            if (tagName.isBlank() && releaseName.isBlank()) return null

            val body = obj.optString("body", "").trim()
            val publishedAt = obj.optString("published_at", "").trim()
            val isDraft = obj.optBoolean("draft", false)
            val isPrerelease = obj.optBoolean("prerelease", false)

            val assetsJson = obj.optJSONArray("assets") ?: JSONArray()
            val assets = mutableListOf<GitHubReleaseAsset>()
            for (i in 0 until assetsJson.length()) {
                val item = assetsJson.optJSONObject(i) ?: continue
                val assetName = item.optString("name", "").trim()
                val downloadUrl = item.optString("browser_download_url", "").trim()
                if (assetName.isBlank() || downloadUrl.isBlank()) continue
                val digestRaw = item.optString("digest", "").trim()
                val sha256 = parseSha256FromDigestField(digestRaw)
                assets.add(
                    GitHubReleaseAsset(
                        id = item.optLong("id", i.toLong()),
                        name = assetName,
                        sizeBytes = item.optLong("size", -1L),
                        contentType = item.optString("content_type", "").trim(),
                        downloadUrl = downloadUrl,
                        sha256Digest = sha256
                    )
                )
            }

            val versionName = extractCleanVersionName(tagName, releaseName)
            val versionCode = extractVersionCode(tagName, releaseName, body, assets)
            val (inlineMap, singleInline) = extractInlineChecksums(body)

            GitHubReleaseInfo(
                releaseId = releaseId,
                tagName = tagName,
                releaseName = releaseName,
                versionName = versionName,
                versionCode = versionCode,
                releaseNotes = body,
                publishedAtIso = publishedAt,
                htmlUrl = htmlUrl,
                isDraft = isDraft,
                isPrerelease = isPrerelease,
                assets = assets,
                inlineChecksums = inlineMap,
                singleInlineChecksum = singleInline
            )
        }.getOrNull()
    }

    /**
     * Parses Android Gradle's `output-metadata.json` or a simple `version.json` companion asset
     * to extract an authoritative numeric `versionCode` (if published alongside the APK).
     */
    fun parseCompanionVersionCode(rawJson: String): Long? {
        return runCatching {
            val obj = JSONObject(rawJson.trim())
            val directCode = obj.optLong("versionCode", -1L).takeIf { it > 0L }
                ?: obj.optLong("version_code", -1L).takeIf { it > 0L }
            if (directCode != null) return@runCatching directCode

            val elements = obj.optJSONArray("elements") ?: return@runCatching null
            var maxCode: Long? = null
            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val code = el.optLong("versionCode", -1L)
                if (code > 0L && (maxCode == null || code > maxCode)) {
                    maxCode = code
                }
            }
            maxCode
        }.getOrNull()
    }

    /**
     * Parses a `.sha256` or `SHA256SUMS` text file to find the SHA-256 hex digest for [targetAssetName].
     */
    fun parseChecksumFileForAsset(rawText: String, targetAssetName: String): String? {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val normalizedTarget = targetAssetName.lowercase(Locale.ROOT)
        var singleHashCandidate: String? = null
        var hashLineCount = 0

        for (line in lines) {
            val match = SHA256_HEX_REGEX.find(line) ?: continue
            val hash = match.groupValues[1].lowercase(Locale.ROOT)
            hashLineCount++
            if (singleHashCandidate == null) singleHashCandidate = hash
            if (line.lowercase(Locale.ROOT).contains(normalizedTarget)) {
                return hash
            }
        }
        return if (hashLineCount == 1) singleHashCandidate else null
    }

    /**
     * Finds a trusted companion metadata JSON asset (`output-metadata.json` / `version.json`)
     * belonging strictly to `drmganesh/SONORA-`.
     */
    fun findCompanionMetadataAsset(release: GitHubReleaseInfo): GitHubReleaseAsset? {
        return release.assets.firstOrNull { asset ->
            val lower = asset.name.lowercase(Locale.ROOT)
            (lower == "output-metadata.json" || lower == "version.json" || lower == "update-metadata.json") &&
                GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(asset.downloadUrl)
        }
    }

    /**
     * Finds a trusted companion checksum asset for [apkAsset] belonging strictly to `drmganesh/SONORA-`.
     */
    fun findCompanionChecksumAsset(
        release: GitHubReleaseInfo,
        apkAsset: GitHubReleaseAsset
    ): GitHubReleaseAsset? {
        val apkLower = apkAsset.name.lowercase(Locale.ROOT)
        val exactMatch = release.assets.firstOrNull { asset ->
            val lower = asset.name.lowercase(Locale.ROOT)
            (lower == "$apkLower.sha256" || lower == "$apkLower.sha256.txt") &&
                GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(asset.downloadUrl)
        }
        if (exactMatch != null) return exactMatch

        return release.assets.firstOrNull { asset ->
            val lower = asset.name.lowercase(Locale.ROOT)
            (lower == "sha256sums" ||
                lower == "sha256sums.txt" ||
                lower == "checksums.txt" ||
                lower == "sha256.txt" ||
                lower.endsWith(".sha256")) &&
                GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(asset.downloadUrl)
        }
    }

    /**
     * Resolves an already-embedded SHA-256 checksum for [apkAsset] from either GitHub's asset
     * `digest` field or the release notes body.
     */
    fun resolveEmbeddedSha256(
        release: GitHubReleaseInfo,
        apkAsset: GitHubReleaseAsset
    ): String? {
        apkAsset.sha256Digest?.takeIf { isValidSha256Hex(it) }?.let { return it.lowercase(Locale.ROOT) }
        val byName = release.inlineChecksums[apkAsset.name.lowercase(Locale.ROOT)]
        if (!byName.isNullOrBlank() && isValidSha256Hex(byName)) return byName.lowercase(Locale.ROOT)

        // Only use an unlabeled single inline checksum when there is a single APK asset in the release
        val apkCount = release.assets.count { isApkAssetCandidate(it) }
        if (apkCount == 1) {
            release.singleInlineChecksum?.takeIf { isValidSha256Hex(it) }?.let {
                return it.lowercase(Locale.ROOT)
            }
        }
        return null
    }

    /**
     * Determines whether [release] represents a newer stable release than the installed app.
     *
     * Rules:
     * - Draft releases and prereleases are always ignored.
     * - Primary comparison compares numeric `release.versionCode > installedVersionCode`.
     * - Never compares version names lexicographically.
     * - Only when a release omits an explicit `versionCode` in API/metadata, compares parsed
     *   integer `(major, minor, patch)` tuples strictly numerically; final install still verifies
     *   the downloaded APK's actual `versionCode > installedVersionCode`.
     */
    fun isReleaseNewerThanInstalled(
        release: GitHubReleaseInfo,
        installedVersionCode: Long,
        installedVersionName: String,
        overrideVersionCode: Long? = null
    ): Boolean {
        if (release.isDraft || release.isPrerelease) return false
        val effectiveCode = overrideVersionCode ?: release.versionCode
        if (effectiveCode != null && effectiveCode > 0L) {
            return effectiveCode > installedVersionCode
        }
        val remoteTuple = parseNumericVersionTuple(release.versionName) ?: return false
        val localTuple = parseNumericVersionTuple(installedVersionName) ?: return false
        return compareVersionTuples(remoteTuple, localTuple) > 0
    }

    /**
     * Selects the best compatible APK asset from [release] for a device supporting [supportedAbis]:
     * 1. Rejects non-APK files, source archives, and assets not hosted on `drmganesh/SONORA-`.
     * 2. Prefers a universal APK if available.
     * 3. Otherwise selects the highest-priority ABI match from [supportedAbis].
     * 4. Returns [AssetSelectionResult.NoCompatibleApk] if no universal or ABI-matching APK exists.
     */
    fun selectCompatibleApkAsset(
        release: GitHubReleaseInfo,
        supportedAbis: List<String>
    ): AssetSelectionResult {
        val validApkAssets = release.assets.filter { asset ->
            isApkAssetCandidate(asset) &&
                GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(asset.downloadUrl)
        }
        if (validApkAssets.isEmpty()) {
            return AssetSelectionResult.NoCompatibleApk()
        }

        val classified = validApkAssets.map { asset ->
            asset to classifyApkAbi(asset.name)
        }

        // 1. Prefer explicit universal APK first, then architecture-agnostic universal APK
        val explicitUniversal = classified.firstOrNull { (asset, target) ->
            target == ReleaseAbiTarget.UNIVERSAL &&
                hasExplicitUniversalMarker(asset.name)
        }
        if (explicitUniversal != null) {
            return AssetSelectionResult.Selected(
                asset = explicitUniversal.first,
                abiTarget = ReleaseAbiTarget.UNIVERSAL,
                isUniversal = true
            )
        }

        val implicitUniversal = classified.firstOrNull { (_, target) ->
            target == ReleaseAbiTarget.UNIVERSAL
        }
        if (implicitUniversal != null) {
            return AssetSelectionResult.Selected(
                asset = implicitUniversal.first,
                abiTarget = ReleaseAbiTarget.UNIVERSAL,
                isUniversal = true
            )
        }

        // 2. Match device ABIs in Android priority order (Build.SUPPORTED_ABIS)
        val normalizedDeviceAbis = supportedAbis
            .map { it.trim().lowercase(Locale.ROOT) }
            .filter { it.isNotEmpty() }

        for (deviceAbi in normalizedDeviceAbis) {
            val match = classified.firstOrNull { (_, target) ->
                target.androidAbi?.equals(deviceAbi, ignoreCase = true) == true
            }
            if (match != null) {
                return AssetSelectionResult.Selected(
                    asset = match.first,
                    abiTarget = match.second,
                    isUniversal = false
                )
            }
        }

        return AssetSelectionResult.NoCompatibleApk()
    }

    fun classifyApkAbi(fileName: String): ReleaseAbiTarget {
        val lower = fileName.lowercase(Locale.ROOT)
        if (hasExplicitUniversalMarker(lower)) return ReleaseAbiTarget.UNIVERSAL
        if (lower.contains("arm64-v8a") || lower.contains("arm64_v8a") ||
            lower.contains("arm64") || lower.contains("aarch64")
        ) {
            return ReleaseAbiTarget.ARM64_V8A
        }
        if (lower.contains("armeabi-v7a") || lower.contains("armeabi_v7a") ||
            lower.contains("armv7") || lower.contains("armeabi")
        ) {
            return ReleaseAbiTarget.ARMEABI_V7A
        }
        if (lower.contains("x86_64") || lower.contains("x86-64") || lower.contains("amd64")) {
            return ReleaseAbiTarget.X86_64
        }
        if (Regex("(^|[^a-z0-9])x86([^a-z0-9]|$)").containsMatchIn(lower)) {
            return ReleaseAbiTarget.X86
        }
        if (lower.contains("mips") || lower.contains("riscv")) {
            return ReleaseAbiTarget.OTHER
        }
        return ReleaseAbiTarget.UNIVERSAL
    }

    fun isApkAssetCandidate(asset: GitHubReleaseAsset): Boolean {
        val lowerName = asset.name.lowercase(Locale.ROOT)
        if (SOURCE_ARCHIVE_EXTENSIONS.any { lowerName.endsWith(it) }) return false
        if (!lowerName.endsWith(".apk")) return false
        if (lowerName.endsWith(".apk.sha256") || lowerName.endsWith(".apk.sig")) return false
        return true
    }

    fun formatReleaseDate(isoTimestamp: String): String {
        if (isoTimestamp.isBlank()) return ""
        return runCatching {
            val instant = Instant.parse(isoTimestamp.trim())
            val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        }.getOrDefault(isoTimestamp.take(10))
    }

    fun formatShortReleaseNotes(rawNotes: String, maxChars: Int = 600): String {
        if (rawNotes.isBlank()) return "Bug fixes, stability improvements, and latest SONORA enhancements."
        val cleaned = rawNotes
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .lines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.matches(Regex("(?i)^(?:sha-?256|checksum)\\s*[:=]?\\s*[a-f0-9]{64}.*$")) ||
                    trimmed.matches(Regex("(?i)^[a-f0-9]{64}\\s+\\S+\\.apk$"))
            }
            .joinToString("\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (cleaned.isBlank()) return "Bug fixes, stability improvements, and latest SONORA enhancements."
        return if (cleaned.length > maxChars) {
            cleaned.take(maxChars).trimEnd() + "…"
        } else {
            cleaned
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return ""
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
            else -> "$bytes B"
        }
    }

    fun isValidSha256Hex(value: String?): Boolean {
        return !value.isNullOrBlank() && value.trim().matches(Regex("(?i)^[a-f0-9]{64}$"))
    }

    internal fun parseNumericVersionTuple(versionName: String): Triple<Int, Int, Int>? {
        val match = SEMVER_NUMERIC_REGEX.find(versionName) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].toIntOrNull() ?: return null
        val patch = match.groupValues[3].ifBlank { "0" }.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    private fun compareVersionTuples(a: Triple<Int, Int, Int>, b: Triple<Int, Int, Int>): Int {
        if (a.first != b.first) return a.first.compareTo(b.first)
        if (a.second != b.second) return a.second.compareTo(b.second)
        return a.third.compareTo(b.third)
    }

    private fun hasExplicitUniversalMarker(lowerName: String): Boolean {
        val lower = lowerName.lowercase(Locale.ROOT)
        return lower.contains("universal") ||
            Regex("(^|[-_.])all([-_.]|\\.apk$)").containsMatchIn(lower) ||
            Regex("(^|[-_.])fat([-_.]|\\.apk$)").containsMatchIn(lower)
    }

    private fun extractCleanVersionName(tagName: String, releaseName: String): String {
        val candidate = tagName.ifBlank { releaseName }.trim()
        val withoutBuild = candidate.substringBefore("+").trim()
        return withoutBuild.removePrefix("v").removePrefix("V").ifBlank { candidate }
    }

    private fun extractVersionCode(
        tagName: String,
        releaseName: String,
        body: String,
        assets: List<GitHubReleaseAsset>
    ): Long? {
        val sources = buildList {
            add(body)
            add(tagName)
            add(releaseName)
            assets.filter { isApkAssetCandidate(it) }.forEach { add(it.name) }
        }
        for (source in sources) {
            if (source.isBlank()) continue
            for (pattern in VERSION_CODE_PATTERNS) {
                val match = pattern.find(source)
                val code = match?.groupValues?.getOrNull(1)?.toLongOrNull()
                if (code != null && code > 0L) {
                    return code
                }
            }
        }
        return null
    }

    private fun parseSha256FromDigestField(digestRaw: String): String? {
        if (digestRaw.isBlank()) return null
        val candidate = if (digestRaw.startsWith("sha256:", ignoreCase = true)) {
            digestRaw.substringAfter(":").trim()
        } else {
            digestRaw.trim()
        }
        return candidate.lowercase(Locale.ROOT).takeIf { isValidSha256Hex(it) }
    }

    private fun extractInlineChecksums(body: String): Pair<Map<String, String>, String?> {
        if (body.isBlank()) return emptyMap<String, String>() to null
        val byAsset = mutableMapOf<String, String>()
        var firstHash: String? = null
        var distinctHashCount = 0
        val seenHashes = mutableSetOf<String>()

        for (line in body.lines()) {
            val match = SHA256_HEX_REGEX.find(line) ?: continue
            val hash = match.groupValues[1].lowercase(Locale.ROOT)
            if (seenHashes.add(hash)) {
                distinctHashCount++
                if (firstHash == null) firstHash = hash
            }
            val apkMatch = Regex("([A-Za-z0-9._-]+\\.apk)", RegexOption.IGNORE_CASE).find(line)
            if (apkMatch != null) {
                byAsset[apkMatch.groupValues[1].lowercase(Locale.ROOT)] = hash
            }
        }
        val singleHash = if (distinctHashCount == 1) firstHash else null
        return byAsset to singleHash
    }
}
