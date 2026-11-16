package data.online

import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Structured transport exception thrown for empty or fatal HTTP 5xx server responses
 * so upper layers can classify temporary server failures and apply bounded retry/backoff.
 */
class HttpTransportException(
    val statusCode: Int,
    val statusMessage: String,
    val requestHost: String,
    message: String = "HTTP $statusCode $statusMessage from $requestHost"
) : IOException(message)

/**
 * OkHttp-backed [Downloader] for NewPipeExtractor.
 * Handles headers, Content-Type preservation, transparent gzip compression, localization,
 * redirects, charset decoding, timeouts, HTTP status validation, and rate-limit detection.
 */
class NewPipeDownloader(
    val okHttpClient: OkHttpClient = createDefaultClient()
) : Downloader() {

    companion object {
        private const val TAG = "NewPipeDownloader"

        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val explicitContentType = headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.firstOrNull()
            ?.toMediaTypeOrNull()

        val requestBody: RequestBody? = when {
            dataToSend != null -> dataToSend.toRequestBody(explicitContentType)
            httpMethod.equals("POST", ignoreCase = true) ||
                httpMethod.equals("PUT", ignoreCase = true) ||
                httpMethod.equals("PATCH", ignoreCase = true) -> {
                ByteArray(0).toRequestBody(explicitContentType)
            }
            else -> null
        }

        val okBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, requestBody)

        var hasUserAgent = false
        var hasAcceptLanguage = false
        for ((headerName, headerValues) in headers) {
            // Let OkHttp's BridgeInterceptor manage Accept-Encoding so gzip decompression stays transparent
            if (headerName.equals("Accept-Encoding", ignoreCase = true)) {
                continue
            }
            if (headerName.equals("User-Agent", ignoreCase = true)) {
                hasUserAgent = true
            }
            if (headerName.equals("Accept-Language", ignoreCase = true)) {
                hasAcceptLanguage = true
            }
            for (value in headerValues) {
                okBuilder.addHeader(headerName, value)
            }
        }
        if (!hasUserAgent) {
            okBuilder.header("User-Agent", DEFAULT_USER_AGENT)
        }
        if (!hasAcceptLanguage) {
            val loc = request.localization()
                ?: runCatching { org.schabi.newpipe.extractor.NewPipe.getPreferredLocalization() }.getOrNull()
            val langHeader = if (loc != null && loc.languageCode.isNotBlank()) {
                if (!loc.countryCode.isNullOrBlank()) {
                    "${loc.languageCode}-${loc.countryCode}"
                } else {
                    loc.languageCode
                }
            } else {
                "${domain.online.OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE}-${domain.online.OnlineRegionCatalog.DEFAULT_COUNTRY_CODE}"
            }
            okBuilder.header("Accept-Language", langHeader)
        }

        val hostForLog = runCatching { okhttp3.HttpUrl.Builder().let { url.substringAfter("://").substringBefore("/") } }
            .getOrDefault("unknown-host")

        okHttpClient.newCall(okBuilder.build()).execute().use { okResponse ->
            val code = okResponse.code
            val statusMessage = okResponse.message
            if (code == 429) {
                Log.w(TAG, "HTTP 429 rate-limit/captcha from host=$hostForLog method=$httpMethod")
                throw ReCaptchaException("HTTP 429 rate-limit / challenge requested", url)
            }

            val bodyString = try {
                okResponse.body?.string().orEmpty()
            } catch (ioe: IOException) {
                Log.e(TAG, "Failed reading HTTP response body (code=$code, host=$hostForLog)", ioe)
                throw ioe
            }

            if (code >= 400) {
                Log.w(TAG, "HTTP error status=$code message=$statusMessage host=$hostForLog bodyLen=${bodyString.length}")
            }

            // Empty HTTP 5xx server failure cannot be parsed by extractors and should trigger transient retry
            if (code in 500..599 && bodyString.isBlank()) {
                throw HttpTransportException(
                    statusCode = code,
                    statusMessage = statusMessage,
                    requestHost = hostForLog
                )
            }

            val latestUrl = okResponse.request.url.toString()
            return Response(
                code,
                statusMessage,
                okResponse.headers.toMultimap(),
                bodyString,
                latestUrl
            )
        }
    }
}
