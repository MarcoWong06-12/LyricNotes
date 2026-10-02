package com.linernotes.app.di

import android.content.Context
import androidx.room.Room
import com.linernotes.app.data.local.LinerNotesDatabase
import com.linernotes.app.data.local.dao.AlbumDao
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
    fun provideBookletDao(database: LinerNotesDatabase): com.linernotes.app.data.local.dao.BookletDao {
        return database.bookletDao()
    }

    @Provides
    @Singleton
    fun provideLyricOffsetDao(database: LinerNotesDatabase): com.linernotes.app.data.local.dao.LyricOffsetDao {
        return database.lyricOffsetDao()
    }

    @Provides
    @Singleton
    fun provideLyricAnnotationDao(database: LinerNotesDatabase): com.linernotes.app.data.local.dao.LyricAnnotationDao {
        return database.lyricAnnotationDao()
    }
}
