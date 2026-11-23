package ui.downloads

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import domain.download.DownloadFormatCandidate
import domain.download.DownloadQualityOption
import domain.download.DownloadQualitySheetData
import domain.download.formatByteCount
import ui.online.OnlineTrackThumbnail
import ui.theme.EditorialColors

@Composable
fun ChooseDownloadLocationDialog(
    reasonMessage: String? = null,
    colors: EditorialColors,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        modifier = Modifier.testTag("choose_download_location_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.accentPill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.FolderOpen,
                    contentDescription = "Choose Download Folder",
                    tint = colors.accentStrong,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = "Choose Download Location",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = colors.textPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select where SONORA should save your downloaded music.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
                if (!reasonMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(0.75.dp, colors.cardBorder)
                    ) {
                        Text(
                            text = reasonMessage,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.accentStrong,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onChooseFolder,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.onAccentStrong
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("choose_folder_button")
            ) {
                Text(
                    text = "Choose Folder",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("cancel_folder_button")
            ) {
                Text(
                    text = "Cancel",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textMuted
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQualitySelectorSheet(
    data: DownloadQualitySheetData,
    colors: EditorialColors,
    onSelectPreset: (DownloadQualityOption) -> Unit,
    onSelectManualCandidate: (String) -> Unit,
    onConfirmDownload: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("download_quality_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OnlineTrackThumbnail(
                    artworkUrl = data.track.artworkUrl,
                    title = data.track.title,
                    colors = colors,
                    size = 54.dp,
                    cornerRadius = 14.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DOWNLOAD TRACK",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = colors.accentStrong
                    )
                    Text(
                        text = data.track.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${data.track.artist} • Saving to ${data.destinationFolderLabel}",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = colors.textPrimary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colors.cardBorder)
            Spacer(modifier = Modifier.height(12.dp))

            when {
                data.isResolvingFormats -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = colors.accentStrong,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Inspecting available audio formats...",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                !data.errorMessage.isNullOrBlank() -> {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surfaceTinted,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Download Unavailable",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = data.errorMessage,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 13.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentPill,
                            contentColor = colors.onAccentPill
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Close", fontFamily = PlusJakartaFontFamily, fontWeight = FontWeight.Bold)
                    }
                }

                else -> {
                    Text(
                        text = "SELECT DOWNLOAD QUALITY",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DownloadQualityOption.entries.forEach { preset ->
                        val isPresetSelected = data.selectedManualStreamId == null && data.selectedPreset == preset
                        val matchedCandidate = data.recommendedByPreset[preset]
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isPresetSelected) colors.accentPill.copy(alpha = 0.7f) else colors.surfaceElevated,
                            border = BorderStroke(
                                width = if (isPresetSelected) 1.25.dp else 0.75.dp,
                                color = if (isPresetSelected) colors.accentStrong else colors.cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onSelectPreset(preset) }
                                .testTag("download_preset_${preset.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isPresetSelected,
                                    onClick = { onSelectPreset(preset) },
                                    colors = RadioButtonDefaults.colors(selectedColor = colors.accentStrong)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.label,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = preset.description,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textSecondary
                                    )
                                    if (matchedCandidate != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = matchedCandidate.summaryLabel,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp,
                                            color = colors.accentStrong
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (data.candidates.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.HighQuality,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "AVAILABLE AUDIO FORMATS (${data.candidates.size})",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp,
                                color = colors.textMuted
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        data.candidates.forEach { candidate ->
                            val selected = data.selectedManualStreamId == candidate.streamId
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) colors.accentPill else colors.surfaceTinted,
                                border = BorderStroke(
                                    width = if (selected) 1.25.dp else 0.75.dp,
                                    color = if (selected) colors.accentStrong else colors.cardBorder
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectManualCandidate(candidate.streamId) }
                                    .testTag("download_format_${candidate.streamId}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${candidate.codecLabel} (.${candidate.containerExtension})",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = colors.textPrimary
                                        )
                                        val meta = buildString {
                                            append(candidate.mimeType)
                                            if (candidate.bitrateKbps > 0) {
                                                append(" • ${candidate.bitrateKbps} kbps")
                                            }
                                            if (candidate.contentLengthBytes > 0L) {
                                                append(" • ${formatByteCount(candidate.contentLengthBytes)}")
                                            }
                                        }
                                        Text(
                                            text = meta,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected Format",
                                            tint = colors.accentStrong,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onConfirmDownload,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.onAccentStrong
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_download_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Download",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
