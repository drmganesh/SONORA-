package com.example

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.PlaylistEntity
import com.example.data.SonoraDatabase
import com.example.data.SonoraPreferences
import com.example.ui.screens.SettingsScreen
import com.example.viewmodel.SonoraViewModel
import data.preferences.AppUpdatePreferences
import data.preferences.UserProfilePreferences
import data.update.ApkBinaryDownloader
import data.update.ApkDownloadTransferResult
import data.update.ApkInstallerLauncher
import data.update.ApkPackageInspector
import data.update.ApkPackageVerifier
import data.update.AppUpdateManager
import data.update.GitHubFetchResult
import data.update.GitHubReleaseTransport
import data.update.InspectedPackageIdentity
import data.update.InstallPreparationResult
import domain.update.ApkVerificationResult
import domain.update.AppUpdateDialogState
import domain.update.AssetSelectionResult
import domain.update.GitHubReleaseParser
import domain.update.GitHubReleaseUrlValidator
import domain.update.PostUpdateReminderState
import domain.update.SonoraGitHubUpdateConfig
import domain.update.UpdateCheckOutcome
import domain.update.UpdateErrorKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.settings.InstalledAppVersionInfo
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors
import ui.update.PostUpdateSecurityReminderDialog
import ui.update.SonoraAppUpdateDialogHost
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraGitHubUpdateSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var updatePrefs: AppUpdatePreferences
    private lateinit var testScope: CoroutineScope

    private val officialPackageName = "com.aistudio.sonora.musica"
    private val trustedCertDigest = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90"
    private val untrustedCertDigest = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        resetFileProviderCacheForRobolectric()
        updatePrefs = AppUpdatePreferences(app)
        updatePrefs.clearAllForTesting()
        ApkInstallerLauncher(app).clearUpdateCache()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    private fun resetFileProviderCacheForRobolectric() {
        runCatching {
            val cacheField = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
            cacheField.isAccessible = true
            (cacheField.get(null) as? MutableMap<*, *>)?.clear()
        }
    }

    @After
    fun tearDown() = runBlocking {
        testScope.cancel()
        updatePrefs.clearAllForTesting()
        ApkInstallerLauncher(app).clearUpdateCache()
    }

    private fun createValidFakeApkBytes(payloadText: String = "SONORA_APK_V6"): ByteArray {
        val temp = File.createTempFile("sonora_test_apk_", ".apk", app.cacheDir)
        try {
            ZipOutputStream(FileOutputStream(temp)).use { zos ->
                zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
                zos.write(payloadText.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("classes.dex"))
                zos.write("DEX_PAYLOAD".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            return temp.readBytes()
        } finally {
            temp.delete()
        }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val temp = File.createTempFile("sonora_sha_", ".bin", app.cacheDir)
        return try {
            temp.writeBytes(bytes)
            requireNotNull(ApkPackageVerifier.computeFileSha256Hex(temp))
        } finally {
            temp.delete()
        }
    }

    private fun releaseJson(
        tag: String = "v1.4.0",
        name: String = "SONORA v1.4.0 (Build 6)",
        body: String = "versionCode: 6\n\n- Faster playback startup\n- Cleaner equalizer controls",
        draft: Boolean = false,
        prerelease: Boolean = false,
        publishedAt: String = "2026-11-20T10:30:00Z",
        htmlUrl: String = "https://github.com/drmganesh/SONORA-/releases/tag/$tag",
        assetsJson: String
    ): String {
        val escapedBody = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
        return """
            {
              "id": 99006,
              "tag_name": "$tag",
              "name": "$name",
              "body": "$escapedBody",
              "draft": $draft,
              "prerelease": $prerelease,
              "published_at": "$publishedAt",
              "html_url": "$htmlUrl",
              "assets": [ $assetsJson ]
            }
        """.trimIndent()
    }

    private fun assetJson(
        name: String,
        size: Long,
        url: String = "https://github.com/drmganesh/SONORA-/releases/download/v1.4.0/$name",
        digest: String? = null
    ): String {
        val digestField = if (digest != null) ""","digest": "sha256:$digest"""" else ""
        return """
            {
              "id": 1001,
              "name": "$name",
              "size": $size,
              "content_type": "application/vnd.android.package-archive",
              "browser_download_url": "$url"
              $digestField
            }
        """.trimIndent()
    }

    private class FakeTransport(
        var latestResult: GitHubFetchResult,
        var assetTextByUrl: Map<String, GitHubFetchResult> = emptyMap()
    ) : GitHubReleaseTransport {
        var latestCallCount = 0
        override suspend fun fetchLatestReleaseJson(): GitHubFetchResult {
            latestCallCount++
            return latestResult
        }

        override suspend fun fetchOfficialAssetText(assetUrl: String, maxBytes: Long): GitHubFetchResult {
            return assetTextByUrl[assetUrl] ?: GitHubFetchResult.Failure(UpdateErrorKind.HTTP_ERROR, "Not found")
        }
    }

    private class FakeDownloader(
        private val bytesToWrite: ByteArray? = null,
        private val failure: ApkDownloadTransferResult.Failed? = null
    ) : ApkBinaryDownloader {
        override suspend fun downloadToFile(
            assetUrl: String,
            expectedSizeBytes: Long,
            destinationFile: File,
            onProgress: (Long, Long) -> Unit
        ): ApkDownloadTransferResult {
            if (failure != null) {
                destinationFile.delete()
                return failure
            }
            val data = bytesToWrite ?: ByteArray(0)
            destinationFile.parentFile?.mkdirs()
            onProgress(data.size / 2L, data.size.toLong())
            destinationFile.writeBytes(data)
            onProgress(data.size.toLong(), data.size.toLong())
            return ApkDownloadTransferResult.Downloaded(destinationFile, data.size.toLong())
        }
    }

    private class FakeInspector(
        var installed: InspectedPackageIdentity?,
        var archive: InspectedPackageIdentity?
    ) : ApkPackageInspector {
        override fun inspectInstalledApp(): InspectedPackageIdentity? = installed
        override fun inspectApkArchive(apkFile: File): InspectedPackageIdentity? = archive
    }

    private fun createManager(
        transport: GitHubReleaseTransport,
        downloader: ApkBinaryDownloader = FakeDownloader(),
        inspector: ApkPackageInspector = FakeInspector(
            installed = InspectedPackageIdentity(officialPackageName, 5L, "1.3.8", setOf(trustedCertDigest)),
            archive = InspectedPackageIdentity(officialPackageName, 6L, "1.4.0", setOf(trustedCertDigest))
        ),
        installedVersion: InstalledAppVersionInfo = InstalledAppVersionInfo("1.3.8", 5L),
        canRequestInstalls: () -> Boolean = { true },
        supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
        networkAvailable: Boolean = true,
        nowMs: () -> Long = { 1_760_000_000_000L }
    ): AppUpdateManager {
        return AppUpdateManager(
            context = app,
            scope = testScope,
            preferences = updatePrefs,
            transport = transport,
            binaryDownloader = downloader,
            packageInspector = inspector,
            installerLauncher = ApkInstallerLauncher(app, canRequestInstallsOverride = canRequestInstalls),
            installedVersionProvider = { installedVersion },
            installedPackageNameProvider = { officialPackageName },
            supportedAbisProvider = { supportedAbis },
            networkAvailableProvider = { networkAvailable },
            clockMs = nowMs
        )
    }

    // =========================================================================
    // SCENARIO 1: No update available (including numeric version comparison)
    // =========================================================================

    @Test
    fun scenario01_noUpdateAvailable_whenVersionCodeMatchesOrIsLower_andNeverLexicographic() = runBlocking {
        val apkBytes = createValidFakeApkBytes()
        val sameVersionJson = releaseJson(
            tag = "v1.3.8",
            body = "versionCode: 5",
            assetsJson = assetJson("SONORA-universal.apk", apkBytes.size.toLong())
        )
        val manager = createManager(transport = FakeTransport(GitHubFetchResult.Success(sameVersionJson)))

        val outcome = manager.checkForUpdatesSuspend(isManual = false)
        assertTrue(outcome is UpdateCheckOutcome.UpToDate)
        assertEquals(AppUpdateDialogState.None, manager.dialogState.value)

        // Verify numeric comparison never fails lexicographically ("1.10.0" > "1.9.0")
        val v10Release = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    tag = "v1.10.0",
                    name = "SONORA v1.10.0",
                    body = "Numeric release notes without explicit code",
                    assetsJson = assetJson("SONORA-universal.apk", 1024L)
                )
            )
        )
        assertTrue(
            GitHubReleaseParser.isReleaseNewerThanInstalled(
                release = v10Release,
                installedVersionCode = 5L,
                installedVersionName = "1.9.0"
            )
        )
        assertFalse(
            GitHubReleaseParser.isReleaseNewerThanInstalled(
                release = v10Release,
                installedVersionCode = 5L,
                installedVersionName = "1.10.0"
            )
        )
    }

    // =========================================================================
    // SCENARIO 2: New stable release available + Update Available popup + Later suppression
    // =========================================================================

    @Test
    fun scenario02_newStableReleaseAvailable_showsDialog_andRespectsLaterAndCacheInterval() = runBlocking {
        val apkBytes = createValidFakeApkBytes()
        val json = releaseJson(
            tag = "v1.4.0",
            body = "versionCode: 6\n\n- Faster playback\n- Refined audio engine",
            assetsJson = assetJson("SONORA-v1.4.0-universal.apk", apkBytes.size.toLong())
        )
        var currentTime = 1_760_000_000_000L
        val transport = FakeTransport(GitHubFetchResult.Success(json))
        val manager = createManager(transport = transport, nowMs = { currentTime })

        val outcome = manager.checkForUpdatesSuspend(isManual = false)
        assertTrue(outcome is UpdateCheckOutcome.UpdateAvailable)
        val dialogState = manager.dialogState.value as AppUpdateDialogState.Available
        assertEquals("1.4.0", dialogState.release.versionName)
        assertEquals(6L, dialogState.resolvedVersionCode)
        assertEquals("SONORA-v1.4.0-universal.apk", dialogState.selectedAsset?.name)

        // Verify Material 3 Update Available dialog renders all required fields
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                SonoraAppUpdateDialogHost(
                    dialogState = dialogState,
                    colors = EditorialColors.default(),
                    onDownloadUpdate = {},
                    onLater = { manager.deferUpdateLater() },
                    onCancelDownload = {},
                    onInstallNow = { _, _ -> },
                    onOpenUnknownSourcesSettings = {},
                    onDismiss = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("update_available_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("Update Available!").assertIsDisplayed()
        composeRule.onNodeWithText("v1.4.0").assertIsDisplayed()
        composeRule.onNodeWithText("v1.3.8", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("update_download_button").assertIsDisplayed()
        composeRule.onNodeWithTag("update_later_button").assertIsDisplayed()

        // Tap Later -> dialog closes and persists dismissal
        composeRule.onNodeWithTag("update_later_button").performClick()
        composeRule.waitForIdle()
        assertEquals(AppUpdateDialogState.None, manager.dialogState.value)

        // Subsequent automatic check within interval reuses cache (no extra network call) and stays suppressed
        currentTime += 60_000L
        val cachedOutcome = manager.checkForUpdatesSuspend(isManual = false)
        assertTrue(cachedOutcome is UpdateCheckOutcome.UpdateAvailable && cachedOutcome.fromCache)
        assertEquals(1, transport.latestCallCount)
        assertEquals(AppUpdateDialogState.None, manager.dialogState.value)

        // Manual check bypasses interval and Later dismissal
        manager.checkForUpdatesSuspend(isManual = true)
        assertEquals(2, transport.latestCallCount)
        assertTrue(manager.dialogState.value is AppUpdateDialogState.Available)
    }

    // =========================================================================
    // SCENARIOS 3 & 4: Draft release ignored & Prerelease ignored
    // =========================================================================

    @Test
    fun scenario03_and_04_draftAndPrerelease_areIgnoredByDefault() = runBlocking {
        val draftJson = releaseJson(
            tag = "v1.5.0",
            body = "versionCode: 7",
            draft = true,
            assetsJson = assetJson("SONORA-universal.apk", 2048L)
        )
        val draftManager = createManager(transport = FakeTransport(GitHubFetchResult.Success(draftJson)))
        val draftOutcome = draftManager.checkForUpdatesSuspend(isManual = false)
        assertTrue(draftOutcome is UpdateCheckOutcome.UpToDate)
        assertEquals(AppUpdateDialogState.None, draftManager.dialogState.value)

        val prereleaseJson = releaseJson(
            tag = "v1.5.0-beta.1",
            body = "versionCode: 8",
            prerelease = true,
            assetsJson = assetJson("SONORA-universal.apk", 2048L)
        )
        val preManager = createManager(transport = FakeTransport(GitHubFetchResult.Success(prereleaseJson)))
        val preOutcome = preManager.checkForUpdatesSuspend(isManual = true)
        assertTrue(preOutcome is UpdateCheckOutcome.UpToDate)
        assertEquals(AppUpdateDialogState.None, preManager.dialogState.value)
    }

    // =========================================================================
    // SCENARIOS 5 & 6: GitHub API unavailable, rate limit, untrusted repo & offline mode
    // =========================================================================

    @Test
    fun scenario05_and_06_gitHubApiUnavailable_untrustedRepo_andOfflineHandledGracefully() = runBlocking {
        // GitHub API unavailable / rate limit
        val apiErrorManager = createManager(
            transport = FakeTransport(
                GitHubFetchResult.Failure(UpdateErrorKind.RATE_LIMITED, "GitHub API rate limit reached.")
            )
        )
        val rateLimitOutcome = apiErrorManager.checkForUpdatesSuspend(isManual = true)
        assertTrue(rateLimitOutcome is UpdateCheckOutcome.Error && rateLimitOutcome.kind == UpdateErrorKind.RATE_LIMITED)
        assertTrue(apiErrorManager.dialogState.value is AppUpdateDialogState.Error)

        // Untrusted repository URL rejected strictly
        assertFalse(
            GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(
                "https://github.com/otheruser/SONORA-/releases/download/v1.4.0/app.apk"
            )
        )
        assertFalse(
            GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(
                "http://github.com/drmganesh/SONORA-/releases/download/v1.4.0/app.apk"
            )
        )
        assertTrue(
            GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(
                "https://github.com/drmganesh/SONORA-/releases/download/v1.4.0/SONORA-universal.apk"
            )
        )

        // Offline mode
        val offlineManager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            networkAvailable = false
        )
        val offlineOutcome = offlineManager.checkForUpdatesSuspend(isManual = true)
        assertTrue(offlineOutcome is UpdateCheckOutcome.Error && offlineOutcome.kind == UpdateErrorKind.OFFLINE)
    }

    // =========================================================================
    // SCENARIO 7: APK download interrupted
    // =========================================================================

    @Test
    fun scenario07_apkDownloadInterrupted_reportsFailureAndCleansUpCache() = runBlocking {
        val release = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = assetJson("SONORA-universal.apk", 4096L)
                )
            )
        )
        val manager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(
                failure = ApkDownloadTransferResult.Failed(
                    UpdateErrorKind.DOWNLOAD_FAILED,
                    "APK download was interrupted."
                )
            )
        )

        val result = manager.downloadAndVerifyReleaseSuspend(release)
        assertTrue(result is ApkVerificationResult.Invalid && result.kind == UpdateErrorKind.DOWNLOAD_FAILED)
        val errorState = manager.dialogState.value as AppUpdateDialogState.Error
        assertEquals(UpdateErrorKind.DOWNLOAD_FAILED, errorState.kind)
        assertTrue(manager.installerLauncher.getUpdateCacheDirectory().listFiles().orEmpty().isEmpty())
    }

    // =========================================================================
    // SCENARIO 8: Incorrect checksum rejected
    // =========================================================================

    @Test
    fun scenario08_incorrectChecksum_rejectsAndDeletesApk() = runBlocking {
        val apkBytes = createValidFakeApkBytes("GOOD_APK")
        val wrongSha = "0000000000000000000000000000000000000000000000000000000000000000"
        val release = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = assetJson("SONORA-universal.apk", apkBytes.size.toLong(), digest = wrongSha)
                )
            )
        )
        val manager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(bytesToWrite = apkBytes)
        )

        val result = manager.downloadAndVerifyReleaseSuspend(release)
        assertTrue(result is ApkVerificationResult.Invalid && result.kind == UpdateErrorKind.CHECKSUM_MISMATCH)
        assertTrue(manager.installerLauncher.getUpdateCacheDirectory().listFiles().orEmpty().isEmpty())
    }

    // =========================================================================
    // SCENARIO 9: Invalid / malformed APK rejected
    // =========================================================================

    @Test
    fun scenario09_invalidApk_rejectsMalformedBinaryAndDeletesFile() = runBlocking {
        val corruptBytes = "NOT_A_VALID_ZIP_OR_APK".toByteArray(Charsets.UTF_8)
        val release = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = assetJson("SONORA-universal.apk", corruptBytes.size.toLong())
                )
            )
        )
        val manager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(bytesToWrite = corruptBytes)
        )

        val result = manager.downloadAndVerifyReleaseSuspend(release)
        assertTrue(result is ApkVerificationResult.Invalid && result.kind == UpdateErrorKind.INVALID_APK)
        assertTrue(manager.installerLauncher.getUpdateCacheDirectory().listFiles().orEmpty().isEmpty())
    }

    // =========================================================================
    // SCENARIO 10: Incompatible ABI + universal preference + source ZIP rejection
    // =========================================================================

    @Test
    fun scenario10_incompatibleAbiAndSourceArchiveSelectionRules() {
        val x86OnlyRelease = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = listOf(
                        assetJson("SONORA-source-code.zip", 50000L),
                        assetJson("SONORA-v1.4.0-x86_64.apk", 25_000_000L)
                    ).joinToString(",")
                )
            )
        )
        val armSelection = GitHubReleaseParser.selectCompatibleApkAsset(
            x86OnlyRelease,
            listOf("arm64-v8a", "armeabi-v7a")
        )
        assertTrue(armSelection is AssetSelectionResult.NoCompatibleApk)
        assertEquals(
            "No compatible APK is available for this release.",
            (armSelection as AssetSelectionResult.NoCompatibleApk).message
        )

        // When universal AND arm64 exist, universal is preferred
        val mixedRelease = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = listOf(
                        assetJson("SONORA-v1.4.0-arm64-v8a.apk", 20_000_000L),
                        assetJson("SONORA-v1.4.0-universal.apk", 28_000_000L)
                    ).joinToString(",")
                )
            )
        )
        val preferredUniversal = GitHubReleaseParser.selectCompatibleApkAsset(
            mixedRelease,
            listOf("arm64-v8a")
        ) as AssetSelectionResult.Selected
        assertEquals("SONORA-v1.4.0-universal.apk", preferredUniversal.asset.name)
        assertTrue(preferredUniversal.isUniversal)

        // When no universal exists, picks matching device ABI
        val splitRelease = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = listOf(
                        assetJson("SONORA-v1.4.0-armeabi-v7a.apk", 18_000_000L),
                        assetJson("SONORA-v1.4.0-arm64-v8a.apk", 20_000_000L)
                    ).joinToString(",")
                )
            )
        )
        val matchedArm64 = GitHubReleaseParser.selectCompatibleApkAsset(
            splitRelease,
            listOf("arm64-v8a", "armeabi-v7a")
        ) as AssetSelectionResult.Selected
        assertEquals("SONORA-v1.4.0-arm64-v8a.apk", matchedArm64.asset.name)
        assertFalse(matchedArm64.isUniversal)
    }

    // =========================================================================
    // SCENARIO 11: Different signing certificate (and package mismatch / downgrade)
    // =========================================================================

    @Test
    fun scenario11_differentSigningCertificate_packageMismatch_andDowngradeRejected() = runBlocking {
        val apkBytes = createValidFakeApkBytes("MISMATCH_CERT_APK")
        val sha = computeSha256(apkBytes)
        val release = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = assetJson("SONORA-universal.apk", apkBytes.size.toLong(), digest = sha)
                )
            )
        )

        val mismatchedCertManager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(bytesToWrite = apkBytes),
            inspector = FakeInspector(
                installed = InspectedPackageIdentity(officialPackageName, 5L, "1.3.8", setOf(trustedCertDigest)),
                archive = InspectedPackageIdentity(officialPackageName, 6L, "1.4.0", setOf(untrustedCertDigest))
            )
        )
        val certResult = mismatchedCertManager.downloadAndVerifyReleaseSuspend(release)
        assertTrue(certResult is ApkVerificationResult.Invalid && certResult.kind == UpdateErrorKind.SIGNATURE_MISMATCH)

        val wrongPkgManager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(bytesToWrite = apkBytes),
            inspector = FakeInspector(
                installed = InspectedPackageIdentity(officialPackageName, 5L, "1.3.8", setOf(trustedCertDigest)),
                archive = InspectedPackageIdentity("com.evil.impostor", 6L, "1.4.0", setOf(trustedCertDigest))
            )
        )
        val pkgResult = wrongPkgManager.downloadAndVerifyReleaseSuspend(release)
        assertTrue(pkgResult is ApkVerificationResult.Invalid && pkgResult.kind == UpdateErrorKind.PACKAGE_MISMATCH)

        val downgradeManager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(bytesToWrite = apkBytes),
            inspector = FakeInspector(
                installed = InspectedPackageIdentity(officialPackageName, 5L, "1.3.8", setOf(trustedCertDigest)),
                archive = InspectedPackageIdentity(officialPackageName, 4L, "1.3.7", setOf(trustedCertDigest))
            )
        )
        val downResult = downgradeManager.downloadAndVerifyReleaseSuspend(release)
        assertTrue(downResult is ApkVerificationResult.Invalid && downResult.kind == UpdateErrorKind.DOWNGRADE_REJECTED)
    }

    // =========================================================================
    // SCENARIO 12: User denies unknown-app installation permission, then grants on return
    // =========================================================================

    @Test
    fun scenario12_unknownAppSourcesPermission_deniedThenGrantedFlow() = runBlocking {
        val apkBytes = createValidFakeApkBytes("VALID_SIGNED_APK_V6")
        val sha = computeSha256(apkBytes)
        val release = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = assetJson("SONORA-universal.apk", apkBytes.size.toLong(), digest = sha)
                )
            )
        )
        var permissionGranted = false
        val manager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(bytesToWrite = apkBytes),
            canRequestInstalls = { permissionGranted }
        )

        val verified = (manager.downloadAndVerifyReleaseSuspend(release) as ApkVerificationResult.Valid).info

        // Step 1: Permission missing -> shows PermissionRequired rationale + ACTION_MANAGE_UNKNOWN_APP_SOURCES intent
        val prep1 = manager.prepareInstallNow(release, verified)
        assertTrue(prep1 is InstallPreparationResult.NeedsUnknownSourcesPermission)
        val settingsIntent = (prep1 as InstallPreparationResult.NeedsUnknownSourcesPermission).settingsIntent
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, settingsIntent?.action)
        assertEquals("package:$officialPackageName", settingsIntent?.data?.toString())
        assertTrue(manager.dialogState.value is AppUpdateDialogState.PermissionRequired)

        // Step 2: User returns still denying permission -> graceful error, no bypass
        val returnDenied = manager.onReturnedFromUnknownSourcesSettings()
        assertTrue(returnDenied is InstallPreparationResult.Failed)
        val err = manager.dialogState.value as AppUpdateDialogState.Error
        assertEquals(UpdateErrorKind.PERMISSION_DENIED, err.kind)

        // Step 3: User retries, enables permission in Settings, returns -> launches FileProvider installer
        manager.prepareInstallNow(release, verified)
        permissionGranted = true
        val returnGranted = manager.onReturnedFromUnknownSourcesSettings()
        assertTrue(returnGranted is InstallPreparationResult.LaunchInstaller)
        val installIntent = (returnGranted as InstallPreparationResult.LaunchInstaller).installIntent
        assertEquals(Intent.ACTION_VIEW, installIntent.action)
        assertEquals(SonoraGitHubUpdateConfig.APK_MIME_TYPE, installIntent.type)
        assertEquals("content", installIntent.data?.scheme)
        assertTrue((installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    // =========================================================================
    // SCENARIO 13: User cancels installation -> pending session cleared, no reminder shown
    // =========================================================================

    @Test
    fun scenario13_userCancelsInstallation_clearsPendingSessionAndNeverShowsReminder() = runBlocking {
        val apkBytes = createValidFakeApkBytes("CANCEL_TEST_APK")
        val sha = computeSha256(apkBytes)
        val release = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    body = "versionCode: 6",
                    assetsJson = assetJson("SONORA-universal.apk", apkBytes.size.toLong(), digest = sha)
                )
            )
        )
        val manager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(bytesToWrite = apkBytes),
            canRequestInstalls = { true }
        )

        val verified = (manager.downloadAndVerifyReleaseSuspend(release) as ApkVerificationResult.Valid).info
        val prep = manager.prepareInstallNow(release, verified)
        assertTrue(prep is InstallPreparationResult.LaunchInstaller)
        assertEquals(5L, updatePrefs.getSnapshot().pendingInstallFromVersionCode)

        // Android installer returns RESULT_CANCELED
        manager.onPackageInstallerReturnedWithoutUpgrade(wasCancelled = true)
        val clearedSnapshot = withTimeout(3_000L) {
            updatePrefs.stateFlow.first { it.pendingInstallFromVersionCode == 0L }
        }
        assertEquals(0L, clearedSnapshot.pendingInstallFromVersionCode)
        assertEquals(
            UpdateErrorKind.INSTALL_CANCELLED,
            (manager.dialogState.value as AppUpdateDialogState.Error).kind
        )

        // Next launch on same versionCode -> NO reminder
        val nextLaunchManager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            installedVersion = InstalledAppVersionInfo("1.3.8", 5L)
        )
        assertNull(nextLaunchManager.evaluatePostUpdateReminderOnLaunch())
    }

    // =========================================================================
    // SCENARIOS 14, 15, 16, 17, 18:
    // Successful install -> First launch reminder -> Tap anywhere dismissal ->
    // No repeat on same version -> Reappears after later update
    // =========================================================================

    @Test
    fun scenarios14_to_18_fullPostUpdateLifecycle_detectionDismissalNoRepeatAndLaterUpdate() = runBlocking {
        val apkBytes = createValidFakeApkBytes("VERIFIED_INSTALL_V6")
        val sha = computeSha256(apkBytes)
        val releaseV6 = requireNotNull(
            GitHubReleaseParser.parseReleaseJson(
                releaseJson(
                    tag = "v1.4.0",
                    body = "versionCode: 6",
                    assetsJson = assetJson("SONORA-universal.apk", apkBytes.size.toLong(), digest = sha)
                )
            )
        )

        // Scenario 14: Download, verify, ReadyToInstall prompt, and official install launch
        val v5Manager = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            downloader = FakeDownloader(bytesToWrite = apkBytes),
            installedVersion = InstalledAppVersionInfo("1.3.8", 5L),
            canRequestInstalls = { true }
        )
        val valid = v5Manager.downloadAndVerifyReleaseSuspend(releaseV6) as ApkVerificationResult.Valid
        assertTrue(valid.info.checksumVerifiedAgainstTrustedSource)
        assertTrue(v5Manager.dialogState.value is AppUpdateDialogState.ReadyToInstall)

        val prep = v5Manager.prepareInstallNow(releaseV6, valid.info)
        assertTrue(prep is InstallPreparationResult.LaunchInstaller)
        val persistedAfterInstallStart = updatePrefs.getSnapshot()
        assertEquals(5L, persistedAfterInstallStart.pendingInstallFromVersionCode)
        assertEquals(6L, persistedAfterInstallStart.pendingInstallTargetVersionCode)
        assertEquals(officialPackageName, persistedAfterInstallStart.pendingInstallPackageName)

        // Failed install / normal restart on same versionCode=5 -> NO reminder
        val unchangedRestart = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            installedVersion = InstalledAppVersionInfo("1.3.8", 5L)
        )
        assertNull(unchangedRestart.evaluatePostUpdateReminderOnLaunch())

        // Re-record official install 5 -> 6 to test downgrade rejection (versionCode 4)
        updatePrefs.recordOfficialInstallInitiated(5L, 6L, "1.4.0", officialPackageName, 1000L)
        val downgradeLaunch = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            installedVersion = InstalledAppVersionInfo("1.3.7", 4L)
        )
        assertNull(downgradeLaunch.evaluatePostUpdateReminderOnLaunch())

        // Scenario 15: First launch after genuine successful update (5 -> 6)
        updatePrefs.recordOfficialInstallInitiated(5L, 6L, "1.4.0", officialPackageName, 1000L)
        val v6FirstLaunch = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            installedVersion = InstalledAppVersionInfo("1.4.0", 6L),
            canRequestInstalls = { true }
        )
        val reminderV6 = v6FirstLaunch.evaluatePostUpdateReminderOnLaunch()
        assertNotNull(reminderV6)
        assertEquals(6L, reminderV6?.installedVersionCode)
        assertTrue(reminderV6?.isUnknownSourcesPermissionEnabled == true)
        assertTrue(v6FirstLaunch.installerLauncher.getUpdateCacheDirectory().listFiles().orEmpty().isEmpty())

        // Scenario 16: Verify exact title/message, Open Settings button, and tap-anywhere dismissal
        var currentReminder by mutableStateOf<PostUpdateReminderState?>(reminderV6)
        var openSettingsClicks = 0
        composeRule.setContent {
            CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                currentReminder?.let { state ->
                    PostUpdateSecurityReminderDialog(
                        state = state,
                        colors = EditorialColors.default(),
                        onOpenSettings = { openSettingsClicks++ },
                        onDismiss = {
                            v6FirstLaunch.dismissPostUpdateReminder()
                            currentReminder = null
                        }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("post_update_security_reminder").assertIsDisplayed()
        composeRule.onNodeWithText("Installation Complete! 🎉").assertIsDisplayed()
        composeRule.onNodeWithText("Install unknown apps", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("post_update_open_settings_button").performClick()
        assertEquals(1, openSettingsClicks)

        // Tap anywhere on the notice card to dismiss (no confirmation button required)
        composeRule.onNodeWithTag("post_update_security_reminder").performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("post_update_security_reminder").fetchSemanticsNodes().isEmpty())
        val dismissedV6 = withTimeout(3_000L) {
            updatePrefs.stateFlow.first { it.lastReminderDismissedVersionCode == 6L }
        }
        assertEquals(6L, dismissedV6.lastReminderDismissedVersionCode)

        // Scenario 17: Subsequent launch on same versionCode=6 must NOT repeat the reminder
        val v6SecondLaunch = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            installedVersion = InstalledAppVersionInfo("1.4.0", 6L),
            canRequestInstalls = { true }
        )
        assertNull(v6SecondLaunch.evaluatePostUpdateReminderOnLaunch())

        // Scenario 18: Later successful update (6 -> 7) shows the reminder again once
        updatePrefs.recordOfficialInstallInitiated(6L, 7L, "1.5.0", officialPackageName, 2000L)
        val v7FirstLaunch = createManager(
            transport = FakeTransport(GitHubFetchResult.Success("{}")),
            installedVersion = InstalledAppVersionInfo("1.5.0", 7L),
            canRequestInstalls = { false } // Already turned off variant
        )
        val reminderV7 = v7FirstLaunch.evaluatePostUpdateReminderOnLaunch()
        assertNotNull(reminderV7)
        assertEquals(7L, reminderV7?.installedVersionCode)
        assertFalse(reminderV7?.isUnknownSourcesPermissionEnabled == true)

        v7FirstLaunch.dismissPostUpdateReminder()
        val dismissedV7 = withTimeout(3_000L) {
            updatePrefs.stateFlow.first { it.lastReminderDismissedVersionCode == 7L }
        }
        assertEquals(7L, dismissedV7.lastReminderDismissedVersionCode)
    }

    // =========================================================================
    // SCENARIO 19: Existing profile, library, playlists, settings, downloaded files intact
    //              + Settings -> About -> App Updates UI verified (zero Telegram)
    // =========================================================================

    @Test
    fun scenario19_userDataAndPermanentDownloadsRemainIntact_andSettingsAboutUpdatesWorks() = runBlocking {
        // Seed user profile, preferences, Room playlist, and a permanent downloaded music file
        val profilePrefs = UserProfilePreferences(app)
        val sonoraPrefs = SonoraPreferences(app)
        val db = SonoraDatabase.getDatabase(app)
        val permanentMusicDir = File(app.filesDir, "permanent_music_downloads").apply { mkdirs() }
        val permanentTrack = File(permanentMusicDir, "raaga_yaman.flac")
        var playlistId: Long? = null

        try {
            profilePrefs.completeFirstLaunchProfile(rawDisplayName = "Dr. Ganeswar", pickedAvatarUri = null)
            sonoraPrefs.showLockScreenControls = true
            playlistId = db.dao().insertPlaylist(PlaylistEntity(name = "Evening Ragas"))
            permanentTrack.writeText("PERMANENT_LOSSLESS_AUDIO_DATA")

            // Run update download + cache cleanup
            val apkBytes = createValidFakeApkBytes("V6_ISOLATION_CHECK")
            val sha = computeSha256(apkBytes)
            val release = requireNotNull(
                GitHubReleaseParser.parseReleaseJson(
                    releaseJson(
                        body = "versionCode: 6",
                        assetsJson = assetJson("SONORA-universal.apk", apkBytes.size.toLong(), digest = sha)
                    )
                )
            )
            val manager = createManager(
                transport = FakeTransport(GitHubFetchResult.Success("{}")),
                downloader = FakeDownloader(bytesToWrite = apkBytes)
            )
            val valid = manager.downloadAndVerifyReleaseSuspend(release) as ApkVerificationResult.Valid
            assertTrue(valid.info.apkFile.canonicalPath.startsWith(File(app.cacheDir, "app_updates").canonicalPath))
            manager.installerLauncher.clearUpdateCache()

            // Confirm profile, settings, Room playlist, and permanent music file are completely intact
            assertEquals("Dr. Ganeswar", profilePrefs.userProfileFlow.first().displayName)
            assertTrue(sonoraPrefs.showLockScreenControls)
            assertTrue(db.dao().getAllPlaylistsFlow().first().any { it.id == playlistId && it.name == "Evening Ragas" })
            assertTrue(permanentTrack.exists())
            assertEquals("PERMANENT_LOSSLESS_AUDIO_DATA", permanentTrack.readText())

            // Verify Settings -> About -> App Updates card is rendered with zero Telegram mentions
            val vm = SonoraViewModel(app)
            composeRule.setContent {
                CompositionLocalProvider(LocalEditorialColors provides EditorialColors.default()) {
                    SettingsScreen(viewModel = vm)
                }
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("settings_lazy_column")
                .performScrollToNode(hasTestTag("settings_about_app_updates_card"))
            composeRule.onNodeWithTag("settings_about_app_updates_card").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag("settings_about_check_updates_button").assertIsDisplayed()
            assertTrue(composeRule.onAllNodesWithTag("settings_check_for_updates_row").fetchSemanticsNodes().isEmpty())
        } finally {
            permanentTrack.delete()
            playlistId?.let { runCatching { db.dao().deletePlaylist(it) } }
            clearUserProfileDataStoreForIsolation(app)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun clearUserProfileDataStoreForIsolation(context: Context) {
        runCatching {
            val clazz = Class.forName("data.preferences.UserProfilePreferencesKt")
            val method = clazz.getDeclaredMethod("getUserProfileDataStore", Context::class.java).apply {
                isAccessible = true
            }
            val dataStore = method.invoke(null, context) as DataStore<Preferences>
            dataStore.edit { it.clear() }
        }
    }
}
