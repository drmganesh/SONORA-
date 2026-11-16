package data.online

import android.net.Uri
import com.example.data.OnlineTrackEntity
import com.example.model.Song
import domain.online.OnlineAudioStreamOption
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineTrack
import domain.online.ResolvedOnlineStreamResult
import domain.online.StreamDeliveryType
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import kotlin.math.abs

/**
 * Pure mapper translating NewPipeExtractor entities, Room [OnlineTrackEntity] rows,
 * [OnlineTrack], and SONORA's unified [Song] model without inventing missing metadata.
 */
object OnlineTrackMapper {

    fun computeStableId(providerKey: String, contentUrl: String): Long {
        val input = "$providerKey|$contentUrl"
        var h = 1125899906842597L
        for (ch in input) {
            h = h * 31L + ch.code.toLong()
        }
        val positiveMagnitude = (h and Long.MAX_VALUE).coerceAtLeast(1000L)
        return -positiveMagnitude
    }

    fun selectBestArtworkUrl(images: List<Image>?): String? {
        if (images.isNullOrEmpty()) return null
        return images
            .filter { it.url.isNotBlank() }
            .maxByOrNull { it.width * it.height }
            ?.url
    }

    fun mapInfoItem(
        item: InfoItem,
        serviceId: Int,
        providerKey: String,
        providerName: String
    ): OnlineTrack? {
        val url = item.url.orEmpty()
        if (url.isBlank()) return null
        val artwork = selectBestArtworkUrl(item.thumbnails)

        return when (item) {
            is StreamInfoItem -> {
                val durationMs = if (item.duration > 0L) item.duration * 1000L else 0L
                val isLive = item.streamType == StreamType.LIVE_STREAM || item.streamType == StreamType.AUDIO_LIVE_STREAM
                OnlineTrack(
                    stableId = computeStableId(providerKey, url),
                    serviceId = serviceId,
                    providerKey = providerKey,
                    providerName = providerName,
                    contentUrl = url,
                    title = item.name.orEmpty().ifBlank { "Untitled Track" },
                    artist = item.uploaderName.orEmpty().ifBlank { providerName },
                    artistUrl = item.uploaderUrl,
                    album = providerName,
                    durationMs = durationMs,
                    artworkUrl = artwork,
                    viewCount = item.viewCount,
                    isLive = isLive,
                    kind = OnlineItemKind.TRACK
                )
            }

            is ChannelInfoItem -> {
                OnlineTrack(
                    stableId = computeStableId(providerKey, url),
                    serviceId = serviceId,
                    providerKey = providerKey,
                    providerName = providerName,
                    contentUrl = url,
                    title = item.name.orEmpty().ifBlank { "Artist" },
                    artist = providerName,
                    artistUrl = url,
                    album = "Artist",
                    durationMs = 0L,
                    artworkUrl = artwork,
                    subscriberCount = item.subscriberCount,
                    trackCount = item.streamCount,
                    kind = OnlineItemKind.ARTIST
                )
            }

            is PlaylistInfoItem -> {
                val itemKind = if (item.playlistType?.name?.contains("ALBUM", ignoreCase = true) == true) {
                    OnlineItemKind.ALBUM
                } else {
                    OnlineItemKind.PLAYLIST
                }
                OnlineTrack(
                    stableId = computeStableId(providerKey, url),
                    serviceId = serviceId,
                    providerKey = providerKey,
                    providerName = providerName,
                    contentUrl = url,
                    title = item.name.orEmpty().ifBlank { "Collection" },
                    artist = item.uploaderName.orEmpty().ifBlank { providerName },
                    artistUrl = item.uploaderUrl,
                    album = providerName,
                    durationMs = 0L,
                    artworkUrl = artwork,
                    trackCount = item.streamCount,
                    kind = itemKind
                )
            }

            else -> null
        }
    }

    fun mapAudioStream(stream: AudioStream, index: Int): OnlineAudioStreamOption? {
        val url = stream.content.orEmpty()
        if (url.isBlank()) return null
        val formatName = stream.format?.name ?: ""
        val mimeType = stream.format?.mimeType ?: "audio/*"
        val codec = stream.codec.orEmpty().ifBlank {
            when {
                mimeType.contains("opus", ignoreCase = true) || formatName.contains("OPUS", ignoreCase = true) -> "OPUS"
                mimeType.contains("mp4", ignoreCase = true) || formatName.contains("M4A", ignoreCase = true) -> "AAC/M4A"
                mimeType.contains("mp3", ignoreCase = true) || formatName.contains("MP3", ignoreCase = true) -> "MP3"
                mimeType.contains("flac", ignoreCase = true) -> "FLAC"
                mimeType.contains("vorbis", ignoreCase = true) -> "VORBIS"
                else -> formatName
            }
        }
        val delivery = when (stream.deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> StreamDeliveryType.PROGRESSIVE_HTTP
            DeliveryMethod.DASH -> StreamDeliveryType.DASH
            DeliveryMethod.HLS -> StreamDeliveryType.HLS
            else -> StreamDeliveryType.UNKNOWN
        }
        val isLossless = mimeType.contains("flac", ignoreCase = true) ||
            codec.contains("flac", ignoreCase = true) ||
            codec.contains("alac", ignoreCase = true)
        return OnlineAudioStreamOption(
            id = stream.id.orEmpty().ifBlank { "audio_${index}_${stream.averageBitrate}_${formatName}" },
            url = url,
            mimeType = mimeType,
            codec = codec,
            formatName = formatName,
            averageBitrateKbps = stream.averageBitrate.coerceAtLeast(0),
            deliveryType = delivery,
            isAudioOnly = true,
            isLossless = isLossless
        )
    }

