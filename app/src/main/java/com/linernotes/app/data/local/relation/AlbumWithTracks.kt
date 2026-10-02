package com.linernotes.app.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity

data class AlbumWithTracks(
    @Embedded
    val album: AlbumEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "albumId"
    )
    val tracks: List<TrackEntity>
)
