package ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PlusJakartaFontFamily
import domain.festival.ActiveFestivalResolution
import ui.theme.EditorialColors

/**
 * Compact, refined festive indicator pill shown on the SONORA Home screen ONLY when a
 * verified Hindu festival is active for the user's selected region.
 * Leaves ZERO empty vertical space when hidden.
 */
@Composable
fun FestivalIndicator(
    resolution: ActiveFestivalResolution,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = resolution.primary ?: return

    val labelText = if (primary.isMultiDay) {
        "${primary.record.iconGlyph} ${primary.indicatorLabel}"
    } else {
        "${primary.record.iconGlyph} ${primary.displayNameForRegion} Today"
    }

    val accessibleDescription = buildString {
        append("Active festival: ")
        append(primary.displayNameForRegion)
        if (resolution.additionalCount > 0) {
            append(", plus ")
            append(resolution.additionalCount)
            append(" more today")
        }
        append(". Tap for festival details.")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = colors.surfaceTinted.copy(alpha = if (colors.isDark) 0.90f else 0.95f),
            border = BorderStroke(0.9.dp, colors.accentStrong.copy(alpha = 0.42f)),
            shadowElevation = 2.dp,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = accessibleDescription }
                .testTag("home_festival_indicator")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = labelText,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.5.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (resolution.additionalCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.accentPill
                    ) {
                        Text(
                            text = "+${resolution.additionalCount}",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.onAccentPill,
                            modifier = Modifier
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("home_festival_indicator_extra_count")
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = colors.accentStrong
                )
            }
        }
    }
}