    fun mapFallbackVideoStream(stream: VideoStream, index: Int): OnlineAudioStreamOption? {
        val url = stream.content.orEmpty()
        if (url.isBlank()) return null
        val formatName = stream.format?.name ?: ""
        val mimeType = stream.format?.mimeType ?: "video/mp4"
        val delivery = when (stream.deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> StreamDeliveryType.PROGRESSIVE_HTTP
            DeliveryMethod.DASH -> StreamDeliveryType.DASH
            DeliveryMethod.HLS -> StreamDeliveryType.HLS
            else -> StreamDeliveryType.UNKNOWN
        }
        return OnlineAudioStreamOption(
            id = stream.id.orEmpty().ifBlank { "muxed_${index}_${stream.resolution}_${formatName}" },
            url = url,
            mimeType = mimeType,
            codec = "Muxed ${stream.resolution.orEmpty()}".trim(),
            formatName = formatName,
            averageBitrateKbps = 0,
            deliveryType = delivery,
            isAudioOnly = false,
            isLossless = false
        )
    }

    fun mapEntityToOnlineTrack(entity: OnlineTrackEntity): OnlineTrack {
        return OnlineTrack(
            stableId = entity.stableId,
            serviceId = entity.serviceId,
            providerKey = entity.providerKey,
            providerName = entity.providerName,
            contentUrl = entity.contentUrl,
            title = entity.title,
            artist = entity.artist,
            artistUrl = entity.artistUrl,
            album = entity.album,
            durationMs = entity.durationMs,
            artworkUrl = entity.artworkUrl,
            kind = OnlineItemKind.TRACK,
            isSaved = entity.isSaved,
            isFavorite = entity.isFavorite,
            playCount = entity.playCount,
            lastPlayedTimestamp = entity.lastPlayedTimestamp
        )
    }

    fun mapOnlineTrackToEntity(track: OnlineTrack): OnlineTrackEntity {
        return OnlineTrackEntity(
            stableId = track.stableId,
            serviceId = track.serviceId,
            providerKey = track.providerKey,
            providerName = track.providerName,
            contentUrl = track.contentUrl,
            title = track.title,
            artist = track.artist,
            artistUrl = track.artistUrl,
            album = track.album,
            durationMs = track.durationMs,
            artworkUrl = track.artworkUrl,
            isSaved = track.isSaved,
            isFavorite = track.isFavorite,
            playCount = track.playCount,
            lastPlayedTimestamp = track.lastPlayedTimestamp,
            savedTimestamp = if (track.isSaved) System.currentTimeMillis() else 0L
        )
    }

    fun toUnifiedSong(
        track: OnlineTrack,
        resolved: ResolvedOnlineStreamResult? = null
    ): Song {
        val opt = resolved?.selectedStream
        val bitrate = opt?.averageBitrateKbps ?: 0
        val effectiveDuration = if ((resolved?.durationMs ?: 0L) > 0L) {
            resolved!!.durationMs
        } else {
            track.durationMs
        }
        val effectiveArtwork = resolved?.artworkUrl ?: track.artworkUrl
        return Song(
            id = track.stableId,
            title = resolved?.title?.takeIf { it.isNotBlank() } ?: track.title,
            artist = resolved?.artist?.takeIf { it.isNotBlank() } ?: track.artist,
            album = track.album.ifBlank { track.providerName },
            albumId = abs(track.stableId),
            durationMs = effectiveDuration,
            uri = Uri.parse(track.contentUrl),
            dataPath = track.contentUrl,
            mimeType = opt?.mimeType ?: "",
            sizeBytes = 0L,
            bitrateKbps = bitrate,
            sampleRateHz = 0,
            bitDepth = 0,
            isFavorite = track.isFavorite,
            playCount = track.playCount,
            lastPlayedTimestamp = track.lastPlayedTimestamp,
            isDemoTrack = false,
            isOnline = true,
            sourceType = "ONLINE",
            serviceId = track.serviceId,
            providerKey = track.providerKey,
            providerName = track.providerName,
            contentUrl = track.contentUrl,
            artworkUrl = effectiveArtwork,
            uploaderUrl = resolved?.artistUrl ?: track.artistUrl,
            streamCodec = opt?.codec ?: "",
            streamQualityLabel = opt?.qualityLabel ?: "",
            isBitrateKnown = bitrate > 0,
            isSampleRateKnown = false
        )
    }

    fun toOnlineTrack(song: Song): OnlineTrack {
        val providerKey = song.providerKey.ifBlank { OnlineMusicProvider.KEY_YOUTUBE_MUSIC }
        val providerName = song.providerName.ifBlank { "Online" }
        val contentUrl = song.contentUrl.ifBlank { song.uri.toString() }
        return OnlineTrack(
            stableId = if (song.isOnline && song.id < 0) song.id else computeStableId(providerKey, contentUrl),
            serviceId = if (song.serviceId >= 0) song.serviceId else 0,
            providerKey = providerKey,
            providerName = providerName,
            contentUrl = contentUrl,
            title = song.title,
            artist = song.artist,
            artistUrl = song.uploaderUrl,
            album = song.album,
            durationMs = song.durationMs,
            artworkUrl = song.artworkUrl,
            kind = OnlineItemKind.TRACK,
            isFavorite = song.isFavorite,
            playCount = song.playCount,
            lastPlayedTimestamp = song.lastPlayedTimestamp
        )
    }

    fun streamInfoRelatedTracks(
        streamInfo: StreamInfo,
        serviceId: Int,
        providerKey: String,
        providerName: String
    ): List<OnlineTrack> {
        return runCatching {
            streamInfo.relatedItems
                .orEmpty()
                .mapNotNull { mapInfoItem(it, serviceId, providerKey, providerName) }
                .filter { it.kind == OnlineItemKind.TRACK }
        }.getOrElse { emptyList() }
    }
}
