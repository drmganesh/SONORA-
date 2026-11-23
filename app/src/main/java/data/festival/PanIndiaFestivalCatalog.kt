package data.festival

import domain.festival.FestivalCategory
import domain.festival.FestivalEffectId
import domain.festival.FestivalRecord
import domain.festival.FestivalSourcePriority
import domain.festival.FestivalVerificationStatus
import domain.festival.IndianFestivalRegion

/**
 * Verified Pan-India & Regional Hindu Festival Catalog (2025–2027) anchored to the
 * India Meteorological Department / Positional Astronomy Centre Rashtriya Panchang
 * (https://packolkata.imd.gov.in/panchang/) and state regional Panchang observances.
 *
 * Covers:
 * - All 36+ Major Pan-India Hindu festivals
 * - Odisha (Raja Parba, Rath Yatra, Snana Purnima, Bahuda Yatra, Suna Besha, Niladri Bije,
 *   Nuakhai, Kumar Purnima, Kartik Purnima / Boita Bandana, Prathamastami, Manabasa Gurubara,
 *   Durga Puja, Kali Puja, Dola Purnima)
 * - West Bengal, Maharashtra, Gujarat, Punjab & North India, Tamil Nadu, Kerala, Karnataka,
 *   Andhra Pradesh & Telangana, Assam & Northeast, plus Bihar, Jharkhand, Rajasthan, Goa,
 *   Himachal Pradesh, Uttarakhand, Jammu & Kashmir, and Union Territories.
 *
 * Preserves regional date differences and documented calendar disagreements rather than
 * assuming all Indian states celebrate every festival on a single date.
 */
object PanIndiaFestivalCatalog {

    private const val SOURCE_RASHTRIYA = "IMD / PAC Rashtriya Panchang"
    private const val SOURCE_ODISHA_PANCHANG = "Odisha Regional Panchang (Panji)"
    private const val SOURCE_REGIONAL_PANCHANG = "Regional State Panchang"
    private const val LICENSE_PUBLIC_GOV = "Public Government Almanac Reference (IMD PAC)"

    /** Catalog validity ceiling (end of 2027) so expired records are never fabricated beyond verified range. */
    const val CATALOG_EXPIRY_EPOCH_MS: Long = 1_830_297_600_000L // 2028-01-01T00:00:00Z
    const val CATALOG_SEED_EPOCH_MS: Long = 1_767_225_600_000L   // 2026-01-01T00:00:00Z

