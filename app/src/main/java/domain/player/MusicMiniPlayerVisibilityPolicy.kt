package domain.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode

enum class MiniPlayerVisualState {
    EXPANDED_BAR,
    CIRCULAR_FLOATING,
    DISMISSED,
    HIDDEN
}

/**
 * Single source of truth governing music mini-player visibility,
 * two-stage circular collapse / dismissal, Online Video isolation,
 * and dynamic bottom spacing across SONORA.
 */
object MusicMiniPlayerVisibilityPolicy {

    /**
     * Returns true whenever the user is inside any Online Video surface:
     * - Online Video Home (including Shorts feed)
     * - Online Video Library
     * - Video Detail / Fullscreen / Shorts Player
     * - Channel Detail screen
     * - Active in-app video mini player
     */
    fun isInOnlineVideoExperience(
        currentTab: Int,
        homeMusicMode: HomeMusicMode,
        librarySectionMode: LibrarySectionMode,
        isVideoPlayerExpanded: Boolean,
        isVideoMiniPlayerVisible: Boolean,
        isChannelDetailOpen: Boolean
    ): Boolean {
        if (isVideoPlayerExpanded || isVideoMiniPlayerVisible || isChannelDetailOpen) return true
        if ((currentTab == 0 || currentTab == 1) && homeMusicMode == HomeMusicMode.ONLINE_VIDEO) return true
        if (currentTab == 2 && librarySectionMode == LibrarySectionMode.ONLINE_VIDEO) return true
        return false
    }

    /**
     * Resolves the active visual representation of the music mini player:
     * - [MiniPlayerVisualState.HIDDEN] when no track is active or inside Online Video mode.
     * - [MiniPlayerVisualState.DISMISSED] when the user dismissed the mini-player UI (playback stays active).
     * - [MiniPlayerVisualState.CIRCULAR_FLOATING] when collapsed to Stage 1 floating circular mode.
     * - [MiniPlayerVisualState.EXPANDED_BAR] whenever a music track is active in standard bar mode.
     */
    fun resolveVisualState(
        hasCurrentSong: Boolean,
        isInOnlineVideoExperience: Boolean,
        isCircularCollapsed: Boolean = false,
        isDismissedByUser: Boolean = false
    ): MiniPlayerVisualState {
        if (!hasCurrentSong || isInOnlineVideoExperience) return MiniPlayerVisualState.HIDDEN
        if (isDismissedByUser) return MiniPlayerVisualState.DISMISSED
        if (isCircularCollapsed) return MiniPlayerVisualState.CIRCULAR_FLOATING
        return MiniPlayerVisualState.EXPANDED_BAR
    }

    /**
     * The standard music mini player bar is shown ONLY when:
     * 1. A music track is active ([hasCurrentSong])
     * 2. The user is NOT in any Online Video experience ([isInOnlineVideoExperience] == false)
     * 3. The user has not collapsed it into circular mode or dismissed its UI.
     */
    fun shouldShowMusicMiniPlayer(
        hasCurrentSong: Boolean,
        isInOnlineVideoExperience: Boolean,
        isCircularCollapsed: Boolean = false,
        isDismissedByUser: Boolean = false
    ): Boolean {
        return resolveVisualState(
            hasCurrentSong = hasCurrentSong,
            isInOnlineVideoExperience = isInOnlineVideoExperience,
            isCircularCollapsed = isCircularCollapsed,
            isDismissedByUser = isDismissedByUser
        ) == MiniPlayerVisualState.EXPANDED_BAR
    }

    /**
     * Computes bottom list content padding so screens never reserve empty phantom space
     * when no mini player is visible.
     */
    fun listBottomContentPadding(
        isMusicMiniPlayerVisible: Boolean,
        isVideoMiniPlayerVisible: Boolean
    ): Dp {
        return if (isMusicMiniPlayerVisible || isVideoMiniPlayerVisible) 184.dp else 104.dp
    }

    /**
     * Computes bottom padding for floating snackbars / download feedback banners.
     */
    fun floatingBannerBottomPadding(
        showBottomChrome: Boolean,
        isMusicMiniPlayerVisible: Boolean,
        isVideoMiniPlayerVisible: Boolean
    ): Dp {
        if (!showBottomChrome) return 20.dp
        return if (isMusicMiniPlayerVisible || isVideoMiniPlayerVisible) 144.dp else 84.dp
    }
}
