package domain.color

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.ui.theme.SonoraDustyRose
import com.example.ui.theme.SonoraCharcoal
import com.example.ui.theme.SonoraObsidian
import com.example.ui.theme.SonoraCyan

/**
 * Raw and analyzed colors extracted from an album artwork bitmap.
 */
data class AlbumArtPalette(
    val dominantColor: Color,
    val vibrantColor: Color,
    val darkVibrantColor: Color,
    val lightVibrantColor: Color,
    val mutedColor: Color,
    val darkMutedColor: Color,
    val lightMutedColor: Color,
    val isMonochrome: Boolean = false,
    val artworkIdentifier: String = ""
) {
    companion object {
        /**
         * Default fallback palette when no artwork is present or extraction fails.
         * Uses SONORA's signature Dusty Rose, Charcoal, and Cyan palette.
         */
        fun default(identifier: String = "default"): AlbumArtPalette {
            return AlbumArtPalette(
                dominantColor = Color(0xFF2A1C1B),
                vibrantColor = SonoraDustyRose,
                darkVibrantColor = Color(0xFF5A3530),
                lightVibrantColor = Color(0xFFE2ABA4),
                mutedColor = Color(0xFF8C716E),
                darkMutedColor = Color(0xFF2E2221),
                lightMutedColor = Color(0xFFBFAFAD),
                isMonochrome = false,
                artworkIdentifier = identifier
            )
        }
    }
}
