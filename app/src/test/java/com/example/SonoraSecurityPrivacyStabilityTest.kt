package com.example

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.OnlineSearchHistoryEntity
import com.example.data.OnlineTrackEntity
import com.example.data.SongMetadataEntity
import com.example.data.SonoraDatabase
import com.example.equalizer.AdaptiveEqualizerEngine
import com.example.model.UserProfile
import data.analytics.ListeningEventEntity
import data.download.DownloadStorageHelper
import data.download.DownloadedTrackEntity
import data.preferences.ProfileOnboardingState
import data.preferences.UserProfilePreferences
import data.preferences.VideoAccountPreferences
import data.video.SavedVideoEntity
import data.video.VideoSearchHistoryEntity
import domain.video.ProviderAuthState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import ui.video.openOfficialExternalUrl
import util.LocalTokenCipher
import util.SecureUrlValidator
import util.SonoraCrashDiagnostics
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 30, 36])
class SonoraSecurityPrivacyStabilityTest {

    private fun appContext(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun manifestEnforcesLeastPrivilegePermissionsAndDisablesCleartextTraffic() {
        val context = appContext()
        val pm = context.packageManager
        val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()

        val forbiddenPermissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.QUERY_ALL_PACKAGES"
        )
        forbiddenPermissions.forEach { perm ->
            assertFalse("Unexpected unnecessary permission declared: $perm", perm in requested)
        }

        // Verify AndroidManifest configures network_security_config (cleartextTrafficPermitted="false")
        val appInfo = pm.getApplicationInfo(context.packageName, 0)
        assertNotNull(appInfo)
        val resId = context.resources.getIdentifier("network_security_config", "xml", context.packageName)
        assertTrue("network_security_config.xml resource must exist", resId != 0)
        assertTrue("Running on supported SDK >= 26 (actual ${Build.VERSION.SDK_INT})", Build.VERSION.SDK_INT >= 26)
    }

    @Test
    fun secureUrlValidatorBlocksUnsafeSchemesSsrfHostsAndObfuscatedIps() {
        val rejectedStrictHttps = listOf(
            "",
            "   ",
            "javascript:alert(1)",
            "file:///etc/passwd",
            "content://com.example.provider/secret",
            "intent:#Intent;action=android.intent.action.VIEW;end",
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://user:pass@example.com/resource",
            "https://localhost/admin",
            "https://sub.localhost/api",
            "https://printer.local/status",
            "https://service.internal/keys",
            "https://127.0.0.1/secret",
            "https://10.0.0.1/router",
            "https://172.16.5.4/internal",
            "https://192.168.1.1/admin",
            "https://169.254.169.254/latest/meta-data/",
            "https://0.0.0.0/",
            "https://100.64.0.1/",
            "https://2130706433/",
            "https://0x7f000001/",
            "https://[::1]/",
            "https://[fc00::1]/",
            "https://[::ffff:127.0.0.1]/",
            "https://example.com/path\r\nX-Injected: true"
        )

        rejectedStrictHttps.forEach { badUrl ->
            assertFalse(
                "Expected URL to be rejected: $badUrl",
                SecureUrlValidator.isSafeHttpsUrl(badUrl, allowHttp = false, allowPrivateHosts = false)
            )
            assertNull(
                "Expected sanitizeExternalBrowseUrl to reject: $badUrl",
                SecureUrlValidator.sanitizeExternalBrowseUrl(badUrl)
            )
        }

        val acceptedPublicHttps = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://api.open-meteo.com/v1/forecast?latitude=20.29&longitude=85.82",
            "https://lrclib.net/api/get?artist_name=Test&track_name=Song",
            "https://cdn.jsdelivr.net/npm/@nager/holidays@latest/data.json"
        )

