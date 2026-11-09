package com.example.model

data class DailyAnalytics(
    val dateKey: String, // e.g. "2026-11-08"
    val displayDate: String, // e.g. "Today" or "Sun, Nov 8"
    val listenedSeconds: Int,
    val tracksPlayed: Int,
    val topArtist: String,
    val topGenre: String,
    val weeklyDayLabels: List<String>,
    val weeklyDayMinutes: List<Int>,
    val totalWeeklyMinutes: Int,
    val peakHourLabel: String
) {
    val listenedFormatted: String
        get() {
            val minutes = listenedSeconds / 60
            val hours = minutes / 60
            val remMinutes = minutes % 60
            return if (hours > 0) "${hours}h ${remMinutes}m" else "${minutes}m"
        }
}
