package data.festival

import domain.festival.FestivalCategory
import domain.festival.FestivalRecord
import domain.festival.FestivalSourcePriority
import domain.festival.FestivalVerificationStatus
import domain.festival.IndianFestivalRegion
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

fun interface FestivalHttpTransport {
    @Throws(IOException::class)
    fun get(url: String): String
}

data class FestivalFeedFetchResult(
    val records: List<FestivalRecord>,
    val sourceSummary: String,
    val fetchedRemoteCount: Int
)

interface FestivalCalendarDataSource {
    suspend fun fetchLatestCatalog(nowEpochMs: Long): Result<FestivalFeedFetchResult>
    fun getVerifiedBaseCatalog(nowEpochMs: Long): List<FestivalRecord>
}

/**
 * Public Festival Calendar DataSource integrating:
 * 1. Verified Pan-India & Regional Rashtriya Panchang catalog (Priority 100/85)
 * 2. MIT-licensed `ganeshsp1/hindu-festivals-api` public JSON feed via jsDelivr CDN (Priority 70)
 *
 * Source-priority & disagreement rules:
 * - Official Rashtriya Panchang & Regional State Panchang entries always retain higher priority.
 * - If the public MIT feed reports a different date for an already-known festival slug, the
 *   disagreement is preserved explicitly in `disagreementNote` and verification status rather than
 *   silently overwriting or inventing dates.
 * - New regional/state festivals present in the public feed are ingested cleanly so coverage
 *   extends beyond any hardcoded list.
 */
