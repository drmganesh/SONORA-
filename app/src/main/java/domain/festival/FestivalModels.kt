package domain.festival

import java.time.LocalDate

/**
 * All 28 Indian States + 8 Union Territories + All India option.
 * Selected manually by the user — zero GPS, zero device location, zero permissions.
 */
enum class IndianFestivalRegion(
    val id: String,
    val displayName: String,
    val shortCode: String,
    val isUnionTerritory: Boolean = false
) {
    ALL_INDIA("all_india", "All India", "IN"),
    ANDHRA_PRADESH("andhra_pradesh", "Andhra Pradesh", "AP"),
    ARUNACHAL_PRADESH("arunachal_pradesh", "Arunachal Pradesh", "AR"),
    ASSAM("assam", "Assam", "AS"),
    BIHAR("bihar", "Bihar", "BR"),
    CHHATTISGARH("chhattisgarh", "Chhattisgarh", "CG"),
    GOA("goa", "Goa", "GA"),
    GUJARAT("gujarat", "Gujarat", "GJ"),
    HARYANA("haryana", "Haryana", "HR"),
    HIMACHAL_PRADESH("himachal_pradesh", "Himachal Pradesh", "HP"),
    JHARKHAND("jharkhand", "Jharkhand", "JH"),
    KARNATAKA("karnataka", "Karnataka", "KA"),
    KERALA("kerala", "Kerala", "KL"),
    MADHYA_PRADESH("madhya_pradesh", "Madhya Pradesh", "MP"),
    MAHARASHTRA("maharashtra", "Maharashtra", "MH"),
    MANIPUR("manipur", "Manipur", "MN"),
    MEGHALAYA("meghalaya", "Meghalaya", "ML"),
    MIZORAM("mizoram", "Mizoram", "MZ"),
    NAGALAND("nagaland", "Nagaland", "NL"),
    ODISHA("odisha", "Odisha", "OD"),
    PUNJAB("punjab", "Punjab", "PB"),
    RAJASTHAN("rajasthan", "Rajasthan", "RJ"),
    SIKKIM("sikkim", "Sikkim", "SK"),
    TAMIL_NADU("tamil_nadu", "Tamil Nadu", "TN"),
    TELANGANA("telangana", "Telangana", "TS"),
    TRIPURA("tripura", "Tripura", "TR"),
    UTTAR_PRADESH("uttar_pradesh", "Uttar Pradesh", "UP"),
    UTTARAKHAND("uttarakhand", "Uttarakhand", "UK"),
    WEST_BENGAL("west_bengal", "West Bengal", "WB"),
    ANDAMAN_NICOBAR("andaman_nicobar", "Andaman & Nicobar Islands", "AN", isUnionTerritory = true),
    CHANDIGARH("chandigarh", "Chandigarh", "CH", isUnionTerritory = true),
    DADRA_NAGAR_HAVELI_DAMAN_DIU("dnh_dd", "Dadra & Nagar Haveli and Daman & Diu", "DN", isUnionTerritory = true),
    DELHI("delhi", "Delhi (NCT)", "DL", isUnionTerritory = true),
    JAMMU_KASHMIR("jammu_kashmir", "Jammu & Kashmir", "JK", isUnionTerritory = true),
    LADAKH("ladakh", "Ladakh", "LA", isUnionTerritory = true),
    LAKSHADWEEP("lakshadweep", "Lakshadweep", "LD", isUnionTerritory = true),
    PUDUCHERRY("puducherry", "Puducherry", "PY", isUnionTerritory = true);

    companion object {
        val DEFAULT = ALL_INDIA

        val STATES_AND_UTS: List<IndianFestivalRegion> =
            entries.filter { it != ALL_INDIA }

        fun fromId(raw: String?): IndianFestivalRegion {
            if (raw.isNullOrBlank()) return DEFAULT
            val normalized = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
            return entries.firstOrNull {
                it.id == normalized ||
                    it.shortCode.equals(raw.trim(), ignoreCase = true) ||
                    it.displayName.equals(raw.trim(), ignoreCase = true)
            } ?: when (normalized) {
                "national", "india", "pan_india" -> ALL_INDIA
                "orissa" -> ODISHA
                "pondicherry" -> PUDUCHERRY
                "uttaranchal" -> UTTARAKHAND
                else -> DEFAULT
            }
        }
    }
}

enum class FestivalRegionMode(val id: String, val label: String, val subtitle: String) {
    ALL_INDIA("all_india", "All India", "Major Pan-India festivals (optional regional observances)"),
    SPECIFIC_STATE("specific_state", "Selected State / UT", "Festivals & regional dates for one chosen state or UT"),
    ALL_INDIA_PLUS_STATE("all_india_plus_state", "All India + Preferred State", "Pan-India festivals combined with your home state's regional calendar");

