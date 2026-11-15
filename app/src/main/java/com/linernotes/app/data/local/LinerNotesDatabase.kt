package com.linernotes.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.linernotes.app.data.local.dao.AlbumDao
import com.linernotes.app.data.local.dao.CachedSongDao
import com.linernotes.app.data.local.dao.LyricAnnotationDao
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.CachedSongEntity
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.local.entity.TrackEntity

@Database(
    entities = [
        AlbumEntity::class,
        TrackEntity::class,
        LyricAnnotationEntity::class,
        SongStoryEntity::class,
        CachedSongEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class LinerNotesDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun lyricAnnotationDao(): LyricAnnotationDao
    abstract fun cachedSongDao(): CachedSongDao
}
