package ui.player.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayerStyle
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Live miniature preview card for each of the 19 Player Styles.
 * When [song] is provided, renders actual embedded album artwork inside the style's frame.
 */
@Composable
fun PlayerStyleMiniPreview(
    style: PlayerStyle,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    song: Song? = null
) {
    val colors = LocalEditorialColors.current
    val accent = if (isSelected) colors.accentStrong else colors.accentStrong.copy(alpha = 0.78f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when (style) {
                    PlayerStyle.NEON -> Color(0xFF0A0B12)
                    PlayerStyle.CANVAS -> Color(0xFF14151D)
                    else -> colors.background
                }
            )
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.accentStrong else colors.cardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            PlayerStyle.CLASSIC -> ClassicMiniPreview(accent, colors, song)
            PlayerStyle.MINIMAL -> MinimalMiniPreview(accent, colors, song)
            PlayerStyle.MATERIAL_YOU -> MaterialYouMiniPreview(accent, colors, song)
            PlayerStyle.GLASS -> GlassMiniPreview(accent, colors, song)
            PlayerStyle.EDITORIAL -> EditorialMiniPreview(accent, colors, song)
            PlayerStyle.CANVAS -> CanvasMiniPreview(accent, song)
            PlayerStyle.BENTO -> BentoMiniPreview(accent, colors, song)
            PlayerStyle.POSTER -> PosterMiniPreview(accent, colors, song)
            PlayerStyle.POLAROID -> PolaroidMiniPreview(accent, colors, song)
            PlayerStyle.VINYL -> VinylMiniPreview(accent, colors, song)
            PlayerStyle.DIAL -> DialMiniPreview(accent, colors, song)
            PlayerStyle.MORPH -> MorphMiniPreview(accent, colors, song)
            PlayerStyle.STICKER -> StickerMiniPreview(accent, colors, song)
            PlayerStyle.WAVEFORM -> WaveformMiniPreview(accent, colors, song)
            PlayerStyle.SPECTRUM -> SpectrumMiniPreview(accent, colors, song)
            PlayerStyle.ORBIT -> OrbitMiniPreview(accent, colors, song)
            PlayerStyle.HERO -> HeroMiniPreview(accent, colors, song)
            PlayerStyle.NEON -> NeonMiniPreview(accent, song)
            PlayerStyle.GESTURE -> GestureMiniPreview(accent, colors, song)
        }
    }
}

@Composable
private fun PreviewArtworkThumb(
    song: Song?,
    accent: Color,
    colors: EditorialColors,
    cornerRadius: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    if (song != null) {
        ArtworkAlbumView(
            song = song,
            modifier = modifier,
            cornerRadius = cornerRadius,
            isPlaying = false
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.65f), colors.accentPill)))
        )
    }
}

@Composable
private fun MiniTransportRow(accent: Color, secondary: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(secondary))
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
        Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(secondary))
    }
}

@Composable
private fun ClassicMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        PreviewArtworkThumb(
            song = song,
            accent = accent,
            colors = colors,
            cornerRadius = 8.dp,
            modifier = Modifier.fillMaxWidth(0.58f).aspectRatio(1f)
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.72f).height(3.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.85f)))
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(2.dp).clip(CircleShape).background(colors.textSecondary.copy(alpha = 0.5f)))
        }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(colors.cardBorder)) {
            Box(modifier = Modifier.fillMaxWidth(0.48f).height(2.dp).background(accent))
        }
        MiniTransportRow(accent, colors.textSecondary.copy(alpha = 0.6f))
    }
}

@Composable
private fun MinimalMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        PreviewArtworkThumb(
            song = song,
            accent = accent,
            colors = colors,
            cornerRadius = 7.dp,
            modifier = Modifier.size(30.dp)
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.width(34.dp).height(2.5.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.8f)))
            Box(modifier = Modifier.width(22.dp).height(2.dp).clip(CircleShape).background(colors.textMuted))
        }
        MiniTransportRow(accent, colors.textMuted)
    }
}

