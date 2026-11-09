package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Album
import com.example.model.Song
import com.example.ui.components.ArtworkAlbumView
import com.example.ui.theme.SonoraCardBorder
import com.example.ui.theme.SonoraCyan
import com.example.ui.theme.SonoraGold
import com.example.ui.theme.SonoraGoldDark
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraOnGold
import com.example.ui.theme.SonoraSurfaceDark
import com.example.ui.theme.SonoraSurfaceElevated
import com.example.ui.theme.SonoraTextMuted
import com.example.ui.theme.SonoraTextPrimary
import com.example.ui.theme.SonoraTextSecondary
import com.example.viewmodel.SonoraViewModel
import java.util.Calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Schedule
import com.example.model.DailyAnalytics
import com.example.model.DailyMix

@Composable
fun HomeScreen(
    viewModel: SonoraViewModel,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allSongs by viewModel.allSongs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val playbackInfo by viewModel.playbackInfo.collectAsState()
    val favorites by viewModel.favoriteSongs.collectAsState()
    val mostPlayed by viewModel.mostPlayedSongs.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val spatialConfig by viewModel.spatialConfig.collectAsState()
    val dailyMixes by viewModel.dailyMixes.collectAsState()
    val dailyAnalytics by viewModel.dailyAnalytics.collectAsState()
    val automixEnabled by viewModel.automixEnabled.collectAsState()
    val nextMatchedSong by viewModel.nextMatchedSong.collectAsState()

    val greeting = rememberGreeting()

    // Smooth subtle pulsing animation for Automix and Studio indicators
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Top Header Greeting
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        color = SonoraTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "SONORA Hi-Fi",
                        color = SonoraTextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }

                // Audio Engine Status Badge & Automix Glow
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (automixEnabled) {
                        Box(
                            modifier = Modifier
                                .background(SonoraCyan.copy(alpha = 0.15f * pulseAlpha), RoundedCornerShape(12.dp))
                                .border(1.dp, SonoraCyan.copy(alpha = 0.5f * pulseAlpha), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = SonoraCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "AUTOMIX",
                                    color = SonoraCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .background(SonoraSurfaceElevated, RoundedCornerShape(12.dp))
                            .border(1.dp, SonoraGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(SonoraGold, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "360° DSP",
                                color = SonoraGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Hero Featured Album Art / Continue Listening Banner
        item {
            val heroSong = currentSong ?: allSongs.firstOrNull()
            if (heroSong != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            if (currentSong?.id == heroSong.id) {
                                onOpenNowPlaying()
                            } else {
                                viewModel.playSong(heroSong)
                            }
                        }
                        .testTag("hero_banner"),
                    color = SonoraSurfaceDark,
                    tonalElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        SonoraSurfaceElevated,
                                        SonoraObsidian
                                    )
                                )
                            )
                            .border(1.dp, SonoraCardBorder, RoundedCornerShape(24.dp))
                            .padding(18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArtworkAlbumView(
                                song = heroSong,
                                modifier = Modifier.size(92.dp),
                                cornerRadius = 16.dp,
                                isPlaying = playbackInfo.isPlaying
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CONTINUE LISTENING",
                                    color = SonoraCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = heroSong.title,
                                    color = SonoraTextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = heroSong.artist,
                                    color = SonoraTextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = heroSong.audioFormatInfo,
                                    color = SonoraTextMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Big Play Button
                            IconButton(
                                onClick = {
                                    if (currentSong?.id == heroSong.id) {
                                        viewModel.togglePlayPause()
                                    } else {
                                        viewModel.playSong(heroSong)
                                    }
                                },
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(
                                        Brush.linearGradient(listOf(SonoraGold, SonoraGoldDark)),
                                        CircleShape
                                    )
                                    .testTag("hero_play_button")
                            ) {
                                Icon(
                                    imageVector = if (playbackInfo.isPlaying && currentSong?.id == heroSong.id) Icons.Default.Equalizer else Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = SonoraOnGold,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Audio Action Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionChip(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle All",
                    onClick = {
                        if (allSongs.isNotEmpty()) {
                            viewModel.toggleShuffle()
                            viewModel.playSong(allSongs.shuffled().first(), allSongs.shuffled())
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                QuickActionChip(
                    icon = Icons.Default.GraphicEq,
                    label = "15-Band EQ",
                    onClick = onNavigateToEqualizer,
                    modifier = Modifier.weight(1f)
                )

                QuickActionChip(
                    icon = Icons.Default.SpatialAudio,
                    label = "360° Studio",
                    onClick = onNavigateToEqualizer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Automix & Harmonic Match Indicator Bar (Shows upcoming matched track)
        if (automixEnabled && nextMatchedSong != null && playbackInfo.isPlaying) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { nextMatchedSong?.let { viewModel.playSong(it) } },
                    color = SonoraSurfaceElevated
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, SonoraCyan.copy(alpha = 0.35f * pulseAlpha), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(SonoraCyan.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = SonoraCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AUTOMIX MATCHED NEXT TRACK",
                                color = SonoraCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${nextMatchedSong?.title} • ${nextMatchedSong?.artist}",
                                color = SonoraTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Matched",
                            color = SonoraGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Section: Daily Mix (Custom Personalized Playlists Generated Daily)
        if (dailyMixes.isNotEmpty()) {
            item {
                SectionHeader(title = "Your Daily Mixes")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(dailyMixes) { mix ->
                        DailyMixCard(
                            dailyMix = mix,
                            onClick = {
                                if (mix.songs.isNotEmpty()) {
                                    viewModel.playSong(mix.songs.first(), mix.songs)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Section: Daily Analytics (Listening insights, weekly chart, and peak hours)
        item {
            SectionHeader(title = "Daily Listening Analytics")
            DailyAnalyticsCard(
                analytics = dailyAnalytics,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Section: Recently Played / Recommended
        if (allSongs.isNotEmpty()) {
            item {
                SectionHeader(title = "Featured Master Tracks")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(allSongs.take(6)) { song ->
                        TrackCard(
                            song = song,
                            isPlaying = playbackInfo.isPlaying && currentSong?.id == song.id,
                            onClick = { viewModel.playSong(song) }
                        )
                    }
                }
            }
        }

        // Section: Favorites
        if (favorites.isNotEmpty()) {
            item {
                SectionHeader(title = "Favorites", onSeeAll = onNavigateToPlaylists)
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(favorites) { song ->
                        TrackCard(
                            song = song,
                            isPlaying = playbackInfo.isPlaying && currentSong?.id == song.id,
                            onClick = { viewModel.playSong(song) }
                        )
                    }
                }
            }
        }

        // Section: Studio Albums
        if (albums.isNotEmpty()) {
            item {
                SectionHeader(title = "Studio Albums")
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(albums) { album ->
                        AlbumCard(
                            album = album,
                            onClick = {
                                val albumSongs = allSongs.filter { it.album == album.title }
                                if (albumSongs.isNotEmpty()) {
                                    viewModel.playSong(albumSongs.first(), albumSongs)
                                }
                            }
                        )
                    }
                }
            }
        }

        // Section: Most Played
        if (mostPlayed.isNotEmpty()) {
            item {
                SectionHeader(title = "Most Played")
            }
            items(mostPlayed.take(5)) { song ->
                RowSongItem(
                    song = song,
                    isPlaying = playbackInfo.isPlaying && currentSong?.id == song.id,
                    onClick = { viewModel.playSong(song) },
                    onFavoriteToggle = { viewModel.toggleFavorite(song) }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = SonoraTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        if (onSeeAll != null) {
            Text(
                text = "See All",
                color = SonoraGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onSeeAll() }
            )
        }
    }
}

@Composable
fun TrackCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .testTag("track_card_${song.id}"),
        color = SonoraSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                .padding(10.dp)
        ) {
            ArtworkAlbumView(
                song = song,
                modifier = Modifier
                    .size(128.dp)
                    .clip(RoundedCornerShape(14.dp)),
                cornerRadius = 14.dp,
                isPlaying = isPlaying
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = song.title,
                color = if (isPlaying) SonoraGold else SonoraTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = song.artist,
                color = SonoraTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = song.durationFormatted,
                    color = SonoraTextMuted,
                    fontSize = 10.sp
                )

                Text(
                    text = if (song.mimeType.contains("flac")) "24-BIT" else "320K",
                    color = SonoraCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(148.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() },
        color = SonoraSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(18.dp))
                .padding(10.dp)
        ) {
            ArtworkAlbumView(
                song = null,
                modifier = Modifier.size(128.dp),
                cornerRadius = 14.dp,
                isVinylRotating = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = album.title,
                color = SonoraTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${album.songCount} tracks • ${album.artist}",
                color = SonoraTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RowSongItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isPlaying) SonoraSurfaceElevated else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkAlbumView(
            song = song,
            modifier = Modifier.size(46.dp),
            cornerRadius = 10.dp,
            isPlaying = isPlaying
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isPlaying) SonoraGold else SonoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.durationFormatted}",
                color = SonoraTextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = if (song.isFavorite) SonoraGold else SonoraTextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = SonoraSurfaceElevated,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(14.dp))
                .padding(vertical = 12.dp, horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = SonoraGold,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = SonoraTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun rememberGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..22 -> "Good Evening"
        else -> "Midnight Session"
    }
}

@Composable
fun DailyMixCard(
    dailyMix: DailyMix,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientBrush = remember(dailyMix.gradientColors) {
        Brush.linearGradient(
            dailyMix.gradientColors.map { Color(it) }
        )
    }

    Surface(
        modifier = modifier
            .width(175.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .testTag("daily_mix_${dailyMix.id}"),
        color = SonoraSurfaceElevated,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(151.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(gradientBrush),
                contentAlignment = Alignment.Center
            ) {
                if (dailyMix.coverSong != null) {
                    ArtworkAlbumView(
                        song = dailyMix.coverSong,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 16.dp,
                        isPlaying = false
                    )
                }

                // Overlay gradient for badge readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                )

                // Top Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(SonoraObsidian.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = dailyMix.badge,
                        color = SonoraGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Play Button icon overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .background(SonoraGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = SonoraOnGold,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = dailyMix.title,
                color = SonoraTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = dailyMix.subtitle,
                color = SonoraGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${dailyMix.songs.size} curated tracks",
                color = SonoraTextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun DailyAnalyticsCard(
    analytics: DailyAnalytics,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .testTag("daily_analytics_card"),
        color = SonoraSurfaceElevated,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, SonoraCardBorder, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            // Header: Today's Total Listening
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(SonoraGold.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Insights,
                            contentDescription = null,
                            tint = SonoraGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "TODAY'S AUDIO SESSION",
                            color = SonoraTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = analytics.listenedFormatted,
                            color = SonoraTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(SonoraCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${analytics.tracksPlayed} Plays",
                        color = SonoraCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Weekly Listening Activity Mini Bar Chart
            Text(
                text = "7-Day Listening Trend",
                color = SonoraTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            val maxMinutes = (analytics.weeklyDayMinutes.maxOrNull() ?: 60).coerceAtLeast(30)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                analytics.weeklyDayLabels.forEachIndexed { i, dayLabel ->
                    val mins = analytics.weeklyDayMinutes.getOrElse(i) { 0 }
                    val heightFraction = (mins.toFloat() / maxMinutes.toFloat()).coerceIn(0.12f, 1f)
                    val isToday = i == analytics.weeklyDayLabels.size - 1

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(44.dp * heightFraction)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isToday) Brush.verticalGradient(listOf(SonoraGold, SonoraGoldDark))
                                    else Brush.verticalGradient(listOf(SonoraCyan.copy(alpha = 0.7f), SonoraSurfaceDark))
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = dayLabel,
                            color = if (isToday) SonoraGold else SonoraTextMuted,
                            fontSize = 9.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Key Stats Pills (Top Artist, Top Genre, Peak Time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(SonoraSurfaceDark, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Top Artist", color = SonoraTextMuted, fontSize = 9.sp)
                        Text(
                            text = analytics.topArtist,
                            color = SonoraTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(SonoraSurfaceDark, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Top Genre", color = SonoraTextMuted, fontSize = 9.sp)
                        Text(
                            text = analytics.topGenre,
                            color = SonoraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(SonoraSurfaceDark, RoundedCornerShape(10.dp))
                        .padding(8.dp)
                ) {
                    Column {
                        Text("Weekly Total", color = SonoraTextMuted, fontSize = 9.sp)
                        Text(
                            text = "${analytics.totalWeeklyMinutes} mins",
                            color = SonoraGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
