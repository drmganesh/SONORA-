package domain.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for SONORA's Smart Auto Aspect Ratio engine and Central Swipe Gesture Classifier.
 * Covers scenarios 1–10: 16:9, 9:16 + rotation metadata, 4:3, 1:1, custom ratios, Auto, Fit, Fill,
 * fixed display frames, explicit Stretch, central swipe zone isolation, and conflict prevention.
 */
class VideoAspectRatioAndGesturesEngineTest {

    @Test
    fun test01_16by9VideoDetectionAndAutoLayout() {
        val ratio = VideoAspectRatioCalculator.resolveDisplayAspectRatio(1920, 1080)
        assertEquals(16f / 9f, ratio, 0.01f)
        assertEquals("16:9", VideoAspectRatioCalculator.detectRatioLabel(ratio))

        val layout = VideoAspectRatioCalculator.computeSurfaceLayout(
            viewportWidthPx = 2400f,
            viewportHeightPx = 1080f,
            videoWidth = 1920,
            videoHeight = 1080,
            mode = VideoAspectRatioMode.AUTO
        )
        assertEquals(1920f, layout.frameWidthPx, 1.5f)
        assertEquals(1080f, layout.frameHeightPx, 1.5f)
        assertFalse(layout.isCropped)
        assertFalse(layout.isDistorted)
    }

    @Test
    fun test02_9by16PortraitVideoAndRotationMetadata() {
        val directRatio = VideoAspectRatioCalculator.resolveDisplayAspectRatio(1080, 1920)
        assertEquals(9f / 16f, directRatio, 0.01f)
        assertEquals("9:16", VideoAspectRatioCalculator.detectRatioLabel(directRatio))

        // Encoded 1920x1080 with 90° or 270° rotation metadata resolves as true 9:16 portrait
        val rotated90 = VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = 1920,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1f,
            unappliedRotationDegrees = 90
        )
        assertEquals(9f / 16f, rotated90, 0.01f)
        assertEquals("9:16", VideoAspectRatioCalculator.detectRatioLabel(rotated90))

