package ui.player.components

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MiniPlayerStyle
import com.example.model.PlayerStyle
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

@Composable
internal fun ExtendedPlayerStyleMiniPreview(
    style: PlayerStyle,
    accent: Color,
    colors: EditorialColors,
    song: Song?
) {
    when (style) {
        PlayerStyle.AMOLED_MINIMAL -> {
            Column(
                modifier = Modifier.fillMaxSize().background(Color.Black).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .border(0.75.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    MiniArtThumb(song, Color.White, 6.dp, Modifier.fillMaxSize())
                }
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(2.dp).background(Color.White.copy(alpha = 0.7f)))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.White))
                    Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
                }
            }
        }

        PlayerStyle.PREMIUM_GRADIENT -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.72f), Color(0xFF121521))))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                MiniArtThumb(song, accent, 10.dp, Modifier.size(48.dp))
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color.White))
            }
        }

        PlayerStyle.LIQUID_GLASS -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), Color(0xFF0E1320), accent.copy(alpha = 0.30f))))
                    .padding(5.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .border(1.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(4.dp)
                    ) {
                        MiniArtThumb(song, accent, 8.dp, Modifier.fillMaxSize())
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(0.75.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
        }

        PlayerStyle.NEUMORPHISM -> {
            val bg = if (colors.isDark) Color(0xFF20242E) else Color(0xFFE9EEF6)
            val rim = if (colors.isDark) Color(0xFF2E3442) else Color.White
            Column(
                modifier = Modifier.fillMaxSize().background(bg).padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(1.5.dp, rim, RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    MiniArtThumb(song, accent, 8.dp, Modifier.fillMaxSize())
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, rim, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
                }
            }
        }

        PlayerStyle.DYNAMIC_COLOR -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.42f))))
                    .padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .padding(4.dp)
                ) {
                    MiniArtThumb(song, accent, 7.dp, Modifier.fillMaxSize())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(4) {
                        Box(modifier = Modifier.size(width = 8.dp, height = 3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f)))
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.28f)))
            }
        }

        PlayerStyle.FLOATING_ARTWORK -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.8f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.35f))
                    )
                    MiniArtThumb(song, accent, 10.dp, Modifier.size(46.dp))
                }
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(2.5.dp).clip(CircleShape).background(accent))
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(accent))
            }
        }

        PlayerStyle.CASSETTE -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1F2128))
                        .padding(5.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0xFFF1E8D8))
                            .padding(4.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(accent))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(22.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF121318)),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.White).border(2.dp, accent, CircleShape))
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.White).border(2.dp, accent, CircleShape))
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(3.dp).clip(CircleShape).background(accent))
            }
        }

        PlayerStyle.CD_PLAYER -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                    MiniArtThumb(song, accent, 27.dp, Modifier.fillMaxSize().clip(CircleShape))
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = size.minDimension / 2f
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, Color(0x66FFFFFF), Color(0x5580E8FF), Color(0x55FF7AE8), Color.Transparent),
                                center = c
                            ),
                            radius = r,
                            center = c
                        )
                        drawCircle(Color(0xFFDDE3EC), radius = r * 0.28f, center = c)
                        drawCircle(colors.background, radius = r * 0.14f, center = c)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(2.5.dp).clip(CircleShape).background(accent))
            }
        }

        PlayerStyle.RETRO_WALKMAN -> {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF232730)).padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF2B2412))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("01:42 WM-88", fontFamily = FontFamily.Monospace, fontSize = 7.sp, color = Color(0xFFFFC145), fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF14171D))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniArtThumb(song, Color(0xFFFF6D2D), 4.dp, Modifier.size(22.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3E4554)))
                    Box(modifier = Modifier.weight(1.3f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFFF6D2D)))
                    Box(modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF3E4554)))
                }
            }
        }

        PlayerStyle.ARTWORK_CAROUSEL -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.weight(0.28f).fillMaxHeight(0.75f).clip(RoundedCornerShape(6.dp)).background(colors.surfaceTinted))
                    MiniArtThumb(song, accent, 8.dp, Modifier.weight(1f).fillMaxHeight())
                    Box(modifier = Modifier.weight(0.28f).fillMaxHeight(0.75f).clip(RoundedCornerShape(6.dp)).background(colors.surfaceTinted))
                }
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(2.5.dp).clip(CircleShape).background(accent))
                Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(accent))
            }
        }

        PlayerStyle.AUDIO_VISUALIZER -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0A0D16)).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val c = Offset(size.width / 2f, size.height * 0.44f)
                    val r = size.minDimension * 0.26f
                    for (i in 0 until 20) {
                        val a = (i / 20.0) * 2.0 * Math.PI
                        val len = if (i % 2 == 0) 9.dp.toPx() else 5.dp.toPx()
                        drawLine(
                            color = accent,
                            start = Offset(c.x + (kotlin.math.cos(a) * r).toFloat(), c.y + (kotlin.math.sin(a) * r).toFloat()),
                            end = Offset(c.x + (kotlin.math.cos(a) * (r + len)).toFloat(), c.y + (kotlin.math.sin(a) * (r + len)).toFloat()),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }
                MiniArtThumb(song, accent, 14.dp, Modifier.size(28.dp).clip(CircleShape))
            }
        }

        PlayerStyle.AURORA -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF071B24), Color(0xFF163D49), Color(0xFF090D18))))
                    .padding(6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    MiniArtThumb(song, Color(0xFF2EE6C5), 10.dp, Modifier.size(44.dp).border(1.dp, Color(0xFF2EE6C5), RoundedCornerShape(10.dp)))
                    Box(modifier = Modifier.fillMaxWidth(0.75f).height(2.5.dp).clip(CircleShape).background(Color(0xFF2EE6C5)))
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(0xFF2EE6C5)))
                }
            }
        }

        PlayerStyle.LIQUID_MUSIC -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0C0E18)).padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(accent.copy(alpha = 0.55f), radius = size.minDimension * 0.34f, center = Offset(size.width * 0.36f, size.height * 0.42f))
                    drawCircle(Color(0xFF8B6BFF).copy(alpha = 0.48f), radius = size.minDimension * 0.30f, center = Offset(size.width * 0.65f, size.height * 0.48f))
                }
                MiniArtThumb(song, accent, 18.dp, Modifier.size(36.dp).clip(CircleShape))
            }
        }

        PlayerStyle.PARTICLE_FLOW -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF080912)).padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    for (i in 0..11) {
                        val x = size.width * ((i * 0.19f) % 0.92f + 0.04f)
                        val y = size.height * ((i * 0.27f) % 0.86f + 0.07f)
                        drawCircle(if (i % 2 == 0) accent else Color.White, radius = 2.dp.toPx(), center = Offset(x, y))
                    }
                }
                MiniArtThumb(song, accent, 10.dp, Modifier.size(40.dp).border(1.dp, accent, RoundedCornerShape(10.dp)))
            }
        }

        PlayerStyle.SPATIAL_3D -> {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0B0E18)).padding(5.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawOval(
                        color = accent.copy(alpha = 0.55f),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.54f),
                        size = Size(size.width * 0.84f, size.height * 0.28f),
                        style = Stroke(1.5.dp.toPx())
                    )
                }
                MiniArtThumb(
                    song,
                    accent,
                    10.dp,
                    Modifier
                        .size(40.dp)
                        .graphicsLayer { rotationY = -14f; rotationX = 8f }
                )
            }
        }

        PlayerStyle.CINEMATIC -> {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xFF050507)).padding(5.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(Color.Black))
                MiniArtThumb(song, Color(0xFFE6C27A), 4.dp, Modifier.fillMaxWidth().height(40.dp))
                Box(modifier = Modifier.fillMaxWidth(0.65f).height(2.dp).background(Color(0xFFE6C27A)))
                Box(modifier = Modifier.fillMaxWidth().height(5.dp).background(Color.Black))
            }
        }

        PlayerStyle.MODERN_COMPACT -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.surface).padding(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MiniArtThumb(song, accent, 6.dp, Modifier.size(30.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.textPrimary))
                        Box(modifier = Modifier.fillMaxWidth(0.65f).height(3.dp).clip(CircleShape).background(colors.textMuted))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceTinted))
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(accent))
            }
        }

        PlayerStyle.TYPOGRAPHY_PLAYER -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    MiniArtThumb(song, accent, 5.dp, Modifier.size(20.dp))
                }
                Column {
                    Text("Aa", fontWeight = FontWeight.Black, fontSize = 24.sp, color = colors.textPrimary, lineHeight = 24.sp)
                    Box(modifier = Modifier.width(34.dp).height(3.dp).clip(CircleShape).background(accent))
                }
                Box(modifier = Modifier.fillMaxWidth().height(2.5.dp).clip(CircleShape).background(accent))
            }
        }

        PlayerStyle.SPLIT_LAYOUT -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).background(accent.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    MiniArtThumb(song, accent, 8.dp, Modifier.size(34.dp))
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(0.85f).background(colors.surface).padding(6.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(3.dp).clip(CircleShape).background(accent))
                    Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(accent))
                }
            }
        }

        PlayerStyle.LANDSCAPE_PLAYER -> {
            Row(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniArtThumb(song, accent, 8.dp, Modifier.size(44.dp))
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(0.8f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(colors.textPrimary))
                    Box(modifier = Modifier.fillMaxWidth(0.75f).height(3.dp).clip(CircleShape).background(accent))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.textMuted))
                        Box(modifier = Modifier.size(11.dp).clip(CircleShape).background(accent))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.textMuted))
                    }
                }
            }
        }

        PlayerStyle.LYRICS_FOCUS -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(6.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    MiniArtThumb(song, accent, 4.dp, Modifier.size(16.dp))
                    Box(modifier = Modifier.width(34.dp).height(3.dp).clip(CircleShape).background(colors.textPrimary))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.fillMaxWidth(0.72f).height(3.dp).clip(CircleShape).background(colors.textMuted.copy(alpha = 0.5f)))
                    Box(modifier = Modifier.fillMaxWidth(0.92f).height(5.dp).clip(CircleShape).background(accent))
                    Box(modifier = Modifier.fillMaxWidth(0.65f).height(3.dp).clip(CircleShape).background(colors.textMuted.copy(alpha = 0.5f)))
                }
                Box(modifier = Modifier.fillMaxWidth().height(2.5.dp).clip(CircleShape).background(accent))
            }
        }

        else -> {
            MiniArtThumb(song, accent, 10.dp, Modifier.size(46.dp))
        }
    }
}

