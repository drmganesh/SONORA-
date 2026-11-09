package ui.player.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.model.PlayerStyle
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraCharcoalBorder
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraDustyRoseLight
import com.example.ui.theme.SonoraSurfaceDark

/**
 * High-fidelity miniature illustration preview for each of the 9 Player Styles.
 * Rendered inside PlayerStyleSelector cards and the circular PlayerStyleWheel.
 */
@Composable
fun PlayerStyleMiniPreview(
    style: PlayerStyle,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val accent = if (isSelected) SonoraDustyRose else SonoraDustyRoseLight.copy(alpha = 0.8f)
    val cardBg = SonoraCharcoal

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) SonoraDustyRose else SonoraCharcoalBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            PlayerStyle.CLASSIC -> ClassicMiniPreview(accent = accent)
            PlayerStyle.GESTURE -> GestureMiniPreview(accent = accent)
            PlayerStyle.EDITORIAL -> EditorialMiniPreview(accent = accent)
            PlayerStyle.CANVAS -> CanvasMiniPreview(accent = accent)
            PlayerStyle.BENTO -> BentoMiniPreview(accent = accent)
            PlayerStyle.STICKER -> StickerMiniPreview(accent = accent)
            PlayerStyle.MORPH -> MorphMiniPreview(accent = accent)
            PlayerStyle.DIAL -> DialMiniPreview(accent = accent)
            PlayerStyle.HERO -> HeroMiniPreview(accent = accent)
        }
    }
}

@Composable
private fun ClassicMiniPreview(accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        // Square artwork
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        listOf(SonoraSurfaceDark, accent.copy(alpha = 0.4f))
                    )
                )
        )
        // Two title lines
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(2.5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
        }
        // Progress line
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(2.dp).background(accent))
        }
        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(accent))
            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
        }
    }
}

@Composable
private fun GestureMiniPreview(accent: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Massive artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.78f)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.45f), SonoraSurfaceDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Horizontal swipe arrows
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
            }
        }
        // Bottom floating glass pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.9f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(SonoraSurfaceDark)
                .border(0.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(7.dp))
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.width(20.dp).height(2.5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
            }
        }
    }
}

@Composable
private fun EditorialMiniPreview(accent: Color) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Upper framed section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.5f), Color(0xFF1E1513))
                    )
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                .padding(3.dp)
        ) {
            Box(modifier = Modifier.width(14.dp).height(2.dp).background(accent))
        }
        Spacer(modifier = Modifier.height(3.dp))
        // Lower stark editorial card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF0A0C0E))
                .padding(3.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.85f).height(3.5.dp).clip(CircleShape).background(Color.White))
            Box(modifier = Modifier.fillMaxWidth(0.5f).height(2.5.dp).clip(CircleShape).background(accent.copy(alpha = 0.8f)))
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
private fun CanvasMiniPreview(accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.7f), Color(0xFF151010), Color.Black)
                )
            )
    ) {
        // Bottom scrim gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.6f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )
        // Overlaid bottom info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(Color.White))
            Box(modifier = Modifier.width(18.dp).height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.5f)))
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
                Box(modifier = Modifier.width(24.dp).height(1.5.dp).background(Color.White.copy(alpha = 0.4f)))
            }
        }
    }
}

@Composable
private fun BentoMiniPreview(accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        // Top row
        Row(
            modifier = Modifier.fillMaxWidth().weight(1.2f),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            // Main Art tile
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.5f), SonoraSurfaceDark)
                        )
                    )
            )
            // Info tile
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .background(SonoraSurfaceDark)
                    .padding(3.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(2.5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.dp).clip(CircleShape).background(accent))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(1.5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
            }
        }
        // Scrubber tile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f)
                .clip(RoundedCornerShape(4.dp))
                .background(SonoraSurfaceDark)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(1.5.dp).background(Color.White.copy(alpha = 0.2f))) {
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(1.5.dp).background(accent))
            }
        }
        // Controls tile
        Row(
            modifier = Modifier.fillMaxWidth().weight(0.55f),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(SonoraSurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.6f)))
            }
        }
    }
}

@Composable
private fun StickerMiniPreview(accent: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Tilted die-cut sticker
        Box(
            modifier = Modifier
                .size(40.dp)
                .rotate(-4f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(1.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent, Color(0xFF2B1C1A))
                        )
                    )
            )
        }
        // Bottom badge pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(RoundedCornerShape(4.dp))
                .background(accent)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color.Black))
        }
    }
}

@Composable
private fun MorphMiniPreview(accent: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(44.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.2f, h * 0.15f)
                cubicTo(w * 0.5f, 0f, w * 0.85f, h * 0.2f, w * 0.9f, h * 0.5f)
                cubicTo(w * 0.95f, h * 0.8f, w * 0.7f, h * 0.95f, w * 0.45f, h * 0.88f)
                cubicTo(w * 0.15f, h * 0.8f, 0f, h * 0.5f, w * 0.2f, h * 0.15f)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.6f), Color(0xFF1E1413))
                )
            )
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
        // Center play dot
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun DialMiniPreview(accent: Color) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(46.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 3.dp.toPx()

            // Outer ring background track
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = radius,
                style = Stroke(width = 3.dp.toPx())
            )
            // Glowing progress arc
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 220f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            // Center artwork circle
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.4f), SonoraSurfaceDark)
                ),
                radius = radius * 0.65f
            )
            // Center play indicator
            drawCircle(
                color = Color.White,
                radius = 2.dp.toPx(),
                center = center
            )
        }
    }
}

@Composable
private fun HeroMiniPreview(accent: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Grand scale artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.5f), Color.Black)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Oversized glowing center play button
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Color.White))
            }
        }
        // Bold title
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.75f).height(3.dp).clip(CircleShape).background(Color.White))
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.4f)))
        }
    }
}
