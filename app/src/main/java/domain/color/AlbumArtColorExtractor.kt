package domain.color

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import data.cache.AlbumColorCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Extracts coordinated, clean, perceptually balanced color palettes from album artwork bitmaps
 * using AndroidX Palette.
 *
 * Handles:
 * - Transparent artwork (composited cleanly over dark studio charcoal before quantization)
 * - Monochrome / Black & White artwork
 * - Extremely bright (high-key) & very dark artwork
 * - Muddy brown-grey / low-chroma filtering so accents stay clean and vibrant
 * - Missing or corrupt artwork (graceful SONORA signature fallback)
 */
object AlbumArtColorExtractor {

    private const val MAX_PALETTE_BITMAP_SIZE = 128

    suspend fun extractPalette(
        bitmap: Bitmap?,
        artworkIdentifier: String
    ): AlbumArtPalette = withContext(Dispatchers.Default) {
        if (bitmap == null || bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext AlbumArtPalette.default(artworkIdentifier)
        }

        // Check LRU cache first
        AlbumColorCache.get(artworkIdentifier)?.let { cached ->
            return@withContext cached
        }

        var workingBitmap: Bitmap? = null
        var flattenedBitmap: Bitmap? = null
        try {
            val scaledBitmap = downsampleForPalette(bitmap)
            if (scaledBitmap !== bitmap) workingBitmap = scaledBitmap

            // Flatten transparency only if the image actually contains translucent pixels
            val analysisBitmap = if (scaledBitmap.hasAlpha() && hasTranslucentPixels(scaledBitmap)) {
                val flat = flattenAlpha(scaledBitmap)
                flattenedBitmap = flat
                flat
            } else {
                scaledBitmap
            }

            val palette = Palette.from(analysisBitmap)
                .maximumColorCount(24)
                .clearFilters()
                .generate()

            val swatches = palette.swatches
            if (swatches.isEmpty()) {
                return@withContext AlbumArtPalette.default(artworkIdentifier)
            }

            val isMonochrome = checkIsMonochrome(swatches)
            val weightedAvgLuminance = computeWeightedAverageLuminance(swatches)
            val isDarkArtwork = weightedAvgLuminance < 0.44f

            val cleanChromatic = swatches
                .filter { !isMuddyOrOverlyGray(it.hsl) }
                .sortedByDescending { swatchScore(it) }

            val dominantSwatch = palette.dominantSwatch
            val dominantColor = cleanDominantColor(dominantSwatch, cleanChromatic)

            val vibrantColor = palette.vibrantSwatch
                ?.takeUnless { isMuddyOrOverlyGray(it.hsl) }
                ?.rgb?.let { Color(it) }
                ?: cleanChromatic.firstOrNull()?.rgb?.let { Color(it) }
                ?: palette.lightVibrantSwatch?.rgb?.let { Color(it) }
                ?: palette.mutedSwatch?.rgb?.let { Color(it) }
                ?: dominantColor

            // Pick a tertiary swatch whose hue diverges meaningfully from the primary vibrant color
            val secondaryCandidate = palette.lightVibrantSwatch
                ?.takeUnless { isMuddyOrOverlyGray(it.hsl) }
                ?.rgb?.let { Color(it) }
                ?: palette.mutedSwatch?.takeUnless { isMuddyOrOverlyGray(it.hsl) }?.rgb?.let { Color(it) }
                ?: lighten(vibrantColor, 0.28f)

            val tertiaryCandidate = findDistinctComplementarySwatch(
                primary = vibrantColor,
                swatches = cleanChromatic.ifEmpty { swatches }
            ) ?: rotateHueSafely(vibrantColor, 34f)

            val darkVibrantColor = palette.darkVibrantSwatch?.rgb?.let { Color(it) }
                ?: palette.darkMutedSwatch?.rgb?.let { Color(it) }
                ?: darken(dominantColor, 0.45f)

            val mutedColor = palette.mutedSwatch?.rgb?.let { Color(it) }
                ?: cleanChromatic.getOrNull(1)?.rgb?.let { Color(it) }
                ?: blendColors(vibrantColor, Color(0xFF8C8E99), 0.35f)

            val darkMutedColor = palette.darkMutedSwatch?.rgb?.let { Color(it) }
                ?: darken(mutedColor, 0.55f)

            val lightMutedColor = palette.lightMutedSwatch?.rgb?.let { Color(it) }
                ?: lighten(mutedColor, 0.32f)

            val result = AlbumArtPalette(
                dominantColor = dominantColor,
                vibrantColor = vibrantColor,
                darkVibrantColor = darkVibrantColor,
                lightVibrantColor = secondaryCandidate,
                mutedColor = mutedColor,
                darkMutedColor = darkMutedColor,
                lightMutedColor = lightMutedColor,
                isMonochrome = isMonochrome,
                artworkIdentifier = artworkIdentifier,
                tertiaryColor = tertiaryCandidate,
                averageLuminance = weightedAvgLuminance,
                isDarkArtwork = isDarkArtwork
            )

            AlbumColorCache.put(artworkIdentifier, result)
            result
        } catch (_: Exception) {
            AlbumArtPalette.default(artworkIdentifier)
        } finally {
            if (flattenedBitmap != null && flattenedBitmap !== bitmap) {
                flattenedBitmap.recycle()
            }
            if (workingBitmap != null && workingBitmap !== bitmap) {
                workingBitmap.recycle()
            }
        }
    }

