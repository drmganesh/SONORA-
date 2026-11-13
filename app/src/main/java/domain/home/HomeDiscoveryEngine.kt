package domain.home

import com.example.model.Album
import com.example.model.Artist
import com.example.model.DailyMix
import com.example.model.Song
import java.util.Calendar

/**
 * Pure domain logic for generating personalized SONORA editorial collections
 * strictly from real local device audio tracks and playback metadata.
 */
object HomeDiscoveryEngine {

    fun greetingForHour(hourOfDay: Int): String {
        return when (hourOfDay) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    fun currentGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return greetingForHour(hour)
    }

    /**
     * Builds the hero "Your Mix" queue prioritizing:
     * 1. Favorite tracks
     * 2. Most-played & recently played tracks
     * 3. Additional library diversity up to 24 tracks
     */
    fun buildYourMix(songs: List<Song>, maxCount: Int = 24): List<Song> {
        if (songs.isEmpty()) return emptyList()

        val ordered = LinkedHashMap<Long, Song>()
        songs.filter { it.isFavorite }
            .sortedByDescending { it.playCount }
            .forEach { ordered[it.id] = it }

        songs.filter { it.lastPlayedTimestamp > 0L }
            .sortedByDescending { it.lastPlayedTimestamp }
            .forEach { if (ordered.size < maxCount) ordered.putIfAbsent(it.id, it) }

        songs.sortedByDescending { it.playCount }
            .forEach { if (ordered.size < maxCount) ordered.putIfAbsent(it.id, it) }

        songs.sortedByDescending { it.dateAdded }
            .forEach { if (ordered.size < maxCount) ordered.putIfAbsent(it.id, it) }

        return ordered.values.toList()
    }

