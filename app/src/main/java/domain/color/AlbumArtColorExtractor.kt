package domain.color

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import data.cache.AlbumColorCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Extracts coordinated color palettes from album artwork bitmaps using AndroidX Palette.
 */
object AlbumArtColorExtractor {

    private const val MAX_PALETTE_BITMAP_SIZE = 128

    suspend fun extractPalette(
        bitmap: Bitmap?,
        artworkIdentifier: String
    ): AlbumArtPalette = withContext(Dispatchers.Default) {
        if (bitmap == null || bitmap.isRecycled) {
            return@withContext AlbumArtPalette.default(artworkIdentifier)
        }

        // Check cache first
        AlbumColorCache.get(artworkIdentifier)?.let { cached ->
            return@withContext cached
        }

        try {
            // Downsample bitmap for fast Palette generation without memory bloat
            val scaledBitmap = if (bitmap.width > MAX_PALETTE_BITMAP_SIZE || bitmap.height > MAX_PALETTE_BITMAP_SIZE) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val targetW: Int
                val targetH: Int
                if (ratio > 1f) {
                    targetW = MAX_PALETTE_BITMAP_SIZE
                    targetH = (MAX_PALETTE_BITMAP_SIZE / ratio).toInt().coerceAtLeast(1)
                } else {
                    targetH = MAX_PALETTE_BITMAP_SIZE
                    targetW = (MAX_PALETTE_BITMAP_SIZE * ratio).toInt().coerceAtLeast(1)
                }
                bitmap.scale(targetW, targetH, filter = false)
            } else {
                bitmap
            }

            // Generate Palette synchronously on Dispatchers.Default
            val palette = Palette.from(scaledBitmap)
                .maximumColorCount(24)
                .clearFilters()
                .generate()

            // Swatches
            val dominant = palette.dominantSwatch
            val vibrant = palette.vibrantSwatch
            val darkVibrant = palette.darkVibrantSwatch
            val lightVibrant = palette.lightVibrantSwatch
            val muted = palette.mutedSwatch
            val darkMuted = palette.darkMutedSwatch
            val lightMuted = palette.lightMutedSwatch

            // Check if bitmap is practically monochrome (low saturation across swatches)
            val isMonochrome = checkIsMonochrome(palette)

            // Resolve robust fallback colors if specific swatches are absent
            val fallbackDominant = dominant?.rgb?.let { Color(it) } ?: Color(0xFF20232A)
            val fallbackVibrant = vibrant?.rgb?.let { Color(it) }
                ?: lightVibrant?.rgb?.let { Color(it) }
                ?: muted?.rgb?.let { Color(it) }
                ?: fallbackDominant

            val fallbackDarkVibrant = darkVibrant?.rgb?.let { Color(it) }
                ?: darkMuted?.rgb?.let { Color(it) }
                ?: darken(fallbackDominant, 0.4f)

            val fallbackLightVibrant = lightVibrant?.rgb?.let { Color(it) }
                ?: lightMuted?.rgb?.let { Color(it) }
                ?: lighten(fallbackVibrant, 0.3f)

            val fallbackMuted = muted?.rgb?.let { Color(it) }
                ?: fallbackVibrant

            val fallbackDarkMuted = darkMuted?.rgb?.let { Color(it) }
                ?: darken(fallbackMuted, 0.5f)

            val fallbackLightMuted = lightMuted?.rgb?.let { Color(it) }
                ?: lighten(fallbackMuted, 0.3f)

            val result = AlbumArtPalette(
                dominantColor = fallbackDominant,
                vibrantColor = fallbackVibrant,
                darkVibrantColor = fallbackDarkVibrant,
                lightVibrantColor = fallbackLightVibrant,
                mutedColor = fallbackMuted,
                darkMutedColor = fallbackDarkMuted,
                lightMutedColor = fallbackLightMuted,
                isMonochrome = isMonochrome,
                artworkIdentifier = artworkIdentifier
            )

            // Cache result
            AlbumColorCache.put(artworkIdentifier, result)

            result
        } catch (e: Exception) {
            AlbumArtPalette.default(artworkIdentifier)
        }
    }

    private fun checkIsMonochrome(palette: Palette): Boolean {
        val swatches = palette.swatches
        if (swatches.isEmpty()) return true
        var maxHslSat = 0f
        for (swatch in swatches) {
            val hsl = swatch.hsl
            if (hsl[1] > maxHslSat) {
                maxHslSat = hsl[1]
            }
        }
        // If saturation is under 0.12 across all detected swatches, it's black-and-white
        return maxHslSat < 0.12f
    }

    private fun darken(color: Color, factor: Float): Color {
        return Color(
            red = (color.red * (1f - factor)).coerceIn(0f, 1f),
            green = (color.green * (1f - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1f - factor)).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }

    private fun lighten(color: Color, factor: Float): Color {
        return Color(
            red = (color.red + (1f - color.red) * factor).coerceIn(0f, 1f),
            green = (color.green + (1f - color.green) * factor).coerceIn(0f, 1f),
            blue = (color.blue + (1f - color.blue) * factor).coerceIn(0f, 1f),
            alpha = color.alpha
        )
    }
}
