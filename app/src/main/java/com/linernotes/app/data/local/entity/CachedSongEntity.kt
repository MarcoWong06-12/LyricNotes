package com.linernotes.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_songs")
data class CachedSongEntity(
    @PrimaryKey val cacheKey: String,
    val lyricsJson: String,
    val songStoryJson: String? = null,
    val annotationsJson: String = "[]",
    val geniusNotice: String? = null,
    val hasAttemptedGenius: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
