package com.linernotes.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["artist"]),
        Index(value = ["purchaseDate"])
    ]
)
data class AlbumEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val translatedTitle: String? = null,
    val artist: String,
    val releaseYear: String,
    val coverUrl: String,
    val barcode: String? = null,
    val purchaseDate: Long? = null,
    val notes: String? = null,
    val mediaType: String = "CD",       // CD, VINYL, CASSETTE, SACD, XRCD
    val label: String? = null,          // Record label
    val audioQuality: String? = null    // STANDARD, SACD, XRCD, MQA, HDCD
)
