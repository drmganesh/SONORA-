package ui.settings

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.BuildConfig
import com.example.R
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.EditorialColors
import java.util.Calendar

private const val ABOUT_TAG = "SettingsAboutSection"

data class InstalledAppVersionInfo(
    val versionName: String,
    val versionCode: Long
) {
    val displayLabel: String
        get() = "Version $versionName (Build $versionCode)"

    val compactLabel: String
        get() = "v$versionName"
}

fun readInstalledAppVersion(context: Context): InstalledAppVersionInfo {
    val fallback = InstalledAppVersionInfo(
        versionName = BuildConfig.VERSION_NAME.ifBlank { "1.0" },
        versionCode = BuildConfig.VERSION_CODE.toLong()
    )
    return runCatching {
        @Suppress("DEPRECATION")
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val name = pkgInfo.versionName?.takeIf { it.isNotBlank() } ?: fallback.versionName
        val code = PackageInfoCompat.getLongVersionCode(pkgInfo)
            .takeIf { it > 0L } ?: fallback.versionCode
        InstalledAppVersionInfo(versionName = name, versionCode = code)
    }.onFailure { err ->
        Log.w(ABOUT_TAG, "Unable to read PackageInfo; using BuildConfig fallback", err)
    }.getOrDefault(fallback)
}

@Composable
fun rememberInstalledAppVersion(): InstalledAppVersionInfo {
    val context = LocalContext.current
    return remember(context) { readInstalledAppVersion(context) }
}

/**
 * Complete Settings -> About content block:
 * 1. SONORA application logo
 * 2. Application name: SONORA
 * 3. Description: Premium Music Player
 * 4. Application version (actual installed PackageInfo version)
 * 5. Developer profile card (Portrait, 🩺 Dr. M. GANESWAR, @drmganeswar, (BAMS Final Year))
 * 6. Existing legal/license links
 * 7. Copyright footer
 */
