package com.linernotes.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["albumId"]),
        Index(value = ["albumId", "trackNumber"], unique = true)
    ]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val albumId: String,
    val trackNumber: Int,
    val title: String,
    val translatedTitle: String? = null,
    val originalLyrics: String? = null,
    val translatedLyrics: String? = null,
    val durationMs: Long? = null
)
