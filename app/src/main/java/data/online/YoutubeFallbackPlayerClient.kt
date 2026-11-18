package data.online

import android.util.Log
import domain.online.OnlineAudioStreamOption
import domain.online.StreamDeliveryType
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.utils.Parser
import java.nio.charset.StandardCharsets

/**
 * Resolves real YouTube Innertube player streams using alternate anonymous client profiles
 * when NewPipeExtractor's primary VISIONOS client fails playability with
 * [SignInConfirmNotBotException] or empty formats.
 *
 * Uses NewPipeExtractor's [Downloader], [YoutubeParsingHelper], [ItagItem], and
 * [YoutubeJavaScriptPlayerManager] without mocking or synthetic audio URLs.
 */
class YoutubeFallbackPlayerClient {

    data class ClientProfile(
        val clientName: String,
        val clientVersion: String,
        val clientId: String,
        val userAgent: String,
        val platform: String,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val androidSdkVersion: Int? = null,
        val isEmbedded: Boolean = false,
        val requiresSignatureTimestamp: Boolean = false
    )

    data class ExtractedPayload(
        val clientNameUsed: String,
        val title: String,
        val artist: String,
        val artistUrl: String?,
        val durationMs: Long,
        val artworkUrl: String?,
        val options: List<OnlineAudioStreamOption>,
        val isLive: Boolean,
        val viewCount: Long
    )

    sealed interface FallbackAttemptResult {
        data class Success(val payload: ExtractedPayload) : FallbackAttemptResult
        data class Failure(
            val lastThrowable: Throwable?,
            val sawBotCheck: Boolean,
            val sawPremiumOnly: Boolean
        ) : FallbackAttemptResult
    }

    companion object {
        private const val TAG = "YoutubeFallbackClient"
        private const val INNERTUBE_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        private const val INNERTUBE_VISITOR_ID_URL = "https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false"

        private val VIDEO_ID_REGEX = Regex("(?:v=|youtu\\.be/|shorts/|embed/|live/)([A-Za-z0-9_-]{11})")

        val FALLBACK_PROFILES: List<ClientProfile> = listOf(
            ClientProfile(
                clientName = "ANDROID_VR",
                clientVersion = "1.65.10",
                clientId = "28",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                platform = "MOBILE",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                osName = "Android",
                osVersion = "12L",
                androidSdkVersion = 32,
                isEmbedded = false,
                requiresSignatureTimestamp = false
            ),
            ClientProfile(
                clientName = "IOS",
                clientVersion = "20.10.4",
                clientId = "5",
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                platform = "MOBILE",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iPhone",
                osVersion = "18.3.2.22D82",
                isEmbedded = false,
                requiresSignatureTimestamp = false
            ),
            ClientProfile(
                clientName = "ANDROID_TESTSUITE",
                clientVersion = "1.9",
                clientId = "30",
                userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 13; en_IN) gzip",
                platform = "MOBILE",
                osName = "Android",
                osVersion = "13",
                androidSdkVersion = 33,
                isEmbedded = false,
                requiresSignatureTimestamp = false
            ),
            ClientProfile(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                clientId = "85",
                userAgent = NewPipeDownloader.DEFAULT_USER_AGENT,
                platform = "TV",
                isEmbedded = true,
                requiresSignatureTimestamp = true
            ),
            ClientProfile(
                clientName = "WEB_EMBEDDED_PLAYER",
                clientVersion = "1.20260805.01.00",
                clientId = "56",
                userAgent = NewPipeDownloader.DEFAULT_USER_AGENT,
                platform = "DESKTOP",
                isEmbedded = true,
                requiresSignatureTimestamp = true
            )
        )

        fun extractVideoId(contentUrl: String): String? {
            val fromNewPipe = runCatching {
                ServiceList.YouTube.streamLHFactory.getId(contentUrl)
            }.getOrNull()?.takeIf { it.length == 11 }
            if (fromNewPipe != null) return fromNewPipe

            val match = VIDEO_ID_REGEX.find(contentUrl)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) return match