@Composable
fun SettingsAboutContent(
    appVersion: InstalledAppVersionInfo,
    colors: EditorialColors,
    reduceAnimations: Boolean,
    developerPortraitModel: Any? = R.drawable.developer_portrait_drmganeswar,
    linksContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("settings_about_content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // 1. SONORA Application Logo
        SonoraAboutAppLogo(colors = colors)

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Application Name: SONORA
        Text(
            text = stringResource(id = R.string.app_name),
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 22.sp,
            letterSpacing = 1.2.sp,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("about_app_name")
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 3. Description: Premium Music Player
        Text(
            text = stringResource(id = R.string.about_app_description),
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("about_app_description")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 4. Actual Installed Application Version Badge
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = colors.accentPill,
            modifier = Modifier
                .border(0.75.dp, colors.cardBorder, RoundedCornerShape(999.dp))
                .testTag("about_app_version")
        ) {
            Text(
                text = appVersion.displayLabel,
                fontFamily = PlusJakartaFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.5.sp,
                color = colors.onAccentPill,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Developer Profile Card
        DeveloperProfileCard(
            colors = colors,
            reduceAnimations = reduceAnimations,
            portraitModel = developerPortraitModel
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = colors.cardBorder)
        Spacer(modifier = Modifier.height(6.dp))

        // Preserved Privacy Policy & Open-Source Libraries links
        linksContent()

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = colors.cardBorder)
        Spacer(modifier = Modifier.height(12.dp))

        // 6. Copyright Information
        Text(
            text = "© $currentYear SONORA • All rights reserved",
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("about_copyright_text")
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = stringResource(id = R.string.about_developer_footer),
            fontFamily = PlusJakartaFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("about_developer_credit")
        )
    }
}

@Composable
private fun SonoraAboutAppLogo(
    colors: EditorialColors,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logoResId = remember(context) {
        runCatching {
            val id = R.drawable.sonora_official_logo
            if (id != 0) id else null
        }.getOrNull()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceElevated,
        shadowElevation = 8.dp,
        modifier = modifier
            .size(68.dp)
            .border(1.dp, colors.accentStrong.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .testTag("about_app_logo")
    ) {
        if (logoResId != null) {
            Image(
                painter = painterResource(id = logoResId),
                contentDescription = stringResource(id = R.string.about_logo_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.accentStrong),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = stringResource(id = R.string.about_logo_desc),
                    tint = colors.onAccentStrong,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
fun DeveloperProfileCard(
    colors: EditorialColors,
    reduceAnimations: Boolean,
    portraitModel: Any? = R.drawable.developer_portrait_drmganeswar,
    modifier: Modifier = Modifier
) {
    var cardVisible by remember(reduceAnimations) { mutableStateOf(reduceAnimations) }
    LaunchedEffect(reduceAnimations) {
        if (!reduceAnimations) {
            cardVisible = true
        }
    }

    val cardShape = RoundedCornerShape(22.dp)
    val cardGradient = Brush.linearGradient(
        colors = listOf(
            colors.surfaceTinted,
            colors.accentPill.copy(alpha = 0.32f),
            colors.surfaceElevated
        )
    )

    AnimatedVisibility(
        visible = cardVisible,
        enter = if (reduceAnimations) {
            fadeIn(snap())
        } else {
            fadeIn(animationSpec = tween(durationMillis = 280)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetY = { it / 6 }
                ) +
                scaleIn(
                    initialScale = 0.96f,
                    animationSpec = tween(durationMillis = 260)
                )
        },
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 6.dp,
                    shape = cardShape,
                    ambientColor = colors.shadowColor,
                    spotColor = colors.shadowColor
                )
                .clip(cardShape)
                .background(cardGradient)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.accentStrong.copy(alpha = 0.42f),
                            colors.cardBorder
                        )
                    ),
                    shape = cardShape
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .testTag("about_developer_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeveloperPortraitAvatar(
                    portraitModel = portraitModel,
                    colors = colors,
                    size = 82.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.about_developer_eyebrow),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        color = colors.accentStrong
                    )

                    Text(
                        text = stringResource(id = R.string.about_developer_name),
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("about_developer_name")
                    )

                    Text(
                        text = stringResource(id = R.string.about_developer_handle),
                        fontFamily = PlusJakartaFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = colors.accentStrong,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("about_developer_handle")
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.accentPill,
                        modifier = Modifier.border(
                            width = 0.75.dp,
                            color = colors.accentStrong.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    ) {
                        Text(
                            text = stringResource(id = R.string.about_developer_qualification),
                            fontFamily = PlusJakartaFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = colors.onAccentPill,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("about_developer_qualification")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperPortraitAvatar(
    portraitModel: Any?,
    colors: EditorialColors,
    size: Dp = 82.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val portraitDescription = stringResource(id = R.string.about_developer_portrait_desc)
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            colors.accentStrong.copy(alpha = 0.85f),
            colors.cardBorder,
            colors.accentStrong.copy(alpha = 0.45f)
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(
                elevation = 8.dp,
                shape = CircleShape,
                ambientColor = colors.shadowColor,
                spotColor = colors.shadowColor
            )
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .border(1.75.dp, borderBrush, CircleShape)
            .testTag("about_developer_portrait"),
        contentAlignment = Alignment.Center
    ) {
        if (portraitModel != null) {
            val request = remember(portraitModel, context) {
                ImageRequest.Builder(context)
                    .data(portraitModel)
                    .crossfade(false)
                    .listener(
                        onError = { _, result ->
                            Log.w(ABOUT_TAG, "Developer portrait failed to load; showing fallback avatar", result.throwable)
                        }
                    )
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = portraitDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                loading = {
                    DeveloperPortraitFallback(colors = colors, iconSize = size * 0.42f)
                },
                error = {
                    DeveloperPortraitFallback(colors = colors, iconSize = size * 0.42f)
                }
            )
        } else {
            DeveloperPortraitFallback(
                colors = colors,
                iconSize = size * 0.42f,
                contentDescription = portraitDescription
            )
        }
    }
}

@Composable
private fun DeveloperPortraitFallback(
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
            )
            .testTag("about_developer_avatar_fallback"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.MedicalServices,
            contentDescription = contentDescription,
            tint = colors.accentStrong,
            modifier = Modifier.size(iconSize)
        )
    }
}
