package data.preferences

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import domain.download.DownloadQualityOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.downloadDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_download_prefs")

data class DownloadLocationConfig(
    val treeUri: String = "",
    val folderLabel: String = ""
) {
    val isConfigured: Boolean
        get() = treeUri.isNotBlank()

    val parsedUri: Uri?
        get() = treeUri.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
}

class DownloadPreferences(private val context: Context) {

    companion object {
        private val KEY_TREE_URI = stringPreferencesKey("download_tree_uri")
        private val KEY_FOLDER_LABEL = stringPreferencesKey("download_folder_label")
        private val KEY_DEFAULT_QUALITY = stringPreferencesKey("download_default_quality")
    }

    val locationConfigFlow: Flow<DownloadLocationConfig> =
        context.downloadDataStore.data.map { prefs ->
            DownloadLocationConfig(
                treeUri = prefs[KEY_TREE_URI].orEmpty(),
                folderLabel = prefs[KEY_FOLDER_LABEL].orEmpty()
            )
        }

    val defaultQualityFlow: Flow<DownloadQualityOption> =
        context.downloadDataStore.data.map { prefs ->
            val raw = prefs[KEY_DEFAULT_QUALITY].orEmpty()
            runCatching { DownloadQualityOption.valueOf(raw) }.getOrDefault(DownloadQualityOption.AUTO)
        }

    suspend fun getLocationConfig(): DownloadLocationConfig {
        return locationConfigFlow.first()
    }

    suspend fun saveDownloadLocation(treeUri: String, folderLabel: String) {
        context.downloadDataStore.edit { prefs ->
            prefs[KEY_TREE_URI] = treeUri
            prefs[KEY_FOLDER_LABEL] = folderLabel
        }
    }

    suspend fun clearDownloadLocation() {
        context.downloadDataStore.edit { prefs ->
            prefs.remove(KEY_TREE_URI)
            prefs.remove(KEY_FOLDER_LABEL)
        }
    }

    suspend fun setDefaultQuality(option: DownloadQualityOption) {
        context.downloadDataStore.edit { prefs ->
            prefs[KEY_DEFAULT_QUALITY] = option.name
        }
    }
}