            val trimmed = contentUrl.trim()
            return if (trimmed.matches(Regex("[A-Za-z0-9_-]{11}"))) trimmed else null
        }
    }

    fun resolveWithFallbackClients(
        downloader: Downloader,
        contentUrl: String,
        localization: Localization,
        country: ContentCountry,
        profiles: List<ClientProfile> = FALLBACK_PROFILES
    ): FallbackAttemptResult {
        val videoId = extractVideoId(contentUrl)
            ?: return FallbackAttemptResult.Failure(
                lastThrowable = IllegalArgumentException("Could not parse YouTube video ID from $contentUrl"),
                sawBotCheck = false,
                sawPremiumOnly = false
            )

        var lastThrowable: Throwable? = null
        var sawBotCheck = false
        var sawPremiumOnly = false
        var cachedVisitorData: String? = null
        var visitorFetched = false

        for (profile in profiles) {
            if (Thread.currentThread().isInterrupted) {
                break
            }
            try {
                // Only fetch WEB visitorData lazily if a web/embedded profile is reached
                val visitorData = if (profile.isEmbedded || profile.platform != "MOBILE") {
                    if (!visitorFetched) {
                        visitorFetched = true
                        cachedVisitorData = fetchOptionalVisitorData(downloader, localization, country)
                    }
                    cachedVisitorData
                } else {
                    null
                }

                val payload = fetchFromClientProfile(
                    downloader = downloader,
                    videoId = videoId,
                    localization = localization,
                    country = country,
                    visitorData = visitorData,
                    profile = profile
                )
                if (payload != null && payload.options.isNotEmpty()) {
                    Log.i(TAG, "Recovered stream for videoId=$videoId via fallback client=${profile.clientName} (${payload.options.size} formats)")
                    return FallbackAttemptResult.Success(payload)
                }
            } catch (botEx: SignInConfirmNotBotException) {
                sawBotCheck = true
                lastThrowable = botEx
                Log.w(TAG, "Fallback client=${profile.clientName} also triggered bot check for videoId=$videoId")
            } catch (premiumEx: YoutubeMusicPremiumContentException) {
                sawPremiumOnly = true
                lastThrowable = premiumEx
                Log.w(TAG, "Fallback client=${profile.clientName} returned YT Music Premium restriction for videoId=$videoId")
            } catch (t: Throwable) {
                lastThrowable = t
                Log.w(TAG, "Fallback client=${profile.clientName} failed for videoId=$videoId: ${t::class.java.simpleName}: ${t.message}")
            }
        }

        return FallbackAttemptResult.Failure(
            lastThrowable = lastThrowable,
            sawBotCheck = sawBotCheck,
            sawPremiumOnly = sawPremiumOnly
        )
    }

    private fun fetchOptionalVisitorData(
        downloader: Downloader,
        localization: Localization,
        country: ContentCountry
    ): String? {
        return runCatching {
            val payloadJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20260805.01.00")
                        put("hl", localization.languageCode.ifBlank { "en" })
                        put("gl", country.countryCode.ifBlank { "IN" })
                    })
                })
            }
            val bodyBytes = payloadJson.toString().toByteArray(StandardCharsets.UTF_8)
            val headers = mapOf(
                "User-Agent" to listOf(NewPipeDownloader.DEFAULT_USER_AGENT),
                "Origin" to listOf("https://www.youtube.com")
            )
            val response = downloader.postWithContentTypeJson(INNERTUBE_VISITOR_ID_URL, headers, bodyBytes, localization)
            val json = JSONObject(YoutubeParsingHelper.getValidJsonResponseBody(response))
            json.optJSONObject("responseContext")?.optString("visitorData", "")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    internal fun fetchFromClientProfile(
        downloader: Downloader,
        videoId: String,
        localization: Localization,
        country: ContentCountry,
        visitorData: String?,
        profile: ClientProfile
    ): ExtractedPayload? {
        val signatureTimestamp = if (profile.requiresSignatureTimestamp) {
            runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }.getOrNull()
        } else {
            null
        }

        val requestJson = buildPlayerRequestBody(
            videoId = videoId,
            localization = localization,
            country = country,
            visitorData = visitorData,
            signatureTimestamp = signatureTimestamp,
            profile = profile
        )
        val headers = LinkedHashMap<String, List<String>>().apply {
            put("User-Agent", listOf(profile.userAgent))
            put("X-YouTube-Client-Name", listOf(profile.clientId))
            put("X-YouTube-Client-Version", listOf(profile.clientVersion))
            put("X-Goog-Api-Format-Version", listOf("2"))
            if (profile.platform != "MOBILE" || profile.isEmbedded) {
                put("Origin", listOf("https://www.youtube.com"))
            }
            if (!visitorData.isNullOrBlank()) {
                put("X-Goog-Visitor-Id", listOf(visitorData))
            }
            if (profile.isEmbedded) {
                put("Referer", listOf("https://www.youtube.com/embed/$videoId"))
            }
        }

        val bodyBytes = requestJson.toString().toByteArray(StandardCharsets.UTF_8)
        val response = downloader.postWithContentTypeJson(INNERTUBE_PLAYER_URL, headers, bodyBytes, localization)
        val responseText = YoutubeParsingHelper.getValidJsonResponseBody(response)
        val root = JSONObject(responseText)
        return parsePlayerResponse(videoId = videoId, root = root, profile = profile)
    }

    internal fun parsePlayerResponse(
        videoId: String,
        root: JSONObject,
        profile: ClientProfile
    ): ExtractedPayload? {
        val playability = root.optJSONObject("playabilityStatus")
        validatePlayabilityStatus(playability)

        val videoDetails = root.optJSONObject("videoDetails")
        val returnedId = videoDetails?.optString("videoId", "")
        if (!returnedId.isNullOrBlank() && returnedId != videoId) {
            return null
        }

        val streamingData = root.optJSONObject("streamingData") ?: return null
        val clientHeaders = mapOf("User-Agent" to profile.userAgent)

        val audioOptions = mutableListOf<OnlineAudioStreamOption>()
        val muxedFallbackOptions = mutableListOf<OnlineAudioStreamOption>()

        extractFormatsArray(
            videoId = videoId,
            formats = streamingData.optJSONArray("adaptiveFormats"),
            preferAudioOnly = true,
            clientHeaders = clientHeaders,
            clientName = profile.clientName,
            outAudio = audioOptions,
            outMuxed = muxedFallbackOptions
        )
        extractFormatsArray(
            videoId = videoId,
            formats = streamingData.optJSONArray("formats"),
            preferAudioOnly = false,
            clientHeaders = clientHeaders,
            clientName = profile.clientName,
            outAudio = audioOptions,
            outMuxed = muxedFallbackOptions
        )

        val hlsManifestUrl = streamingData.optString("hlsManifestUrl", "")
        if (hlsManifestUrl.isNotBlank()) {
            audioOptions.add(
                OnlineAudioStreamOption(
                    id = "${profile.clientName}_hls_${videoId}",
                    url = hlsManifestUrl,
                    mimeType = "application/x-mpegURL",
                    codec = "HLS",
                    formatName = "HLS",
                    averageBitrateKbps = 128,
                    deliveryType = StreamDeliveryType.HLS,
                    isAudioOnly = true,
                    isLossless = false,
                    headers = clientHeaders
                )
            )
        }

        val effectiveOptions = audioOptions
            .distinctBy { it.url }
            .sortedByDescending { it.averageBitrateKbps }
            .ifEmpty { muxedFallbackOptions.distinctBy { it.url } }

        if (effectiveOptions.isEmpty()) return null

        val durationSeconds = videoDetails?.optString("lengthSeconds", "")?.toLongOrNull() ?: 0L
        val viewCount = videoDetails?.optString("viewCount", "")?.toLongOrNull() ?: -1L
        val isLive = videoDetails?.optBoolean("isLiveContent", false) == true && durationSeconds == 0L
        val channelId = videoDetails?.optString("channelId", "").orEmpty()
        val artworkUrl = extractBestThumbnailUrl(videoDetails?.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))

        return ExtractedPayload(
            clientNameUsed = profile.clientName,
            title = videoDetails?.optString("title", "").orEmpty().ifBlank { "Untitled Track" },
            artist = videoDetails?.optString("author", "").orEmpty().ifBlank { "YouTube" },
            artistUrl = if (channelId.isNotBlank()) "https://www.youtube.com/channel/$channelId" else null,
            durationMs = if (durationSeconds > 0L) durationSeconds * 1000L else 0L,
            artworkUrl = artworkUrl,
            options = effectiveOptions,
            isLive = isLive,
            viewCount = viewCount
        )
    }

    private fun extractFormatsArray(
        videoId: String,
        formats: JSONArray?,
        preferAudioOnly: Boolean,
        clientHeaders: Map<String, String>,
        clientName: String,
        outAudio: MutableList<OnlineAudioStreamOption>,
        outMuxed: MutableList<OnlineAudioStreamOption>
    ) {
        if (formats == null) return
        for (i in 0 until formats.length()) {
            val obj = formats.optJSONObject(i) ?: continue
            val rawMimeType = obj.optString("mimeType", "")
            val isAudio = rawMimeType.startsWith("audio/", ignoreCase = true)
            val isVideoWithAudio = !isAudio && rawMimeType.startsWith("video/", ignoreCase = true) && !preferAudioOnly
            if (!isAudio && !isVideoWithAudio) continue

            val resolvedUrl = resolveStreamFormatUrl(videoId = videoId, formatObj = obj) ?: continue
            val itag = obj.optInt("itag", -1)
            val itagInfo = if (itag > 0) runCatching { ItagItem.getItag(itag) }.getOrNull() else null
            val mimeBase = rawMimeType.substringBefore(';').trim().ifBlank {
                itagInfo?.mediaFormat?.mimeType ?: if (isAudio) "audio/mp4" else "video/mp4"
            }
            val codec = rawMimeType
                .substringAfter("codecs=\"", missingDelimiterValue = "")
                .substringBefore("\"")
                .ifBlank { itagInfo?.mediaFormat?.name ?: if (isAudio) "AAC/M4A" else "MP4" }
            val formatName = itagInfo?.mediaFormat?.name ?: mimeBase.substringAfter('/').uppercase()
            val rawBitrateBps = obj.optInt("averageBitrate", 0)
                .takeIf { it > 0 }
                ?: obj.optInt("bitrate", 0)
            val bitrateKbps = when {
                rawBitrateBps > 0 -> (rawBitrateBps / 1000).coerceAtLeast(1)
                itagInfo != null && itagInfo.bitrate > 0 -> itagInfo.bitrate
                else -> 0
            }

            val option = OnlineAudioStreamOption(
                id = "${clientName}_itag_${itag}_${bitrateKbps}",
                url = resolvedUrl,
                mimeType = mimeBase,
                codec = codec,
                formatName = formatName,
                averageBitrateKbps = bitrateKbps,
                deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
                isAudioOnly = isAudio,
                isLossless = false,
                headers = clientHeaders
            )
            if (isAudio) outAudio.add(option) else outMuxed.add(option)
        }
    }

    private fun resolveStreamFormatUrl(videoId: String, formatObj: JSONObject): String? {
        val directUrl = formatObj.optString("url", "")
        var resolved = if (directUrl.isNotBlank()) {
            directUrl
        } else {
            val cipher = formatObj.optString("signatureCipher", "")
                .ifBlank { formatObj.optString("cipher", "") }
            if (cipher.isBlank()) return null
            val parsed = runCatching { Parser.compatParseMap(cipher) }.getOrNull() ?: return null
            val baseUrl = parsed["url"].orEmpty()
            if (baseUrl.isBlank()) return null
            val sig = parsed["s"].orEmpty()
            val sp = parsed["sp"].orEmpty().ifBlank { "signature" }
            if (sig.isNotBlank()) {
                val deobf = runCatching { YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, sig) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: return null
                val separator = if (baseUrl.contains('?')) '&' else '?'
                "$baseUrl$separator$sp=$deobf"
            } else {
                baseUrl
            }
        }

        resolved = runCatching {
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, resolved)
        }.getOrDefault(resolved)

        return resolved.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }

    private fun validatePlayabilityStatus(playability: JSONObject?) {
        if (playability == null) return
        val status = playability.optString("status", "")
        if (status.equals("OK", ignoreCase = true) || status.isBlank()) return

        val reason = playability.optString("reason", "")
        if (status.equals("LOGIN_REQUIRED", ignoreCase = true)) {
            if (reason.isBlank()) {
                throw ContentNotAvailableException("Video is private or requires login")
            }
            if (reason.contains("a bot", ignoreCase = true) || reason.contains("Sign in to confirm", ignoreCase = true)) {
                throw SignInConfirmNotBotException(
                    "YouTube temporarily blocked anonymous watch access with this IP ($status: $reason)"
                )
            }
            if (reason.contains("private", ignoreCase = true)) {
                throw ContentNotAvailableException("Private video")
            }
            if (reason.contains("age", ignoreCase = true)) {
                throw AgeRestrictedContentException("Age-restricted video")
            }
        }

        if (status.equals("UNPLAYABLE", ignoreCase = true) || status.equals("ERROR", ignoreCase = true)) {
            when {
                reason.contains("Music Premium", ignoreCase = true) ->
                    throw YoutubeMusicPremiumContentException()
                reason.contains("payment", ignoreCase = true) || reason.contains("members", ignoreCase = true) ->
                    throw PaidContentException(reason)
                reason.contains("country", ignoreCase = true) ->
                    throw GeographicRestrictionException(reason)
            }
        }
        throw ContentNotAvailableException("$status: $reason".trim())
    }

    private fun buildPlayerRequestBody(
        videoId: String,
        localization: Localization,
        country: ContentCountry,
        visitorData: String?,
        signatureTimestamp: Int?,
        profile: ClientProfile
    ): JSONObject {
        val clientObj = JSONObject().apply {
            put("hl", localization.languageCode.ifBlank { "en" })
            put("gl", country.countryCode.ifBlank { "IN" })
            put("clientName", profile.clientName)
            put("clientVersion", profile.clientVersion)
            put("platform", profile.platform)
            profile.deviceMake?.let { put("deviceMake", it) }
            profile.deviceModel?.let { put("deviceModel", it) }
            profile.osName?.let { put("osName", it) }
            profile.osVersion?.let { put("osVersion", it) }
            profile.androidSdkVersion?.let { put("androidSdkVersion", it) }
            if (profile.isEmbedded) {
                put("clientScreen", "EMBED")
            }
            if (!visitorData.isNullOrBlank()) {
                put("visitorData", visitorData)
            }
        }

        val contextObj = JSONObject().apply {
            put("client", clientObj)
            if (profile.isEmbedded) {
                put("thirdParty", JSONObject().apply {
                    put("embedUrl", "https://www.youtube.com/embed/$videoId")
                })
            }
        }

        return JSONObject().apply {
            put("context", contextObj)
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            if (signatureTimestamp != null) {
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("signatureTimestamp", signatureTimestamp)
                    })
                })
            }
        }
    }

    private fun extractBestThumbnailUrl(thumbnails: JSONArray?): String? {
        if (thumbnails == null || thumbnails.length() == 0) return null
        var bestUrl: String? = null
        var bestPixels = -1L
        for (i in 0 until thumbnails.length()) {
            val obj = thumbnails.optJSONObject(i) ?: continue
            val url = obj.optString("url", "")
            if (url.isBlank()) continue
            val pixels = obj.optInt("width", 0).toLong() * obj.optInt("height", 0).toLong()
            if (pixels >= bestPixels) {
                bestPixels = pixels
                bestUrl = url
            }
        }
        return bestUrl
    }
}
