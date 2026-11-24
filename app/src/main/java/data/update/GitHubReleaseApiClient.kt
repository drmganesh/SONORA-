package data.update

import domain.update.GitHubReleaseUrlValidator
import domain.update.SonoraGitHubUpdateConfig
import domain.update.UpdateErrorKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import util.SecureUrlValidator
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed interface GitHubFetchResult {
    data class Success(val body: String) : GitHubFetchResult
    data class Failure(val kind: UpdateErrorKind, val message: String) : GitHubFetchResult
}

/**
 * Abstraction over HTTPS transport for GitHub Releases API and companion metadata/checksum assets.
 */
interface GitHubReleaseTransport {
    suspend fun fetchLatestReleaseJson(): GitHubFetchResult
    suspend fun fetchOfficialAssetText(assetUrl: String, maxBytes: Long): GitHubFetchResult
}

/**
 * Production HTTPS client using OkHttp with standard system TLS verification, bounded response
 * reads, explicit GitHub REST v3 headers, and strict redirect validation for companion assets.
 */
class OkHttpGitHubReleaseTransport(
    private val client: OkHttpClient = defaultHttpClient()
) : GitHubReleaseTransport {

    companion object {
        private const val MAX_REDIRECTS = 5

        fun defaultHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
        }
    }

    override suspend fun fetchLatestReleaseJson(): GitHubFetchResult = withContext(Dispatchers.IO) {
        val url = SonoraGitHubUpdateConfig.LATEST_RELEASE_API_URL
        if (!GitHubReleaseUrlValidator.isValidOfficialApiUrl(url)) {
            return@withContext GitHubFetchResult.Failure(
                UpdateErrorKind.INVALID_RESPONSE,
                "Configured release endpoint is not valid."
            )
        }
        executeValidatedGet(
            initialUrl = url,
            acceptHeader = "application/vnd.github+json",
            maxBytes = SonoraGitHubUpdateConfig.MAX_METADATA_JSON_BYTES,
            allowAssetCdnRedirects = false
        )
    }

    override suspend fun fetchOfficialAssetText(assetUrl: String, maxBytes: Long): GitHubFetchResult =
        withContext(Dispatchers.IO) {
            if (!GitHubReleaseUrlValidator.isValidOfficialReleaseAssetUrl(assetUrl)) {
                return@withContext GitHubFetchResult.Failure(
                    UpdateErrorKind.INVALID_RESPONSE,
                    "Refused untrusted release metadata asset URL."
                )
            }
            executeValidatedGet(
                initialUrl = assetUrl,
                acceptHeader = "application/octet-stream, text/plain, application/json",
                maxBytes = maxBytes,
                allowAssetCdnRedirects = true
            )
        }

    private fun executeValidatedGet(
        initialUrl: String,
        acceptHeader: String,
        maxBytes: Long,
        allowAssetCdnRedirects: Boolean
    ): GitHubFetchResult {
        var currentUrl = SecureUrlValidator.parseSafeHttpUrl(initialUrl, allowHttp = false, allowPrivateHosts = false)
            ?: return GitHubFetchResult.Failure(
                UpdateErrorKind.INVALID_RESPONSE,
                "Invalid HTTPS release URL."
            )

        for (hop in 0..MAX_REDIRECTS) {
            val request = Request.Builder()
                .url(currentUrl)
                .header("Accept", acceptHeader)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "SONORA-Android-Updater")
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    if (code in setOf(301, 302, 303, 307, 308)) {
                        val location = response.header("Location")
                        val nextUrl = if (allowAssetCdnRedirects) {
                            GitHubReleaseUrlValidator.resolveAndValidateRedirect(currentUrl, location)
                        } else {
                            val candidate = location?.let { currentUrl.resolve(it.trim()) }
                            candidate?.takeIf { GitHubReleaseUrlValidator.isValidOfficialApiUrl(it.toString()) }
                        }
                        if (nextUrl == null) {
                            return GitHubFetchResult.Failure(
                                UpdateErrorKind.INVALID_RESPONSE,
                                "Blocked untrusted redirect during update check."
                            )
                        }
                        currentUrl = nextUrl
                        return@use
                    }

                    if (code == 404) {
                        return GitHubFetchResult.Failure(
                            UpdateErrorKind.NO_RELEASE_FOUND,
                            "No published GitHub release was found yet."
                        )
                    }

                    if (code == 429 || (code == 403 && response.header("X-RateLimit-Remaining") == "0")) {
                        return GitHubFetchResult.Failure(
                            UpdateErrorKind.RATE_LIMITED,
                            "GitHub API rate limit reached. Please try again later."
                        )
                    }

                    if (code !in 200..299) {
                        return GitHubFetchResult.Failure(
                            UpdateErrorKind.HTTP_ERROR,
                            "GitHub returned HTTP $code while checking for updates."
                        )
                    }

                    val text = SecureUrlValidator.readBoundedString(response.body, maxBytes)
                    if (text.isBlank()) {
                        return GitHubFetchResult.Failure(
                            UpdateErrorKind.INVALID_RESPONSE,
                            "Received an empty response from GitHub."
                        )
                    }
                    return GitHubFetchResult.Success(text)
                }
            } catch (_: SocketTimeoutException) {
                return GitHubFetchResult.Failure(
                    UpdateErrorKind.TIMEOUT,
                    "Connection timed out while checking GitHub for updates."
                )
            } catch (_: UnknownHostException) {
                return GitHubFetchResult.Failure(
                    UpdateErrorKind.OFFLINE,
                    "Unable to reach GitHub. Check your internet connection."
                )
            } catch (_: SSLException) {
                return GitHubFetchResult.Failure(
                    UpdateErrorKind.HTTP_ERROR,
                    "Secure HTTPS connection to GitHub could not be verified."
                )
            } catch (_: IOException) {
                return GitHubFetchResult.Failure(
                    UpdateErrorKind.HTTP_ERROR,
                    "Network error while contacting GitHub Releases."
                )
            }
        }

        return GitHubFetchResult.Failure(
            UpdateErrorKind.INVALID_RESPONSE,
            "Too many redirects while contacting GitHub Releases."
        )
    }
}
