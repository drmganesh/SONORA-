package domain.festival

/**
 * Metadata describing a reusable festival visual effect registered in [FestivalEffectRegistry].
 * New festivals can register bespoke visual descriptors without altering the core engine.
 */
data class FestivalEffectDescriptor(
    val effectId: FestivalEffectId,
    val displayName: String,
    val description: String,
    val iconGlyph: String,
    val primaryColorArgb: Long,
    val secondaryColorArgb: Long,
    val accentColorArgb: Long,
    val baseParticleCap: Int,
    val supportsWeatherCoexistence: Boolean = true,
    val defaultOpacity: Float = 0.62f
)

/**
 * Extensible registry mapping [FestivalEffectId] values to visual metadata.
 * Supports dynamic registration for future festivals without modifying the core renderer or engine.
 */
object FestivalEffectRegistry {

    private val registry: MutableMap<FestivalEffectId, FestivalEffectDescriptor> = linkedMapOf()

    init {
        registerDefaults()
    }

    private fun registerDefaults() {
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.DIWALI_FIREWORKS,
                displayName = "Diwali Fireworks & Diyas",
                description = "Gentle upward fireworks bursts in the background with glowing edge diyas and subtle golden sparkles",
                iconGlyph = "🪔",
                primaryColorArgb = 0xFFFFC107L,
                secondaryColorArgb = 0xFFFF6D00L,
                accentColorArgb = 0xFFFFE082L,
                baseParticleCap = 24,
                defaultOpacity = 0.66f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.KARTIK_BOITA_BANDANA,
                displayName = "Kartik Purnima Boita & Diyas",
                description = "Peaceful traditional Odisha boita boats sailing gently across water reflections with illuminated floating diyas",
                iconGlyph = "⛵",
                primaryColorArgb = 0xFFFFB74DL,
                secondaryColorArgb = 0xFF4FC3F7L,
                accentColorArgb = 0xFFFFF176L,
                baseParticleCap = 18,
                defaultOpacity = 0.64f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.DUSSEHRA_RAMA_ARROW,
                displayName = "Dussehra Sacred Arrow & Flame",
                description = "Stylized, respectful background silhouette of Lord Rama's glowing golden arrow igniting a ceremonial flame",
                iconGlyph = "🏹",
                primaryColorArgb = 0xFFFF9800L,
                secondaryColorArgb = 0xFFFF5722L,
                accentColorArgb = 0xFFFFD54FL,
                baseParticleCap = 20,
                defaultOpacity = 0.62f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.HOLI_GULAL,
                displayName = "Holi Gulal Color Clouds",
                description = "Soft pastel clouds of pink, marigold, turquoise, and saffron gulal powder drifting gently",
                iconGlyph = "🎨",
                primaryColorArgb = 0xFFEC407AL,
                secondaryColorArgb = 0xFFFFB300L,
                accentColorArgb = 0xFF26C6DAL,
                baseParticleCap = 24,
                defaultOpacity = 0.58f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.GANESH_DIYA_FLOWERS,
                displayName = "Ganesh Chaturthi Flowers & Diyas",
                description = "Respectful warm temple diyas, drifting marigold petals, and soft golden festive aura",
                iconGlyph = "🌺",
                primaryColorArgb = 0xFFFF9100L,
                secondaryColorArgb = 0xFFFF5252L,
                accentColorArgb = 0xFFFFD740L,
                baseParticleCap = 20,
                defaultOpacity = 0.60f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.DURGA_NAVRATRI_PETALS,
                displayName = "Durga Puja & Navratri Petals",
                description = "Floating crimson and marigold petals, subtle dhunuchi incense wisps, and rhythmic warm light pulses",
                iconGlyph = "🪷",
                primaryColorArgb = 0xFFE53935L,
                secondaryColorArgb = 0xFFFFB300L,
                accentColorArgb = 0xFFFFCC80L,
                baseParticleCap = 22,
                defaultOpacity = 0.60f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.RATH_YATRA_CHARIOT,
                displayName = "Rath Yatra Sacred Chariot",
                description = "Stylized traditional temple chariot moving slowly across the background with ceremonial flags and flower petals",
                iconGlyph = "🛕",
                primaryColorArgb = 0xFFD84315L,
                secondaryColorArgb = 0xFFFFCA28L,
                accentColorArgb = 0xFFEF5350L,
                baseParticleCap = 20,
                defaultOpacity = 0.62f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.JANMASHTAMI_FLUTE_PEACOCK,
                displayName = "Janmashtami Flute & Peacock Feathers",
                description = "Subtle golden flute accents, floating peacock feather motifs, and tranquil blue-and-gold illumination",
                iconGlyph = "🦚",
                primaryColorArgb = 0xFF1E88E5L,
                secondaryColorArgb = 0xFF26A69AL,
                accentColorArgb = 0xFFFFD54FL,
                baseParticleCap = 20,
                defaultOpacity = 0.60f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.SANKRANTI_KITES,
                displayName = "Makar Sankranti Drifting Kites",
                description = "Colorful traditional kites with delicate fluttering tails gliding slowly across the sky",
                iconGlyph = "🪁",
                primaryColorArgb = 0xFFFF7043L,
                secondaryColorArgb = 0xFF29B6F6L,
                accentColorArgb = 0xFFFFEE58L,
                baseParticleCap = 18,
                defaultOpacity = 0.58f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.PONGAL_KOLAM_STEAM,
                displayName = "Pongal Kolam & Harvest Glow",
                description = "Delicate traditional kolam dot-ring motifs, sugarcane accents, and warm rising harvest steam",
                iconGlyph = "🌾",
                primaryColorArgb = 0xFFFFA726L,
                secondaryColorArgb = 0xFF66BB6AL,
                accentColorArgb = 0xFFFFF8E1L,
                baseParticleCap = 18,
                defaultOpacity = 0.56f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.ONAM_POOKALAM_BOAT,
                displayName = "Onam Pookalam & Petals",
                description = "Concentric pookalam floral pattern, floating petals, and subtle Kerala boat-race horizon motif",
                iconGlyph = "🌼",
                primaryColorArgb = 0xFFFFCA28L,
                secondaryColorArgb = 0xFFAB47BCL,
                accentColorArgb = 0xFF66BB6AL,
                baseParticleCap = 20,
                defaultOpacity = 0.58f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.BIHU_FESTIVE_PATTERN,
                displayName = "Bihu Festive Patterns & Blossoms",
                description = "Assamese gamusa-inspired geometric diamond accents, floating orchid petals, and gentle rhythmic light",
                iconGlyph = "🌺",
                primaryColorArgb = 0xFFD32F2FL,
                secondaryColorArgb = 0xFFFFF8E1L,
                accentColorArgb = 0xFFFFB74DL,
                baseParticleCap = 18,
                defaultOpacity = 0.56f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.SHIVARATRI_SACRED_NIGHT,
                displayName = "Maha Shivaratri Sacred Night",
                description = "Serene silver-indigo moonlight crescent, sacred ash sparkles, and gentle temple lamp glow",
                iconGlyph = "🌙",
                primaryColorArgb = 0xFF7E57C2L,
                secondaryColorArgb = 0xFF90CAF9L,
                accentColorArgb = 0xFFE1F5FEL,
                baseParticleCap = 18,
                defaultOpacity = 0.58f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.SPRING_BLOOM_PETALS,
                displayName = "Spring Blossoms & New Year Petals",
                description = "Soft golden-marigold and mango-blossom petals drifting in warm seasonal light",
                iconGlyph = "🌼",
                primaryColorArgb = 0xFFFFD54FL,
                secondaryColorArgb = 0xFFFF8A65L,
                accentColorArgb = 0xFF81C784L,
                baseParticleCap = 20,
                defaultOpacity = 0.56f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.RIVER_DIYA_GANGA,
                displayName = "Sacred River Diyas",
                description = "Glowing earthen diyas drifting across calm shimmering river ripples at dusk",
                iconGlyph = "🪔",
                primaryColorArgb = 0xFFFFB74DL,
                secondaryColorArgb = 0xFF4DD0E1L,
                accentColorArgb = 0xFFFFF59DL,
                baseParticleCap = 18,
                defaultOpacity = 0.60f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.GOLDEN_AARTI_LAMPS,
                displayName = "Sacred Aarti Lamps",
                description = "Warm shimmering golden lamp light and slow rising festive embers",
                iconGlyph = "✨",
                primaryColorArgb = 0xFFFFCA28L,
                secondaryColorArgb = 0xFFFF8F00L,
                accentColorArgb = 0xFFFFF9C4L,
                baseParticleCap = 18,
                defaultOpacity = 0.56f
            )
        )
        register(
            FestivalEffectDescriptor(
                effectId = FestivalEffectId.NONE,
                displayName = "None",
                description = "No festival effect active",
                iconGlyph = "",
                primaryColorArgb = 0x00000000L,
                secondaryColorArgb = 0x00000000L,
                accentColorArgb = 0x00000000L,
                baseParticleCap = 0,
                defaultOpacity = 0f
            )
        )
    }

    fun register(descriptor: FestivalEffectDescriptor) {
        registry[descriptor.effectId] = descriptor
    }

    fun get(effectId: FestivalEffectId): FestivalEffectDescriptor =
        registry[effectId] ?: registry.getValue(FestivalEffectId.GOLDEN_AARTI_LAMPS)

    fun allPreviewable(): List<FestivalEffectDescriptor> =
        registry.values.filter { it.effectId != FestivalEffectId.NONE }
}
