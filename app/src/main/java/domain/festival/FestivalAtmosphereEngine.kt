package domain.festival

import data.preferences.FestivalAtmosphereSettings

/**
 * Resolved rendering blueprint consumed by [ui.components.FestivalAnimationRenderer].
 * Zero per-frame allocations: built only when festival state or settings change.
 */
data class FestivalRenderPlan(
    val isActive: Boolean,
    val effectId: FestivalEffectId,
    val descriptor: FestivalEffectDescriptor,
    val festivalTitle: String,
    val isPreviewOnly: Boolean,
    val particleLimit: Int,
    val opacityScale: Float,
    val speedMultiplier: Float,
    val minFrameIntervalNanos: Long,
    val isStaticReducedMotion: Boolean
) {
    companion object {
        val INACTIVE = FestivalRenderPlan(
            isActive = false,
            effectId = FestivalEffectId.NONE,
            descriptor = FestivalEffectRegistry.get(FestivalEffectId.NONE),
            festivalTitle = "",
            isPreviewOnly = false,
            particleLimit = 0,
            opacityScale = 0f,
            speedMultiplier = 0f,
            minFrameIntervalNanos = Long.MAX_VALUE,
            isStaticReducedMotion = true
        )
    }
}

/**
 * Pure deterministic engine translating active festival resolution + user settings + device limits
 * into a battery-safe [FestivalRenderPlan].
 */
object FestivalAtmosphereEngine {

    fun resolvePlan(
        settings: FestivalAtmosphereSettings,
        resolution: ActiveFestivalResolution,
        surfaceRole: FestivalSurfaceRole,
        isLowRamDevice: Boolean = false,
        globalReduceAnimations: Boolean = false,
        globalBudgetMultiplier: Float = 1f,
        previewOverrideEffect: FestivalEffectId? = null
    ): FestivalRenderPlan {
        // Preview mode applies ONLY inside SettingsPreview, never leaking onto Home or Player
        val isPreviewSurface = surfaceRole is FestivalSurfaceRole.SettingsPreview
        val resolvedEffectId: FestivalEffectId
        val title: String
        val isPreviewOnly: Boolean

        if (isPreviewSurface && previewOverrideEffect != null && previewOverrideEffect != FestivalEffectId.NONE) {
            resolvedEffectId = previewOverrideEffect
            title = FestivalEffectRegistry.get(previewOverrideEffect).displayName
            isPreviewOnly = true
        } else {
            if (!settings.enabled || !settings.automaticDetection) {
                return FestivalRenderPlan.INACTIVE
            }
            val primary = resolution.primary ?: return FestivalRenderPlan.INACTIVE
            if (primary.record.effectId == FestivalEffectId.NONE) {
                return FestivalRenderPlan.INACTIVE
            }
            resolvedEffectId = primary.record.effectId
            title = primary.displayNameForRegion
            isPreviewOnly = false
        }

        val descriptor = FestivalEffectRegistry.get(resolvedEffectId)
        val staticReduced = settings.reduceMotion || globalReduceAnimations

        val surfaceScale = when (surfaceRole) {
            FestivalSurfaceRole.HomeBackground -> 0.88f
            FestivalSurfaceRole.FullscreenPlayer -> 0.82f
            FestivalSurfaceRole.SettingsPreview -> 1.0f
        }

        val opacity = (descriptor.defaultOpacity * settings.animationIntensity.opacityMultiplier * surfaceScale)
            .coerceIn(0.18f, 0.78f)

        val particleLimit = settings.effectiveParticleLimit(
            descriptorBaseCap = descriptor.baseParticleCap,
            isLowRamDevice = isLowRamDevice,
            globalReduceAnimations = globalReduceAnimations,
            globalBudgetMultiplier = globalBudgetMultiplier
        )

        return FestivalRenderPlan(
            isActive = true,
            effectId = resolvedEffectId,
            descriptor = descriptor,
            festivalTitle = title,
            isPreviewOnly = isPreviewOnly,
            particleLimit = particleLimit,
            opacityScale = opacity,
            speedMultiplier = settings.effectiveSpeedMultiplier(globalReduceAnimations),
            minFrameIntervalNanos = settings.minFrameIntervalNanos(
                isLowRamDevice = isLowRamDevice,
                globalReduceAnimations = globalReduceAnimations
            ),
            isStaticReducedMotion = staticReduced
        )
    }
}
