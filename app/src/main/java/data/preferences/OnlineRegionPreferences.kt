package data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.SonoraPreferences
import domain.online.DiscoverMusicLanguage
import domain.online.OnlineRegionCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.Locale

private const val ONLINE_REGION_PREFS_TAG = "OnlineRegionPreferences"

private val Context.onlineRegionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_online_region",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(ONLINE_REGION_PREFS_TAG, "Corrupted online region DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

/**
 * DataStore-backed preferences for Online Music Content Country, Preferred Language,
 * and optional Discover language filters.
 * Keeps Country and Language strictly independent while mirroring initial values synchronously
 * via [SonoraPreferences] for deterministic NewPipe single-init at Application startup.
 */
class OnlineRegionPreferences(private val context: Context) {
    private val sonoraPrefs = SonoraPreferences(context)

    companion object {
        val KEY_CONTENT_COUNTRY = stringPreferencesKey("key_online_content_country")
        val KEY_CONTENT_LANGUAGE = stringPreferencesKey("key_online_content_language")
        val KEY_DISCOVER_LANGUAGE = stringPreferencesKey("key_online_discover_language")
    }

    private val safeData: Flow<Preferences> = context.onlineRegionDataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.e(ONLINE_REGION_PREFS_TAG, "IO error reading online region DataStore, using defaults", exception)
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    val contentCountryFlow: Flow<String> = safeData.map { prefs ->
        normalizeCountry(prefs[KEY_CONTENT_COUNTRY] ?: sonoraPrefs.onlineContentCountry)
    }

    val contentLanguageFlow: Flow<String> = safeData.map { prefs ->
        normalizeLanguage(prefs[KEY_CONTENT_LANGUAGE] ?: sonoraPrefs.onlineContentLanguage)
    }

    val discoverLanguageFlow: Flow<DiscoverMusicLanguage> = safeData.map { prefs ->
        val raw = prefs[KEY_DISCOVER_LANGUAGE] ?: sonoraPrefs.discoverMusicLanguageId
        DiscoverMusicLanguage.fromId(raw)
    }

    fun getInitialCountryCode(): String = normalizeCountry(sonoraPrefs.onlineContentCountry)

    fun getInitialLanguageCode(): String = normalizeLanguage(sonoraPrefs.onlineContentLanguage)

    fun getInitialDiscoverLanguage(): DiscoverMusicLanguage =
        DiscoverMusicLanguage.fromId(sonoraPrefs.discoverMusicLanguageId)

    /**
     * Updates content country independently without modifying language preference.
     */
    suspend fun setContentCountry(countryCode: String): String {
        val clean = normalizeCountry(countryCode)
        sonoraPrefs.onlineContentCountry = clean
        try {
            context.onlineRegionDataStore.edit { prefs ->
                prefs[KEY_CONTENT_COUNTRY] = clean
            }
        } catch (e: Exception) {
            Log.e(ONLINE_REGION_PREFS_TAG, "Failed persisting content country", e)
        }
        return clean
    }

    /**
     * Updates preferred language independently without modifying content country preference.
     */
    suspend fun setContentLanguage(languageCode: String): String {
        val clean = normalizeLanguage(languageCode)
        sonoraPrefs.onlineContentLanguage = clean
        try {
            context.onlineRegionDataStore.edit { prefs ->
                prefs[KEY_CONTENT_LANGUAGE] = clean
            }
        } catch (e: Exception) {
            Log.e(ONLINE_REGION_PREFS_TAG, "Failed persisting content language", e)
        }
        return clean
    }

    suspend fun setDiscoverLanguage(language: DiscoverMusicLanguage) {
        sonoraPrefs.discoverMusicLanguageId = language.id
        try {
            context.onlineRegionDataStore.edit { prefs ->
                prefs[KEY_DISCOVER_LANGUAGE] = language.id
            }
        } catch (e: Exception) {
            Log.e(ONLINE_REGION_PREFS_TAG, "Failed persisting discover language filter", e)
        }
    }

    private fun normalizeCountry(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.length == 2 && trimmed.all { it.isLetter() }) {
            trimmed.uppercase(Locale.ROOT)
        } else {
            OnlineRegionCatalog.DEFAULT_COUNTRY_CODE
        }
    }

    private fun normalizeLanguage(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return if (trimmed.length in 2..3 && trimmed.all { it.isLetter() }) {
            trimmed.lowercase(Locale.ROOT)
        } else {
            OnlineRegionCatalog.DEFAULT_LANGUAGE_CODE
        }
    }
}
