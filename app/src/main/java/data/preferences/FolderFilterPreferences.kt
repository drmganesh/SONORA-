package data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import data.scanner.FolderScannerHelper
import domain.model.FolderFilterSettings
import domain.model.FolderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val FOLDER_FILTER_PREFS_TAG = "FolderFilterPreferences"

private val Context.folderFilterDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonora_folder_filter_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler { ex ->
        Log.e(FOLDER_FILTER_PREFS_TAG, "Corrupted folder filter DataStore recovered with defaults", ex)
        emptyPreferences()
    }
)

/**
 * DataStore Preferences manager for Blacklist and multi-folder Whitelist Folder Settings in SONORA.
 */
class FolderFilterPreferences(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val KEY_WHITELIST_ENABLED = booleanPreferencesKey("whitelist_music_enabled")
        val KEY_WHITELISTED_FOLDER_JSON = stringPreferencesKey("whitelisted_folder_json")
        val KEY_WHITELISTED_FOLDERS_JSON = stringPreferencesKey("whitelisted_folders_json")
        val KEY_BLACKLISTED_FOLDERS_JSON = stringPreferencesKey("blacklisted_folders_json")
    }

    val settingsFlow: Flow<FolderFilterSettings> = context.folderFilterDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(FOLDER_FILTER_PREFS_TAG, "IO error reading folder filter DataStore, using defaults", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val whitelistEnabled = prefs[KEY_WHITELIST_ENABLED] ?: false

            val multiWhitelist = prefs[KEY_WHITELISTED_FOLDERS_JSON]?.let { jsonStr ->
                try {
                    val arr = JSONArray(jsonStr)
                    (0 until arr.length()).map { i -> FolderInfo.fromJson(arr.getJSONObject(i)) }
                } catch (e: Exception) {
                    Log.w(FOLDER_FILTER_PREFS_TAG, "Failed parsing whitelisted folders JSON", e)
                    null
                }
            }

            val whitelistedFolders = when {
                multiWhitelist != null && multiWhitelist.isNotEmpty() -> multiWhitelist
                prefs[KEY_WHITELISTED_FOLDER_JSON] != null -> {
                    val single = prefs[KEY_WHITELISTED_FOLDER_JSON]?.let { rawJson ->
                        try {
                            FolderInfo.fromJson(JSONObject(rawJson))
                        } catch (e: Exception) {
                            Log.w(FOLDER_FILTER_PREFS_TAG, "Failed parsing whitelisted folder JSON", e)
                            FolderScannerHelper.getDefaultMusicFolder()
                        }
                    } ?: FolderScannerHelper.getDefaultMusicFolder()
                    listOf(single)
                }
                else -> listOf(FolderScannerHelper.getDefaultMusicFolder())
            }

            val blacklistedFolders = prefs[KEY_BLACKLISTED_FOLDERS_JSON]?.let { jsonStr ->
                try {
                    val array = JSONArray(jsonStr)
                    (0 until array.length()).map { i ->
                        FolderInfo.fromJson(array.getJSONObject(i))
                    }
                } catch (e: Exception) {
                    Log.w(FOLDER_FILTER_PREFS_TAG, "Failed parsing blacklisted folders JSON", e)
                    emptyList()
                }
            } ?: emptyList()

            FolderFilterSettings(
                whitelistEnabled = whitelistEnabled,
                whitelistedFolders = whitelistedFolders,
                blacklistedFolders = blacklistedFolders
            )
        }

    val settings: StateFlow<FolderFilterSettings> = settingsFlow.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = FolderFilterSettings()
    )

    suspend fun setWhitelistEnabled(enabled: Boolean) {
        try {
            context.folderFilterDataStore.edit { prefs ->
                prefs[KEY_WHITELIST_ENABLED] = enabled
            }
        } catch (e: Exception) {
            Log.e(FOLDER_FILTER_PREFS_TAG, "Failed writing whitelist enabled flag", e)
        }
    }

    suspend fun setWhitelistedFolder(folder: FolderInfo) {
        val current = settings.value.whitelistedFolders.toMutableList()
        current.removeAll { it.path.equals(folder.path, ignoreCase = true) }
        current.add(0, folder)
        saveWhitelistedFolders(current)
    }

    suspend fun addWhitelistedFolder(folder: FolderInfo) {
        val current = settings.value.whitelistedFolders.toMutableList()
        current.removeAll {
            it.path.equals(folder.path, ignoreCase = true) ||
                (it.uriString.isNotBlank() && it.uriString == folder.uriString)
        }
        current.add(folder)
        saveWhitelistedFolders(current)
    }

    suspend fun removeWhitelistedFolder(pathOrUri: String) {
        val current = settings.value.whitelistedFolders.filterNot {
            it.path.equals(pathOrUri, ignoreCase = true) || it.uriString.equals(pathOrUri, ignoreCase = true)
        }
        saveWhitelistedFolders(current.ifEmpty { listOf(FolderScannerHelper.getDefaultMusicFolder()) })
    }

    private suspend fun saveWhitelistedFolders(folders: List<FolderInfo>) {
        val jsonArray = JSONArray()
        folders.forEach { jsonArray.put(it.toJson()) }
        try {
            context.folderFilterDataStore.edit { prefs ->
                prefs[KEY_WHITELISTED_FOLDERS_JSON] = jsonArray.toString()
                folders.firstOrNull()?.let { prefs[KEY_WHITELISTED_FOLDER_JSON] = it.toJson().toString() }
            }
        } catch (e: Exception) {
            Log.e(FOLDER_FILTER_PREFS_TAG, "Failed saving whitelisted folders", e)
        }
    }

    suspend fun addBlacklistedFolder(folder: FolderInfo) {
        val current = settings.value.blacklistedFolders.toMutableList()
        current.removeAll { it.path.equals(folder.path, ignoreCase = true) || (it.uriString.isNotBlank() && it.uriString == folder.uriString) }
        current.add(0, folder)
        saveBlacklistedFolders(current)
    }

    suspend fun addBlacklistedFolders(folders: List<FolderInfo>) {
        val current = settings.value.blacklistedFolders.toMutableList()
        for (f in folders) {
            current.removeAll { it.path.equals(f.path, ignoreCase = true) || (it.uriString.isNotBlank() && it.uriString == f.uriString) }
            current.add(f)
        }
        saveBlacklistedFolders(current)
    }

    suspend fun removeBlacklistedFolder(pathOrUri: String) {
        val current = settings.value.blacklistedFolders.filterNot {
            it.path.equals(pathOrUri, ignoreCase = true) || it.uriString.equals(pathOrUri, ignoreCase = true)
        }
        saveBlacklistedFolders(current)
    }

    suspend fun clearBlacklist() {
        saveBlacklistedFolders(emptyList())
    }

    private suspend fun saveBlacklistedFolders(folders: List<FolderInfo>) {
        val jsonArray = JSONArray()
        for (f in folders) {
            jsonArray.put(f.toJson())
        }
        try {
            context.folderFilterDataStore.edit { prefs ->
                prefs[KEY_BLACKLISTED_FOLDERS_JSON] = jsonArray.toString()
            }
        } catch (e: Exception) {
            Log.e(FOLDER_FILTER_PREFS_TAG, "Failed saving blacklisted folders", e)
        }
    }
}
