package data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import domain.update.SonoraGitHubUpdateConfig
import java.io.File

/**
 * Manages isolated update cache files, FileProvider content:// URIs, Unknown App Sources
 * permission checks/settings intents, and standard Android ACTION_VIEW APK install intents.
 *
 * Security guarantees:
 * - Never exposes raw file:// URIs.
 * - Strictly confines APKs to `cacheDir/app_updates/`.
 * - Always grants temporary read-only URI permission (`FLAG_GRANT_READ_URI_PERMISSION`).
 * - Never attempts silent installation or bypasses Android PackageInstaller.
 */
class ApkInstallerLauncher(
    private val context: Context,
    private val canRequestInstallsOverride: (() -> Boolean)? = null
) {

    private val appContext = context.applicationContext

    val fileProviderAuthority: String
        get() = "${appContext.packageName}.update.fileprovider"

    fun getUpdateCacheDirectory(): File {
        val dir = File(appContext.cacheDir, SonoraGitHubUpdateConfig.UPDATE_CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Checks whether Android currently permits SONORA to request package installations.
     */
    fun canRequestPackageInstalls(): Boolean {
        canRequestInstallsOverride?.let { return it.invoke() }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { appContext.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
        } else {
            true
        }
    }

    /**
     * Builds the system Settings intent for "Install unknown apps" scoped directly to SONORA.
     * Returns null if unsupported on the device.
     */
    fun buildManageUnknownAppSourcesIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}")
        )
        return if (intent.resolveActivity(appContext.packageManager) != null) {
            intent
        } else {
            // Still return the standard intent as some OEMs resolve it without pre-querying packages
            intent
        }
    }

    /**
     * Builds a secure FileProvider `content://` URI and `ACTION_VIEW` intent for the verified APK.
     * Enforces canonical directory containment inside `cacheDir/app_updates/`.
     */
    fun buildInstallApkIntent(apkFile: File): Intent? {
        val cacheRoot = getUpdateCacheDirectory().canonicalFile
        val canonicalApk = runCatching { apkFile.canonicalFile }.getOrNull() ?: return null
        val cachePrefix = cacheRoot.path + File.separator
        if (!canonicalApk.path.startsWith(cachePrefix) || !canonicalApk.isFile) {
            return null
        }

        val contentUri = runCatching {
            FileProvider.getUriForFile(appContext, fileProviderAuthority, canonicalApk)
        }.getOrNull() ?: return null

        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, SonoraGitHubUpdateConfig.APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    /**
     * Safely opens Android's "Install unknown apps" Settings screen for SONORA.
     * Returns true if the system Settings activity could be started.
     */
    fun openManageUnknownAppSourcesSettings(): Boolean {
        val intent = buildManageUnknownAppSourcesIntent() ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            appContext.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /**
     * Removes cached temporary update APKs and partial files without touching permanent media downloads.
     */
    fun clearUpdateCache() {
        runCatching {
            val dir = File(appContext.cacheDir, SonoraGitHubUpdateConfig.UPDATE_CACHE_DIR_NAME)
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { child ->
                    if (child.isFile) {
                        child.delete()
                    }
                }
            }
        }
    }
}
