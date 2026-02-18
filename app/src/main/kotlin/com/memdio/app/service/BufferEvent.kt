package com.memdio.app.service

import com.memdio.app.data.db.ChunkEntity

/**
 * Events emitted by the audio buffer pipeline.
 *
 * Collect these from [BufferEventBus.events] to react to chunk lifecycle
 * changes anywhere in the app without polling or ServiceConnection binding.
 */
sealed class BufferEvent {
    /** A full 30-second chunk was recorded and persisted to the database. */
    data class ChunkRecorded(val chunk: ChunkEntity) : BufferEvent()

    /** The oldest chunk was evicted from the buffer to enforce the size limit. */
    data class ChunkDeleted(val chunk: ChunkEntity) : BufferEvent()
}