@Composable
private fun MiniArtThumb(song: Song?, accent: Color, cornerRadius: Dp, modifier: Modifier = Modifier) {
    if (song != null) {
        ArtworkAlbumView(song = song, modifier = modifier, cornerRadius = cornerRadius, isPlaying = false)
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.35f))))
        )
    }
}

/**
 * Miniature visual preview thumbnail for each of the 20 Mini Player styles.
 */
@Composable
fun MiniPlayerStyleMiniPreview(
    style: MiniPlayerStyle,
    isSelected: Boolean,
    song: Song? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val accent = if (isSelected) colors.accentStrong else colors.accentStrong.copy(alpha = 0.8f)

    val containerBg = when (style) {
        MiniPlayerStyle.AMOLED_BAR -> Color.Black
        MiniPlayerStyle.NEON_MINI -> Color(0xFF080912)
        MiniPlayerStyle.CASSETTE_MINI -> Color(0xFF1F222A)
        MiniPlayerStyle.DYNAMIC_ARTWORK_BAR, MiniPlayerStyle.GRADIENT_BAR -> accent.copy(alpha = 0.75f)
        MiniPlayerStyle.BLUR_CARD -> Color(0xFF151926)
        MiniPlayerStyle.MATERIAL_YOU_MINI -> colors.accentPill
        else -> colors.surface
    }
    val borderColor = when {
        style == MiniPlayerStyle.NEON_MINI -> Color(0xFF39F3FF)
        isSelected -> colors.accentStrong
        else -> colors.cardBorder
    }
    val barShape = when (style) {
        MiniPlayerStyle.EDGE_TO_EDGE_MINI -> RoundedCornerShape(4.dp)
        MiniPlayerStyle.CIRCULAR_PLAYER, MiniPlayerStyle.FLOATING_CORNER_PLAYER -> RoundedCornerShape(24.dp)
        else -> RoundedCornerShape(14.dp)
    }
    val inkPrimary = when (style) {
        MiniPlayerStyle.AMOLED_BAR, MiniPlayerStyle.NEON_MINI, MiniPlayerStyle.CASSETTE_MINI,
        MiniPlayerStyle.DYNAMIC_ARTWORK_BAR, MiniPlayerStyle.GRADIENT_BAR, MiniPlayerStyle.BLUR_CARD -> Color.White
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background)
            .padding(horizontal = if (style == MiniPlayerStyle.EDGE_TO_EDGE_MINI) 0.dp else 6.dp, vertical = 6.dp),
        contentAlignment = if (style == MiniPlayerStyle.FLOATING_CORNER_PLAYER) Alignment.CenterEnd else Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (style == MiniPlayerStyle.CIRCULAR_PLAYER || style == MiniPlayerStyle.FLOATING_CORNER_PLAYER) {
                        Modifier.width(116.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
                .height(38.dp)
                .clip(barShape)
                .background(containerBg)
                .border(1.dp, borderColor, barShape)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            when (style) {
                MiniPlayerStyle.MINIMAL_TEXT -> Unit
                MiniPlayerStyle.VINYL_MINI -> {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF14151A)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(accent))
                    }
                }
                MiniPlayerStyle.CASSETTE_MINI -> {
                    Row(
                        modifier = Modifier.size(width = 28.dp, height = 18.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF101218)),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                    }
                }
                MiniPlayerStyle.CD_MINI -> {
                    Box(
                        modifier = Modifier.size(22.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(accent, Color(0xFF80E8FF), Color(0xFFFF8DF0), accent))),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.background))
                    }
                }
                MiniPlayerStyle.CIRCULAR_PLAYER, MiniPlayerStyle.FLOATING_CORNER_PLAYER -> {
                    Box(
                        modifier = Modifier.size(24.dp).border(2.dp, accent, CircleShape).padding(3.dp)
                    ) {
                        MiniArtThumb(song, accent, 10.dp, Modifier.fillMaxSize().clip(CircleShape))
                    }
                }
                else -> {
                    MiniArtThumb(
                        song = song,
                        accent = accent,
                        cornerRadius = if (style == MiniPlayerStyle.FLOATING_PILL || style == MiniPlayerStyle.WAVE_MINI) 11.dp else 6.dp,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(modifier = Modifier.fillMaxWidth(0.75f).height(4.dp).clip(CircleShape).background(inkPrimary.copy(alpha = 0.85f)))
                if (style == MiniPlayerStyle.WAVE_MINI || style == MiniPlayerStyle.SPECTRUM_MINI) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                        listOf(4, 8, 5, 9, 6, 8, 4).forEach { h ->
                            Box(modifier = Modifier.size(width = 3.dp, height = h.dp).clip(CircleShape).background(accent))
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth(0.5f).height(2.5.dp).clip(CircleShape).background(inkPrimary.copy(alpha = 0.45f)))
                }
            }

            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(if (style == MiniPlayerStyle.MATERIAL_YOU_MINI || style == MiniPlayerStyle.ARTWORK_FOCUS) RoundedCornerShape(5.dp) else CircleShape)
                    .background(if (style == MiniPlayerStyle.NEON_MINI) Color(0xFFFF4EDB) else accent)
            )
        }
    }
}
