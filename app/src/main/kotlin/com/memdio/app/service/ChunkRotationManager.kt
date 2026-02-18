package com.memdio.app.service

import com.memdio.app.data.repository.ChunkRepository
import javax.inject.Inject

/**
 * Pure business logic for the circular-buffer rotation policy.
 *
 * After each new chunk is written, [enforceLimit] loops until total buffered
 * duration fits within [limitMs], deleting the oldest chunk each iteration
 * and removing its file from disk via [fileDeleter].
 *
 * Kept separate from [BufferService] so it can be tested without an Android context.
 */
class ChunkRotationManager @Inject constructor(
    private val repo: ChunkRepository,
    private val eventBus: BufferEventBus,
) {

    // Default implementation deletes the file; can be replaced in tests.
    var fileDeleter: (String) -> Unit = { path -> java.io.File(path).delete() }


    /**
     * Deletes the oldest chunk(s) until total buffer duration ≤ [limitMs].
     * No-op if the buffer is already within bounds or empty.
     */
    suspend fun enforceLimit(limitMs: Long) {
        while (repo.getTotalDurationMs() > limitMs) {
            val oldest = repo.getOldestChunk() ?: break
            fileDeleter(oldest.filePath)
            repo.deleteOldest()
            eventBus.emit(BufferEvent.ChunkDeleted(oldest))
        }
    }
}
