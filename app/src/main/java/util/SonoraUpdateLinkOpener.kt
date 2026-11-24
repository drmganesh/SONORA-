package util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

/**
 * Result of attempting to open SONORA's official Telegram updates channel.
 */
enum class UpdateLinkOpenResult {
    OPENED_IN_TELEGRAM,
    OPENED_IN_BROWSER,
    NO_HANDLER_AVAILABLE
}

/**
 * Safely opens SONORA's official Telegram update & announcement channel via Android's
 * standard ACTION_VIEW intent without requiring any additional permissions.
 *
 * Behavior:
 * 1. Prefers an installed Telegram client when available.
 * 2. Falls back to the device's default web browser when Telegram is not installed.
 * 3. Handles missing handlers / ActivityNotFoundException safely without crashing.
 */
object SonoraUpdateLinkOpener {

    private const val TAG = "SonoraUpdateLinkOpener"

    // Exact official SONORA Telegram updates channel invite URL — do not alter.
    const val SONORA_TELEGRAM_UPDATE_URL = "https://t.me/+9OhSS3Wt1oEyOTVl"

    internal val TELEGRAM_PACKAGES = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram"
    )

    fun buildTelegramIntent(packageName: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(SONORA_TELEGRAM_UPDATE_URL)).apply {
            setPackage(packageName)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }

    fun buildBrowserFallbackIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(SONORA_TELEGRAM_UPDATE_URL)).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }

    fun openTelegramUpdateChannel(
        context: Context,
        startActivityFn: (Intent) -> Unit = { intent -> context.startActivity(intent) }
    ): UpdateLinkOpenResult {
        val pm = context.packageManager
        val needsNewTaskFlag = context !is Activity

        // 1. Try installed Telegram clients first
        for (pkg in TELEGRAM_PACKAGES) {
            val telegramIntent = buildTelegramIntent(pkg).apply {
                if (needsNewTaskFlag) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val resolved = resolveIntentSafely(pm, telegramIntent)
            if (resolved) {
                val launched = runCatching {
                    startActivityFn(telegramIntent)
                    true
                }.onFailure { err ->
                    Log.w(TAG, "Failed launching Telegram package $pkg, will fall back to browser", err)
                }.getOrDefault(false)
                if (launched) {
                    return UpdateLinkOpenResult.OPENED_IN_TELEGRAM
                }
            }
        }

        // 2. Fallback to Android default browser / generic ACTION_VIEW handler
        val browserIntent = buildBrowserFallbackIntent().apply {
            if (needsNewTaskFlag) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            startActivityFn(browserIntent)
            UpdateLinkOpenResult.OPENED_IN_BROWSER
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No Telegram or browser app available to open update link", e)
            UpdateLinkOpenResult.NO_HANDLER_AVAILABLE
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException opening update link", e)
            UpdateLinkOpenResult.NO_HANDLER_AVAILABLE
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected error opening update link", t)
            UpdateLinkOpenResult.NO_HANDLER_AVAILABLE
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveIntentSafely(pm: PackageManager?, intent: Intent): Boolean {
        if (pm == null) return false
        return runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        }.getOrDefault(false)
    }
}
