package ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ui.components.AdaptiveSingleLineBadge
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

data class NowPlayingDownloadAction(
    val isDownloaded: Boolean,
    val onDownloadClick: () -> Unit
)

data class NowPlayingOnlineLibraryAction(
    val isSaved: Boolean,
    val onToggleSaveClick: () -> Unit,
    val onAddToOnlinePlaylistClick: () -> Unit
)

data class NowPlayingSongRadioAction(
    val isRadioActive: Boolean,
    val onOpenSongRadio: () -> Unit
)

val LocalNowPlayingDownloadAction = compositionLocalOf<NowPlayingDownloadAction?> { null }
val LocalNowPlayingOnlineLibraryAction = compositionLocalOf<NowPlayingOnlineLibraryAction?> { null }
val LocalNowPlayingStopAction = compositionLocalOf<(() -> Unit)?> { null }
val LocalNowPlayingSongRadioAction = compositionLocalOf<NowPlayingSongRadioAction?> { null }

/**
 * Unified SONORA AESTHETIC UI Header for Now Playing:
 * - 48dp accessible Collapse, Queue, and Overflow Menu touch targets.
 * - SONORA branding + audio format badge pill.
 * - Overflow menu exposing Download, Equalizer, Audio Studio (8D & FX), Output Device, Focus Mode, Style Wheel, Visualizer, Lyrics, and Sleep Timer.
 */
