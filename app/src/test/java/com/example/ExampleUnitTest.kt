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
    assertEquals(9, styles.size)

    val expectedIds = listOf(
      "classic", "gesture", "editorial", "canvas", "bento", "sticker", "morph", "dial", "hero"
    )
    val actualIds = styles.map { it.id }
    assertEquals(expectedIds, actualIds)

    // Verify lookup by id
    assertEquals(com.example.model.PlayerStyle.BENTO, com.example.model.PlayerStyle.fromId("bento"))
    assertEquals(com.example.model.PlayerStyle.DIAL, com.example.model.PlayerStyle.fromId("DIAL"))
    assertEquals(com.example.model.PlayerStyle.HERO, com.example.model.PlayerStyle.fromId("HeRo"))
    assertEquals(com.example.model.PlayerStyle.CLASSIC, com.example.model.PlayerStyle.fromId("non_existent"))
    assertEquals(com.example.model.PlayerStyle.CLASSIC, com.example.model.PlayerStyle.fromId(null))
  }

  @Test
  fun testSeekbarStyleDefinitions() {
    val styles = ui.player.seekbar.SeekbarStyle.entries
    assertEquals(9, styles.size)

    val expectedIds = listOf(
      "blocks", "waveform", "wave_line", "classic", "dots", "gradient", "neon_glow", "material3", "m3_expressive"
    )
    val actualIds = styles.map { it.id }
    assertEquals(expectedIds, actualIds)

    // Verify Default requirement is BLOCKS
    assertEquals(ui.player.seekbar.SeekbarStyle.BLOCKS, ui.player.seekbar.SeekbarStyle.DEFAULT)

    // Verify lookup by id with fallback to BLOCKS
    assertEquals(ui.player.seekbar.SeekbarStyle.WAVEFORM, ui.player.seekbar.SeekbarStyle.fromId("waveform"))
    assertEquals(ui.player.seekbar.SeekbarStyle.NEON_GLOW, ui.player.seekbar.SeekbarStyle.fromId("NEON_GLOW"))
    assertEquals(ui.player.seekbar.SeekbarStyle.M3_EXPRESSIVE, ui.player.seekbar.SeekbarStyle.fromId("m3_expressive"))
    assertEquals(ui.player.seekbar.SeekbarStyle.BLOCKS, ui.player.seekbar.SeekbarStyle.fromId("unknown_style"))
    assertEquals(ui.player.seekbar.SeekbarStyle.BLOCKS, ui.player.seekbar.SeekbarStyle.fromId(null))
  }
}
