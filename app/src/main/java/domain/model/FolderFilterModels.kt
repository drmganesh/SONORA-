package domain.model

import org.json.JSONObject

/**
 * Information representing a device folder (either SAF tree URI or direct filesystem path).
 */
data class FolderInfo(
    val uriString: String = "",
    val path: String = "",
    val name: String = "",
    val isCustomSaf: Boolean = false,
    val songCount: Int = 0
) {
    val displayPath: String
        get() = when {
            path.isNotBlank() -> path
            uriString.isNotBlank() -> uriString
            else -> "/storage/emulated/0/Music"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("uri", uriString)
        put("path", path)
        put("name", name)
        put("isSaf", isCustomSaf)
        put("songCount", songCount)
    }

    companion object {
        fun fromJson(json: JSONObject): FolderInfo = FolderInfo(
            uriString = json.optString("uri", ""),
            path = json.optString("path", ""),
            name = json.optString("name", "Folder"),
            isCustomSaf = json.optBoolean("isSaf", false),
            songCount = json.optInt("songCount", 0)
        )

        fun defaultMusicFolder(): FolderInfo = FolderInfo(
            uriString = "",
            path = "/storage/emulated/0/Music",
            name = "Music",
            isCustomSaf = false
        )
    }
}

/**
 * Complete settings state for Blacklist and multi-folder Whitelist filtering.
 */
data class FolderFilterSettings(
    val whitelistEnabled: Boolean = false,
    val whitelistedFolders: List<FolderInfo> = listOf(FolderInfo.defaultMusicFolder()),
    val blacklistedFolders: List<FolderInfo> = emptyList()
) {
    /**
     * Backward-compatible primary whitelisted folder accessor.
     */
    val whitelistedFolder: FolderInfo
        get() = whitelistedFolders.firstOrNull() ?: FolderInfo.defaultMusicFolder()

    val blacklistedPaths: Set<String>
        get() = blacklistedFolders.map { it.path.trim().trimEnd('/') }.filter { it.isNotBlank() }.toSet()

    fun isBlacklisted(folderPath: String): Boolean {
        val clean = folderPath.trim().trimEnd('/')
        return blacklistedPaths.any { blacklisted ->
            clean.equals(blacklisted, ignoreCase = true) || clean.startsWith("$blacklisted/", ignoreCase = true)
        }
    }
}
