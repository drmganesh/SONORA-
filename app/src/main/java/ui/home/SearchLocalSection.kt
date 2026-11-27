package ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Album
import com.example.model.Artist
import com.example.model.Genre
import com.example.model.MusicFolder
import com.example.model.PlaybackInfo
import com.example.model.Playlist
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import java.io.File
import ui.components.CircularArtwork
import ui.theme.EditorialColors

data class SearchCollectionModal(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.localSearchLazyContent(
    query: String,
    selectedFilter: Int,
    allSongs: List<Song>,
    genres: List<Genre>,
    recentSearches: List<String>,
    smartSuggestions: List<String>,
    matchedSongs: List<Song>,
    matchedAlbums: List<Album>,
    matchedArtists: List<Artist>,
    matchedPlaylists: List<Playlist>,
    matchedFolders: List<MusicFolder>,
    currentSong: Song?,
    playbackInfo: com.example.model.PlaybackStatusSummary,
    colors: EditorialColors,
    haptic: HapticFeedback,
    viewModel: SonoraViewModel,
    showCombinedHeader: Boolean,
    onSelectQuery: (String) -> Unit,
    onOpenModal: (SearchCollectionModal) -> Unit,
    onOpenContextMenu: (Song) -> Unit,
    onSwitchToOnlineSearch: (() -> Unit)? = null
) {
    if (showCombinedHeader) {
        item(key = "combined_local_section_header") {
            Text(
                text = "Offline Library",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = 22.dp, vertical = 6.dp)
                    .testTag("search_combined_local_header")
            )
        }
    }

    if (!showCombinedHeader && query.isBlank() && recentSearches.isNotEmpty()) {
        item(key = "recent_searches_block") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Recent Searches",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.textPrimary
                    )
                }
                Text(
                    text = "Clear All",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = colors.accentStrong,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { viewModel.clearRecentSearches() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recentSearches, key = { "rec_$it" }) { term ->
                    Surface(
                        onClick = { onSelectQuery(term) },
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surface,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = term,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textPrimary
                            )
                            IconButton(
                                onClick = { viewModel.removeRecentSearch(term) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove search $term",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (!showCombinedHeader && query.isBlank() && smartSuggestions.isNotEmpty()) {
        item(key = "smart_suggestions") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Smart Suggestions",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.textPrimary
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(smartSuggestions, key = { "sug_$it" }) { term ->
                    Surface(
                        onClick = {
                            onSelectQuery(term)
                            viewModel.addRecentSearch(term)
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = colors.accentPill.copy(alpha = 0.65f),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = term,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.onAccentPill
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (!showCombinedHeader && query.isBlank() && genres.isNotEmpty()) {
        item(key = "browse_genres") {
            Text(
                text = "Browse Genres",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(genres.take(12), key = { it.name }) { g ->
                    Surface(
                        onClick = {
                            val genreSongs = allSongs.filter { it.genre == g.name }
                            onOpenModal(
                                SearchCollectionModal(
                                    title = g.name,
                                    subtitle = "Genre Collection",
                                    songs = genreSongs
                                )
                            )
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, colors.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(
                                text = g.name,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${g.songCount} tracks",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Matched Artists
    if ((selectedFilter == 0 || selectedFilter == 3) && matchedArtists.isNotEmpty()) {
        item(key = "artists_header") {
            Text(
                text = "Artists",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
        }
        items(matchedArtists, key = { "sr_art_${it.name}" }) { artist ->
            val artistSongs = allSongs.filter { it.artist == artist.name }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .clickable {
                        onOpenModal(
                            SearchCollectionModal(
                                title = artist.name,
                                subtitle = "${artist.albumCount} albums",
                                songs = artistSongs
                            )
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.accentPill, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Person, contentDescription = null, tint = colors.onAccentPill)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = artist.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${artist.songCount} songs",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }

    // Matched Albums
    if ((selectedFilter == 0 || selectedFilter == 2) && matchedAlbums.isNotEmpty()) {
        item(key = "albums_header") {
            Text(
                text = "Albums",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
        }
        items(matchedAlbums, key = { "sr_alb_${it.id}_${it.title}" }) { album ->
            val albumSongs = allSongs.filter { it.album == album.title }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .clickable {
                        onOpenModal(
                            SearchCollectionModal(
                                title = album.title,
                                subtitle = album.artist,
                                songs = albumSongs
                            )
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularArtwork(
                    song = albumSongs.firstOrNull(),
                    fallbackLabel = album.title,
                    accentColor = colors.accentPill,
                    shadowElevation = 3.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.artist} • ${album.songCount} tracks",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(Icons.Rounded.Album, contentDescription = null, tint = colors.textMuted)
            }
        }
    }

    // Matched Playlists
    if ((selectedFilter == 0 || selectedFilter == 4) && matchedPlaylists.isNotEmpty()) {
        item(key = "playlists_header") {
            Text(
                text = "Playlists",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
        }
        items(matchedPlaylists, key = { "sr_pl_${it.id}" }) { playlist ->
            val playlistSongs by viewModel.getSongsForPlaylistFlow(playlist.id).collectAsState(initial = emptyList())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .clickable {
                        if (query.isNotBlank()) viewModel.addRecentSearch(query)
                        onOpenModal(
                            SearchCollectionModal(
                                title = playlist.name,
                                subtitle = "Custom Playlist",
                                songs = playlistSongs
                            )
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.accentPill, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = colors.onAccentPill)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = playlist.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${playlistSongs.size} songs",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }

    // Matched Folders
    if ((selectedFilter == 0 || selectedFilter == 5) && matchedFolders.isNotEmpty()) {
        item(key = "folders_header") {
            Text(
                text = "Folders",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
        }
        items(matchedFolders, key = { "sr_fld_${it.path}" }) { folder ->
            val folderSongs = allSongs.filter { (File(it.dataPath).parent ?: "/storage") == folder.path }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surface)
                    .clickable {
                        if (query.isNotBlank()) viewModel.addRecentSearch(query)
                        onOpenModal(
                            SearchCollectionModal(
                                title = folder.name,
                                subtitle = folder.path,
                                songs = folderSongs
                            )
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.accentPill, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.onAccentPill)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${folder.songCount} songs • ${folder.path}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Matched Songs
    if ((selectedFilter == 0 || selectedFilter == 1) && matchedSongs.isNotEmpty()) {
        item(key = "songs_header") {
            Text(
                text = if (query.isBlank()) "Quick Library Picks" else "Songs (${matchedSongs.size})",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp)
            )
        }

        items(matchedSongs, key = { "sr_song_${it.id}" }) { song ->
            val isCurrent = currentSong?.id == song.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isCurrent) colors.accentPill.copy(alpha = 0.55f) else colors.surface)
                    .combinedClickable(
                        onClick = {
                            if (query.isNotBlank()) viewModel.addRecentSearch(query)
                            viewModel.playSong(song, matchedSongs)
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenContextMenu(song)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("search_local_song_row_${song.id}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularArtwork(
                    song = song,
                    isPlaying = isCurrent && playbackInfo.isPlaying,
                    accentColor = colors.accentPill,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${song.artist} • ${song.album}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onOpenContextMenu(song) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options for ${song.title}",
                        tint = colors.textMuted
                    )
                }
            }
        }
    }

    if (query.isNotBlank() &&
        matchedSongs.isEmpty() &&
        matchedAlbums.isEmpty() &&
        matchedArtists.isEmpty() &&
        matchedPlaylists.isEmpty() &&
        matchedFolders.isEmpty()
    ) {
        item(key = "no_local_results") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .testTag("search_local_no_results"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No local tracks, albums, or artists found for \"$query\"",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
                if (onSwitchToOnlineSearch != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        onClick = onSwitchToOnlineSearch,
                        shape = RoundedCornerShape(20.dp),
                        color = colors.accentStrong,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("search_local_switch_online_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CloudQueue,
                                contentDescription = null,
                                tint = colors.onAccentStrong,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Switch to Online Search",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = colors.onAccentStrong
                            )
                        }
                    }
                }
            }
        }
    }
}
