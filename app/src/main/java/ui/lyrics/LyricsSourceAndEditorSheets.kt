package ui.lyrics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Source
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.lyrics.LrcParser
import domain.lyrics.LyricsDocument
import domain.lyrics.LyricsSearchCandidate
import domain.lyrics.LyricsSourceOption
import domain.lyrics.LyricsSourceType
import domain.lyrics.LyricsSyncType
import ui.theme.EditorialColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsSourceManagerSheet(
    document: LyricsDocument?,
    availableSources: List<LyricsSourceOption>,
    accentColor: Color,
    colors: EditorialColors,
    onSelectSource: (LyricsSourceType) -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onImportLrc: () -> Unit,
    onOpenEditor: () -> Unit,
    onSaveLocally: () -> Unit,
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("lyrics_source_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Source,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "LYRICS SOURCES & TOOLS",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            color = accentColor
                        )
                        Text(
                            text = document?.let { "${it.sourceLabel} • ${it.syncType.displayLabel}" }
                                ?: "No active lyrics loaded",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.5.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close source manager",
                        tint = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Action Tiles Row 1: Search, Refresh, Import LRC
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SourceQuickActionCard(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    accentColor = accentColor,
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_action_search"),
                    onClick = {
                        onDismiss()
                        onOpenSearch()
                    }
                )
                SourceQuickActionCard(
                    icon = Icons.Rounded.Refresh,
                    label = "Refresh",
                    accentColor = accentColor,
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_action_refresh"),
                    onClick = {
                        onRefresh()
                        onDismiss()
                    }
                )
                SourceQuickActionCard(
                    icon = Icons.Rounded.FileOpen,
                    label = "Import LRC",
                    accentColor = accentColor,
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_action_import_lrc"),
                    onClick = {
                        onDismiss()
                        onImportLrc()
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick Action Tiles Row 2: Edit Lyrics, Save Locally
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SourceQuickActionCard(
                    icon = Icons.Rounded.Edit,
                    label = "Edit Lyrics",
                    accentColor = accentColor,
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_action_edit"),
                    onClick = {
                        onDismiss()
                        onOpenEditor()
                    }
                )
                SourceQuickActionCard(
                    icon = Icons.Rounded.Save,
                    label = "Save Locally",
                    accentColor = accentColor,
                    colors = colors,
                    enabled = document != null && document.hasLyrics,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_action_save_local"),
                    onClick = {
                        onSaveLocally()
                        onDismiss()
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "SELECT SOURCE",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 1.1.sp,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            val sourcesToRender = if (availableSources.isNotEmpty()) {
                availableSources
            } else {
                listOf(
                    LyricsSourceOption(LyricsSourceType.EMBEDDED_TAG, "Embedded Audio Metadata", "Check audio file tags", true, document?.sourceType == LyricsSourceType.EMBEDDED_TAG),
                    LyricsSourceOption(LyricsSourceType.LOCAL_SIDECAR_LRC, "Local Sidecar LRC", "Matching .lrc / .elrc file", true, document?.sourceType == LyricsSourceType.LOCAL_SIDECAR_LRC),
                    LyricsSourceOption(LyricsSourceType.ONLINE_LRCLIB, "Online Lyrics (LRCLIB)", "Open synchronized lyrics API", true, document?.sourceType == LyricsSourceType.ONLINE_LRCLIB),
                    LyricsSourceOption(LyricsSourceType.IMPORTED_LRC, "Imported / Saved Locally", "Stored in local database", false, document?.sourceType == LyricsSourceType.IMPORTED_LRC || document?.sourceType == LyricsSourceType.LOCAL_EDITED),
                    LyricsSourceOption(LyricsSourceType.PLAIN_TEXT, "Local Plain Text (.txt)", "Unsynced text fallback", false, document?.sourceType == LyricsSourceType.PLAIN_TEXT)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sourcesToRender.forEach { option ->
                    Surface(
                        onClick = {
                            onSelectSource(option.sourceType)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (option.isActive) accentColor.copy(alpha = 0.14f) else colors.surfaceElevated,
                        border = BorderStroke(
                            width = if (option.isActive) 1.25.dp else 1.dp,
                            color = if (option.isActive) accentColor else colors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("lyrics_source_option_${option.sourceType.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (option.isActive) accentColor else colors.textPrimary
                                )
                                Text(
                                    text = option.subtitle,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.5.sp,
                                    color = colors.textSecondary
                                )
                            }
                            if (option.isActive) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Active source",
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else if (option.isAvailable) {
                                Text(
                                    text = "Use",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun SourceQuickActionCard(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    colors: EditorialColors,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceElevated.copy(alpha = if (enabled) 1f else 0.45f),
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = modifier.heightIn(min = 66.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) accentColor else colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
                color = if (enabled) colors.textPrimary else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsOnlineSearchSheet(
    initialQuery: String,
    results: List<LyricsSearchCandidate>,
    isSearching: Boolean,
    accentColor: Color,
    colors: EditorialColors,
    onSearch: (String) -> Unit,
    onSelectCandidate: (LyricsSearchCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }

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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("lyrics_search_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SEARCH ONLINE LYRICS",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = accentColor
                    )
                    Text(
                        text = "Powered by open LRCLIB catalog",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close search", tint = colors.textPrimary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Song title & artist", fontFamily = PlusJakartaFontFamily) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = colors.cardBorder,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("lyrics_search_input")
                )
                Button(
                    onClick = { onSearch(query) },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("lyrics_search_submit")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search lyrics",
                        tint = Color(0xFF101014)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isSearching) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accentColor)
                }
            } else if (results.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Enter a track name and tap Search to browse synced & plain lyrics.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(results, key = { it.id }) { candidate ->
                        Surface(
                            onClick = {
                                onSelectCandidate(candidate)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("lyrics_search_result_${candidate.id}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title.ifBlank { "Untitled" },
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            append(candidate.artist.ifBlank { "Unknown Artist" })
                                            if (candidate.album.isNotBlank()) append(" • ${candidate.album}")
                                            if (candidate.durationSec > 0) {
                                                val m = candidate.durationSec / 60
                                                val s = candidate.durationSec % 60
                                                append(String.format(java.util.Locale.US, " • %d:%02d", m, s))
                                            }
                                        },
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = if (candidate.hasSyncedLyrics) accentColor.copy(alpha = 0.2f) else colors.surface,
                                    border = BorderStroke(
                                        0.75.dp,
                                        if (candidate.hasSyncedLyrics) accentColor else colors.cardBorder
                                    )
                                ) {
                                    Text(
                                        text = when {
                                            candidate.hasSyncedLyrics -> "SYNCED"
                                            candidate.instrumental -> "INSTR"
                                            else -> "PLAIN"
                                        },
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (candidate.hasSyncedLyrics) accentColor else colors.textSecondary,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsEditorSheet(
    initialRawText: String,
    currentPositionMs: Long,
    accentColor: Color,
    colors: EditorialColors,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember(initialRawText) { mutableStateOf(initialRawText) }
    val previewParse = remember(text) { LrcParser.parse(text) }

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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("lyrics_editor_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "EDIT & SYNC LYRICS",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.2.sp,
                        color = accentColor
                    )
                    Text(
                        text = "Detected: ${previewParse.syncType.displayLabel} (${previewParse.lines.size} lines)",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close editor", tint = colors.textPrimary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val stamp = LrcParser.formatLrcTimestamp(currentPositionMs)
                        text = if (text.isBlank() || text.endsWith("\n")) "$text$stamp " else "$text\n$stamp "
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("lyrics_editor_insert_timestamp")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Insert ${LrcParser.formatLrcTimestamp(currentPositionMs)}",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.textPrimary
                    )
                }

                Text(
                    text = "Supports [mm:ss.xx] & <mm:ss.xx>",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = {
                    Text(
                        "[00:12.00]Paste or type LRC / plain lyrics here...",
                        fontFamily = PlusJakartaFontFamily
                    )
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    lineHeight = 20.sp
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = colors.cardBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .testTag("lyrics_editor_textfield")
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", fontFamily = PlusJakartaFontFamily, color = colors.textSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(text)
                        onDismiss()
                    },
                    enabled = text.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("lyrics_editor_save_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Save,
                        contentDescription = null,
                        tint = Color(0xFF101014),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Save Locally",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF101014)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
