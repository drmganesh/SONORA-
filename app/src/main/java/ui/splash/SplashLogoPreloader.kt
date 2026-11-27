package ui.splash

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Off-main-thread preloader and process-level cache for the official SONORA splash emblem.
 *
 * Pre-decodes [R.drawable.sonora_official_logo] on [Dispatchers.IO] during Application startup
 * at an appropriately scaled pixel resolution for the display density (and lower memory footprint
 * on low-RAM devices) so Frame 1 of [SonoraSplashScreen] renders without main-thread decoding,
 * redundant allocations, or blank frames.
 */
object SplashLogoPreloader {

    private const val TARGET_LOGO_DP = 132f
    private const val MIN_TARGET_PX = 192
    private const val MAX_TARGET_PX = 512
    private const val LOW_RAM_MAX_TARGET_PX = 256

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val decodeLock = Any()

    @Volatile
    private var cachedLogo: ImageBitmap? = null

    @Volatile
    private var cachedTargetPx: Int = 0

    @Volatile
    private var preloadDispatched: Boolean = false

    fun computeTargetSizePx(context: Context): Int {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val rawPx = (TARGET_LOGO_DP * density).roundToInt()
        val isLowRam = runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.isLowRamDevice == true
        }.getOrDefault(false)
        val maxPx = if (isLowRam) LOW_RAM_MAX_TARGET_PX else MAX_TARGET_PX
        return rawPx.coerceIn(MIN_TARGET_PX, maxPx)
    }

    /**
     * Kicks off asynchronous decoding on [Dispatchers.IO]. Safe to call multiple times;
     * subsequent calls are no-ops once cached or already dispatched.
     */
    fun preloadAsync(context: Context) {
        if (cachedLogo != null) return
        val appContext = context.applicationContext
        val targetPx = computeTargetSizePx(appContext)
        synchronized(decodeLock) {
            if (cachedLogo != null || preloadDispatched) return
            preloadDispatched = true
        }
        preloadScope.launch {
            runCatching { decodeAndCacheLocked(appContext, targetPx) }
                .onFailure {
                    synchronized(decodeLock) { preloadDispatched = false }
                }
        }
    }

    /**
     * Returns the cached [ImageBitmap] immediately without allocation when already preloaded.
     * If called before the background preload finishes on an ultra-fast cold start, decodes
     * once synchronously at the downsampled target size so the intro never renders a blank frame.
     */
    fun getCachedOrDecode(context: Context): ImageBitmap? {
        cachedLogo?.let { return it }
        val appContext = context.applicationContext
        val targetPx = computeTargetSizePx(appContext)
        return runCatching { decodeAndCacheLocked(appContext, targetPx) }.getOrNull()
    }

    /**
     * Non-blocking cache read for diagnostics and tests.
     */
    fun peekCached(): ImageBitmap? = cachedLogo

    private fun decodeAndCacheLocked(context: Context, targetPx: Int): ImageBitmap? {
        synchronized(decodeLock) {
            cachedLogo?.let { existing ->
                if (cachedTargetPx >= targetPx) return existing
            }
            val resources = context.resources
            val logoRes = domain.branding.AppBranding.LOGO_DRAWABLE_RES
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, logoRes, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetPx, targetPx)
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            val decoded = BitmapFactory.decodeResource(resources, logoRes, decodeOpts)
                ?: return null

            val finalBitmap = if (decoded.width > targetPx && decoded.height > targetPx) {
                val scaled = Bitmap.createScaledBitmap(decoded, targetPx, targetPx, true)
                if (scaled !== decoded) decoded.recycle()
                scaled
            } else {
                decoded
            }

            val imageBitmap = finalBitmap.asImageBitmap()
            cachedLogo = imageBitmap
            cachedTargetPx = targetPx
            return imageBitmap
        }
    }

    internal fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    internal fun clearCacheForTesting() {
        synchronized(decodeLock) {
            cachedLogo = null
            cachedTargetPx = 0
            preloadDispatched = false
        }
    }
}
