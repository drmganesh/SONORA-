package ui.video

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import domain.video.ChannelNotificationPreference
import domain.video.ChannelPlaylistDetailState
import domain.video.ChannelTabPageState
import domain.video.ChannelTabType
import domain.video.OnlineVideoChannel
import domain.video.OnlineVideoItem
import domain.video.OnlineVideoPlaylistSummary
import domain.video.ProviderAuthState
import domain.video.VideoChannelUiState
import ui.theme.EditorialColors

fun shareChannelLink(context: Context, channel: OnlineVideoChannel) {
    runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, channel.name)
            putExtra(Intent.EXTRA_TEXT, "${channel.name} — ${channel.channelUrl}")
        }
        val chooser = Intent.createChooser(sendIntent, "Share channel").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}

/**
 * Full-screen SONORA YouTube-style Channel Detail Page.
 *
 * Preserves existing NewPipe Extractor metadata, Media3 ExoPlayer continuity (never restarts
 * or recreates active video playback when opened), dynamic EditorialColors, per-tab scroll
 * positions, real pagination without duplicate items, and honest local Follow vs authenticated
 * provider Subscription behavior.
 */
@Composable
fun ChannelDetailScreen(
    state: VideoChannelUiState,
    authState: ProviderAuthState,
    supportsNotificationApi: Boolean,
    activeVideoId: String?,
    watchProgressByVideoId: Map<String, Float>,
    hasBottomMiniPlayer: Boolean,
    colors: EditorialColors,
    onBack: () -> Unit,
    onRetryChannel: (String, String) -> Unit,
    onRefreshChannel: () -> Unit,
    onSelectTab: (ChannelTabType) -> Unit,
    onRetryTab: (ChannelTabType) -> Unit,
    onLoadMoreTab: (ChannelTabType) -> Unit,
    onPlayVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onPlayAll: (List<OnlineVideoItem>) -> Unit,
    onPlayBackground: (List<OnlineVideoItem>, String) -> Unit,
    onPlayPopup: (List<OnlineVideoItem>) -> Unit,
    onOpenPlaylist: (OnlineVideoPlaylistSummary) -> Unit,
    onToggleFollowLocal: (OnlineVideoChannel) -> Unit,
    onToggleSubscribeProvider: (OnlineVideoChannel) -> Unit,
    onSelectNotificationPref: (OnlineVideoChannel, ChannelNotificationPreference) -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    onShowToast: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (state is VideoChannelUiState.Idle) return

    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val isProviderConnected = authState is ProviderAuthState.Connected && authState.isTokenValid()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showNotificationSheet by remember { mutableStateOf(false) }

    val activeChannel = (state as? VideoChannelUiState.Success)?.channel
    val topBarTitle = when (state) {
        is VideoChannelUiState.Success -> state.channel.name
        is VideoChannelUiState.Loading -> state.name
        is VideoChannelUiState.Error -> state.channelName
        VideoChannelUiState.Idle -> ""
    }

    Surface(
        color = colors.background,
        contentColor = colors.textPrimary,
        modifier = modifier
            .fillMaxSize()
            .testTag("channel_detail_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("channel_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Navigate back",
                        tint = colors.textPrimary
                    )
                }

                Text(
                    text = topBarTitle,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )

                if (activeChannel != null) {
                    IconButton(
                        onClick = { shareChannelLink(context, activeChannel) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("channel_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Share ${activeChannel.name}",
                            tint = colors.textPrimary
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("channel_more_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More channel options",
                                tint = colors.textPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            containerColor = colors.surfaceElevated
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Refresh Channel",
                                        fontFamily = PlusJakartaFontFamily,
                                        color = colors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.accentStrong)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onRefreshChannel()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Copy Channel URL",
                                        fontFamily = PlusJakartaFontFamily,
                                        color = colors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = colors.accentStrong)
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    copyTextToClipboard(context, activeChannel.name, activeChannel.channelUrl)
                                    onShowToast("Copied channel URL")
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Open in Official YouTube",
                                        fontFamily = PlusJakartaFontFamily,
                                        color = colors.textPrimary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = null,
                                        tint = colors.accentStrong
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    openOfficialExternalUrl(context, activeChannel.channelUrl)
                                }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.cardBorder, thickness = 0.75.dp)

            when (state) {
                is VideoChannelUiState.Loading -> {
                    ChannelLoadingView(
                        channelName = state.name,
                        avatarUrl = state.avatarUrl,
                        colors = colors
                    )
                }
                is VideoChannelUiState.Error -> {
                    ChannelErrorView(
                        channelName = state.channelName,
                        message = state.message,
                        colors = colors,
                        onRetry = { onRetryChannel(state.channelUrl, state.channelName) }
                    )
                }
                is VideoChannelUiState.Success -> {
                    ChannelLoadedBody(
                        state = state,
                        isProviderConnected = isProviderConnected,
                        activeVideoId = activeVideoId,
                        watchProgressByVideoId = watchProgressByVideoId,
                        hasBottomMiniPlayer = hasBottomMiniPlayer,
                        colors = colors,
                        onSelectTab = onSelectTab,
                        onRetryTab = onRetryTab,
                        onLoadMoreTab = onLoadMoreTab,
                        onPlayVideo = onPlayVideo,
                        onPlayAll = onPlayAll,
                        onPlayBackground = onPlayBackground,
                        onPlayPopup = onPlayPopup,
                        onOpenPlaylist = onOpenPlaylist,
                        onToggleFollowLocal = onToggleFollowLocal,
                        onToggleSubscribeProvider = onToggleSubscribeProvider,
                        onOpenNotificationSheet = { showNotificationSheet = true },
                        onVideoMoreClick = onVideoMoreClick,
                        onShareChannel = { shareChannelLink(context, state.channel) },
                        onCopyChannelUrl = {
                            copyTextToClipboard(context, state.channel.name, state.channel.channelUrl)
                            onShowToast("Copied channel URL")
                        },
                        onOpenExternalUrl = { url -> openOfficialExternalUrl(context, url) }
                    )
                }
                VideoChannelUiState.Idle -> Unit
            }
        }
    }

    if (showNotificationSheet && activeChannel != null) {
        NotificationBellOptionsSheet(
            channel = activeChannel,
            currentPreference = activeChannel.notificationPreference,
            supportsApi = supportsNotificationApi && isProviderConnected,
            colors = colors,
            onSelectPreference = { pref -> onSelectNotificationPref(activeChannel, pref) },
            onDismiss = { showNotificationSheet = false }
        )
    }
}

