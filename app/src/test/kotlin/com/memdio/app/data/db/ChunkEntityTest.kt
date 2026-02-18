package com.memdio.app.data.db

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * RED: Verifies ChunkEntity is a pure value type with the expected fields.
 * These tests define the contract before the entity exists.
 */
class ChunkEntityTest {

    @Test
    fun `entity stores start and end timestamps`() {
        val chunk = ChunkEntity(
            startMs = 1_000L,
            endMs = 31_000L,
            filePath = "/data/user/0/com.memdio.app/files/buffer/1000.aac",
            sizeBytes = 240_000L
        )
        assertThat(chunk.startMs).isEqualTo(1_000L)
        assertThat(chunk.endMs).isEqualTo(31_000L)
    }

    @Test
    fun `durationMs is derived from start and end`() {
        val chunk = ChunkEntity(
            startMs = 0L,
            endMs = 30_000L,
            filePath = "/buf/0.aac",
            sizeBytes = 1_024L
        )
        assertThat(chunk.durationMs).isEqualTo(30_000L)
    }

    @Test
    fun `two chunks with same primary key are equal`() {
        val a = ChunkEntity(startMs = 42L, endMs = 72L, filePath = "/a.aac", sizeBytes = 100L)
        val b = ChunkEntity(startMs = 42L, endMs = 72L, filePath = "/a.aac", sizeBytes = 100L)
        assertThat(a).isEqualTo(b)
    }
}
