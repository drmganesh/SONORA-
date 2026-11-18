package com.example

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.SonoraApp
import com.example.viewmodel.SonoraViewModel
import ui.splash.SonoraSplashScreen

class MainActivity : ComponentActivity() {

    companion object {
        private const val KEY_LAUNCH_ANIMATION_COMPLETED = "sonora_launch_animation_completed"
        private const val RECENT_APPS_BG_COLOR = 0xFF0D1B26.toInt()
    }

    private val viewModel: SonoraViewModel by viewModels()

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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LAUNCH_ANIMATION_COMPLETED, viewModel.hasCompletedLaunchAnimation)
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
