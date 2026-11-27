package ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaFontFamily

/**
 * Container-width breakpoints used by SONORA's adaptive Compose layouts
 * (derived from BoxWithConstraints maxWidth + active system fontScale).
 */
enum class SonoraWidthTier {
    ULTRA_COMPACT, // < 340.dp (e.g. 320dp phones or heavy font scaling)
    COMPACT,       // 340.dp .. 389.dp (e.g. 360dp phones)
    STANDARD,      // 390.dp .. 479.dp (e.g. 393dp–412dp phones)
    EXPANDED       // >= 480.dp (large phones, landscape, foldables, tablets)
}

fun resolveSonoraWidthTier(maxWidth: Dp, fontScale: Float = 1f): SonoraWidthTier {
    val effectiveWidthDp = (maxWidth.value / fontScale.coerceIn(0.85f, 1.6f))
    return when {
        effectiveWidthDp < 340f -> SonoraWidthTier.ULTRA_COMPACT
        effectiveWidthDp < 390f -> SonoraWidthTier.COMPACT
        effectiveWidthDp < 480f -> SonoraWidthTier.STANDARD
        else -> SonoraWidthTier.EXPANDED
    }
}

/**
 * Single-line non-wrapping badge pill for short technical/source labels
 * (e.g. ONLINE, LOCAL, OPUS, FLAC, WAV, HI-RES, YT MUSIC, HI-FI).
 * Guarantees that short labels never split into vertical letters across any screen width.
 */
@Composable
fun AdaptiveSingleLineBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    cornerRadius: Dp = 6.dp,
    fontSize: TextUnit = 9.sp,
    fontFamily: FontFamily = PlusJakartaFontFamily,
    fontWeight: FontWeight = FontWeight.Bold,
    contentPadding: PaddingValues = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
) {
    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        border = border,
        modifier = modifier.wrapContentWidth()
    ) {
        Text(
            text = text,
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(contentPadding)
        )
    }
}
