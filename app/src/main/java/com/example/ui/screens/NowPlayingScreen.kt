package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.viewmodel.SonoraViewModel
import ui.player.PlayerScreen

@Composable
fun NowPlayingScreen(
    viewModel: SonoraViewModel,
    onClose: () -> Unit,
    onOpenEqualizer: (() -> Unit)? = null,
    onOpenAudioStudio: (() -> Unit)? = null,
    onOpenQueueScreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    PlayerScreen(
        viewModel = viewModel,
        onClose = onClose,
        onOpenEqualizer = onOpenEqualizer,
        onOpenAudioStudio = onOpenAudioStudio,
        onOpenQueueScreen = onOpenQueueScreen,
        modifier = modifier.testTag("now_playing_screen")
    )
}
