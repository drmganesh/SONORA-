package data.update

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import data.preferences.AppUpdatePreferences
import domain.update.ApkVerificationResult
import domain.update.AppUpdateDialogState
import domain.update.AppUpdateSummaryState
import domain.update.AssetSelectionResult
import domain.update.GitHubReleaseInfo
import domain.update.GitHubReleaseParser
import domain.update.PostUpdateReminderState
import domain.update.PostUpdateVerificationEngine
import domain.update.SonoraGitHubUpdateConfig
import domain.update.UpdateCheckOutcome
import domain.update.UpdateErrorKind
import domain.update.VerifiedApkInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.settings.InstalledAppVersionInfo
import ui.settings.readInstalledAppVersion
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Coordinates SONORA's complete GitHub Releases update lifecycle:
 * - Automatic launch checks throttled by configurable interval & cached release metadata
 * - Manual checks from Settings -> About -> App Updates
 * - Universal/ABI-aware APK selection, isolated cache download, cancellation, and verification
 * - Unknown App Sources permission handling and FileProvider PackageInstaller handoff
 * - Verified post-update detection and one-time dismissible security reminder
 */
class AppUpdateManager(
    context: Context,
    private val scope: CoroutineScope,
    private val preferences: AppUpdatePreferences = AppUpdatePreferences(context),
    private val transport: GitHubReleaseTransport = OkHttpGitHubReleaseTransport(),
    private val binaryDownloader: ApkBinaryDownloader = OkHttpApkBinaryDownloader(),
    private val packageInspector: ApkPackageInspector = AndroidApkPackageInspector(context),
    val installerLauncher: ApkInstallerLauncher = ApkInstallerLauncher(context),
    private val installedVersionProvider: () -> InstalledAppVersionInfo = { readInstalledAppVersion(context) },
    private val installedPackageNameProvider: () -> String = { context.applicationContext.packageName },
    private val supportedAbisProvider: () -> List<String> = { Build.SUPPORTED_ABIS?.toList().orEmpty() },
    private val networkAvailableProvider: () -> Boolean = { isDeviceNetworkConnected(context) },
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    private val verifier = ApkPackageVerifier(packageInspector)

    private val _summaryState = MutableStateFlow(buildInitialSummary())
    val summaryState: StateFlow<AppUpdateSummaryState> = _summaryState.asStateFlow()

    private val _dialogState = MutableStateFlow<AppUpdateDialogState>(AppUpdateDialogState.None)
    val dialogState: StateFlow<AppUpdateDialogState> = _dialogState.asStateFlow()

    private val _postUpdateReminder = MutableStateFlow<PostUpdateReminderState?>(null)
    val postUpdateReminder: StateFlow<PostUpdateReminderState?> = _postUpdateReminder.asStateFlow()

    private var activeCheckJob: Job? = null
    private var activeDownloadJob: Job? = null
    private var hasEvaluatedLaunchReminder = false
    private var hasTriggeredAutomaticLaunchCheck = false
    private var awaitingUnknownSourcesFor: Pair<GitHubReleaseInfo, VerifiedApkInfo>? = null

    init {
        scope.launch {
            preferences.stateFlow.collect { persisted ->
                val installed = installedVersionProvider()
                val cachedRelease = persisted.cachedReleaseJson
                    .takeIf { it.isNotBlank() }
                    ?.let { GitHubReleaseParser.parseReleaseJson(it) }
                val effectiveCode = cachedRelease?.versionCode
                    ?: persisted.cachedLatestVersionCode.takeIf { it > 0L }
                val isNewer = cachedRelease != null && GitHubReleaseParser.isReleaseNewerThanInstalled(
                    release = cachedRelease,
                    installedVersionCode = installed.versionCode,
                    installedVersionName = installed.versionName,
                    overrideVersionCode = effectiveCode
                )
                _summaryState.update { current ->
                    current.copy(
                        installedVersionName = installed.versionName,
                        installedVersionCode = installed.versionCode,
                        latestVersionName = cachedRelease?.versionName
                            ?: persisted.cachedLatestVersionName.takeIf { it.isNotBlank() },
                        latestVersionCode = effectiveCode,
                        isUpdateAvailable = isNewer,
                        lastCheckedAtMs = persisted.lastCheckedAtMs,
                        cachedAvailableRelease = if (isNewer) cachedRelease else null
                    )
                }
            }
        }
    }

    private fun buildInitialSummary(): AppUpdateSummaryState {
        val installed = installedVersionProvider()
        return AppUpdateSummaryState(
            installedVersionName = installed.versionName,
            installedVersionCode = installed.versionCode
        )
    }

    /**
     * Evaluates on launch whether SONORA just completed a genuine update initiated through
     * the official update flow. Safe to call multiple times; guarded per process launch.
     */
    suspend fun evaluatePostUpdateReminderOnLaunch(): PostUpdateReminderState? {
        if (hasEvaluatedLaunchReminder && _postUpdateReminder.value != null) {
            return _postUpdateReminder.value
        }
        hasEvaluatedLaunchReminder = true

        val installed = installedVersionProvider()
        val persisted = preferences.getSnapshot()
        val permissionEnabled = installerLauncher.canRequestPackageInstalls()

        val evaluation = PostUpdateVerificationEngine.evaluateOnLaunch(
            persisted = persisted,
            currentPackageName = installedPackageNameProvider(),
            currentVersionCode = installed.versionCode,
            currentVersionName = installed.versionName,
            isUnknownSourcesPermissionEnabled = permissionEnabled
        )

        if (evaluation.clearPendingSession) {
            preferences.clearPendingInstallSession()
        }
        if (evaluation.shouldCleanUpdateCache) {
            withContext(Dispatchers.IO) {
                installerLauncher.clearUpdateCache()
            }
        }
        evaluation.markVerifiedCompletedVersionCode?.let { verifiedCode ->
            preferences.markVerifiedCompletedUpdate(verifiedCode, installed.versionName)
        }

        _postUpdateReminder.value = if (evaluation.showReminder) evaluation.reminderState else null
        return _postUpdateReminder.value
    }

    /**
     * Refreshes the unknown-app-sources state in the active post-update reminder after the user
     * returns from Android Settings.
     */
    fun refreshPostUpdateReminderPermissionState() {
        val current = _postUpdateReminder.value ?: return
        _postUpdateReminder.value = current.copy(
            isUnknownSourcesPermissionEnabled = installerLauncher.canRequestPackageInstalls()
        )
    }

    /**
     * Permanently dismisses the one-time post-update security reminder for the currently
     * installed versionCode. Never repeats for this same versionCode.
     */
    fun dismissPostUpdateReminder() {
        val reminder = _postUpdateReminder.value ?: return
        _postUpdateReminder.value = null
        scope.launch {
            preferences.markPostUpdateReminderDismissed(reminder.installedVersionCode)
        }
    }

    /**
     * Runs once on app open to check for updates respecting check interval, cache, and "Later".
     */
    fun triggerAutomaticLaunchCheckIfNeeded() {
        if (hasTriggeredAutomaticLaunchCheck) return
        hasTriggeredAutomaticLaunchCheck = true
        scope.launch {
            evaluatePostUpdateReminderOnLaunch()
            // Avoid stacking the update popup directly on top of the post-update celebration notice
            if (_postUpdateReminder.value == null) {
                checkForUpdatesSuspend(isManual = false)
            }
        }
    }

    /**
     * Triggers an explicit user check from Settings -> About -> App Updates.
     * Bypasses cache throttle and previous "Later" dismissal.
     */
    fun checkForUpdatesManual() {
        activeCheckJob?.cancel()
        activeCheckJob = scope.launch {
            checkForUpdatesSuspend(isManual = true)
        }
    }

    /**
     * Re-opens the Update Available dialog from cached release metadata if available.
     */
    fun openCachedAvailableUpdateDialog() {
        val release = _summaryState.value.cachedAvailableRelease ?: return
        val installed = installedVersionProvider()
        val effectiveCode = release.versionCode ?: _summaryState.value.latestVersionCode ?: 0L
        val selection = GitHubReleaseParser.selectCompatibleApkAsset(release, supportedAbisProvider())
        val (asset, errorMsg) = when (selection) {
            is AssetSelectionResult.Selected -> selection.asset to null
            is AssetSelectionResult.NoCompatibleApk -> null to selection.message
        }
        _dialogState.value = AppUpdateDialogState.Available(
            release = release,
            selectedAsset = asset,
            assetErrorMessage = errorMsg,
            installedVersionName = installed.versionName,
            installedVersionCode = installed.versionCode,
            resolvedVersionCode = effectiveCode
        )
    }

    suspend fun checkForUpdatesSuspend(isManual: Boolean): UpdateCheckOutcome {
        val installed = installedVersionProvider()
        val persisted = preferences.getSnapshot()
        val now = clockMs()
        val intervalMs = TimeUnit.HOURS.toMillis(persisted.checkIntervalHours.toLong())

        // For automatic checks within the configured interval, reuse cached release metadata
        if (!isManual && persisted.lastCheckedAtMs > 0L && (now - persisted.lastCheckedAtMs) < intervalMs) {
            val cachedRelease = persisted.cachedReleaseJson
                .takeIf { it.isNotBlank() }
                ?.let { GitHubReleaseParser.parseReleaseJson(it) }

            if (cachedRelease != null) {
                val cachedCode = cachedRelease.versionCode
                    ?: persisted.cachedLatestVersionCode.takeIf { it > 0L }
                return handleParsedRelease(
                    release = cachedRelease,
                    resolvedVersionCode = cachedCode,
                    installed = installed,
                    persisted = persisted,
                    isManual = false,
                    fromCache = true
                )
            }
            return UpdateCheckOutcome.UpToDate(reason = "Checked recently.")
        }

        if (!networkAvailableProvider()) {
            val msg = "You're offline. Connect to the internet to check for updates."
            _summaryState.update {
                it.copy(
                    isChecking = false,
                    statusMessage = msg,
                    errorKind = UpdateErrorKind.OFFLINE
                )
            }
            return UpdateCheckOutcome.Error(UpdateErrorKind.OFFLINE, msg)
        }

        _summaryState.update {
            it.copy(
                isChecking = true,
                statusMessage = "Checking GitHub Releases…",
                errorKind = null
            )
        }

        val fetchResult = transport.fetchLatestReleaseJson()
        return when (fetchResult) {
            is GitHubFetchResult.Failure -> {
                _summaryState.update {
                    it.copy(
                        isChecking = false,
                        statusMessage = fetchResult.message,
                        errorKind = fetchResult.kind
                    )
                }
                if (isManual) {
                    _dialogState.value = AppUpdateDialogState.Error(
                        kind = fetchResult.kind,
                        title = "Update Check Failed",
                        message = fetchResult.message
                    )
                }
                UpdateCheckOutcome.Error(fetchResult.kind, fetchResult.message)
            }

            is GitHubFetchResult.Success -> {
                val parsed = GitHubReleaseParser.parseReleaseJson(fetchResult.body)
                if (parsed == null) {
                    val msg = "Received an unrecognized or untrusted release payload from GitHub."
                    _summaryState.update {
                        it.copy(
                            isChecking = false,
                            statusMessage = msg,
                            errorKind = UpdateErrorKind.INVALID_RESPONSE
                        )
                    }
                    if (isManual) {
                        _dialogState.value = AppUpdateDialogState.Error(
                            kind = UpdateErrorKind.INVALID_RESPONSE,
                            title = "Update Check Failed",
                            message = msg
                        )
                    }
                    return UpdateCheckOutcome.Error(UpdateErrorKind.INVALID_RESPONSE, msg)
                }

                // Ignore drafts and prereleases strictly
                if (parsed.isDraft || parsed.isPrerelease) {
                    preferences.saveLastCheckedTimestamp(now)
                    val reason = "No newer stable release is available."
                    _summaryState.update {
                        it.copy(
                            isChecking = false,
                            isUpdateAvailable = false,
                            lastCheckedAtMs = now,
                            statusMessage = reason,
                            errorKind = null,
                            cachedAvailableRelease = null
                        )
                    }
                    return UpdateCheckOutcome.UpToDate(latestRelease = parsed, reason = reason)
                }

                // Optionally inspect official companion output-metadata.json / version.json for numeric versionCode
                var resolvedVersionCode = parsed.versionCode
                if (resolvedVersionCode == null) {
                    val metadataAsset = GitHubReleaseParser.findCompanionMetadataAsset(parsed)
                    if (metadataAsset != null) {
                        val metaFetch = transport.fetchOfficialAssetText(
                            metadataAsset.downloadUrl,
                            SonoraGitHubUpdateConfig.MAX_METADATA_JSON_BYTES
                        )
                        if (metaFetch is GitHubFetchResult.Success) {
                            resolvedVersionCode = GitHubReleaseParser.parseCompanionVersionCode(metaFetch.body)
                        }
                    }
                }

                preferences.saveReleaseCheckSuccess(
                    checkedAtMs = now,
                    rawReleaseJson = fetchResult.body,
                    latestVersionName = parsed.versionName,
                    latestVersionCode = resolvedVersionCode ?: 0L
                )

                handleParsedRelease(
                    release = parsed,
                    resolvedVersionCode = resolvedVersionCode,
                    installed = installed,
                    persisted = preferences.getSnapshot(),
                    isManual = isManual,
                    fromCache = false
                )
            }
        }
    }

    private fun handleParsedRelease(
        release: GitHubReleaseInfo,
        resolvedVersionCode: Long?,
        installed: InstalledAppVersionInfo,
        persisted: domain.update.AppUpdatePersistedState,
        isManual: Boolean,
        fromCache: Boolean
    ): UpdateCheckOutcome {
        val isNewer = GitHubReleaseParser.isReleaseNewerThanInstalled(
            release = release,
            installedVersionCode = installed.versionCode,
            installedVersionName = installed.versionName,
            overrideVersionCode = resolvedVersionCode
        )

        if (!isNewer) {
            val msg = "SONORA is up to date (v${installed.versionName})."
            _summaryState.update {
                it.copy(
                    isChecking = false,
                    latestVersionName = release.versionName,
                    latestVersionCode = resolvedVersionCode,
                    isUpdateAvailable = false,
                    statusMessage = msg,
                    errorKind = null,
                    cachedAvailableRelease = null
                )
            }
            return UpdateCheckOutcome.UpToDate(latestRelease = release, reason = msg)
        }

        val effectiveCode = resolvedVersionCode ?: 0L
        val selection = GitHubReleaseParser.selectCompatibleApkAsset(release, supportedAbisProvider())
        val (asset, assetError) = when (selection) {
            is AssetSelectionResult.Selected -> selection.asset to null
            is AssetSelectionResult.NoCompatibleApk -> null to selection.message
        }

        _summaryState.update {
            it.copy(
                isChecking = false,
                latestVersionName = release.versionName,
                latestVersionCode = resolvedVersionCode,
                isUpdateAvailable = true,
                statusMessage = assetError ?: "Update v${release.versionName} available",
                errorKind = if (asset == null) UpdateErrorKind.NO_COMPATIBLE_APK else null,
                cachedAvailableRelease = release
            )
        }

        val suppressed = PostUpdateVerificationEngine.isSuppressedByLaterChoice(
            persisted = persisted,
            releaseVersionCode = effectiveCode,
            releaseTag = release.tagName,
            isManualCheck = isManual
        )

        if (!suppressed) {
            _dialogState.value = AppUpdateDialogState.Available(
                release = release,
                selectedAsset = asset,
                assetErrorMessage = assetError,
                installedVersionName = installed.versionName,
                installedVersionCode = installed.versionCode,
                resolvedVersionCode = effectiveCode
            )
        }

        return UpdateCheckOutcome.UpdateAvailable(
            release = release,
            selectedAsset = asset,
            assetErrorMessage = assetError,
            effectiveVersionCode = effectiveCode,
            fromCache = fromCache
        )
    }

    /**
     * User tapped "Later" on the Update Available dialog. Suppresses automatic popups for this
     * release version/tag until the user manually checks again or a newer release is published.
     */
    fun deferUpdateLater() {
        val state = _dialogState.value
        _dialogState.value = AppUpdateDialogState.None
        if (state is AppUpdateDialogState.Available) {
            scope.launch {
                preferences.markReleaseDismissedLater(
                    versionCode = state.resolvedVersionCode,
                    tagName = state.release.tagName
                )
            }
        }
    }

    /**
     * Starts downloading and verifying the compatible APK asset for the active release.
     */
    fun startDownloadForRelease(release: GitHubReleaseInfo) {
        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            downloadAndVerifyReleaseSuspend(release)
        }
    }

    suspend fun downloadAndVerifyReleaseSuspend(release: GitHubReleaseInfo): ApkVerificationResult {
        val selection = GitHubReleaseParser.selectCompatibleApkAsset(release, supportedAbisProvider())
        val selected = when (selection) {
            is AssetSelectionResult.Selected -> selection.asset
            is AssetSelectionResult.NoCompatibleApk -> {
                _dialogState.value = AppUpdateDialogState.Error(
                    kind = UpdateErrorKind.NO_COMPATIBLE_APK,
                    title = "Update Unavailable",
                    message = selection.message
                )
                return ApkVerificationResult.Invalid(UpdateErrorKind.NO_COMPATIBLE_APK, selection.message)
            }
        }

        if (!networkAvailableProvider()) {
            val msg = "You're offline. Connect to the internet to download the update."
            _dialogState.value = AppUpdateDialogState.Error(
                kind = UpdateErrorKind.OFFLINE,
                title = "Download Failed",
                message = msg,
                releaseForRetry = release
            )
            return ApkVerificationResult.Invalid(UpdateErrorKind.OFFLINE, msg)
        }

        _dialogState.value = AppUpdateDialogState.Downloading(
            release = release,
            asset = selected,
            bytesDownloaded = 0L,
            totalBytes = selected.sizeBytes,
            statusText = "Downloading ${selected.name}…"
        )

        // Resolve trusted SHA-256 checksum (embedded asset digest, release notes, or companion .sha256)
        var trustedSha256 = GitHubReleaseParser.resolveEmbeddedSha256(release, selected)
        if (trustedSha256 == null) {
            val checksumAsset = GitHubReleaseParser.findCompanionChecksumAsset(release, selected)
            if (checksumAsset != null) {
                val checksumFetch = transport.fetchOfficialAssetText(
                    checksumAsset.downloadUrl,
                    SonoraGitHubUpdateConfig.MAX_CHECKSUM_TEXT_BYTES
                )
                if (checksumFetch is GitHubFetchResult.Success) {
                    trustedSha256 = GitHubReleaseParser.parseChecksumFileForAsset(
                        checksumFetch.body,
                        selected.name
                    )
                }
            }
        }

        val cacheDir = installerLauncher.getUpdateCacheDirectory()
        val safeToken = (release.versionCode?.takeIf { it > 0L }?.toString()
            ?: release.releaseId.takeIf { it > 0L }?.toString()
            ?: "latest")
        val destinationApk = File(cacheDir, "sonora-update-$safeToken.apk")

        val transferResult = try {
            binaryDownloader.downloadToFile(
                assetUrl = selected.downloadUrl,
                expectedSizeBytes = selected.sizeBytes,
                destinationFile = destinationApk
            ) { downloaded, total ->
                _dialogState.value = AppUpdateDialogState.Downloading(
                    release = release,
                    asset = selected,
                    bytesDownloaded = downloaded,
                    totalBytes = total,
                    statusText = "Downloading SONORA v${release.versionName}…"
                )
            }
        } catch (ce: CancellationException) {
            destinationApk.delete()
            _dialogState.value = AppUpdateDialogState.None
            throw ce
        }

        when (transferResult) {
            is ApkDownloadTransferResult.Failed -> {
                destinationApk.delete()
                _dialogState.value = AppUpdateDialogState.Error(
                    kind = transferResult.kind,
                    title = "Download Failed",
                    message = transferResult.message,
                    releaseForRetry = release
                )
                return ApkVerificationResult.Invalid(transferResult.kind, transferResult.message)
            }

            is ApkDownloadTransferResult.Downloaded -> {
                _dialogState.value = AppUpdateDialogState.Verifying(
                    release = release,
                    statusText = "Verifying APK signature and checksum…"
                )
                val installed = installedVersionProvider()
                val verification = withContext(Dispatchers.IO) {
                    verifier.verifyDownloadedApk(
                        apkFile = transferResult.file,
                        expectedSizeBytes = selected.sizeBytes,
                        expectedSha256Hex = trustedSha256,
                        fallbackInstalledPackageName = installedPackageNameProvider(),
                        fallbackInstalledVersionCode = installed.versionCode
                    )
                }

                when (verification) {
                    is ApkVerificationResult.Invalid -> {
                        transferResult.file.delete()
                        _dialogState.value = AppUpdateDialogState.Error(
                            kind = verification.kind,
                            title = "Verification Failed",
                            message = verification.message,
                            releaseForRetry = release
                        )
                    }

                    is ApkVerificationResult.Valid -> {
                        _dialogState.value = AppUpdateDialogState.ReadyToInstall(
                            release = release,
                            verifiedApk = verification.info
                        )
                    }
                }
                return verification
            }
        }
    }

    fun cancelActiveDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        _dialogState.value = AppUpdateDialogState.None
        scope.launch(Dispatchers.IO) {
            installerLauncher.clearUpdateCache()
        }
    }

    /**
     * Prepares the official Android installation intent when the user taps "Install Now".
     *
     * Returns:
     * - [InstallPreparationResult.NeedsUnknownSourcesPermission] if Android requires enabling
     *   "Install unknown apps" for SONORA first.
     * - [InstallPreparationResult.LaunchInstaller] with a verified FileProvider `content://`
     *   Intent after persisting the pre-install versionCode in DataStore.
     * - [InstallPreparationResult.Failed] if the verified APK is missing or intent creation fails.
     */
    suspend fun prepareInstallNow(
        release: GitHubReleaseInfo,
        verifiedApk: VerifiedApkInfo
    ): InstallPreparationResult {
        if (!verifiedApk.apkFile.isFile) {
            val msg = "Downloaded update file is no longer available. Please download again."
            _dialogState.value = AppUpdateDialogState.Error(
                kind = UpdateErrorKind.INSTALL_FAILED,
                title = "Installation Unavailable",
                message = msg,
                releaseForRetry = release
            )
            return InstallPreparationResult.Failed(msg)
        }

        if (!installerLauncher.canRequestPackageInstalls()) {
            awaitingUnknownSourcesFor = release to verifiedApk
            _dialogState.value = AppUpdateDialogState.PermissionRequired(
                release = release,
                verifiedApk = verifiedApk,
                rationaleMessage = "Android requires permission for SONORA to hand the verified update package to the system installer. You can turn this permission off right after updating."
            )
            return InstallPreparationResult.NeedsUnknownSourcesPermission(
                installerLauncher.buildManageUnknownAppSourcesIntent()
            )
        }

        val installIntent = installerLauncher.buildInstallApkIntent(verifiedApk.apkFile)
        if (installIntent == null) {
            val msg = "Unable to prepare Android PackageInstaller for the verified APK."
            _dialogState.value = AppUpdateDialogState.Error(
                kind = UpdateErrorKind.INSTALL_FAILED,
                title = "Installation Failed",
                message = msg,
                releaseForRetry = release
            )
            return InstallPreparationResult.Failed(msg)
        }

        val installed = installedVersionProvider()
        preferences.recordOfficialInstallInitiated(
            fromVersionCode = installed.versionCode,
            targetVersionCode = verifiedApk.versionCode,
            targetVersionName = verifiedApk.versionName.ifBlank { release.versionName },
            packageName = installedPackageNameProvider(),
            initiatedAtMs = clockMs()
        )
        awaitingUnknownSourcesFor = null
        return InstallPreparationResult.LaunchInstaller(installIntent)
    }

    /**
     * Called when the user returns from Android's `ACTION_MANAGE_UNKNOWN_APP_SOURCES` screen.
     */
    suspend fun onReturnedFromUnknownSourcesSettings(): InstallPreparationResult {
        val pending = awaitingUnknownSourcesFor ?: return InstallPreparationResult.None
        if (installerLauncher.canRequestPackageInstalls()) {
            return prepareInstallNow(pending.first, pending.second)
        }
        awaitingUnknownSourcesFor = null
        val msg = "Permission to install unknown apps was not granted. You can enable it later to install SONORA v${pending.first.versionName}."
        _dialogState.value = AppUpdateDialogState.Error(
            kind = UpdateErrorKind.PERMISSION_DENIED,
            title = "Installation Permission Needed",
            message = msg,
            releaseForRetry = pending.first,
            verifiedApkForRetry = pending.second
        )
        return InstallPreparationResult.Failed(msg)
    }

    /**
     * Called when Android's PackageInstaller returns to the still-running old version of SONORA
     * (i.e., user tapped Cancel or Android reported an installation error).
     * Clears the pending install session so the post-update reminder will NEVER falsely fire.
     */
    fun onPackageInstallerReturnedWithoutUpgrade(wasCancelled: Boolean) {
        scope.launch {
            preferences.clearPendingInstallSession()
        }
        val currentState = _dialogState.value
        val (release, verified) = when (currentState) {
            is AppUpdateDialogState.ReadyToInstall -> currentState.release to currentState.verifiedApk
            is AppUpdateDialogState.PermissionRequired -> currentState.release to currentState.verifiedApk
            else -> null to null
        }
        _dialogState.value = if (wasCancelled) {
            AppUpdateDialogState.Error(
                kind = UpdateErrorKind.INSTALL_CANCELLED,
                title = "Installation Cancelled",
                message = "Update installation was cancelled. Your music library and settings were not changed.",
                releaseForRetry = release,
                verifiedApkForRetry = verified
            )
        } else {
            AppUpdateDialogState.Error(
                kind = UpdateErrorKind.INSTALL_FAILED,
                title = "Installation Did Not Complete",
                message = "Android could not complete the update installation. Please check storage space or signing compatibility and try again.",
                releaseForRetry = release,
                verifiedApkForRetry = verified
            )
        }
    }

    fun dismissDialog() {
        awaitingUnknownSourcesFor = null
        _dialogState.value = AppUpdateDialogState.None
    }

    companion object {
        fun isDeviceNetworkConnected(context: Context): Boolean {
            return runCatching {
                val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager ?: return true
                val network = cm.activeNetwork ?: return false
                val caps = cm.getNetworkCapabilities(network) ?: return false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }.getOrDefault(true)
        }
    }
}

sealed interface InstallPreparationResult {
    data object None : InstallPreparationResult
    data class NeedsUnknownSourcesPermission(val settingsIntent: Intent?) : InstallPreparationResult
    data class LaunchInstaller(val installIntent: Intent) : InstallPreparationResult
    data class Failed(val message: String) : InstallPreparationResult
}
