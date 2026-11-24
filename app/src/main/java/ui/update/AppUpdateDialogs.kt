package ui.update

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.update.AppUpdateDialogState
import domain.update.AppUpdateSummaryState
import domain.update.GitHubReleaseInfo
import domain.update.GitHubReleaseParser
import domain.update.PostUpdateReminderState
import domain.update.VerifiedApkInfo
import ui.theme.EditorialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders the active GitHub Release update dialog (Update Available, Downloading, Verifying,
 * Ready to Install, Unknown App Sources Permission Rationale, or Error).
 */
@Composable
fun SonoraAppUpdateDialogHost(
    dialogState: AppUpdateDialogState,
    colors: EditorialColors,
    onDownloadUpdate: (GitHubReleaseInfo) -> Unit,
    onLater: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallNow: (GitHubReleaseInfo, VerifiedApkInfo) -> Unit,
    onOpenUnknownSourcesSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    when (dialogState) {
        AppUpdateDialogState.None -> Unit

        is AppUpdateDialogState.Available -> {
            UpdateAvailableDialog(
                state = dialogState,
                colors = colors,
                onDownload = { onDownloadUpdate(dialogState.release) },
                onLater = onLater
            )
        }

        is AppUpdateDialogState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = colors.surface,
                modifier = Modifier.testTag("update_downloading_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Downloading Update",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = dialogState.statusText,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                        val fraction = dialogState.progressFraction
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                color = colors.accentStrong,
                                trackColor = colors.cardBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .testTag("update_download_progress_bar")
                            )
                            val pct = (fraction * 100f).toInt()
                            val downloadedText = GitHubReleaseParser.formatBytes(dialogState.bytesDownloaded).ifBlank { "0 KB" }
                            val totalText = GitHubReleaseParser.formatBytes(dialogState.totalBytes)
                            Text(
                                text = "$downloadedText / $totalText ($pct%)",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.textPrimary
                            )
                        } else {
                            LinearProgressIndicator(
                                color = colors.accentStrong,
                                trackColor = colors.cardBorder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .testTag("update_download_progress_bar")
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.testTag("update_cancel_download_button")
                    ) {
                        Text("Cancel", color = colors.accentStrong, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        is AppUpdateDialogState.Verifying -> {
            AlertDialog(
                onDismissRequest = {},
                containerColor = colors.surface,
                modifier = Modifier.testTag("update_verifying_dialog"),
                title = {
                    Text(
                        text = "Verifying Package",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(
                            color = colors.accentStrong,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = dialogState.statusText,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is AppUpdateDialogState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = colors.surface,
                modifier = Modifier.testTag("update_ready_install_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.InstallMobile,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(id = R.string.update_ready_message),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SONORA v${dialogState.release.versionName} (Build ${dialogState.verifiedApk.versionCode}) has been verified and is ready for Android's system installer.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = colors.textSecondary
                        )
                        val verificationBadge = if (dialogState.verifiedApk.checksumVerifiedAgainstTrustedSource) {
                            "SHA-256 checksum & signing certificate verified"
                        } else {
                            "Package identity & signing certificate verified"
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.accentPill,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = verificationBadge,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp,
                                    color = colors.onAccentPill
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onInstallNow(dialogState.release, dialogState.verifiedApk) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.onAccentStrong
                        ),
                        modifier = Modifier.testTag("update_install_now_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.update_install_now_button),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_ready_later_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.update_later_button),
                            color = colors.textSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }

        is AppUpdateDialogState.PermissionRequired -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = colors.surface,
                modifier = Modifier.testTag("update_permission_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = stringResource(id = R.string.update_permission_title),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = dialogState.rationaleMessage,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = onOpenUnknownSourcesSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentStrong,
                            contentColor = colors.onAccentStrong
                        ),
                        modifier = Modifier.testTag("update_permission_open_settings_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.update_open_settings_button),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("update_permission_cancel_button")
                    ) {
                        Text("Cancel", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        is AppUpdateDialogState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = colors.surface,
                modifier = Modifier.testTag("update_error_dialog"),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = dialogState.title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                text = {
                    Text(
                        text = dialogState.message,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    val verifiedRetry = dialogState.verifiedApkForRetry
                    val releaseRetry = dialogState.releaseForRetry
                    if (verifiedRetry != null && releaseRetry != null) {
                        Button(
                            onClick = { onInstallNow(releaseRetry, verifiedRetry) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            ),
                            modifier = Modifier.testTag("update_error_retry_button")
                        ) {
                            Text("Try Again", fontWeight = FontWeight.Bold)
                        }
                    } else if (releaseRetry != null) {
                        Button(
                            onClick = { onDownloadUpdate(releaseRetry) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            ),
                            modifier = Modifier.testTag("update_error_retry_button")
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("update_error_close_button")
                        ) {
                            Text("Close", color = colors.accentStrong, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    if (dialogState.releaseForRetry != null || dialogState.verifiedApkForRetry != null) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("update_error_close_button")
                        ) {
                            Text("Close", color = colors.textSecondary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun UpdateAvailableDialog(
    state: AppUpdateDialogState.Available,
    colors: EditorialColors,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    val releaseDate = remember(state.release.publishedAtIso) {
        GitHubReleaseParser.formatReleaseDate(state.release.publishedAtIso)
    }
    val shortNotes = remember(state.release.releaseNotes) {
        GitHubReleaseParser.formatShortReleaseNotes(state.release.releaseNotes)
    }
    val downloadSize = remember(state.selectedAsset?.sizeBytes) {
        state.selectedAsset?.sizeBytes?.let { GitHubReleaseParser.formatBytes(it) }.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onLater,
        containerColor = colors.surface,
        modifier = Modifier.testTag("update_available_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceElevated,
                    modifier = Modifier
                        .size(46.dp)
                        .border(1.dp, colors.accentStrong.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sonora_official_logo),
                        contentDescription = stringResource(id = R.string.about_logo_desc),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.update_dialog_title),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "SONORA v${state.release.versionName}" +
                            if (state.resolvedVersionCode > 0L) " (Build ${state.resolvedVersionCode})" else "",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = colors.accentStrong
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UpdateMetadataLine(
                            label = "Current version",
                            value = "v${state.installedVersionName} (Build ${state.installedVersionCode})",
                            colors = colors
                        )
                        UpdateMetadataLine(
                            label = "Latest version",
                            value = "v${state.release.versionName}",
                            colors = colors
                        )
                        if (releaseDate.isNotBlank()) {
                            UpdateMetadataLine(
                                label = "Release date",
                                value = releaseDate,
                                colors = colors
                            )
                        }
                        if (downloadSize.isNotBlank()) {
                            UpdateMetadataLine(
                                label = "Download size",
                                value = downloadSize,
                                colors = colors
                            )
                        }
                    }
                }

                Text(
                    text = "What's New",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = shortNotes,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("update_available_release_notes")
                )

                if (state.assetErrorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.assetErrorMessage,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .padding(10.dp)
                                .testTag("update_no_compatible_apk_message")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                enabled = state.selectedAsset != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.onAccentStrong
                ),
                modifier = Modifier.testTag("update_download_button")
            ) {
                Text(
                    text = stringResource(id = R.string.update_download_button),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onLater,
                modifier = Modifier.testTag("update_later_button")
            ) {
                Text(
                    text = stringResource(id = R.string.update_later_button),
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun UpdateMetadataLine(
    label: String,
    value: String,
    colors: EditorialColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 11.5.sp,
            color = colors.textMuted
        )
        Text(
            text = value,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * One-time post-update security reminder displayed once on the first launch after a
 * verified successful SONORA update. The entire notice is dismissible by tapping anywhere.
 */
@Composable
fun PostUpdateSecurityReminderDialog(
    state: PostUpdateReminderState,
    colors: EditorialColors,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surface,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onDismiss)
                .testTag("post_update_security_reminder")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.accentPill, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (state.isUnknownSourcesPermissionEnabled) {
                                Icons.Rounded.Security
                            } else {
                                Icons.Rounded.VerifiedUser
                            },
                            contentDescription = "Security notice",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.update_complete_title),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 19.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.testTag("post_update_reminder_title")
                        )
                        Text(
                            text = "Version ${state.installedVersionName} (Build ${state.installedVersionCode})",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.accentStrong
                        )
                    }
                }

                Text(
                    text = if (state.isUnknownSourcesPermissionEnabled) {
                        stringResource(id = R.string.update_complete_message)
                    } else {
                        stringResource(id = R.string.update_complete_message_already_off)
                    },
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("post_update_reminder_message")
                )

                if (state.isUnknownSourcesPermissionEnabled) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("post_update_open_settings_button")
                    ) {
                        Text(
                            text = stringResource(id = R.string.update_open_settings_button),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentStrong
                        )
                    }
                }

                Text(
                    text = stringResource(id = R.string.update_tap_anywhere_to_dismiss),
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textMuted,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

/**
 * Compact App Updates card embedded inside Settings -> About.
 */
@Composable
fun AboutAppUpdatesCard(
    summary: AppUpdateSummaryState,
    colors: EditorialColors,
    onCheckForUpdates: () -> Unit,
    onViewAvailableUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lastCheckedLabel = remember(summary.lastCheckedAtMs) {
        if (summary.lastCheckedAtMs <= 0L) {
            "Never checked"
        } else {
            val fmt = SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
            fmt.format(Date(summary.lastCheckedAtMs))
        }
    }
    val latestLabel = when {
        !summary.latestVersionName.isNullOrBlank() -> "v${summary.latestVersionName}" +
            (summary.latestVersionCode?.takeIf { it > 0L }?.let { " (Build $it)" } ?: "")
        else -> "Not checked yet"
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_about_app_updates_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(colors.accentPill, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (summary.isUpdateAvailable) {
                            Icons.Rounded.SystemUpdate
                        } else {
                            Icons.Rounded.CheckCircle
                        },
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.settings_about_app_updates_title),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Official GitHub Releases",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.5.sp,
                        color = colors.textSecondary
                    )
                }
            }

            HorizontalDivider(color = colors.cardBorder.copy(alpha = 0.65f))

            UpdateMetadataLine(
                label = "Installed version",
                value = "v${summary.installedVersionName} (Build ${summary.installedVersionCode})",
                colors = colors
            )
            UpdateMetadataLine(
                label = "Latest available",
                value = latestLabel,
                colors = colors
            )
            UpdateMetadataLine(
                label = "Last checked",
                value = lastCheckedLabel,
                colors = colors
            )

            if (!summary.statusMessage.isNullOrBlank()) {
                Text(
                    text = summary.statusMessage,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = if (summary.isUpdateAvailable) colors.accentStrong else colors.textSecondary,
                    modifier = Modifier.testTag("settings_about_updates_status")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCheckForUpdates,
                    enabled = !summary.isChecking,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("settings_about_check_updates_button")
                ) {
                    if (summary.isChecking) {
                        CircularProgressIndicator(
                            color = colors.onAccentStrong,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checking…", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.settings_about_check_for_updates),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                if (summary.isUpdateAvailable && summary.cachedAvailableRelease != null) {
                    OutlinedButton(
                        onClick = onViewAvailableUpdate,
                        border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("settings_about_view_update_button")
                    ) {
                        Text(
                            text = "View",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }
        }
    }
}
