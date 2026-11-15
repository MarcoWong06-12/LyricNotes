package com.linernotes.app.di

import android.content.Context
import androidx.room.Room
import com.linernotes.app.data.local.LinerNotesDatabase
import com.linernotes.app.data.local.dao.AlbumDao
import com.linernotes.app.data.local.dao.CachedSongDao
import com.linernotes.app.data.local.dao.LyricAnnotationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LinerNotesDatabase {
        return Room.databaseBuilder(
            context,
            LinerNotesDatabase::class.java,
            "linernotes.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideAlbumDao(database: LinerNotesDatabase): AlbumDao {
        return database.albumDao()
    }

    @Provides
    @Singleton
    fun provideLyricAnnotationDao(database: LinerNotesDatabase): LyricAnnotationDao {
        return database.lyricAnnotationDao()
    }

    @Provides
    @Singleton
    fun provideCachedSongDao(database: LinerNotesDatabase): CachedSongDao {
        return database.cachedSongDao()
    }
}
