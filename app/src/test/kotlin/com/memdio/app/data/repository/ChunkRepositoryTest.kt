package com.memdio.app.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.memdio.app.data.db.ChunkDao
import com.memdio.app.data.db.ChunkEntity
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * RED → GREEN: ChunkRepository is a thin DAO wrapper.
 * All tests use MockK so there's no need for an Android context.
 */
class ChunkRepositoryTest {

    private val dao: ChunkDao = mockk()
    private lateinit var repo: ChunkRepository

    @Before
    fun setUp() {
        repo = ChunkRepository(dao)
    }

    @Test
    fun `insert delegates to dao`() = runTest {
        val chunk = chunk(0, 30_000)
        coJustRun { dao.insert(chunk) }

        repo.insert(chunk)

        coVerify(exactly = 1) { dao.insert(chunk) }
    }

    @Test
    fun `allChunks emits dao flow`() = runTest {
        val chunks = listOf(chunk(0, 30_000), chunk(30_000, 60_000))
        coEvery { dao.getAllSortedByStart() } returns flowOf(chunks)

        repo.allChunks.test {
            assertThat(awaitItem()).isEqualTo(chunks)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getTotalDurationMs delegates to dao`() = runTest {
        coEvery { dao.getTotalDurationMs() } returns 120_000L
        assertThat(repo.getTotalDurationMs()).isEqualTo(120_000L)
    }

    @Test
    fun `getTotalSizeBytes delegates to dao`() = runTest {
        coEvery { dao.getTotalSizeBytes() } returns 480_000L
        assertThat(repo.getTotalSizeBytes()).isEqualTo(480_000L)
    }

    @Test
    fun `deleteOldest delegates to dao`() = runTest {
        coJustRun { dao.deleteOldest() }
        repo.deleteOldest()
        coVerify(exactly = 1) { dao.deleteOldest() }
    }

    @Test
    fun `deleteAll delegates to dao`() = runTest {
        coJustRun { dao.deleteAll() }
        repo.deleteAll()
        coVerify(exactly = 1) { dao.deleteAll() }
    }

    @Test
    fun `getOldestChunk delegates to dao`() = runTest {
        val expected = chunk(0, 30_000)
        coEvery { dao.getOldestChunk() } returns expected
        assertThat(repo.getOldestChunk()).isEqualTo(expected)
    }

    private fun chunk(startMs: Long, endMs: Long) =
        ChunkEntity(startMs = startMs, endMs = endMs, filePath = "/buf/$startMs.aac", sizeBytes = 240_000L)
}
