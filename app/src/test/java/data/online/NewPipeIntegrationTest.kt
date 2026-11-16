package data.online

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import domain.online.AudioQualityPreference
import domain.online.OnlineAudioStreamOption
import domain.online.OnlineItemKind
import domain.online.OnlineMusicProvider
import domain.online.OnlineSearchFilter
import domain.online.OnlineTrack
import domain.online.StreamDeliveryType
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NewPipeIntegrationTest {

    @Test
    fun `NewPipeInitializer initializes once and discovers expected streaming services`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NewPipeInitializer.ensureInitialized(context)
        NewPipeInitializer.ensureInitialized(context)

        assertNotNull("NewPipe downloader must be configured", NewPipe.getDownloader())

        val providers = NewPipeInitializer.discoverProviders()
        assertTrue("Providers list must not be empty", providers.isNotEmpty())

        val keys = providers.map { it.key }.toSet()
        assertTrue("YT Music provider should be discovered", OnlineMusicProvider.KEY_YOUTUBE_MUSIC in keys)
        assertTrue("YouTube provider should be discovered", OnlineMusicProvider.KEY_YOUTUBE in keys)
        assertTrue("SoundCloud provider should be discovered", OnlineMusicProvider.KEY_SOUNDCLOUD in keys)
        assertTrue("Bandcamp provider should be discovered", OnlineMusicProvider.KEY_BANDCAMP in keys)
    }

    @Test
    fun `content filters map YT Music and SoundCloud filters accurately`() {
        val ytFilters = NewPipeInitializer.resolveNewPipeContentFilters(
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            filter = OnlineSearchFilter.SONGS,
            service = ServiceList.YouTube
        )
        assertEquals(listOf("music_songs"), ytFilters)

        val artistFilters = NewPipeInitializer.resolveNewPipeContentFilters(
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            filter = OnlineSearchFilter.ARTISTS,
            service = ServiceList.YouTube
        )
        assertEquals(listOf("channels"), artistFilters)

        val scFilters = NewPipeInitializer.resolveNewPipeContentFilters(
            providerKey = OnlineMusicProvider.KEY_SOUNDCLOUD,
            filter = OnlineSearchFilter.SONGS,
            service = ServiceList.SoundCloud
        )
        assertEquals(listOf("tracks"), scFilters)
    }

    @Test
    fun `OnlineTrackMapper generates deterministic negative IDs and never fabricates lossless metadata`() {
        val url = "https://music.youtube.com/watch?v=dQw4w9WgXcQ"
        val id1 = OnlineTrackMapper.computeStableId(OnlineMusicProvider.KEY_YOUTUBE_MUSIC, url)
        val id2 = OnlineTrackMapper.computeStableId(OnlineMusicProvider.KEY_YOUTUBE_MUSIC, url)
        val idOther = OnlineTrackMapper.computeStableId(OnlineMusicProvider.KEY_SOUNDCLOUD, url)

        assertTrue("Online stableId must be negative to never collide with MediaStore positive IDs", id1 < 0L)
        assertEquals("Stable ID must be deterministic", id1, id2)
        assertNotEquals("Different provider keys must map to distinct stable IDs", id1, idOther)

        val images = listOf(
            Image("https://img.example.com/small.jpg", 120, 90, Image.ResolutionLevel.LOW),
            Image("https://img.example.com/hd.jpg", 1280, 720, Image.ResolutionLevel.HIGH),
            Image("   ", 2000, 2000, Image.ResolutionLevel.HIGH)
        )
        assertEquals("https://img.example.com/hd.jpg", OnlineTrackMapper.selectBestArtworkUrl(images))

        val track = OnlineTrack(
            stableId = id1,
            serviceId = 0,
            providerKey = OnlineMusicProvider.KEY_YOUTUBE_MUSIC,
            providerName = "YT Music",
            contentUrl = url,
            title = "Sample Online Song",
            artist = "Sample Artist",
            durationMs = 215_000L,
            artworkUrl = "https://img.example.com/hd.jpg",
            kind = OnlineItemKind.TRACK
        )
        val song = OnlineTrackMapper.toUnifiedSong(track)
        assertTrue(song.isOnline)
        assertFalse("Unresolved online song must not pretend bitrate is known", song.isBitrateKnown)
        assertFalse("Unresolved online song must not pretend sample rate is known", song.isSampleRateKnown)
        assertEquals("YT Music • Stream", song.audioFormatInfo)
        assertEquals(url, song.contentUrl)
    }

    @Test
    fun `OnlineStreamResolver selects appropriate stream for AUTO, HIGHEST, DATA_SAVER, and manual selection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val resolver = OnlineStreamResolver(NetworkConnectivityMonitor(context))

        val lowOpus = OnlineAudioStreamOption(
            id = "opus_48",
            url = "https://cdn.example.com/48.webm",
            mimeType = "audio/webm",
            codec = "opus",
            formatName = "WEBMA_OPUS",
            averageBitrateKbps = 48,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
            isAudioOnly = true
        )
        val midAac = OnlineAudioStreamOption(
            id = "aac_128",
            url = "https://cdn.example.com/128.m4a",
            mimeType = "audio/mp4",
            codec = "aac",
            formatName = "M4A",
            averageBitrateKbps = 128,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
            isAudioOnly = true
        )
        val highOpus = OnlineAudioStreamOption(
            id = "opus_256",
            url = "https://cdn.example.com/256.webm",
            mimeType = "audio/webm",
            codec = "opus",
            formatName = "WEBMA_OPUS",
            averageBitrateKbps = 256,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
            isAudioOnly = true
        )
        val muxedVideo = OnlineAudioStreamOption(
            id = "muxed_720",
            url = "https://cdn.example.com/720.mp4",
            mimeType = "video/mp4",
            codec = "avc1+mp4a",
            formatName = "MPEG_4",
            averageBitrateKbps = 1500,
            deliveryType = StreamDeliveryType.PROGRESSIVE_HTTP,
            isAudioOnly = false
        )

        val allOptions = listOf(lowOpus, midAac, highOpus, muxedVideo)

        val highest = resolver.selectBestAudioStream(allOptions, AudioQualityPreference.HIGHEST, isMeteredConnection = false)
        assertEquals("Highest should prefer best audio-only stream over muxed video", "opus_256", highest?.id)

        val saver = resolver.selectBestAudioStream(allOptions, AudioQualityPreference.DATA_SAVER, isMeteredConnection = false)
        assertEquals("Data Saver should pick compact 48 kbps stream", "opus_48", saver?.id)

        val autoWifi = resolver.selectBestAudioStream(allOptions, AudioQualityPreference.AUTO, isMeteredConnection = false)
        assertEquals("Auto on Wi-Fi should prefer 256 kbps stream", "opus_256", autoWifi?.id)

        val autoMetered = resolver.selectBestAudioStream(allOptions, AudioQualityPreference.AUTO, isMeteredConnection = true)
        assertEquals("Auto on metered network should target ~128 kbps", "aac_128", autoMetered?.id)

        val manual = resolver.selectBestAudioStream(allOptions, AudioQualityPreference.DATA_SAVER, isMeteredConnection = true, manualStreamId = "opus_256")
        assertEquals("Manual override must win regardless of quality mode", "opus_256", manual?.id)
    }

    @Test
    fun `NewPipeDownloader forwards headers, injects default User-Agent, and throws ReCaptchaException on HTTP 429`() {
        var capturedUserAgent: String? = null
        var capturedCustomHeader: String? = null
        var nextStatusCode = 200

        val interceptor = Interceptor { chain ->
            val req = chain.request()
            capturedUserAgent = req.header("User-Agent")
            capturedCustomHeader = req.header("X-Test-Header")
            okhttp3.Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(nextStatusCode)
                .message(if (nextStatusCode == 200) "OK" else "Too Many Requests")
                .body("{\"ok\":true}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()
        val downloader = NewPipeDownloader(client)

        val request200 = Request.Builder()
            .get("https://example.org/api")
            .headers(mapOf("X-Test-Header" to listOf("sonora-check")))
            .build()
        val response = downloader.execute(request200)
        assertEquals(200, response.responseCode())
        assertEquals("{\"ok\":true}", response.responseBody())
        assertEquals(NewPipeDownloader.DEFAULT_USER_AGENT, capturedUserAgent)
        assertEquals("sonora-check", capturedCustomHeader)

        nextStatusCode = 429
        var threwReCaptcha = false
        try {
            downloader.execute(request200)
        } catch (_: ReCaptchaException) {
            threwReCaptcha = true
        }
        assertTrue("HTTP 429 must raise ReCaptchaException for accurate rate-limit reporting", threwReCaptcha)
    }
}
