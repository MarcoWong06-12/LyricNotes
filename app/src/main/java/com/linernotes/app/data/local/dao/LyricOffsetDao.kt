package com.linernotes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.linernotes.app.data.local.entity.LyricOffsetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricOffsetDao {
    @Query("SELECT * FROM lyric_offsets WHERE trackId = :trackId LIMIT 1")
    suspend fun getOffset(trackId: Long): LyricOffsetEntity?

    @Query("SELECT * FROM lyric_offsets WHERE trackId = :trackId LIMIT 1")
    fun getOffsetFlow(trackId: Long): Flow<LyricOffsetEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveOffset(offset: LyricOffsetEntity)

    @Query("DELETE FROM lyric_offsets WHERE trackId = :trackId")
    suspend fun deleteOffset(trackId: Long)
}
