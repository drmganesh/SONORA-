package ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.PlaybackInfo
import com.example.model.Song
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import com.example.viewmodel.OnlineSearchUiState
import domain.online.OnlineErrorKind
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import ui.online.OnlineErrorCard
import ui.online.OnlineProviderPillBar
import ui.online.OnlineResultRow
import ui.theme.EditorialColors

enum class SearchSourceScope(val label: String, val tagSuffix: String) {
    LOCAL(label = "Offline", tagSuffix = "local"),
    ONLINE(label = "Online", tagSuffix = "online"),
    COMBINED(label = "All", tagSuffix = "combined")
}

@Composable
fun SearchScopeSelectorRow(
    selectedScope: SearchSourceScope,
    onSelectScope: (SearchSourceScope) -> Unit,
    colors: EditorialColors,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("search_scope_selector"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchSourceScope.entries.forEach { scope ->
            val selected = scope == selectedScope
            val icon = when (scope) {
                SearchSourceScope.LOCAL -> Icons.Rounded.LibraryMusic
                SearchSourceScope.ONLINE -> Icons.Rounded.CloudQueue
                SearchSourceScope.COMBINED -> Icons.Rounded.TravelExplore
            }
            Surface(
                onClick = { onSelectScope(scope) },
                shape = RoundedCornerShape(18.dp),
                color = if (selected) colors.accentStrong else colors.surface,
                border = BorderStroke(
                    width = if (selected) 1.dp else 0.75.dp,
                    color = if (selected) colors.accentStrong else colors.cardBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("search_scope_${scope.tagSuffix}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) colors.onAccentStrong else colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = scope.label,
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = if (selected) colors.onAccentStrong else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SearchOnlineControlsHeader(
    isNetworkOnline: Boolean,
    providers: List<OnlineMusicProvider>,
    selectedProvider: OnlineMusicProvider?,
    selectedFilter: OnlineSearchFilter,
    query: String,
    suggestions: List<String>,
    colors: EditorialColors,
    onSelectProvider: (OnlineMusicProvider) -> Unit,
    onSelectFilter: (OnlineSearchFilter) -> Unit,
    onExecuteSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_online_controls"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isNetworkOnline) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceTinted,
                border = BorderStroke(0.75.dp, colors.accentStrong.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("search_online_offline_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
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
                        text = "You're offline. Switch to Offline search or reconnect to search NewPipe.",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        OnlineProviderPillBar(
            providers = providers,
            selectedProvider = selectedProvider,
            colors = colors,
            onSelectProvider = onSelectProvider
        )

        val supportedFilters = selectedProvider?.supportedFilters.orEmpty()
        if (supportedFilters.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(supportedFilters, key = { "inline_onl_flt_${it.name}" }) { filter ->
                    val active = filter == selectedFilter
                    Surface(
                        onClick = { onSelectFilter(filter) },
                        shape = RoundedCornerShape(18.dp),
                        color = if (active) colors.accentPill else colors.surfaceTinted,
                        border = BorderStroke(
                            width = 0.75.dp,
                            color = if (active) colors.accentStrong else colors.cardBorder
                        ),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("search_online_filter_${filter.name.lowercase()}")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filter.label,
                                fontFamily = PlusJakartaFontFamily,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (active) colors.onAccentPill else colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        if (suggestions.isNotEmpty() && query.isNotBlank()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(suggestions, key = { "inline_onl_sug_$it" }) { sug ->
                    Surface(
                        onClick = { onExecuteSuggestion(sug) },
                        shape = RoundedCornerShape(16.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(0.75.dp, colors.cardBorder),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = sug,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.sp,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

fun LazyListScope.onlineSearchLazyContent(
    searchState: OnlineSearchUiState,
    searchHistory: List<String>,
    selectedProvider: OnlineMusicProvider?,
    selectedFilter: OnlineSearchFilter,
    query: String,
    currentSong: Song?,
    playbackInfo: PlaybackInfo,
    savedIds: Set<Long>,
    colors: EditorialColors,
    showCombinedHeader: Boolean,
    onExecuteSearch: (String, Boolean) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlayTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenCollection: (OnlineTrack) -> Unit,
    onMoreClick: (OnlineTrack) -> Unit,
    onLoadMore: () -> Unit,
    onRetryPlayback: () -> Unit,
    onSwitchSoundCloudFallback: () -> Unit,
    onDismissPlaybackError: () -> Unit
) {
    val activePlayErr = playbackInfo.playbackError
    if (!activePlayErr.isNullOrBlank()) {
        item(key = "inline_online_playback_error") {
            val errKind = playbackInfo.playbackErrorKind ?: OnlineErrorKind.EXTRACTION_FAILED
            val canSwitchSc = errKind == OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA &&
                selectedProvider?.key != OnlineMusicProvider.KEY_SOUNDCLOUD
            OnlineErrorCard(
                kind = errKind,
                message = activePlayErr,
                showRetry = currentSong != null,
                colors = colors,
                onRetry = onRetryPlayback,
                secondaryActionLabel = if (canSwitchSc) "Try SoundCloud" else null,
                onSecondaryAction = if (canSwitchSc) onSwitchSoundCloudFallback else null,
                onDismiss = onDismissPlaybackError
            )
        }
    }

    if (showCombinedHeader) {
        item(key = "combined_online_section_header") {
            Text(
                text = "Online Music (${selectedProvider?.displayName ?: "YT Music"})",
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = 22.dp, vertical = 6.dp)
                    .testTag("search_combined_online_header")
            )
        }
    }

    when (searchState) {
        is OnlineSearchUiState.Idle -> {
            if (!showCombinedHeader && searchHistory.isNotEmpty()) {
                item(key = "inline_online_history_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = colors.accentStrong,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recent Online Searches",
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.textPrimary
                            )
                        }
                        Text(
                            text = "Clear All",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = colors.accentStrong,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onClearHistory)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                items(searchHistory, key = { "inline_oh_$it" }) { term ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .clickable { onExecuteSearch(term, false) }
                            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = term,
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 14.sp,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { onRemoveHistory(term) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove $term",
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else if (!showCombinedHeader) {
                item(key = "inline_online_prompt") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 44.dp)
                            .testTag("search_online_empty_prompt"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(colors.accentPill, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.onAccentPill,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Search ${selectedProvider?.displayName ?: "Online Music"}",
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Type a song, artist, album, or playlist and tap Search on your keyboard to stream via NewPipe Extractor.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                item(key = "combined_online_idle_hint") {
                    Text(
                        text = if (query.isBlank()) {
                            "Enter a query and press Search to look up online music alongside local files."
                        } else {
                            "Press Search on your keyboard to include online results for \"${query.trim()}\"."
                        },
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
                    )
                }
            }
        }

        is OnlineSearchUiState.Loading -> {
            item(key = "inline_online_loading") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = colors.accentStrong)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Searching ${selectedProvider?.displayName ?: "Online"} for \"${searchState.query}\"...",
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        is OnlineSearchUiState.Error -> {
            item(key = "inline_online_error") {
                val canSwitchSc = searchState.kind == OnlineErrorKind.RATE_LIMITED_OR_CAPTCHA &&
                    selectedProvider?.key != OnlineMusicProvider.KEY_SOUNDCLOUD
                OnlineErrorCard(
                    kind = searchState.kind,
                    message = searchState.message,
                    showRetry = searchState.isRetryable,
                    colors = colors,
                    onRetry = { onExecuteSearch(query, true) },
                    secondaryActionLabel = if (canSwitchSc) "Try SoundCloud" else null,
                    onSecondaryAction = if (canSwitchSc) onSwitchSoundCloudFallback else null
                )
            }
        }

        is OnlineSearchUiState.Success -> {
            val correction = searchState.page.correctedSuggestion
            if (!correction.isNullOrBlank()) {
                item(key = "inline_corrected_suggestion") {
                    Surface(
                        onClick = { onExecuteSearch(correction, false) },
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfaceTinted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Showing results for \"${searchState.page.query}\". Search instead for: $correction",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.accentStrong,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            if (searchState.items.isEmpty()) {
                item(key = "inline_online_no_results") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No ${selectedFilter.label.lowercase()} results found for \"${searchState.page.query}\" on ${selectedProvider?.displayName ?: "provider"}.",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 14.sp,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val playableTracks = searchState.items.filter { it.kind == OnlineItemKind.TRACK }
                items(searchState.items, key = { "inline_res_${it.stableId}_${it.contentUrl}" }) { item ->
                    val isCurrent = currentSong?.id == item.stableId
                    OnlineResultRow(
                        track = item,
                        isCurrent = isCurrent,
                        isPlaying = playbackInfo.isPlaying,
                        isBuffering = playbackInfo.isBuffering,
                        isSaved = item.stableId in savedIds,
                        colors = colors,
                        onClick = {
                            if (item.kind == OnlineItemKind.TRACK) {
                                onPlayTrack(item, playableTracks)
                            } else {
                                onOpenCollection(item)
                            }
                        },
                        onMoreClick = { onMoreClick(item) }
                    )
                }

                if (searchState.page.hasNextPage) {
                    item(key = "inline_online_load_more") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            OutlinedButton(
                                onClick = onLoadMore,
                                enabled = !searchState.isLoadingMore,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, colors.accentStrong),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("search_online_load_more")
                            ) {
                                if (searchState.isLoadingMore) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = colors.accentStrong,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = if (searchState.isLoadingMore) "Loading More..." else "Load More Results",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentStrong
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