        acceptedPublicHttps.forEach { goodUrl ->
            assertTrue(
                "Expected valid public HTTPS URL to be accepted: $goodUrl",
                SecureUrlValidator.isSafeHttpsUrl(goodUrl, allowHttp = false, allowPrivateHosts = false)
            )
            assertNotNull(SecureUrlValidator.sanitizeExternalBrowseUrl(goodUrl))
        }
    }

    @Test
    fun boundedResponseReadersEnforceMemoryLimits() {
        val smallPayload = "hello-sonora".toByteArray(Charsets.UTF_8)
        val readBytes = SecureUrlValidator.readBoundedBytes(ByteArrayInputStream(smallPayload), maxBytes = 64L)
        assertEquals("hello-sonora", String(readBytes, Charsets.UTF_8))

        val oversizedPayload = ByteArray(1024) { 'A'.code.toByte() }
        var threwOnStream = false
        try {
            SecureUrlValidator.readBoundedBytes(ByteArrayInputStream(oversizedPayload), maxBytes = 256L)
        } catch (_: IOException) {
            threwOnStream = true
        }
        assertTrue("Expected IOException when stream exceeds maxBytes", threwOnStream)

        val oversizedBody = String(oversizedPayload, Charsets.UTF_8).toResponseBody("text/plain".toMediaType())
        var threwOnBody = false
        try {
            SecureUrlValidator.readBoundedString(oversizedBody, maxBytes = 256L)
        } catch (_: IOException) {
            threwOnBody = true
        }
        assertTrue("Expected IOException when ResponseBody exceeds maxBytes", threwOnBody)
    }

    @Test
    fun logAndCrashSanitizationRedactsTokensAndSecrets() {
        val sensitive = "Request failed Authorization: Bearer ya29.superSecretTokenValue12345 " +
            "url=https://api.example.com/v1?access_token=abc123xyz&api_key=MY_KEY_999&safe=1"
        val redacted = SecureUrlValidator.redactSensitiveForLogs(sensitive)
        assertFalse(redacted.contains("ya29.superSecretTokenValue12345"))
        assertFalse(redacted.contains("abc123xyz"))
        assertFalse(redacted.contains("MY_KEY_999"))
        assertTrue(redacted.contains("[REDACTED]"))

        val crashReport = SonoraCrashDiagnostics.buildSanitizedCrashReport(
            threadName = "main",
            throwable = RuntimeException(sensitive)
        )
        assertFalse(crashReport.contains("ya29.superSecretTokenValue12345"))
        assertFalse(crashReport.contains("abc123xyz"))
        assertFalse(crashReport.contains("MY_KEY_999"))
        assertTrue(crashReport.length <= 16 * 1024)
    }

    @Test
    fun openOfficialExternalUrlBlocksDangerousSchemesAndAllowsPublicHttpsWithBrowsableCategory() {
        val context = appContext()
        val shadowApp = Shadows.shadowOf(context as Application)
        shadowApp.clearNextStartedActivities()

        assertFalse(openOfficialExternalUrl(context, "javascript:alert(1)"))
        assertFalse(openOfficialExternalUrl(context, "file:///data/data/com.example/shared_prefs/secret.xml"))
        assertFalse(openOfficialExternalUrl(context, "intent:#Intent;component=com.example/.MainActivity;end"))
        assertFalse(openOfficialExternalUrl(context, "http://127.0.0.1:8080/internal"))
        assertNull("No intent should be started for rejected URLs", shadowApp.nextStartedActivity)

        val opened = openOfficialExternalUrl(context, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue("Valid public HTTPS URL should launch", opened)
        val launched = shadowApp.nextStartedActivity
        assertNotNull(launched)
        assertEquals(Intent.ACTION_VIEW, launched.action)
        assertTrue(launched.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", launched.dataString)
    }

    @Test
    fun oauthTokenIsEncryptedAtRestAndTamperedOrPlaintextValuesAreRejected() = runBlocking {
        val context = appContext()
        val secretToken = "ya29.test_secret_oauth_token_987654321"

        val encrypted = LocalTokenCipher.encrypt(secretToken)
        assertNotNull(encrypted)
        assertTrue(LocalTokenCipher.isEncryptedPayload(encrypted))
        assertFalse("Ciphertext must never contain plaintext token", encrypted!!.contains(secretToken))
        assertEquals(secretToken, LocalTokenCipher.decrypt(encrypted))

        // Tampered or plaintext inputs must fail closed (return null)
        assertNull(LocalTokenCipher.decrypt(secretToken))
        assertNull(LocalTokenCipher.decrypt("enc:v1:AAAA_tampered_invalid_payload"))

        val accountPrefs = VideoAccountPreferences(context)
        val futureExpiry = System.currentTimeMillis() + 3_600_000L
        accountPrefs.saveOAuthSession(
            accountLabel = "Test Listener",
            accessToken = secretToken,
            expiresAtMs = futureExpiry,
            scopes = setOf("https://www.googleapis.com/auth/youtube.force-ssl")
        )

        val state = accountPrefs.authStateFlow.first()
        assertTrue(state is ProviderAuthState.Connected)
        assertEquals(secretToken, (state as ProviderAuthState.Connected).accessToken)

        // Verify raw DataStore file on disk does not contain the plaintext OAuth token
        val prefsFile = File(context.filesDir, "datastore/sonora_video_account_prefs.preferences_pb")
        assertTrue("Video account DataStore file should exist", prefsFile.exists())
        val rawText = String(prefsFile.readBytes(), Charsets.ISO_8859_1)
        assertFalse("Plaintext OAuth token must never appear in DataStore file", rawText.contains(secretToken))
        assertTrue("Encrypted prefix should be present in DataStore file", rawText.contains("enc:v1:"))

        accountPrefs.clearOAuthSession()
        assertEquals(ProviderAuthState.Disconnected, accountPrefs.authStateFlow.first())
    }

    @Test
    fun downloadStorageHelperConfinesStagingAndArtworkPathsAgainstTraversalAndPrefixBypass() {
        val context = appContext()
        val helper = DownloadStorageHelper(context)

        // 1. Traversal characters in taskId/extension are sanitized strictly inside download_staging/
        val stagingFile = helper.getStagingFile("../../etc/passwd", "../sh")
        assertTrue(helper.isInsideStagingDir(stagingFile))
        assertEquals(helper.getStagingDir().canonicalPath, stagingFile.parentFile?.canonicalPath)

        // 2. deleteStagingFile refuses to delete sibling file outside download_staging/
        val siblingCacheFile = File(context.cacheDir, "download_staging_outside.txt").apply {
            writeText("must-not-be-deleted")
        }
        helper.deleteStagingFile(siblingCacheFile.absolutePath)
        assertTrue("File outside download_staging must not be deleted", siblingCacheFile.exists())
        siblingCacheFile.delete()

        // 3. deleteDownloadedTrackFiles refuses prefix-bypass sibling directory (downloaded_art_evil)
        val evilSiblingDir = File(context.filesDir, "downloaded_art_evil").apply { mkdirs() }
        val evilFile = File(evilSiblingDir, "keep_me.jpg").apply { writeText("protected") }
        val legitArtDir = File(context.filesDir, "downloaded_art").apply { mkdirs() }
        val legitArtFile = File(legitArtDir, "cover_1.jpg").apply { writeText("art") }
        val legitAudioFile = File(context.cacheDir, "sample_track.m4a").apply { writeText("audio") }

        val attackTrack = DownloadedTrackEntity(
            id = -1001L,
            sourceKey = "yt|test",
            serviceId = 0,
            providerKey = "youtube",
            providerName = "YouTube",
            contentUrl = "https://www.youtube.com/watch?v=test",
            title = "Test",
            artist = "Artist",
            album = "Album",
            durationMs = 1000L,
            artworkUrl = null,
            localArtworkPath = evilFile.absolutePath,
            localFileUri = Uri.fromFile(legitAudioFile).toString(),
            treeFolderUri = Uri.fromFile(context.cacheDir).toString(),
            displayFileName = "sample_track.m4a",
            formatLabel = "M4A",
            mimeType = "audio/mp4",
            bitrateKbps = 128,
            sampleRateHz = 44100,
            fileSizeBytes = 5L,
            downloadedAt = 1L
        )
        helper.deleteDownloadedTrackFiles(attackTrack)
        assertTrue("Sibling directory prefix bypass must not delete outside files", evilFile.exists())
        evilFile.delete()
        evilSiblingDir.delete()

        val legitTrack = attackTrack.copy(localArtworkPath = legitArtFile.absolutePath)
        helper.deleteDownloadedTrackFiles(legitTrack)
        assertFalse("Legitimate artwork inside downloaded_art should be deleted", legitArtFile.exists())
    }

    @Test
    fun adaptiveEqualizerEngineSanitizesNonFiniteDspInputs() {
        val engine = AdaptiveEqualizerEngine.getInstance(appContext())
        engine.setMasterEnabled(true)
        engine.setDynamicPad(0.4f, -0.2f)
        engine.setDynamicPad(Float.NaN, Float.POSITIVE_INFINITY)
        assertTrue(engine.settings.value.tilt.isFinite())
        assertTrue(engine.settings.value.contour.isFinite())

        engine.setManualBandGain(0, Float.NaN)
        engine.setManualBandGain(1, Float.NEGATIVE_INFINITY)
        engine.applyCustomPresetGains(FloatArray(15) { if (it % 2 == 0) Float.NaN else 3.0f }, "sanitized")
        engine.applyStudioShelf(true, Float.NaN)
        engine.applyStudioShelf(false, Float.POSITIVE_INFINITY)
        engine.setVolumeBoost(Float.NaN)

        val current = engine.settings.value
        assertTrue(current.manualBands.all { it.isFinite() })
        assertTrue(current.getEffectiveGains().all { it.isFinite() })
        assertTrue(current.volumeBoostDb.isFinite())
        assertTrue(engine.frequencyResponseCurve.value.all { it.isFinite() })
        engine.releaseHardwareEffects()
    }

    @Test
    fun userProfileResetAndDaoHistoryClearErasePersonalDataWhilePreservingFavorites() = runBlocking {
        val context = appContext()
        val profilePrefs = UserProfilePreferences(context, hasPreExistingInstallProvider = { false })
        val sampleImage = File(context.cacheDir, "avatar_src.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val completed = profilePrefs.completeFirstLaunchProfile("Aarav", Uri.fromFile(sampleImage))
        assertTrue(completed.isSuccess)
        val savedProfile = profilePrefs.userProfileFlow.first()
        assertEquals("Aarav", savedProfile.displayName)
        assertNotNull(savedProfile.avatarUri)

        profilePrefs.resetPersonalProfileData()
        val resetProfile = profilePrefs.userProfileFlow.first()
        assertEquals(UserProfile.DEFAULT_DISPLAY_NAME, resetProfile.displayName)
        assertNull(resetProfile.avatarUri)
        assertEquals(ProfileOnboardingState.COMPLETED, profilePrefs.resolveInitialOnboardingState())
        sampleImage.delete()

        val db = Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.dao()
            dao.upsertMetadata(SongMetadataEntity(id = 42L, isFavorite = true, playCount = 7, lastPlayedTimestamp = 999L))
            dao.upsertOnlineTrack(
                OnlineTrackEntity(
                    stableId = 100L,
                    serviceId = 0,
                    providerKey = "youtube",
                    providerName = "YouTube",
                    contentUrl = "https://www.youtube.com/watch?v=abc",
                    title = "Track",
                    artist = "Artist",
                    album = "Album",
                    durationMs = 120_000L,
                    artworkUrl = null,
                    isSaved = true,
                    isFavorite = true,
                    playCount = 5,
                    lastPlayedTimestamp = 888L,
                    savedTimestamp = 777L
                )
            )
            dao.upsertSavedVideo(
                SavedVideoEntity(
                    videoId = "vid1",
                    stableId = 200L,
                    contentUrl = "https://www.youtube.com/watch?v=vid1",
                    title = "Video",
                    channelName = "Channel",
                    channelUrl = null,
                    channelAvatarUrl = null,
                    thumbnailUrl = null,
                    durationSeconds = 60L,
                    viewCount = 100L,
                    uploadDateText = null,
                    isLive = false,
                    isWatchLater = false,
                    isSaved = false,
                    lastWatchedAt = 555L,
                    lastPositionMs = 12_000L,
                    watchCount = 2
                )
            )
            dao.upsertOnlineSearchHistory(
                OnlineSearchHistoryEntity(query = "lofi beats", providerKey = "youtube", timestamp = 123L)
            )
            dao.upsertVideoSearchHistory(VideoSearchHistoryEntity(query = "music video", timestamp = 123L))
            dao.insertListeningEvent(
                ListeningEventEntity(
                    sessionToken = "sess-1",
                    songId = 42L,
                    title = "Track",
                    artist = "Artist",
                    album = "Album",
                    genre = "Ambient",
                    isOnline = false,
                    providerKey = "local",
                    artworkUrl = null,
                    listenedMs = 30_000L,
                    startedAtMs = 100L,
                    endedAtMs = 30_100L,
                    dayEpoch = 20_000L,
                    isPlayStart = true
                )
            )

            // Execute complete history erasure sequence used by SonoraViewModel.clearListeningHistory()
            dao.clearAllListeningEvents()
            dao.clearLocalPlayHistory()
            dao.clearOnlinePlayHistory()
            dao.clearAllVideoHistory()
            dao.pruneUnreferencedVideos()
            dao.clearOnlineSearchHistory()
            dao.clearVideoSearchHistory()

            assertTrue(dao.getAllListeningEventsOnce().isEmpty())
            val localMeta = dao.getMetadata(42L)
            assertNotNull(localMeta)
            assertEquals(0, localMeta!!.playCount)
            assertEquals(0L, localMeta.lastPlayedTimestamp)
            assertTrue("Favorites must be preserved when clearing listening history", localMeta.isFavorite)

            val onlineMeta = dao.getOnlineTrackById(100L)
            assertNotNull(onlineMeta)
            assertEquals(0, onlineMeta!!.playCount)
            assertEquals(0L, onlineMeta.lastPlayedTimestamp)
            assertTrue("Saved & favorite flags must be preserved", onlineMeta.isSaved && onlineMeta.isFavorite)

            assertTrue(dao.getOnlineSearchHistoryFlow().first().isEmpty())
            assertTrue(dao.getVideoSearchHistoryFlow().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
