package data.download

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import domain.download.DownloadQualityOption
import domain.download.DownloadTask
import domain.download.DownloadedTrack
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.math.abs

class InsufficientStorageException(message: String) : IOException(message)
class StorageAccessLostException(message: String) : IOException(message)

data class ExtractedAudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val embeddedArtworkBytes: ByteArray?
)

class DownloadStorageHelper(private val context: Context) {

    companion object {
        private const val TAG = "DownloadStorageHelper"
        private const val MIN_SAFETY_MARGIN_BYTES = 2L * 1024L * 1024L
        private const val MIN_UNKNOWN_SIZE_REQUIRED_BYTES = 5L * 1024L * 1024L
    }

    fun computeCanonicalSourceKey(providerKey: String, contentUrl: String): String {
        val cleanProvider = providerKey.trim().lowercase().ifBlank { "online" }
        val cleanUrl = contentUrl.trim()
        return "$cleanProvider|$cleanUrl"
    }

    fun computeDownloadedSongId(sourceKey: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest("DOWNLOADED|$sourceKey".toByteArray())
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (digest[i].toLong() and 0xffL)
        }
        val magnitude = (abs(value) and 0x3FFF_FFFF_FFFF_FFFFL).coerceAtLeast(1_000_000_000L)
        return -magnitude
    }

    fun buildSuggestedInitialTreeUri(): Uri? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Music"
                )
            } else {
                null
            }
        }.getOrNull()
    }

    fun persistSafTreePermission(uri: Uri): Boolean {
        return try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (t: SecurityException) {
            Log.w(TAG, "Unable to persist SAF URI permission directly for $uri: ${t.message}")
            // Still verify whether DocumentFile is currently accessible (e.g. Robolectric test trees)
            isTreeUriAccessible(uri.toString(), requirePersistedRecord = false)
        } catch (t: Throwable) {
            Log.e(TAG, "Unexpected failure persisting SAF permission for $uri", t)
            false
        }
    }

    fun isTreeUriAccessible(
        treeUriString: String,
        requirePersistedRecord: Boolean = true
    ): Boolean {
        if (treeUriString.isBlank()) return false
        val uri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return false
        if (uri.scheme == "file") {
            val dir = uri.path?.let { File(it) } ?: return false
            return dir.exists() && dir.isDirectory && dir.canWrite()
        }
        if (requirePersistedRecord) {
            val persisted = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
            if (!persisted) return false
        }
        val docDir = runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull() ?: return false
        return docDir.exists() && docDir.isDirectory && docDir.canWrite()
    }

    fun resolveFolderDisplayName(uri: Uri): String {
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).name }?.ifBlank { "Music Downloads" } ?: "Music Downloads"
        }
        val docName = runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
        if (!docName.isNullOrBlank()) return docName

        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        if (!docId.isNullOrBlank()) {
            val cleaned = docId.substringAfter(':', docId).trim('/').replace("/", " / ")
            if (cleaned.isNotBlank()) return cleaned
        }
        return uri.lastPathSegment?.substringAfter(':', "")?.ifBlank { null } ?: "Selected Folder"
    }

    fun getStagingDir(): File {
        val dir = File(context.cacheDir, "download_staging")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isInsideDirectory(candidate: File, parentDir: File): Boolean {
        return runCatching {
            val parentCanonical = parentDir.canonicalPath
            val candidateCanonical = candidate.canonicalPath
            candidateCanonical.startsWith(parentCanonical + File.separator)
        }.getOrDefault(false)
    }

    fun isInsideStagingDir(file: File): Boolean = isInsideDirectory(file, getStagingDir())

    fun getStagingFile(taskId: String, extension: String): File {
        val safeTaskId = taskId.replace(Regex("[^A-Za-z0-9_-]"), "_").take(96).ifBlank { "task" }
        val safeExt = extension.lowercase().replace(Regex("[^a-z0-9]"), "").take(10).ifBlank { "m4a" }
        val stagingDir = getStagingDir()
        val candidate = File(stagingDir, "${safeTaskId}.$safeExt")
        if (!isInsideDirectory(candidate, stagingDir)) {
            throw SecurityException("Rejected staging file path outside staging directory")
        }
        return candidate
    }

    fun deleteStagingFile(stagingFilePath: String?) {
        if (stagingFilePath.isNullOrBlank()) return
        runCatching {
            val f = File(stagingFilePath)
            if (isInsideStagingDir(f) && f.exists()) {
                f.delete()
            }
        }
    }

    fun checkAvailableStorage(expectedTotalBytes: Long, alreadyDownloadedBytes: Long = 0L) {
        val stagingDir = getStagingDir()
        val availableBytes = runCatching {
            val stat = StatFs(stagingDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(Long.MAX_VALUE)

        val remainingNeeded = if (expectedTotalBytes > 0L) {
            (expectedTotalBytes - alreadyDownloadedBytes).coerceAtLeast(0L) + MIN_SAFETY_MARGIN_BYTES
        } else {
            MIN_UNKNOWN_SIZE_REQUIRED_BYTES
        }
        if (availableBytes < remainingNeeded) {
            throw InsufficientStorageException(
                "Not enough free storage space to complete this download."
            )
        }
    }

    fun sanitizeBaseFileName(title: String, artist: String): String {
        val cleanTitle = title.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(64)
            .ifBlank { "${domain.branding.AppBranding.APP_NAME} Track" }
        val cleanArtist = artist.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(40)
        return if (cleanArtist.isNotBlank() && !cleanArtist.equals("Online", ignoreCase = true)) {
            "$cleanTitle - $cleanArtist"
        } else {
            cleanTitle
        }
    }

    fun extractMetadataFromFile(file: File): ExtractedAudioMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitrateBps = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val sampleRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull() ?: 0
            } else {
                0
            }
            val artwork = runCatching { retriever.embeddedPicture }.getOrNull()
            ExtractedAudioMetadata(
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs.coerceAtLeast(0L),
                bitrateKbps = if (bitrateBps > 0) bitrateBps / 1000 else 0,
                sampleRateHz = sampleRateHz.coerceAtLeast(0),
                embeddedArtworkBytes = artwork
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Metadata extraction partial/skipped for ${file.name}: ${t.message}")
            ExtractedAudioMetadata(null, null, null, 0L, 0, 0, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun saveArtworkBytes(downloadedId: Long, bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            val artDir = File(context.filesDir, "downloaded_art").apply { if (!exists()) mkdirs() }
            val file = File(artDir, "art_${abs(downloadedId)}.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    fun commitStagingFileToTargetFolder(
        task: DownloadTask,
        stagingFile: File,
        requirePersistedRecord: Boolean = true
    ): Pair<String, String> {
        if (!stagingFile.exists() || stagingFile.length() <= 0L) {
            throw IOException("Downloaded staging file is empty or missing.")
        }
        val folderUri = runCatching { Uri.parse(task.targetFolderUri) }.getOrNull()
            ?: throw StorageAccessLostException("Download folder location is not configured.")

        if (folderUri.scheme == "file") {
            val targetDir = folderUri.path?.let { File(it) }
                ?: throw StorageAccessLostException("Invalid local download directory.")
            if (!targetDir.exists() || !targetDir.isDirectory || !targetDir.canWrite()) {
                throw StorageAccessLostException("Selected download folder is no longer writable.")
            }
            val ext = task.fileExtension.lowercase().replace(Regex("[^a-z0-9]"), "").take(10).ifBlank { "m4a" }
            val baseName = sanitizeBaseFileName(task.title, task.artist)
            var candidate = File(targetDir, "$baseName.$ext")
            var suffix = 1
            while (candidate.exists()) {
                candidate = File(targetDir, "$baseName ($suffix).$ext")
                suffix++
            }
            if (!isInsideDirectory(candidate, targetDir)) {
                throw SecurityException("Rejected destination file path outside selected download folder")
            }
            FileInputStream(stagingFile).use { input ->
                candidate.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                    output.flush()
                }
            }
            if (!candidate.exists() || candidate.length() <= 0L) {
                candidate.delete()
                throw IOException("Failed writing audio file to destination folder.")
            }
            return Uri.fromFile(candidate).toString() to candidate.name
        }

        if (!isTreeUriAccessible(task.targetFolderUri, requirePersistedRecord = requirePersistedRecord)) {
            throw StorageAccessLostException("Permission for the selected download folder expired. Please reselect your download folder.")
        }

        val treeDoc = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw StorageAccessLostException("Cannot access selected download folder.")

        val ext = task.fileExtension.lowercase().replace(Regex("[^a-z0-9]"), "").take(10).ifBlank { "m4a" }
        val baseName = sanitizeBaseFileName(task.title, task.artist)
        var targetName = "$baseName.$ext"
        var suffix = 1
        while (treeDoc.findFile(targetName) != null) {
            targetName = "$baseName ($suffix).$ext"
            suffix++
        }

        val effectiveMime = task.mimeType.ifBlank { mimeTypeForExtension(ext) }
        // DocumentFile.createFile appends extension when mime matches on some providers if displayName includes it;
        // stripping extension when provider expects display name without it varies, so keep safe base + mime fallback.
        val created = treeDoc.createFile(effectiveMime, targetName)
            ?: treeDoc.createFile("application/octet-stream", targetName)
            ?: throw IOException("Could not create audio file in destination folder.")

        try {
            val out = context.contentResolver.openOutputStream(created.uri, "w")
                ?: throw IOException("Unable to open output stream for downloaded file.")
            FileInputStream(stagingFile).use { input ->
                out.use { output ->
                    input.copyTo(output, bufferSize = 32 * 1024)
                    output.flush()
                }
            }
            val verifiedSize = runCatching {
                context.contentResolver.openAssetFileDescriptor(created.uri, "r")?.use { it.length }
            }.getOrNull() ?: created.length()

            if (verifiedSize == 0L) {
                runCatching { created.delete() }
                throw IOException("Destination file verification failed (0 bytes written).")
            }
            return created.uri.toString() to (created.name ?: targetName)
        } catch (t: Throwable) {
            runCatching { created.delete() }
            throw t
        }
    }

    fun doesDownloadedFileExist(localFileUri: String): Boolean {
        if (localFileUri.isBlank()) return false
        val uri = runCatching { Uri.parse(localFileUri) }.getOrNull() ?: return false
        return if (uri.scheme == "file") {
            val f = uri.path?.let { File(it) } ?: return false
            f.exists() && f.length() > 0L
        } else {
            runCatching {
                val doc = DocumentFile.fromSingleUri(context, uri)
                if (doc != null && doc.exists() && doc.length() > 0L) {
                    true
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        pfd.statSize > 0L || pfd.statSize == -1L
                    } ?: false
                }
            }.getOrDefault(false)
        }
    }

    fun deleteDownloadedTrackFiles(track: DownloadedTrackEntity): Boolean {
        var deleted = false
        val uri = runCatching { Uri.parse(track.localFileUri) }.getOrNull()
        if (uri != null) {
            deleted = if (uri.scheme == "file") {
                val f = uri.path?.let { File(it) }
                if (f != null && f.exists()) f.delete() else true
            } else {
                val doc = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
                if (doc != null && doc.exists()) doc.delete() else true
            }
        }
        val artPath = track.localArtworkPath
        if (!artPath.isNullOrBlank()) {
            runCatching {
                val artFile = File(artPath)
                val artDir = File(context.filesDir, "downloaded_art")
                if (artFile.exists() && isInsideDirectory(artFile, artDir)) {
                    artFile.delete()
                }
            }
        }
        return deleted
    }

    fun mimeTypeForExtension(ext: String): String {
        return when (ext.lowercase()) {
            "m4a", "mp4" -> "audio/mp4"
            "webm" -> "audio/webm"
            "opus" -> "audio/opus"
            "ogg" -> "audio/ogg"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            else -> "audio/mp4"
        }
    }
}
