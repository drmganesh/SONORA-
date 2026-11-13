package ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

/**
 * Unified SONORA AESTHETIC UI Header for Now Playing:
 * - 48dp accessible Collapse, Queue, and Overflow Menu touch targets.
 * - SONORA branding + audio format badge pill.
 * - Overflow menu exposing Equalizer, Audio Studio (8D & FX), Output Device, Focus Mode, Style Wheel, Visualizer, Lyrics, and Sleep Timer.
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
    modifier: Modifier = Modifier
) {
    val colors = LocalEditorialColors.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
                    .size(48.dp)
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
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SONORA",
                        fontFamily = OutfitFontFamily,
                        color = colors.accentStrong,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.4.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.accentPill
                    ) {
                        Text(
                            text = "HI-FI",
                            fontFamily = PlusJakartaFontFamily,
                            color = colors.onAccentPill,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }

                val formatString = song?.audioFormatInfo ?: "LOCAL AUDIO"
                Text(
                    text = formatString,
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenQueue,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_queue_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .size(48.dp)
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
                        text = { Text("Player Style (9 Styles)", fontFamily = PlusJakartaFontFamily, color = colors.textPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = colors.accentStrong, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onOpenStyleWheel()
                        }
                    )

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
                }
            }
        }
    }
}
