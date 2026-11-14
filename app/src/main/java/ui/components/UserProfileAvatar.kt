package ui.components

import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.model.UserProfile
import ui.theme.EditorialColors
import java.io.File

/**
 * Circular User Profile Avatar displaying the user's uploaded photo or a clean,
 * dynamic-themed default person avatar. Never falls back to album artwork.
 */
@Composable
fun UserProfileAvatar(
    userProfile: UserProfile,
    colors: EditorialColors,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    borderWidth: Dp = 1.5.dp,
    shadowElevation: Dp = 6.dp,
    reduceAnimations: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !reduceAnimations) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "user_avatar_press_scale"
    )

    val validAvatarUri = remember(userProfile.avatarUri) {
        val raw = userProfile.avatarUri?.trim().orEmpty()
        if (raw.isEmpty()) {
            null
        } else {
            val parsed = runCatching { Uri.parse(raw) }.getOrNull()
            if (parsed?.scheme == "file") {
                val path = parsed.path
                if (path != null && File(path).exists()) parsed else null
            } else {
                parsed
            }
        }
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            role = Role.Button,
            onClick = onClick
        )
    } else {
        Modifier
    }

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            colors.accentStrong.copy(alpha = 0.65f),
            colors.cardBorder.copy(alpha = 0.9f)
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = shadowElevation,
                shape = CircleShape,
                ambientColor = colors.shadowColor,
                spotColor = colors.shadowColor
            )
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .then(clickableModifier)
            .border(borderWidth, borderBrush, CircleShape)
            .testTag("user_profile_avatar"),
        contentAlignment = Alignment.Center
    ) {
        if (validAvatarUri != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(validAvatarUri)
                    .crossfade(!reduceAnimations)
                    .build(),
                contentDescription = "Profile avatar for ${userProfile.displayName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    DefaultPersonAvatarContent(colors = colors, iconSize = size * 0.52f)
                },
                error = {
                    DefaultPersonAvatarContent(colors = colors, iconSize = size * 0.52f)
                }
            )
        } else {
            DefaultPersonAvatarContent(
                colors = colors,
                iconSize = size * 0.52f,
                contentDescription = "Default profile avatar for ${userProfile.displayName}"
            )
        }
    }
}

@Composable
private fun DefaultPersonAvatarContent(
    colors: EditorialColors,
    iconSize: Dp,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.accentPill,
                        colors.surfaceTinted
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = contentDescription,
            tint = colors.accentStrong,
            modifier = Modifier.size(iconSize)
        )
    }
}
