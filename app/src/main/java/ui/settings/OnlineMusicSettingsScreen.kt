package ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.SonoraViewModel
import domain.online.AudioQualityPreference
import domain.online.DiscoverMusicLanguage
import domain.online.DiscoverSectionState
import domain.online.OnlineMusicProvider
import domain.online.OnlineRegionCatalog
import ui.theme.EditorialColors
import ui.theme.LocalEditorialColors

/**
 * Dedicated Settings → Online Music screen consolidating all Online Music discovery controls
 * moved from the Home screen:
 *  A. Region & Language (Country, Language, current IN • EN summary, India regional language filter)
 *  B. Streaming Services (YouTube Music, YouTube, SoundCloud, and other supported NewPipe providers)
 *  C. Discovery Preferences (Real Refresh, Recommendations, Auto Radio, Audio Quality, Music Experience)
 *  D. Online Music Library (Live counts + direct access to existing Online Music Library & Explore hub)
 *
 * Reuses [SonoraViewModel], [data.online.OnlineMusicRepository], and DataStore preferences directly
 * without duplicating state or managers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnlineMusicSettingsScreen(
    viewModel: SonoraViewModel,
    onOpenMusicExperience: () -> Unit,
    onOpenOnlineLibrary: () -> Unit,
    onOpenOnlineMusicExplore: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val colors = LocalEditorialColors.current
    val countryCode by viewModel.onlineContentCountry.collectAsStateWithLifecycle()
    val languageCode by viewModel.onlineContentLanguage.collectAsStateWithLifecycle()
    val discoverLanguage by viewModel.discoverMusicLanguage.collectAsStateWithLifecycle()
    val providers by viewModel.onlineProviders.collectAsStateWithLifecycle()
    val selectedProvider by viewModel.selectedOnlineProvider.collectAsStateWithLifecycle()
    val discoverSections by viewModel.discoverSectionsState.collectAsStateWithLifecycle()
    val sonoraRecommendState by viewModel.sonoraRecommendState.collectAsStateWithLifecycle()
    val isNetworkOnline by viewModel.isNetworkOnline.collectAsStateWithLifecycle()
    val musicSettings by viewModel.musicExperienceSettings.collectAsState()
    val qualityPreference by viewModel.onlineQualityPreference.collectAsStateWithLifecycle()

    val savedOnlineTracks by viewModel.onlineSavedTracks.collectAsStateWithLifecycle()
    val onlinePlaylists by viewModel.onlinePlaylists.collectAsStateWithLifecycle()
    val followedArtistIds by viewModel.followedOnlineArtistIds.collectAsStateWithLifecycle()
    val savedAlbumIds by viewModel.savedOnlineAlbumIds.collectAsStateWithLifecycle()
    val recentlyPlayedOnline by viewModel.recentlyPlayedOnlineTracks.collectAsStateWithLifecycle()

    var manualRefreshCount by remember { mutableIntStateOf(0) }

    val countryOption = remember(countryCode) {
        OnlineRegionCatalog.supportedCountries.firstOrNull {
            it.code.equals(countryCode, ignoreCase = true)
        }
    }
    val languageOption = remember(languageCode) {
        OnlineRegionCatalog.supportedLanguages.firstOrNull {
            it.code.equals(languageCode, ignoreCase = true)
        }
    }
    val countryName = countryOption?.name ?: countryCode.uppercase()
    val languageName = languageOption?.name ?: languageCode.uppercase()

    val isDiscoveryLoading = remember(discoverSections, sonoraRecommendState.isLoading, sonoraRecommendState.isRefreshing) {
        discoverSections.values.any { it is DiscoverSectionState.Loading } ||
            sonoraRecommendState.isLoading ||
            sonoraRecommendState.isRefreshing
    }
    val readySectionsCount = remember(discoverSections) {
        discoverSections.values.count { it is DiscoverSectionState.Success }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("online_music_settings_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
        ) {
            // Top Editorial Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
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
                        .testTag("online_music_settings_back_button")
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
                        text = stringResource(id = R.string.settings_online_music_title),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = colors.textPrimary
                    )
                    Text(
                        text = stringResource(id = R.string.settings_online_music_subtitle),
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("online_music_settings_list"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Offline notice banner when disconnected
                if (!isNetworkOnline) {
                    item(key = "online_settings_offline_banner") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("online_settings_offline_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WifiOff,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(id = R.string.settings_online_refresh_offline),
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary
                                )
                            }
                        }
                    }
                }

                // A. REGION & LANGUAGE
                item(key = "online_settings_region_section") {
                    OnlineSettingsSectionCard(
                        eyebrow = stringResource(id = R.string.settings_online_region_section),
                        icon = Icons.Rounded.Public,
                        colors = colors,
                        modifier = Modifier.testTag("online_settings_region_section")
                    ) {
                        // Current Selection Display (preserves IN • EN badge clarity)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("online_settings_current_region_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$countryName • $languageName",
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "Accept-Language: ${languageCode.lowercase()}-${countryCode.uppercase()}" +
                                            if (discoverLanguage != DiscoverMusicLanguage.ALL) " • ${discoverLanguage.label}" else "",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.5.sp,
                                        color = colors.textSecondary
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.accentPill
                                ) {
                                    Text(
                                        text = "${countryCode.uppercase()} • ${languageCode.uppercase()}",
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = colors.onAccentPill,
                                        modifier = Modifier
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("online_settings_region_badge")
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Content Country Selector
                        Text(
                            text = stringResource(id = R.string.settings_online_country_label),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(id = R.string.settings_online_country_hint),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OnlineRegionCatalog.supportedCountries.forEach { option ->
                                val selected = option.code.equals(countryCode, ignoreCase = true)
                                SelectablePillChip(
                                    label = "${option.name} (${option.code})",
                                    selected = selected,
                                    colors = colors,
                                    onClick = { viewModel.setOnlineContentCountry(option.code) },
                                    modifier = Modifier.testTag("online_settings_country_${option.code.lowercase()}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(14.dp))

                        // Preferred Language Selector
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(id = R.string.settings_online_language_label),
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = stringResource(id = R.string.settings_online_language_hint),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OnlineRegionCatalog.supportedLanguages.forEach { option ->
                                val selected = option.code.equals(languageCode, ignoreCase = true)
                                SelectablePillChip(
                                    label = "${option.name} (${option.code})",
                                    selected = selected,
                                    colors = colors,
                                    onClick = { viewModel.setOnlineContentLanguage(option.code) },
                                    modifier = Modifier.testTag("online_settings_language_${option.code.lowercase()}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(14.dp))

                        // India & Regional Language Filter
                        Text(
                            text = stringResource(id = R.string.settings_online_regional_filter_label),
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(id = R.string.settings_online_regional_filter_hint),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            DiscoverMusicLanguage.entries.forEach { lang ->
                                val selected = lang == discoverLanguage
                                SelectablePillChip(
                                    label = lang.label,
                                    selected = selected,
                                    colors = colors,
                                    onClick = { viewModel.setDiscoverMusicLanguage(lang) },
                                    modifier = Modifier.testTag("online_settings_regional_lang_${lang.id}")
                                )
                            }
                        }
                    }
                }

                // B. STREAMING SERVICES (Real NewPipe Extractor Providers)
                item(key = "online_settings_providers_section") {
                    OnlineSettingsSectionCard(
                        eyebrow = stringResource(id = R.string.settings_online_providers_section),
                        icon = Icons.Rounded.CloudQueue,
                        colors = colors,
                        modifier = Modifier.testTag("online_settings_providers_section")
                    ) {
                        Text(
                            text = stringResource(id = R.string.settings_online_providers_hint),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            providers.forEach { provider ->
                                val isSelected = provider.key == selectedProvider?.key
                                ProviderSelectionRow(
                                    provider = provider,
                                    selected = isSelected,
                                    colors = colors,
                                    onClick = { viewModel.selectOnlineProvider(provider.key) }
                                )
                            }
                        }
                    }
                }

                // C. DISCOVERY PREFERENCES
                item(key = "online_settings_discovery_section") {
                    OnlineSettingsSectionCard(
                        eyebrow = stringResource(id = R.string.settings_online_discovery_section),
                        icon = Icons.Rounded.AutoAwesome,
                        colors = colors,
                        modifier = Modifier.testTag("online_settings_discovery_section")
                    ) {
                        // Real Refresh Online Music Discovery Action
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.75.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentPill),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isDiscoveryLoading) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                color = colors.onAccentPill,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = null,
                                                tint = colors.onAccentPill,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(id = R.string.settings_online_refresh_title),
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = colors.textPrimary
                                        )
                                        val statusText = when {
                                            !isNetworkOnline -> stringResource(id = R.string.settings_online_refresh_offline)
                                            isDiscoveryLoading -> stringResource(id = R.string.settings_online_refresh_loading)
                                            manualRefreshCount > 0 ->
                                                "Refreshed for ${selectedProvider?.displayName ?: "YT Music"} ($countryCode • ${languageCode.uppercase()})"
                                            readySectionsCount > 0 ->
                                                "$readySectionsCount discovery sections active • ${selectedProvider?.displayName ?: "YT Music"}"
                                            else -> "Reload charts, trending songs & ${domain.branding.AppBranding.RECOMMEND_NAME}"
                                        }
                                        Text(
                                            text = statusText,
                                            fontFamily = PlusJakartaFontFamily,
                                            fontSize = 11.5.sp,
                                            color = colors.textSecondary
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        manualRefreshCount += 1
                                        viewModel.refreshOnlineDiscovery(forceRefresh = true)
                                    },
                                    enabled = isNetworkOnline,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.accentStrong,
                                        contentColor = colors.onAccentStrong
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("online_settings_refresh_discovery_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isDiscoveryLoading) {
                                            stringResource(id = R.string.settings_online_refresh_loading)
                                        } else {
                                            stringResource(id = R.string.settings_online_refresh_action)
                                        },
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OnlineSettingsSwitchRow(
                            title = "Music Recommendations",
                            subtitle = "Enable personalized mixes, ${domain.branding.AppBranding.RECOMMEND_NAME} & regional discovery",
                            checked = musicSettings.musicRecommendationsEnabled,
                            onCheckedChange = { viewModel.setMusicRecommendationsEnabled(it) },
                            colors = colors,
                            switchTag = "online_settings_recommendations_switch"
                        )
                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 4.dp))
                        OnlineSettingsSwitchRow(
                            title = "Automatic Song Radio",
                            subtitle = "Keep similar non-repeating tracks queued during online radio",
                            checked = musicSettings.autoSongRadioEnabled,
                            onCheckedChange = { viewModel.setAutomaticSongRadioEnabled(it) },
                            colors = colors,
                            switchTag = "online_settings_auto_radio_switch"
                        )

                        HorizontalDivider(color = colors.cardBorder, modifier = Modifier.padding(vertical = 8.dp))

                        // Stream Audio Quality Selector (reuses existing ViewModel preference)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.GraphicEq,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Streaming Audio Quality",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AudioQualityPreference.entries.forEach { pref ->
                                SelectablePillChip(
                                    label = pref.title,
                                    selected = pref == qualityPreference,
                                    colors = colors,
                                    onClick = { viewModel.selectOnlineQualityPreference(pref) },
                                    modifier = Modifier.testTag("online_settings_quality_${pref.id}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(6.dp))

                        OnlineSettingsLinkRow(
                            icon = Icons.Rounded.Radio,
                            title = "Music Experience",
                            subtitle = "Artwork: ${musicSettings.albumArtworkQualityMode.label} • ${if (musicSettings.unlimitedQueueEnabled) "Unlimited Queue" else "Standard Queue"}",
                            colors = colors,
                            onClick = onOpenMusicExperience,
                            modifier = Modifier.testTag("online_settings_open_music_experience")
                        )
                    }
                }

                // D. ONLINE MUSIC LIBRARY
                item(key = "online_settings_library_section") {
                    OnlineSettingsSectionCard(
                        eyebrow = stringResource(id = R.string.settings_online_library_section),
                        icon = Icons.Rounded.LibraryMusic,
                        colors = colors,
                        modifier = Modifier.testTag("online_settings_library_section")
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LibraryMetricBadge(label = "${savedOnlineTracks.size} Saved Songs", colors = colors)
                            LibraryMetricBadge(label = "${onlinePlaylists.size} Playlists", colors = colors)
                            LibraryMetricBadge(label = "${followedArtistIds.size} Artists", colors = colors)
                            LibraryMetricBadge(label = "${savedAlbumIds.size} Albums", colors = colors)
                            LibraryMetricBadge(label = "${recentlyPlayedOnline.size} Recent", colors = colors)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = colors.cardBorder)
                        Spacer(modifier = Modifier.height(4.dp))

                        OnlineSettingsLinkRow(
                            icon = Icons.Rounded.LibraryMusic,
                            title = stringResource(id = R.string.settings_online_open_library_title),
                            subtitle = stringResource(id = R.string.settings_online_open_library_subtitle),
                            colors = colors,
                            onClick = onOpenOnlineLibrary,
                            modifier = Modifier.testTag("online_settings_open_online_library")
                        )

                        OnlineSettingsLinkRow(
                            icon = Icons.Rounded.Explore,
                            title = stringResource(id = R.string.settings_online_explore_title),
                            subtitle = stringResource(id = R.string.settings_online_explore_subtitle),
                            colors = colors,
                            onClick = onOpenOnlineMusicExplore,
                            modifier = Modifier.testTag("online_settings_open_online_explore")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineSettingsSectionCard(
    eyebrow: String,
    icon: ImageVector,
    colors: EditorialColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(24.dp), ambientColor = colors.shadowColor, spotColor = colors.shadowColor)
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(24.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = eyebrow,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.1.sp,
                    color = colors.accentStrong
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ProviderSelectionRow(
    provider: OnlineMusicProvider,
    selected: Boolean,
    colors: EditorialColors,
    onClick: () -> Unit
) {
    val capabilitiesText = buildString {
        append(if (provider.isMusicFocused) "Music-focused" else "Music & media")
        if (provider.supportsDiscoveryFeed) append(" • Discovery")
        if (provider.supportsSuggestions) append(" • Suggestions")
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.accentPill.copy(alpha = 0.55f) else colors.surfaceTinted,
        border = BorderStroke(
            width = if (selected) 1.5.dp else 0.75.dp,
            color = if (selected) colors.accentStrong else colors.cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag("online_settings_provider_${provider.key}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.accentStrong else colors.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = provider.shortLabel.take(3).uppercase(),
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = if (selected) colors.onAccentStrong else colors.accentStrong
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = capabilitiesText,
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Selected provider",
                    tint = colors.accentStrong,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectablePillChip(
    label: String,
    selected: Boolean,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colors.accentStrong else colors.surfaceTinted,
        border = BorderStroke(
            width = if (selected) 1.25.dp else 0.75.dp,
            color = if (selected) colors.accentStrong else colors.cardBorder
        ),
        modifier = modifier.heightIn(min = 42.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp,
                color = if (selected) colors.onAccentStrong else colors.textPrimary
            )
        }
    }
}

@Composable
private fun OnlineSettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: EditorialColors,
    switchTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccentStrong,
                checkedTrackColor = colors.accentStrong,
                uncheckedThumbColor = colors.textMuted,
                uncheckedTrackColor = colors.surfaceTinted
            )
        )
    }
}

@Composable
private fun OnlineSettingsLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.accentPill),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onAccentPill,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = colors.textPrimary
            )
            Text(
                text = subtitle,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 12.sp,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = title,
            tint = colors.textMuted
        )
    }
}

@Composable
private fun LibraryMetricBadge(
    label: String,
    colors: EditorialColors
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(0.75.dp, colors.cardBorder)
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
