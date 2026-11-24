package data.festival

import domain.festival.FestivalCategory
import domain.festival.FestivalRecord
import domain.festival.FestivalSourcePriority
import domain.festival.FestivalVerificationStatus
import domain.festival.IndianFestivalRegion
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

fun interface FestivalHttpTransport {
    @Throws(IOException::class)
    fun get(url: String): String
}

data class FestivalHttpResponse(
    val statusCode: Int,
    val body: String?,
    val etag: String? = null,
    val lastModified: String? = null,
    val contentType: String? = null
)

fun interface FestivalConditionalHttpTransport {
    @Throws(IOException::class)
    fun get(
        url: String,
        ifNoneMatch: String?,
        ifModifiedSince: String?
    ): FestivalHttpResponse
}

/**
 * Typed server/transport failure (HTTP 404/429/5xx, DNS, TLS, timeout).
 * Never embeds raw response bodies in [message].
 */
class FestivalServerException(
    val httpStatusCode: Int? = null,
    val isTimeout: Boolean = false,
    val isRateLimited: Boolean = false,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

/**
 * Typed response integrity / schema validation failure (empty body, HTML error page,
 * malformed JSON, changed/unrecognized schema, zero valid festival records).
 * Never embeds raw JSON payloads in [message].
 */
class FestivalInvalidResponseException(
    val reasonCode: String,
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Structured parse result tracking accepted vs individually rejected records and discovered collections.
 * Preserves raw source length/hash diagnostics without ever exposing raw JSON to UI.
 */
data class FestivalParseSummary(
    val records: List<FestivalRecord>,
    val validCount: Int,
    val rejectedCount: Int,
    val collectionsFound: List<String>,
    val rawPayloadBytes: Int = 0
)

data class FestivalFeedFetchResult(
    val records: List<FestivalRecord>,
    val sourceSummary: String,
    val sourceAttribution: String,
    val datasetVersion: String,
    val fetchedRemoteCount: Int,
    val rejectedRemoteCount: Int = 0,
    val collectionsFound: List<String> = emptyList(),
    val httpStatusCode: Int = 200,
    val contentType: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val isNotModified: Boolean = false
)

interface FestivalCalendarDataSource {
    suspend fun fetchLatestCatalog(
        nowEpochMs: Long,
        ifNoneMatch: String? = null,
        ifModifiedSince: String? = null,
        existingCachedRecords: List<FestivalRecord> = emptyList()
    ): Result<FestivalFeedFetchResult>

    fun getVerifiedBaseCatalog(nowEpochMs: Long): List<FestivalRecord>
}

/**
 * Public Festival Calendar DataSource integrating:
 * 1. Verified Pan-India & Regional Rashtriya Panchang catalog (82 baseline records, Priority 100/85)
 * 2. MIT-licensed `ganeshsp1/hindu-festivals-api` public JSON feed via jsDelivr CDN (Priority 70)
 *
 * Schema support:
 * - Object keyed by collection (`{"national": [...], "maharashtra": [...], "kerala": [...], ...}`),
 *   which is the actual schema returned by `data/latest.json`.
 * - Nested regional container (`{"national": [...], "regional": {"kerala": [...], ...}}` / `"states"`).
 * - Wrapped collections (`{"festivals": [...]}` or `{"data": {...}}`).
 * - Top-level array (`[{ "date": "...", "name": "...", "state_proxy": "..." }]`) for state feeds.
 *
 * Parsing guarantees:
 * - National and regional collections are parsed as separate collections.
 * - Multi-festival strings (e.g., `"Ugadi, Gudi Padwa"` or `"Makar Sankranti / Pongal"`) are split
 *   into individual normalized [FestivalRecord] items instead of merging unrelated names.
 * - Malformed records are rejected individually without discarding valid records in the same feed.
 * - Raw JSON is NEVER stored in festival names or error messages.
 */
class PublicFestivalCalendarDataSource(
    private val conditionalTransport: FestivalConditionalHttpTransport = defaultConditionalTransport()
) : FestivalCalendarDataSource {

    constructor(transport: FestivalHttpTransport) : this(
        conditionalTransport = FestivalConditionalHttpTransport { url, _, _ ->
            val body = transport.get(url)
            FestivalHttpResponse(statusCode = 200, body = body, contentType = "application/json")
        }
    )

    companion object {
        const val MIT_FESTIVALS_LATEST_URL =
            "https://cdn.jsdelivr.net/gh/ganeshsp1/hindu-festivals-api/data/latest.json"
        const val MIT_FEED_SOURCE_NAME = "ganeshsp1/hindu-festivals-api (jsDelivr)"
        const val MIT_FEED_LICENSE = "MIT License"
        const val FULL_SOURCE_ATTRIBUTION =
            "IMD / PAC Rashtriya Panchang • Regional State Panjis • ganeshsp1/hindu-festivals-api (MIT) • satmargastro.com (CC BY 4.0)"
        private const val REMOTE_RECORD_TTL_MS = 180L * 24L * 60L * 60L * 1000L // 180 days
        private const val MIN_VALID_YEAR = 2024
        private const val MAX_VALID_YEAR = 2035
        private const val MAX_FESTIVAL_NAME_LENGTH = 120

        private val METADATA_ONLY_KEYS = setOf(
            "year", "version", "updated", "updated_at", "last_updated",
            "generated_at", "source", "license", "meta", "metadata",
            "status", "message", "error", "count", "total"
        )

        private val WRAPPER_CONTAINER_KEYS = setOf(
            "festivals", "data", "events", "items", "calendar", "holidays"
        )

        private val NESTED_REGIONAL_CONTAINER_KEYS = setOf(
            "regional", "regions", "states", "state_calendars"
        )

        private val ISO_DATE_PREFIX_REGEX = Regex("^(\\d{4})[-/](\\d{1,2})[-/](\\d{1,2}).*$")
        private val MULTI_FESTIVAL_SPLIT_REGEX = Regex("\\s*(?:,|;|/|\\||\\s+&\\s+)\\s*")
        private val MULTI_WHITESPACE_REGEX = Regex("\\s+")
        private val HAS_LETTER_REGEX = Regex("[\\p{L}]")

        private fun defaultConditionalTransport(): FestivalConditionalHttpTransport {
            val client = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            return FestivalConditionalHttpTransport { url, ifNoneMatch, ifModifiedSince ->
                val safeUrl = util.SecureUrlValidator.parseSafeHttpUrl(
                    rawUrl = url,
                    allowHttp = false,
                    allowPrivateHosts = false
                ) ?: throw FestivalServerException(
                    httpStatusCode = null,
                    message = "Rejected unsafe festival calendar URL"
                )
                val builder = Request.Builder()
                    .url(safeUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "SONORA-Android/${com.example.BuildConfig.VERSION_NAME}")
                    .get()
                if (!ifNoneMatch.isNullOrBlank()) {
                    builder.header("If-None-Match", ifNoneMatch)
                }
                if (!ifModifiedSince.isNullOrBlank()) {
                    builder.header("If-Modified-Since", ifModifiedSince)
                }
                try {
                    client.newCall(builder.build()).execute().use { response ->
                        val code = response.code
                        val etag = response.header("ETag")
                        val lastModified = response.header("Last-Modified")
                        val contentType = response.header("Content-Type")
                        if (code == 304) {
                            return@FestivalConditionalHttpTransport FestivalHttpResponse(
                                statusCode = 304,
                                body = null,
                                etag = etag ?: ifNoneMatch,
                                lastModified = lastModified ?: ifModifiedSince,
                                contentType = contentType
                            )
                        }
                        val body = util.SecureUrlValidator.readBoundedString(
                            body = response.body,
                            maxBytes = 4L * 1024L * 1024L
                        )
                        FestivalHttpResponse(
                            statusCode = code,
                            body = body,
                            etag = etag,
                            lastModified = lastModified,
                            contentType = contentType
                        )
                    }
                } catch (e: SocketTimeoutException) {
                    throw FestivalServerException(
                        httpStatusCode = null,
                        isTimeout = true,
                        message = "Request timed out",
                        cause = e
                    )
                } catch (e: UnknownHostException) {
                    throw FestivalServerException(
                        httpStatusCode = null,
                        message = "DNS resolution failed",
                        cause = e
                    )
                } catch (e: SSLException) {
                    throw FestivalServerException(
                        httpStatusCode = null,
                        message = "TLS handshake failed",
                        cause = e
                    )
                } catch (e: FestivalServerException) {
                    throw e
                } catch (e: IOException) {
                    throw FestivalServerException(
                        httpStatusCode = null,
                        message = "Network I/O error (${e.javaClass.simpleName})",
                        cause = e
                    )
                }
            }
        }

        /**
         * Validates HTTP response status code and Content-Type header before JSON parsing.
         */
        fun validateHttpResponse(response: FestivalHttpResponse) {
            val code = response.statusCode
            when {
                code == 304 -> return
                code == 429 -> throw FestivalServerException(
                    httpStatusCode = 429,
                    isRateLimited = true,
                    message = "HTTP 429 rate limited"
                )
                code == 404 -> throw FestivalServerException(
                    httpStatusCode = 404,
                    message = "HTTP 404 resource not found"
                )
                code in 500..599 -> throw FestivalServerException(
                    httpStatusCode = code,
                    message = "HTTP $code server error"
                )
                code !in 200..299 -> throw FestivalServerException(
                    httpStatusCode = code,
                    message = "HTTP $code unexpected status"
                )
            }

            val rawCt = response.contentType?.trim()?.lowercase().orEmpty()
            if (rawCt.isNotEmpty()) {
                val mediaType = rawCt.substringBefore(';').trim()
                val allowed = mediaType == "application/json" ||
                    mediaType == "text/json" ||
                    mediaType == "text/plain" ||
                    mediaType.endsWith("+json")
                if (!allowed) {
                    throw FestivalInvalidResponseException(
                        reasonCode = "INVALID_CONTENT_TYPE",
                        message = "Unsupported content type: $mediaType"
                    )
                }
            }
        }

        /**
         * Parses public Hindu festival JSON payloads supporting both:
         * - Object schema: `{"national": [...], "maharashtra": [...], "kerala": [...], ...}`
         * - Nested regional schema: `{"national": [...], "regional": {"kerala": [...], ...}}`
         * - Wrapped schema: `{"festivals": [...]}` or `{"data": {...}}`
         * - Array schema: `[{"date": "...", "name": "...", "state_proxy": "..."}]`
         *
         * Rejects malformed individual records without discarding valid records; throws
         * [FestivalInvalidResponseException] (with zero raw JSON embedded) if the overall payload
         * is empty, malformed JSON, unrecognized schema, or yields zero valid records.
         */
        fun parsePublicCalendarPayload(
            rawJson: String,
            nowEpochMs: Long
        ): FestivalParseSummary {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) {
                throw FestivalInvalidResponseException(
                    reasonCode = "EMPTY_RESPONSE",
                    message = "Calendar response body was empty"
                )
            }

            val rootValue = try {
                JSONTokener(trimmed).nextValue()
            } catch (e: JSONException) {
                throw FestivalInvalidResponseException(
                    reasonCode = "MALFORMED_JSON",
                    message = "Response body is not valid JSON",
                    cause = e
                )
            }

            val collections = linkedMapOf<String, JSONArray>()
            when (rootValue) {
                is JSONArray -> {
                    collections["national"] = rootValue
                }
                is JSONObject -> {
                    extractCollectionsFromObject(rootValue, collections)
                }
                else -> {
                    throw FestivalInvalidResponseException(
                        reasonCode = "UNEXPECTED_ROOT_TYPE",
                        message = "Expected JSON object or array at calendar root"
                    )
                }
            }

            if (collections.isEmpty()) {
                throw FestivalInvalidResponseException(
                    reasonCode = "UNRECOGNIZED_SCHEMA",
                    message = "Response schema contains no recognized festival collections"
                )
            }

            val validRecords = mutableListOf<FestivalRecord>()
            val seenIdentityKeys = HashSet<String>()
            var rejectedCount = 0

            for ((collectionKey, jsonArray) in collections) {
                val collectionRegions = resolveRegionsFromCollectionKey(collectionKey)
                val collectionIsNational = isNationalCollectionKey(collectionKey)

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i)
                    if (item == null) {
                        rejectedCount++
                        continue
                    }

                    val parsedForItem = parseSingleFestivalObject(
                        obj = item,
                        collectionKey = collectionKey,
                        collectionIsNational = collectionIsNational,
                        collectionRegions = collectionRegions,
                        nowEpochMs = nowEpochMs
                    )
                    if (parsedForItem.isEmpty()) {
                        rejectedCount++
                        continue
                    }

                    for (record in parsedForItem) {
                        val dedupIdentity = stableFestivalIdentity(record)
                        if (seenIdentityKeys.add(dedupIdentity)) {
                            validRecords += record
                        }
                    }
                }
            }

            if (validRecords.isEmpty()) {
                throw FestivalInvalidResponseException(
                    reasonCode = "NO_VALID_RECORDS",
                    message = "Calendar response contained zero valid festival entries (rejected=$rejectedCount)"
                )
            }

            return FestivalParseSummary(
                records = validRecords,
                validCount = validRecords.size,
                rejectedCount = rejectedCount,
                collectionsFound = collections.keys.toList(),
                rawPayloadBytes = trimmed.toByteArray(Charsets.UTF_8).size
            )
        }

        /**
         * Backward-compatible entry point returning the validated list of parsed records.
         */
        fun parseMitFestivalsJson(
            rawJson: String,
            nowEpochMs: Long
        ): List<FestivalRecord> = parsePublicCalendarPayload(rawJson, nowEpochMs).records

        private fun extractCollectionsFromObject(
            obj: JSONObject,
            outCollections: MutableMap<String, JSONArray>
        ) {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val rawKey = keys.next() ?: continue
                val normalizedKey = rawKey.trim().lowercase().replace('-', '_').replace(' ', '_')
                if (normalizedKey in METADATA_ONLY_KEYS) continue

                val value = obj.opt(rawKey)
                when {
                    normalizedKey in WRAPPER_CONTAINER_KEYS && value is JSONArray -> {
                        outCollections["national"] = value
                    }
                    normalizedKey in WRAPPER_CONTAINER_KEYS && value is JSONObject -> {
                        extractCollectionsFromObject(value, outCollections)
                    }
                    normalizedKey in NESTED_REGIONAL_CONTAINER_KEYS && value is JSONObject -> {
                        val subKeys = value.keys()
                        while (subKeys.hasNext()) {
                            val regionKey = subKeys.next() ?: continue
                            val regionArray = value.optJSONArray(regionKey) ?: continue
                            val normRegion = regionKey.trim().lowercase().replace('-', '_').replace(' ', '_')
                            if (normRegion.isNotBlank() && normRegion !in METADATA_ONLY_KEYS) {
                                outCollections[normRegion] = regionArray
                            }
                        }
                    }
                    value is JSONArray -> {
                        // Direct top-level national or regional array (e.g., "national", "maharashtra", "kerala", "andhra_telangana")
                        if (isNationalCollectionKey(normalizedKey) || resolveRegionsFromCollectionKey(normalizedKey).isNotEmpty()) {
                            outCollections[normalizedKey] = value
                        }
                    }
                }
            }
        }

        private fun parseSingleFestivalObject(
            obj: JSONObject,
            collectionKey: String,
            collectionIsNational: Boolean,
            collectionRegions: Set<IndianFestivalRegion>,
            nowEpochMs: Long
        ): List<FestivalRecord> {
            val rawDate = firstNonBlankString(obj, "date", "start_date", "startDate") ?: return emptyList()
            val startDate = parseValidIsoDate(rawDate) ?: return emptyList()

            val rawEndDate = firstNonBlankString(obj, "end_date", "endDate")
            val endDate = if (rawEndDate != null) {
                val parsedEnd = parseValidIsoDate(rawEndDate) ?: return emptyList()
                if (parsedEnd.isBefore(startDate)) return emptyList()
                parsedEnd
            } else {
                startDate
            }

            val rawName = firstNonBlankString(obj, "name", "title", "festival_name", "festivalName") ?: return emptyList()
            val individualNames = splitAndSanitizeFestivalNames(rawName)
            if (individualNames.isEmpty()) return emptyList()

            val rawStateProxy = firstNonBlankString(obj, "state_proxy", "state", "region").orEmpty()
            val rawTithi = firstNonBlankString(obj, "tithi")?.takeIf { !looksLikeRawJsonOrCode(it) }
            val explicitId = firstNonBlankString(obj, "id", "festival_id")?.takeIf { !looksLikeRawJsonOrCode(it) }

            val itemProxyRegions = resolveRegionsFromCollectionKey(rawStateProxy)
            val itemExplicitlyNational = isNationalCollectionKey(rawStateProxy)

            val resolvedRegions: Set<IndianFestivalRegion>
            val isPanIndia: Boolean
            when {
                // Separate national vs regional collections cleanly
                !collectionIsNational && collectionRegions.isNotEmpty() -> {
                    resolvedRegions = if (itemProxyRegions.isNotEmpty()) {
                        collectionRegions + itemProxyRegions
                    } else {
                        collectionRegions
                    }
                    isPanIndia = false
                }
                itemProxyRegions.isNotEmpty() && !itemExplicitlyNational -> {
                    resolvedRegions = itemProxyRegions
                    isPanIndia = false
                }
                else -> {
                    resolvedRegions = emptySet()
                    isPanIndia = true
                }
            }

            val regionKeySuffix = if (isPanIndia) {
                "mit_pan"
            } else {
                "mit_" + resolvedRegions.map { it.shortCode.lowercase() }.sorted().joinToString("_")
            }

            val regionDisplay = if (isPanIndia) {
                "across India"
            } else {
                "(${resolvedRegions.joinToString(" / ") { it.displayName }} regional calendar)"
            }

            val output = ArrayList<FestivalRecord>(individualNames.size)
            for (cleanName in individualNames) {
                val (inferredSlug, effectId, glyph) = PanIndiaFestivalCatalog.inferEffectAndGlyph(cleanName)
                val stableSlug = if (individualNames.size == 1 && !explicitId.isNullOrBlank()) {
                    normalizeSlug(explicitId).ifBlank { inferredSlug }
                } else {
                    inferredSlug
                }

                output += FestivalRecord(
                    recordKey = "${stableSlug}_${startDate}_$regionKeySuffix",
                    festivalSlug = stableSlug,
                    canonicalName = cleanName,
                    alternateRegionalNames = emptyList(),
                    startDateIso = startDate.toString(),
                    endDateIso = endDate.toString(),
                    tithi = rawTithi,
                    regions = resolvedRegions,
                    isPanIndia = isPanIndia,
                    category = if (isPanIndia) FestivalCategory.MAJOR_PAN_INDIA else FestivalCategory.REGIONAL_STATE,
                    shortDescription = "Public Hindu festival observance $regionDisplay.",
                    effectId = effectId,
                    iconGlyph = glyph,
                    sourceName = MIT_FEED_SOURCE_NAME,
                    sourceLicense = MIT_FEED_LICENSE,
                    sourcePriority = FestivalSourcePriority.PUBLIC_MIT_JSON,
                    verificationStatus = FestivalVerificationStatus.PUBLIC_FEED_VERIFIED,
                    disagreementNote = null,
                    lastUpdatedEpochMs = nowEpochMs,
                    expiresAtEpochMs = nowEpochMs + REMOTE_RECORD_TTL_MS
                )
            }
            return output
        }

        /**
         * Splits multi-festival source strings (e.g. `"Ugadi, Gudi Padwa"`, `"Holika Dahan / Chhoti Holi"`,
         * `"Makar Sankranti; Pongal"`) into individual clean festival names.
         * Rejects raw JSON objects/arrays or non-text garbage so raw JSON can never become a festival name.
         */
        fun splitAndSanitizeFestivalNames(rawName: String): List<String> {
            val trimmed = rawName.trim()
            if (trimmed.isEmpty() || looksLikeRawJsonOrCode(trimmed)) return emptyList()

            val parts = MULTI_FESTIVAL_SPLIT_REGEX.split(trimmed)
            val seenLower = HashSet<String>()
            val result = mutableListOf<String>()

            for (part in parts) {
                val normalized = part
                    .replace(MULTI_WHITESPACE_REGEX, " ")
                    .trim(' ', '-', ':', '•', '.', '"', '\'')
                if (normalized.length < 2 || normalized.length > MAX_FESTIVAL_NAME_LENGTH) continue
                if (!HAS_LETTER_REGEX.containsMatchIn(normalized)) continue
                if (looksLikeRawJsonOrCode(normalized)) continue
                if (seenLower.add(normalized.lowercase())) {
                    result += normalized
                }
            }
            return result
        }

        /**
         * Detects raw JSON payloads, Kotlin object dumps, or HTML markup so they are never
         * accepted as festival names or surfaced directly to users.
         */
        fun looksLikeRawJsonOrCode(text: String): Boolean {
            val t = text.trim()
            if (t.isEmpty()) return false
            return t.startsWith("{") ||
                t.startsWith("[") ||
                t.contains("{\"") ||
                t.contains("\":[") ||
                t.contains("\":\"") ||
                t.contains("org.json.") ||
                t.contains("JSONObject") ||
                t.contains("JSONArray") ||
                t.startsWith("<!DOCTYPE", ignoreCase = true) ||
                t.startsWith("<html", ignoreCase = true)
        }

        /**
         * Sanitizes any exception/diagnostic string so raw JSON bodies, stack traces, or
         * internal class dumps are NEVER stored in user-facing fields or rendered in UI.
         */
        fun sanitizeDiagnosticText(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val singleLine = raw.lineSequence().firstOrNull()?.trim().orEmpty()
            if (singleLine.isEmpty()) return null
            if (looksLikeRawJsonOrCode(singleLine) ||
                singleLine.contains('{') ||
                singleLine.contains('}') ||
                singleLine.contains('[') ||
                singleLine.contains(']')
            ) {
                return "Unexpected response structure from calendar source"
            }
            return singleLine.take(120)
        }

        private fun firstNonBlankString(obj: JSONObject, vararg keys: String): String? {
            for (key in keys) {
                if (!obj.has(key) || obj.isNull(key)) continue
                val value = obj.opt(key)
                // Strictly reject nested JSONObject or JSONArray values inside scalar text fields
                if (value is JSONObject || value is JSONArray) return null
                val str = value?.toString()?.trim().orEmpty()
                if (str.isNotEmpty() && !str.equals("null", ignoreCase = true)) {
                    return str
                }
            }
            return null
        }

        private fun parseValidIsoDate(raw: String): LocalDate? {
            val direct = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()
            val candidate = direct ?: run {
                val match = ISO_DATE_PREFIX_REGEX.matchEntire(raw.trim()) ?: return null
                val y = match.groupValues[1].toIntOrNull() ?: return null
                val m = match.groupValues[2].toIntOrNull() ?: return null
                val d = match.groupValues[3].toIntOrNull() ?: return null
                runCatching { LocalDate.of(y, m, d) }.getOrNull()
            } ?: return null

            return candidate.takeIf { it.year in MIN_VALID_YEAR..MAX_VALID_YEAR }
        }

        private fun isNationalCollectionKey(raw: String): Boolean {
            val norm = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
            return norm == "national" || norm == "all_india" || norm == "india" || norm == "pan_india"
        }

        fun resolveRegionsFromCollectionKey(raw: String): Set<IndianFestivalRegion> {
            val norm = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
            if (norm.isEmpty() || isNationalCollectionKey(norm)) return emptySet()

            return when (norm) {
                "andhra_telangana", "andhra_pradesh_telangana", "ap_ts", "telugu" ->
                    setOf(IndianFestivalRegion.ANDHRA_PRADESH, IndianFestivalRegion.TELANGANA)
                "maharashtra", "marathi" -> setOf(IndianFestivalRegion.MAHARASHTRA)
                "karnataka", "kannada" -> setOf(IndianFestivalRegion.KARNATAKA)
                "tamil_nadu", "tamilnadu", "tamil" -> setOf(IndianFestivalRegion.TAMIL_NADU)
                "kerala", "malayalam" -> setOf(IndianFestivalRegion.KERALA)
                "west_bengal", "bengal", "bengali" -> setOf(IndianFestivalRegion.WEST_BENGAL)
                "gujarat", "gujarati" -> setOf(IndianFestivalRegion.GUJARAT)
                "odisha", "orissa", "odia", "oriya" -> setOf(IndianFestivalRegion.ODISHA)
                "punjab", "punjabi" -> setOf(IndianFestivalRegion.PUNJAB)
                "assam", "assamese" -> setOf(IndianFestivalRegion.ASSAM)
                "rajasthan", "rajasthani" -> setOf(IndianFestivalRegion.RAJASTHAN)
                "uttar_pradesh", "up" -> setOf(IndianFestivalRegion.UTTAR_PRADESH)
                "madhya_pradesh", "mp" -> setOf(IndianFestivalRegion.MADHYA_PRADESH)
                "himachal_pradesh", "hp" -> setOf(IndianFestivalRegion.HIMACHAL_PRADESH)
                "jammu_kashmir", "jk", "kashmir" -> setOf(IndianFestivalRegion.JAMMU_KASHMIR)
                else -> {
                    val mapped = IndianFestivalRegion.fromId(norm)
                    if (mapped != IndianFestivalRegion.ALL_INDIA) setOf(mapped) else emptySet()
                }
            }
        }

        private fun normalizeSlug(raw: String): String =
            raw.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')

        /**
         * Stable deduplication identity based on festival slug, start date, and normalized region scope.
         */
        fun stableFestivalIdentity(record: FestivalRecord): String {
            val regionKey = if (record.isPanIndia) {
                "pan_india"
            } else {
                record.regions.map { it.id }.sorted().joinToString(",")
            }
            return "${record.festivalSlug}|${record.startDateIso}|$regionKey"
        }

        /**
         * Validates a batch of candidate records before committing them to Room.
         * Ensures non-empty dataset, valid start<=end dates, valid clean names (no JSON),
         * valid attribution, and valid region coverage.
         */
        fun validateRecordsForCommit(records: List<FestivalRecord>): Boolean {
            if (records.isEmpty()) return false
            return records.all { rec ->
                val start = rec.startDateOrNull() ?: return@all false
                val end = rec.endDateOrNull() ?: return@all false
                !end.isBefore(start) &&
                    start.year in MIN_VALID_YEAR..MAX_VALID_YEAR &&
                    rec.canonicalName.isNotBlank() &&
                    !looksLikeRawJsonOrCode(rec.canonicalName) &&
                    rec.festivalSlug.isNotBlank() &&
                    rec.sourceName.isNotBlank() &&
                    rec.sourceLicense.isNotBlank() &&
                    (rec.isPanIndia || rec.regions.isNotEmpty())
            }
        }

        /**
         * Computes a stable deterministic dataset digest over canonical festival fields
         * (excluding volatile sync timestamps) so unchanged datasets are detected accurately.
         */
        fun computeDatasetVersion(records: List<FestivalRecord>): String {
            val md = MessageDigest.getInstance("SHA-256")
            records.sortedBy { it.recordKey }.forEach { r ->
                val line = "${r.recordKey}|${r.festivalSlug}|${r.canonicalName}|${r.startDateIso}|${r.endDateIso}|${r.isPanIndia}|${r.regions.map { it.id }.sorted().joinToString(",")}|${r.verificationStatus.id}\n"
                md.update(line.toByteArray(Charsets.UTF_8))
            }
            val hex = md.digest().take(8).joinToString("") { "%02x".format(it) }
            return "v2026-$hex"
        }

        /**
         * Merges verified Panchang records with external public feed entries using source priority,
         * explicit disagreement preservation, and deduplication by stable identity (festival + date + region).
         * Preserves all 82 baseline records and any valid non-expired cached records from prior syncs.
         */
        fun mergeWithPriorityAndDisagreements(
            baseCatalog: List<FestivalRecord>,
            remoteRecords: List<FestivalRecord>,
            existingCachedRecords: List<FestivalRecord> = emptyList(),
            nowEpochMs: Long = System.currentTimeMillis()
        ): List<FestivalRecord> {
            val mergedByKey = linkedMapOf<String, FestivalRecord>()
            val seenIdentities = HashSet<String>()

            baseCatalog.forEach { rec ->
                mergedByKey[rec.recordKey] = rec
                seenIdentities.add(stableFestivalIdentity(rec))
            }

            // Preserve any valid non-expired cached remote records not superseded by base catalog
            for (cached in existingCachedRecords) {
                if (!cached.isExpiredAt(nowEpochMs) &&
                    !looksLikeRawJsonOrCode(cached.canonicalName) &&
                    !mergedByKey.containsKey(cached.recordKey) &&
                    seenIdentities.add(stableFestivalIdentity(cached))
                ) {
                    mergedByKey[cached.recordKey] = cached
                }
            }

            val baseBySlugYear = baseCatalog.groupBy { rec ->
                "${rec.festivalSlug}_${rec.startDateIso.take(4)}"
            }

            for (remote in remoteRecords) {
                if (looksLikeRawJsonOrCode(remote.canonicalName)) continue

                val remoteIdentity = stableFestivalIdentity(remote)
                if (remoteIdentity in seenIdentities) {
                    // Exact same festival + date + region already present
                    continue
                }

                val slugYear = "${remote.festivalSlug}_${remote.startDateIso.take(4)}"
                val existingSameFestival = baseBySlugYear[slugYear].orEmpty()

                val exactMatch = existingSameFestival.firstOrNull {
                    it.startDateIso == remote.startDateIso &&
                        (it.isPanIndia == remote.isPanIndia || it.regions == remote.regions)
                }
                if (exactMatch != null) {
                    continue
                }

                val conflictingDateBase = existingSameFestival.firstOrNull {
                    it.startDateIso != remote.startDateIso &&
                        (it.regions == remote.regions || (it.isPanIndia && remote.isPanIndia))
                }
                if (conflictingDateBase != null) {
                    val note = "Calendar variation preserved: ${conflictingDateBase.sourceName} lists ${conflictingDateBase.startDateIso}; " +
                        "${remote.sourceName} lists ${remote.startDateIso}."
                    val existingNote = conflictingDateBase.disagreementNote
                    val combinedNote = when {
                        existingNote.isNullOrBlank() -> note
                        existingNote.contains(remote.startDateIso) -> existingNote
                        else -> "$existingNote $note"
                    }
                    mergedByKey[conflictingDateBase.recordKey] = conflictingDateBase.copy(
                        verificationStatus = FestivalVerificationStatus.DISAGREEMENT_PRESERVED,
                        disagreementNote = combinedNote
                    )
                    mergedByKey[remote.recordKey] = remote.copy(
                        verificationStatus = FestivalVerificationStatus.DISAGREEMENT_PRESERVED,
                        disagreementNote = note
                    )
                    seenIdentities.add(remoteIdentity)
                } else {
                    mergedByKey[remote.recordKey] = remote
                    seenIdentities.add(remoteIdentity)
                }
            }

            return mergedByKey.values.toList()
        }
    }

    override fun getVerifiedBaseCatalog(nowEpochMs: Long): List<FestivalRecord> =
        PanIndiaFestivalCatalog.verifiedSeedRecords(nowEpochMs = nowEpochMs)

    override suspend fun fetchLatestCatalog(
        nowEpochMs: Long,
        ifNoneMatch: String?,
        ifModifiedSince: String?,
        existingCachedRecords: List<FestivalRecord>
    ): Result<FestivalFeedFetchResult> = runCatching {
        val response = conditionalTransport.get(
            url = MIT_FESTIVALS_LATEST_URL,
            ifNoneMatch = ifNoneMatch,
            ifModifiedSince = ifModifiedSince
        )

        validateHttpResponse(response)

        if (response.statusCode == 304) {
            return@runCatching FestivalFeedFetchResult(
                records = existingCachedRecords,
                sourceSummary = "IMD Rashtriya Panchang + Public Hindu Festivals API (MIT)",
                sourceAttribution = FULL_SOURCE_ATTRIBUTION,
                datasetVersion = computeDatasetVersion(existingCachedRecords.ifEmpty { getVerifiedBaseCatalog(nowEpochMs) }),
                fetchedRemoteCount = 0,
                rejectedRemoteCount = 0,
                collectionsFound = emptyList(),
                httpStatusCode = 304,
                contentType = response.contentType,
                etag = response.etag ?: ifNoneMatch,
                lastModified = response.lastModified ?: ifModifiedSince,
                isNotModified = true
            )
        }

        val body = response.body?.takeIf { it.isNotBlank() }
            ?: throw FestivalInvalidResponseException(
                reasonCode = "EMPTY_RESPONSE",
                message = "Empty festival calendar response body"
            )

        val parseSummary = parsePublicCalendarPayload(rawJson = body, nowEpochMs = nowEpochMs)

        val base = getVerifiedBaseCatalog(nowEpochMs)
        val merged = mergeWithPriorityAndDisagreements(
            baseCatalog = base,
            remoteRecords = parseSummary.records,
            existingCachedRecords = existingCachedRecords,
            nowEpochMs = nowEpochMs
        )
        if (!validateRecordsForCommit(merged)) {
            throw FestivalInvalidResponseException(
                reasonCode = "COMMIT_VALIDATION_FAILED",
                message = "Merged festival calendar failed integrity validation"
            )
        }

        FestivalFeedFetchResult(
            records = merged,
            sourceSummary = "IMD Rashtriya Panchang + Public Hindu Festivals API (MIT)",
            sourceAttribution = FULL_SOURCE_ATTRIBUTION,
            datasetVersion = computeDatasetVersion(merged),
            fetchedRemoteCount = parseSummary.validCount,
            rejectedRemoteCount = parseSummary.rejectedCount,
            collectionsFound = parseSummary.collectionsFound,
            httpStatusCode = response.statusCode,
            contentType = response.contentType,
            etag = response.etag,
            lastModified = response.lastModified,
            isNotModified = false
        )
    }
}
