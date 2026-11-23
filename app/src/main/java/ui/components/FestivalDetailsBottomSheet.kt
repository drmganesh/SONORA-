package ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.festival.ActiveFestivalResolution
import domain.festival.FestivalEffectRegistry
import domain.festival.ResolvedFestivalOccurrence
import ui.theme.EditorialColors

/**
 * Details Bottom Sheet opened when the user taps the Home screen Festival Indicator.
 * Displays all recognized festivals happening today along with regional names, tithi,
 * multi-day span, active visual effect, calendar source verification, and disagreement notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FestivalDetailsBottomSheet(
    resolution: ActiveFestivalResolution,
    colors: EditorialColors,
    onDismiss: () -> Unit,
    onOpenFestivalSettings: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("festival_details_sheet")
    ) {
        FestivalDetailsSheetContent(
            resolution = resolution,
            colors = colors,
            onOpenFestivalSettings = {
                onDismiss()
                onOpenFestivalSettings()
            }
        )
    }
}

@Composable
fun FestivalDetailsSheetContent(
    resolution: ActiveFestivalResolution,
    colors: EditorialColors,
    onOpenFestivalSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding()
            .testTag("festival_details_sheet_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (resolution.allActive.size > 1) {
                "Today's Festivals (${resolution.allActive.size})"
            } else {
                "Festival Atmosphere"
            },
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            color = colors.textPrimary
        )

        resolution.allActive.forEachIndexed { index, occurrence ->
            FestivalOccurrenceDetailCard(
                occurrence = occurrence,
                isPrimary = index == 0,
                colors = colors
            )
        }

        Button(
            onClick = onOpenFestivalSettings,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentStrong,
                contentColor = colors.onAccentStrong
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("festival_details_open_settings_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Customize Festival Atmosphere",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun FestivalOccurrenceDetailCard(
    occurrence: ResolvedFestivalOccurrence,
    isPrimary: Boolean,
    colors: EditorialColors
) {
    val record = occurrence.record
    val descriptor = FestivalEffectRegistry.get(record.effectId)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(
            width = if (isPrimary) 1.dp else 0.75.dp,
            color = if (isPrimary) colors.accentStrong.copy(alpha = 0.45f) else colors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("festival_detail_card_${record.festivalSlug}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = record.iconGlyph,
                    fontSize = 26.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = occurrence.displayNameForRegion,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                    if (record.alternateRegionalNames.isNotEmpty()) {
                        Text(
                            text = record.alternateRegionalNames.joinToString(" • "),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                if (isPrimary) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.accentPill
                    ) {
                        Text(
                            text = "Active",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = colors.onAccentPill,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = record.shortDescription,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = colors.textPrimary
            )

            HorizontalDivider(color = colors.cardBorder)

            // Date & Tithi
            DetailInfoRow(
                icon = Icons.Rounded.CalendarMonth,
                label = "Date",
                value = buildString {
                    if (occurrence.isMultiDay) {
                        append("${record.startDateIso} to ${record.endDateIso} (Day ${occurrence.dayIndex}/${occurrence.totalDays})")
                    } else {
                        append(record.startDateIso)
                    }
                    if (!record.tithi.isNullOrBlank()) {
                        append(" • ")
                        append(record.tithi)
                    }
                },
                colors = colors
            )

            // Region coverage
            DetailInfoRow(
                icon = Icons.Rounded.Public,
                label = "Region",
                value = record.displayRegionSummary(),
                colors = colors
            )

            // Active visual effect
            DetailInfoRow(
                icon = Icons.Rounded.AutoAwesome,
                label = "Atmosphere",
                value = descriptor.displayName,
                colors = colors
            )

            // Calendar source & verification
            DetailInfoRow(
                icon = Icons.Rounded.Verified,
                label = "Calendar Source",
                value = "${record.verificationStatus.label} (${record.sourceName})",
                colors = colors
            )

            if (!record.disagreementNote.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.surface,
                    border = BorderStroke(0.75.dp, colors.cardBorder)
                ) {
                    Text(
                        text = "Regional Calendar Note: ${record.disagreementNote}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    colors: EditorialColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.accentStrong,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = colors.textMuted
            )
            Text(
                text = value,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.5.sp,
                color = colors.textSecondary
            )
        }
    }
}
