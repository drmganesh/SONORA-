package ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.onboarding.MusicTasteArtist
import domain.onboarding.MusicTasteArtistDiscoveryUiState
import ui.theme.EditorialColors

@Composable
fun MusicTasteArtistStep(
    colors: EditorialColors,
    reduceAnimations: Boolean,
    artistState: MusicTasteArtistDiscoveryUiState,
    selectedArtists: List<MusicTasteArtist>,
    isEditMode: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleArtist: (MusicTasteArtist) -> Unit,
    onClearAll: () -> Unit,
    onRetryLoad: () -> Unit,
    onSkip: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedKeys = selectedArtists.map { it.normalizedKey }.toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("music_taste_artist_step")
    ) {
        // Header section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = stringResource(id = R.string.music_taste_artists_title),
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = colors.textPrimary,
                modifier = Modifier.testTag("music_taste_artists_title")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(id = R.string.music_taste_artists_subtitle),
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.textSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Search bar
            OutlinedTextField(
                value = artistState.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.music_taste_artists_search_placeholder),
                        fontFamily = PlusJakartaFontFamily,
                        color = colors.textMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = colors.textSecondary
                    )
                },
                trailingIcon = {
                    if (artistState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.testTag("music_taste_artist_search_clear")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(id = R.string.music_taste_clear_search_desc),
                                tint = colors.textSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentStrong,
                    unfocusedBorderColor = colors.cardBorder,
                    focusedContainerColor = colors.surfaceElevated,
                    unfocusedContainerColor = colors.surface,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentStrong
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music_taste_artist_search_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Selection count + Clear All row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (selectedArtists.isNotEmpty()) colors.accentPill else colors.surfaceElevated,
                    border = BorderStroke(1.dp, if (selectedArtists.isNotEmpty()) colors.accentStrong.copy(alpha = 0.4f) else colors.cardBorder)
                ) {
                    Text(
                        text = if (selectedArtists.isEmpty()) {
                            stringResource(id = R.string.music_taste_artists_none_selected)
                        } else {
                            stringResource(id = R.string.music_taste_artists_selected_count, selectedArtists.size)
                        },
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = if (selectedArtists.isNotEmpty()) colors.onAccentPill else colors.textSecondary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("music_taste_artist_selected_count")
                    )
                }

                if (selectedArtists.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        modifier = Modifier.testTag("music_taste_artists_clear_all")
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

            AnimatedVisibility(visible = artistState.isLoadingRelated || artistState.lastRelatedSourceName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                ) {
                    if (artistState.isLoadingRelated) {
                        CircularProgressIndicator(
                            color = colors.accentStrong,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Finding related artists…",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    } else if (artistState.lastRelatedSourceName != null) {
                        Text(
                            text = "More artists like ${artistState.lastRelatedSourceName} added below",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main artist grid / loading / empty state
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                (artistState.isLoadingInitial || artistState.isSearching) && artistState.artists.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = colors.accentStrong)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (artistState.isSearching) "Searching real artists…" else "Loading artists for you…",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                    }
                }

                artistState.artists.isEmpty() -> {
                    ArtistDiscoveryEmptyState(
                        colors = colors,
                        isOffline = artistState.isOffline,
                        errorMessage = artistState.errorMessage,
                        hasSearchQuery = artistState.searchQuery.isNotBlank(),
                        onRetry = onRetryLoad
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 104.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("music_taste_artist_grid")
                    ) {
                        if (artistState.isSearching) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = colors.accentStrong,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Updating search results…",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 12.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }

                        items(
                            items = artistState.artists,
                            key = { it.normalizedKey.ifBlank { it.id } }
                        ) { artist ->
                            val isSelected = artist.normalizedKey in selectedKeys
                            ArtistSelectionTile(
                                artist = artist,
                                isSelected = isSelected,
                                colors = colors,
                                reduceAnimations = reduceAnimations,
                                onClick = { onToggleArtist(artist) }
                            )
                        }
                    }
                }
            }
        }

        // Bottom CTA footer
        Surface(
            color = colors.surface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textPrimary),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("music_taste_artists_skip_button")
                ) {
                    Text(
                        text = if (isEditMode) stringResource(id = R.string.music_taste_cancel)
                        else stringResource(id = R.string.music_taste_skip),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
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
                        .testTag("music_taste_artists_continue_button")
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
private fun ArtistSelectionTile(
    artist: MusicTasteArtist,
    isSelected: Boolean,
    colors: EditorialColors,
    reduceAnimations: Boolean,
    onClick: () -> Unit
) {
    val ringColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentStrong else colors.cardBorder,
        animationSpec = tween(if (reduceAnimations) 0 else 200),
        label = "artist_ring_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Checkbox, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("music_taste_artist_tile_${artist.normalizedKey.replace(" ", "_")}")
    ) {
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.25.dp,
                        color = ringColor,
                        shape = CircleShape
                    )
                    .background(colors.surfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                if (!artist.imageUrl.isNullOrBlank()) {
                    SubcomposeAsyncImage(
                        model = artist.imageUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        loading = {
                            ArtistMonogramPlaceholder(name = artist.name, colors = colors)
                        },
                        error = {
                            ArtistMonogramPlaceholder(name = artist.name, colors = colors)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ArtistMonogramPlaceholder(name = artist.name, colors = colors)
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.accentStrong)
                        .border(2.dp, colors.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected ${artist.name}",
                        tint = colors.onAccentStrong,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.name,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            color = if (isSelected) colors.accentStrong else colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        if (!artist.relatedToArtistName.isNullOrBlank() && !isSelected) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Similar",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 10.5.sp,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun ArtistMonogramPlaceholder(
    name: String,
    colors: EditorialColors,
    modifier: Modifier = Modifier
) {
    val initials = name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.surfaceTinted,
                        colors.surfaceElevated
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isNotBlank()) {
            Text(
                text = initials,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = colors.accentStrong
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun ArtistDiscoveryEmptyState(
    colors: EditorialColors,
    isOffline: Boolean,
    errorMessage: String?,
    hasSearchQuery: Boolean,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
            .testTag("music_taste_artist_empty_state"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (isOffline) Icons.Rounded.CloudOff else Icons.Rounded.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                isOffline -> "You're offline"
                hasSearchQuery -> "No artists found"
                else -> "Couldn't load artists right now"
            },
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                isOffline -> "Connect to the internet to browse real artists, or tap Skip to set this up later in Profile → Music Taste."
                !errorMessage.isNullOrBlank() -> errorMessage
                hasSearchQuery -> "Try a different artist name or continue to genres."
                else -> "Retry or skip for now — you can always pick artists in Profile → Music Taste."
            },
            fontFamily = PlusJakartaFontFamily,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentStrong),
            modifier = Modifier
                .height(48.dp)
                .testTag("music_taste_artists_retry_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Try Again",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}
