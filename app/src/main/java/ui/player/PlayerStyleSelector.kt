package ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.example.model.LockScreenStyle
import com.example.model.MiniPlayerStyle
import com.example.model.PlayerStyle
import com.example.model.PlayerStyleCategory
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import ui.player.components.PlayerStyleMiniPreview
import ui.theme.LocalEditorialColors
import ui.theme.LocalReduceAnimations

/**
 * Redesigned Editorial Player Style Selector:
 * - Responsive two-column grid with live artwork previews for all 19 Player Styles.
 * - Category filter chips: All, Minimal, Artistic, Immersive, Classic, Experimental.
 * - Direct Mini-Player Style & Lock-Screen Media Style selectors + Appearance Customizer.
 */
@Composable
fun PlayerStyleSelector(
    viewModel: SonoraViewModel,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenCustomization: (() -> Unit)? = null
) {
    val colors = LocalEditorialColors.current
    val reduceAnimations = LocalReduceAnimations.current
    val currentStyle by viewModel.playerStyle.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val miniPlayerStyle by viewModel.miniPlayerStyle.collectAsState()
    val lockScreenStyle by viewModel.lockScreenStyle.collectAsState()

    var selectedCategory by remember { mutableStateOf(PlayerStyleCategory.ALL) }
    var showInternalCustomization by remember { mutableStateOf(false) }

    val filteredStyles = remember(selectedCategory) {
        PlayerStyle.entries.filter { it.matchesCategory(selectedCategory) }
    }

    if (showInternalCustomization) {
        PlayerCustomizationScreen(
            viewModel = viewModel,
            onBack = { showInternalCustomization = false },
            onOpenPlayer = {
                showInternalCustomization = false
                onOpenPlayer()
            }
        )
        return
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .testTag("player_style_selector"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable { onBack() }
                            .testTag("style_selector_back_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Player Styles",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = "19 expressive Now Playing experiences",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.accentPill,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            onOpenCustomization?.invoke() ?: run { showInternalCustomization = true }
                        }
                        .testTag("open_appearance_customization_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Customize Appearance", tint = colors.onAccentPill, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Customize",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = colors.onAccentPill
                        )
                    }
                }
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerStyleCategory.entries.forEach { category ->
                    val selected = category == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (selected) colors.accentStrong else colors.surface,
                        border = BorderStroke(
                            width = 0.75.dp,
                            color = if (selected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { selectedCategory = category }
                            .testTag("style_category_${category.id}")
                    ) {
                        Text(
                            text = category.displayName,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selected) colors.onAccentStrong else colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredStyles, key = { it.name }) { style ->
                    val isSelected = style == currentStyle
                    val cardBg by animateColorAsState(
                        targetValue = if (isSelected) colors.accentPill.copy(alpha = 0.40f) else colors.surface,
                        animationSpec = if (reduceAnimations) snap() else tween(220),
                        label = "styleCardBg"
                    )
                    val elevation by animateDpAsState(
                        targetValue = if (isSelected) 6.dp else 1.dp,
                        animationSpec = if (reduceAnimations) snap() else tween(220),
                        label = "styleCardElevation"
                    )

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardBg,
                        tonalElevation = elevation,
                        border = BorderStroke(
                            width = if (isSelected) 1.75.dp else 0.75.dp,
                            color = if (isSelected) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable { viewModel.setPlayerStyle(style) }
                            .testTag("player_style_card_${style.id}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                PlayerStyleMiniPreview(
                                    style = style,
                                    isSelected = isSelected,
                                    song = currentSong,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.12f)
                                )
                                if (isSelected) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colors.surface,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = colors.accentStrong,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = style.displayName,
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isSelected) colors.accentStrong else colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = colors.surfaceTinted
                                    ) {
                                        Text(
                                            text = style.category.displayName,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 9.sp,
                                            color = colors.textSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = style.description,
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = colors.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Multi-surface Mini-Player Style Picker
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MINI-PLAYER STYLE",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            color = colors.accentStrong
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MiniPlayerStyle.entries.forEach { variant ->
                                val selected = variant == miniPlayerStyle
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) colors.accentPill.copy(alpha = 0.5f) else colors.surface,
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 0.75.dp,
                                        color = if (selected) colors.accentStrong else colors.cardBorder
                                    ),
                                    modifier = Modifier
                                        .width(156.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable { viewModel.setMiniPlayerStyle(variant) }
                                        .testTag("mini_style_${variant.id}")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = variant.displayName,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (selected) colors.accentStrong else colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = variant.subtitle,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 10.sp,
                                            color = colors.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Multi-surface Lock-Screen Notification Style Picker
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "LOCK-SCREEN MEDIA STYLE",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            color = colors.accentStrong
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LockScreenStyle.entries.forEach { lockStyle ->
                                val selected = lockStyle == lockScreenStyle
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = if (selected) colors.accentPill.copy(alpha = 0.5f) else colors.surface,
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 0.75.dp,
                                        color = if (selected) colors.accentStrong else colors.cardBorder
                                    ),
                                    modifier = Modifier
                                        .width(164.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable {
                                            viewModel.setLockScreenStyle(lockStyle)
                                            viewModel.syncNotification()
                                        }
                                        .testTag("lock_style_${lockStyle.id}")
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = lockStyle.displayName,
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (selected) colors.accentStrong else colors.textPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = lockStyle.subtitle,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 10.sp,
                                            color = colors.textSecondary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (currentSong != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Button(
                            onClick = onOpenPlayer,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentStrong,
                                contentColor = colors.onAccentStrong
                            ),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .height(52.dp)
                                .testTag("preview_selected_style_button")
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preview ${currentStyle.displayName} in Player", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
