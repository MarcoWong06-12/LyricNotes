package com.linernotes.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.linernotes.app.data.local.dao.AlbumDao
import com.linernotes.app.data.local.dao.BookletDao
import com.linernotes.app.data.local.dao.LyricAnnotationDao
import com.linernotes.app.data.local.dao.LyricOffsetDao
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.BookletPageEntity
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.LyricOffsetEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import com.linernotes.app.data.local.entity.TrackEntity

@Database(
    entities = [
        AlbumEntity::class,
        TrackEntity::class,
        BookletPageEntity::class,
        LyricOffsetEntity::class,
        LyricAnnotationEntity::class,
        SongStoryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LinerNotesDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun bookletDao(): BookletDao
    abstract fun lyricOffsetDao(): LyricOffsetDao
    abstract fun lyricAnnotationDao(): LyricAnnotationDao
}
