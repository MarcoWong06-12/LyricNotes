package com.linernotes.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.linernotes.app.data.local.entity.BookletPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookletDao {
    @Query("SELECT * FROM booklet_pages WHERE albumId = :albumId ORDER BY pageNumber ASC")
    fun getBookletPagesFlow(albumId: String): Flow<List<BookletPageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: BookletPageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<BookletPageEntity>)

    @Query("DELETE FROM booklet_pages WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)

    @Query("DELETE FROM booklet_pages WHERE albumId = :albumId")
    suspend fun deleteAllPagesForAlbum(albumId: String)

    @Query("SELECT COUNT(*) FROM booklet_pages WHERE albumId = :albumId")
    suspend fun getPageCount(albumId: String): Int
}
