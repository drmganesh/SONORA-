package ui.player

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlayerStyle
import com.example.model.PlayerStyleCategory
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.player.components.PlayerStyleMiniPreview
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

/**
 * Quick Style Switching: Style Wheel Modal
 * Displays all 19 player styles in an interactive horizontal wheel with category filters
 * and live album artwork previews without interrupting playback.
 */
@Composable
fun PlayerStyleWheel(
    currentStyle: PlayerStyle,
    onSelectStyle: (PlayerStyle) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    song: Song? = null
) {
    val colors = LocalEditorialColors.current
    val reduceAnimations = LocalReduceAnimations.current
    val listState = rememberLazyListState()
    var dragY by remember { mutableFloatStateOf(0f) }
    var selectedCategory by remember { mutableStateOf(PlayerStyleCategory.ALL) }

    val visibleStyles = remember(selectedCategory) {
        PlayerStyle.entries.filter { it.matchesCategory(selectedCategory) }
    }

    LaunchedEffect(currentStyle, selectedCategory) {
        val idx = visibleStyles.indexOf(currentStyle)
        if (idx >= 0) {
            if (reduceAnimations) listState.scrollToItem(idx) else listState.animateScrollToItem(idx)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragEnd = {
                        if (dragY > 80f) {
                            onDismiss()
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragY += dragAmount
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .navigationBarsPadding(),
            color = colors.surface,
            tonalElevation = 12.dp,
            border = BorderStroke(1.dp, colors.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.cardBorder)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = colors.accentPill,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Palette, contentDescription = null, tint = colors.onAccentPill, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PLAYER STYLE WHEEL",
                                fontFamily = OutfitFontFamily,
                                color = colors.accentStrong,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "Select from 19 bespoke layouts",
                                fontFamily = PlusJakartaFontFamily,
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlayerStyleCategory.entries.forEach { category ->
                        val selected = category == selectedCategory
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) colors.accentStrong else colors.surfaceTinted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { selectedCategory = category }
                        ) {
                            Text(
                                text = category.displayName,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = if (selected) colors.onAccentStrong else colors.textSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(visibleStyles, key = { it.name }) { style ->
                        val isSelected = style == currentStyle
                        val cardScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.04f else 0.96f,
                            animationSpec = if (reduceAnimations) snap() else spring(stiffness = Spring.StiffnessMediumLow),
                            label = "scale"
                        )

                        Surface(
                            modifier = Modifier
                                .width(136.dp)
                                .scale(cardScale)
                                .clip(RoundedCornerShape(22.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) colors.accentStrong else colors.cardBorder,
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .clickable {
                                    onSelectStyle(style)
                                    onDismiss()
                                }
                                .testTag("style_wheel_card_${style.name.lowercase()}"),
                            color = if (isSelected) colors.surfaceTinted else colors.surfaceElevated,
                            tonalElevation = if (isSelected) 6.dp else 1.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                PlayerStyleMiniPreview(
                                    style = style,
                                    isSelected = isSelected,
                                    song = song,
                                    modifier = Modifier.size(78.dp)
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = style.displayName,
                                        fontFamily = OutfitFontFamily,
                                        color = if (isSelected) colors.accentStrong else colors.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = colors.accentStrong,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = style.subtitle,
                                    fontFamily = PlusJakartaFontFamily,
                                    color = colors.textSecondary,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tip: Long-press album artwork anytime to switch style seamlessly",
                    fontFamily = PlusJakartaFontFamily,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    }
}
