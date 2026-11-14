package ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import data.scanner.FolderScannerHelper
import domain.model.FolderInfo
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Editorial Library Folder Management Screen:
 * 1. Blacklist (hide specific folders & subfolders)
 * 2. Multi-Folder Whitelist (include only selected folders & subfolders)
 * 3. Blacklist vs Whitelist Precedence Comparison Card
 * 4. Detected Device Music Directories
 */
@Composable
fun LibraryFolderManagementScreen(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalEditorialColors.current

    val filterSettings by viewModel.folderFilterSettings.collectAsState()
    val allSongs by viewModel.allSongs.collectAsState()
    val rawSongs by viewModel.rawScannedSongs.collectAsState()
    val availableFolders = remember(rawSongs) {
        viewModel.getAvailableDeviceFolders()
    }

    var showFolderPickerDialog by remember { mutableStateOf(false) }
    var folderPickerForWhitelist by remember { mutableStateOf(false) }
    var folderToRemoveConfirm by remember { mutableStateOf<FolderInfo?>(null) }

    val whitelistTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val folderInfo = FolderScannerHelper.resolveUriToFolderInfo(context, uri)
            viewModel.addWhitelistedFolder(folderInfo)
            Toast.makeText(context, "Added \"${folderInfo.name}\" to Whitelist", Toast.LENGTH_SHORT).show()
        }
    }

    val blacklistTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val folderInfo = FolderScannerHelper.resolveUriToFolderInfo(context, uri)
            viewModel.addBlacklistedFolder(folderInfo)
            Toast.makeText(context, "Added \"${folderInfo.name}\" to Blacklist", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .testTag("library_folder_management_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.surface,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onBack() }
                        .testTag("folder_management_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = colors.textPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Folder Filtering",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${allSongs.size} active tracks after filtering",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION 1: BLACKLIST
                item {
                    SectionLabel("BLACKLIST", colors)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(colors.accentPill, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderOff,
                                            contentDescription = null,
                                            tint = colors.onAccentPill,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Blacklist",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = "${filterSettings.blacklistedFolders.size} folders hidden",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = colors.accentStrong
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        folderPickerForWhitelist = false
                                        showFolderPickerDialog = true
                                    },
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentStrong,
                                        contentColor = colors.onAccentStrong
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("add_blacklist_folder_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "The content of blacklisted folders is hidden from your library.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            if (filterSettings.blacklistedFolders.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceTinted,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No folders blacklisted. All accessible folders are included.",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.sp,
                                        color = colors.textMuted,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    filterSettings.blacklistedFolders.forEach { folder ->
                                        FolderEntryRow(
                                            folder = folder,
                                            iconTint = Color(0xFFD9534F),
                                            colors = colors,
                                            onRemove = { folderToRemoveConfirm = folder }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 2: MULTI-FOLDER WHITELIST MUSIC
                item {
                    SectionLabel("WHITELIST MUSIC", colors)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(colors.accentPill, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.FolderSpecial,
                                            contentDescription = null,
                                            tint = colors.onAccentPill,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Whitelist music",
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = colors.textPrimary
                                        )
                                        Text(
                                            text = if (filterSettings.whitelistEnabled)
                                                "Active • ${filterSettings.whitelistedFolders.size} allowed folder(s)"
                                            else
                                                "Only show music from /Music Folder",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 12.sp,
                                            color = if (filterSettings.whitelistEnabled) colors.accentStrong else colors.textSecondary
                                        )
                                    }
                                }

                                Switch(
                                    checked = filterSettings.whitelistEnabled,
                                    onCheckedChange = { viewModel.setWhitelistEnabled(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.onAccentStrong,
                                        checkedTrackColor = colors.accentStrong
                                    ),
                                    modifier = Modifier.testTag("whitelist_music_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "When enabled, SONORA shows audio files exclusively from the allowed directories below and their subfolders.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Allowed Folders (${filterSettings.whitelistedFolders.size})",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary
                                )
                                TextButton(
                                    onClick = {
                                        folderPickerForWhitelist = true
                                        showFolderPickerDialog = true
                                    },
                                    modifier = Modifier.testTag("change_allowed_folder_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Add Allowed Folder",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.accentStrong
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                filterSettings.whitelistedFolders.forEach { folder ->
                                    val canRemove = filterSettings.whitelistedFolders.size > 1
                                    FolderEntryRow(
                                        folder = folder,
                                        iconTint = colors.accentStrong,
                                        colors = colors,
                                        onRemove = if (canRemove) {
                                            { viewModel.removeWhitelistedFolder(folder.path) }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 3: WHITELIST VS BLACKLIST COMPARISON CARD
                item {
                    SectionLabel("HOW FILTERING WORKS", colors)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surfaceTinted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CompareArrows, contentDescription = null, tint = colors.accentStrong)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Whitelist vs Blacklist Precedence",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colors.textPrimary
                                )
                            }
                            ComparisonBullet(
                                badge = "Blacklist",
                                detail = "Blocks selected directories and all nested subfolders (e.g. WhatsApp Audio, Ringtones, Call Recordings). Always overrides Whitelist.",
                                colors = colors
                            )
                            ComparisonBullet(
                                badge = "Whitelist",
                                detail = "Restricts the entire library scan to ONLY your allowed directories and their subfolders across Songs, Albums, Artists, Genres, Search, and Smart Mixes.",
                                colors = colors
                            )
                        }
                    }
                }

                // SECTION 4: DETECTED DEVICE DIRECTORIES
                item {
                    SectionLabel("DETECTED DEVICE FOLDERS", colors)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(22.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (availableFolders.isEmpty()) {
                                Text(
                                    text = "No audio directories detected yet. Tap Rescan in Library if you just added files.",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textMuted
                                )
                            } else {
                                availableFolders.forEach { folder ->
                                    val isBlacklisted = filterSettings.isBlacklisted(folder.path)
                                    val isWhitelisted = filterSettings.whitelistedFolders.any {
                                        it.path.equals(folder.path, ignoreCase = true)
                                    }
                                    DetectedDirectoryCard(
                                        folder = folder,
                                        isBlacklisted = isBlacklisted,
                                        isWhitelisted = isWhitelisted,
                                        colors = colors,
                                        onToggleBlacklist = {
                                            if (isBlacklisted) {
                                                viewModel.removeBlacklistedFolder(folder.path)
                                            } else {
                                                viewModel.addBlacklistedFolder(folder)
                                            }
                                        },
                                        onAddWhitelist = {
                                            if (isWhitelisted && filterSettings.whitelistedFolders.size > 1) {
                                                viewModel.removeWhitelistedFolder(folder.path)
                                            } else {
                                                viewModel.addWhitelistedFolder(folder)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm remove blacklist
    folderToRemoveConfirm?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToRemoveConfirm = null },
            containerColor = colors.surface,
            title = {
                Text(
                    text = "Remove from Blacklist?",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Audio files inside \"${folder.name}\" will reappear across your library.",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeBlacklistedFolder(folder.path)
                        folderToRemoveConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    )
                ) {
                    Text("Restore Folder")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemoveConfirm = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    // Picker Dialog
    if (showFolderPickerDialog) {
        AlertDialog(
            onDismissRequest = { showFolderPickerDialog = false },
            containerColor = colors.surface,
            title = {
                Text(
                    text = if (folderPickerForWhitelist) "Add Allowed Folder" else "Add Folder to Blacklist",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.accentPill,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                showFolderPickerDialog = false
                                if (folderPickerForWhitelist) whitelistTreeLauncher.launch(null)
                                else blacklistTreeLauncher.launch(null)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.FolderSpecial, contentDescription = null, tint = colors.onAccentPill)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Browse Storage / SD Card",
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = colors.onAccentPill
                                )
                                Text(
                                    text = "Pick any directory via system folder picker",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.onAccentPill.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    if (availableFolders.isNotEmpty()) {
                        Text(
                            text = "Quick pick detected folders:",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.accentStrong
                        )
                        availableFolders.take(5).forEach { f ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surfaceTinted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showFolderPickerDialog = false
                                        if (folderPickerForWhitelist) {
                                            viewModel.addWhitelistedFolder(f)
                                            Toast.makeText(context, "Added \"${f.name}\" to Whitelist", Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.addBlacklistedFolder(f)
                                            Toast.makeText(context, "Added \"${f.name}\" to Blacklist", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(f.name, fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.textPrimary)
                                        Text("${f.songCount} tracks • ${f.path}", fontFamily = PlusJakartaFontFamily, fontSize = 11.sp, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFolderPickerDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String, colors: EditorialColors) {
    Text(
        text = text,
        fontFamily = PlusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.2.sp,
        color = colors.accentStrong,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun FolderEntryRow(
    folder: FolderInfo,
    iconTint: Color,
    colors: EditorialColors,
    onRemove: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceTinted,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = folder.displayPath,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove ${folder.name}",
                        tint = Color(0xFFD9534F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonBullet(
    badge: String,
    detail: String,
    colors: EditorialColors
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = colors.accentPill
        ) {
            Text(
                text = badge,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = colors.onAccentPill,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = detail,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DetectedDirectoryCard(
    folder: FolderInfo,
    isBlacklisted: Boolean,
    isWhitelisted: Boolean,
    colors: EditorialColors,
    onToggleBlacklist: () -> Unit,
    onAddWhitelist: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceTinted,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = if (isBlacklisted) Color(0xFFD9534F) else colors.accentStrong,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${folder.songCount} tracks • ${folder.path}",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onAddWhitelist,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isWhitelisted) "Allowed" else "+ Allow",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isWhitelisted) colors.accentStrong else colors.textSecondary
                )
            }
            TextButton(
                onClick = onToggleBlacklist,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isBlacklisted) "Restore" else "Hide",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBlacklisted) colors.accentStrong else Color(0xFFD9534F)
                )
            }
        }
    }
}