        val rotated270 = VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = 1920,
            videoHeight = 1080,
            unappliedRotationDegrees = 270
        )
        assertEquals(9f / 16f, rotated270, 0.01f)

        val layout = VideoAspectRatioCalculator.computeSurfaceLayout(
            viewportWidthPx = 1080f,
            viewportHeightPx = 1920f,
            videoWidth = 1920,
            videoHeight = 1080,
            unappliedRotationDegrees = 90,
            mode = VideoAspectRatioMode.AUTO
        )
        assertEquals(1080f, layout.surfaceWidthPx, 1f)
        assertEquals(1920f, layout.surfaceHeightPx, 1f)
        assertFalse(layout.isDistorted)
    }

    @Test
    fun test03_4by3VideoDetectionAndLayout() {
        val ratio = VideoAspectRatioCalculator.resolveDisplayAspectRatio(1440, 1080)
        assertEquals(4f / 3f, ratio, 0.01f)
        assertEquals("4:3", VideoAspectRatioCalculator.detectRatioLabel(ratio))

        val layout = VideoAspectRatioCalculator.computeSurfaceLayout(
            viewportWidthPx = 1920f,
            viewportHeightPx = 1080f,
            videoWidth = 1440,
            videoHeight = 1080,
            mode = VideoAspectRatioMode.AUTO
        )
        assertEquals(1440f, layout.frameWidthPx, 1.5f)
        assertEquals(1080f, layout.frameHeightPx, 1.5f)
        assertFalse(layout.isDistorted)
    }

    @Test
    fun test04_1by1SquareVideoDetectionAndLayout() {
        val ratio = VideoAspectRatioCalculator.resolveDisplayAspectRatio(1080, 1080)
        assertEquals(1f, ratio, 0.01f)
        assertEquals("1:1", VideoAspectRatioCalculator.detectRatioLabel(ratio))

        val layout = VideoAspectRatioCalculator.computeSurfaceLayout(
            viewportWidthPx = 1920f,
            viewportHeightPx = 1080f,
            videoWidth = 1080,
            videoHeight = 1080,
            mode = VideoAspectRatioMode.AUTO
        )
        assertEquals(1080f, layout.frameWidthPx, 1.5f)
        assertEquals(1080f, layout.frameHeightPx, 1.5f)
        assertFalse(layout.isCropped)
        assertFalse(layout.isDistorted)
    }

    @Test
    fun test05_customAspectRatiosPixelAspectRatioAndUnknownDimensionsFallback() {
        assertEquals("18:9", VideoAspectRatioCalculator.detectRatioLabel(2160f / 1080f))
        assertEquals("19.5:9", VideoAspectRatioCalculator.detectRatioLabel(2340f / 1080f))
        assertEquals("20:9", VideoAspectRatioCalculator.detectRatioLabel(2400f / 1080f))
        assertEquals("3:2", VideoAspectRatioCalculator.detectRatioLabel(1620f / 1080f))
        assertEquals("4:5", VideoAspectRatioCalculator.detectRatioLabel(864f / 1080f))
        assertEquals("2.39:1", VideoAspectRatioCalculator.detectRatioLabel(2.39f))

        val anamorphic = VideoAspectRatioCalculator.resolveDisplayAspectRatio(
            videoWidth = 1440,
            videoHeight = 1080,
            pixelWidthHeightRatio = 1.3333f
        )
        assertEquals(16f / 9f, anamorphic, 0.02f)

        val unknownFallback = VideoAspectRatioCalculator.resolveDisplayAspectRatio(0, 0)
        assertEquals(16f / 9f, unknownFallback, 0.01f)
    }

    @Test
    fun test06_07_08_allEightAspectRatioModesBehaviorAndZeroUnwantedDistortion() {
        val vpW = 2400f
        val vpH = 1080f

        val autoLayout = VideoAspectRatioCalculator.computeSurfaceLayout(vpW, vpH, 1920, 1080, mode = VideoAspectRatioMode.AUTO)
        val fitLayout = VideoAspectRatioCalculator.computeSurfaceLayout(vpW, vpH, 1920, 1080, mode = VideoAspectRatioMode.FIT)
        assertEquals(autoLayout.surfaceWidthPx, fitLayout.surfaceWidthPx, 0.5f)
        assertFalse(fitLayout.isCropped)
        assertFalse(fitLayout.isDistorted)

        val fillLayout = VideoAspectRatioCalculator.computeSurfaceLayout(vpW, vpH, 1920, 1080, mode = VideoAspectRatioMode.FILL)
        assertEquals(vpW, fillLayout.frameWidthPx, 0.5f)
        assertEquals(vpH, fillLayout.frameHeightPx, 0.5f)
        assertTrue(fillLayout.surfaceHeightPx > vpH)
        assertEquals(16f / 9f, fillLayout.surfaceWidthPx / fillLayout.surfaceHeightPx, 0.02f)
        assertTrue(fillLayout.isCropped)
        assertFalse(fillLayout.isDistorted)

        listOf(
            VideoAspectRatioMode.RATIO_16_9 to (16f / 9f),
            VideoAspectRatioMode.RATIO_4_3 to (4f / 3f),
            VideoAspectRatioMode.RATIO_1_1 to 1f,
            VideoAspectRatioMode.RATIO_9_16 to (9f / 16f)
        ).forEach { (mode, expectedFrameRatio) ->
            val fixed = VideoAspectRatioCalculator.computeSurfaceLayout(vpW, vpH, 1920, 1080, mode = mode)
            assertEquals(expectedFrameRatio, fixed.displayedFrameAspectRatio, 0.02f)
            assertEquals(16f / 9f, fixed.surfaceWidthPx / fixed.surfaceHeightPx, 0.02f)
            assertFalse("Mode $mode must never distort original pixels", fixed.isDistorted)
        }

        val stretchLayout = VideoAspectRatioCalculator.computeSurfaceLayout(vpW, vpH, 1920, 1080, mode = VideoAspectRatioMode.STRETCH)
        assertEquals(vpW, stretchLayout.surfaceWidthPx, 0.5f)
        assertEquals(vpH, stretchLayout.surfaceHeightPx, 0.5f)
        assertTrue(stretchLayout.isDistorted)
    }

    @Test
    fun test09_10_centralSwipeZoneClassificationAndConflictPrevention() {
        val vpW = 1080f
        val vpH = 608f

        assertEquals(VideoTouchZone.SYSTEM_EDGE_GUARD, VideoGestureClassifier.classifyTouchZone(540f, 30f, vpW, vpH))
        assertEquals(VideoTouchZone.SYSTEM_EDGE_GUARD, VideoGestureClassifier.classifyTouchZone(540f, 580f, vpW, vpH))
        assertEquals(VideoTouchZone.LEFT_BRIGHTNESS, VideoGestureClassifier.classifyTouchZone(140f, 300f, vpW, vpH))
        assertEquals(VideoTouchZone.CENTER_FULLSCREEN, VideoGestureClassifier.classifyTouchZone(540f, 300f, vpW, vpH))
        assertEquals(VideoTouchZone.RIGHT_VOLUME, VideoGestureClassifier.classifyTouchZone(940f, 300f, vpW, vpH))

        assertEquals(
            FullscreenSwipeDecision.ENTER_FULLSCREEN,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = 12f,
                totalDy = -180f,
                thresholdPx = 140f,
                isFullScreenLandscape = false,
                isSwipeEnabled = true
            )
        )

        assertEquals(
            FullscreenSwipeDecision.NONE,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = 4f,
                totalDy = -60f,
                thresholdPx = 140f,
                isFullScreenLandscape = false,
                isSwipeEnabled = true
            )
        )

        assertEquals(
            FullscreenSwipeDecision.NONE,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = 220f,
                totalDy = -160f,
                thresholdPx = 140f,
                isFullScreenLandscape = false,
                isSwipeEnabled = true
            )
        )

        assertEquals(
            FullscreenSwipeDecision.NONE,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = 0f,
                totalDy = -240f,
                thresholdPx = 140f,
                isFullScreenLandscape = false,
                isSwipeEnabled = true,
                isBlockedByControlsOrMenu = true
            )
        )

        assertEquals(
            FullscreenSwipeDecision.EXIT_FULLSCREEN,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = -10f,
                totalDy = 190f,
                thresholdPx = 140f,
                isFullScreenLandscape = true,
                isSwipeEnabled = true
            )
        )

        assertEquals(
            FullscreenSwipeDecision.NONE,
            VideoGestureClassifier.evaluateVerticalFullscreenSwipe(
                totalDx = 0f,
                totalDy = 240f,
                thresholdPx = 140f,
                isFullScreenLandscape = true,
                isSwipeEnabled = false
            )
        )
    }
}
