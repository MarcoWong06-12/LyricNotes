package com.linernotes.app.data.repository

import com.linernotes.app.data.local.dao.AlbumDao
import com.linernotes.app.data.local.entity.AlbumEntity
import com.linernotes.app.data.local.entity.TrackEntity
import com.linernotes.app.data.local.relation.AlbumWithTracks
import com.linernotes.app.domain.repository.AlbumRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao
) : AlbumRepository {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override fun getCollectionStream(): Flow<List<AlbumEntity>> {
        return albumDao.getAllAlbumsFlow().flowOn(ioDispatcher)
    }

    override fun searchCollectionStream(query: String): Flow<List<AlbumEntity>> {
        return albumDao.searchAlbumsFlow(query.trim()).flowOn(ioDispatcher)
    }

    override fun getAlbumBookletStream(albumId: String): Flow<AlbumWithTracks?> {
        return albumDao.getAlbumWithTracksFlow(albumId).flowOn(ioDispatcher)
    }

    override suspend fun saveAlbum(album: AlbumEntity, tracks: List<TrackEntity>): List<TrackEntity> {
        return withContext(ioDispatcher) {
            albumDao.insertAlbumWithTracks(album, tracks)
            albumDao.getAlbumWithTracksOnce(album.id)?.tracks ?: tracks
        }
    }

    override suspend fun updateAlbumNotes(albumId: String, notes: String?) {
        withContext(ioDispatcher) {
            val existing = albumDao.getAlbumWithTracksOnce(albumId)?.album ?: return@withContext
            albumDao.updateAlbum(existing.copy(notes = notes))
        }
    }

    override suspend fun updateTrackTranslation(
        trackId: Long,
        translatedTitle: String?,
        originalLyrics: String?,
        translatedLyrics: String?
    ) {
        withContext(ioDispatcher) {
            albumDao.updateTrackLyricsAndTranslation(
                trackId = trackId,
                translatedTitle = translatedTitle,
                originalLyrics = originalLyrics,
                translatedLyrics = translatedLyrics
            )
        }
    }

    override suspend fun updateAlbumTranslation(albumId: String, translatedTitle: String?) {
        withContext(ioDispatcher) {
            val existing = albumDao.getAlbumWithTracksOnce(albumId)?.album ?: return@withContext
            albumDao.updateAlbum(existing.copy(translatedTitle = translatedTitle))
        }
    }

    override suspend fun removeAlbumFromShelf(albumId: String) {
        withContext(ioDispatcher) {
            albumDao.deleteAlbumById(albumId)
        }
    }
}
