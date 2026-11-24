package util

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local-only, PII-redacted crash diagnostics recorder active ONLY in debug builds.
 *
 * Guarantees:
 * - Zero network transmission and zero third-party crash/telemetry SDKs.
 * - Redacts tokens, cookies, and query secrets before writing.
 * - Bounded file size (<= 16 KB) inside app-private filesDir/debug_diagnostics/.
 * - Always chains cleanly to the previous UncaughtExceptionHandler.
 */
object SonoraCrashDiagnostics {

    private const val TAG = "SonoraCrashDiagnostics"
    private const val DIAGNOSTICS_DIR = "debug_diagnostics"
    private const val CRASH_FILE_NAME = "last_crash.txt"
    private const val MAX_STACK_FRAMES = 24
    private const val MAX_REPORT_BYTES = 16 * 1024

    @Volatile
    private var installed = false

    fun installIfDebug(context: Context) {
        if (!BuildConfig.DEBUG || installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    recordSanitizedCrashReport(appContext, thread.name, throwable)
                }.onFailure { err ->
                    Log.w(TAG, "Unable to write debug crash summary: ${err.javaClass.simpleName}")
                }
                previousHandler?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    internal fun buildSanitizedCrashReport(
        threadName: String,
        throwable: Throwable,
        timestampMs: Long = System.currentTimeMillis()
    ): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val safeMessage = SecureUrlValidator.redactSensitiveForLogs(throwable.message)
        val frames = throwable.stackTrace
            .take(MAX_STACK_FRAMES)
            .joinToString("\n") { "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})" }

        val raw = buildString {
            appendLine("SONORA Debug Crash Summary (local-only)")
            appendLine("Timestamp: ${formatter.format(Date(timestampMs))}")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Thread: ${SecureUrlValidator.redactSensitiveForLogs(threadName)}")
            appendLine("Exception: ${throwable.javaClass.name}")
            if (safeMessage.isNotBlank()) {
                appendLine("Message: $safeMessage")
            }
            if (frames.isNotBlank()) {
                appendLine("Stack:")
                appendLine(frames)
            }
        }
        return raw.take(MAX_REPORT_BYTES)
    }

    private fun recordSanitizedCrashReport(context: Context, threadName: String, throwable: Throwable) {
        val dir = File(context.filesDir, DIAGNOSTICS_DIR).apply { if (!exists()) mkdirs() }
        val file = File(dir, CRASH_FILE_NAME)
        val report = buildSanitizedCrashReport(threadName, throwable)
        file.writeText(report, Charsets.UTF_8)
    }
}
