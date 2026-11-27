package data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import domain.update.ApkVerificationResult
import domain.update.GitHubReleaseParser
import domain.update.UpdateErrorKind
import domain.update.VerifiedApkInfo
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipFile

data class InspectedPackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val signingCertSha256Digests: Set<String>
)

/**
 * Abstraction over Android PackageManager archive & installed package inspection so every
 * security check (corrupt APK, wrong package, downgrade, mismatched signing cert) can be
 * verified deterministically in JVM/Robolectric tests as well as on real devices.
 */
interface ApkPackageInspector {
    fun inspectInstalledApp(): InspectedPackageIdentity?
    fun inspectApkArchive(apkFile: File): InspectedPackageIdentity?
}

class AndroidApkPackageInspector(private val context: Context) : ApkPackageInspector {

    private val appContext = context.applicationContext

    override fun inspectInstalledApp(): InspectedPackageIdentity? {
        return runCatching {
            val pm = appContext.packageManager
            val pkgInfo = getPackageInfoWithSignatures(pm, appContext.packageName) ?: return null
            toIdentity(pkgInfo)
        }.getOrNull()
    }

    override fun inspectApkArchive(apkFile: File): InspectedPackageIdentity? {
        if (!apkFile.isFile || !apkFile.canRead()) return null
        return runCatching {
            val pm = appContext.packageManager
            val pkgInfo = getArchiveInfoWithSignatures(pm, apkFile.absolutePath) ?: return null
            toIdentity(pkgInfo)
        }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfoWithSignatures(pm: PackageManager, packageName: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
        }
    }

    @Suppress("DEPRECATION")
    private fun getArchiveInfoWithSignatures(pm: PackageManager, archivePath: String): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(
                archivePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageArchiveInfo(archivePath, PackageManager.GET_SIGNATURES)
        }
    }

    private fun toIdentity(pkgInfo: PackageInfo): InspectedPackageIdentity {
        val digests = extractSigningCertificateSha256Digests(pkgInfo)
        return InspectedPackageIdentity(
            packageName = pkgInfo.packageName.orEmpty(),
            versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
            versionName = pkgInfo.versionName.orEmpty(),
            signingCertSha256Digests = digests
        )
    }

    @Suppress("DEPRECATION")
    private fun extractSigningCertificateSha256Digests(pkgInfo: PackageInfo): Set<String> {
        val result = linkedSetOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo
            if (signingInfo != null) {
                signingInfo.apkContentsSigners?.forEach { sig ->
                    result.add(sha256Hex(sig.toByteArray()))
                }
                signingInfo.signingCertificateHistory?.forEach { sig ->
                    result.add(sha256Hex(sig.toByteArray()))
                }
            }
        }
        if (result.isEmpty()) {
            pkgInfo.signatures?.forEach { sig ->
                result.add(sha256Hex(sig.toByteArray()))
            }
        }
        return result
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Verifies every downloaded APK before SONORA ever hands it to Android's PackageInstaller:
 * 1. Non-empty file matching expected size (when provided)
 * 2. Valid ZIP/APK container containing AndroidManifest.xml
 * 3. Constant-time SHA-256 checksum verification when a trusted checksum is available
 * 4. Package name strictly matches installed SONORA package
 * 5. Archive versionCode strictly exceeds installed versionCode (prevents downgrade/reinstall)
 * 6. Signing certificate matches installed SONORA signing certificate
 */
