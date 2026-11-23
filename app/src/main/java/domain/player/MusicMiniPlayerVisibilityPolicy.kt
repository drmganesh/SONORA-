package domain.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import domain.home.HomeMusicMode
import domain.library.LibrarySectionMode

enum class MiniPlayerVisualState {
    EXPANDED_BAR,
    CIRCULAR_FLOATING,
    HIDDEN
}

/**
 * Single source of truth governing music mini-player visibility, circular collapse mode,
 * Online Video isolation, and dynamic bottom spacing across SONORA.
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
     * Resolves the active visual representation of the music mini player from the unified
     * [MiniPlayerDisplayState] model:
     * - ALWAYS [MiniPlayerVisualState.HIDDEN] when no track is active or inside Online Video mode.
     * - [MiniPlayerVisualState.DISMISSED -> HIDDEN] when Stage 2 dismissed (restorable via header entry).
     * - [MiniPlayerVisualState.CIRCULAR_FLOATING] when in Stage 1 circular state and Circular Mini Player is enabled.
     * - [MiniPlayerVisualState.EXPANDED_BAR] otherwise.
     */
    fun resolveVisualState(
        hasCurrentSong: Boolean,
        displayState: MiniPlayerDisplayState,
        circularMiniPlayerEnabled: Boolean,
        isInOnlineVideoExperience: Boolean
    ): MiniPlayerVisualState {
        if (!hasCurrentSong || isInOnlineVideoExperience) return MiniPlayerVisualState.HIDDEN
        return when (displayState) {
            MiniPlayerDisplayState.DISMISSED -> MiniPlayerVisualState.HIDDEN
            MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER -> {
                if (circularMiniPlayerEnabled) MiniPlayerVisualState.CIRCULAR_FLOATING
                else MiniPlayerVisualState.EXPANDED_BAR
            }
            MiniPlayerDisplayState.EXPANDED_MINI_PLAYER -> MiniPlayerVisualState.EXPANDED_BAR
        }
    }

    /**
     * Resolves the active visual representation of the music mini player (legacy boolean overload).
     */
    fun resolveVisualState(
        hasCurrentSong: Boolean,
        isMiniPlayerDismissed: Boolean,
        isCollapsedToCircle: Boolean,
        circularMiniPlayerEnabled: Boolean,
        isInOnlineVideoExperience: Boolean
    ): MiniPlayerVisualState {
        val displayState = when {
            isCollapsedToCircle && circularMiniPlayerEnabled -> MiniPlayerDisplayState.CIRCULAR_MINI_PLAYER
            isMiniPlayerDismissed -> MiniPlayerDisplayState.DISMISSED
            else -> MiniPlayerDisplayState.EXPANDED_MINI_PLAYER
        }
        return resolveVisualState(
            hasCurrentSong = hasCurrentSong,
            displayState = displayState,
            circularMiniPlayerEnabled = circularMiniPlayerEnabled,
            isInOnlineVideoExperience = isInOnlineVideoExperience
        )
    }

    /**
     * The standard music mini player bar is shown ONLY when:
     * 1. A music track is active ([hasCurrentSong])
     * 2. The unified visual state is [MiniPlayerDisplayState.EXPANDED_MINI_PLAYER]
     * 3. The user is NOT in any Online Video experience ([isInOnlineVideoExperience] == false)
     */
    fun shouldShowMusicMiniPlayer(
        hasCurrentSong: Boolean,
        displayState: MiniPlayerDisplayState,
        circularMiniPlayerEnabled: Boolean,
        isInOnlineVideoExperience: Boolean
    ): Boolean {
        return resolveVisualState(
            hasCurrentSong = hasCurrentSong,
            displayState = displayState,
            circularMiniPlayerEnabled = circularMiniPlayerEnabled,
            isInOnlineVideoExperience = isInOnlineVideoExperience
        ) == MiniPlayerVisualState.EXPANDED_BAR
    }

    /**
     * Legacy boolean overload preserved for compatibility.
     */
    fun shouldShowMusicMiniPlayer(
        hasCurrentSong: Boolean,
        isMiniPlayerDismissed: Boolean,
        isInOnlineVideoExperience: Boolean,
        isCollapsedToCircle: Boolean = false,
        circularMiniPlayerEnabled: Boolean = false
    ): Boolean {
        return resolveVisualState(
            hasCurrentSong = hasCurrentSong,
            isMiniPlayerDismissed = isMiniPlayerDismissed,
            isCollapsedToCircle = isCollapsedToCircle,
            circularMiniPlayerEnabled = circularMiniPlayerEnabled,
            isInOnlineVideoExperience = isInOnlineVideoExperience
        ) == MiniPlayerVisualState.EXPANDED_BAR
    }

    /**
     * Returns true ONLY when the circular floating music mini player should be shown.
     * Always false in Online Video mode.
     */
    fun shouldShowCircularMiniPlayer(
        hasCurrentSong: Boolean,
        displayState: MiniPlayerDisplayState,
        circularMiniPlayerEnabled: Boolean,
        isInOnlineVideoExperience: Boolean
    ): Boolean {
        return resolveVisualState(
            hasCurrentSong = hasCurrentSong,
            displayState = displayState,
            circularMiniPlayerEnabled = circularMiniPlayerEnabled,
            isInOnlineVideoExperience = isInOnlineVideoExperience
        ) == MiniPlayerVisualState.CIRCULAR_FLOATING
    }

    /**
     * Legacy boolean overload preserved for compatibility.
     */
    fun shouldShowCircularMiniPlayer(
        hasCurrentSong: Boolean,
        isCollapsedToCircle: Boolean,
        circularMiniPlayerEnabled: Boolean,
        isInOnlineVideoExperience: Boolean
    ): Boolean {
        return resolveVisualState(
            hasCurrentSong = hasCurrentSong,
            isMiniPlayerDismissed = false,
            isCollapsedToCircle = isCollapsedToCircle,
            circularMiniPlayerEnabled = circularMiniPlayerEnabled,
            isInOnlineVideoExperience = isInOnlineVideoExperience
        ) == MiniPlayerVisualState.CIRCULAR_FLOATING
    }

    /**
     * Persistent header playback entry allowing the user to restore the normal mini player
     * whenever it is in [MiniPlayerDisplayState.DISMISSED] — strictly restricted to Music modes
     * and never shown inside Online Video experiences.
     */
    fun shouldShowHeaderRestoreEntry(
        hasCurrentSong: Boolean,
        displayState: MiniPlayerDisplayState,
        isInOnlineVideoExperience: Boolean
    ): Boolean {
        return hasCurrentSong &&
            displayState == MiniPlayerDisplayState.DISMISSED &&
            !isInOnlineVideoExperience
    }

    /**
     * Legacy boolean overload preserved for compatibility.
     */
    fun shouldShowHeaderRestoreEntry(
        hasCurrentSong: Boolean,
        isMiniPlayerDismissed: Boolean,
        isInOnlineVideoExperience: Boolean,
        isCollapsedToCircle: Boolean = false,
        circularMiniPlayerEnabled: Boolean = false
    ): Boolean {
        return hasCurrentSong &&
            isMiniPlayerDismissed &&
            !(isCollapsedToCircle && circularMiniPlayerEnabled) &&
            !isInOnlineVideoExperience
    }

    /**
     * Computes bottom list content padding so screens NEVER reserve empty phantom space
     * for a hidden, dismissed, or circular-floating mini player.
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