    private fun downsampleForPalette(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= MAX_PALETTE_BITMAP_SIZE && bitmap.height <= MAX_PALETTE_BITMAP_SIZE) {
            return bitmap
        }
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
        return bitmap.scale(targetW, targetH, filter = false)
    }

    private fun hasTranslucentPixels(src: Bitmap): Boolean {
        val w = src.width
        val h = src.height
        val stepX = (w / 16).coerceAtLeast(1)
        val stepY = (h / 16).coerceAtLeast(1)
        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                if ((src.getPixel(x, y) ushr 24) < 250) return true
            }
        }
        return false
    }

    private fun flattenAlpha(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val bgR = 24
        val bgG = 26
        val bgB = 32
        for (i in pixels.indices) {
            val c = pixels[i]
            val a = (c ushr 24) and 0xFF
            if (a < 255) {
                val r = (((c ushr 16) and 0xFF) * a + bgR * (255 - a)) / 255
                val g = (((c ushr 8) and 0xFF) * a + bgG * (255 - a)) / 255
                val b = ((c and 0xFF) * a + bgB * (255 - a)) / 255
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).apply {
            setHasAlpha(false)
        }
    }

    internal fun isMuddyOrOverlyGray(hsl: FloatArray): Boolean {
        val hue = hsl[0]
        val sat = hsl[1]
        val lum = hsl[2]
        // Pure neutral greys
        if (sat < 0.16f) return true
        // Murky dark olive/brownish grey tones
        if (hue in 18f..62f && sat < 0.36f && lum < 0.30f) return true
        // Near-black or blown-out near-white with barely any chroma
        if ((lum < 0.08f || lum > 0.94f) && sat < 0.28f) return true
        return false
    }

    private fun swatchScore(swatch: Palette.Swatch): Float {
        val hsl = swatch.hsl
        val satWeight = hsl[1].coerceIn(0f, 1f) * 2.2f
        val midLumFitness = 1f - abs(hsl[2] - 0.52f)
        val popWeight = kotlin.math.ln((swatch.population + 1).toDouble()).toFloat() * 0.14f
        return satWeight + midLumFitness + popWeight
    }

    private fun cleanDominantColor(
        dominantSwatch: Palette.Swatch?,
        cleanChromatic: List<Palette.Swatch>
    ): Color {
        if (dominantSwatch != null && !isMuddyOrOverlyGray(dominantSwatch.hsl)) {
            return Color(dominantSwatch.rgb)
        }
        return cleanChromatic.firstOrNull()?.rgb?.let { Color(it) }
            ?: dominantSwatch?.rgb?.let { Color(it) }
            ?: Color(0xFF20232A)
    }

    private fun findDistinctComplementarySwatch(
        primary: Color,
        swatches: List<Palette.Swatch>
    ): Color? {
        val primaryHsl = FloatArray(3)
        ColorUtils.colorToHSL(colorToArgbInt(primary), primaryHsl)
        val primaryHue = primaryHsl[0]

        return swatches
            .filter { !isMuddyOrOverlyGray(it.hsl) }
            .maxByOrNull { sw ->
                val rawDiff = abs(sw.hsl[0] - primaryHue)
                val hueDistance = minOf(rawDiff, 360f - rawDiff)
                // Prefer distinct secondary hues (>= 32 deg separation) with healthy saturation
                hueDistance * 0.04f + sw.hsl[1] * 2.0f
            }
            ?.takeIf { sw ->
                val rawDiff = abs(sw.hsl[0] - primaryHue)
                minOf(rawDiff, 360f - rawDiff) >= 28f
            }
            ?.rgb?.let { Color(it) }
    }

    private fun checkIsMonochrome(swatches: List<Palette.Swatch>): Boolean {
        if (swatches.isEmpty()) return true
        var maxSat = 0f
        var weightedSatSum = 0.0
        var totalPop = 0L
        for (swatch in swatches) {
            val s = swatch.hsl[1]
            if (s > maxSat) maxSat = s
            val pop = swatch.population.coerceAtLeast(1).toLong()
            weightedSatSum += s * pop
            totalPop += pop
        }
        val avgSat = if (totalPop > 0L) (weightedSatSum / totalPop).toFloat() else 0f
        return maxSat < 0.14f || avgSat < 0.09f
    }

    private fun computeWeightedAverageLuminance(swatches: List<Palette.Swatch>): Float {
        if (swatches.isEmpty()) return 0.25f
        var lumSum = 0.0
        var totalPop = 0L
        for (swatch in swatches) {
            val lum = Color(swatch.rgb).luminance().toDouble()
            val pop = swatch.population.coerceAtLeast(1).toLong()
            lumSum += lum * pop
            totalPop += pop
        }
        return if (totalPop > 0L) (lumSum / totalPop).toFloat().coerceIn(0f, 1f) else 0.25f
    }

    private fun rotateHueSafely(color: Color, degrees: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(colorToArgbInt(color), hsl)
        hsl[0] = (hsl[0] + degrees + 360f) % 360f
        hsl[1] = hsl[1].coerceIn(0.32f, 0.85f)
        hsl[2] = hsl[2].coerceIn(0.42f, 0.72f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    private fun colorToArgbInt(color: Color): Int {
        return android.graphics.Color.argb(
            (color.alpha * 255f).toInt().coerceIn(0, 255),
            (color.red * 255f).toInt().coerceIn(0, 255),
            (color.green * 255f).toInt().coerceIn(0, 255),
            (color.blue * 255f).toInt().coerceIn(0, 255)
        )
    }

    private fun darken(color: Color, factor: Float): Color {
        return Color(
            red = (color.red * (1f - factor)).coerceIn(0f, 1f),
            green = (color.green * (1f - factor)).coerceIn(0f, 1f),
            blue = (color.blue * (1f - factor)).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    private fun lighten(color: Color, factor: Float): Color {
        return Color(
            red = (color.red + (1f - color.red) * factor).coerceIn(0f, 1f),
            green = (color.green + (1f - color.green) * factor).coerceIn(0f, 1f),
            blue = (color.blue + (1f - color.blue) * factor).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    private fun blendColors(c1: Color, c2: Color, ratio: Float): Color {
        val inv = 1f - ratio
        return Color(
            red = (c1.red * inv + c2.red * ratio).coerceIn(0f, 1f),
            green = (c1.green * inv + c2.green * ratio).coerceIn(0f, 1f),
            blue = (c1.blue * inv + c2.blue * ratio).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
}
