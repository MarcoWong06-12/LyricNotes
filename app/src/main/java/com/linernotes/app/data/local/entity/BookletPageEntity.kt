package com.linernotes.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "booklet_pages",
    foreignKeys = [ForeignKey(entity = AlbumEntity::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["albumId"])]
)
data class BookletPageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val albumId: String,
    val pageNumber: Int,
    val imagePath: String,
    val pageType: String = "CONTENT" // COVER_FRONT, CONTENT, CREDITS, COVER_BACK
)
