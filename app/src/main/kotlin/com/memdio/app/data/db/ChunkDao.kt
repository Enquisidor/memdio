package com.memdio.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: ChunkEntity)

    /** Emits the full list whenever any row changes. Ordered oldest-first. */
    @Query("SELECT * FROM chunks ORDER BY startMs ASC")
    fun getAllSortedByStart(): Flow<List<ChunkEntity>>

    /** Returns the sum of (endMs - startMs) across all rows, or 0 if empty. */
    @Query("SELECT COALESCE(SUM(endMs - startMs), 0) FROM chunks")
    suspend fun getTotalDurationMs(): Long

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM chunks")
    suspend fun getTotalSizeBytes(): Long

    /**
     * Deletes the single oldest chunk (smallest startMs).
     * Uses a subquery so the DELETE works on all SQLite versions.
     */
    @Query("DELETE FROM chunks WHERE startMs = (SELECT MIN(startMs) FROM chunks)")
    suspend fun deleteOldest()

    @Query("DELETE FROM chunks")
    suspend fun deleteAll()

    @Query("SELECT * FROM chunks ORDER BY startMs ASC LIMIT 1")
    suspend fun getOldestChunk(): ChunkEntity?
}