@Composable
private fun MaterialYouMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = colors.accentPill.copy(alpha = 0.45f)
        ) {
            Box(modifier = Modifier.padding(4.dp), contentAlignment = Alignment.Center) {
                PreviewArtworkThumb(
                    song = song,
                    accent = accent,
                    colors = colors,
                    cornerRadius = 9.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceTinted))
            Box(modifier = Modifier.size(width = 16.dp, height = 10.dp).clip(RoundedCornerShape(5.dp)).background(accent))
            Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(3.dp)).background(colors.surfaceTinted))
        }
    }
}

@Composable
private fun GlassMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.5f), colors.accentPill.copy(alpha = 0.55f), colors.surface)))
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PreviewArtworkThumb(
                song = song,
                accent = accent,
                colors = colors,
                cornerRadius = 8.dp,
                modifier = Modifier.fillMaxWidth(0.75f).weight(1f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface.copy(alpha = 0.72f))
                    .border(0.6.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                MiniTransportRow(accent, colors.textPrimary.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun EditorialMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .clip(RoundedCornerShape(6.dp))
                .border(0.5.dp, colors.cardBorder, RoundedCornerShape(6.dp))
        ) {
            PreviewArtworkThumb(song = song, accent = accent, colors = colors, cornerRadius = 6.dp, modifier = Modifier.fillMaxSize())
        }
        Spacer(modifier = Modifier.height(3.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surface)
                .padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(3.dp).clip(CircleShape).background(colors.textPrimary))
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(2.dp).clip(CircleShape).background(accent))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(26.dp).height(1.5.dp).background(accent))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
            }
        }
    }
}

@Composable
private fun CanvasMiniPreview(accent: Color, song: Song?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
    ) {
        if (song != null) {
            ArtworkAlbumView(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 10.dp)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.78f), Color(0xFF1A1B24), Color.Black)))
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(5.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(Color.White))
            Box(modifier = Modifier.width(18.dp).height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
                Box(modifier = Modifier.width(24.dp).height(1.5.dp).background(Color.White.copy(alpha = 0.45f)))
            }
        }
    }
}

@Composable
private fun BentoMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1.2f),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            PreviewArtworkThumb(
                song = song,
                accent = accent,
                colors = colors,
                cornerRadius = 5.dp,
                modifier = Modifier.weight(1.1f).fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .background(colors.surface)
                    .padding(3.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(2.5.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.8f)))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.dp).clip(CircleShape).background(accent))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(1.5.dp).clip(CircleShape).background(colors.textSecondary.copy(alpha = 0.5f)))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.4f)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surface)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(colors.cardBorder)) {
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(1.5.dp).background(accent))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(0.55f),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(4.dp)).background(colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(colors.textSecondary.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun PosterMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .border(0.6.dp, colors.cardBorder, RoundedCornerShape(8.dp))
            .padding(3.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        PreviewArtworkThumb(
            song = song,
            accent = accent,
            colors = colors,
            cornerRadius = 5.dp,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = song?.title?.uppercase()?.take(8) ?: "POSTER",
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Clip
        )
        Box(modifier = Modifier.fillMaxWidth(0.55f).height(2.dp).background(accent))
    }
}

@Composable
private fun PolaroidMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .fillMaxHeight(0.86f)
                .rotate(-4f),
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            shadowElevation = 4.dp,
            border = BorderStroke(0.5.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(3.5.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                PreviewArtworkThumb(
                    song = song,
                    accent = accent,
                    colors = colors,
                    cornerRadius = 3.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(modifier = Modifier.fillMaxWidth(0.65f).height(2.dp).background(Color.Black.copy(alpha = 0.65f)))
            }
        }
    }
}

@Composable
private fun VinylMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xFF14151B))
                .border(1.5.dp, accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(32.dp).border(0.6.dp, Color.White.copy(alpha = 0.18f), CircleShape))
            if (song != null) {
                ArtworkAlbumView(
                    song = song,
                    modifier = Modifier.size(20.dp),
                    isVinylRotating = true,
                    isPlaying = false
                )
            } else {
                Box(modifier = Modifier.size(18.dp).clip(CircleShape).background(accent))
            }
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF14151B)))
        }
        MiniTransportRow(accent, colors.textMuted)
    }
}

@Composable
private fun DialMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(54.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 3.dp.toPx()

            drawCircle(color = colors.cardBorder, radius = radius, style = Stroke(width = 3.dp.toPx()))
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 225f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        }
        if (song != null) {
            ArtworkAlbumView(
                song = song,
                modifier = Modifier.size(34.dp),
                isVinylRotating = true,
                isPlaying = false
            )
        } else {
            Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.4f)))
        }
    }
}

