package domain.online

enum class AudioQualityPreference(
    val id: String,
    val title: String,
    val description: String
) {
    AUTO(
        id = "auto",
        title = "Auto",
        description = "Adapts between Wi-Fi and cellular data"
    ),
    HIGHEST(
        id = "highest",
        title = "Highest Available",
        description = "Selects the highest extracted audio bitrate"
    ),
    DATA_SAVER(
        id = "data_saver",
        title = "Data Saver",
        description = "Prefers compact audio streams to save data"
    );

    companion object {
        fun fromId(id: String?): AudioQualityPreference =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
    }
}

enum class StreamDeliveryType(val label: String) {
    PROGRESSIVE_HTTP("HTTP"),
    DASH("DASH"),
    HLS("HLS"),
    UNKNOWN("Stream")
}

data class OnlineAudioStreamOption(
    val id: String,
    val url: String,
    val mimeType: String,
    val codec: String,
    val formatName: String,
    val averageBitrateKbps: Int,
    val deliveryType: StreamDeliveryType,
    val isAudioOnly: Boolean,
    val isLossless: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val extractedAtMs: Long = System.currentTimeMillis()
) {
    val qualityLabel: String
        get() {
            val codecOrFmt = when {
                codec.isNotBlank() -> codec.uppercase()
                formatName.isNotBlank() -> formatName.uppercase()
                mimeType.isNotBlank() -> mimeType.substringAfter('/').uppercase()
                else -> "AUDIO"
            }
            return buildString {
                append(codecOrFmt)
                if (averageBitrateKbps > 0) {
                    append(" • ")
                    append(averageBitrateKbps)
                    append(" kbps")
                }
                if (deliveryType != StreamDeliveryType.PROGRESSIVE_HTTP && deliveryType != StreamDeliveryType.UNKNOWN) {
                    append(" • ")
                    append(deliveryType.label)
                }
            }
        }
}

data class ResolvedOnlineStreamResult(
    val serviceId: Int,
    val providerKey: String,
    val providerName: String,
    val contentUrl: String,
    val title: String,
    val artist: String,
    val artistUrl: String?,
    val durationMs: Long,
    val artworkUrl: String?,
    val selectedStream: OnlineAudioStreamOption,
    val availableStreams: List<OnlineAudioStreamOption>,
    val isLive: Boolean = false,
    val viewCount: Long = -1L,
    val relatedTracks: List<OnlineTrack> = emptyList(),
    val extractedAtMs: Long = System.currentTimeMillis()
)