@Composable
private fun ChannelLoadingView(
    channelName: String,
    avatarUrl: String?,
    colors: EditorialColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("channel_loading_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ChannelAvatarBadge(
            avatarUrl = avatarUrl,
            channelName = channelName,
            colors = colors,
            sizeDp = 72
        )
        Spacer(modifier = Modifier.height(16.dp))
        CircularProgressIndicator(color = colors.accentStrong, strokeWidth = 3.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Loading $channelName...",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Fetching channel info, videos, and playlists",
            fontFamily = PlusJakartaFontFamily,
            fontSize = 12.5.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ChannelErrorView(
    channelName: String,
    message: String,
    colors: EditorialColors,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp)
            .testTag("channel_error_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceTinted,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Couldn't load $channelName",
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accentStrong,
                contentColor = colors.onAccentStrong
            ),
            modifier = Modifier
                .height(48.dp)
                .testTag("channel_retry_button")
        ) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Retry",
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChannelLoadedBody(
    state: VideoChannelUiState.Success,
    isProviderConnected: Boolean,
    activeVideoId: String?,
    watchProgressByVideoId: Map<String, Float>,
    hasBottomMiniPlayer: Boolean,
    colors: EditorialColors,
    onSelectTab: (ChannelTabType) -> Unit,
    onRetryTab: (ChannelTabType) -> Unit,
    onLoadMoreTab: (ChannelTabType) -> Unit,
    onPlayVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onPlayAll: (List<OnlineVideoItem>) -> Unit,
    onPlayBackground: (List<OnlineVideoItem>, String) -> Unit,
    onPlayPopup: (List<OnlineVideoItem>) -> Unit,
    onOpenPlaylist: (OnlineVideoPlaylistSummary) -> Unit,
    onToggleFollowLocal: (OnlineVideoChannel) -> Unit,
    onToggleSubscribeProvider: (OnlineVideoChannel) -> Unit,
    onOpenNotificationSheet: () -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    onShareChannel: () -> Unit,
    onCopyChannelUrl: () -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    val channel = state.channel
    // Per-tab scroll states preserved across tab switches for the same channel!
    val videosListState = remember(channel.channelId) { LazyListState() }
    val shortsGridState = remember(channel.channelId) { LazyGridState() }
    val playlistsListState = remember(channel.channelId) { LazyListState() }
    val aboutScrollState = remember(channel.channelId) { androidx.compose.foundation.ScrollState(0) }

    val bottomPadding = if (hasBottomMiniPlayer) 144.dp else 32.dp
    val quickActionVideos = remember(state.currentVideos, state.tabStates) {
        state.currentVideos.ifEmpty {
            (state.tabStates[ChannelTabType.SHORTS] as? ChannelTabPageState.Success)?.videos.orEmpty()
        }
    }

    val showPinnedTabBar by remember(
        state.selectedTab,
        videosListState,
        shortsGridState,
        playlistsListState,
        aboutScrollState
    ) {
        derivedStateOf {
            when (state.selectedTab) {
                ChannelTabType.VIDEOS -> videosListState.firstVisibleItemIndex >= 1
                ChannelTabType.SHORTS -> shortsGridState.firstVisibleItemIndex >= 1
                ChannelTabType.PLAYLISTS -> playlistsListState.firstVisibleItemIndex >= 1
                ChannelTabType.ABOUT -> aboutScrollState.value > 640
            }
        }
    }

    val headerSection: @Composable () -> Unit = {
        ChannelHeaderSection(
            channel = channel,
            isProviderConnected = isProviderConnected,
            hasPlayableVideos = quickActionVideos.isNotEmpty(),
            colors = colors,
            onToggleFollowLocal = { onToggleFollowLocal(channel) },
            onToggleSubscribeProvider = { onToggleSubscribeProvider(channel) },
            onOpenNotificationSheet = onOpenNotificationSheet,
            onPlayBackground = { onPlayBackground(quickActionVideos, channel.name) },
            onPlayAll = { onPlayAll(quickActionVideos) },
            onPlayPopup = { onPlayPopup(quickActionVideos) },
            onOpenAboutTab = { onSelectTab(ChannelTabType.ABOUT) }
        )
    }

    val inlineTabBar: @Composable () -> Unit = {
        ChannelNavigationTabBar(
            availableTabs = state.availableTabs,
            selectedTab = state.selectedTab,
            colors = colors,
            tagPrefix = if (showPinnedTabBar) "inline_channel_tab_" else "channel_tab_",
            onSelectTab = onSelectTab
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (state.selectedTab) {
            ChannelTabType.VIDEOS -> {
                val tabState = state.tabStates[ChannelTabType.VIDEOS] ?: ChannelTabPageState.Success(videos = channel.videos)
                ChannelVideosTabList(
                    tabState = tabState,
                    listState = videosListState,
                    activeVideoId = activeVideoId,
                    watchProgressByVideoId = watchProgressByVideoId,
                    bottomPadding = bottomPadding,
                    colors = colors,
                    headerContent = headerSection,
                    tabBarContent = inlineTabBar,
                    onPlayVideo = onPlayVideo,
                    onVideoMoreClick = onVideoMoreClick,
                    onLoadMore = { onLoadMoreTab(ChannelTabType.VIDEOS) },
                    onRetry = { onRetryTab(ChannelTabType.VIDEOS) }
                )
            }

            ChannelTabType.SHORTS -> {
                val tabState = state.tabStates[ChannelTabType.SHORTS] ?: ChannelTabPageState.Loading
                ChannelShortsTabGrid(
                    tabState = tabState,
                    gridState = shortsGridState,
                    activeVideoId = activeVideoId,
                    bottomPadding = bottomPadding,
                    colors = colors,
                    headerContent = headerSection,
                    tabBarContent = inlineTabBar,
                    onPlayShort = onPlayVideo,
                    onLoadMore = { onLoadMoreTab(ChannelTabType.SHORTS) },
                    onRetry = { onRetryTab(ChannelTabType.SHORTS) }
                )
            }

            ChannelTabType.PLAYLISTS -> {
                val tabState = state.tabStates[ChannelTabType.PLAYLISTS] ?: ChannelTabPageState.Loading
                ChannelPlaylistsTabList(
                    tabState = tabState,
                    listState = playlistsListState,
                    bottomPadding = bottomPadding,
                    colors = colors,
                    headerContent = headerSection,
                    tabBarContent = inlineTabBar,
                    onOpenPlaylist = onOpenPlaylist,
                    onLoadMore = { onLoadMoreTab(ChannelTabType.PLAYLISTS) },
                    onRetry = { onRetryTab(ChannelTabType.PLAYLISTS) }
                )
            }

            ChannelTabType.ABOUT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(aboutScrollState)
                        .navigationBarsPadding()
                        .padding(bottom = bottomPadding)
                        .testTag("channel_tab_content_about")
                ) {
                    headerSection()
                    inlineTabBar()
                    ChannelAboutTabContent(
                        channel = channel,
                        colors = colors,
                        onShareChannel = onShareChannel,
                        onCopyChannelUrl = onCopyChannelUrl,
                        onOpenExternalUrl = onOpenExternalUrl
                    )
                }
            }
        }

        if (showPinnedTabBar) {
            Surface(
                color = colors.background.copy(alpha = 0.97f),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                ChannelNavigationTabBar(
                    availableTabs = state.availableTabs,
                    selectedTab = state.selectedTab,
                    colors = colors,
                    tagPrefix = "channel_tab_",
                    onSelectTab = onSelectTab
                )
            }
        }
    }
}

