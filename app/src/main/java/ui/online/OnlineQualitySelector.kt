package ui.online

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.online.AudioQualityPreference
import domain.online.OnlineAudioStreamOption
import domain.online.ResolvedOnlineStreamResult
import ui.theme.EditorialColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineQualitySelectorSheet(
    currentPreference: AudioQualityPreference,
    resolvedStream: ResolvedOnlineStreamResult?,
    isMeteredNetwork: Boolean,
    isNetworkOnline: Boolean,
    colors: EditorialColors,
    onSelectPreference: (AudioQualityPreference) -> Unit,
    onSelectManualStream: (OnlineAudioStreamOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp)
                .testTag("online_quality_selector_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.accentPill),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HighQuality,
                            contentDescription = null,
                            tint = colors.onAccentPill,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Stream Audio Quality",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary
                        )
                        val netLabel = when {
                            !isNetworkOnline -> "Offline"
                            isMeteredNetwork -> "Mobile / Metered Data"
                            else -> "Unmetered / Wi-Fi"
                        }
                        Text(
                            text = "Network: $netLabel",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close quality selector",
                        tint = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "DEFAULT QUALITY MODE",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = colors.textMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioQualityPreference.entries.forEach { pref ->
                    val selected = currentPreference == pref
                    Surface(
                        onClick = {
                            onSelectPreference(pref)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(18.dp),
                        color = if (selected) colors.accentPill.copy(alpha = 0.72f) else colors.surfaceTinted,
                        border = BorderStroke(
                            width = if (selected) 1.25.dp else 0.75.dp,
                            color = if (selected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quality_pref_${pref.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NetworkCheck,
                                contentDescription = null,
                                tint = if (selected) colors.accentStrong else colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pref.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (selected) colors.onAccentPill else colors.textPrimary
                                )
                                Text(
                                    text = pref.description,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = if (selected) colors.onAccentPill.copy(alpha = 0.78f) else colors.textSecondary
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (resolvedStream != null && resolvedStream.availableStreams.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = colors.cardBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "EXTRACTED AUDIO STREAMS (${resolvedStream.providerName.uppercase()})",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Actual streams discovered for \"${resolvedStream.title}\"",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(resolvedStream.availableStreams, key = { it.id }) { option ->
                        val isActive = option.id == resolvedStream.selectedStream.id
                        Surface(
                            onClick = {
                                onSelectManualStream(option)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isActive) colors.accentPill else colors.surfaceElevated,
                            border = BorderStroke(
                                width = 0.75.dp,
                                color = if (isActive) colors.accentStrong else colors.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("manual_stream_${option.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = if (isActive) colors.accentStrong else colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.qualityLabel,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = if (isActive) colors.onAccentPill else colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val details = buildString {
                                        if (option.mimeType.isNotBlank()) append(option.mimeType)
                                        if (option.formatName.isNotBlank()) {
                                            if (isNotEmpty()) append(" • ")
                                            append(option.formatName)
                                        }
                                        if (!option.isAudioOnly) {
                                            if (isNotEmpty()) append(" • ")
                                            append("Progressive AV")
                                        }
                                    }
                                    if (details.isNotBlank()) {
                                        Text(
                                            text = details,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.sp,
                                            color = if (isActive) colors.onAccentPill.copy(alpha = 0.78f) else colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (isActive) {
                                    Text(
                                        text = "ACTIVE",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = colors.accentStrong
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