@Composable
fun NowPlayingTopHeader(
    song: Song?,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenStyleWheel: () -> Unit,
    onToggleVisualizer: () -> Unit,
    isVisualizerActive: Boolean,
    onOpenEqualizer: (() -> Unit)? = null,
    onOpenAudioStudio: (() -> Unit)? = null,
    onOpenOutputDevice: (() -> Unit)? = null,
    onToggleFocusMode: (() -> Unit)? = null,
    onOpenCustomization: (() -> Unit)? = null,
    onOpenStreamQuality: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    val downloadAction = LocalNowPlayingDownloadAction.current
    val onlineLibraryAction = LocalNowPlayingOnlineLibraryAction.current
    val stopAction = LocalNowPlayingStopAction.current
    val songRadioAction = LocalNowPlayingSongRadioAction.current
    var showMenu by remember { mutableStateOf(false) }

    val fontScale = LocalDensity.current.fontScale
    val badgeLabel = remember(song?.isOnline, song?.providerName) {
        if (song?.isOnline == true) {
            song.providerName.ifBlank { "ONLINE" }.uppercase()
        } else {
            "HI-FI"
        }
    }
    val formatString = song?.audioFormatInfo ?: "LOCAL AUDIO"

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        // Parent player styles already provide 18–24dp horizontal margin; avoid double-padding on compact widths.
        val outerHorizontalPad = when {
            maxWidth < 380.dp -> 0.dp
            maxWidth < 440.dp -> 4.dp
            else -> 12.dp
        }
        val actionButtonWidth = when {
            maxWidth < 340.dp -> 40.dp
            maxWidth < 390.dp -> 44.dp
            else -> 48.dp
        }
        val trailingCount = if (songRadioAction != null) 3 else 2
        val availableCenterWidth = maxWidth - (outerHorizontalPad * 2) - actionButtonWidth * (1 + trailingCount)
        val useStackedMetaRow = availableCenterWidth < 156.dp || fontScale > 1.15f

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = outerHorizontalPad, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .width(actionButtonWidth)
                        .height(48.dp)
                        .testTag("collapse_now_playing_button")
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (useStackedMetaRow) {
                        Text(
                            text = domain.branding.AppBranding.APP_NAME,
                            fontFamily = OutfitFontFamily,
                            color = colors.accentStrong,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.0.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("now_playing_brand_label")
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = domain.branding.AppBranding.APP_NAME,
                                fontFamily = OutfitFontFamily,
                                color = colors.accentStrong,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 2.2.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("now_playing_brand_label")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AdaptiveSingleLineBadge(
                                text = badgeLabel,
                                containerColor = colors.accentPill,
                                contentColor = colors.onAccentPill,
                                cornerRadius = 6.dp,
                                fontSize = 9.sp,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.5.dp),
                                modifier = Modifier.testTag("now_playing_source_badge")
                            )
                        }
                        Text(
                            text = formatString,
                            fontFamily = PlusJakartaFontFamily,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("now_playing_format_label")
                        )
                    }
                }

                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenQueue,
                            modifier = Modifier
                                .width(actionButtonWidth)
                                .height(48.dp)
                                .testTag("open_queue_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Queue",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (songRadioAction != null) {
                            IconButton(
                                onClick = songRadioAction.onOpenSongRadio,
                                modifier = Modifier
                                    .width(actionButtonWidth)
                                    .height(48.dp)
                                    .testTag("player_song_radio_button")
                            ) {
                                Icon(
                                    Icons.Default.Radio,
                                    contentDescription = if (songRadioAction.isRadioActive) "Song Radio Active" else "Start Song Radio",
                                    tint = if (songRadioAction.isRadioActive) colors.accentStrong else colors.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .width(actionButtonWidth)
                                .height(48.dp)
                                .testTag("now_playing_menu_button")
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Options",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.surface)
                        .border(BorderStroke(1.dp, colors.cardBorder), RoundedCornerShape(18.dp))
                ) {
                    if (song?.isOnline == true && onlineLibraryAction != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (onlineLibraryAction.isSaved) "Saved in Online Library" else "Save to Online Library",
                                    fontFamily = PlusJakartaFontFamily,
                                    color = if (onlineLibraryAction.isSaved) colors.accentStrong else colors.textPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (onlineLibraryAction.isSaved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onlineLibraryAction.onToggleSaveClick()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Add to Online Playlist",
                                    fontFamily = PlusJakartaFontFamily,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.AutoMirrored.Filled.PlaylistAdd,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onlineLibraryAction.onAddToOnlinePlaylistClick()
                            }
                        )
                    }

                    if (song?.isOnline == true && downloadAction != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (downloadAction.isDownloaded) "Downloaded Offline" else "Download Track",
                                    fontFamily = PlusJakartaFontFamily,
                                    color = colors.textPrimary,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (downloadAction.isDownloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                downloadAction.onDownloadClick()
                            }
                        )
                    }

                    if (song?.isOnline == true && onOpenStreamQuality != null) {
                        DropdownMenuItem(
                            text = { Text("Stream Quality", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.HighQuality, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onOpenStreamQuality()
                            }
                        )
                    }

                    if (onOpenEqualizer != null) {
                        DropdownMenuItem(
                            text = { Text("15-Band Equalizer", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onOpenEqualizer()
                            }
                        )
                    }

                    if (onOpenAudioStudio != null) {
                        DropdownMenuItem(
                            text = { Text("Audio Studio (8D & FX)", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.SpatialAudio, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onOpenAudioStudio()
                            }
                        )
                    }

                    if (onOpenOutputDevice != null) {
                        DropdownMenuItem(
                            text = { Text("Audio Output Device", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Headphones, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onOpenOutputDevice()
                            }
                        )
                    }

                    if (onToggleFocusMode != null) {
                        DropdownMenuItem(
                            text = { Text("Full Artwork Focus", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onToggleFocusMode()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Quick Style Wheel", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onOpenStyleWheel()
                        }
                    )

                    if (onOpenCustomization != null) {
                        DropdownMenuItem(
                            text = { Text("Music Player", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onOpenCustomization()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text(if (isVisualizerActive) "Hide Visualizer" else "Show Visualizer", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onToggleVisualizer()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Track Lyrics", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Lyrics, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onOpenLyrics()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Sleep Timer", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Bedtime, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onOpenSleepTimer()
                        }
                    )

                    if (stopAction != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Stop Playback",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.accentStrong,
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.StopCircle,
                                    contentDescription = "Stop Playback",
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                stopAction()
                            },
                            modifier = Modifier.testTag("now_playing_stop_playback_item")
                        )
                    }
                }
            }
            }

            if (useStackedMetaRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AdaptiveSingleLineBadge(
                        text = badgeLabel,
                        containerColor = colors.accentPill,
                        contentColor = colors.onAccentPill,
                        cornerRadius = 6.dp,
                        fontSize = 9.sp,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 1.5.dp),
                        modifier = Modifier.testTag("now_playing_source_badge")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatString,
                        fontFamily = PlusJakartaFontFamily,
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .testTag("now_playing_format_label")
                    )
                }
            }
        }
    }
}
