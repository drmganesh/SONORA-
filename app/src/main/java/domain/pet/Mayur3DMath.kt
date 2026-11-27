package domain.pet

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lightweight 3D vector & camera projection math for the procedural 3D MAYUR mascot.
 * Coordinate convention (right-handed, character-centric):
 *  - +X: character's right (viewer's right when facing camera at yaw = 0°)
 *  - +Y: upward
 *  - +Z: forward toward the camera at yaw = 0° (tail is at -Z)
 */
data class Vec3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(other: Vec3): Vec3 = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3): Vec3 = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float): Vec3 = Vec3(x * scalar, y * scalar, z * scalar)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun length(): Float = sqrt(x * x + y * y + z * z)

    fun normalized(): Vec3 {
        val len = length()
        return if (len > 1e-5f) Vec3(x / len, y / len, z / len) else Vec3(0f, 0f, 1f)
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
    }
}

data class ProjectedPoint(
    val sx: Float,
    val sy: Float,
    /** Depth in camera space: larger Z is closer to the viewer. */
    val camZ: Float,
    /** Perspective pixel scale factor at this point's depth. */
    val scale: Float
)

/**
 * 3D hit sphere projected to screen space so touch zones (Head, Headphones, Tail, Body)
 * remain accurate from every 360° angle (front, side, back, and tilted views).
 */
data class HitZoneProjection(
    val zone: MayurTouchZone,
    val screenX: Float,
    val screenY: Float,
    val radiusPx: Float,
    val camZ: Float
)

object Mayur3DMath {
    private const val DEG_TO_RAD = (PI / 180.0).toFloat()

    fun degToRad(deg: Float): Float = deg * DEG_TO_RAD

    /** Normalizes any angle in degrees into [-180f, +180f). */
    fun wrapAngleSigned(deg: Float): Float {
        var a = (deg + 180f) % 360f
        if (a < 0f) a += 360f
        return a - 180f
    }

    /** Normalizes any angle in degrees into [0f, 360f). */
    fun wrapAngle360(deg: Float): Float {
        var a = deg % 360f
        if (a < 0f) a += 360f
        return a
    }

    fun rotateX(v: Vec3, deg: Float): Vec3 {
        if (deg == 0f) return v
        val r = deg * DEG_TO_RAD
        val c = cos(r)
        val s = sin(r)
        return Vec3(v.x, v.y * c - v.z * s, v.y * s + v.z * c)
    }

    fun rotateY(v: Vec3, deg: Float): Vec3 {
        if (deg == 0f) return v
        val r = deg * DEG_TO_RAD
        val c = cos(r)
        val s = sin(r)
        return Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
    }

    fun rotateZ(v: Vec3, deg: Float): Vec3 {
        if (deg == 0f) return v
        val r = deg * DEG_TO_RAD
        val c = cos(r)
        val s = sin(r)
        return Vec3(v.x * c - v.y * s, v.x * s + v.y * c, v.z)
    }

    /** Applies local Euler rotation in Z -> X -> Y order (roll, pitch, yaw). */
    fun rotateEuler(v: Vec3, pitchDeg: Float, yawDeg: Float, rollDeg: Float): Vec3 {
        val rz = rotateZ(v, rollDeg)
        val rx = rotateX(rz, pitchDeg)
        return rotateY(rx, yawDeg)
    }

    /**
     * Transforms a world-space point into camera view space for the given orbit yaw & pitch.
     * Camera sits at (0, 0, +cameraDistance) looking toward the origin.
     */
    fun worldToView(world: Vec3, orbitYawDeg: Float, orbitPitchDeg: Float): Vec3 {
        val yawed = rotateY(world, orbitYawDeg)
        return rotateX(yawed, orbitPitchDeg)
    }

    /**
     * Projects a view-space point to 2D canvas coordinates with perspective foreshortening.
     */
    fun projectViewPoint(
        view: Vec3,
        canvasWidth: Float,
        canvasHeight: Float,
        cameraDistance: Float = 4.6f,
        focalMultiplier: Float = 1.62f
    ): ProjectedPoint {
        val unitPx = minOf(canvasWidth, canvasHeight) * 0.36f
        val depthFromCam = (cameraDistance - view.z).coerceAtLeast(0.85f)
        val persp = (cameraDistance * focalMultiplier) / depthFromCam
        val sx = canvasWidth * 0.50f + view.x * unitPx * persp * 0.62f
        val sy = canvasHeight * 0.56f - view.y * unitPx * persp * 0.62f
        return ProjectedPoint(
            sx = sx,
            sy = sy,
            camZ = view.z,
            scale = unitPx * persp * 0.62f
        )
    }

    /**
     * Convenience world-to-screen projection.
     */
    fun projectWorld(
        world: Vec3,
        orbitYawDeg: Float,
        orbitPitchDeg: Float,
        canvasWidth: Float,
        canvasHeight: Float
    ): ProjectedPoint {
        val view = worldToView(world, orbitYawDeg, orbitPitchDeg)
        return projectViewPoint(view, canvasWidth, canvasHeight)
    }

    /**
     * Computes depth-prioritized 3D hit-test against projected anatomical spheres.
     * Picks the closest-to-camera zone containing (touchX, touchY), falling back to BODY.
     */
    fun hitTestProjectedZones(
        touchX: Float,
        touchY: Float,
        zones: List<HitZoneProjection>
    ): MayurTouchZone {
        var bestZone: MayurTouchZone? = null
        var bestCamZ = Float.NEGATIVE_INFINITY

        for (i in zones.indices) {
            val h = zones[i]
            val dist = hypot(touchX - h.screenX, touchY - h.screenY)
            if (dist <= h.radiusPx && h.camZ > bestCamZ) {
                bestCamZ = h.camZ
                bestZone = h.zone
            }
        }
        return bestZone ?: MayurTouchZone.BODY
    }
}
