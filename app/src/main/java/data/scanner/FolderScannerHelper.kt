package data.scanner

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import com.example.model.Song
import domain.model.FolderInfo
import java.io.File
import java.net.URLDecoder

object FolderScannerHelper {

    private const val TAG = "FolderScannerHelper"

    /**
     * Resolves a Storage Access Framework (SAF) DocumentTree Uri into a FolderInfo with
     * absolute filesystem path, display name, and persisted read permissions.
     */
    fun resolveUriToFolderInfo(context: Context, treeUri: Uri): FolderInfo {
        // 1. Persist read URI permission
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to take persistable URI permission: ${e.message}")
        } catch (e: Throwable) {
            Log.w(TAG, "Permission error: ${e.message}")
        }

        // 2. Decode Document Tree ID
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            treeUri.lastPathSegment ?: ""
        }

        val decodedDocId = try {
            URLDecoder.decode(docId, "UTF-8")
        } catch (_: Exception) {
            docId
        }

        // 3. Translate to real filesystem path
        val resolvedPath = when {
            decodedDocId.startsWith("primary:", ignoreCase = true) -> {
                val rel = decodedDocId.substringAfter("primary:").trimStart('/')
                val root = Environment.getExternalStorageDirectory().absolutePath
                if (rel.isBlank()) root else "$root/$rel"
            }
            decodedDocId.contains(":") -> {
                val parts = decodedDocId.split(":")
                val volumeId = parts[0]
                val subPath = parts.getOrNull(1)?.trimStart('/') ?: ""
                val extRoot = "/storage/$volumeId"
                if (subPath.isBlank()) extRoot else "$extRoot/$subPath"
            }
            decodedDocId.startsWith("/storage") -> {
                decodedDocId
            }
            else -> {
                val fallbackRoot = Environment.getExternalStorageDirectory().absolutePath
                if (decodedDocId.isNotBlank()) "$fallbackRoot/$decodedDocId" else fallbackRoot
            }
        }

        // 4. Derive display name
        val displayName = when {
            File(resolvedPath).name.isNotBlank() -> File(resolvedPath).name
            decodedDocId.substringAfterLast("/").isNotBlank() -> decodedDocId.substringAfterLast("/")
            else -> "Selected Folder"
        }

        return FolderInfo(
            uriString = treeUri.toString(),
            path = resolvedPath,
            name = displayName,
            isCustomSaf = true
        )
    }

    /**
     * Extracts distinct music directories containing audio tracks from the currently scanned songs.
     */
    fun extractDistinctFoldersFromSongs(songs: List<Song>): List<FolderInfo> {
        return songs
            .filter { !it.isDemoTrack && it.dataPath.isNotBlank() }
            .groupBy { File(it.dataPath).parent ?: "/storage/emulated/0/Music" }
            .map { (parentPath, songList) ->
                val dirName = File(parentPath).name.ifBlank { "Music" }
                FolderInfo(
                    uriString = "",
                    path = parentPath,
                    name = dirName,
                    isCustomSaf = false,
                    songCount = songList.size
                )
            }
            .sortedByDescending { it.songCount }
    }

    /**
     * Default suggested music folder (/storage/emulated/0/Music).
     */
    fun getDefaultMusicFolder(): FolderInfo {
        val path = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).absolutePath
        } catch (_: Throwable) {
            "/storage/emulated/0/Music"
        }
        return FolderInfo(
            uriString = "",
            path = path,
            name = "Music",
            isCustomSaf = false
        )
    }
}