@Composable
private fun MorphMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    val morphShape = RoundedCornerShape(topStart = 18.dp, topEnd = 8.dp, bottomEnd = 18.dp, bottomStart = 8.dp)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(morphShape)
                .border(1.5.dp, accent, morphShape)
        ) {
            PreviewArtworkThumb(
                song = song,
                accent = accent,
                colors = colors,
                cornerRadius = 10.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun StickerMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .rotate(-4f)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.surface)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(9.dp))
                .padding(2.dp)
        ) {
            PreviewArtworkThumb(song = song, accent = accent, colors = colors, cornerRadius = 7.dp, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(4.dp))
                .background(accent)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(colors.onAccentStrong))
        }
    }
}

@Composable
private fun WaveformMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PreviewArtworkThumb(song = song, accent = accent, colors = colors, cornerRadius = 5.dp, modifier = Modifier.size(18.dp))
            Box(modifier = Modifier.weight(1f).height(2.5.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.7f)))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(20.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val heights = listOf(0.3f, 0.7f, 0.95f, 0.5f, 0.85f, 0.4f, 0.75f, 0.9f, 0.45f, 0.6f, 0.35f)
            heights.forEachIndexed { idx, h ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(h)
                        .clip(CircleShape)
                        .background(if (idx < 6) accent else colors.cardBorder)
                )
            }
        }
        MiniTransportRow(accent, colors.textMuted)
    }
}

@Composable
private fun SpectrumMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PreviewArtworkThumb(song = song, accent = accent, colors = colors, cornerRadius = 5.dp, modifier = Modifier.size(18.dp))
            Box(modifier = Modifier.weight(1f).height(2.5.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.7f)))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(22.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            val bars = listOf(0.35f, 0.65f, 0.9f, 0.55f, 0.95f, 0.75f, 0.45f, 0.8f, 0.5f, 0.3f)
            bars.forEach { frac ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(frac)
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(Brush.verticalGradient(listOf(accent, colors.accentPill)))
                )
            }
        }
        MiniTransportRow(accent, colors.textMuted)
    }
}

@Composable
private fun OrbitMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(54.dp)) {
            val r = size.minDimension / 2f - 4.dp.toPx()
            drawCircle(color = colors.cardBorder, radius = r, style = Stroke(1.5.dp.toPx()))
            drawArc(
                color = accent,
                startAngle = -110f,
                sweepAngle = 210f,
                useCenter = false,
                style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(size.width / 2f - r, size.height / 2f - r),
                size = Size(r * 2, r * 2)
            )
            drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(size.width / 2f + r * 0.75f, size.height / 2f - r * 0.65f))
        }
        if (song != null) {
            ArtworkAlbumView(song = song, modifier = Modifier.size(30.dp), isVinylRotating = true, isPlaying = false)
        } else {
            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(accent.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun HeroMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .clip(RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            PreviewArtworkThumb(song = song, accent = accent, colors = colors, cornerRadius = 7.dp, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier.size(16.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(colors.onAccentStrong))
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.75f).height(3.dp).clip(CircleShape).background(colors.textPrimary))
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(2.dp).clip(CircleShape).background(colors.textSecondary.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun NeonMiniPreview(accent: Color, song: Song?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.5.dp, accent, RoundedCornerShape(9.dp))
                .padding(2.dp)
        ) {
            if (song != null) {
                ArtworkAlbumView(song = song, modifier = Modifier.fillMaxSize(), cornerRadius = 7.dp)
            } else {
                Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)).background(accent.copy(alpha = 0.35f)))
            }
        }
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(2.dp).clip(CircleShape).background(accent))
        MiniTransportRow(accent, Color.White.copy(alpha = 0.45f))
    }
}

@Composable
private fun GestureMiniPreview(accent: Color, colors: EditorialColors, song: Song?) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.78f)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            PreviewArtworkThumb(song = song, accent = accent, colors = colors, cornerRadius = 8.dp, modifier = Modifier.fillMaxSize())
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.85f)))
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.92f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(colors.surface)
                .border(0.6.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(7.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(20.dp).height(2.5.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.75f)))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
            }
        }
    }
}
