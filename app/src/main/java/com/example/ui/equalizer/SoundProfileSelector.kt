package com.example.ui.equalizer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.equalizer.EqualizerPresets
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.PlusJakartaFontFamily
import ui.theme.LocalEditorialColors

/**
 * Sound Profile Selector — SONORA AESTHETIC UI.
 */
@Composable
fun SoundProfileSelector(
    selectedProfileId: String,
    isEnabled: Boolean,
    onSelectProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val colors = LocalEditorialColors.current
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SOUND PROFILES",
                fontFamily = PlusJakartaFontFamily,
                color = colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            val activeProfile = EqualizerPresets.getById(selectedProfileId)
            Text(
                text = if (selectedProfileId == "custom" || selectedProfileId.startsWith("user_")) "Custom Tuning" else activeProfile.name,
                fontFamily = OutfitFontFamily,
                color = if (isEnabled) colors.accentStrong else colors.textMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EqualizerPresets.PROFILES.forEach { profile ->
                val isSelected = profile.id == selectedProfileId
                val activeBg = if (isSelected && isEnabled) colors.accentPill else colors.surface
                val activeBorder = if (isSelected && isEnabled) colors.accentStrong else colors.cardBorder
                val textColor = when {
                    isSelected && isEnabled -> colors.onAccentPill
                    isEnabled -> colors.textPrimary
                    else -> colors.textMuted
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = activeBg,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, activeBorder, RoundedCornerShape(16.dp))
                        .clickable(enabled = isEnabled) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onSelectProfile(profile.id)
                        }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = profile.name,
                            fontFamily = PlusJakartaFontFamily,
                            color = textColor,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