@Composable
private fun ChannelHeaderSection(
    channel: OnlineVideoChannel,
    isProviderConnected: Boolean,
    hasPlayableVideos: Boolean,
    colors: EditorialColors,
    onToggleFollowLocal: () -> Unit,
    onToggleSubscribeProvider: () -> Unit,
    onOpenNotificationSheet: () -> Unit,
    onPlayBackground: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayPopup: () -> Unit,
    onOpenAboutTab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("channel_header_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Channel Banner with Dynamic SONORA Theme Fallback
        ChannelBannerHeader(channel = channel, colors = colors)

        // 2. Circular Avatar + Channel Identity + Description Preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(2.dp, colors.accentStrong.copy(alpha = 0.5f), CircleShape)
                    .padding(2.dp)
            ) {
                ChannelAvatarBadge(
                    avatarUrl = channel.avatarUrl,
                    channelName = channel.name,
                    colors = colors,
                    sizeDp = 68
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = channel.name,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (channel.isVerified) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verified channel",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val subtitleParts = listOfNotNull(
                    channel.handle?.takeIf { it.isNotBlank() },
                    channel.formattedSubscribers()
                )
                if (subtitleParts.isNotEmpty()) {
                    Text(
                        text = subtitleParts.joinToString(" • "),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.5.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("channel_subscriber_meta")
                    )
                }

                channel.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onOpenAboutTab)
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = desc.replace('\n', ' '),
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 12.sp,
                            color = colors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Read full channel description",
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 3. Honest Subscription / Follow Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isProviderConnected) {
                val subscribed = channel.isSubscribedOnProvider
                Button(
                    onClick = onToggleSubscribeProvider,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (subscribed) colors.surfaceTinted else colors.accentStrong,
                        contentColor = if (subscribed) colors.textPrimary else colors.onAccentStrong
                    ),
                    border = if (subscribed) BorderStroke(1.dp, colors.cardBorder) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("channel_subscribe_button")
                ) {
                    Icon(
                        imageVector = if (subscribed) Icons.Rounded.Check else Icons.Rounded.Subscriptions,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (subscribed) "Subscribed" else "Subscribe",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    onClick = onOpenNotificationSheet,
                    shape = CircleShape,
                    color = colors.surfaceTinted,
                    border = BorderStroke(0.75.dp, colors.cardBorder),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("channel_notification_bell_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (channel.isSubscribedOnProvider) {
                                Icons.Rounded.NotificationsActive
                            } else {
                                Icons.Rounded.NotificationsNone
                            },
                            contentDescription = "Channel notification settings",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            } else {
                // Honest local Follow Channel when unauthenticated (never pretends to be an official YouTube subscription)
                val isFollowing = channel.isSubscribedLocal
                Button(
                    onClick = onToggleFollowLocal,
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) colors.surfaceTinted else colors.accentStrong,
                        contentColor = if (isFollowing) colors.textPrimary else colors.onAccentStrong
                    ),
                    border = if (isFollowing) BorderStroke(1.dp, colors.accentStrong.copy(alpha = 0.55f)) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("channel_follow_button")
                ) {
                    Icon(
                        imageVector = if (isFollowing) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFollowing) "Following (Local)" else "Follow Channel",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (!isProviderConnected && channel.isSubscribedLocal) {
            Text(
                text = "Saved locally in ${domain.branding.AppBranding.APP_NAME} Online Library • Not synced to a YouTube account",
                fontFamily = PlusJakartaFontFamily,
                fontSize = 11.sp,
                color = colors.textMuted,
                modifier = Modifier.testTag("channel_local_follow_notice")
            )
        }

        // 4. Channel Quick Actions Bar: Background, Play All, Popup Player
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("channel_quick_actions_bar"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChannelQuickActionChip(
                icon = Icons.Rounded.Headphones,
                label = "Background",
                enabled = hasPlayableVideos,
                colors = colors,
                testTag = "channel_action_background",
                onClick = onPlayBackground,
                modifier = Modifier.weight(1f)
            )
            ChannelQuickActionChip(
                icon = Icons.Rounded.PlayArrow,
                label = "Play All",
                enabled = hasPlayableVideos,
                colors = colors,
                testTag = "channel_action_play_all",
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            )
            ChannelQuickActionChip(
                icon = Icons.Rounded.PictureInPictureAlt,
                label = "Popup",
                enabled = hasPlayableVideos,
                colors = colors,
                testTag = "channel_action_popup",
                onClick = onPlayPopup,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChannelBannerHeader(
    channel: OnlineVideoChannel,
    colors: EditorialColors
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accentStrong.copy(alpha = 0.85f),
                        colors.accentPill,
                        colors.surfaceElevated
                    )
                )
            )
            .border(0.75.dp, colors.cardBorder, RoundedCornerShape(18.dp))
            .testTag("channel_banner_box")
    ) {
        if (!channel.bannerUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.bannerUrl,
                contentDescription = "${channel.name} banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Dynamic SONORA theme fallback art when banner artwork is unavailable
            val circleColor1 = colors.onAccentStrong.copy(alpha = 0.14f)
            val circleColor2 = colors.accentStrong.copy(alpha = 0.22f)
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = circleColor1,
                    radius = size.height * 0.95f,
                    center = Offset(size.width * 0.18f, size.height * 0.15f)
                )
                drawCircle(
                    color = circleColor2,
                    radius = size.height * 0.85f,
                    center = Offset(size.width * 0.88f, size.height * 0.85f)
                )
            }
            Text(
                text = channel.name.trim().take(2).uppercase(),
                fontFamily = OutfitFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 44.sp,
                color = colors.onAccentStrong.copy(alpha = 0.26f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ChannelQuickActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    colors: EditorialColors,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (enabled) colors.surfaceTinted else colors.surfaceTinted.copy(alpha = 0.45f),
        border = BorderStroke(0.75.dp, if (enabled) colors.cardBorder else colors.cardBorder.copy(alpha = 0.4f)),
        modifier = modifier
            .height(48.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) colors.accentStrong else colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (enabled) colors.textPrimary else colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChannelNavigationTabBar(
    availableTabs: List<ChannelTabType>,
    selectedTab: ChannelTabType,
    colors: EditorialColors,
    tagPrefix: String = "channel_tab_",
    onSelectTab: (ChannelTabType) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("${tagPrefix}row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableTabs.forEach { tab ->
                val isSelected = tab == selectedTab
                Surface(
                    onClick = { onSelectTab(tab) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) colors.accentStrong else colors.surface,
                    border = BorderStroke(
                        width = if (isSelected) 1.dp else 0.75.dp,
                        color = if (isSelected) colors.accentStrong else colors.cardBorder
                    ),
                    modifier = Modifier
                        .height(42.dp)
                        .testTag("$tagPrefix${tab.id}")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            letterSpacing = 0.6.sp,
                            color = if (isSelected) colors.onAccentStrong else colors.textSecondary
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = colors.cardBorder, thickness = 0.75.dp)
    }
}

@Composable
private fun ChannelVideosTabList(
    tabState: ChannelTabPageState,
    listState: LazyListState,
    activeVideoId: String?,
    watchProgressByVideoId: Map<String, Float>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    colors: EditorialColors,
    headerContent: @Composable () -> Unit,
    tabBarContent: @Composable () -> Unit,
    onPlayVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val successState = tabState as? ChannelTabPageState.Success
    val shouldLoadMore by remember(listState, successState?.videos?.size, successState?.nextPageToken, successState?.isLoadingMore) {
        derivedStateOf {
            val s = successState ?: return@derivedStateOf false
            if (s.nextPageToken == null || s.isLoadingMore || s.videos.isEmpty()) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 3 && lastVisible >= totalItems - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("channel_tab_content_videos"),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "channel_videos_header") {
            headerContent()
        }
        item(key = "channel_videos_tabs") {
            tabBarContent()
        }

        when (tabState) {
            is ChannelTabPageState.Idle,
            is ChannelTabPageState.Loading -> {
                item(key = "channel_videos_loading") {
                    ChannelTabLoadingPlaceholder(label = "Loading channel videos...", colors = colors)
                }
            }

            is ChannelTabPageState.Error -> {
                item(key = "channel_videos_error") {
                    ChannelTabErrorPlaceholder(
                        message = tabState.message,
                        isRetryable = tabState.isRetryable,
                        colors = colors,
                        onRetry = onRetry
                    )
                }
            }

            is ChannelTabPageState.Success -> {
                if (tabState.videos.isEmpty()) {
                    item(key = "channel_videos_empty") {
                        ChannelTabEmptyPlaceholder(
                            icon = Icons.Rounded.PlayCircle,
                            title = "No uploaded videos found",
                            subtitle = "This channel hasn't published public videos in this tab yet.",
                            colors = colors
                        )
                    }
                } else {
                    items(tabState.videos, key = { "ch_vid_${it.videoId}" }) { video ->
                        CompactVideoRowCard(
                            video = video,
                            colors = colors,
                            isPlayingNow = video.videoId == activeVideoId,
                            watchProgressFraction = watchProgressByVideoId[video.videoId],
                            onClick = { onPlayVideo(video, tabState.videos) },
                            onMoreClick = { onVideoMoreClick(video) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (tabState.nextPageToken != null) {
                        item(key = "channel_videos_load_more") {
                            ChannelPaginationFooter(
                                isLoadingMore = tabState.isLoadingMore,
                                label = "Load More Videos",
                                colors = colors,
                                testTag = "channel_videos_load_more_button",
                                onLoadMore = onLoadMore
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelShortsTabGrid(
    tabState: ChannelTabPageState,
    gridState: LazyGridState,
    activeVideoId: String?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    colors: EditorialColors,
    headerContent: @Composable () -> Unit,
    tabBarContent: @Composable () -> Unit,
    onPlayShort: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    val successState = tabState as? ChannelTabPageState.Success
    val shouldLoadMore by remember(gridState, successState?.videos?.size, successState?.nextPageToken, successState?.isLoadingMore) {
        derivedStateOf {
            val s = successState ?: return@derivedStateOf false
            if (s.nextPageToken == null || s.isLoadingMore || s.videos.isEmpty()) return@derivedStateOf false
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            totalItems > 3 && lastVisible >= totalItems - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("channel_tab_content_shorts"),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "channel_shorts_header", span = { GridItemSpan(2) }) {
            headerContent()
        }
        item(key = "channel_shorts_tabs", span = { GridItemSpan(2) }) {
            tabBarContent()
        }

        when (tabState) {
            is ChannelTabPageState.Idle,
            is ChannelTabPageState.Loading -> {
                item(key = "channel_shorts_loading", span = { GridItemSpan(2) }) {
                    ChannelTabLoadingPlaceholder(label = "Loading Shorts...", colors = colors)
                }
            }

            is ChannelTabPageState.Error -> {
                item(key = "channel_shorts_error", span = { GridItemSpan(2) }) {
                    ChannelTabErrorPlaceholder(
                        message = tabState.message,
                        isRetryable = tabState.isRetryable,
                        colors = colors,
                        onRetry = onRetry
                    )
                }
            }

            is ChannelTabPageState.Success -> {
                if (tabState.videos.isEmpty()) {
                    item(key = "channel_shorts_empty", span = { GridItemSpan(2) }) {
                        ChannelTabEmptyPlaceholder(
                            icon = Icons.Rounded.ShortText,
                            title = "No Shorts available",
                            subtitle = "This channel does not have short-form videos available right now.",
                            colors = colors
                        )
                    }
                } else {
                    items(
                        items = tabState.videos,
                        key = { "ch_short_${it.videoId}" }
                    ) { shortVideo ->
                        val index = tabState.videos.indexOf(shortVideo)
                        val startPad = if (index % 2 == 0) 16.dp else 0.dp
                        val endPad = if (index % 2 == 1) 16.dp else 0.dp
                        ChannelShortVerticalCard(
                            video = shortVideo,
                            isPlayingNow = shortVideo.videoId == activeVideoId,
                            colors = colors,
                            onClick = { onPlayShort(shortVideo, tabState.videos) },
                            modifier = Modifier.padding(start = startPad, end = endPad)
                        )
                    }

                    if (tabState.nextPageToken != null) {
                        item(key = "channel_shorts_load_more", span = { GridItemSpan(2) }) {
                            ChannelPaginationFooter(
                                isLoadingMore = tabState.isLoadingMore,
                                label = "Load More Shorts",
                                colors = colors,
                                testTag = "channel_shorts_load_more_button",
                                onLoadMore = onLoadMore
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelShortVerticalCard(
    video: OnlineVideoItem,
    isPlayingNow: Boolean,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(
            width = if (isPlayingNow) 1.5.dp else 0.75.dp,
            color = if (isPlayingNow) colors.accentStrong else colors.cardBorder
        ),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .testTag("channel_short_card_${video.videoId}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Bottom gradient scrim for readable title & views
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f))
                        )
                    )
            )

            // Top badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isPlayingNow) colors.accentStrong else Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = if (isPlayingNow) "PLAYING" else "SHORT",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp,
                    color = if (isPlayingNow) colors.onAccentStrong else Color.White,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = video.title,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 16.5.sp,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                video.formattedViewCount()?.let { views ->
                    Text(
                        text = views,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelPlaylistsTabList(
    tabState: ChannelTabPageState,
    listState: LazyListState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    colors: EditorialColors,
    headerContent: @Composable () -> Unit,
    tabBarContent: @Composable () -> Unit,
    onOpenPlaylist: (OnlineVideoPlaylistSummary) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("channel_tab_content_playlists"),
        contentPadding = PaddingValues(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "channel_playlists_header") {
            headerContent()
        }
        item(key = "channel_playlists_tabs") {
            tabBarContent()
        }

        when (tabState) {
            is ChannelTabPageState.Idle,
            is ChannelTabPageState.Loading -> {
                item(key = "channel_playlists_loading") {
                    ChannelTabLoadingPlaceholder(label = "Loading channel playlists...", colors = colors)
                }
            }

            is ChannelTabPageState.Error -> {
                item(key = "channel_playlists_error") {
                    ChannelTabErrorPlaceholder(
                        message = tabState.message,
                        isRetryable = tabState.isRetryable,
                        colors = colors,
                        onRetry = onRetry
                    )
                }
            }

            is ChannelTabPageState.Success -> {
                if (tabState.playlists.isEmpty()) {
                    item(key = "channel_playlists_empty") {
                        ChannelTabEmptyPlaceholder(
                            icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            title = "No public playlists found",
                            subtitle = "This channel does not have public playlists available.",
                            colors = colors
                        )
                    }
                } else {
                    items(
                        items = tabState.playlists,
                        key = { "ch_pl_${it.externalUrl ?: it.name}" }
                    ) { playlist ->
                        ChannelPlaylistRowCard(
                            playlist = playlist,
                            colors = colors,
                            onClick = { onOpenPlaylist(playlist) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (tabState.nextPageToken != null) {
                        item(key = "channel_playlists_load_more") {
                            ChannelPaginationFooter(
                                isLoadingMore = tabState.isLoadingMore,
                                label = "Load More Playlists",
                                colors = colors,
                                testTag = "channel_playlists_load_more_button",
                                onLoadMore = onLoadMore
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelPlaylistRowCard(
    playlist: OnlineVideoPlaylistSummary,
    colors: EditorialColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("channel_playlist_card_${playlist.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(136.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceTinted),
                contentAlignment = Alignment.Center
            ) {
                val thumb = playlist.displayThumbnailUrl
                if (!thumb.isNullOrBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Video count badge overlay
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (playlist.videoCount > 0L) "${playlist.videoCount}" else "Playlist",
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = playlist.name,
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOfNotNull(
                    playlist.channelName?.takeIf { it.isNotBlank() },
                    playlist.videoCount.takeIf { it > 0L }?.let { "$it videos" }
                ).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 11.5.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open playlist",
                tint = colors.textMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelAboutTabContent(
    channel: OnlineVideoChannel,
    colors: EditorialColors,
    onShareChannel: () -> Unit,
    onCopyChannelUrl: () -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Full Description
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            border = BorderStroke(0.75.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Description",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
                Text(
                    text = channel.description?.takeIf { it.isNotBlank() }
                        ?: "This channel has not provided a description.",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = colors.textSecondary
                )
            }
        }

        // Real Channel Details & Stats (only display fields actually returned by extractor)
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            border = BorderStroke(0.75.dp, colors.cardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Channel Info",
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = colors.textPrimary
                )
                channel.handle?.takeIf { it.isNotBlank() }?.let { handle ->
                    AboutDetailRow(label = "Handle", value = handle, colors = colors)
                }
                channel.formattedSubscribers()?.let { subs ->
                    AboutDetailRow(label = "Subscribers", value = subs, colors = colors)
                }
                channel.formattedTotalViews()?.let { totalViews ->
                    AboutDetailRow(label = "Total views", value = totalViews, colors = colors)
                }
                channel.joinedDateText?.takeIf { it.isNotBlank() }?.let { joined ->
                    AboutDetailRow(label = "Joined", value = joined, colors = colors)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onCopyChannelUrl)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Channel URL",
                            fontFamily = PlusJakartaFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.textMuted
                        )
                        Text(
                            text = channel.channelUrl,
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.5.sp,
                            color = colors.accentStrong,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onCopyChannelUrl, modifier = Modifier.size(44.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy channel URL",
                            tint = colors.accentStrong,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // External Links extracted from description
        if (channel.externalLinks.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surface,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Links",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    channel.externalLinks.forEach { link ->
                        Surface(
                            onClick = { onOpenExternalUrl(link.url) },
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surfaceTinted,
                            border = BorderStroke(0.5.dp, colors.cardBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = colors.accentStrong,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = link.label,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = link.url,
                                        fontFamily = PlusJakartaFontFamily,
                                        fontSize = 11.sp,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "Open link",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Channel Tags when available
        if (channel.tags.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surface,
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Topics",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        channel.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = colors.surfaceTinted,
                                border = BorderStroke(0.5.dp, colors.cardBorder)
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontFamily = PlusJakartaFontFamily,
                                    fontSize = 12.sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Share & Official App Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onShareChannel,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentStrong,
                    contentColor = colors.onAccentStrong
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("channel_about_share_button")
            ) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Share Channel",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            OutlinedButton(
                onClick = { onOpenExternalUrl(channel.channelUrl) },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(0.75.dp, colors.cardBorder),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    tint = colors.accentStrong,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open YouTube",
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun AboutDetailRow(
    label: String,
    value: String,
    colors: EditorialColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 12.5.sp,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChannelTabLoadingPlaceholder(
    label: String,
    colors: EditorialColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(
            color = colors.accentStrong,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = label,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 13.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ChannelTabErrorPlaceholder(
    message: String,
    isRetryable: Boolean,
    colors: EditorialColors,
    onRetry: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = colors.surfaceTinted,
        border = BorderStroke(0.75.dp, colors.cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = message,
                fontFamily = PlusJakartaFontFamily,
                fontSize = 13.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (isRetryable) {
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.accentStrong),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = colors.accentStrong,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Retry",
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentStrong
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelTabEmptyPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    colors: EditorialColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(34.dp)
        )
        Text(
            text = title,
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.5.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            fontFamily = PlusJakartaFontFamily,
            fontSize = 12.5.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChannelPaginationFooter(
    isLoadingMore: Boolean,
    label: String,
    colors: EditorialColors,
    testTag: String,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isLoadingMore) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(vertical = 10.dp)
            ) {
                CircularProgressIndicator(
                    color = colors.accentStrong,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Loading more...",
                    fontFamily = PlusJakartaFontFamily,
                    fontSize = 12.5.sp,
                    color = colors.textSecondary
                )
            }
        } else {
            OutlinedButton(
                onClick = onLoadMore,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, colors.accentStrong),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(testTag)
            ) {
                Text(
                    text = label,
                    fontFamily = PlusJakartaFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentStrong
                )
            }
        }
    }
}

/**
 * Dedicated Playlist Detail Sheet opened when tapping any public playlist on a Channel Detail Page.
 * Loads real playlist tracks from NewPipe Extractor with Play All, Background Audio, and pagination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelPlaylistDetailSheet(
    state: ChannelPlaylistDetailState,
    activeVideoId: String?,
    watchProgressByVideoId: Map<String, Float>,
    colors: EditorialColors,
    onPlayVideo: (OnlineVideoItem, List<OnlineVideoItem>) -> Unit,
    onPlayAll: (List<OnlineVideoItem>) -> Unit,
    onPlayBackground: (List<OnlineVideoItem>, String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: (OnlineVideoPlaylistSummary) -> Unit,
    onVideoMoreClick: (OnlineVideoItem) -> Unit,
    onDismiss: () -> Unit
) {
    if (state is ChannelPlaylistDetailState.Idle) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        when (state) {
            is ChannelPlaylistDetailState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp)
                        .testTag("channel_playlist_detail_loading"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = colors.accentStrong)
                    Text(
                        text = "Loading ${state.title}...",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.textPrimary
                    )
                }
            }

            is ChannelPlaylistDetailState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = state.message,
                        fontFamily = PlusJakartaFontFamily,
                        fontSize = 13.5.sp,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = {
                            onRetry(
                                OnlineVideoPlaylistSummary(
                                    externalUrl = state.playlistUrl,
                                    name = state.title
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentStrong)
                    ) {
                        Text("Retry", color = colors.onAccentStrong)
                    }
                }
            }

            is ChannelPlaylistDetailState.Success -> {
                val playlist = state.playlist
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("channel_playlist_detail_sheet"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    item(key = "pl_header") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = playlist.name,
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = colors.textPrimary
                            )
                            val meta = listOfNotNull(
                                playlist.channelName?.takeIf { it.isNotBlank() },
                                "${playlist.videos.size} loaded" +
                                    if (playlist.videoCount > playlist.videos.size) " of ${playlist.videoCount} videos" else " videos"
                            ).joinToString(" • ")
                            Text(
                                text = meta,
                                fontFamily = PlusJakartaFontFamily,
                                fontSize = 12.5.sp,
                                color = colors.textSecondary
                            )

                            if (playlist.videos.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onPlayAll(playlist.videos)
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.accentStrong,
                                            contentColor = colors.onAccentStrong
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Play All",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onPlayBackground(playlist.videos, playlist.name)
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(0.75.dp, colors.cardBorder),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Headphones,
                                            contentDescription = null,
                                            tint = colors.accentStrong,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Background",
                                            fontFamily = PlusJakartaFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = colors.cardBorder)
                        }
                    }

                    if (playlist.videos.isEmpty()) {
                        item(key = "pl_empty") {
                            ChannelTabEmptyPlaceholder(
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                title = "No playable videos in this playlist",
                                subtitle = "Videos may be private or unavailable in your region.",
                                colors = colors
                            )
                        }
                    } else {
                        items(playlist.videos, key = { "ch_pl_vid_${it.videoId}" }) { video ->
                            CompactVideoRowCard(
                                video = video,
                                colors = colors,
                                isPlayingNow = video.videoId == activeVideoId,
                                watchProgressFraction = watchProgressByVideoId[video.videoId],
                                onClick = {
                                    onDismiss()
                                    onPlayVideo(video, playlist.videos)
                                },
                                onMoreClick = { onVideoMoreClick(video) }
                            )
                        }
                        if (state.nextPageToken != null) {
                            item(key = "pl_load_more") {
                                ChannelPaginationFooter(
                                    isLoadingMore = state.isLoadingMore,
                                    label = "Load More Playlist Videos",
                                    colors = colors,
                                    testTag = "channel_playlist_load_more_button",
                                    onLoadMore = onLoadMore
                                )
                            }
                        }
                    }
                }
            }

            ChannelPlaylistDetailState.Idle -> Unit
        }
    }
}
