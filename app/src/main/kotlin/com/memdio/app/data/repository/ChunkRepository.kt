package com.memdio.app.data.repository

import com.memdio.app.data.db.ChunkDao
import com.memdio.app.data.db.ChunkEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for audio chunk metadata.
 * Thin wrapper around [ChunkDao]; business logic lives in [BufferService].
 */
@Singleton
class ChunkRepository @Inject constructor(private val dao: ChunkDao) {

    val allChunks: Flow<List<ChunkEntity>> = dao.getAllSortedByStart()

    suspend fun insert(chunk: ChunkEntity) = dao.insert(chunk)

    suspend fun getTotalDurationMs(): Long = dao.getTotalDurationMs()

    suspend fun getTotalSizeBytes(): Long = dao.getTotalSizeBytes()

    suspend fun deleteOldest() = dao.deleteOldest()

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun getOldestChunk(): ChunkEntity? = dao.getOldestChunk()
}
