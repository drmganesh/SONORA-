package data.update

import domain.update.GitHubReleaseUrlValidator
import domain.update.SonoraGitHubUpdateConfig
import domain.update.UpdateErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import util.SecureUrlValidator
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed interface ApkDownloadTransferResult {
    data class Downloaded(
        val file: File,
        val bytesDownloaded: Long
    ) : ApkDownloadTransferResult

    data class Failed(
        val kind: UpdateErrorKind,
        val message: String
    ) : ApkDownloadTransferResult
}

interface ApkBinaryDownloader {
    suspend fun downloadToFile(
        assetUrl: String,
        expectedSizeBytes: Long,
        destinationFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): ApkDownloadTransferResult
}

/**
 * Streams an official SONORA APK from GitHub Releases into an isolated app cache file,
 * validating HTTPS transport, repository ownership, every redirect hop, file size bounds,
 * and supporting cooperative cancellation with automatic partial-file cleanup.
 */
class OkHttpApkBinaryDownloader(
    private val client: OkHttpClient = defaultDownloadClient()
) : ApkBinaryDownloader {

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val PROGRESS_EMIT_INTERVAL_BYTES = 64L * 1024L

        fun defaultDownloadClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }

    override suspend fun downloadToFile(
        assetUrl: String,
        expectedSizeBytes: Long,
        destinationFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): ApkDownloadTransferResult = withContext(Dispatchers.IO) {
        if (!GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(assetUrl)) {
            return@withContext ApkDownloadTransferResult.Failed(
                UpdateErrorKind.INVALID_RESPONSE,
                "Blocked APK download from untrusted source."
            )
        }

        if (expectedSizeBytes > SonoraGitHubUpdateConfig.MAX_APK_SIZE_BYTES) {
            return@withContext ApkDownloadTransferResult.Failed(
                UpdateErrorKind.DOWNLOAD_FAILED,
                "Release APK exceeds maximum safe download size."
            )
        }

        val parentDir = destinationFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }
        val partFile = File(destinationFile.parentFile, "${destinationFile.name}.part")
        if (partFile.exists()) partFile.delete()

        var currentUrl = SecureUrlValidator.parseSafeHttpUrl(assetUrl, allowHttp = false, allowPrivateHosts = false)
            ?: return@withContext ApkDownloadTransferResult.Failed(
                UpdateErrorKind.INVALID_RESPONSE,
                "Invalid HTTPS APK URL."
            )

        try {
            for (hop in 0..MAX_REDIRECTS) {
                currentCoroutineContext().ensureActive()
                val request = Request.Builder()
                    .url(currentUrl)
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", "SONORA-Android-Updater")
                    .get()
                    .build()

                val call = client.newCall(request)
                try {
                    call.execute().use { response ->
                        val code = response.code
                        if (code in setOf(301, 302, 303, 307, 308)) {
                            val nextUrl = GitHubReleaseUrlValidator.resolveAndValidateRedirect(
                                currentUrl,
                                response.header("Location")
                            ) ?: return@withContext ApkDownloadTransferResult.Failed(
                                UpdateErrorKind.INVALID_RESPONSE,
                                "Blocked untrusted redirect during APK download."
                            )
                            currentUrl = nextUrl
                            return@use
                        }

                        if (code == 429 || (code == 403 && response.header("X-RateLimit-Remaining") == "0")) {
                            return@withContext ApkDownloadTransferResult.Failed(
                                UpdateErrorKind.RATE_LIMITED,
                                "GitHub download rate limit reached. Please try again later."
                            )
                        }

                        if (code !in 200..299) {
                            return@withContext ApkDownloadTransferResult.Failed(
                                UpdateErrorKind.DOWNLOAD_FAILED,
                                "Download failed with HTTP $code."
                            )
                        }

                        val body = response.body ?: return@withContext ApkDownloadTransferResult.Failed(
                            UpdateErrorKind.DOWNLOAD_FAILED,
                            "Empty APK download response."
                        )

                        val declaredLength = body.contentLength()
                        val totalBytes = when {
                            declaredLength > 0L -> declaredLength
                            expectedSizeBytes > 0L -> expectedSizeBytes
                            else -> -1L
                        }
                        if (declaredLength > SonoraGitHubUpdateConfig.MAX_APK_SIZE_BYTES) {
                            return@withContext ApkDownloadTransferResult.Failed(
                                UpdateErrorKind.DOWNLOAD_FAILED,
                                "APK size exceeds safe download limit."
                            )
                        }

                        var downloaded = 0L
                        var lastReported = 0L
                        onProgress(0L, totalBytes)

                        body.byteStream().use { input ->
                            FileOutputStream(partFile).use { output ->
                                val buffer = ByteArray(16 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    downloaded += read
                                    if (downloaded > SonoraGitHubUpdateConfig.MAX_APK_SIZE_BYTES) {
                                        throw IOException("Downloaded APK exceeded safe size limit.")
                                    }
                                    output.write(buffer, 0, read)
                                    if (downloaded - lastReported >= PROGRESS_EMIT_INTERVAL_BYTES) {
                                        lastReported = downloaded
                                        onProgress(downloaded, totalBytes)
                                    }
                                }
                                output.flush()
                            }
                        }

                        onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded)

                        if (declaredLength > 0L && downloaded != declaredLength) {
                            partFile.delete()
                            return@withContext ApkDownloadTransferResult.Failed(
                                UpdateErrorKind.DOWNLOAD_FAILED,
                                "APK download was interrupted before completion."
                            )
                        }

                        if (destinationFile.exists()) {
                            destinationFile.delete()
                        }
                        if (!partFile.renameTo(destinationFile)) {
                            partFile.copyTo(destinationFile, overwrite = true)
                            partFile.delete()
                        }

                        return@withContext ApkDownloadTransferResult.Downloaded(
                            file = destinationFile,
                            bytesDownloaded = downloaded
                        )
                    }
                } catch (ce: CancellationException) {
                    call.cancel()
                    partFile.delete()
                    throw ce
                }
            }

            partFile.delete()
            ApkDownloadTransferResult.Failed(
                UpdateErrorKind.DOWNLOAD_FAILED,
                "Too many redirects while downloading APK."
            )
        } catch (ce: CancellationException) {
            partFile.delete()
            throw ce
        } catch (_: SocketTimeoutException) {
            partFile.delete()
            ApkDownloadTransferResult.Failed(
                UpdateErrorKind.TIMEOUT,
                "APK download timed out. Please try again."
            )
        } catch (_: UnknownHostException) {
            partFile.delete()
            ApkDownloadTransferResult.Failed(
                UpdateErrorKind.OFFLINE,
                "Internet connection lost during download."
            )
        } catch (_: SSLException) {
            partFile.delete()
            ApkDownloadTransferResult.Failed(
                UpdateErrorKind.DOWNLOAD_FAILED,
                "Secure HTTPS connection could not be verified for APK download."
            )
        } catch (_: IOException) {
            partFile.delete()
            ApkDownloadTransferResult.Failed(
                UpdateErrorKind.DOWNLOAD_FAILED,
                "APK download was interrupted. Please retry."
            )
        }
    }
}
