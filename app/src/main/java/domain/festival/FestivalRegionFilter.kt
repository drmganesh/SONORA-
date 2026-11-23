package domain.festival

/**
 * Pure, deterministic region filter for Pan-India and regional state/UT observances.
 * Uses only the user's manually selected region — never device GPS or location APIs.
 */
object FestivalRegionFilter {

    /**
     * Returns true if [record] applies under the user's selected region mode and state/UT.
     *
     * Rules:
     * - [FestivalRegionMode.ALL_INDIA]: includes Pan-India festivals; includes regional-only festivals
     *   ONLY when [includeRegionalInAllIndia] is true.
     * - [FestivalRegionMode.SPECIFIC_STATE]: includes Pan-India festivals AND festivals explicitly
     *   covering [selectedState]. Excludes festivals specific to other states.
     * - [FestivalRegionMode.ALL_INDIA_PLUS_STATE]: includes all Pan-India festivals AND all regional
     *   festivals covering [selectedState] (giving regional date variants for [selectedState] priority).
     */
    fun matches(
        record: FestivalRecord,
        regionMode: FestivalRegionMode,
        selectedState: IndianFestivalRegion,
        includeRegionalInAllIndia: Boolean
    ): Boolean {
        val effectiveState = selectedState.takeIf { it != IndianFestivalRegion.ALL_INDIA }
        val targetsAllIndia = record.isPanIndia || record.regions.contains(IndianFestivalRegion.ALL_INDIA)

        return when (regionMode) {
            FestivalRegionMode.ALL_INDIA -> {
                if (targetsAllIndia) true else includeRegionalInAllIndia
            }
            FestivalRegionMode.SPECIFIC_STATE -> {
                if (effectiveState == null) {
                    targetsAllIndia || includeRegionalInAllIndia
                } else {
                    record.regions.contains(effectiveState) ||
                        (targetsAllIndia && record.regions.none { it != IndianFestivalRegion.ALL_INDIA })
                }
            }
            FestivalRegionMode.ALL_INDIA_PLUS_STATE -> {
                if (effectiveState == null) {
                    targetsAllIndia || includeRegionalInAllIndia
                } else {
                    record.regions.contains(effectiveState) || targetsAllIndia
                }
            }
        }
    }

    /**
     * Returns true if [record] is a dedicated regional variant specifically authored for [state].
     */
    fun isStateSpecificVariant(record: FestivalRecord, state: IndianFestivalRegion): Boolean {
        if (state == IndianFestivalRegion.ALL_INDIA) return false
        return record.regions.contains(state)
    }
}
