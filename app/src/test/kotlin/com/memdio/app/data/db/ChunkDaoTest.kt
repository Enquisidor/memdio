package com.memdio.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * RED → GREEN: Verifies all DAO queries against a real in-memory Room database
 * running on the JVM via Robolectric (no emulator required).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChunkDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ChunkDao

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.chunkDao()
    }

    @After
    fun tearDown() = db.close()

    // ── insert / getAllSortedByStart ──────────────────────────────────────────

    @Test
    fun `insert then getAllSortedByStart returns chunks in start order`() = runTest {
        dao.insert(chunk(startMs = 2_000, endMs = 32_000))
        dao.insert(chunk(startMs = 0, endMs = 30_000))
        dao.insert(chunk(startMs = 1_000, endMs = 31_000))

        val all = dao.getAllSortedByStart().first()
        assertThat(all.map { it.startMs }).isEqualTo(listOf(0L, 1_000L, 2_000L))
    }

    @Test
    fun `insert duplicate primary key replaces existing chunk`() = runTest {
        dao.insert(chunk(startMs = 0, endMs = 30_000, sizeBytes = 100))
        dao.insert(chunk(startMs = 0, endMs = 30_000, sizeBytes = 999))

        val all = dao.getAllSortedByStart().first()
        assertThat(all).hasSize(1)
        assertThat(all.first().sizeBytes).isEqualTo(999)
    }

    // ── getTotalDurationMs ───────────────────────────────────────────────────

    @Test
    fun `getTotalDurationMs sums all chunk durations`() = runTest {
        dao.insert(chunk(startMs = 0, endMs = 30_000))
        dao.insert(chunk(startMs = 30_000, endMs = 60_000))

        val total = dao.getTotalDurationMs()
        assertThat(total).isEqualTo(60_000L)
    }

    @Test
    fun `getTotalDurationMs returns 0 when table is empty`() = runTest {
        assertThat(dao.getTotalDurationMs()).isEqualTo(0L)
    }

    // ── getTotalSizeBytes ────────────────────────────────────────────────────

    @Test
    fun `getTotalSizeBytes sums all chunk sizes`() = runTest {
        dao.insert(chunk(startMs = 0, endMs = 30_000, sizeBytes = 240_000))
        dao.insert(chunk(startMs = 30_000, endMs = 60_000, sizeBytes = 240_000))

        assertThat(dao.getTotalSizeBytes()).isEqualTo(480_000L)
    }

    // ── deleteOldest ─────────────────────────────────────────────────────────

    @Test
    fun `deleteOldest removes the chunk with the smallest startMs`() = runTest {
        dao.insert(chunk(startMs = 0, endMs = 30_000))
        dao.insert(chunk(startMs = 30_000, endMs = 60_000))
        dao.insert(chunk(startMs = 60_000, endMs = 90_000))

        dao.deleteOldest()

        val remaining = dao.getAllSortedByStart().first()
        assertThat(remaining).hasSize(2)
        assertThat(remaining.first().startMs).isEqualTo(30_000L)
    }

    @Test
    fun `deleteOldest on empty table does not throw`() = runTest {
        dao.deleteOldest() // must not crash
        assertThat(dao.getAllSortedByStart().first()).isEmpty()
    }

    // ── deleteAll ────────────────────────────────────────────────────────────

    @Test
    fun `deleteAll removes every chunk`() = runTest {
        repeat(5) { i -> dao.insert(chunk(startMs = i.toLong() * 30_000, endMs = (i + 1).toLong() * 30_000)) }
        dao.deleteAll()
        assertThat(dao.getAllSortedByStart().first()).isEmpty()
    }

    // ── getOldestChunk ───────────────────────────────────────────────────────

    @Test
    fun `getOldestChunk returns chunk with minimum startMs`() = runTest {
        dao.insert(chunk(startMs = 60_000, endMs = 90_000))
        dao.insert(chunk(startMs = 0, endMs = 30_000))

        val oldest = dao.getOldestChunk()
        assertThat(oldest?.startMs).isEqualTo(0L)
    }

    @Test
    fun `getOldestChunk returns null on empty table`() = runTest {
        assertThat(dao.getOldestChunk()).isNull()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun chunk(
        startMs: Long,
        endMs: Long,
        filePath: String = "/buf/$startMs.aac",
        sizeBytes: Long = 240_000L,
    ) = ChunkEntity(startMs = startMs, endMs = endMs, filePath = filePath, sizeBytes = sizeBytes)
}
