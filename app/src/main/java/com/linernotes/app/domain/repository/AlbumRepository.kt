package com.linernotes.app.domain.repository

import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.local.relation.AlbumWithTracks
import kotlinx.coroutines.flow.Flow

interface AlbumRepository {
    fun getCollectionStream(): Flow<List<AlbumEntity>>
    fun searchCollectionStream(query: String): Flow<List<AlbumEntity>>
    fun getAlbumBookletStream(albumId: String): Flow<AlbumWithTracks?>
    suspend fun saveAlbum(album: AlbumEntity, tracks: List<TrackEntity>): List<TrackEntity>
    suspend fun updateAlbumNotes(albumId: String, notes: String?)
    suspend fun updateTrackTranslation(
        trackId: Long,
        translatedTitle: String?,
        originalLyrics: String?,
        translatedLyrics: String?
    )
    suspend fun updateAlbumTranslation(albumId: String, translatedTitle: String?)
    suspend fun removeAlbumFromShelf(albumId: String)
}
