package com.example

import com.example.util.LyricsExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testLrcParserAndActiveLineIndex() {
    val sampleLrc = """
      [ti:Sonora Test]
      [ar:Artist]
      [00:00.00]Intro music starts
      [00:05.50]First line of the song
      [00:12.80]Second line of the chorus
      [00:20.00]Outro melody
    """.trimIndent()

    val parsed = LyricsExtractor.parseLrcLines(sampleLrc)
    assertEquals(4, parsed.size)
    assertEquals("Intro music starts", parsed[0].text)
    assertEquals(0L, parsed[0].timestampMs)
    assertEquals("First line of the song", parsed[1].text)
    assertEquals(5500L, parsed[1].timestampMs)
    assertEquals("Second line of the chorus", parsed[2].text)
    assertEquals(12800L, parsed[2].timestampMs)

    // Test active line detection at different playback timestamps
    assertEquals(0, LyricsExtractor.findActiveLineIndex(parsed, 1000L))
    assertEquals(1, LyricsExtractor.findActiveLineIndex(parsed, 7000L))
    assertEquals(2, LyricsExtractor.findActiveLineIndex(parsed, 15000L))
    assertEquals(3, LyricsExtractor.findActiveLineIndex(parsed, 25000L))
  }

  @Test
  fun testPlayerStyleSystemDefinitions() {
    val styles = com.example.model.PlayerStyle.entries
    assertEquals(19, styles.size)

    val expectedIds = listOf(
      "classic", "minimal", "material_you", "glass",
      "editorial", "canvas", "bento", "poster", "polaroid", "vinyl",
      "dial", "morph", "sticker", "waveform", "spectrum", "orbit", "hero", "neon",
      "gesture"
    )
    val actualIds = styles.map { it.id }
    assertEquals(expectedIds, actualIds)

    // Verify lookup by id
    assertEquals(com.example.model.PlayerStyle.BENTO, com.example.model.PlayerStyle.fromId("bento"))
    assertEquals(com.example.model.PlayerStyle.DIAL, com.example.model.PlayerStyle.fromId("DIAL"))
    assertEquals(com.example.model.PlayerStyle.HERO, com.example.model.PlayerStyle.fromId("HeRo"))
    assertEquals(com.example.model.PlayerStyle.MATERIAL_YOU, com.example.model.PlayerStyle.fromId("material_you"))
    assertEquals(com.example.model.PlayerStyle.POLAROID, com.example.model.PlayerStyle.fromId("polaroid"))
    assertEquals(com.example.model.PlayerStyle.ORBIT, com.example.model.PlayerStyle.fromId("orbit"))
    assertEquals(com.example.model.PlayerStyle.CLASSIC, com.example.model.PlayerStyle.fromId("non_existent"))
    assertEquals(com.example.model.PlayerStyle.CLASSIC, com.example.model.PlayerStyle.fromId(null))

    // Verify category filters cover all 19 styles cleanly
    assertEquals(19, styles.count { it.matchesCategory(com.example.model.PlayerStyleCategory.ALL) })
    assertTrue(com.example.model.PlayerStyle.MINIMAL.matchesCategory(com.example.model.PlayerStyleCategory.MINIMAL))
    assertTrue(com.example.model.PlayerStyle.VINYL.matchesCategory(com.example.model.PlayerStyleCategory.ARTISTIC))
    assertTrue(com.example.model.PlayerStyle.GLASS.matchesCategory(com.example.model.PlayerStyleCategory.IMMERSIVE))
    assertTrue(com.example.model.PlayerStyle.WAVEFORM.matchesCategory(com.example.model.PlayerStyleCategory.EXPERIMENTAL))
  }

  @Test
  fun testSeekbarStyleDefinitions() {
    val styles = ui.player.seekbar.SeekbarStyle.entries
    assertEquals(10, styles.size)

    val expectedIds = listOf(
      "blocks", "waveform", "wave_line", "classic", "dots",
      "gradient", "neon_glow", "material3", "m3_expressive", "circular_progress"
    )
    val actualIds = styles.map { it.id }
    assertEquals(expectedIds, actualIds)

    // Verify Default requirement is BLOCKS
    assertEquals(ui.player.seekbar.SeekbarStyle.BLOCKS, ui.player.seekbar.SeekbarStyle.DEFAULT)

    // Verify lookup by id with fallback to BLOCKS
    assertEquals(ui.player.seekbar.SeekbarStyle.WAVEFORM, ui.player.seekbar.SeekbarStyle.fromId("waveform"))
    assertEquals(ui.player.seekbar.SeekbarStyle.NEON_GLOW, ui.player.seekbar.SeekbarStyle.fromId("NEON_GLOW"))
    assertEquals(ui.player.seekbar.SeekbarStyle.M3_EXPRESSIVE, ui.player.seekbar.SeekbarStyle.fromId("m3_expressive"))
    assertEquals(ui.player.seekbar.SeekbarStyle.CIRCULAR_PROGRESS, ui.player.seekbar.SeekbarStyle.fromId("circular_progress"))
    assertEquals(ui.player.seekbar.SeekbarStyle.BLOCKS, ui.player.seekbar.SeekbarStyle.fromId("unknown_style"))
    assertEquals(ui.player.seekbar.SeekbarStyle.BLOCKS, ui.player.seekbar.SeekbarStyle.fromId(null))
  }

  @Test
  fun testPlayerCustomizationAndGestureDefaults() {
    assertEquals(4, com.example.model.MiniPlayerStyle.entries.size)
    assertEquals(3, com.example.model.LockScreenStyle.entries.size)
    assertEquals(
      com.example.model.MiniPlayerStyle.WAVE_MINI,
      com.example.model.MiniPlayerStyle.fromId("wave_mini")
    )
    assertEquals(
      com.example.model.LockScreenStyle.MINIMAL_PRIVATE,
      com.example.model.LockScreenStyle.fromId("minimal_private")
    )
    assertEquals(
      com.example.model.ArtworkSizeOption.LARGE,
      com.example.model.ArtworkSizeOption.fromId("large")
    )
    assertEquals(
      com.example.model.CornerRadiusOption.PILL,
      com.example.model.CornerRadiusOption.fromId("pill")
    )
    assertEquals(
      com.example.model.PlayerBackgroundStyle.BLURRED_ARTWORK,
      com.example.model.PlayerBackgroundStyle.fromId("blurred_artwork")
    )
    assertEquals(
      com.example.model.ControlArrangement.SPLIT_WIDE,
      com.example.model.ControlArrangement.fromId("split_wide")
    )
    assertEquals(
      com.example.model.ButtonShapeOption.SQUIRCLE,
      com.example.model.ButtonShapeOption.fromId("squircle")
    )
    assertEquals(
      com.example.model.SwipeAction.SEEK_15S,
      com.example.model.SwipeAction.fromId("seek_15s")
    )
    assertEquals(
      com.example.model.DoubleTapAction.TOGGLE_FAVORITE,
      com.example.model.DoubleTapAction.fromId("toggle_favorite")
    )
    assertEquals(
      com.example.model.SingleTapAction.TOGGLE_CONTROLS_VISIBILITY,
      com.example.model.SingleTapAction.fromId("toggle_controls")
    )

    val defaultAppearance = com.example.model.PlayerAppearanceConfig.DEFAULT
    assertTrue(defaultAppearance.useDynamicAlbumAccent)
    assertEquals(1.0f, defaultAppearance.typographyScale, 0.001f)
    assertEquals(1.0f, defaultAppearance.seekbarThicknessScale, 0.001f)

    val defaultGesture = com.example.model.PlayerGestureConfig.DEFAULT
    assertTrue(defaultGesture.gesturesEnabled)
    assertTrue(defaultGesture.pinchZoomArtworkEnabled)
    assertTrue(defaultGesture.rightEdgeVolumeEnabled)
    assertTrue(defaultGesture.leftEdgeBrightnessEnabled)
    assertEquals(64, defaultGesture.swipeThresholdDp)
  }

  @Test
  fun testDynamicThemeModeDefinitions() {
    val modes = com.example.model.DynamicThemeMode.entries
    assertEquals(5, modes.size)

    val expectedIds = listOf(
      "full_dynamic", "amoled_dynamic", "subtle_dynamic", "background_only", "static_theme"
    )
    val actualIds = modes.map { it.id }
    assertEquals(expectedIds, actualIds)

    // Verify Default requirement is FULL_DYNAMIC
    assertEquals(com.example.model.DynamicThemeMode.FULL_DYNAMIC, com.example.model.DynamicThemeMode.DEFAULT)

    // Verify lookup by id with fallback to FULL_DYNAMIC
    assertEquals(com.example.model.DynamicThemeMode.FULL_DYNAMIC, com.example.model.DynamicThemeMode.fromId("full_dynamic"))
    assertEquals(com.example.model.DynamicThemeMode.AMOLED_DYNAMIC, com.example.model.DynamicThemeMode.fromId("AMOLED_DYNAMIC"))
    assertEquals(com.example.model.DynamicThemeMode.SUBTLE_DYNAMIC, com.example.model.DynamicThemeMode.fromId("subtle_dynamic"))
    assertEquals(com.example.model.DynamicThemeMode.BACKGROUND_ONLY, com.example.model.DynamicThemeMode.fromId("background_only"))
    assertEquals(com.example.model.DynamicThemeMode.STATIC_THEME, com.example.model.DynamicThemeMode.fromId("static_theme"))
    assertEquals(com.example.model.DynamicThemeMode.FULL_DYNAMIC, com.example.model.DynamicThemeMode.fromId("unknown_mode"))
    assertEquals(com.example.model.DynamicThemeMode.FULL_DYNAMIC, com.example.model.DynamicThemeMode.fromId(null))

    // Verify default dynamic color palette properties
    val defaultPalette = ui.theme.DynamicColorPalette.default()
    assertEquals(androidx.compose.ui.graphics.Color(0xFF000000), defaultPalette.background)
    assertEquals(com.example.ui.theme.SonoraDustyRose, defaultPalette.primary)
  }

  @Test
  fun testMusicFolderNormalizationAndContainment() {
    val normalized = domain.filter.MusicFolderFilter.normalizePath("/storage/emulated/0//Music///")
    assertEquals("/storage/emulated/0/Music", normalized)

    val windowsPath = domain.filter.MusicFolderFilter.normalizePath("\\storage\\emulated\\0\\Music\\Rock\\")
    assertEquals("/storage/emulated/0/Music/Rock", windowsPath)

    // Direct match
    assertTrue(
      domain.filter.MusicFolderFilter.isPathInsideFolder(
        "/storage/emulated/0/Music",
        "/storage/emulated/0/Music"
      )
    )

    // Child in subfolder
    assertTrue(
      domain.filter.MusicFolderFilter.isPathInsideFolder(
        "/storage/emulated/0/Music/Pink Floyd/Money.flac",
        "/storage/emulated/0/Music"
      )
    )

    // Deeply nested child
    assertTrue(
      domain.filter.MusicFolderFilter.isPathInsideFolder(
        "/storage/emulated/0/Music/FLAC/24bit/HiRes/Track1.flac",
        "/storage/emulated/0/Music"
      )
    )

    // Sibling folder that starts with same prefix should NOT match (e.g. Music2 vs Music)
    org.junit.Assert.assertFalse(
      domain.filter.MusicFolderFilter.isPathInsideFolder(
        "/storage/emulated/0/Music2/Track.mp3",
        "/storage/emulated/0/Music"
      )
    )

    // Unrelated path
    org.junit.Assert.assertFalse(
      domain.filter.MusicFolderFilter.isPathInsideFolder(
        "/storage/emulated/0/Download/VoiceNotes/note.m4a",
        "/storage/emulated/0/Music"
      )
    )
  }

  @Test
  fun testFolderFilterSettingsPrecedence() {
    val musicFolder = domain.model.FolderInfo(
      path = "/storage/emulated/0/Music",
      name = "Music"
    )
    val whatsappAudio = domain.model.FolderInfo(
      path = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp Audio",
      name = "WhatsApp Audio"
    )
    val podcastsInMusic = domain.model.FolderInfo(
      path = "/storage/emulated/0/Music/Podcasts",
      name = "Podcasts"
    )

    // Blacklist check
    val settings = domain.model.FolderFilterSettings(
      whitelistEnabled = true,
      whitelistedFolders = listOf(musicFolder),
      blacklistedFolders = listOf(whatsappAudio, podcastsInMusic)
    )

    assertTrue(settings.isBlacklisted("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp Audio"))
    assertTrue(settings.isBlacklisted("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp Audio/Sent"))
    assertTrue(settings.isBlacklisted("/storage/emulated/0/Music/Podcasts/Episode1.mp3"))
    org.junit.Assert.assertFalse(settings.isBlacklisted("/storage/emulated/0/Music/Rock/Queen.flac"))
  }

  @Test
  fun testAdaptiveEqualizerDynamicSoundMapper() {
    // 1. Neutral (0, 0) must produce exactly 0 dB across all 15 bands
    val neutralGains = com.example.equalizer.DynamicSoundMapper.calculateGains(0f, 0f)
    assertEquals(15, neutralGains.size)
    for (i in 0 until 15) {
      assertEquals(0f, neutralGains[i], 0.001f)
    }

    // 2. Warm Tilt (-0.8f, 0f): Bass bands must be boosted, treble bands must be attenuated
    val warmGains = com.example.equalizer.DynamicSoundMapper.calculateGains(-0.8f, 0f)
    assertTrue("63 Hz should be boosted for warm tilt", warmGains[2] > 2.0f)
    assertTrue("100 Hz should be boosted for warm tilt", warmGains[3] > 2.0f)
    assertTrue("10 kHz should be attenuated for warm tilt", warmGains[13] < -1.0f)

    // 3. Bright Tilt (+0.8f, 0f): Treble bands must be boosted
    val brightGains = com.example.equalizer.DynamicSoundMapper.calculateGains(0.8f, 0f)
    assertTrue("10 kHz should be boosted for bright tilt", brightGains[13] > 2.0f)
    assertTrue("16 kHz should be boosted for bright tilt", brightGains[14] > 2.0f)

    // 4. Focused Contour (0f, +0.8f): Midrange presence (1kHz - 2.5kHz) must be boosted
    val focusedGains = com.example.equalizer.DynamicSoundMapper.calculateGains(0f, 0.8f)
    assertTrue("1.6 kHz presence must be boosted", focusedGains[9] > 2.0f)
    assertTrue("2.5 kHz presence must be boosted", focusedGains[10] > 1.5f)

    // 5. Broad Contour (0f, -0.8f): Bass and air bands boosted
    val broadGains = com.example.equalizer.DynamicSoundMapper.calculateGains(0f, -0.8f)
    assertTrue("63 Hz should be boosted for broad contour", broadGains[2] > 1.5f)
    assertTrue("10 kHz should be boosted for broad contour", broadGains[13] > 1.5f)
  }

  @Test
  fun testBiquadFilterMagnitudeResponse() {
    val filter1k = com.example.equalizer.BiquadFilter(centerFrequency = 1000f, gainDb = 6f, qFactor = 1.0f)
    // At center frequency (1 kHz), magnitude response should equal target gain (+6 dB)
    val peakDb = filter1k.magnitudeResponseDbAt(1000f)
    assertEquals(6.0f, peakDb, 0.15f)

    // At far-away frequencies, response should roll off towards 0 dB
    val farBassDb = filter1k.magnitudeResponseDbAt(50f)
    assertTrue("50 Hz should have near 0 dB gain on a 1 kHz peaking filter", kotlin.math.abs(farBassDb) < 0.2f)

    // Flat filter produces 0 dB everywhere
    val flatFilter = com.example.equalizer.BiquadFilter(centerFrequency = 1000f, gainDb = 0f)
    assertEquals(0f, flatFilter.magnitudeResponseDbAt(1000f), 0.001f)
    assertEquals(0f, flatFilter.magnitudeResponseDbAt(100f), 0.001f)
  }

  @Test
  fun testEqualizerSoundProfilesAndMacro() {
    val profiles = com.example.equalizer.EqualizerPresets.PROFILES
    assertEquals(8, profiles.size)

    val expectedIds = listOf("balanced", "warm", "forward", "bright", "broad", "focused", "deep", "natural")
    assertEquals(expectedIds, profiles.map { it.id })

    // Verify lookup
    val warm = com.example.equalizer.EqualizerPresets.getById("warm")
    assertEquals("Warm", warm.name)
    assertTrue(warm.tilt < 0f)

    val forward = com.example.equalizer.EqualizerPresets.getById("forward")
    assertEquals("Forward", forward.name)
    assertEquals(com.example.equalizer.MacroProfile.FOCUSED, forward.macroProfile)

    // Unknown profile defaults to Balanced
    val fallback = com.example.equalizer.EqualizerPresets.getById("unknown_profile")
    assertEquals("balanced", fallback.id)
  }

  @Test
  fun testEqualizerGainBoundsAndPresetRoundtrip() {
    // 1. Extreme tilt/contour values must stay clamped within -12 dB .. +12 dB
    val extremePositive = com.example.equalizer.DynamicSoundMapper.calculateGains(
      tilt = 5.0f,
      contour = 5.0f,
      macroProfile = com.example.equalizer.MacroProfile.FOCUSED
    )
    val extremeNegative = com.example.equalizer.DynamicSoundMapper.calculateGains(
      tilt = -5.0f,
      contour = -5.0f,
      macroProfile = com.example.equalizer.MacroProfile.BROAD
    )
    assertEquals(15, extremePositive.size)
    assertEquals(15, extremeNegative.size)
    assertTrue(extremePositive.all { it in -12.0f..12.0f })
    assertTrue(extremeNegative.all { it in -12.0f..12.0f })

    // 2. Manual mode returns defensive copy of manual bands via AdaptiveEqualizerSettings
    val manual = FloatArray(15) { idx -> (idx - 7).toFloat() }
    val eqSettings = com.example.equalizer.AdaptiveEqualizerSettings(
      isEnabled = true,
      mode = com.example.equalizer.EqualizerMode.MANUAL,
      manualBands = manual,
      volumeBoostDb = 3.5f
    )
    val effective = eqSettings.getEffectiveGains()
    assertEquals(15, effective.size)
    for (i in 0 until 15) {
      assertEquals(manual[i], effective[i], 0.001f)
    }

    // 3. CustomEqPresetEntity comma-separated 15-band JSON/CSV roundtrip
    val serialized = manual.joinToString(",")
    val entity = com.example.data.CustomEqPresetEntity(
      name = "Editorial Acoustic",
      bandLevelsJson = serialized,
      bassBoost = 350,
      virtualizer = 450,
      reverbPreset = 2
    )
    val parsedBands = entity.bandLevelsJson.split(",").mapNotNull { it.trim().toFloatOrNull() }
    assertEquals(15, parsedBands.size)
    for (i in 0 until 15) {
      assertEquals(manual[i], parsedBands[i], 0.001f)
    }
    assertEquals("Editorial Acoustic", entity.name)
    assertEquals(350, entity.bassBoost)
  }
}


