package domain.update

import java.io.File

/**
 * Immutable repository constants for SONORA's official GitHub Releases update pipeline.
 */
object SonoraGitHubUpdateConfig {
    const val REPOSITORY_OWNER = "drmganesh"
    const val REPOSITORY_NAME = "SONORA-"
    const val REPOSITORY_WEB_URL = "https://github.com/drmganesh/SONORA-"
    const val LATEST_RELEASE_API_URL = "https://api.github.com/repos/drmganesh/SONORA-/releases/latest"
    const val DEFAULT_CHECK_INTERVAL_HOURS = 12
    const val MIN_CHECK_INTERVAL_HOURS = 1
    const val MAX_CHECK_INTERVAL_HOURS = 168
    const val MAX_APK_SIZE_BYTES = 250L * 1024L * 1024L // 250 MB safety cap
    const val MAX_METADATA_JSON_BYTES = 512L * 1024L // 512 KB cap for release/metadata JSON
    const val MAX_CHECKSUM_TEXT_BYTES = 128L * 1024L // 128 KB cap for .sha256 files
    const val UPDATE_CACHE_DIR_NAME = "app_updates"
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
}

enum class ReleaseAbiTarget(val androidAbi: String?) {
    UNIVERSAL(null),
    ARM64_V8A("arm64-v8a"),
    ARMEABI_V7A("armeabi-v7a"),
    X86_64("x86_64"),
    X86("x86"),
    OTHER(null)
}

data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val contentType: String,
    val downloadUrl: String,
    val sha256Digest: String? = null
)

data class GitHubReleaseInfo(
    val releaseId: Long,
    val tagName: String,
    val releaseName: String,
    val versionName: String,
    val versionCode: Long?,
    val releaseNotes: String,
    val publishedAtIso: String,
    val htmlUrl: String,
    val isDraft: Boolean,
    val isPrerelease: Boolean,
    val assets: List<GitHubReleaseAsset>,
    val inlineChecksums: Map<String, String> = emptyMap(),
    val singleInlineChecksum: String? = null
)

sealed interface AssetSelectionResult {
    data class Selected(
        val asset: GitHubReleaseAsset,
        val abiTarget: ReleaseAbiTarget,
        val isUniversal: Boolean
    ) : AssetSelectionResult

    data class NoCompatibleApk(
        val message: String = "No compatible APK is available for this release."
    ) : AssetSelectionResult
}

sealed interface UpdateCheckOutcome {
    data class UpdateAvailable(
        val release: GitHubReleaseInfo,
        val selectedAsset: GitHubReleaseAsset?,
        val assetErrorMessage: String?,
        val effectiveVersionCode: Long,
        val fromCache: Boolean = false
    ) : UpdateCheckOutcome

    data class UpToDate(
        val latestRelease: GitHubReleaseInfo? = null,
        val reason: String = "SONORA is up to date."
    ) : UpdateCheckOutcome

    data class Error(
        val kind: UpdateErrorKind,
        val message: String
    ) : UpdateCheckOutcome
}

enum class UpdateErrorKind {
    OFFLINE,
    RATE_LIMITED,
    TIMEOUT,
    HTTP_ERROR,
    INVALID_RESPONSE,
    NO_RELEASE_FOUND,
    NO_COMPATIBLE_APK,
    DOWNLOAD_FAILED,
    DOWNLOAD_CANCELLED,
    CHECKSUM_MISMATCH,
    INVALID_APK,
    PACKAGE_MISMATCH,
    SIGNATURE_MISMATCH,
    DOWNGRADE_REJECTED,
    PERMISSION_DENIED,
    INSTALL_CANCELLED,
    INSTALL_FAILED
}

data class VerifiedApkInfo(
    val apkFile: File,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val fileSizeBytes: Long,
    val sha256Hex: String,
    val checksumVerifiedAgainstTrustedSource: Boolean
)

sealed interface ApkVerificationResult {
    data class Valid(val info: VerifiedApkInfo) : ApkVerificationResult
    data class Invalid(
        val kind: UpdateErrorKind,
        val message: String
    ) : ApkVerificationResult
}

/**
 * Snapshot persisted in DataStore for update checking metadata and post-update detection.
 */
data class AppUpdatePersistedState(
    val lastCheckedAtMs: Long = 0L,
    val checkIntervalHours: Int = SonoraGitHubUpdateConfig.DEFAULT_CHECK_INTERVAL_HOURS,
    val cachedReleaseJson: String = "",
    val cachedLatestVersionName: String = "",
    val cachedLatestVersionCode: Long = 0L,
    val dismissedUpdateVersionCode: Long = 0L,
    val dismissedUpdateTag: String = "",
    val pendingInstallFromVersionCode: Long = 0L,
    val pendingInstallTargetVersionCode: Long = 0L,
    val pendingInstallTargetVersionName: String = "",
    val pendingInstallPackageName: String = "",
    val pendingInstallInitiatedAtMs: Long = 0L,
    val verifiedCompletedUpdateVersionCode: Long = 0L,
    val verifiedCompletedUpdateVersionName: String = "",
    val lastReminderDismissedVersionCode: Long = 0L
)

/**
 * UI state driving both Settings -> About -> App Updates and global update dialogs.
 */
sealed interface AppUpdateDialogState {
    data object None : AppUpdateDialogState

    data class Available(
        val release: GitHubReleaseInfo,
        val selectedAsset: GitHubReleaseAsset?,
        val assetErrorMessage: String?,
        val installedVersionName: String,
        val installedVersionCode: Long,
        val resolvedVersionCode: Long
    ) : AppUpdateDialogState

    data class Downloading(
        val release: GitHubReleaseInfo,
        val asset: GitHubReleaseAsset,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val statusText: String = "Downloading update..."
    ) : AppUpdateDialogState {
        val progressFraction: Float?
            get() = if (totalBytes > 0L) {
                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else null
    }

    data class Verifying(
        val release: GitHubReleaseInfo,
        val statusText: String = "Verifying APK integrity and signature..."
    ) : AppUpdateDialogState

    data class ReadyToInstall(
        val release: GitHubReleaseInfo,
        val verifiedApk: VerifiedApkInfo
    ) : AppUpdateDialogState

    data class PermissionRequired(
        val release: GitHubReleaseInfo,
        val verifiedApk: VerifiedApkInfo,
        val rationaleMessage: String
    ) : AppUpdateDialogState

    data class Error(
        val kind: UpdateErrorKind,
        val title: String,
        val message: String,
        val releaseForRetry: GitHubReleaseInfo? = null,
        val verifiedApkForRetry: VerifiedApkInfo? = null
    ) : AppUpdateDialogState
}

data class PostUpdateReminderState(
    val installedVersionCode: Long,
    val installedVersionName: String,
    val isUnknownSourcesPermissionEnabled: Boolean
)

data class AppUpdateSummaryState(
    val installedVersionName: String = "",
    val installedVersionCode: Long = 0L,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val isUpdateAvailable: Boolean = false,
    val isChecking: Boolean = false,
    val lastCheckedAtMs: Long = 0L,
    val statusMessage: String? = null,
    val errorKind: UpdateErrorKind? = null,
    val cachedAvailableRelease: GitHubReleaseInfo? = null
)
