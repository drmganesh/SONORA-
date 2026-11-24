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

/**
 * Persisted synchronization metadata for SONORA's Public Festival Calendar:
 * - [lastSyncedEpochMs]: timestamp of the last SUCCESSFUL remote synchronization (0L until a real sync succeeds)
 * - [lastRemoteAttemptEpochMs]: timestamp of the most recent sync attempt (auto or manual)
 * - [datasetVersion]: validated dataset digest/version identifier
 * - [remoteEtag] / [remoteLastModified]: conditional HTTP validators for incremental / 304 checks
 * - [sourceAttribution]: authoritative source and license attribution
 * - [lastSyncStatusCode] / [lastErrorMessage]: persisted outcome and error details
 */
@Entity(tableName = "festival_sync_meta")
data class FestivalSyncMetaEntity(
    @PrimaryKey val id: Int = 1,
    val lastSyncedEpochMs: Long,
    val lastRemoteAttemptEpochMs: Long,
    val lastSourceSummary: String,
    val isOfflineFallback: Boolean,
    val consecutiveFailures: Int,
    val recordCount: Int,
    val datasetVersion: String = "rashtriya-panchang-2026.1",
    val remoteEtag: String? = null,
    val remoteLastModified: String? = null,
    val sourceAttribution: String = "IMD / PAC Rashtriya Panchang • Regional State Panjis • ganeshsp1/hindu-festivals-api (MIT) • satmargastro.com (CC BY 4.0)",
    val lastSyncStatusCode: String = "IDLE",
    val lastErrorMessage: String? = null
)
