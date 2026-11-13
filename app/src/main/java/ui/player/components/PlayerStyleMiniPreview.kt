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
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Miniature illustration preview for each of the 9 Player Styles.
 * Fully adapts to the active SONORA AESTHETIC UI theme palette.
 */
@Composable
fun PlayerStyleMiniPreview(
    style: PlayerStyle,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    val colors = LocalEditorialColors.current
    val accent = if (isSelected) colors.accentStrong else colors.accentStrong.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) colors.accentStrong else colors.cardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            PlayerStyle.CLASSIC -> ClassicMiniPreview(accent = accent, colors = colors)
            PlayerStyle.GESTURE -> GestureMiniPreview(accent = accent, colors = colors)
            PlayerStyle.EDITORIAL -> EditorialMiniPreview(accent = accent, colors = colors)
            PlayerStyle.CANVAS -> CanvasMiniPreview(accent = accent)
            PlayerStyle.BENTO -> BentoMiniPreview(accent = accent, colors = colors)
            PlayerStyle.STICKER -> StickerMiniPreview(accent = accent, colors = colors)
            PlayerStyle.MORPH -> MorphMiniPreview(accent = accent, colors = colors)
            PlayerStyle.DIAL -> DialMiniPreview(accent = accent, colors = colors)
            PlayerStyle.HERO -> HeroMiniPreview(accent = accent, colors = colors)
        }
    }
}

@Composable
private fun ClassicMiniPreview(accent: Color, colors: EditorialColors) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Brush.linearGradient(listOf(colors.accentPill, accent.copy(alpha = 0.55f))))
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(3.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.8f)))
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(2.dp).clip(CircleShape).background(colors.textSecondary.copy(alpha = 0.5f)))
        }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape).background(colors.cardBorder)) {
            Box(modifier = Modifier.fillMaxWidth(0.45f).height(2.dp).background(accent))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(colors.textSecondary.copy(alpha = 0.6f)))
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(accent))
            Box(modifier = Modifier.size(4.5.dp).clip(CircleShape).background(colors.textSecondary.copy(alpha = 0.6f)))
        }
    }
}

@Composable
private fun GestureMiniPreview(accent: Color, colors: EditorialColors) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.76f)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), colors.surfaceTinted))),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.7f)))
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.7f)))
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

@Composable
private fun EditorialMiniPreview(accent: Color, colors: EditorialColors) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f)
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), colors.accentPill)))
                .border(0.5.dp, colors.cardBorder, RoundedCornerShape(5.dp))
                .padding(3.dp)
        ) {
            Box(modifier = Modifier.width(14.dp).height(2.dp).background(accent))
        }
        Spacer(modifier = Modifier.height(3.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.9f)
                .clip(RoundedCornerShape(5.dp))
                .background(colors.surface)
                .padding(3.dp),
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
private fun CanvasMiniPreview(accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.75f), Color(0xFF1A1B24), Color.Black)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(0.6f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.width(28.dp).height(3.dp).clip(CircleShape).background(Color.White))
            Box(modifier = Modifier.width(18.dp).height(2.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.55f)))
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
private fun BentoMiniPreview(accent: Color, colors: EditorialColors) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1.2f),
            horizontalArrangement = Arrangement.spacedBy(2.5.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), colors.accentPill)))
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
                .weight(0.45f)
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
                    .background(colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(colors.textSecondary.copy(alpha = 0.7f)))
            }
        }
    }
}

@Composable
private fun StickerMiniPreview(accent: Color, colors: EditorialColors) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .rotate(-4f)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surface)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(8.dp))
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Brush.linearGradient(listOf(accent, colors.accentPill)))
            )
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
private fun MorphMiniPreview(accent: Color, colors: EditorialColors) {
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
                brush = Brush.radialGradient(colors = listOf(accent.copy(alpha = 0.6f), colors.accentPill))
            )
            drawPath(
                path = path,
                color = accent,
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.textPrimary))
    }
}

@Composable
private fun DialMiniPreview(accent: Color, colors: EditorialColors) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(46.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 3.dp.toPx()

            drawCircle(
                color = colors.cardBorder,
                radius = radius,
                style = Stroke(width = 3.dp.toPx())
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 220f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(accent.copy(alpha = 0.45f), colors.surfaceTinted)),
                radius = radius * 0.62f
            )
            drawCircle(
                color = colors.textPrimary,
                radius = 2.dp.toPx(),
                center = center
            )
        }
    }
}

@Composable
private fun HeroMiniPreview(accent: Color, colors: EditorialColors) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.2f)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.45f), colors.surfaceTinted))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(accent),
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
