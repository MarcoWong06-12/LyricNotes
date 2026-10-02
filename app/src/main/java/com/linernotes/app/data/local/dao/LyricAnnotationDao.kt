package com.linernotes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.linernotes.app.data.local.entity.LyricAnnotationEntity
import com.linernotes.app.data.local.entity.SongStoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricAnnotationDao {

    @Query("SELECT * FROM lyric_annotations WHERE trackId = :trackId ORDER BY id ASC")
    fun getAnnotationsFlow(trackId: Long): Flow<List<LyricAnnotationEntity>>

    @Query("SELECT * FROM lyric_annotations WHERE trackId = :trackId ORDER BY id ASC")
    suspend fun getAnnotations(trackId: Long): List<LyricAnnotationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<LyricAnnotationEntity>): List<Long>

    @Update
    suspend fun updateAnnotation(annotation: LyricAnnotationEntity)

    @Query("DELETE FROM lyric_annotations WHERE trackId = :trackId")
    suspend fun deleteAnnotationsForTrack(trackId: Long)

    @androidx.room.Transaction
    suspend fun replaceAnnotationsForTrack(trackId: Long, annotations: List<LyricAnnotationEntity>): List<LyricAnnotationEntity> {
        deleteAnnotationsForTrack(trackId)
        if (annotations.isNotEmpty()) {
            val rowIds = insertAnnotations(annotations)
            return annotations.mapIndexed { index, entity ->
                val generatedId = rowIds.getOrNull(index) ?: entity.id
                if (generatedId > 0L) entity.copy(id = generatedId) else entity
            }
        }
        return emptyList()
    }

    @Query("SELECT * FROM song_stories WHERE trackId = :trackId LIMIT 1")
    fun getSongStoryFlow(trackId: Long): Flow<SongStoryEntity?>

    @Query("SELECT * FROM song_stories WHERE trackId = :trackId LIMIT 1")
    suspend fun getSongStory(trackId: Long): SongStoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongStory(story: SongStoryEntity)

    @Update
    suspend fun updateSongStory(story: SongStoryEntity)

    @Query("DELETE FROM song_stories WHERE trackId = :trackId")
    suspend fun deleteSongStoryForTrack(trackId: Long)

    @Query("SELECT * FROM lyric_annotations WHERE trackId IN (:trackIds)")
    suspend fun getAnnotationsForTracks(trackIds: List<Long>): List<LyricAnnotationEntity>

    @Query("SELECT * FROM song_stories WHERE trackId IN (:trackIds)")
    suspend fun getSongStoriesForTracks(trackIds: List<Long>): List<SongStoryEntity>
}
