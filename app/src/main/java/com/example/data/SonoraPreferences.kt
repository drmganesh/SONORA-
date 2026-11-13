package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.PlayerStyle
import ui.player.seekbar.SeekbarStyle
import com.example.model.RepeatMode
import com.example.model.ShuffleMode
import com.example.model.SortOption
import com.example.model.SpatialAudioConfig
import com.example.model.SpatialMode
import com.example.model.ThemeMode
import org.json.JSONArray

class SonoraPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sonora_app_settings", Context.MODE_PRIVATE)

    init {
        // Safely remove obsolete Equalizer and 8D preferences without altering other user settings
        if (prefs.contains("equalizer_enabled") || prefs.contains("saved_band_gains") || prefs.contains("current_eq_preset_index")) {
            prefs.edit()
                .remove("equalizer_enabled")
                .remove("current_eq_preset_index")
                .remove("saved_band_gains")
                .apply()
        }
    }

    // Theme Mode
    var themeMode: ThemeMode
        get() {
            val name = prefs.getString("theme_mode", ThemeMode.AMOLED_BLACK.name) ?: ThemeMode.AMOLED_BLACK.name
            return try {
                ThemeMode.valueOf(name)
            } catch (e: Exception) {
                ThemeMode.AMOLED_BLACK
            }
        }
        set(value) = prefs.edit().putString("theme_mode", value.name).apply()

    // Player Style System (9 Premium Styles)
    var playerStyle: PlayerStyle
        get() {
            val id = prefs.getString("player_style", PlayerStyle.DEFAULT.id)
            return PlayerStyle.fromId(id)
        }
        set(value) = prefs.edit().putString("player_style", value.id).apply()

    // Seekbar Style System (9 Premium Styles, default = BLOCKS)
    var seekbarStyle: SeekbarStyle
        get() {
            val id = prefs.getString("seekbar_style", SeekbarStyle.DEFAULT.id)
            return SeekbarStyle.fromId(id)
        }
        set(value) = prefs.edit().putString("seekbar_style", value.id).apply()

    // Dynamic Album Colors Theme Engine
    var dynamicColorsEnabled: Boolean
        get() = prefs.getBoolean("dynamic_colors_enabled", true)
        set(value) = prefs.edit().putBoolean("dynamic_colors_enabled", value).apply()

    var dynamicThemeMode: com.example.model.DynamicThemeMode
        get() {
            val id = prefs.getString("dynamic_theme_mode", com.example.model.DynamicThemeMode.DEFAULT.id)
            return com.example.model.DynamicThemeMode.fromId(id)
        }
        set(value) = prefs.edit().putString("dynamic_theme_mode", value.id).apply()

    // Editorial Home Theme & Custom Accent
    var homeThemeMode: ui.theme.HomeThemeMode
        get() {
            val id = prefs.getString("home_theme_mode", ui.theme.HomeThemeMode.DEFAULT.id)
            return ui.theme.HomeThemeMode.fromId(id)
        }
        set(value) = prefs.edit().putString("home_theme_mode", value.id).apply()

    var homeCustomAccentArgb: Long
        get() = prefs.getLong("home_custom_accent_argb", -1L)
        set(value) = prefs.edit().putLong("home_custom_accent_argb", value).apply()

    // Playback Settings
    var crossfadeSeconds: Int
        get() = prefs.getInt("crossfade_seconds", 0)
        set(value) = prefs.edit().putInt("crossfade_seconds", value).apply()

    var gaplessEnabled: Boolean
        get() = prefs.getBoolean("gapless_enabled", true)
        set(value) = prefs.edit().putBoolean("gapless_enabled", value).apply()

    var repeatMode: RepeatMode
        get() {
            val name = prefs.getString("repeat_mode", RepeatMode.OFF.name) ?: RepeatMode.OFF.name
            return try {
                RepeatMode.valueOf(name)
            } catch (e: Exception) {
                RepeatMode.OFF
            }
        }
        set(value) = prefs.edit().putString("repeat_mode", value.name).apply()

    var shuffleMode: ShuffleMode
        get() {
            val name = prefs.getString("shuffle_mode", ShuffleMode.OFF.name) ?: ShuffleMode.OFF.name
            return try {
                ShuffleMode.valueOf(name)
            } catch (e: Exception) {
                ShuffleMode.OFF
            }
        }
        set(value) = prefs.edit().putString("shuffle_mode", value.name).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1.0f)
        set(value) = prefs.edit().putFloat("playback_speed", value).apply()

    // Audio FX
    var bassBoost: Int
        get() = prefs.getInt("bass_boost", 600)
        set(value) = prefs.edit().putInt("bass_boost", value).apply()

    var virtualizer: Int
        get() = prefs.getInt("virtualizer", 750)
        set(value) = prefs.edit().putInt("virtualizer", value).apply()

    var reverbPreset: Short
        get() = prefs.getInt("reverb_preset", 0).toShort()
        set(value) = prefs.edit().putInt("reverb_preset", value.toInt()).apply()

    var loudnessGainMb: Int
        get() = prefs.getInt("loudness_gain_mb", 300)
        set(value) = prefs.edit().putInt("loudness_gain_mb", value).apply()

    // 360 Spatial Audio
    var selectedSpatialPresetIndex: Int
        get() = prefs.getInt("selected_spatial_preset_index", 0)
        set(value) = prefs.edit().putInt("selected_spatial_preset_index", value).apply()

    var spatialConfig: SpatialAudioConfig
        get() {
            val isEnabled = prefs.getBoolean("spatial_enabled", true)
            val modeName = prefs.getString("spatial_mode", SpatialMode.HEADPHONES.name) ?: SpatialMode.HEADPHONES.name
            val mode = try {
                SpatialMode.valueOf(modeName)
            } catch (e: Exception) {
                SpatialMode.HEADPHONES
            }
            val intensity = prefs.getFloat("spatial_intensity", 0.75f)
            val soundstageWidth = prefs.getFloat("spatial_soundstage_width", 0.85f)
            val listenerX = prefs.getFloat("spatial_listener_x", 0f)
            val listenerY = prefs.getFloat("spatial_listener_y", 0f)
            val virtualSurround = prefs.getBoolean("spatial_virtual_surround", true)
            val stereoWidening = prefs.getFloat("spatial_stereo_widening", 0.80f)
            val channelBalance = prefs.getFloat("spatial_channel_balance", 0.0f)
            val twsWideStereo = prefs.getBoolean("spatial_tws_wide_stereo", true)
            val twsStereoBoost = prefs.getFloat("spatial_tws_stereo_boost", 0.90f)

            return SpatialAudioConfig(
                isEnabled = isEnabled,
                mode = mode,
                intensity = intensity,
                soundstageWidth = soundstageWidth,
                listenerX = listenerX,
                listenerY = listenerY,
                virtualSurround = virtualSurround,
                stereoWidening = stereoWidening,
                channelBalance = channelBalance,
                twsWideStereoEnabled = twsWideStereo,
                twsStereoBoost = twsStereoBoost
            )
        }
        set(value) {
            prefs.edit()
                .putBoolean("spatial_enabled", value.isEnabled)
                .putString("spatial_mode", value.mode.name)
                .putFloat("spatial_intensity", value.intensity)
                .putFloat("spatial_soundstage_width", value.soundstageWidth)
                .putFloat("spatial_listener_x", value.listenerX)
                .putFloat("spatial_listener_y", value.listenerY)
                .putBoolean("spatial_virtual_surround", value.virtualSurround)
                .putFloat("spatial_stereo_widening", value.stereoWidening)
                .putFloat("spatial_channel_balance", value.channelBalance)
                .putBoolean("spatial_tws_wide_stereo", value.twsWideStereoEnabled)
                .putFloat("spatial_tws_stereo_boost", value.twsStereoBoost)
                .apply()
        }

    // Library Sort Option
    var sortOption: SortOption
        get() {
            val name = prefs.getString("sort_option", SortOption.TITLE_ASC.name) ?: SortOption.TITLE_ASC.name
            return try {
                SortOption.valueOf(name)
            } catch (e: Exception) {
                SortOption.TITLE_ASC
            }
        }
        set(value) = prefs.edit().putString("sort_option", value.name).apply()

    // Gesture Controls Settings
    var gesturesEnabled: Boolean
        get() = prefs.getBoolean("gestures_enabled", true)
        set(value) = prefs.edit().putBoolean("gestures_enabled", value).apply()

    var swipeArtworkToSkip: Boolean
        get() = prefs.getBoolean("gesture_swipe_artwork_skip", true)
        set(value) = prefs.edit().putBoolean("gesture_swipe_artwork_skip", value).apply()

    var doubleTapToSeek: Boolean
        get() = prefs.getBoolean("gesture_double_tap_seek", true)
        set(value) = prefs.edit().putBoolean("gesture_double_tap_seek", value).apply()

    var swipeDownToCollapse: Boolean
        get() = prefs.getBoolean("gesture_swipe_down_collapse", true)
        set(value) = prefs.edit().putBoolean("gesture_swipe_down_collapse", value).apply()

    var miniPlayerGestures: Boolean
        get() = prefs.getBoolean("gesture_mini_player", true)
        set(value) = prefs.edit().putBoolean("gesture_mini_player", value).apply()

    var volumeSlideGesture: Boolean
        get() = prefs.getBoolean("gesture_volume_slide", true)
        set(value) = prefs.edit().putBoolean("gesture_volume_slide", value).apply()

    // Last played track ID and queue persistence
    var lastPlayedSongId: Long
        get() = prefs.getLong("last_played_song_id", -1L)
        set(value) = prefs.edit().putLong("last_played_song_id", value).apply()

    var lastQueueSongIds: List<Long>
        get() {
            val raw = prefs.getString("last_queue_song_ids", null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                val list = mutableListOf<Long>()
                for (i in 0 until arr.length()) list.add(arr.getLong(i))
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            prefs.edit().putString("last_queue_song_ids", arr.toString()).apply()
        }

    var lastQueueIndex: Int
        get() = prefs.getInt("last_queue_index", 0)
        set(value) = prefs.edit().putInt("last_queue_index", value).apply()

    // Automix & Harmonic Crossfade Match
    var automixEnabled: Boolean
        get() = prefs.getBoolean("automix_enabled", true)
        set(value) = prefs.edit().putBoolean("automix_enabled", value).apply()

    var matchNextSongEnabled: Boolean
        get() = prefs.getBoolean("match_next_song_enabled", true)
        set(value) = prefs.edit().putBoolean("match_next_song_enabled", value).apply()

    // Daily Listening Analytics (Stores seconds listened per day: key format "analytics_day_YYYY-MM-DD")
    fun addListeningTime(seconds: Int, dateKey: String) {
        val cur = prefs.getInt("analytics_day_$dateKey", 0)
        prefs.edit().putInt("analytics_day_$dateKey", cur + seconds).apply()
    }

    fun getListeningTime(dateKey: String): Int {
        return prefs.getInt("analytics_day_$dateKey", 0)
    }

    fun getWeeklyListeningStats(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val cal = java.util.Calendar.getInstance()
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        for (i in 6 downTo 0) {
            val c = cal.clone() as java.util.Calendar
            c.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val key = format.format(c.time)
            map[key] = getListeningTime(key)
        }
        return map
    }

    // Motion & Accessibility
    var reduceAnimations: Boolean
        get() = prefs.getBoolean("reduce_animations", false)
        set(value) = prefs.edit().putBoolean("reduce_animations", value).apply()

    // Persisted Recent Search History (max 10 queries)
    var recentSearches: List<String>
        get() {
            val raw = prefs.getString("recent_searches_json", null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.distinct().take(10).forEach { arr.put(it) }
            prefs.edit().putString("recent_searches_json", arr.toString()).apply()
        }

    // Audio Effects Studio: Global Bypass & 8D Slow-Orbit Spatializer
    var globalEffectsBypass: Boolean
        get() = prefs.getBoolean("global_effects_bypass", false)
        set(value) = prefs.edit().putBoolean("global_effects_bypass", value).apply()

    var orbit8dEnabled: Boolean
        get() = prefs.getBoolean("orbit_8d_enabled", false)
        set(value) = prefs.edit().putBoolean("orbit_8d_enabled", value).apply()

    var orbit8dSpeed: Float
        get() = prefs.getFloat("orbit_8d_speed", 0.35f)
        set(value) = prefs.edit().putFloat("orbit_8d_speed", value).apply()

    var orbit8dDepth: Float
        get() = prefs.getFloat("orbit_8d_depth", 0.75f)
        set(value) = prefs.edit().putFloat("orbit_8d_depth", value).apply()

    // Extended Playback & Notification Preferences
    var audioFocusEnabled: Boolean
        get() = prefs.getBoolean("audio_focus_enabled", true)
        set(value) = prefs.edit().putBoolean("audio_focus_enabled", value).apply()

    var resumeOnLaunch: Boolean
        get() = prefs.getBoolean("resume_on_launch", true)
        set(value) = prefs.edit().putBoolean("resume_on_launch", value).apply()

    var headsetAutoPlayPause: Boolean
        get() = prefs.getBoolean("headset_auto_play_pause", true)
        set(value) = prefs.edit().putBoolean("headset_auto_play_pause", value).apply()

    var showLockScreenControls: Boolean
        get() = prefs.getBoolean("show_lock_screen_controls", true)
        set(value) = prefs.edit().putBoolean("show_lock_screen_controls", value).apply()

    var showNotificationFormatBadge: Boolean
        get() = prefs.getBoolean("show_notification_format_badge", true)
        set(value) = prefs.edit().putBoolean("show_notification_format_badge", value).apply()
}
