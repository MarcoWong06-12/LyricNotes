package com.linernotes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.linernotes.app.data.local.entity.CachedSongEntity

@Dao
interface CachedSongDao {

    @Query("SELECT * FROM cached_songs WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): CachedSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedSongEntity)

    @Query("DELETE FROM cached_songs WHERE cacheKey = :cacheKey")
    suspend fun deleteByKey(cacheKey: String)

    @Query("DELETE FROM cached_songs WHERE updatedAt < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM cached_songs")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM cached_songs WHERE cacheKey NOT IN (
            SELECT cacheKey FROM cached_songs ORDER BY updatedAt DESC LIMIT :maxEntries
        )
        """
    )
    suspend fun trimToMaxEntries(maxEntries: Int)
}