    companion object {
        val DEFAULT = ALL_INDIA
        fun fromId(raw: String?): FestivalRegionMode =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class FestivalIntensityLevel(val id: String, val label: String, val opacityMultiplier: Float, val speedMultiplier: Float) {
    LOW("low", "Low", 0.72f, 0.78f),
    MEDIUM("medium", "Medium", 0.88f, 1.0f),
    HIGH("high", "High", 1.0f, 1.18f);

    companion object {
        val DEFAULT = LOW
        fun fromId(raw: String?): FestivalIntensityLevel =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Particle upper limits matching SONORA battery guidelines:
 * Low: 8–15, Medium: 15–25, High: 25–40.
 */
enum class FestivalDensityLevel(val id: String, val label: String, val minParticles: Int, val maxParticles: Int) {
    LOW("low", "Low", 8, 14),
    MEDIUM("medium", "Medium", 15, 24),
    HIGH("high", "High", 25, 38);

    companion object {
        val DEFAULT = LOW
        fun fromId(raw: String?): FestivalDensityLevel =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class FestivalCategory(val id: String, val label: String) {
    MAJOR_PAN_INDIA("major_pan_india", "Major Hindu Festival"),
    REGIONAL_STATE("regional_state", "Regional Festival"),
    HARVEST_SOLAR("harvest_solar", "Harvest & Solar Observance"),
    DEVOTIONAL_VRAT("devotional_vrat", "Devotional & Sacred Day"),
    CULTURAL_TRADITION("cultural_tradition", "Cultural Celebration");

    companion object {
        fun fromId(raw: String?): FestivalCategory =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: MAJOR_PAN_INDIA
    }
}

enum class FestivalVerificationStatus(val id: String, val label: String) {
    OFFICIAL_PANCHANG("official_panchang", "Verified • Rashtriya Panchang"),
    REGIONAL_PANCHANG("regional_panchang", "Verified • Regional Panchang"),
    PUBLIC_FEED_VERIFIED("public_feed_verified", "Verified • Public Calendar Feed"),
    DISAGREEMENT_PRESERVED("disagreement_preserved", "Regional Date Variation Preserved");

    companion object {
        fun fromId(raw: String?): FestivalVerificationStatus =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: PUBLIC_FEED_VERIFIED
    }
}

/**
 * Source priority hierarchy:
 * Official Panchang > Regional Panchang > Verified MIT JSON feed > CC BY 4.0 CSV feed.
 */
enum class FestivalSourcePriority(val rank: Int, val label: String) {
    RASHTRIYA_PANCHANG(100, "IMD / PAC Rashtriya Panchang"),
    REGIONAL_PANCHANG(85, "Regional State Panchang"),
    PUBLIC_MIT_JSON(70, "Public Hindu Festivals API (MIT)"),
    PUBLIC_CC_BY_CSV(60, "Open Panchang Dataset (CC BY 4.0)");

    companion object {
        fun fromRank(rank: Int): FestivalSourcePriority =
            entries.firstOrNull { it.rank == rank } ?: PUBLIC_MIT_JSON
    }
}

/**
 * Rule controlling how calendar disagreements between two trusted sources are handled.
 */
enum class RegionalObservanceRule(val id: String, val label: String) {
    PREFER_REGIONAL_PANCHANG("prefer_regional", "Prefer Selected State Panchang"),
    PREFER_OFFICIAL_PANCHANG("prefer_official", "Prefer National Rashtriya Panchang");

    companion object {
        val DEFAULT = PREFER_REGIONAL_PANCHANG
        fun fromId(raw: String?): RegionalObservanceRule =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Extensible visual effect IDs mapped through [FestivalEffectRegistry].
 */
enum class FestivalEffectId(val id: String, val displayName: String) {
    DIWALI_FIREWORKS("diwali_fireworks", "Diwali Fireworks & Diyas"),
    KARTIK_BOITA_BANDANA("kartik_boita_bandana", "Kartik Purnima Boita & Floating Lamps"),
    DUSSEHRA_RAMA_ARROW("dussehra_rama_arrow", "Dussehra Sacred Arrow & Flame"),
    HOLI_GULAL("holi_gulal", "Holi Gulal Color Clouds"),
    GANESH_DIYA_FLOWERS("ganesh_diya_flowers", "Ganesh Chaturthi Flowers & Diyas"),
    DURGA_NAVRATRI_PETALS("durga_navratri_petals", "Durga Puja & Navratri Petals"),
    RATH_YATRA_CHARIOT("rath_yatra_chariot", "Rath Yatra Sacred Chariot"),
    JANMASHTAMI_FLUTE_PEACOCK("janmashtami_flute_peacock", "Janmashtami Flute & Peacock Feathers"),
    SANKRANTI_KITES("sankranti_kites", "Makar Sankranti Drifting Kites"),
    PONGAL_KOLAM_STEAM("pongal_kolam_steam", "Pongal Kolam & Harvest Steam"),
    ONAM_POOKALAM_BOAT("onam_pookalam_boat", "Onam Pookalam & Petals"),
    BIHU_FESTIVE_PATTERN("bihu_festive_pattern", "Bihu Woven Patterns & Blossoms"),
    SHIVARATRI_SACRED_NIGHT("shivaratri_sacred_night", "Maha Shivaratri Sacred Night Glow"),
    SPRING_BLOOM_PETALS("spring_bloom_petals", "Spring Blossoms & Festive Petals"),
    RIVER_DIYA_GANGA("river_diya_ganga", "Sacred River Diyas & Ripples"),
    GOLDEN_AARTI_LAMPS("golden_aarti_lamps", "Sacred Aarti Lamps & Sparkles"),
    NONE("none", "None");

    companion object {
        fun fromId(raw: String?): FestivalEffectId =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: GOLDEN_AARTI_LAMPS
    }
}

/**
 * Structured festival record cached locally in Room and resolved by [FestivalDateResolver].
 */
data class FestivalRecord(
    val recordKey: String,
    val festivalSlug: String,
    val canonicalName: String,
    val alternateRegionalNames: List<String>,
    val startDateIso: String,
    val endDateIso: String,
    val tithi: String?,
    val regions: Set<IndianFestivalRegion>,
    val isPanIndia: Boolean,
    val category: FestivalCategory,
    val shortDescription: String,
    val effectId: FestivalEffectId,
    val iconGlyph: String,
    val sourceName: String,
    val sourceLicense: String,
    val sourcePriority: FestivalSourcePriority,
    val verificationStatus: FestivalVerificationStatus,
    val disagreementNote: String? = null,
    val lastUpdatedEpochMs: Long,
    val expiresAtEpochMs: Long
) {
    fun startDateOrNull(): LocalDate? = runCatching { LocalDate.parse(startDateIso) }.getOrNull()
    fun endDateOrNull(): LocalDate? = runCatching { LocalDate.parse(endDateIso) }.getOrNull()

    fun isActiveOn(date: LocalDate): Boolean {
        val start = startDateOrNull() ?: return false
        val end = endDateOrNull() ?: start
        return !date.isBefore(start) && !date.isAfter(end)
    }

    fun isExpiredAt(nowEpochMs: Long): Boolean = expiresAtEpochMs in 1L until nowEpochMs

    fun displayRegionSummary(): String {
        if (isPanIndia && regions.isEmpty()) return "All India"
        if (isPanIndia && regions.size >= 4) return "Pan-India & Regional"
        val names = regions.filter { it != IndianFestivalRegion.ALL_INDIA }.map { it.displayName }
        return when {
            names.isEmpty() -> "All India"
            names.size <= 2 -> names.joinToString(" • ")
            else -> "${names.take(2).joinToString(", ")} +${names.size - 2} more"
        }
    }
}

/**
 * Resolved active festival occurrence ready for UI indicator, details bottom sheet, and animation engine.
 */
data class ResolvedFestivalOccurrence(
    val record: FestivalRecord,
    val displayNameForRegion: String,
    val dayIndex: Int,
    val totalDays: Int,
    val relevanceScore: Int
) {
    val isMultiDay: Boolean get() = totalDays > 1

    val indicatorLabel: String
        get() = when {
            isMultiDay -> "$displayNameForRegion • Day $dayIndex/$totalDays"
            else -> displayNameForRegion
        }
}

/**
 * Complete resolution result for a given date and region configuration.
 */
data class ActiveFestivalResolution(
    val dateIso: String,
    val primary: ResolvedFestivalOccurrence?,
    val allActive: List<ResolvedFestivalOccurrence>,
    val lastUpdatedEpochMs: Long,
    val isFromOfflineCache: Boolean,
    val isCalendarExpired: Boolean
) {
    val hasActiveFestival: Boolean get() = primary != null
    val additionalCount: Int get() = (allActive.size - 1).coerceAtLeast(0)

    companion object {
        val EMPTY = ActiveFestivalResolution(
            dateIso = "",
            primary = null,
            allActive = emptyList(),
            lastUpdatedEpochMs = 0L,
            isFromOfflineCache = false,
            isCalendarExpired = false
        )
    }
}

sealed class FestivalSurfaceRole {
    data object HomeBackground : FestivalSurfaceRole()
    data object FullscreenPlayer : FestivalSurfaceRole()
    data object SettingsPreview : FestivalSurfaceRole()
}
