package domain.filter

import com.example.model.Song
import domain.model.FolderFilterSettings

/**
 * Centralized filtering system for Blacklist and multi-folder Whitelist management in SONORA.
 *
 * Rules:
 * 1. If whitelist is enabled, only include audio files inside ANY allowed folder & its subfolders.
 * 2. Always exclude blacklisted folders and their subfolders.
 * 3. If whitelist is disabled, include all accessible music except blacklisted locations.
 * 4. Avoid duplicate songs.
 * 5. Handle nested directories and path canonicalization.
 * 6. Blacklist exclusions always take precedence, even inside a whitelisted directory.
 */
object MusicFolderFilter {

    fun filterSongs(allSongs: List<Song>, settings: FolderFilterSettings): List<Song> {
        if (allSongs.isEmpty()) return emptyList()

        val normalizedBlacklist = settings.blacklistedFolders
            .map { normalizePath(it.path) }
            .filter { it.isNotBlank() }

        val isWhitelistActive = settings.whitelistEnabled
        val normalizedWhitelists = settings.whitelistedFolders
            .map { normalizePath(it.path) }
            .filter { it.isNotBlank() }

        return allSongs
            .filter { song ->
                val songPath = normalizePath(song.dataPath)

                // Rule 1: Check Whitelist if enabled
                if (isWhitelistActive && normalizedWhitelists.isNotEmpty()) {
                    val isInsideWhitelist = normalizedWhitelists.any { wl ->
                        isPathInsideFolder(songPath, wl)
                    }
                    val isDemoAllowed = song.isDemoTrack && normalizedWhitelists.any {
                        it.contains("Music", ignoreCase = true)
                    }
                    if (!isInsideWhitelist && !isDemoAllowed) {
                        return@filter false
                    }
                }

                // Rule 2 & 6: Check Blacklist (Precedence over whitelist)
                for (blacklisted in normalizedBlacklist) {
                    if (isPathInsideFolder(songPath, blacklisted)) {
                        return@filter false
                    }
                }

                true
            }
            .distinctBy { it.id }
    }

    /**
     * Checks if targetPath is either identical to or a descendant of folderPath.
     */
    fun isPathInsideFolder(targetPath: String, folderPath: String): Boolean {
        if (targetPath.isBlank() || folderPath.isBlank()) return false
        val cleanTarget = normalizePath(targetPath)
        val cleanFolder = normalizePath(folderPath)

        if (cleanTarget.equals(cleanFolder, ignoreCase = true)) return true
        return cleanTarget.startsWith("$cleanFolder/", ignoreCase = true)
    }

    /**
     * Canonicalizes and normalizes path separators, removes redundant slashes.
     */
    fun normalizePath(rawPath: String): String {
        if (rawPath.isBlank()) return ""
        return rawPath.trim()
            .replace('\\', '/')
            .replace(Regex("/+"), "/")
            .trimEnd('/')
    }

    /**
     * Counts how many songs from the provided list reside within the given folder path.
     */
    fun countSongsInFolder(songs: List<Song>, folderPath: String): Int {
        val normalized = normalizePath(folderPath)
        if (normalized.isBlank()) return 0
        return songs.count { isPathInsideFolder(it.dataPath, normalized) }
    }
}
