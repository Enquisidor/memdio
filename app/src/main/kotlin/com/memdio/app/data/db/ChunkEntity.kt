package com.memdio.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents one 30-second AAC audio chunk on disk.
 * [startMs] is the primary key — chunks are named by their start epoch.
 */
@Entity(tableName = "chunks")
data class ChunkEntity(
    @PrimaryKey val startMs: Long,
    val endMs: Long,
    val filePath: String,
    val sizeBytes: Long,
) {
    val durationMs: Long get() = endMs - startMs
}
