package ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.onboarding.MusicTasteCatalog
import ui.theme.EditorialColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicTasteGenreStep(
    colors: EditorialColors,
    reduceAnimations: Boolean,
    selectedGenres: List<String>,
    isEditMode: Boolean,
    onToggleGenre: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSet = selectedGenres.map { it.lowercase() }.toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("music_taste_genre_step")
    ) {
        // Header section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = stringResource(id = R.string.music_taste_genres_title),
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = colors.textPrimary,
                modifier = Modifier.testTag("music_taste_genres_title")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.music_taste_genres_subtitle),
                fontFamily = PlusJakartaFontFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Selected count + Clear All row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (selectedGenres.isNotEmpty()) colors.accentPill else colors.surfaceElevated,
                    border = BorderStroke(
                        1.dp,
                        if (selectedGenres.isNotEmpty()) colors.accentStrong.copy(alpha = 0.4f) else colors.cardBorder
                    )
                ) {
                    Text(
                        text = if (selectedGenres.isEmpty()) {
                            stringResource(id = R.string.music_taste_genres_none_selected)
                        } else {
                            stringResource(id = R.string.music_taste_genres_selected_count, selectedGenres.size)
                        },
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = if (selectedGenres.isNotEmpty()) colors.onAccentPill else colors.textSecondary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("music_taste_genre_selected_count")
                    )
                }

                if (selectedGenres.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("music_taste_genres_clear_all")
                    ) {
                        Text(
                            text = stringResource(id = R.string.music_taste_clear_all),
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Responsive genre chip flow
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .testTag("music_taste_genre_chips_container")
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MusicTasteCatalog.CURATED_GENRES.forEach { genre ->
                    val isSelected = genre.lowercase() in selectedSet
                    MusicGenreSelectableChip(
                        genre = genre,
                        isSelected = isSelected,
                        colors = colors,
                        reduceAnimations = reduceAnimations,
                        onClick = { onToggleGenre(genre) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Bottom CTA footer: Back, Skip, Continue
        Surface(
            color = colors.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isEditMode) {
                    OutlinedButton(
                        onClick = onBack,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, colors.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("music_taste_genres_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(id = R.string.music_taste_back),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(id = R.string.music_taste_back),
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    }
                }

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("music_taste_genres_skip_button")
                ) {
                    Text(
                        text = if (isEditMode) stringResource(id = R.string.music_taste_cancel)
                        else stringResource(id = R.string.music_taste_skip),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                }

                Button(
                    onClick = onContinue,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentStrong,
                        contentColor = colors.onAccentStrong
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("music_taste_genres_continue_button")
                ) {
                    Text(
                        text = if (isEditMode) stringResource(id = R.string.music_taste_save_changes)
                        else stringResource(id = R.string.music_taste_continue),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicGenreSelectableChip(
    genre: String,
    isSelected: Boolean,
    colors: EditorialColors,
    reduceAnimations: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentStrong else colors.surfaceElevated,
        animationSpec = tween(if (reduceAnimations) 0 else 180),
        label = "genre_chip_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.onAccentStrong else colors.textPrimary,
        animationSpec = tween(if (reduceAnimations) 0 else 180),
        label = "genre_chip_fg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentStrong else colors.cardBorder,
        animationSpec = tween(if (reduceAnimations) 0 else 180),
        label = "genre_chip_border"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(1.25.dp, borderColor),
        modifier = Modifier
            .height(48.dp)
            .semantics {
                selected = isSelected
            }
            .testTag("music_taste_genre_chip_${genre.lowercase().replace(Regex("[^a-z0-9]+"), "_")}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = genre,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                color = contentColor
            )
        }
    }
}
