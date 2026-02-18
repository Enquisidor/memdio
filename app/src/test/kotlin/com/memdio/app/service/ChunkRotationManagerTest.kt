package com.memdio.app.service

import com.google.common.truth.Truth.assertThat
import com.memdio.app.data.db.ChunkEntity
import com.memdio.app.data.repository.ChunkRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * RED → GREEN: Pure business logic for rotating audio chunks.
 * The manager decides when to evict the oldest chunk based on the configured limit.
 *
 * Completely independent from Android framework — no Robolectric needed.
 */
class ChunkRotationManagerTest {

    private val repo: ChunkRepository = mockk()
    private val fileDeleter: (String) -> Unit = mockk(relaxed = true)

    private lateinit var manager: ChunkRotationManager

    @Before
    fun setUp() {
        manager = ChunkRotationManager(repo, fileDeleter)
    }

    // ── enforceLimit ─────────────────────────────────────────────────────────

    @Test
    fun `enforceLimit does not delete when total duration is under limit`() = runTest {
        val limitMs = 60 * 60 * 1_000L // 60 min
        coEvery { repo.getTotalDurationMs() } returns 30 * 60 * 1_000L // 30 min

        manager.enforceLimit(limitMs)

        coVerify(exactly = 0) { repo.deleteOldest() }
    }

    @Test
    fun `enforceLimit deletes oldest chunk when total duration exceeds limit`() = runTest {
        val limitMs = 60 * 60 * 1_000L
        val oldest = chunk(startMs = 0, endMs = 30_000)
        // First call: over limit; second call: under limit
        coEvery { repo.getTotalDurationMs() } returnsMany listOf(
            limitMs + 30_000L,
            limitMs - 1L,
        )
        coEvery { repo.getOldestChunk() } returns oldest
        coJustRun { repo.deleteOldest() }

        manager.enforceLimit(limitMs)

        coVerify(exactly = 1) { repo.deleteOldest() }
        verify { fileDeleter(oldest.filePath) }
    }

    @Test
    fun `enforceLimit deletes multiple chunks until under limit`() = runTest {
        val limitMs = 60_000L
        val oldest1 = chunk(startMs = 0, endMs = 30_000)
        val oldest2 = chunk(startMs = 30_000, endMs = 60_000)
        // totalDuration: over → over → under
        coEvery { repo.getTotalDurationMs() } returnsMany listOf(
            120_000L,
            90_000L,
            55_000L,
        )
        coEvery { repo.getOldestChunk() } returnsMany listOf(oldest1, oldest2)
        coJustRun { repo.deleteOldest() }

        manager.enforceLimit(limitMs)

        coVerify(exactly = 2) { repo.deleteOldest() }
    }

    @Test
    fun `enforceLimit handles empty repo gracefully`() = runTest {
        coEvery { repo.getTotalDurationMs() } returns 0L

        manager.enforceLimit(60 * 60 * 1_000L) // must not throw
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun chunk(startMs: Long, endMs: Long) =
        ChunkEntity(startMs = startMs, endMs = endMs, filePath = "/buf/$startMs.aac", sizeBytes = 240_000L)

    private fun verify(block: () -> Unit) = io.mockk.verify { block() }
}
