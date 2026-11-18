package domain.home

/**
 * Top-level Home discovery & library mode toggled from the Home header.
 * Defaults to [OFFLINE] on first install so the local MediaStore library
 * experience works immediately without requiring network connectivity.
 */
enum class HomeMusicMode(
    val id: String,
    val title: String,
    val badgeLabel: String,
    val subtitle: String
) {
    OFFLINE(
        id = "offline",
        title = "Offline Music",
        badgeLabel = "OFFLINE",
        subtitle = "Local device library, albums, mixes & lossless audio"
    ),
    ONLINE(
        id = "online",
        title = "Online Music",
        badgeLabel = "ONLINE",
        subtitle = "Streaming charts, Indian regional hits & NewPipe discovery"
    );

    companion object {
        val DEFAULT = OFFLINE

        fun fromId(id: String?): HomeMusicMode {
            return entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: DEFAULT
        }
    }
}
