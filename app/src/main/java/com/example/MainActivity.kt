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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.ui.SonoraApp
import com.example.viewmodel.SonoraViewModel
import domain.video.VideoPlayerUiState
import kotlinx.coroutines.flow.combine
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

        setContent {
            var showSplashOverlay by rememberSaveable { mutableStateOf(!alreadyPlayedInSession) }
            val reduceAnimations by viewModel.reduceAnimations.collectAsState()

            Box(modifier = Modifier.fillMaxSize()) {
                SonoraApp(viewModel = viewModel)

                if (showSplashOverlay) {
                    SonoraSplashScreen(
                        appReduceAnimations = reduceAnimations,
                        onAnimationFinished = {
                            viewModel.hasCompletedLaunchAnimation = true
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
        if (autoPipEnabled && videoState.hasActiveVideo && videoState.isPlaying) {
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
            updatePipParamsSafely(
                state = viewModel.videoController.playerState.value,
                autoEnterEnabled = viewModel.videoController.playerState.value.videoExperienceSettings.autoPipEnabled
            )
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
        if (!state.hasActiveVideo) return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val params = buildPictureInPictureParams(
                    state = state,
                    autoEnterEnabled = state.videoExperienceSettings.autoPipEnabled
                )
                enterPictureInPictureMode(params)
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
                    viewModel.videoExperienceSettings
                ) { videoState, expSettings ->
                    videoState to expSettings
                }.collect { (videoState, expSettings) ->
                    val allowAuto = expSettings.autoPipEnabled && videoState.hasActiveVideo && videoState.isPlaying
                    updatePipParamsSafely(videoState, allowAuto)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipParamsSafely(state: VideoPlayerUiState, autoEnterEnabled: Boolean) {
        if (!isPictureInPictureSupported()) return
        runCatching {
            setPictureInPictureParams(buildPictureInPictureParams(state, autoEnterEnabled))
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPictureInPictureParams(
        state: VideoPlayerUiState,
        autoEnterEnabled: Boolean
    ): PictureInPictureParams {
        val (effW, effH) = state.effectiveVideoDimensions
        val w = kotlin.math.round(effW).toInt().coerceAtLeast(16)
        val h = kotlin.math.round(effH).toInt().coerceAtLeast(9)
        val ratio = Rational(w, h)
        val clampedRatio = when {
            ratio.toFloat() > 2.39f -> Rational(239, 100)
            ratio.toFloat() < 0.42f -> Rational(42, 100)
            else -> ratio
        }

        val actions = mutableListOf<RemoteAction>()
        val playPauseIntent = PendingIntent.getBroadcast(
            this,
            REQUEST_CODE_PIP_PLAY_PAUSE,
            Intent(ACTION_PIP_TOGGLE_PLAY_PAUSE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIcon = Icon.createWithResource(
            this,
            if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )
        val playPauseTitle = if (state.isPlaying) "Pause" else "Play"
        actions.add(RemoteAction(playPauseIcon, playPauseTitle, playPauseTitle, playPauseIntent))

        if (state.hasNextInQueue) {
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
            .setAspectRatio(clampedRatio)
            .setActions(actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnterEnabled)
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