    fun verifiedSeedRecords(nowEpochMs: Long = CATALOG_SEED_EPOCH_MS): List<FestivalRecord> {
        val list = mutableListOf<FestivalRecord>()

        fun add(
            slug: String,
            name: String,
            altNames: List<String> = emptyList(),
            startDate: String,
            endDate: String = startDate,
            tithi: String? = null,
            regions: Set<IndianFestivalRegion> = emptySet(),
            isPanIndia: Boolean = regions.isEmpty(),
            category: FestivalCategory = if (isPanIndia) FestivalCategory.MAJOR_PAN_INDIA else FestivalCategory.REGIONAL_STATE,
            desc: String,
            effect: FestivalEffectId,
            glyph: String,
            source: String = if (isPanIndia) SOURCE_RASHTRIYA else SOURCE_REGIONAL_PANCHANG,
            priority: FestivalSourcePriority = if (isPanIndia) FestivalSourcePriority.RASHTRIYA_PANCHANG else FestivalSourcePriority.REGIONAL_PANCHANG,
            status: FestivalVerificationStatus = if (isPanIndia) FestivalVerificationStatus.OFFICIAL_PANCHANG else FestivalVerificationStatus.REGIONAL_PANCHANG,
            disagreement: String? = null,
            variantSuffix: String = if (isPanIndia) "pan" else regions.firstOrNull()?.shortCode?.lowercase() ?: "reg"
        ) {
            list += FestivalRecord(
                recordKey = "${slug}_${startDate}_$variantSuffix",
                festivalSlug = slug,
                canonicalName = name,
                alternateRegionalNames = altNames,
                startDateIso = startDate,
                endDateIso = endDate,
                tithi = tithi,
                regions = regions,
                isPanIndia = isPanIndia,
                category = category,
                shortDescription = desc,
                effectId = effect,
                iconGlyph = glyph,
                sourceName = source,
                sourceLicense = LICENSE_PUBLIC_GOV,
                sourcePriority = priority,
                verificationStatus = status,
                disagreementNote = disagreement,
                lastUpdatedEpochMs = nowEpochMs,
                expiresAtEpochMs = CATALOG_EXPIRY_EPOCH_MS
            )
        }

        // ==================== 2026 VERIFIED CALENDAR ====================
        // January 2026
        add(
            slug = "lohri", name = "Lohri", altNames = listOf("Lohadi"),
            startDate = "2026-01-13", tithi = "Pausha",
            regions = setOf(IndianFestivalRegion.PUNJAB, IndianFestivalRegion.HARYANA, IndianFestivalRegion.HIMACHAL_PRADESH, IndianFestivalRegion.DELHI, IndianFestivalRegion.CHANDIGARH, IndianFestivalRegion.JAMMU_KASHMIR),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Traditional winter bonfire and harvest celebration marking the passing of the winter solstice.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🔥"
        )
        add(
            slug = "makar_sankranti", name = "Makar Sankranti", altNames = listOf("Uttarayan", "Sankranti", "Maghi", "Makara Sankramana"),
            startDate = "2026-01-14", tithi = "Makara Sankranti (Solar Transit)",
            isPanIndia = true, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Auspicious solar transition into Capricorn celebrated with kite flying, sesame sweets, and sacred river dips.",
            effect = FestivalEffectId.SANKRANTI_KITES, glyph = "🪁"
        )
        add(
            slug = "pongal", name = "Pongal", altNames = listOf("Thai Pongal", "Bhogi", "Mattu Pongal", "Kaanum Pongal"),
            startDate = "2026-01-14", endDate = "2026-01-17", tithi = "Thai 1–4",
            regions = setOf(IndianFestivalRegion.TAMIL_NADU, IndianFestivalRegion.PUDUCHERRY),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Four-day Tamil harvest thanksgiving festival featuring vibrant kolam patterns, sugarcane, and sweet Pongal.",
            effect = FestivalEffectId.PONGAL_KOLAM_STEAM, glyph = "🌾"
        )
        add(
            slug = "magh_bihu", name = "Magh Bihu", altNames = listOf("Bhogali Bihu"),
            startDate = "2026-01-14", endDate = "2026-01-15", tithi = "Magh Sankranti",
            regions = setOf(IndianFestivalRegion.ASSAM, IndianFestivalRegion.ARUNACHAL_PRADESH, IndianFestivalRegion.TRIPURA),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Assamese harvest festival celebrated with community feasts, traditional pitha, and Meji bonfires.",
            effect = FestivalEffectId.BIHU_FESTIVE_PATTERN, glyph = "🌾"
        )
        add(
            slug = "vasant_panchami", name = "Vasant Panchami", altNames = listOf("Saraswati Puja", "Basant Panchami", "Shri Panchami"),
            startDate = "2026-01-23", tithi = "Magha Shukla Panchami",
            isPanIndia = true,
            desc = "Festival heralding spring and honoring Goddess Saraswati, deity of music, learning, and the arts.",
            effect = FestivalEffectId.SPRING_BLOOM_PETALS, glyph = "🌼"
        )
        add(
            slug = "thaipusam", name = "Thaipusam", altNames = listOf("Thai Poosam"),
            startDate = "2026-02-01", tithi = "Thai Pournami • Pushya Nakshatra",
            regions = setOf(IndianFestivalRegion.TAMIL_NADU, IndianFestivalRegion.PUDUCHERRY, IndianFestivalRegion.KERALA),
            isPanIndia = false, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Sacred Tamil festival dedicated to Lord Murugan celebrated with kavadi processions and temple lamps.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )

        // February – March 2026
        add(
            slug = "maha_shivaratri", name = "Maha Shivaratri", altNames = listOf("Herath", "Shivaratri"),
            startDate = "2026-02-15", tithi = "Magha / Phalguna Krishna Chaturdashi",
            isPanIndia = true,
            desc = "The Great Night of Shiva observed with night-long devotion, sacred chants, and temple lamps across India.",
            effect = FestivalEffectId.SHIVARATRI_SACRED_NIGHT, glyph = "🌙"
        )
        add(
            slug = "attukal_pongala", name = "Attukal Pongala",
            startDate = "2026-03-03", tithi = "Kumbham Pooram",
            regions = setOf(IndianFestivalRegion.KERALA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Iconic Thiruvananthapuram temple offering where devotees prepare sweet pongala in earthen pots.",
            effect = FestivalEffectId.PONGAL_KOLAM_STEAM, glyph = "🪔"
        )
        // Regional date variation example: Odisha Dola Purnima (Mar 3) vs Pan-India Holi (Mar 4)
        add(
            slug = "holi", name = "Dola Purnima", altNames = listOf("Dola Jatra", "Odisha Holi"),
            startDate = "2026-03-03", endDate = "2026-03-04", tithi = "Phalguna Purnima",
            regions = setOf(IndianFestivalRegion.ODISHA, IndianFestivalRegion.WEST_BENGAL),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Celebrated in Odisha and Bengal as Dola Purnima / Dol Jatra with palanquin processions and vibrant abira colors.",
            effect = FestivalEffectId.HOLI_GULAL, glyph = "🎨",
            source = SOURCE_ODISHA_PANCHANG,
            status = FestivalVerificationStatus.DISAGREEMENT_PRESERVED,
            disagreement = "Odisha & West Bengal observe Dola Purnima on Mar 3 while North India celebrates Rangwali Holi on Mar 4."
        )
        add(
            slug = "holi", name = "Holi", altNames = listOf("Rangwali Holi", "Dhulandi", "Shigmo", "Yaoshang"),
            startDate = "2026-03-04", tithi = "Phalguna Krishna Pratipada",
            isPanIndia = true,
            desc = "Festival of colors celebrating spring, joy, and the triumph of devotion with soft gulal clouds.",
            effect = FestivalEffectId.HOLI_GULAL, glyph = "🎨",
            disagreement = "Dola Purnima / Holika Dahan falls on Mar 3 in Odisha & West Bengal; Rangwali Holi on Mar 4 Pan-India."
        )
        add(
            slug = "rang_panchami", name = "Rang Panchami",
            startDate = "2026-03-08", tithi = "Chaitra Krishna Panchami",
            regions = setOf(IndianFestivalRegion.MAHARASHTRA, IndianFestivalRegion.MADHYA_PRADESH, IndianFestivalRegion.UTTAR_PRADESH, IndianFestivalRegion.RAJASTHAN),
            isPanIndia = true, category = FestivalCategory.MAJOR_PAN_INDIA,
            desc = "Fifth day after Holi celebrated with festive gulal colors, folk music, and joyous gatherings.",
            effect = FestivalEffectId.HOLI_GULAL, glyph = "🎨"
        )
        add(
            slug = "ugadi", name = "Ugadi", altNames = listOf("Yugadi", "Chandramana Ugadi"),
            startDate = "2026-03-19", tithi = "Chaitra Shukla Pratipada",
            regions = setOf(IndianFestivalRegion.ANDHRA_PRADESH, IndianFestivalRegion.TELANGANA, IndianFestivalRegion.KARNATAKA),
            isPanIndia = true, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Telugu and Kannada New Year welcomed with mango-leaf torans, Ugadi Pachadi, and Panchanga Shravanam.",
            effect = FestivalEffectId.SPRING_BLOOM_PETALS, glyph = "🌼"
        )
        add(
            slug = "gudi_padwa", name = "Gudi Padwa", altNames = listOf("Samvatsar Padvo"),
            startDate = "2026-03-19", tithi = "Chaitra Shukla Pratipada",
            regions = setOf(IndianFestivalRegion.MAHARASHTRA, IndianFestivalRegion.GOA),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Marathi and Konkani New Year marked by raising the festive Gudi banner, rangoli, and neem-jaggery.",
            effect = FestivalEffectId.SPRING_BLOOM_PETALS, glyph = "🌼"
        )
        add(
            slug = "ram_navami", name = "Ram Navami", altNames = listOf("Sri Rama Navami"),
            startDate = "2026-03-26", tithi = "Chaitra Shukla Navami",
            isPanIndia = true,
            desc = "Auspicious birth anniversary of Lord Rama celebrated with devotional bhajans, recitals, and temple lamps.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "panguni_uthiram", name = "Panguni Uthiram",
            startDate = "2026-04-01", tithi = "Panguni Pournami • Uttara Phalguni",
            regions = setOf(IndianFestivalRegion.TAMIL_NADU, IndianFestivalRegion.PUDUCHERRY),
            isPanIndia = false, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Auspicious Tamil temple festival celebrating divine celestial weddings with lamps and floral garlands.",
            effect = FestivalEffectId.GANESH_DIYA_FLOWERS, glyph = "🌺"
        )
        add(
            slug = "hanuman_jayanti", name = "Hanuman Jayanti", altNames = listOf("Hanuman Janmotsav"),
            startDate = "2026-04-02", tithi = "Chaitra Purnima",
            isPanIndia = true,
            desc = "Birth celebration of Lord Hanuman honored with Hanuman Chalisa chants and saffron temple lamps.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "karaga", name = "Bengaluru Karaga",
            startDate = "2026-04-02", tithi = "Chaitra Purnima",
            regions = setOf(IndianFestivalRegion.KARNATAKA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Centuries-old Bengaluru floral procession honoring Goddess Draupadi under the full moon.",
            effect = FestivalEffectId.DURGA_NAVRATRI_PETALS, glyph = "🌺"
        )

        // April 2026 Solar New Years & Harvests
        add(
            slug = "baisakhi", name = "Baisakhi", altNames = listOf("Vaisakhi", "Mesha Sankranti"),
            startDate = "2026-04-14", tithi = "Vaisakha 1 (Solar New Year)",
            regions = setOf(IndianFestivalRegion.PUNJAB, IndianFestivalRegion.HARYANA, IndianFestivalRegion.CHANDIGARH, IndianFestivalRegion.DELHI, IndianFestivalRegion.HIMACHAL_PRADESH, IndianFestivalRegion.UTTARAKHAND),
            isPanIndia = true, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Joyous North Indian spring harvest and solar New Year festival.",
            effect = FestivalEffectId.SPRING_BLOOM_PETALS, glyph = "🌾"
        )
        add(
            slug = "tamil_puthandu", name = "Tamil Puthandu", altNames = listOf("Chithirai Thirunal", "Pana Sankranti"),
            startDate = "2026-04-14", tithi = "Chithirai 1",
            regions = setOf(IndianFestivalRegion.TAMIL_NADU, IndianFestivalRegion.PUDUCHERRY, IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Traditional Tamil New Year (and Maha Vishuba / Pana Sankranti in Odisha) greeted with mango blossoms and kolam.",
            effect = FestivalEffectId.PONGAL_KOLAM_STEAM, glyph = "🌼"
        )
        add(
            slug = "vishu", name = "Vishu",
            startDate = "2026-04-14", tithi = "Medam 1",
            regions = setOf(IndianFestivalRegion.KERALA, IndianFestivalRegion.LAKSHADWEEP),
            isPanIndia = true, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Malayalam astronomical New Year celebrated with golden Kani Konna blossoms and Vishukkani lamps.",
            effect = FestivalEffectId.SPRING_BLOOM_PETALS, glyph = "🌼"
        )
        add(
            slug = "bohag_bihu", name = "Bohag Bihu", altNames = listOf("Rongali Bihu"),
            startDate = "2026-04-14", endDate = "2026-04-20", tithi = "Bohag 1–7",
            regions = setOf(IndianFestivalRegion.ASSAM, IndianFestivalRegion.ARUNACHAL_PRADESH, IndianFestivalRegion.MEGHALAYA, IndianFestivalRegion.MANIPUR, IndianFestivalRegion.TRIPURA),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Assamese New Year and spring festival celebrated with traditional gamusa motifs, kopou orchids, and music.",
            effect = FestivalEffectId.BIHU_FESTIVE_PATTERN, glyph = "🌺"
        )
        add(
            slug = "poila_boishakh", name = "Poila Boishakh", altNames = listOf("Nabo Barsho", "Bengali New Year"),
            startDate = "2026-04-15", tithi = "Boishakh 1",
            regions = setOf(IndianFestivalRegion.WEST_BENGAL, IndianFestivalRegion.TRIPURA),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Bengali New Year welcomed with Prabhat Pheri songs, alpona motifs, and cultural festivities.",
            effect = FestivalEffectId.SPRING_BLOOM_PETALS, glyph = "🌼"
        )
        add(
            slug = "akshaya_tritiya", name = "Akshaya Tritiya", altNames = listOf("Akha Teej"),
            startDate = "2026-04-19", tithi = "Vaishakha Shukla Tritiya",
            isPanIndia = true,
            desc = "Auspicious day of enduring prosperity, gratitude, and sacred new beginnings (also Chandan Yatra start in Odisha).",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "✨"
        )
        add(
            slug = "thrissur_pooram", name = "Thrissur Pooram",
            startDate = "2026-04-26", tithi = "Medam Pooram",
            regions = setOf(IndianFestivalRegion.KERALA),
            isPanIndia = false, category = FestivalCategory.CULTURAL_TRADITION,
            desc = "Grand Kerala temple festival celebrated with percussion ensembles, colorful parasols, and lamps.",
            effect = FestivalEffectId.ONAM_POOKALAM_BOAT, glyph = "🌼"
        )

        // May – July 2026
        add(
            slug = "vat_savitri", name = "Vat Savitri", altNames = listOf("Savitri Brata"),
            startDate = "2026-05-16", tithi = "Jyeshtha Amavasya",
            isPanIndia = true, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Sacred observance honoring devotion and longevity around the banyan tree.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "ganga_dussehra", name = "Ganga Dussehra",
            startDate = "2026-05-26", tithi = "Jyeshtha Shukla Dashami",
            isPanIndia = true, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Celebration of the sacred descent of River Ganga observed with glowing river diyas and evening aarti.",
            effect = FestivalEffectId.RIVER_DIYA_GANGA, glyph = "🪔"
        )
        add(
            slug = "raja_parba", name = "Raja Parba", altNames = listOf("Mithuna Sankranti", "Pahili Raja", "Raja Sankranti", "Basi Raja"),
            startDate = "2026-06-14", endDate = "2026-06-16", tithi = "Mithuna Sankranti",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Beloved three-day Odisha festival celebrating Mother Earth, monsoon arrival, swings, and traditional poda pitha.",
            effect = FestivalEffectId.SPRING_BLOOM_PETALS, glyph = "🌺",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "ambubachi_mela", name = "Ambubachi Mela",
            startDate = "2026-06-22", endDate = "2026-06-25", tithi = "Ashadha",
            regions = setOf(IndianFestivalRegion.ASSAM),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Annual sacred monsoon observance at Kamakhya Temple in Guwahati celebrating earth's vitality.",
            effect = FestivalEffectId.BIHU_FESTIVE_PATTERN, glyph = "🪔"
        )
        add(
            slug = "snana_purnima", name = "Snana Purnima", altNames = listOf("Devasnana Purnima"),
            startDate = "2026-06-29", tithi = "Jyeshtha Purnima",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Sacred bathing ceremony of Lord Jagannath, Balabhadra, and Subhadra in Puri heralding Rath Yatra.",
            effect = FestivalEffectId.RATH_YATRA_CHARIOT, glyph = "🛕",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "rath_yatra", name = "Rath Yatra", altNames = listOf("Gundicha Yatra", "Car Festival"),
            startDate = "2026-07-16", tithi = "Ashadha Shukla Dwitiya",
            regions = setOf(IndianFestivalRegion.ODISHA, IndianFestivalRegion.WEST_BENGAL, IndianFestivalRegion.GUJARAT, IndianFestivalRegion.JHARKHAND),
            isPanIndia = true, category = FestivalCategory.MAJOR_PAN_INDIA,
            desc = "Grand chariot festival of Lord Jagannath, Balabhadra, and Subhadra celebrated in Puri and across India.",
            effect = FestivalEffectId.RATH_YATRA_CHARIOT, glyph = "🛕",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "bahuda_yatra", name = "Bahuda Yatra",
            startDate = "2026-07-24", tithi = "Ashadha Shukla Dashami",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Auspicious return chariot journey of Lord Jagannath to Srimandir in Puri.",
            effect = FestivalEffectId.RATH_YATRA_CHARIOT, glyph = "🛕",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "ashadhi_ekadashi", name = "Ashadhi Ekadashi • Wari", altNames = listOf("Devshayani Ekadashi", "Pandharpur Wari"),
            startDate = "2026-07-25", tithi = "Ashadha Shukla Ekadashi",
            regions = setOf(IndianFestivalRegion.MAHARASHTRA, IndianFestivalRegion.KARNATAKA, IndianFestivalRegion.GOA),
            isPanIndia = false, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Sacred Maharashtra Wari pilgrimage to Pandharpur celebrating Lord Vitthal with devotional abhangs.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "suna_besha", name = "Suna Besha", altNames = listOf("Rajarajeswara Besha"),
            startDate = "2026-07-25", tithi = "Ashadha Shukla Ekadashi",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Golden attire ceremony of the Holy Trinity atop their grand chariots before Srimandir in Puri.",
            effect = FestivalEffectId.RATH_YATRA_CHARIOT, glyph = "✨",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "niladri_bije", name = "Niladri Bije", altNames = listOf("Rasagola Dibasa"),
            startDate = "2026-07-27", tithi = "Ashadha Shukla Trayodashi",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Concluding day of Rath Yatra when Lord Jagannath returns to the Ratna Singhasana.",
            effect = FestivalEffectId.RATH_YATRA_CHARIOT, glyph = "🛕",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "guru_purnima", name = "Guru Purnima", altNames = listOf("Vyasa Purnima"),
            startDate = "2026-07-29", tithi = "Ashadha Purnima",
            isPanIndia = true,
            desc = "Sacred full moon day dedicated to honoring spiritual and academic teachers across India.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🌕"
        )
        add(
            slug = "bonalu", name = "Bonalu",
            startDate = "2026-07-19", endDate = "2026-08-09", tithi = "Ashadha Sundays",
            regions = setOf(IndianFestivalRegion.TELANGANA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Vibrant Telangana thanksgiving festival honoring Goddess Mahakali with decorated pots and folk rhythms.",
            effect = FestivalEffectId.DURGA_NAVRATRI_PETALS, glyph = "🌺"
        )

        // August 2026
        add(
            slug = "nag_panchami", name = "Nag Panchami",
            startDate = "2026-08-17", tithi = "Shravana Shukla Panchami",
            isPanIndia = true, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Traditional Shravana observance honoring nature, sacred guardians, and monsoon harmony.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "varamahalakshmi", name = "Varamahalakshmi Vrata",
            startDate = "2026-08-21", tithi = "Shravana Shukla Friday",
            regions = setOf(IndianFestivalRegion.KARNATAKA, IndianFestivalRegion.ANDHRA_PRADESH, IndianFestivalRegion.TELANGANA, IndianFestivalRegion.TAMIL_NADU),
            isPanIndia = false, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Auspicious South Indian observance dedicated to Goddess Lakshmi for household harmony and wellbeing.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "onam", name = "Onam", altNames = listOf("Thiruvonam"),
            startDate = "2026-08-24", endDate = "2026-08-26", tithi = "Chingam Thiruvonam",
            regions = setOf(IndianFestivalRegion.KERALA, IndianFestivalRegion.LAKSHADWEEP),
            isPanIndia = true, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Kerala's vibrant harvest celebration adorned with pookalam floral carpets, Onasadya, and Vallam Kali boat races.",
            effect = FestivalEffectId.ONAM_POOKALAM_BOAT, glyph = "🌼"
        )
        add(
            slug = "raksha_bandhan", name = "Raksha Bandhan", altNames = listOf("Rakhi", "Gamha Purnima"),
            startDate = "2026-08-28", tithi = "Shravana Purnima",
            isPanIndia = true,
            desc = "Cherished celebration of the bond of care and protection between siblings.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "✨"
        )
        add(
            slug = "narali_purnima", name = "Narali Purnima",
            startDate = "2026-08-28", tithi = "Shravana Purnima",
            regions = setOf(IndianFestivalRegion.MAHARASHTRA, IndianFestivalRegion.GOA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Coastal Maharashtra festival offering coconuts to the sea god Varuna for calm waters and safe voyages.",
            effect = FestivalEffectId.RIVER_DIYA_GANGA, glyph = "🌊"
        )

        // September 2026
        add(
            slug = "krishna_janmashtami", name = "Krishna Janmashtami", altNames = listOf("Gokulashtami", "Janmashtami"),
            startDate = "2026-09-04", tithi = "Bhadrapada Krishna Ashtami",
            isPanIndia = true,
            desc = "Midnight celebration of Lord Krishna's birth with flute melodies, peacock feathers, and devotional kirtans.",
            effect = FestivalEffectId.JANMASHTAMI_FLUTE_PEACOCK, glyph = "🦚"
        )
        add(
            slug = "ganesh_chaturthi", name = "Ganesh Chaturthi", altNames = listOf("Vinayaka Chaturthi", "Ganeshotsav"),
            startDate = "2026-09-14", endDate = "2026-09-25", tithi = "Bhadrapada Shukla Chaturthi",
            regions = setOf(IndianFestivalRegion.MAHARASHTRA, IndianFestivalRegion.GOA, IndianFestivalRegion.KARNATAKA, IndianFestivalRegion.ANDHRA_PRADESH, IndianFestivalRegion.TELANGANA, IndianFestivalRegion.ODISHA),
            isPanIndia = true,
            desc = "Joyous festival welcoming Lord Ganesha, remover of obstacles, with marigold flowers, diyas, and aarti.",
            effect = FestivalEffectId.GANESH_DIYA_FLOWERS, glyph = "🌺"
        )
        add(
            slug = "nuakhai", name = "Nuakhai", altNames = listOf("Nuakhai Juhar"),
            startDate = "2026-09-15", tithi = "Bhadrapada Shukla Panchami",
            regions = setOf(IndianFestivalRegion.ODISHA, IndianFestivalRegion.CHHATTISGARH),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Major Western Odisha agricultural harvest festival welcoming the season's new rice with family unity and folk music.",
            effect = FestivalEffectId.PONGAL_KOLAM_STEAM, glyph = "🌾",
            source = SOURCE_ODISHA_PANCHANG
        )

        // October 2026 (Navratri, Bathukamma, Durga Puja, Dussehra, Sharad/Kumar Purnima, Karwa Chauth)
        add(
            slug = "bathukamma", name = "Bathukamma",
            startDate = "2026-10-10", endDate = "2026-10-18", tithi = "Bhadrapada Amavasya – Ashwin Ashtami",
            regions = setOf(IndianFestivalRegion.TELANGANA, IndianFestivalRegion.ANDHRA_PRADESH),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Telangana's floral festival celebrating nature with concentric seasonal flower stacks and traditional songs.",
            effect = FestivalEffectId.DURGA_NAVRATRI_PETALS, glyph = "🌸"
        )
        add(
            slug = "navratri", name = "Sharad Navratri", altNames = listOf("Navratri", "Garba"),
            startDate = "2026-10-11", endDate = "2026-10-19", tithi = "Ashwin Shukla Pratipada – Navami",
            isPanIndia = true,
            desc = "Nine sacred nights honoring Goddess Durga with devotional music, glowing lamps, and festive dance.",
            effect = FestivalEffectId.DURGA_NAVRATRI_PETALS, glyph = "🪷"
        )
        add(
            slug = "durga_puja", name = "Durga Puja", altNames = listOf("Sharadiya Durga Puja", "Durgotsav"),
            startDate = "2026-10-17", endDate = "2026-10-20", tithi = "Ashwin Shukla Shashthi – Dashami",
            regions = setOf(IndianFestivalRegion.WEST_BENGAL, IndianFestivalRegion.ODISHA, IndianFestivalRegion.ASSAM, IndianFestivalRegion.TRIPURA, IndianFestivalRegion.BIHAR, IndianFestivalRegion.JHARKHAND),
            isPanIndia = true,
            desc = "Grand autumn celebration of Goddess Durga with dhak beats, floating petals, dhunuchi incense, and artistry.",
            effect = FestivalEffectId.DURGA_NAVRATRI_PETALS, glyph = "🪷"
        )
        add(
            slug = "kati_bihu", name = "Kati Bihu", altNames = listOf("Kongali Bihu"),
            startDate = "2026-10-17", tithi = "Kati Sankranti",
            regions = setOf(IndianFestivalRegion.ASSAM),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Serene Assamese observance lighting earthen saki lamps beneath tulsi plants and in rice fields.",
            effect = FestivalEffectId.BIHU_FESTIVE_PATTERN, glyph = "🪔"
        )
        add(
            slug = "dussehra", name = "Vijayadashami • Dussehra", altNames = listOf("Dussehra", "Dasara", "Mysuru Dasara"),
            startDate = "2026-10-20", tithi = "Ashwin Shukla Dashami",
            isPanIndia = true,
            desc = "Celebrates the triumph of righteousness and Lord Rama's victory with symbolic illumination and festive cheer.",
            effect = FestivalEffectId.DUSSEHRA_RAMA_ARROW, glyph = "🏹"
        )
        add(
            slug = "sharad_purnima", name = "Sharad Purnima", altNames = listOf("Kojagiri Purnima", "Lakshmi Puja"),
            startDate = "2026-10-25", tithi = "Ashwin Purnima",
            isPanIndia = true,
            desc = "Luminous autumn full moon celebrated across India (and as Kojagiri Lakshmi Puja in Bengal and Maharashtra).",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🌕"
        )
        add(
            slug = "kumar_purnima", name = "Kumar Purnima",
            startDate = "2026-10-25", tithi = "Ashwin Purnima",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Odisha's joyful autumn moonlit festival honoring Kartikeya and the full moon with traditional songs and lamps.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🌕",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "karwa_chauth", name = "Karwa Chauth",
            startDate = "2026-10-29", tithi = "Kartik Krishna Chaturthi",
            regions = setOf(IndianFestivalRegion.PUNJAB, IndianFestivalRegion.HARYANA, IndianFestivalRegion.DELHI, IndianFestivalRegion.UTTAR_PRADESH, IndianFestivalRegion.RAJASTHAN, IndianFestivalRegion.MADHYA_PRADESH, IndianFestivalRegion.HIMACHAL_PRADESH, IndianFestivalRegion.UTTARAKHAND, IndianFestivalRegion.CHANDIGARH),
            isPanIndia = true, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Traditional moonrise fast observed across North India celebrating love, care, and family wellbeing.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🌙"
        )

        // November 2026 (Diwali Week, Chhath Puja, Prabodhini Ekadashi, Tulsi Vivah, Kartik Purnima / Boita Bandana, Dev Deepawali, Manabasa Gurubara)
        add(
            slug = "dhanteras", name = "Dhanteras", altNames = listOf("Dhanatrayodashi"),
            startDate = "2026-11-06", tithi = "Kartik Krishna Trayodashi",
            isPanIndia = true,
            desc = "Auspicious opening day of Diwali celebrations welcoming light, health, and abundance.",
            effect = FestivalEffectId.DIWALI_FIREWORKS, glyph = "🪔"
        )
        add(
            slug = "naraka_chaturdashi", name = "Naraka Chaturdashi", altNames = listOf("Choti Diwali", "Kali Chaudas"),
            startDate = "2026-11-07", tithi = "Kartik Krishna Chaturdashi",
            isPanIndia = true,
            desc = "Eve of Diwali celebrated with early-morning lamps, rangoli, and radiant festive spirit.",
            effect = FestivalEffectId.DIWALI_FIREWORKS, glyph = "🪔"
        )
        add(
            slug = "diwali", name = "Diwali", altNames = listOf("Deepavali", "Lakshmi Puja", "Kali Puja"),
            startDate = "2026-11-08", tithi = "Kartik Amavasya",
            isPanIndia = true,
            desc = "Festival of Lights celebrating the victory of light over darkness with glowing diyas and gentle fireworks.",
            effect = FestivalEffectId.DIWALI_FIREWORKS, glyph = "🪔"
        )
        add(
            slug = "kali_puja", name = "Kali Puja", altNames = listOf("Shyama Puja"),
            startDate = "2026-11-08", tithi = "Kartik Amavasya (Nishita)",
            regions = setOf(IndianFestivalRegion.WEST_BENGAL, IndianFestivalRegion.ODISHA, IndianFestivalRegion.ASSAM, IndianFestivalRegion.TRIPURA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Midnight worship of Goddess Kali celebrated across Bengal, Odisha, and Assam with rows of earthen lamps.",
            effect = FestivalEffectId.DIWALI_FIREWORKS, glyph = "🪔"
        )
        add(
            slug = "govardhan_puja", name = "Govardhan Puja", altNames = listOf("Annakut", "Balipratipada"),
            startDate = "2026-11-09", tithi = "Kartik Shukla Pratipada",
            isPanIndia = true,
            desc = "Day after Diwali honoring gratitude toward nature and Lord Krishna's protection.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "bhai_dooj", name = "Bhai Dooj", altNames = listOf("Bhai Phonta", "Bhau Beej", "Yama Dwitiya"),
            startDate = "2026-11-11", tithi = "Kartik Shukla Dwitiya",
            isPanIndia = true,
            desc = "Warm celebration of sibling affection concluding the five-day Diwali festival.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "chhath_puja", name = "Chhath Puja", altNames = listOf("Surya Shashthi", "Dala Chhath"),
            startDate = "2026-11-14", endDate = "2026-11-16", tithi = "Kartik Shukla Chaturthi – Saptami",
            regions = setOf(IndianFestivalRegion.BIHAR, IndianFestivalRegion.JHARKHAND, IndianFestivalRegion.UTTAR_PRADESH, IndianFestivalRegion.DELHI),
            isPanIndia = true,
            desc = "Ancient solar thanksgiving festival offering arghya to the setting and rising sun along glowing riverbanks.",
            effect = FestivalEffectId.RIVER_DIYA_GANGA, glyph = "🌅"
        )
        add(
            slug = "jagaddhatri_puja", name = "Jagaddhatri Puja",
            startDate = "2026-11-18", endDate = "2026-11-19", tithi = "Kartik Shukla Navami",
            regions = setOf(IndianFestivalRegion.WEST_BENGAL),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Luminous West Bengal autumn celebration of Goddess Jagaddhatri renowned in Chandannagar and Krishnanagar.",
            effect = FestivalEffectId.DURGA_NAVRATRI_PETALS, glyph = "🪷"
        )
        add(
            slug = "prabodhini_ekadashi", name = "Prabodhini Ekadashi", altNames = listOf("Devutthana Ekadashi"),
            startDate = "2026-11-20", tithi = "Kartik Shukla Ekadashi",
            isPanIndia = true, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Sacred Ekadashi marking the awakening of Lord Vishnu and the start of auspicious celebrations.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "tulsi_vivah", name = "Tulsi Vivah",
            startDate = "2026-11-21", tithi = "Kartik Shukla Dwadashi",
            isPanIndia = true, category = FestivalCategory.DEVOTIONAL_VRAT,
            desc = "Traditional ceremonial union of Sacred Tulsi and Shaligram celebrated with courtyard lamps and floral rangoli.",
            effect = FestivalEffectId.GANESH_DIYA_FLOWERS, glyph = "🌺"
        )
        // Kartik Purnima / Boita Bandana (Odisha regional observance + Pan-India Dev Deepawali & Karthigai Deepam)
        add(
            slug = "kartik_purnima", name = "Kartik Purnima • Boita Bandana", altNames = listOf("Boita Bandana", "Bali Yatra", "Rasa Purnima"),
            startDate = "2026-11-24", tithi = "Kartik Purnima",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Sacred Odisha maritime tradition floating illuminated miniature boats (boitas) with diyas at dawn to honor ancient Kalinga sailors.",
            effect = FestivalEffectId.KARTIK_BOITA_BANDANA, glyph = "⛵",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "kartik_purnima", name = "Kartik Purnima", altNames = listOf("Dev Deepawali", "Boita Bandana", "Tripurari Purnima"),
            startDate = "2026-11-24", tithi = "Kartik Purnima",
            isPanIndia = true,
            desc = "Sacred full moon celebrated with illuminated river lamps, Dev Deepawali in Varanasi, and Boita Bandana in Odisha.",
            effect = FestivalEffectId.KARTIK_BOITA_BANDANA, glyph = "⛵"
        )
        add(
            slug = "dev_deepawali", name = "Dev Deepawali",
            startDate = "2026-11-24", tithi = "Kartik Purnima",
            regions = setOf(IndianFestivalRegion.UTTAR_PRADESH, IndianFestivalRegion.DELHI, IndianFestivalRegion.MADHYA_PRADESH, IndianFestivalRegion.BIHAR),
            isPanIndia = true,
            desc = "Diwali of the Gods illuminating the ghats of Varanasi with thousands of glowing earthen diyas.",
            effect = FestivalEffectId.RIVER_DIYA_GANGA, glyph = "🪔"
        )
        add(
            slug = "karthigai_deepam", name = "Karthigai Deepam",
            startDate = "2026-11-24", tithi = "Karthigai Pournami • Krittika",
            regions = setOf(IndianFestivalRegion.TAMIL_NADU, IndianFestivalRegion.PUDUCHERRY, IndianFestivalRegion.KERALA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Ancient Tamil Festival of Lights illuminating homes and Tiruvannamalai hill with rows of glowing oil lamps.",
            effect = FestivalEffectId.DIWALI_FIREWORKS, glyph = "🪔"
        )
        add(
            slug = "manabasa_gurubara", name = "Manabasa Gurubara",
            startDate = "2026-11-26", tithi = "Margashira First Thursday",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Beloved Odisha Margashira Thursday worship of Goddess Lakshmi adorned with intricate white jhoti chita rice-paste art.",
            effect = FestivalEffectId.PONGAL_KOLAM_STEAM, glyph = "🪔",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "prathamastami", name = "Prathamastami",
            startDate = "2026-12-01", tithi = "Margashira Krishna Ashtami",
            regions = setOf(IndianFestivalRegion.ODISHA),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Unique Odisha family celebration blessing the eldest child with new attire, lamps, and aromatic enduri pitha.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "thiruvathira", name = "Thiruvathira", altNames = listOf("Arudra Darshan"),
            startDate = "2026-12-24", tithi = "Dhanu Thiruvathira",
            regions = setOf(IndianFestivalRegion.KERALA, IndianFestivalRegion.TAMIL_NADU),
            isPanIndia = false, category = FestivalCategory.REGIONAL_STATE,
            desc = "Traditional Kerala winter celebration honoring Lord Shiva with graceful Thiruvathirakali dance and lamps.",
            effect = FestivalEffectId.SHIVARATRI_SACRED_NIGHT, glyph = "🌙"
        )
        add(
            slug = "rann_utsav", name = "Rann Utsav Cultural Season",
            startDate = "2026-12-01", endDate = "2026-12-15", tithi = "Margashira Full Moon Season",
            regions = setOf(IndianFestivalRegion.GUJARAT),
            isPanIndia = false, category = FestivalCategory.CULTURAL_TRADITION,
            desc = "Vibrant Kutch cultural celebration of Gujarati folk music, handicrafts, and moonlit desert skies.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "✨"
        )

        // ==================== 2027 KEY VERIFIED DATES ====================
        add(
            slug = "makar_sankranti", name = "Makar Sankranti", altNames = listOf("Uttarayan", "Sankranti"),
            startDate = "2027-01-14", tithi = "Makara Sankranti",
            isPanIndia = true, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Auspicious solar transition celebrated with colorful drifting kites and festive warmth.",
            effect = FestivalEffectId.SANKRANTI_KITES, glyph = "🪁"
        )
        add(
            slug = "pongal", name = "Pongal",
            startDate = "2027-01-14", endDate = "2027-01-17", tithi = "Thai 1–4",
            regions = setOf(IndianFestivalRegion.TAMIL_NADU, IndianFestivalRegion.PUDUCHERRY),
            isPanIndia = false, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Four-day Tamil harvest thanksgiving celebration.",
            effect = FestivalEffectId.PONGAL_KOLAM_STEAM, glyph = "🌾"
        )
        add(
            slug = "maha_shivaratri", name = "Maha Shivaratri", altNames = listOf("Herath"),
            startDate = "2027-03-06", tithi = "Phalguna Krishna Chaturdashi",
            isPanIndia = true,
            desc = "Sacred night honoring Lord Shiva across India.",
            effect = FestivalEffectId.SHIVARATRI_SACRED_NIGHT, glyph = "🌙"
        )
        add(
            slug = "holi", name = "Holi", altNames = listOf("Rangwali Holi", "Dola Purnima"),
            startDate = "2027-03-22", tithi = "Phalguna Purnima / Pratipada",
            isPanIndia = true,
            desc = "Festival of colors celebrating spring with soft drifting gulal clouds.",
            effect = FestivalEffectId.HOLI_GULAL, glyph = "🎨"
        )
        add(
            slug = "ram_navami", name = "Ram Navami",
            startDate = "2027-04-15", tithi = "Chaitra Shukla Navami",
            isPanIndia = true,
            desc = "Auspicious birth celebration of Lord Rama.",
            effect = FestivalEffectId.GOLDEN_AARTI_LAMPS, glyph = "🪔"
        )
        add(
            slug = "rath_yatra", name = "Rath Yatra",
            startDate = "2027-07-06", tithi = "Ashadha Shukla Dwitiya",
            regions = setOf(IndianFestivalRegion.ODISHA, IndianFestivalRegion.WEST_BENGAL, IndianFestivalRegion.GUJARAT),
            isPanIndia = true,
            desc = "Grand chariot festival of Lord Jagannath in Puri and across India.",
            effect = FestivalEffectId.RATH_YATRA_CHARIOT, glyph = "🛕",
            source = SOURCE_ODISHA_PANCHANG
        )
        add(
            slug = "krishna_janmashtami", name = "Krishna Janmashtami", altNames = listOf("Gokulashtami"),
            startDate = "2027-08-25", tithi = "Bhadrapada Krishna Ashtami",
            isPanIndia = true,
            desc = "Celebration of Lord Krishna's birth with flute melodies and peacock feathers.",
            effect = FestivalEffectId.JANMASHTAMI_FLUTE_PEACOCK, glyph = "🦚"
        )
        add(
            slug = "ganesh_chaturthi", name = "Ganesh Chaturthi",
            startDate = "2027-09-04", endDate = "2027-09-14", tithi = "Bhadrapada Shukla Chaturthi",
            isPanIndia = true,
            desc = "Festival welcoming Lord Ganesha with flowers and warm diyas.",
            effect = FestivalEffectId.GANESH_DIYA_FLOWERS, glyph = "🌺"
        )
        add(
            slug = "onam", name = "Onam",
            startDate = "2027-09-11", endDate = "2027-09-13", tithi = "Chingam Thiruvonam",
            regions = setOf(IndianFestivalRegion.KERALA),
            isPanIndia = true, category = FestivalCategory.HARVEST_SOLAR,
            desc = "Kerala harvest festival with floral pookalam carpets.",
            effect = FestivalEffectId.ONAM_POOKALAM_BOAT, glyph = "🌼"
        )
        add(
            slug = "dussehra", name = "Vijayadashami • Dussehra", altNames = listOf("Mysuru Dasara"),
            startDate = "2027-10-10", tithi = "Ashwin Shukla Dashami",
            isPanIndia = true,
            desc = "Celebrates the triumph of righteousness and Lord Rama's victory.",
            effect = FestivalEffectId.DUSSEHRA_RAMA_ARROW, glyph = "🏹"
        )
        add(
            slug = "diwali", name = "Diwali", altNames = listOf("Deepavali", "Kali Puja"),
            startDate = "2027-10-29", tithi = "Kartik Amavasya",
            isPanIndia = true,
            desc = "Festival of Lights with glowing diyas and festive background fireworks.",
            effect = FestivalEffectId.DIWALI_FIREWORKS, glyph = "🪔"
        )
        add(
            slug = "kartik_purnima", name = "Kartik Purnima • Boita Bandana", altNames = listOf("Boita Bandana", "Dev Deepawali"),
            startDate = "2027-11-14", tithi = "Kartik Purnima",
            isPanIndia = true,
            desc = "Sacred full moon celebrated with Odisha's Boita Bandana boats and Dev Deepawali river lamps.",
            effect = FestivalEffectId.KARTIK_BOITA_BANDANA, glyph = "⛵"
        )

        return list
    }

    /**
     * Maps any external public festival feed title (e.g., from MIT `ganeshsp1/hindu-festivals-api`)
     * to a canonical slug, effect ID, and icon glyph so new public entries automatically render
     * appropriate, respectful visuals without hardcoding a closed list.
     */
    fun inferEffectAndGlyph(name: String): Triple<String, FestivalEffectId, String> {
        val lower = name.trim().lowercase()
        return when {
            "diwali" in lower || "deepavali" in lower || "dhanteras" in lower || "naraka chaturdashi" in lower || "kali puja" in lower || "karthigai" in lower ->
                Triple(slugify(name), FestivalEffectId.DIWALI_FIREWORKS, "🪔")
            "kartik purnima" in lower || "boita" in lower ->
                Triple("kartik_purnima", FestivalEffectId.KARTIK_BOITA_BANDANA, "⛵")
            "dussehra" in lower || "vijayadashami" in lower || "dasara" in lower ->
                Triple("dussehra", FestivalEffectId.DUSSEHRA_RAMA_ARROW, "🏹")
            "holi" in lower || "dola" in lower || "rang panchami" in lower || "shigmo" in lower ->
                Triple(if ("rang" in lower) "rang_panchami" else "holi", FestivalEffectId.HOLI_GULAL, "🎨")
            "ganesh" in lower || "vinayaka" in lower ->
                Triple("ganesh_chaturthi", FestivalEffectId.GANESH_DIYA_FLOWERS, "🌺")
            "durga" in lower || "navratri" in lower || "bathukamma" in lower || "bonalu" in lower || "jagaddhatri" in lower ->
                Triple(slugify(name), FestivalEffectId.DURGA_NAVRATRI_PETALS, "🪷")
            "rath yatra" in lower || "bahuda" in lower || "snana purnima" in lower || "suna besha" in lower || "niladri" in lower ->
                Triple(slugify(name), FestivalEffectId.RATH_YATRA_CHARIOT, "🛕")
            "janmashtami" in lower || "gokulashtami" in lower ->
                Triple("krishna_janmashtami", FestivalEffectId.JANMASHTAMI_FLUTE_PEACOCK, "🦚")
            "sankranti" in lower || "uttarayan" in lower ->
                Triple("makar_sankranti", FestivalEffectId.SANKRANTI_KITES, "🪁")
            "pongal" in lower || "nuakhai" in lower || "manabasa" in lower ->
                Triple(slugify(name), FestivalEffectId.PONGAL_KOLAM_STEAM, "🌾")
            "onam" in lower || "thrissur" in lower ->
                Triple(slugify(name), FestivalEffectId.ONAM_POOKALAM_BOAT, "🌼")
            "bihu" in lower || "ambubachi" in lower ->
                Triple(slugify(name), FestivalEffectId.BIHU_FESTIVE_PATTERN, "🌺")
            "shivaratri" in lower || "herath" in lower || "thiruvathira" in lower ->
                Triple(slugify(name), FestivalEffectId.SHIVARATRI_SACRED_NIGHT, "🌙")
            "chhath" in lower || "ganga" in lower || "dev deepawali" in lower || "narali" in lower ->
                Triple(slugify(name), FestivalEffectId.RIVER_DIYA_GANGA, "🪔")
            "ugadi" in lower || "gudi padwa" in lower || "vishu" in lower || "baisakhi" in lower || "vasant" in lower || "boishakh" in lower || "puthandu" in lower || "raja" in lower ->
                Triple(slugify(name), FestivalEffectId.SPRING_BLOOM_PETALS, "🌼")
            else ->
                Triple(slugify(name), FestivalEffectId.GOLDEN_AARTI_LAMPS, "✨")
        }
    }

    private fun slugify(raw: String): String =
        raw.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "festival" }
}