class PublicFestivalCalendarDataSource(
    private val transport: FestivalHttpTransport = defaultTransport()
) : FestivalCalendarDataSource {

    companion object {
        const val MIT_FESTIVALS_LATEST_URL =
            "https://cdn.jsdelivr.net/gh/ganeshsp1/hindu-festivals-api/data/latest.json"
        const val MIT_FEED_SOURCE_NAME = "ganeshsp1/hindu-festivals-api (jsDelivr)"
        const val MIT_FEED_LICENSE = "MIT License"
        private const val REMOTE_RECORD_TTL_MS = 180L * 24L * 60L * 60L * 1000L // 180 days

        private fun defaultTransport(): FestivalHttpTransport {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(15, TimeUnit.SECONDS)
                .build()
            return FestivalHttpTransport { url ->
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("User-Agent", "SONORA-Android/${com.example.BuildConfig.VERSION_NAME}")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Festival feed HTTP ${response.code}")
                    }
                    response.body?.string() ?: throw IOException("Empty festival feed body")
                }
            }
        }

        /**
         * Parses `ganeshsp1/hindu-festivals-api` JSON array format:
         * `[{ "date": "2026-03-19", "name": "Ugadi, Gudi Padwa", "state_proxy": "maharashtra", "month": "march" }]`
         */
        fun parseMitFestivalsJson(
            rawJson: String,
            nowEpochMs: Long
        ): List<FestivalRecord> {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) return emptyList()

            val array = JSONArray(trimmed)
            val parsed = mutableListOf<FestivalRecord>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val dateIso = obj.optString("date").trim()
                val rawName = obj.optString("name").trim()
                val stateProxy = obj.optString("state_proxy").trim()

                // Strictly validate Gregorian date — never invent dates
                val validDate = runCatching { LocalDate.parse(dateIso) }.getOrNull() ?: continue
                if (rawName.isBlank()) continue

                val parts = rawName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val primaryName = parts.firstOrNull() ?: rawName
                val altNames = parts.drop(1)

                val mappedRegion = IndianFestivalRegion.fromId(stateProxy)
                val isNational = stateProxy.isBlank() ||
                    stateProxy.equals("national", ignoreCase = true) ||
                    mappedRegion == IndianFestivalRegion.ALL_INDIA

                val (slug, effectId, glyph) = PanIndiaFestivalCatalog.inferEffectAndGlyph(primaryName)
                val regionSet = if (isNational) emptySet() else setOf(mappedRegion)
                val keySuffix = if (isNational) "mit_pan" else "mit_${mappedRegion.shortCode.lowercase()}"

                parsed += FestivalRecord(
                    recordKey = "${slug}_${validDate}_$keySuffix",
                    festivalSlug = slug,
                    canonicalName = primaryName,
                    alternateRegionalNames = altNames,
                    startDateIso = validDate.toString(),
                    endDateIso = validDate.toString(),
                    tithi = null,
                    regions = regionSet,
                    isPanIndia = isNational,
                    category = if (isNational) FestivalCategory.MAJOR_PAN_INDIA else FestivalCategory.REGIONAL_STATE,
                    shortDescription = "Public Hindu festival observance" +
                        if (!isNational) " (${mappedRegion.displayName} regional calendar)." else " across India.",
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
            return parsed
        }

        /**
         * Merges verified Panchang records with external public feed entries using source priority
         * and explicit disagreement preservation (never silently discarding a calendar difference).
         */
        fun mergeWithPriorityAndDisagreements(
            baseCatalog: List<FestivalRecord>,
            remoteRecords: List<FestivalRecord>
        ): List<FestivalRecord> {
            val mergedByKey = linkedMapOf<String, FestivalRecord>()
            baseCatalog.forEach { mergedByKey[it.recordKey] = it }

            val baseBySlugYear = baseCatalog.groupBy { rec ->
                "${rec.festivalSlug}_${rec.startDateIso.take(4)}"
            }

            for (remote in remoteRecords) {
                val slugYear = "${remote.festivalSlug}_${remote.startDateIso.take(4)}"
                val existingSameFestival = baseBySlugYear[slugYear].orEmpty()

                val exactMatch = existingSameFestival.firstOrNull {
                    it.startDateIso == remote.startDateIso &&
                        (it.isPanIndia == remote.isPanIndia || it.regions == remote.regions)
                }
                if (exactMatch != null) {
                    // Already verified at higher priority (Rashtriya / Regional Panchang); keep authoritative record
                    continue
                }

                val conflictingDateBase = existingSameFestival.firstOrNull {
                    it.startDateIso != remote.startDateIso &&
                        (it.regions == remote.regions || (it.isPanIndia && remote.isPanIndia))
                }
                if (conflictingDateBase != null) {
                    val note = "Calendar variation preserved: ${conflictingDateBase.sourceName} lists ${conflictingDateBase.startDateIso}; " +
                        "${remote.sourceName} lists ${remote.startDateIso}."
                    // Annotate the authoritative record AND preserve the regional alternate record
                    mergedByKey[conflictingDateBase.recordKey] = conflictingDateBase.copy(
                        verificationStatus = FestivalVerificationStatus.DISAGREEMENT_PRESERVED,
                        disagreementNote = conflictingDateBase.disagreementNote?.let { "$it $note" } ?: note
                    )
                    mergedByKey[remote.recordKey] = remote.copy(
                        verificationStatus = FestivalVerificationStatus.DISAGREEMENT_PRESERVED,
                        disagreementNote = note
                    )
                } else {
                    // Extensible new festival or regional observance from public feed
                    mergedByKey[remote.recordKey] = remote
                }
            }

            return mergedByKey.values.toList()
        }
    }

    override fun getVerifiedBaseCatalog(nowEpochMs: Long): List<FestivalRecord> =
        PanIndiaFestivalCatalog.verifiedSeedRecords(nowEpochMs = nowEpochMs)

    override suspend fun fetchLatestCatalog(nowEpochMs: Long): Result<FestivalFeedFetchResult> =
        runCatching {
            val base = getVerifiedBaseCatalog(nowEpochMs)
            val rawJson = transport.get(MIT_FESTIVALS_LATEST_URL)
            val remoteRecords = parseMitFestivalsJson(rawJson = rawJson, nowEpochMs = nowEpochMs)
            val merged = mergeWithPriorityAndDisagreements(baseCatalog = base, remoteRecords = remoteRecords)
            FestivalFeedFetchResult(
                records = merged,
                sourceSummary = "IMD Rashtriya Panchang + Public Hindu Festivals API (MIT)",
                fetchedRemoteCount = remoteRecords.size
            )
        }
}
