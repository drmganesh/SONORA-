package com.example.model

data class DailyMix(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val badge: String,
    val songs: List<Song>,
    val coverSong: Song?,
    val gradientColors: List<Long>
)
