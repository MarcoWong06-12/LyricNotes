package com.linernotes.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "lyric_annotations",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"])]
)
data class LyricAnnotationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val lyricFragment: String,
    val explanationText: String,
    val authorName: String? = null,
    val authorAvatarUrl: String? = null,
    val isVerified: Boolean = false,
    val votesTotal: Int = 0,
    val imageUrlsJson: String? = null, // JSON array string e.g. ["url1", "url2"]
    val source: String = "GENIUS",     // "GENIUS" or "AI_CURATED"
    val geniusSongId: Long? = null,
    val geniusUrl: String? = null,
    val explanationTranslation: String? = null,
    val lyricTranslation: String? = null
)
