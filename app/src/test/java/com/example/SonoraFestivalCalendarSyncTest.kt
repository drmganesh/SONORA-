package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.data.SonoraDatabase
import data.festival.FestivalCalendarDao
import data.festival.FestivalCalendarEntity
import data.festival.FestivalCalendarRepository
import data.festival.FestivalCalendarSyncCoordinator
import data.festival.FestivalConditionalHttpTransport
import data.festival.FestivalHttpResponse
import data.festival.FestivalSyncMetaEntity
import data.festival.FestivalSyncStatus
import data.festival.FestivalSyncUiState
import data.festival.PanIndiaFestivalCatalog
import data.festival.PublicFestivalCalendarDataSource
import data.preferences.FestivalAtmosphereSettings
import domain.festival.FestivalRegionMode
import domain.festival.IndianFestivalRegion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.settings.FestivalCalendarContent
import ui.theme.EditorialColors
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraFestivalCalendarSyncTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val indiaZone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun istEpochMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, indiaZone).toInstant().toEpochMilli()

    private fun createInMemoryDb(): SonoraDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return Room.inMemoryDatabaseBuilder(context, SonoraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    /**
     * Realistic response sample matching `ganeshsp1/hindu-festivals-api/data/latest.json`
     * top-level object schema with separate `national` and regional collections plus
     * multi-festival comma-separated titles.
     */
    private val realisticProviderObjectJson = """
        {
          "national": [
            {
              "date": "2026-11-08",
              "name": "Diwali",
              "state_proxy": "national",
              "month": "november"
            },
            {
              "date": "2026-12-20",
              "name": "Gita Jayanti, Mokshada Ekadashi",
              "state_proxy": "national",
              "month": "december"
            }
          ],
          "maharashtra": [
            {
              "date": "2026-03-19",
              "name": "Ugadi, Gudi Padwa",
              "state_proxy": "maharashtra",
              "month": "march"
            },
            {
              "date": "2026-07-25",
              "name": "Ashadi Ekadashi",
              "state_proxy": "maharashtra",
              "month": "july"
            }
          ],
          "kerala": [
            {
              "date": "2026-04-15",
              "name": "Vishu",
              "state_proxy": "kerala",
              "month": "april"
            }
          ],
          "andhra_telangana": [
            {
              "date": "2026-09-21",
              "name": "Bathukamma Begins",
              "state_proxy": "andhra_telangana",
              "month": "september"
            }
          ]
        }
    """.trimIndent()

    // 1. Successful manual sync + 16. Successful timestamp updated after commit + structured diagnostics
    @Test
    fun successfulManualSync_parsesProviderObjectSchema_commitsAtomically_andUpdatesTimestamp() = runBlocking {
        val db = createInMemoryDb()
        try {
            val syncTime = istEpochMs(2026, 10, 20, 9, 30)
            val logs = mutableListOf<String>()

            val transport = FestivalConditionalHttpTransport { _, _, _ ->
                FestivalHttpResponse(
                    statusCode = 200,
                    body = realisticProviderObjectJson,
                    etag = "\"latest-2026-v1\"",
                    lastModified = "Tue, 20 Oct 2026 04:00:00 GMT",
                    contentType = "application/json; charset=utf-8"
                )
            }

            val coordinator = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { true },
                clockEpochMs = { syncTime },
                backoffDelayMs = {},
                diagnosticLogger = { logs += it }
            )

            // Verify all 82 baseline records are seeded first and timestamp is still 0L
            coordinator.ensureLocalSeedPresent()
            val seedCount = db.festivalCalendarDao().countEntries()
            assertEquals(82, seedCount)
            assertEquals(0L, db.festivalCalendarDao().getSyncMetaOnce()!!.lastSyncedEpochMs)

            // Run manual Sync Now
            val result = coordinator.syncNowManually()
            assertTrue(result.performedNetworkRequest)
            assertTrue(result.uiState is FestivalSyncUiState.Updated)
            assertEquals("Calendar updated successfully", result.uiState.message)

            val allStored = db.festivalCalendarDao().getAllEntriesOnce().map { it.toDomain() }
            assertTrue("Must preserve all 82 baseline records plus merged updates", allStored.size > 82)
            // Split multi-festival entries became individual clean records
            assertTrue(allStored.any { it.canonicalName == "Gita Jayanti" && it.startDateIso == "2026-12-20" && it.isPanIndia })
            assertTrue(allStored.any { it.canonicalName == "Mokshada Ekadashi" && it.startDateIso == "2026-12-20" && it.isPanIndia })
            assertTrue(allStored.any { it.canonicalName == "Ashadi Ekadashi" && IndianFestivalRegion.MAHARASHTRA in it.regions && !it.isPanIndia })
            assertTrue(allStored.any {
                it.canonicalName == "Bathukamma Begins" &&
                    IndianFestivalRegion.ANDHRA_PRADESH in it.regions &&
                    IndianFestivalRegion.TELANGANA in it.regions
            })
            // Existing Diwali record was not duplicated
            assertEquals(1, allStored.count { it.festivalSlug == "diwali" && it.startDateIso == "2026-11-08" && it.isPanIndia })
            // No raw JSON in any festival name
            assertTrue(allStored.none { PublicFestivalCalendarDataSource.looksLikeRawJsonOrCode(it.canonicalName) })

            val meta = db.festivalCalendarDao().getSyncMetaOnce()!!
            assertEquals(syncTime, meta.lastSyncedEpochMs)
            assertEquals(syncTime, meta.lastRemoteAttemptEpochMs)
            assertEquals("\"latest-2026-v1\"", meta.remoteEtag)
            assertEquals(allStored.size, meta.recordCount)

            // Verify structured safe diagnostic logs emitted without raw JSON
            assertTrue(logs.any { it.startsWith("SYNC_STARTED") })
            assertTrue(logs.any { it.startsWith("SOURCE_REQUESTED") })
            assertTrue(logs.any { it.startsWith("HTTP_RESPONSE status=200") })
            assertTrue(logs.any { it.startsWith("PARSE_RESULT") })
            assertTrue(logs.any { it.startsWith("DB_TRANSACTION result=COMMITTED") })
            assertTrue(logs.any { it.startsWith("SYNC_COMPLETED status=UPDATED") })
            assertTrue(logs.none { it.contains("{\"national\"") })
        } finally {
            db.close()
        }
    }

    // 2. Offline manual sync + 14. Cached calendar preserved + 15. Successful timestamp unchanged
    @Test
    fun offlineManualSync_preserves82CachedRecords_andLeavesSuccessfulTimestampUnchanged() = runBlocking {
        val db = createInMemoryDb()
        try {
            val attemptTime = istEpochMs(2026, 10, 21, 8, 15)
            var online = false
            val networkCalls = AtomicInteger(0)

            val transport = FestivalConditionalHttpTransport { _, _, _ ->
                networkCalls.incrementAndGet()
                FestivalHttpResponse(200, realisticProviderObjectJson, contentType = "application/json")
            }

            val coordinator = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { online },
                clockEpochMs = { attemptTime },
                backoffDelayMs = {}
            )

            val offlineRes = coordinator.syncNowManually()
            assertFalse(offlineRes.performedNetworkRequest)
            assertEquals(0, networkCalls.get())
            assertTrue(offlineRes.uiState is FestivalSyncUiState.NoInternet)
            assertEquals("No internet connection. Using cached calendar.", offlineRes.uiState.message)
            assertEquals(82, db.festivalCalendarDao().countEntries())

            val meta = db.festivalCalendarDao().getSyncMetaOnce()!!
            assertEquals(0L, meta.lastSyncedEpochMs)
            assertEquals(attemptTime, meta.lastRemoteAttemptEpochMs)
            assertEquals("OFFLINE", meta.lastSyncStatusCode)

            // Automatic sync on next opening when online succeeds because failed/offline attempt did not mark day synced
            online = true
            val onlineRetry = coordinator.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = true)
            assertTrue(onlineRetry.performedNetworkRequest)
            assertEquals(1, networkCalls.get())
            assertTrue(onlineRetry.uiState is FestivalSyncUiState.Updated)
        } finally {
            db.close()
        }
    }

    // 3. HTTP 404 (fails fast without retries, preserves cache & timestamp)
    @Test
    fun http404_returnsServerUnavailable_preservesCache_andDoesNotRetry() = runBlocking {
        val db = createInMemoryDb()
        try {
            val day1 = istEpochMs(2026, 10, 20, 9, 0)
            val day2 = istEpochMs(2026, 10, 21, 9, 0)
            var now = day1
            var statusToReturn = 200
            val calls = AtomicInteger(0)

            val transport = FestivalConditionalHttpTransport { _, _, _ ->
                calls.incrementAndGet()
                if (statusToReturn == 200) {
                    FestivalHttpResponse(200, realisticProviderObjectJson, contentType = "application/json")
                } else {
                    FestivalHttpResponse(statusToReturn, "Not Found", contentType = "text/plain")
                }
            }

            val coordinator = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { true },
                clockEpochMs = { now },
                backoffDelayMs = {}
            )

            assertTrue(coordinator.syncNowManually().uiState is FestivalSyncUiState.Updated)
            val baselineCount = db.festivalCalendarDao().countEntries()

            now = day2
            statusToReturn = 404
            calls.set(0)
            val res404 = coordinator.syncNowManually()

            assertEquals(1, calls.get())
            assertTrue(res404.uiState is FestivalSyncUiState.ServerError)
            assertEquals("Festival calendar server unavailable. Using cached data.", res404.uiState.message)
            assertEquals(baselineCount, db.festivalCalendarDao().countEntries())
            val meta = db.festivalCalendarDao().getSyncMetaOnce()!!
            assertEquals(day1, meta.lastSyncedEpochMs)
            assertEquals(day2, meta.lastRemoteAttemptEpochMs)
        } finally {
            db.close()
        }
    }

    // 4. HTTP 429 rate limit (fails fast without hammering API, preserves cache & timestamp)
    @Test
    fun http429RateLimit_failsFast_preservesCache_andKeepsLastSuccessfulTimestamp() = runBlocking {
        val db = createInMemoryDb()
        try {
            val now = istEpochMs(2026, 10, 22, 10, 0)
            val calls = AtomicInteger(0)
            val transport = FestivalConditionalHttpTransport { _, _, _ ->
                calls.incrementAndGet()
                FestivalHttpResponse(429, "Too Many Requests", contentType = "text/plain")
            }

            val coordinator = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { true },
                clockEpochMs = { now },
                backoffDelayMs = {}
            )

            val res = coordinator.syncNowManually()
            assertEquals(1, calls.get())
            assertTrue(res.uiState is FestivalSyncUiState.ServerError)
            assertEquals("Festival calendar server unavailable. Using cached data.", res.uiState.message)
            assertEquals(82, db.festivalCalendarDao().countEntries())
            val meta = db.festivalCalendarDao().getSyncMetaOnce()!!
            assertEquals(0L, meta.lastSyncedEpochMs)
            assertEquals(now, meta.lastRemoteAttemptEpochMs)
        } finally {
            db.close()
        }
    }

    // 5. HTTP 500 server failure (retries with exponential backoff 1s -> 2s, preserves cache)
    @Test
    fun http500ServerFailure_retriesWithBackoff_andPreservesCache() = runBlocking {
        val db = createInMemoryDb()
        try {
            val now = istEpochMs(2026, 10, 22, 12, 0)
            val calls = AtomicInteger(0)
            val backoffs = mutableListOf<Long>()

            val transport = FestivalConditionalHttpTransport { _, _, _ ->
                calls.incrementAndGet()
                FestivalHttpResponse(500, "<html>Internal Server Error</html>", contentType = "text/html")
            }

            val coordinator = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { true },
                clockEpochMs = { now },
                backoffDelayMs = { backoffs += it }
            )

            val res = coordinator.syncNowManually()
            assertEquals(3, calls.get())
            assertEquals(listOf(1_000L, 2_000L), backoffs)
            assertTrue(res.uiState is FestivalSyncUiState.ServerError)
            assertEquals("Festival calendar server unavailable. Using cached data.", res.uiState.message)
            assertEquals(82, db.festivalCalendarDao().countEntries())
            assertEquals(0L, db.festivalCalendarDao().getSyncMetaOnce()!!.lastSyncedEpochMs)
        } finally {
            db.close()
        }
    }

    // 6. Timeout (SocketTimeoutException retries with backoff and recovers or reports ServerError)
    @Test
    fun timeoutException_retriesWithExponentialBackoff_andRecoversOrReportsServerUnavailable() = runBlocking {
        val db = createInMemoryDb()
        try {
            val now = istEpochMs(2026, 10, 23, 14, 0)
            val calls = AtomicInteger(0)
            val backoffs = mutableListOf<Long>()

            // Case A: All 3 attempts time out
            val alwaysTimeout = FestivalConditionalHttpTransport { _, _, _ ->
                calls.incrementAndGet()
                throw SocketTimeoutException("connect timed out")
            }
            val coordTimeout = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = alwaysTimeout),
                isNetworkAvailable = { true },
                clockEpochMs = { now },
                backoffDelayMs = { backoffs += it }
            )
            val failedTimeout = coordTimeout.syncNowManually()
            assertEquals(3, calls.get())
            assertEquals(listOf(1_000L, 2_000L), backoffs)
            assertTrue(failedTimeout.uiState is FestivalSyncUiState.ServerError)
            assertEquals("Festival calendar server unavailable. Using cached data.", failedTimeout.uiState.message)
            assertEquals(0L, db.festivalCalendarDao().getSyncMetaOnce()!!.lastSyncedEpochMs)

            // Case B: Recovers on 3rd attempt
            calls.set(0)
            backoffs.clear()
            val flakyTimeout = FestivalConditionalHttpTransport { _, _, _ ->
                if (calls.incrementAndGet() < 3) throw SocketTimeoutException("read timed out")
                FestivalHttpResponse(200, realisticProviderObjectJson, contentType = "application/json")
            }
            val coordRecover = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = flakyTimeout),
                isNetworkAvailable = { true },
                clockEpochMs = { now },
                backoffDelayMs = { backoffs += it }
            )
            val recovered = coordRecover.syncNowManually()
            assertEquals(3, calls.get())
            assertEquals(listOf(1_000L, 2_000L), backoffs)
            assertTrue(recovered.uiState is FestivalSyncUiState.Updated)
            assertEquals(now, db.festivalCalendarDao().getSyncMetaOnce()!!.lastSyncedEpochMs)
        } finally {
            db.close()
        }
    }

    // 7. Invalid JSON + HTML content-type rejected cleanly without leaking raw JSON
    @Test
    fun invalidJsonAndHtmlContentType_returnInvalidResponse_andPreserveCache() = runBlocking {
        val db = createInMemoryDb()
        try {
            val now = istEpochMs(2026, 10, 24, 9, 0)
            var body = "{ broken_json_payload"
            var contentType = "application/json"

            val transport = FestivalConditionalHttpTransport { _, _, _ ->
                FestivalHttpResponse(200, body, contentType = contentType)
            }
            val coordinator = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { true },
                clockEpochMs = { now },
                backoffDelayMs = {}
            )

            val malformed = coordinator.syncNowManually()
            assertTrue(malformed.uiState is FestivalSyncUiState.InvalidResponse)
            assertEquals("Calendar data could not be verified. Using cached data.", malformed.uiState.message)
            assertFalse(malformed.uiState.debugDiagnostic.orEmpty().contains("{"))
            assertEquals(82, db.festivalCalendarDao().countEntries())
            assertEquals(0L, db.festivalCalendarDao().getSyncMetaOnce()!!.lastSyncedEpochMs)

            // HTML CDN error page with 200 OK
            body = "<!DOCTYPE html><html><body>CDN Blocked</body></html>"
            contentType = "text/html; charset=utf-8"
            val htmlRes = coordinator.syncNowManually()
            assertTrue(htmlRes.uiState is FestivalSyncUiState.InvalidResponse)
            assertEquals("Calendar data could not be verified. Using cached data.", htmlRes.uiState.message)
            assertEquals(82, db.festivalCalendarDao().countEntries())
        } finally {
            db.close()
        }
    }

    // 8. Changed JSON response schema rejected cleanly without corrupting cache
    @Test
    fun changedJsonSchema_returnsInvalidResponse_andPreservesExistingCache() = runBlocking {
        val db = createInMemoryDb()
        try {
            val now = istEpochMs(2026, 10, 24, 15, 0)
            val changedSchemaJson = """{"status": "ok", "version": 3, "migrated_to": "v3"}"""

            val transport = FestivalConditionalHttpTransport { _, _, _ ->
                FestivalHttpResponse(200, changedSchemaJson, contentType = "application/json")
            }
            val coordinator = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { true },
                clockEpochMs = { now },
                backoffDelayMs = {}
            )

            val res = coordinator.syncNowManually()
            assertTrue(res.uiState is FestivalSyncUiState.InvalidResponse)
            assertEquals("Calendar data could not be verified. Using cached data.", res.uiState.message)
            assertEquals(82, db.festivalCalendarDao().countEntries())
            assertEquals(0L, db.festivalCalendarDao().getSyncMetaOnce()!!.lastSyncedEpochMs)
        } finally {
            db.close()
        }
    }

    // 9. Empty response (empty body, {}, {"national":[]}) preserves all 82 cached records
    @Test
    fun emptyResponses_returnInvalidResponse_andPreserveAll82CachedRecords() = runBlocking {
        val db = createInMemoryDb()
        try {
            val now = istEpochMs(2026, 10, 25, 9, 0)
            val emptyPayloads = listOf("", "   ", "[]", "{}", """{"national":[]}""")

            for (payload in emptyPayloads) {
                val transport = FestivalConditionalHttpTransport { _, _, _ ->
                    FestivalHttpResponse(200, payload, contentType = "application/json")
                }
                val coordinator = FestivalCalendarSyncCoordinator(
                    dao = db.festivalCalendarDao(),
                    dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                    isNetworkAvailable = { true },
                    clockEpochMs = { now },
                    backoffDelayMs = {}
                )
                val res = coordinator.syncNowManually()
                assertTrue("Payload '$payload' must fail validation", res.uiState is FestivalSyncUiState.InvalidResponse)
                assertEquals("Calendar data could not be verified. Using cached data.", res.uiState.message)
                assertEquals(82, db.festivalCalendarDao().countEntries())
                assertEquals(0L, db.festivalCalendarDao().getSyncMetaOnce()!!.lastSyncedEpochMs)
            }
        } finally {
            db.close()
        }
    }

    // 10. Multiple regional festival collections (both flat object keys and nested "regional" map)
    @Test
    fun parser_handlesMultipleRegionalCollections_separatelyFromNational() {
        val now = istEpochMs(2026, 10, 26, 9, 0)
        val nestedSchema = """
            {
              "year": 2026,
              "national": [
                {"date": "2026-12-20", "name": "Gita Jayanti", "state_proxy": "national"}
              ],
              "regional": {
                "tamil_nadu": [
                  {"date": "2026-08-18", "name": "Aadi Perukku"}
                ],
                "west_bengal": [
                  {"date": "2026-10-25", "name": "Kojagari Lakshmi Puja"}
                ],
                "andhra_telangana": [
                  {"date": "2026-07-19", "name": "Bonalu"}
                ]
              }
            }
        """.trimIndent()

        val summary = PublicFestivalCalendarDataSource.parsePublicCalendarPayload(nestedSchema, now)
        assertEquals(4, summary.validCount)
        assertEquals(0, summary.rejectedCount)
        assertTrue(summary.collectionsFound.containsAll(listOf("national", "tamil_nadu", "west_bengal", "andhra_telangana")))

        val gita = summary.records.first { it.canonicalName == "Gita Jayanti" }
        assertTrue(gita.isPanIndia)
        assertTrue(gita.regions.isEmpty())

        val aadi = summary.records.first { it.canonicalName == "Aadi Perukku" }
        assertFalse(aadi.isPanIndia)
        assertEquals(setOf(IndianFestivalRegion.TAMIL_NADU), aadi.regions)

        val lakshmi = summary.records.first { it.canonicalName == "Kojagari Lakshmi Puja" }
        assertFalse(lakshmi.isPanIndia)
        assertEquals(setOf(IndianFestivalRegion.WEST_BENGAL), lakshmi.regions)

        val bonalu = summary.records.first { it.canonicalName == "Bonalu" }
        assertFalse(bonalu.isPanIndia)
        assertEquals(setOf(IndianFestivalRegion.ANDHRA_PRADESH, IndianFestivalRegion.TELANGANA), bonalu.regions)
    }

    // 11. Duplicate festival records deduplicated by stable identity (slug + date + region)
    @Test
    fun parserAndMerger_deduplicateRepeatedFestivalRecords() {
        val now = istEpochMs(2026, 10, 26, 10, 0)
        val duplicatedFeed = """
            {
              "national": [
                {"date": "2026-12-20", "name": "Gita Jayanti", "state_proxy": "national"},
                {"date": "2026-12-20", "name": "  Gita   Jayanti ", "state_proxy": "national"},
                {"date": "2026-11-08", "name": "Diwali", "state_proxy": "national"}
              ],
              "kerala": [
                {"date": "2026-04-15", "name": "Vishu", "state_proxy": "kerala"},
                {"date": "2026-04-15", "name": "Vishu", "state_proxy": "kerala"}
              ]
            }
        """.trimIndent()

        val parsed = PublicFestivalCalendarDataSource.parsePublicCalendarPayload(duplicatedFeed, now)
        assertEquals(1, parsed.records.count { it.canonicalName == "Gita Jayanti" })
        assertEquals(1, parsed.records.count { it.canonicalName == "Vishu" })

        val merged = PublicFestivalCalendarDataSource.mergeWithPriorityAndDisagreements(
            baseCatalog = PanIndiaFestivalCatalog.verifiedSeedRecords(now),
            remoteRecords = parsed.records,
            nowEpochMs = now
        )
        assertEquals(1, merged.count { it.festivalSlug == "diwali" && it.startDateIso == "2026-11-08" && it.isPanIndia })
        assertEquals(1, merged.count { it.canonicalName == "Gita Jayanti" && it.startDateIso == "2026-12-20" })
    }

    // 12. Multiple festival names split into individual records & malformed records rejected individually
    @Test
    fun parser_splitsMultiFestivalNames_andRejectsMalformedEntriesIndividually() {
        val now = istEpochMs(2026, 10, 26, 11, 0)
        val mixedMalformedFeed = """
            {
              "national": [
                {"date": "2026-03-19", "name": "Ugadi, Gudi Padwa; Cheti Chand / Navreh"},
                {"date": "2026-02-30", "name": "Invalid Calendar Date"},
                {"date": "2026-05-10", "name": "{\"national\":[{\"name\":\"Bad\"}]}"},
                {"date": "2026-05-11", "name": {"nested": "object"}},
                {"date": "1999-01-01", "name": "Out Of Range Year"},
                "not_an_object"
              ]
            }
        """.trimIndent()

        val summary = PublicFestivalCalendarDataSource.parsePublicCalendarPayload(mixedMalformedFeed, now)
        assertEquals(4, summary.validCount)
        assertEquals(5, summary.rejectedCount)
        assertEquals(
            setOf("Ugadi", "Gudi Padwa", "Cheti Chand", "Navreh"),
            summary.records.map { it.canonicalName }.toSet()
        )
        assertTrue(summary.records.none { it.canonicalName.contains(",") || it.canonicalName.contains("{") })
    }

    // 13. Room database transaction failure preserves existing data and leaves lastSuccessfulSyncAt unchanged
    @Test
    fun databaseTransactionFailure_preservesExistingCache_andLeavesTimestampUnchanged() = runBlocking {
        val db = createInMemoryDb()
        try {
            val realDao = db.festivalCalendarDao()
            val day1 = istEpochMs(2026, 10, 20, 9, 0)
            val day2 = istEpochMs(2026, 10, 21, 9, 0)
            var failCommits = false

            val failingDao = object : FestivalCalendarDao {
                override fun observeAllEntries(): Flow<List<FestivalCalendarEntity>> = realDao.observeAllEntries()
                override suspend fun getAllEntriesOnce(): List<FestivalCalendarEntity> = realDao.getAllEntriesOnce()
                override suspend fun countEntries(): Int = realDao.countEntries()
                override suspend fun upsertEntries(entries: List<FestivalCalendarEntity>) = realDao.upsertEntries(entries)
                override suspend fun clearAllEntries() = realDao.clearAllEntries()
                override suspend fun replaceAllEntries(entries: List<FestivalCalendarEntity>) = realDao.replaceAllEntries(entries)
                override suspend fun commitSyncUpdate(entries: List<FestivalCalendarEntity>, meta: FestivalSyncMetaEntity) {
                    if (failCommits) throw IllegalStateException("SQLITE_FULL: disk full during transaction")
                    realDao.commitSyncUpdate(entries, meta)
                }
                override fun observeSyncMeta(): Flow<FestivalSyncMetaEntity?> = realDao.observeSyncMeta()
                override suspend fun getSyncMetaOnce(): FestivalSyncMetaEntity? = realDao.getSyncMetaOnce()
                override suspend fun upsertSyncMeta(meta: FestivalSyncMetaEntity) = realDao.upsertSyncMeta(meta)
            }

            var currentClock = day1
            val transport = FestivalConditionalHttpTransport { _, _, _ ->
                FestivalHttpResponse(200, realisticProviderObjectJson, contentType = "application/json")
            }

            val coordinator = FestivalCalendarSyncCoordinator(
                dao = failingDao,
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { true },
                clockEpochMs = { currentClock },
                backoffDelayMs = {}
            )

            // Seed cleanly first (82 records)
            coordinator.ensureLocalSeedPresent()
            assertEquals(82, realDao.countEntries())

            // Simulate DB transaction failure during manual Sync Now
            failCommits = true
            currentClock = day2
            val res = coordinator.syncNowManually()

            assertTrue(res.uiState is FestivalSyncUiState.DatabaseError)
            assertEquals("Could not save the updated calendar. Existing data preserved.", res.uiState.message)
            assertEquals(82, realDao.countEntries())
            val meta = realDao.getSyncMetaOnce()!!
            assertEquals("Successful timestamp must stay 0L after DB commit failure", 0L, meta.lastSyncedEpochMs)
            assertEquals(day2, meta.lastRemoteAttemptEpochMs)
            assertEquals("DATABASE_ERROR", meta.lastSyncStatusCode)
        } finally {
            db.close()
        }
    }

    // 18. Multiple rapid Sync Now taps do not start duplicate requests
    @Test
    fun rapidManualSyncTaps_executeOnlyOneNetworkRequest() = runBlocking {
        val db = createInMemoryDb()
        try {
            val nowMs = istEpochMs(2026, 10, 22, 10, 0)
            val requestStarted = CompletableDeferred<Unit>()
            val releaseNetwork = CompletableDeferred<Unit>()
            val networkCalls = AtomicInteger(0)

            val blockingTransport = FestivalConditionalHttpTransport { _, _, _ ->
                networkCalls.incrementAndGet()
                requestStarted.complete(Unit)
                runBlocking { releaseNetwork.await() }
                FestivalHttpResponse(200, realisticProviderObjectJson, etag = "\"v2\"", contentType = "application/json")
            }

            val coordinator = FestivalCalendarSyncCoordinator(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = blockingTransport),
                isNetworkAvailable = { true },
                clockEpochMs = { nowMs },
                backoffDelayMs = {}
            )

            val firstJob = async(Dispatchers.IO) { coordinator.syncNowManually() }
            requestStarted.await()
            assertTrue(coordinator.isSyncInFlight)

            // Rapid simultaneous taps + auto open
            val rapidTap2 = coordinator.syncNowManually()
            val rapidTap3 = coordinator.syncNowManually()
            val concurrentAuto = coordinator.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = true)

            assertTrue(rapidTap2.skippedDuplicateInFlight)
            assertTrue(rapidTap3.skippedDuplicateInFlight)
            assertTrue(concurrentAuto.skippedDuplicateInFlight)
            assertFalse(rapidTap2.performedNetworkRequest)

            releaseNetwork.complete(Unit)
            val completed = firstJob.await()
            assertTrue(completed.performedNetworkRequest)
            assertEquals(1, networkCalls.get())
            assertFalse(coordinator.isSyncInFlight)
        } finally {
            db.close()
        }
    }

    // Automatic daily sync on app opening + next IST day 304 + WorkManager single unique job
    @Test
    fun automaticDailySync_respectsIstCalendarDay_handles304_andKeepsSingleWorkManagerJob() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build()
        )
        val db = createInMemoryDb()
        try {
            var currentClock = istEpochMs(2026, 10, 20, 9, 0)
            val calls = AtomicInteger(0)

            val transport = FestivalConditionalHttpTransport { _, ifNoneMatch, _ ->
                calls.incrementAndGet()
                if (ifNoneMatch == "\"v1\"") {
                    FestivalHttpResponse(304, null, etag = "\"v1\"")
                } else {
                    FestivalHttpResponse(200, realisticProviderObjectJson, etag = "\"v1\"", contentType = "application/json")
                }
            }

            val repo = FestivalCalendarRepository(
                dao = db.festivalCalendarDao(),
                dataSource = PublicFestivalCalendarDataSource(conditionalTransport = transport),
                isNetworkAvailable = { true },
                clockEpochMs = { currentClock }
            )

            // Single unique WorkManager job even if scheduled repeatedly
            repo.schedulePeriodicRefresh(context)
            repo.schedulePeriodicRefresh(context)
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(FestivalCalendarRepository.PERIODIC_SYNC_WORK_NAME)
                .get()
            assertEquals(1, workInfos.size)
            assertEquals(WorkInfo.State.ENQUEUED, workInfos.first().state)

            // Day 1 app open -> syncs once
            val day1Res = repo.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = true)
            assertTrue(day1Res.performedNetworkRequest)
            assertTrue(day1Res.uiState is FestivalSyncUiState.Updated)
            assertEquals(1, calls.get())

            // Same IST day later -> uses cached calendar without downloading again
            currentClock = istEpochMs(2026, 10, 20, 23, 50)
            val sameDayRes = repo.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = true)
            assertFalse(sameDayRes.performedNetworkRequest)
            assertTrue(sameDayRes.skippedAlreadySyncedToday)
            assertEquals(1, calls.get())

            // Next IST day -> triggers automatic sync and handles HTTP 304 cleanly
            currentClock = istEpochMs(2026, 10, 21, 0, 10)
            val nextDayRes = repo.syncOnAppOpenIfNeeded(automaticDailySyncEnabled = true)
            assertTrue(nextDayRes.performedNetworkRequest)
            assertTrue(nextDayRes.uiState is FestivalSyncUiState.AlreadyUpToDate)
            assertEquals("Calendar is already up to date", nextDayRes.uiState.message)
            assertEquals(2, calls.get())
        } finally {
            db.close()
        }
    }

    // 17. UI NEVER displays raw JSON (even if legacy DB metadata or error contains {"national":[...]})
    // and displays all exact friendly states, separated timestamps, Sync Now, and Auto Daily Sync toggle
    @Test
    fun festivalCalendarUi_neverDisplaysRawJson_andShowsAllFriendlyStates() {
        val lastSuccessMs = istEpochMs(2026, 10, 27, 17, 30)
        val lastAttemptMs = istEpochMs(2026, 10, 28, 9, 15)
        val rawJsonPoison = """Value {"national":[{"date":"2026-11-08","name":"Diwali"}]} of type org.json.JSONObject cannot be converted to JSONArray"""

        var syncUiState by mutableStateOf<FestivalSyncUiState>(FestivalSyncUiState.Idle)
        var syncStatus by mutableStateOf(
            FestivalSyncStatus(
                lastSyncedEpochMs = lastSuccessMs,
                lastAttemptedEpochMs = lastAttemptMs,
                recordCount = 82,
                datasetVersion = "v2026-abc12345",
                lastSyncStatusCode = "INVALID_RESPONSE",
                lastErrorMessage = rawJsonPoison
            )
        )
        var settings by mutableStateOf(
            FestivalAtmosphereSettings.DEFAULT.copy(
                regionMode = FestivalRegionMode.SPECIFIC_STATE,
                selectedState = IndianFestivalRegion.ODISHA,
                automaticDailySyncEnabled = true
            )
        )
        var syncClickCount = 0

        val upcoming = FestivalCalendarRepository.filterUpcomingFestivals(
            records = PanIndiaFestivalCatalog.verifiedSeedRecords(),
            fromDate = LocalDate.parse("2026-11-01"),
            regionMode = FestivalRegionMode.SPECIFIC_STATE,
            selectedState = IndianFestivalRegion.ODISHA,
            includeRegionalInAllIndia = false,
            nowEpochMs = PanIndiaFestivalCatalog.CATALOG_SEED_EPOCH_MS + 10_000L
        )

        composeRule.setContent {
            FestivalCalendarContent(
                settings = settings,
                syncStatus = syncStatus,
                syncUiState = syncUiState,
                upcomingFestivals = upcoming,
                colors = EditorialColors.default(),
                onSyncNow = { syncClickCount++ },
                onToggleAutoDailySync = { enabled ->
                    settings = settings.copy(automaticDailySyncEnabled = enabled)
                },
                onBack = {},
                nowEpochMs = lastAttemptMs,
                showDebugDiagnostics = true // Even in debug mode, raw JSON MUST be stripped!
            )
        }

        // Verify separated timestamps & friendly message & ZERO raw JSON anywhere on screen
        composeRule.onNodeWithTag("festival_calendar_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Calendar data could not be verified. Using cached data.").assertIsDisplayed()
        composeRule.onNodeWithText("Last synced: 27 Oct 2026, 05:30 PM IST").assertIsDisplayed()
        composeRule.onNodeWithText("Last attempt: 28 Oct 2026, 09:15 AM IST").assertIsDisplayed()
        composeRule.onNodeWithText("82 cached festivals • Dataset v2026-abc12345").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("national", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("JSONObject", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("{", substring = true).fetchSemanticsNodes().size)

        // Tap Sync Now + Top Sync action
        composeRule.onNodeWithTag("festival_calendar_sync_now_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("festival_calendar_top_sync_button")
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForIdle()
        assertEquals(2, syncClickCount)

        // State: Syncing... (buttons disabled)
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            syncUiState = FestivalSyncUiState.Syncing
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithTag("festival_calendar_sync_now_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("festival_calendar_top_sync_button").assertIsNotEnabled()

        // State: Updated
        composeRule.runOnUiThread {
            syncUiState = FestivalSyncUiState.Updated(86, lastAttemptMs)
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Calendar updated successfully").assertIsDisplayed()

        // State: Already up to date
        composeRule.runOnUiThread { syncUiState = FestivalSyncUiState.AlreadyUpToDate(lastAttemptMs) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Calendar is already up to date").assertIsDisplayed()

        // State: Offline
        composeRule.runOnUiThread { syncUiState = FestivalSyncUiState.NoInternet }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No internet connection. Using cached calendar.").assertIsDisplayed()

        // State: Server error
        composeRule.runOnUiThread { syncUiState = FestivalSyncUiState.ServerError(500, "HTTP 500 server error") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Festival calendar server unavailable. Using cached data.").assertIsDisplayed()

        // State: Invalid response (poisoned with raw JSON -> still zero JSON on screen!)
        composeRule.runOnUiThread { syncUiState = FestivalSyncUiState.InvalidResponse("MALFORMED_JSON", rawJsonPoison) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Calendar data could not be verified. Using cached data.").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("{", substring = true).fetchSemanticsNodes().size)

        // State: Database error
        composeRule.runOnUiThread { syncUiState = FestivalSyncUiState.DatabaseError("SQLiteFullException") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Could not save the updated calendar. Existing data preserved.").assertIsDisplayed()

        // Verify Selected Region card renders cleanly
        composeRule.onNodeWithTag("festival_calendar_lazy_column")
            .performScrollToKey("calendar_region_source_card")
        composeRule.onNodeWithText("Odisha (OD)").assertIsDisplayed()
    }
}
