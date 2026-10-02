package com.linernotes.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "song_stories",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["trackId"], unique = true)]
)
data class SongStoryEntity(
    @PrimaryKey val trackId: Long,
    val geniusSongId: Long? = null,
    val title: String,
    val artist: String,
    val descriptionPlain: String,
    val descriptionTranslation: String? = null,
    val releaseDate: String? = null,
    val headerImageUrl: String? = null,
    val songArtImageUrl: String? = null,
    val producerCredits: String? = null,
    val songUrl: String? = null,
    val source: String = "GENIUS"
)