class ApkPackageVerifier(
    private val inspector: ApkPackageInspector
) {

    fun verifyDownloadedApk(
        apkFile: File,
        expectedSizeBytes: Long,
        expectedSha256Hex: String?,
        fallbackInstalledPackageName: String,
        fallbackInstalledVersionCode: Long
    ): ApkVerificationResult {
        if (!apkFile.isFile || apkFile.length() <= 0L) {
            return ApkVerificationResult.Invalid(
                UpdateErrorKind.INVALID_APK,
                "Downloaded update file is empty or missing."
            )
        }

        val actualSize = apkFile.length()
        if (expectedSizeBytes > 0L && actualSize != expectedSizeBytes) {
            return ApkVerificationResult.Invalid(
                UpdateErrorKind.DOWNLOAD_FAILED,
                "Downloaded APK size did not match the release file size."
            )
        }

        // Verify valid APK ZIP structure containing AndroidManifest.xml
        if (!hasValidApkZipStructure(apkFile)) {
            return ApkVerificationResult.Invalid(
                UpdateErrorKind.INVALID_APK,
                "Downloaded file is not a valid Android APK package."
            )
        }

        // Compute SHA-256 digest and compare constant-time when trusted checksum is available
        val actualSha256 = computeFileSha256Hex(apkFile)
            ?: return ApkVerificationResult.Invalid(
                UpdateErrorKind.INVALID_APK,
                "Unable to compute APK SHA-256 checksum."
            )

        val normalizedExpectedSha256 = expectedSha256Hex?.trim()?.lowercase(Locale.ROOT)
            ?.takeIf { GitHubReleaseParser.isValidSha256Hex(it) }

        if (normalizedExpectedSha256 != null) {
            val expectedBytes = normalizedExpectedSha256.toByteArray(Charsets.UTF_8)
            val actualBytes = actualSha256.toByteArray(Charsets.UTF_8)
            if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
                return ApkVerificationResult.Invalid(
                    UpdateErrorKind.CHECKSUM_MISMATCH,
                    "APK checksum verification failed. The download was rejected for your safety."
                )
            }
        }

        // Inspect Android package metadata & signing certificates
        val archiveIdentity = inspector.inspectApkArchive(apkFile)
            ?: return ApkVerificationResult.Invalid(
                UpdateErrorKind.INVALID_APK,
                "Android could not parse the downloaded APK package."
            )

        val installedIdentity = inspector.inspectInstalledApp()
        val expectedPackageName = installedIdentity?.packageName?.takeIf { it.isNotBlank() }
            ?: fallbackInstalledPackageName
        val installedVersionCode = installedIdentity?.versionCode?.takeIf { it > 0L }
            ?: fallbackInstalledVersionCode

        if (archiveIdentity.packageName != expectedPackageName) {
            return ApkVerificationResult.Invalid(
                UpdateErrorKind.PACKAGE_MISMATCH,
                "Update package ID does not match ${domain.branding.AppBranding.APP_NAME}. Installation blocked."
            )
        }

        if (archiveIdentity.versionCode <= installedVersionCode) {
            return ApkVerificationResult.Invalid(
                UpdateErrorKind.DOWNGRADE_REJECTED,
                "This APK is not newer than your installed version of ${domain.branding.AppBranding.APP_NAME}."
            )
        }

        // Verify signing certificate compatibility
        val installedCerts = installedIdentity?.signingCertSha256Digests.orEmpty()
        val archiveCerts = archiveIdentity.signingCertSha256Digests
        if (archiveCerts.isEmpty()) {
            return ApkVerificationResult.Invalid(
                UpdateErrorKind.SIGNATURE_MISMATCH,
                "Update APK is unsigned or its signing certificate could not be verified."
            )
        }
        if (installedCerts.isNotEmpty() && installedCerts.intersect(archiveCerts).isEmpty()) {
            return ApkVerificationResult.Invalid(
                UpdateErrorKind.SIGNATURE_MISMATCH,
                "Update signing certificate does not match the installed ${domain.branding.AppBranding.APP_NAME} app."
            )
        }

        return ApkVerificationResult.Valid(
            VerifiedApkInfo(
                apkFile = apkFile,
                packageName = archiveIdentity.packageName,
                versionCode = archiveIdentity.versionCode,
                versionName = archiveIdentity.versionName,
                fileSizeBytes = actualSize,
                sha256Hex = actualSha256,
                checksumVerifiedAgainstTrustedSource = normalizedExpectedSha256 != null
            )
        )
    }

    private fun hasValidApkZipStructure(file: File): Boolean {
        return runCatching {
            // Check ZIP local file header magic (PK\x03\x04)
            FileInputStream(file).use { input ->
                val header = ByteArray(4)
                if (input.read(header) != 4) return false
                if (header[0] != 0x50.toByte() ||
                    header[1] != 0x4B.toByte() ||
                    header[2] != 0x03.toByte() ||
                    header[3] != 0x04.toByte()
                ) {
                    return false
                }
            }
            ZipFile(file).use { zip ->
                zip.getEntry("AndroidManifest.xml") != null
            }
        }.getOrDefault(false)
    }

    companion object {
        fun computeFileSha256Hex(file: File): String? {
            return runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(16 * 1024)
                FileInputStream(file).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        digest.update(buffer, 0, read)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }
    }
}
