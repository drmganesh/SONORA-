package com.example

import android.app.ActivityManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.SonoraApp
import com.example.viewmodel.SonoraViewModel
import domain.video.SystemPipParamsSnapshot
import domain.video.SystemPipPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ui.splash.SonoraSplashScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val KEY_LAUNCH_ANIMATION_COMPLETED = "sonora_launch_animation_completed"
        private const val RECENT_APPS_BG_COLOR = 0xFF0D1B26.toInt()
        const val ACTION_PIP_TOGGLE_PLAY_PAUSE = "com.aistudio.sonora.ACTION_PIP_TOGGLE_PLAY_PAUSE"
        const val ACTION_PIP_PLAY_NEXT = "com.aistudio.sonora.ACTION_PIP_PLAY_NEXT"
        private const val REQUEST_CODE_PIP_PLAY_PAUSE = 4101
        private const val REQUEST_CODE_PIP_NEXT = 4102
    }

    private val viewModel: SonoraViewModel by viewModels()
    private val pipSourceRectHintFlow = MutableStateFlow<Rect?>(null)

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_TOGGLE_PLAY_PAUSE -> {
                    viewModel.videoController.toggleVideoPlayPause()
                }
                ACTION_PIP_PLAY_NEXT -> {
                    viewModel.videoController.playNextInQueue()
                }
            }
        }
    }

    private var pipReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val restoredCompleted = savedInstanceState?.getBoolean(KEY_LAUNCH_ANIMATION_COMPLETED, false) == true
        val alreadyPlayedInSession = restoredCompleted || viewModel.hasCompletedLaunchAnimation
        if (alreadyPlayedInSession) {
            viewModel.hasCompletedLaunchAnimation = true
            viewModel.onStartupIntroCompleted()
        }

        // Release the native Android 12+ / compat splash immediately so Compose owns the
        // dark AMOLED launch canvas on frame 1 without duplicate splash screens or blocked startup.
        splashScreen.setKeepOnScreenCondition { false }
        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.remove()
        }

        configureRecentAppsBranding()
        enableEdgeToEdge()
        registerPipControlReceiver()
        observePipParameters()
        handleNotificationVideoIntent(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            viewModel.videoController.onPictureInPictureModeChanged(true)
        }

        setContent {
            var showSplashOverlay by rememberSaveable { mutableStateOf(!alreadyPlayedInSession) }
            var isHomeContentMounted by rememberSaveable { mutableStateOf(alreadyPlayedInSession) }
            val reduceAnimations by viewModel.reduceAnimations.collectAsState()
            val videoPlayerState by viewModel.videoController.playerState.collectAsStateWithLifecycle()
            val isSystemPipVideoOnly = SystemPipPolicy.shouldRenderVideoOnlyPipUi(videoPlayerState)

            Box(modifier = Modifier.fillMaxSize()) {
                if (isHomeContentMounted || isSystemPipVideoOnly) {
                    SonoraApp(
                        viewModel = viewModel,
                        isIntroOverlayActive = showSplashOverlay && !isSystemPipVideoOnly,
                        onVideoViewportBoundsChanged = { rect ->
                            pipSourceRectHintFlow.value = rect
                        }
                    )
                }

                if (showSplashOverlay && !isSystemPipVideoOnly) {
                    SonoraSplashScreen(
                        appReduceAnimations = reduceAnimations,
                        onPrepareHomeContent = {
                            isHomeContentMounted = true
                        },
                        onTransitionToHome = {
                            isHomeContentMounted = true
                            viewModel.onStartupIntroCompleted()
                        },
                        onAnimationFinished = {
                            isHomeContentMounted = true
                            viewModel.hasCompletedLaunchAnimation = true
                            viewModel.onStartupIntroCompleted()
                            showSplashOverlay = false
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationVideoIntent(intent)
    }

    private fun handleNotificationVideoIntent(intent: Intent?) {
        val videoId = intent?.getStringExtra(data.video.ChannelUploadNotificationWorker.EXTRA_OPEN_VIDEO_ID)
            ?.takeIf { it.isNotBlank() } ?: return
        val contentUrl = intent.getStringExtra(data.video.ChannelUploadNotificationWorker.EXTRA_OPEN_VIDEO_URL)
            ?.takeIf { it.isNotBlank() } ?: "https://www.youtube.com/watch?v=$videoId"
        val title = intent.getStringExtra(data.video.ChannelUploadNotificationWorker.EXTRA_OPEN_VIDEO_TITLE)
            ?.takeIf { it.isNotBlank() } ?: "New Upload"
        val channelName = intent.getStringExtra(data.video.ChannelUploadNotificationWorker.EXTRA_OPEN_VIDEO_CHANNEL)
            ?.takeIf { it.isNotBlank() } ?: "Channel"
        val channelUrl = intent.getStringExtra(data.video.ChannelUploadNotificationWorker.EXTRA_OPEN_VIDEO_CHANNEL_URL)
        val thumbnailUrl = intent.getStringExtra(data.video.ChannelUploadNotificationWorker.EXTRA_OPEN_VIDEO_THUMBNAIL)

        // Clear extra so configuration changes do not re-trigger the same notification intent
        intent.removeExtra(data.video.ChannelUploadNotificationWorker.EXTRA_OPEN_VIDEO_ID)
        viewModel.videoController.openVideoFromNotification(
            videoId = videoId,
            contentUrl = contentUrl,
            title = title,
            channelName = channelName,
            channelUrl = channelUrl,
            thumbnailUrl = thumbnailUrl
        )
    }

    override fun onDestroy() {
        if (pipReceiverRegistered) {
            runCatching { unregisterReceiver(pipActionReceiver) }
            pipReceiverRegistered = false
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LAUNCH_ANIMATION_COMPLETED, viewModel.hasCompletedLaunchAnimation)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val videoState = viewModel.videoController.playerState.value
        val autoPipEnabled = videoState.videoExperienceSettings.autoPipEnabled
        if (SystemPipPolicy.shouldAutoEnterSystemPip(videoState, autoPipEnabled)) {
            enterVideoPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.videoController.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            val state = viewModel.videoController.playerState.value
            val rect = pipSourceRectHintFlow.value
            updatePipParamsSafely(
                SystemPipPolicy.buildParamsSnapshot(
                    state = state,
                    autoPipEnabled = state.videoExperienceSettings.autoPipEnabled,
                    sourceRectLeft = rect?.left ?: 0,
                    sourceRectTop = rect?.top ?: 0,
                    sourceRectRight = rect?.right ?: 0,
                    sourceRectBottom = rect?.bottom ?: 0
                )
            )
        } else if (!isInPictureInPictureMode && !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            // PiP window was dismissed/closed (Activity stopped rather than expanded back to foreground)
            viewModel.videoController.onAppBackgrounded()
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegroundedForFestivalSync()
    }

    override fun onStop() {
        super.onStop()
        viewModel.videoController.onAppBackgrounded()
        viewModel.onAppBackgrounded(isChangingConfigurations)
    }

    fun isPictureInPictureSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterVideoPictureInPicture(): Boolean {
        if (!isPictureInPictureSupported()) return false
        val state = viewModel.videoController.playerState.value
        if (!SystemPipPolicy.canEnterSystemPip(state)) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val rect = pipSourceRectHintFlow.value
                val snapshot = SystemPipPolicy.buildParamsSnapshot(
                    state = state,
                    autoPipEnabled = state.videoExperienceSettings.autoPipEnabled,
                    sourceRectLeft = rect?.left ?: 0,
                    sourceRectTop = rect?.top ?: 0,
                    sourceRectRight = rect?.right ?: 0,
                    sourceRectBottom = rect?.bottom ?: 0
                )
                val params = buildPictureInPictureParams(snapshot)
                val entered = enterPictureInPictureMode(params)
                if (entered) {
                    // Sync immediately so Compose switches to the video-only PiP surface without
                    // waiting for an asynchronous callback frame while the PiP window animates.
                    viewModel.videoController.onPictureInPictureModeChanged(true)
                }
                entered
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun registerPipControlReceiver() {
        if (pipReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_TOGGLE_PLAY_PAUSE)
            addAction(ACTION_PIP_PLAY_NEXT)
        }
        runCatching {
            ContextCompat.registerReceiver(
                this,
                pipActionReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            pipReceiverRegistered = true
        }
    }

    private fun observePipParameters() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.videoController.playerState,
                    viewModel.videoExperienceSettings,
                    pipSourceRectHintFlow
                ) { videoState, expSettings, sourceRect ->
                    SystemPipPolicy.buildParamsSnapshot(
                        state = videoState,
                        autoPipEnabled = expSettings.autoPipEnabled,
                        sourceRectLeft = sourceRect?.left ?: 0,
                        sourceRectTop = sourceRect?.top ?: 0,
                        sourceRectRight = sourceRect?.right ?: 0,
                        sourceRectBottom = sourceRect?.bottom ?: 0
                    )
                }
                    .distinctUntilChanged()
                    .collect { snapshot ->
                        updatePipParamsSafely(snapshot)
                    }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipParamsSafely(snapshot: SystemPipParamsSnapshot) {
        if (!isPictureInPictureSupported()) return
        runCatching {
            setPictureInPictureParams(buildPictureInPictureParams(snapshot))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    internal fun buildPictureInPictureParams(snapshot: SystemPipParamsSnapshot): PictureInPictureParams {
        val aspectRatio = Rational(snapshot.aspectNumerator, snapshot.aspectDenominator)

        val actions = mutableListOf<RemoteAction>()
        val playPauseIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_PIP_PLAY_PAUSE,
            Intent(ACTION_PIP_TOGGLE_PLAY_PAUSE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = Icon.createWithResource(
            this,
            if (snapshot.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        val playPauseTitle = if (snapshot.isPlaying) "Pause" else "Play"
        actions.add(RemoteAction(playPauseIcon, playPauseTitle, playPauseTitle, playPauseIntent))

        if (snapshot.hasNextInQueue) {
            val nextIntent = PendingIntent.getBroadcast(
                this,
                REQUEST_CODE_PIP_NEXT,
                Intent(ACTION_PIP_PLAY_NEXT).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val nextIcon = Icon.createWithResource(this, android.R.drawable.ic_media_next)
            actions.add(RemoteAction(nextIcon, "Next", "Next Video", nextIntent))
        }

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)
            .setActions(actions)

        if (snapshot.hasValidSourceRect) {
            builder.setSourceRectHint(
                Rect(
                    snapshot.sourceRectLeft,
                    snapshot.sourceRectTop,
                    snapshot.sourceRectRight,
                    snapshot.sourceRectBottom
                )
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(snapshot.autoEnterEnabled)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    @Suppress("DEPRECATION")
    private fun configureRecentAppsBranding() {
        runCatching {
            val label = getString(R.string.app_name)
            val taskDescription = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    ActivityManager.TaskDescription.Builder()
                        .setLabel(label)
                        .setIcon(R.mipmap.ic_launcher)
                        .setPrimaryColor(RECENT_APPS_BG_COLOR)
                        .build()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> {
                    ActivityManager.TaskDescription(label, R.mipmap.ic_launcher, RECENT_APPS_BG_COLOR)
                }
                else -> {
                    ActivityManager.TaskDescription(label, null, RECENT_APPS_BG_COLOR)
                }
            }
            setTaskDescription(taskDescription)
        }
    }
}