    fun buildContributorsSummary(songs: List<Song>): String {
        val artists = songs
            .map { it.artist.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) && !it.equals("<unknown>", ignoreCase = true) }
            .distinct()
        return when {
            artists.isEmpty() -> if (songs.isNotEmpty()) "${songs.size} local tracks curated" else "Scan device audio to begin"
            artists.size <= 3 -> artists.joinToString(", ")
            else -> "${artists.take(3).joinToString(", ")}..."
        }
    }

    /**
     * Generates personalized mixes strictly from real local audio tracks.
     */
    fun buildDailyMixes(songs: List<Song>): List<DailyMix> {
        if (songs.isEmpty()) return emptyList()

        val mixes = mutableListOf<DailyMix>()

        // 1. Daily Mix 1: Heavy Rotation & Favorites
        val heavyRotation = (songs.filter { it.isFavorite } + songs.sortedByDescending { it.playCount })
            .distinctBy { it.id }
            .take(16)
            .ifEmpty { songs.take(12) }

        mixes.add(
            DailyMix(
                id = "daily_mix_1",
                title = "Daily Mix 1",
                subtitle = "Heavy Rotation",
                description = "Your top repeats & favorite tracks",
                badge = "ON REPEAT",
                songs = heavyRotation,
                coverSong = heavyRotation.firstOrNull(),
                gradientColors = listOf(0xFF4A6FA5, 0xFF8AA8E6)
            )
        )

        // 2. Daily Mix 2: Artist Spotlight Mix (Top artist & collaborators)
        val topArtistGroup = songs.groupBy { it.artist }
            .entries
            .sortedByDescending { entry -> entry.value.sumOf { it.playCount + if (it.isFavorite) 3 else 1 } }
            .firstOrNull()
        val mix2Songs = if (topArtistGroup != null && topArtistGroup.value.size >= 2) {
            (topArtistGroup.value + songs.filter { it.genre.equals(topArtistGroup.value.first().genre, ignoreCase = true) })
                .distinctBy { it.id }
                .take(14)
        } else {
            songs.drop(1).take(12).ifEmpty { songs }
        }
        val mix2Subtitle = topArtistGroup?.key?.takeIf { it.isNotBlank() } ?: "Artist Spotlight"

        mixes.add(
            DailyMix(
                id = "daily_mix_2",
                title = "Daily Mix 2",
                subtitle = mix2Subtitle,
                description = "Curated around your top artists",
                badge = "ARTIST MIX",
                songs = mix2Songs,
                coverSong = mix2Songs.firstOrNull(),
                gradientColors = listOf(0xFF6B5B95, 0xFFB8A9C9)
            )
        )

        // 3. Daily Mix 3: Fresh & Recently Added Discovery
        val unplayedOrRecent = (songs.filter { it.playCount == 0 }.sortedByDescending { it.dateAdded } +
            songs.sortedByDescending { it.dateAdded })
            .distinctBy { it.id }
            .take(14)

        mixes.add(
            DailyMix(
                id = "daily_mix_3",
                title = "Daily Mix 3",
                subtitle = "Fresh Discovery",
                description = "Recent additions & unheard gems",
                badge = "DISCOVER",
                songs = unplayedOrRecent,
                coverSong = unplayedOrRecent.firstOrNull(),
                gradientColors = listOf(0xFF3E7B75, 0xFF81C3B9)
            )
        )

        // 4. Energy Mix
        val energyKeywords = listOf("rock", "pop", "dance", "electronic", "hip", "rap", "edm", "metal", "workout", "beat")
        val energyTracks = songs.filter { song ->
            energyKeywords.any { kw ->
                song.genre.contains(kw, ignoreCase = true) || song.title.contains(kw, ignoreCase = true)
            }
        }.ifEmpty {
            songs.filter { it.durationMs in 120_000L..260_000L }.ifEmpty { songs.take(12) }
        }.take(14)

        mixes.add(
            DailyMix(
                id = "energy_mix",
                title = "Energy Mix",
                subtitle = "Upbeat & Driving",
                description = "High-momentum tracks from your collection",
                badge = "ENERGY",
                songs = energyTracks,
                coverSong = energyTracks.firstOrNull(),
                gradientColors = listOf(0xFFC85A53, 0xFFE9967A)
            )
        )

        // 5. Chill Mix
        val chillKeywords = listOf("chill", "ambient", "lo-fi", "lofi", "jazz", "soul", "r&b", "calm", "night", "slow")
        val chillTracks = songs.filter { song ->
            chillKeywords.any { kw ->
                song.genre.contains(kw, ignoreCase = true) || song.title.contains(kw, ignoreCase = true)
            }
        }.ifEmpty {
            songs.sortedByDescending { it.durationMs }.take(12)
        }.take(14)

        mixes.add(
            DailyMix(
                id = "chill_mix",
                title = "Chill Mix",
                subtitle = "Late Night & Mellow",
                description = "Laid-back grooves for winding down",
                badge = "CHILL",
                songs = chillTracks,
                coverSong = chillTracks.firstOrNull(),
                gradientColors = listOf(0xFF4B6C8B, 0xFF8FB4D9)
            )
        )

        // 6. Acoustic Mix
        val acousticKeywords = listOf("acoustic", "classical", "folk", "piano", "instrumental", "unplugged", "indie", "soundtrack")
        val acousticTracks = songs.filter { song ->
            acousticKeywords.any { kw ->
                song.genre.contains(kw, ignoreCase = true) ||
                    song.album.contains(kw, ignoreCase = true) ||
                    song.title.contains(kw, ignoreCase = true)
            }
        }.ifEmpty {
            songs.filter { it.bitrateKbps >= 320 || it.mimeType.contains("flac", ignoreCase = true) }
                .ifEmpty { songs.reversed().take(12) }
        }.take(14)

        mixes.add(
            DailyMix(
                id = "acoustic_mix",
                title = "Acoustic Mix",
                subtitle = "Warm & Organic",
                description = "Rich instrumental & lossless textures",
                badge = "ACOUSTIC",
                songs = acousticTracks,
                coverSong = acousticTracks.firstOrNull(),
                gradientColors = listOf(0xFF8C6D46, 0xFFD9BA8C)
            )
        )

        return mixes
    }

    fun rankFavouriteAlbums(songs: List<Song>, allAlbums: List<Album>, limit: Int = 12): List<Album> {
        if (allAlbums.isEmpty()) return emptyList()
        val scoreByAlbum = songs.groupBy { it.album }.mapValues { (_, list) ->
            list.sumOf { (if (it.isFavorite) 10 else 0) + it.playCount }
        }
        return allAlbums
            .sortedWith(compareByDescending<Album> { scoreByAlbum[it.title] ?: 0 }.thenByDescending { it.songCount })
            .take(limit)
    }

    fun rankFavouriteArtists(songs: List<Song>, allArtists: List<Artist>, limit: Int = 14): List<Artist> {
        if (allArtists.isEmpty()) return emptyList()
        val scoreByArtist = songs.groupBy { it.artist }.mapValues { (_, list) ->
            list.sumOf { (if (it.isFavorite) 10 else 0) + it.playCount }
        }
        return allArtists
            .sortedWith(compareByDescending<Artist> { scoreByArtist[it.name] ?: 0 }.thenByDescending { it.songCount })
            .take(limit)
    }
}
