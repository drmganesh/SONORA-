package ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.PlusJakartaFontFamily
import ui.components.OrganicArtworkCard
import ui.components.OrganicHeroLeafShape
import ui.components.OrganicPebbleShape
import ui.theme.EditorialColors

/**
 * Artistic asymmetric collage of real local album artwork covers:
 * Large organic hero artwork + overlapping circular and pebble thumbnails.
 * Updates dynamically as library contents change.
 */
@Composable
fun FeaturedArtworkCollage(
    collageSongs: List<Song>,
    colors: EditorialColors,
    onArtworkClick: (Song) -> Unit,
    onArtworkLongClick: (Song) -> Unit,
    modifier: Modifier = Modifier
) {
    if (collageSongs.isEmpty()) return

    val heroSong = collageSongs[0]
    val topLeftSong = collageSongs.getOrElse(1) { heroSong }
    val bottomRightSong = collageSongs.getOrElse(2) { topLeftSong }
    val bottomLeftSong = collageSongs.getOrNull(3)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("featured_artwork_collage")
    ) {
        val availableWidth = maxWidth.coerceAtMost(480.dp)
        val heroSize = (availableWidth * 0.72f).coerceIn(220.dp, 310.dp)
        val topCircleSize = (availableWidth * 0.30f).coerceIn(92.dp, 126.dp)
        val accentCircleSize = (availableWidth * 0.24f).coerceIn(74.dp, 102.dp)
        val containerHeight = heroSize + 44.dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(containerHeight)
        ) {
            // 1. Main oversized organic curved hero artwork on the center-right
            OrganicArtworkCard(
                song = heroSong,
                contentDescription = "${heroSong.album} featured artwork",
                shape = OrganicHeroLeafShape,
                fallbackTint = colors.accentPill,
                iconTint = colors.onAccentPill,
                shadowColor = colors.shadowColor,
                shadowElevation = 18.dp,
                onClick = { onArtworkClick(heroSong) },
                onLongClick = { onArtworkLongClick(heroSong) },
                modifier = Modifier
                    .size(heroSize)
                    .align(Alignment.CenterEnd)
                    .offset(x = (-6).dp, y = 10.dp)
                    .testTag("collage_hero_artwork")
            )

            // 2. Floating overlapping circular thumbnail top-left
            Box(
                modifier = Modifier
                    .size(topCircleSize + 6.dp)
                    .align(Alignment.TopStart)
                    .offset(x = 6.dp, y = 4.dp)
                    .background(colors.background.copy(alpha = 0.7f), CircleShape)
                    .padding(3.dp)
            ) {
                OrganicArtworkCard(
                    song = topLeftSong,
                    contentDescription = "${topLeftSong.album} album thumbnail",
                    shape = CircleShape,
                    fallbackTint = colors.surfaceTinted,
                    iconTint = colors.textPrimary,
                    shadowColor = colors.shadowColor,
                    shadowElevation = 12.dp,
                    onClick = { onArtworkClick(topLeftSong) },
                    onLongClick = { onArtworkLongClick(topLeftSong) },
                    modifier = Modifier
                        .size(topCircleSize)
                        .testTag("collage_secondary_circle")
                )
            }

            // 3. Optional bottom-left pebble accent if 4+ distinct albums exist
            if (bottomLeftSong != null) {
                OrganicArtworkCard(
                    song = bottomLeftSong,
                    contentDescription = "${bottomLeftSong.album} album artwork",
                    shape = OrganicPebbleShape,
                    fallbackTint = colors.accentPill,
                    iconTint = colors.onAccentPill,
                    shadowColor = colors.shadowColor,
                    shadowElevation = 10.dp,
                    onClick = { onArtworkClick(bottomLeftSong) },
                    onLongClick = { onArtworkLongClick(bottomLeftSong) },
                    modifier = Modifier
                        .size((accentCircleSize * 0.92f))
                        .align(Alignment.BottomStart)
                        .offset(x = 18.dp, y = (-18).dp)
                        .testTag("collage_accent_pebble")
                )
            }

            // 4. Bottom-right circular artwork badge overlapping the bottom edge
            Box(
                modifier = Modifier
                    .size(accentCircleSize + 6.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = (-4).dp, y = 0.dp)
                    .background(colors.background.copy(alpha = 0.82f), CircleShape)
                    .padding(3.dp)
            ) {
                OrganicArtworkCard(
                    song = bottomRightSong,
                    contentDescription = "${bottomRightSong.album} artwork",
                    shape = CircleShape,
                    fallbackTint = colors.surfaceTinted,
                    iconTint = colors.textPrimary,
                    shadowColor = colors.shadowColor,
                    shadowElevation = 10.dp,
                    onClick = { onArtworkClick(bottomRightSong) },
                    onLongClick = { onArtworkLongClick(bottomRightSong) },
                    modifier = Modifier
                        .size(accentCircleSize)
                        .testTag("collage_tertiary_circle")
                )
            }

            // Subtle floating album caption pill tucked cleanly on the bottom-left / center-left
            Surface(
                onClick = { onArtworkClick(heroSong) },
                shape = RoundedCornerShape(20.dp),
                color = colors.surface.copy(alpha = 0.90f),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 8.dp, bottom = 6.dp)
                    .border(0.75.dp, colors.cardBorder, RoundedCornerShape(20.dp))
            ) {
                Text(
                    text = heroSong.album.ifBlank { heroSong.title },
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
