package ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.CircularArtwork
import ui.theme.EditorialColors

/**
 * Horizontal scrolling Recently Played section using circular artwork medallions,
 * backed directly by Room lastPlayedTimestamp playback history.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentlyPlayedSection(
    songs: List<Song>,
    currentPlayingSongId: Long?,
    isPlaying: Boolean,
    colors: EditorialColors,
    onSongClick: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return

    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("recently_played_section")
    ) {
        EditorialSectionTitle(
            title = "Recently Played",
            subtitle = "Pick up right where you left off",
            colors = colors
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(songs, key = { "recent_${it.id}" }) { song ->
                val isCurrent = currentPlayingSongId == song.id
                Column(
                    modifier = Modifier
                        .width(106.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { onSongClick(song) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSongLongClick(song)
                            }
                        )
                        .padding(4.dp)
                        .testTag("recent_song_item_${song.id}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularArtwork(
                        song = song,
                        isPlaying = isCurrent && isPlaying,
                        accentColor = if (isCurrent) colors.accentStrong else colors.accentPill,
                        shadowElevation = if (isCurrent) 12.dp else 7.dp,
                        borderWidth = if (isCurrent) 2.5.dp else 0.dp,
                        borderColor = if (isCurrent) colors.accentStrong else androidx.compose.ui.graphics.Color.Transparent,
                        modifier = Modifier.size(96.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = song.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isCurrent) colors.accentStrong else colors.textPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = song.artist,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
