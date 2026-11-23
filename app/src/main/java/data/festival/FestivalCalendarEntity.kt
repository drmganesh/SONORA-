package data.festival

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import domain.festival.FestivalCategory
import domain.festival.FestivalEffectId
import domain.festival.FestivalRecord
import domain.festival.FestivalSourcePriority
import domain.festival.FestivalVerificationStatus
import domain.festival.IndianFestivalRegion

@Entity(
    tableName = "festival_calendar_entries",
    indices = [
        Index(value = ["startDateIso", "endDateIso"]),
        Index(value = ["festivalSlug"]),
        Index(value = ["isPanIndia"])
    ]
)
data class FestivalCalendarEntity(
    @PrimaryKey val recordKey: String,
    val festivalSlug: String,
    val canonicalName: String,
    val alternateRegionalNamesCsv: String,
    val startDateIso: String,
    val endDateIso: String,
    val tithi: String?,
    val regionsCsv: String,
    val isPanIndia: Boolean,
    val categoryId: String,
    val shortDescription: String,
    val effectId: String,
    val iconGlyph: String,
    val sourceName: String,
    val sourceLicense: String,
    val sourcePriorityRank: Int,
    val verificationStatusId: String,
    val disagreementNote: String?,
    val lastUpdatedEpochMs: Long,
    val expiresAtEpochMs: Long
) {
    fun toDomain(): FestivalRecord {
        val altNames = alternateRegionalNamesCsv
            .split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val regionSet = regionsCsv
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { IndianFestivalRegion.fromId(it) }
            .toSet()

        return FestivalRecord(
            recordKey = recordKey,
            festivalSlug = festivalSlug,
            canonicalName = canonicalName,
            alternateRegionalNames = altNames,
            startDateIso = startDateIso,
            endDateIso = endDateIso,
            tithi = tithi,
            regions = regionSet,
            isPanIndia = isPanIndia,
            category = FestivalCategory.fromId(categoryId),
            shortDescription = shortDescription,
            effectId = FestivalEffectId.fromId(effectId),
            iconGlyph = iconGlyph,
            sourceName = sourceName,
            sourceLicense = sourceLicense,
            sourcePriority = FestivalSourcePriority.fromRank(sourcePriorityRank),
            verificationStatus = FestivalVerificationStatus.fromId(verificationStatusId),
            disagreementNote = disagreementNote,
            lastUpdatedEpochMs = lastUpdatedEpochMs,
            expiresAtEpochMs = expiresAtEpochMs
        )
    }

    companion object {
        fun fromDomain(record: FestivalRecord): FestivalCalendarEntity = FestivalCalendarEntity(
            recordKey = record.recordKey,
            festivalSlug = record.festivalSlug,
            canonicalName = record.canonicalName,
            alternateRegionalNamesCsv = record.alternateRegionalNames.joinToString("|"),
            startDateIso = record.startDateIso,
            endDateIso = record.endDateIso,
            tithi = record.tithi,
            regionsCsv = record.regions.joinToString(",") { it.id },
            isPanIndia = record.isPanIndia,
            categoryId = record.category.id,
            shortDescription = record.shortDescription,
            effectId = record.effectId.id,
            iconGlyph = record.iconGlyph,
            sourceName = record.sourceName,
            sourceLicense = record.sourceLicense,
            sourcePriorityRank = record.sourcePriority.rank,
            verificationStatusId = record.verificationStatus.id,
            disagreementNote = record.disagreementNote,
            lastUpdatedEpochMs = record.lastUpdatedEpochMs,
            expiresAtEpochMs = record.expiresAtEpochMs
        )
    }
}

@Entity(tableName = "festival_sync_meta")
data class FestivalSyncMetaEntity(
    @PrimaryKey val id: Int = 1,
    val lastSyncedEpochMs: Long,
    val lastRemoteAttemptEpochMs: Long,
    val lastSourceSummary: String,
    val isOfflineFallback: Boolean,
    val consecutiveFailures: Int,
    val recordCount: Int
)
