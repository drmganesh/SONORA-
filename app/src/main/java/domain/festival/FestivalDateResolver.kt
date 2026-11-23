package domain.festival

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Resolves active Hindu festivals for a specific Gregorian date and user-selected region.
 *
 * Handles:
 * - Multi-day festival spans (e.g., Navratri, Durga Puja, Raja Parba, Rath Yatra, Pongal, Onam)
 * - Regional date variations (never assumes every state celebrates on the same day)
 * - Source priority & calendar disagreement preservation (Rashtriya Panchang vs Regional Panchang)
 * - Deterministic ranking when multiple festivals fall on the same date
 * - Never fabricates festivals when cached calendar data is missing or expired
 */
object FestivalDateResolver {

    fun resolveForDate(
        records: List<FestivalRecord>,
        date: LocalDate,
        regionMode: FestivalRegionMode,
        selectedState: IndianFestivalRegion,
        includeRegionalInAllIndia: Boolean,
        observanceRule: RegionalObservanceRule = RegionalObservanceRule.DEFAULT,
        nowEpochMs: Long = System.currentTimeMillis(),
        lastUpdatedEpochMs: Long = 0L,
        isFromOfflineCache: Boolean = false
    ): ActiveFestivalResolution {
        if (records.isEmpty()) {
            return ActiveFestivalResolution(
                dateIso = date.toString(),
                primary = null,
                allActive = emptyList(),
                lastUpdatedEpochMs = lastUpdatedEpochMs,
                isFromOfflineCache = isFromOfflineCache,
                isCalendarExpired = false
            )
        }

        val nonExpiredRecords = records.filterNot { it.isExpiredAt(nowEpochMs) }
        val isAllExpired = nonExpiredRecords.isEmpty()
        if (isAllExpired) {
            return ActiveFestivalResolution(
                dateIso = date.toString(),
                primary = null,
                allActive = emptyList(),
                lastUpdatedEpochMs = lastUpdatedEpochMs,
                isFromOfflineCache = isFromOfflineCache,
                isCalendarExpired = true
            )
        }

        // Group by canonical festivalSlug so regional date overrides for the selected state
        // cleanly supersede generic pan-India dates when that state observes a different date!
        val groupedBySlug = nonExpiredRecords.groupBy { it.festivalSlug }
        val resolvedOccurrences = mutableListOf<ResolvedFestivalOccurrence>()
        val activeState = selectedState.takeIf { it != IndianFestivalRegion.ALL_INDIA }

        for ((_, variants) in groupedBySlug) {
            val selectedVariant = pickVariantForRegion(
                variants = variants,
                regionMode = regionMode,
                selectedState = activeState,
                includeRegionalInAllIndia = includeRegionalInAllIndia,
                observanceRule = observanceRule
            ) ?: continue

            if (!selectedVariant.isActiveOn(date)) continue

            val start = selectedVariant.startDateOrNull() ?: continue
            val end = selectedVariant.endDateOrNull() ?: start
            val totalDays = (ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
            val dayIndex = (ChronoUnit.DAYS.between(start, date).toInt() + 1).coerceIn(1, totalDays)

            val regionalDisplayName = resolveRegionalDisplayName(
                record = selectedVariant,
                selectedState = activeState
            )

            val relevance = computeRelevanceScore(
                record = selectedVariant,
                selectedState = activeState,
                regionMode = regionMode,
                totalDays = totalDays
            )

            resolvedOccurrences += ResolvedFestivalOccurrence(
                record = selectedVariant,
                displayNameForRegion = regionalDisplayName,
                dayIndex = dayIndex,
                totalDays = totalDays,
                relevanceScore = relevance
            )
        }

        val sorted = resolvedOccurrences.sortedWith(
            compareByDescending<ResolvedFestivalOccurrence> { it.relevanceScore }
                .thenByDescending { it.record.sourcePriority.rank }
                .thenBy { it.displayNameForRegion }
        )

        return ActiveFestivalResolution(
            dateIso = date.toString(),
            primary = sorted.firstOrNull(),
            allActive = sorted,
            lastUpdatedEpochMs = lastUpdatedEpochMs,
            isFromOfflineCache = isFromOfflineCache,
            isCalendarExpired = false
        )
    }

    private fun pickVariantForRegion(
        variants: List<FestivalRecord>,
        regionMode: FestivalRegionMode,
        selectedState: IndianFestivalRegion?,
        includeRegionalInAllIndia: Boolean,
        observanceRule: RegionalObservanceRule
    ): FestivalRecord? {
        val eligible = variants.filter { record ->
            FestivalRegionFilter.matches(
                record = record,
                regionMode = regionMode,
                selectedState = selectedState ?: IndianFestivalRegion.ALL_INDIA,
                includeRegionalInAllIndia = includeRegionalInAllIndia
            )
        }
        if (eligible.isEmpty()) return null

        // If the user selected a specific state or All India + Preferred State, check whether
        // there is an explicit regional date record for that state (e.g. Odisha Dola Purnima date vs North India Holi).
        if (selectedState != null && regionMode != FestivalRegionMode.ALL_INDIA) {
            val stateSpecific = eligible.filter { it.regions.contains(selectedState) && !it.isPanIndia }
            val stateTagged = eligible.filter { it.regions.contains(selectedState) }

            val preferredPool = when (observanceRule) {
                RegionalObservanceRule.PREFER_REGIONAL_PANCHANG ->
                    stateSpecific.ifEmpty { stateTagged.ifEmpty { eligible } }
                RegionalObservanceRule.PREFER_OFFICIAL_PANCHANG ->
                    eligible.filter { it.sourcePriority == FestivalSourcePriority.RASHTRIYA_PANCHANG }
                        .ifEmpty { stateTagged.ifEmpty { eligible } }
            }
            return preferredPool.maxByOrNull { it.sourcePriority.rank }
        }

        // In All India mode, prefer official Pan-India record with highest source priority
        val panIndiaRecords = eligible.filter { it.isPanIndia }
        return (panIndiaRecords.ifEmpty { eligible }).maxByOrNull { it.sourcePriority.rank }
    }

    private fun resolveRegionalDisplayName(
        record: FestivalRecord,
        selectedState: IndianFestivalRegion?
    ): String {
        if (selectedState == null || record.alternateRegionalNames.isEmpty()) {
            return record.canonicalName
        }
        // Highlight well-known state-preferred local names while keeping canonical identity clear
        return when (selectedState) {
            IndianFestivalRegion.ODISHA -> when (record.festivalSlug) {
                "kartik_purnima" -> "Kartik Purnima • Boita Bandana"
                "holi" -> "Dola Purnima • Holi"
                else -> record.canonicalName
            }
            IndianFestivalRegion.GUJARAT -> when (record.festivalSlug) {
                "makar_sankranti" -> "Uttarayan"
                else -> record.canonicalName
            }
            IndianFestivalRegion.MAHARASHTRA -> when (record.festivalSlug) {
                "krishna_janmashtami" -> "Gokulashtami"
                else -> record.canonicalName
            }
            IndianFestivalRegion.WEST_BENGAL -> when (record.festivalSlug) {
                "vasant_panchami" -> "Saraswati Puja"
                "diwali" -> "Kali Puja • Diwali"
                else -> record.canonicalName
            }
            IndianFestivalRegion.KARNATAKA -> when (record.festivalSlug) {
                "dussehra" -> "Mysuru Dasara"
                else -> record.canonicalName
            }
            IndianFestivalRegion.ANDHRA_PRADESH, IndianFestivalRegion.TELANGANA -> when (record.festivalSlug) {
                "makar_sankranti" -> "Sankranti"
                else -> record.canonicalName
            }
            IndianFestivalRegion.JAMMU_KASHMIR -> when (record.festivalSlug) {
                "maha_shivaratri" -> "Herath • Maha Shivaratri"
                else -> record.canonicalName
            }
            else -> record.canonicalName
        }
    }

    private fun computeRelevanceScore(
        record: FestivalRecord,
        selectedState: IndianFestivalRegion?,
        regionMode: FestivalRegionMode,
        totalDays: Int
    ): Int {
        var score = 0
        // Dedicated regional match for the user's selected state gets top prominence
        if (selectedState != null && regionMode != FestivalRegionMode.ALL_INDIA &&
            record.regions.contains(selectedState)
        ) {
            score += if (!record.isPanIndia) 45 else 25
        }
        score += when (record.category) {
            FestivalCategory.MAJOR_PAN_INDIA -> 50
            FestivalCategory.REGIONAL_STATE -> 38
            FestivalCategory.HARVEST_SOLAR -> 34
            FestivalCategory.DEVOTIONAL_VRAT -> 26
            FestivalCategory.CULTURAL_TRADITION -> 22
        }
        // Single-day peak festivals (e.g., Vijayadashami/Dussehra on Dashami day) rank above
        // trailing days of overlapping multi-day windows (e.g., Durga Puja Day 4/4)
        if (totalDays == 1) {
            score += 12
        }
        // Signature bespoke visual effects rank slightly higher on overlapping dates
        if (record.effectId != FestivalEffectId.GOLDEN_AARTI_LAMPS &&
            record.effectId != FestivalEffectId.NONE
        ) {
            score += 10
        }
        return score
    }
}
