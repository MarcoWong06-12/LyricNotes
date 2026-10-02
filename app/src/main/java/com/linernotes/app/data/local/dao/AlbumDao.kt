package com.linernotes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.local.relation.AlbumWithTracks
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {

    @Query("SELECT * FROM albums ORDER BY purchaseDate DESC, id DESC")
    fun getAllAlbumsFlow(): Flow<List<AlbumEntity>>

    @Query(
        """
        SELECT * FROM albums 
        WHERE title LIKE '%' || :query || '%' 
           OR translatedTitle LIKE '%' || :query || '%' 
           OR artist LIKE '%' || :query || '%'
        ORDER BY purchaseDate DESC
        """
    )
    fun searchAlbumsFlow(query: String): Flow<List<AlbumEntity>>

    @Transaction
    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    fun getAlbumWithTracksFlow(albumId: String): Flow<AlbumWithTracks?>

    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumOnce(albumId: String): AlbumEntity?

    @Transaction
    @Query("SELECT * FROM albums WHERE id = :albumId LIMIT 1")
    suspend fun getAlbumWithTracksOnce(albumId: String): AlbumWithTracks?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: AlbumEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Transaction
    suspend fun insertAlbumWithTracks(album: AlbumEntity, tracks: List<TrackEntity>) {
        insertAlbum(album)
        insertTracks(tracks)
    }

    @Update
    suspend fun updateAlbum(album: AlbumEntity)

    @Query(
        """
        UPDATE tracks 
        SET translatedTitle = :translatedTitle,
            originalLyrics = :originalLyrics,
            translatedLyrics = :translatedLyrics 
        WHERE id = :trackId
        """
    )
    suspend fun updateTrackLyricsAndTranslation(
        trackId: Long,
        translatedTitle: String?,
        originalLyrics: String?,
        translatedLyrics: String?
    )

    @Query("DELETE FROM albums WHERE id = :albumId")
    suspend fun deleteAlbumById(albumId: String)
}
