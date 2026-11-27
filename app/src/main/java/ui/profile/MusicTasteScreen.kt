package ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.onboarding.MusicTasteArtist
import domain.onboarding.MusicTasteDiscoveryTrack
import domain.onboarding.MusicTasteProfile
import ui.onboarding.ArtistMonogramPlaceholder
import ui.theme.EditorialColors

/**
 * Permanent Profile -> Music Taste management screen.
 * Allows users to inspect and edit Favorite Artists, Favorite Genres,
 * Liked & Disliked Discovery Songs, restart Song Discovery, or reset Music Taste
 * without ever affecting their library, playlists, downloads, or playback history.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicTasteScreen(
    profile: MusicTasteProfile,
    colors: EditorialColors,
    onBack: () -> Unit,
    onEditArtists: () -> Unit,
    onEditGenres: () -> Unit,
    onRestartSongDiscovery: () -> Unit,
    onRemoveArtist: (MusicTasteArtist) -> Unit,
    onRemoveGenre: (String) -> Unit,
    onRemoveLikedTrack: (String) -> Unit,
    onRemovePassedTrack: (String) -> Unit,
    onResetMusicTaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("profile_music_taste_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("music_taste_screen_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(id = R.string.music_taste_back),
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(id = R.string.profile_music_taste_title),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = profile.summarySubtitle(),
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Quick Actions Card
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "TUNE YOUR RECOMMENDATIONS",
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.1.sp,
                                color = colors.textMuted
                            )
                            Text(
                                text = "Changes update ${domain.branding.AppBranding.RECOMMEND_NAME}, Recommend Songs, Your Mix, and Radio immediately — all stored privately on this device.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = colors.textSecondary
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onEditArtists,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.55f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentStrong),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("music_taste_edit_artists_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Edit Artists",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = onEditGenres,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.55f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accentStrong),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("music_taste_edit_genres_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Edit Genres",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Button(
                                onClick = onRestartSongDiscovery,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accentStrong,
                                    contentColor = colors.onAccentStrong
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("music_taste_restart_discovery_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Explore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(id = R.string.music_taste_restart_discovery),
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                            }
                        }
                    }
                }

                // Section 1: Favorite Artists
                item {
                    MusicTasteSectionCard(
                        title = "Favorite Artists",
                        count = profile.selectedArtists.size,
                        actionLabel = "Edit",
                        onAction = onEditArtists,
                        colors = colors
                    ) {
                        if (profile.selectedArtists.isEmpty()) {
                            Text(
                                text = "No favorite artists selected yet. Tap Edit to pick artists you love.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("music_taste_favorite_artists_list")
                            ) {
                                items(profile.selectedArtists, key = { it.normalizedKey }) { artist ->
                                    FavoriteArtistChipCard(
                                        artist = artist,
                                        colors = colors,
                                        onRemove = { onRemoveArtist(artist) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2: Favorite Genres
                item {
                    MusicTasteSectionCard(
                        title = "Favorite Genres",
                        count = profile.selectedGenres.size,
                        actionLabel = "Edit",
                        onAction = onEditGenres,
                        colors = colors
                    ) {
                        if (profile.selectedGenres.isEmpty()) {
                            Text(
                                text = "No favorite genres selected yet. Tap Edit to pick genres you enjoy.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.testTag("music_taste_favorite_genres_list")
                            ) {
                                profile.selectedGenres.forEach { genre ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = colors.accentPill,
                                        border = BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.45f))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
                                        ) {
                                            Text(
                                                text = genre,
                                                fontFamily = PlusJakartaFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = colors.onAccentPill
                                            )
                                            IconButton(
                                                onClick = { onRemoveGenre(genre) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Remove $genre",
                                                    tint = colors.onAccentPill,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 3: Liked Discovery Songs
                item {
                    MusicTasteSectionCard(
                        title = "Liked Discovery Songs",
                        count = profile.likedDiscoveryTracks.size,
                        actionLabel = "Discover",
                        onAction = onRestartSongDiscovery,
                        colors = colors
                    ) {
                        if (profile.likedDiscoveryTracks.isEmpty()) {
                            Text(
                                text = "Songs you keep or swipe up in Song Discovery appear here.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("music_taste_liked_tracks_list")
                            ) {
                                profile.likedDiscoveryTracks.take(15).forEach { track ->
                                    DiscoveryTrackReviewRow(
                                        track = track,
                                        isLiked = true,
                                        colors = colors,
                                        onRemove = { onRemoveLikedTrack(track.contentUrl) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 4: Disliked / Passed Discovery Songs
                item {
                    MusicTasteSectionCard(
                        title = "Passed Discovery Songs",
                        count = profile.passedDiscoveryTracks.size,
                        actionLabel = null,
                        onAction = null,
                        colors = colors
                    ) {
                        if (profile.passedDiscoveryTracks.isEmpty()) {
                            Text(
                                text = "Songs you pass on are hidden from future recommendations.",
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                color = colors.textSecondary
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("music_taste_passed_tracks_list")
                            ) {
                                profile.passedDiscoveryTracks.take(15).forEach { track ->
                                    DiscoveryTrackReviewRow(
                                        track = track,
                                        isLiked = false,
                                        colors = colors,
                                        onRemove = { onRemovePassedTrack(track.contentUrl) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Reset Music Taste section
                item {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(1.dp, Color(0xFFE05263).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.music_taste_reset_title),
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary
                            )
                            Text(
                                text = stringResource(id = R.string.music_taste_reset_description),
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = colors.textSecondary
                            )
                            OutlinedButton(
                                onClick = { showResetConfirmDialog = true },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFE05263).copy(alpha = 0.7f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE05263)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("music_taste_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RestartAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(id = R.string.music_taste_reset_title),
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            containerColor = colors.surfaceElevated,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            title = {
                Text(
                    text = stringResource(id = R.string.music_taste_reset_confirm_title),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(id = R.string.music_taste_reset_confirm_message),
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        onResetMusicTaste()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE05263),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("music_taste_reset_confirm_button")
                ) {
                    Text(
                        text = "Reset",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetConfirmDialog = false },
                    modifier = Modifier.testTag("music_taste_reset_cancel_button")
                ) {
                    Text(
                        text = stringResource(id = R.string.music_taste_cancel),
                        fontFamily = PlusJakartaFontFamily,
                        color = colors.textPrimary
                    )
                }
            }
        )
    }
}

@Composable
private fun MusicTasteSectionCard(
    title: String,
    count: Int,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    colors: EditorialColors,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceElevated,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = colors.textPrimary
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = colors.surfaceTinted
                    ) {
                        Text(
                            text = count.toString(),
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = colors.accentStrong,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(
                            text = actionLabel,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = colors.accentStrong
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
private fun FavoriteArtistChipCard(
    artist: MusicTasteArtist,
    colors: EditorialColors,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.cardBorder),
        modifier = Modifier.width(110.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(10.dp)
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, colors.accentStrong.copy(alpha = 0.6f), CircleShape)
                        .background(colors.surfaceElevated)
                ) {
                    if (!artist.imageUrl.isNullOrBlank()) {
                        SubcomposeAsyncImage(
                            model = artist.imageUrl,
                            contentDescription = artist.name,
                            contentScale = ContentScale.Crop,
                            loading = { ArtistMonogramPlaceholder(artist.name, colors) },
                            error = { ArtistMonogramPlaceholder(artist.name, colors) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ArtistMonogramPlaceholder(artist.name, colors)
                    }
                }
                Surface(
                    onClick = onRemove,
                    shape = CircleShape,
                    color = colors.surfaceElevated,
                    border = BorderStroke(1.dp, colors.cardBorder),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove ${artist.name}",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = artist.name,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiscoveryTrackReviewRow(
    track: MusicTasteDiscoveryTrack,
    isLiked: Boolean,
    colors: EditorialColors,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceTinted),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                SubcomposeAsyncImage(
                    model = track.artworkUrl,
                    contentDescription = track.title,
                    contentScale = ContentScale.Crop,
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = colors.accentStrong,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = if (isLiked) Icons.Rounded.ThumbUp else Icons.Rounded.ThumbDown,
            contentDescription = null,
            tint = if (isLiked) Color(0xFF2ECC71) else Color(0xFFE05263),
            modifier = Modifier.size(16.dp)
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove ${track.title}",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
