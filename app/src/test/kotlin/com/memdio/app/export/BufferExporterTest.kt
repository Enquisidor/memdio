package com.memdio.app.export

import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.memdio.app.data.db.ChunkEntity
import com.memdio.app.data.repository.ChunkRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * RED → GREEN: Tests for [BufferExporter] logic.
 *
 * MediaMuxer/MediaExtractor can't be unit-tested on JVM (they are NDK-backed),
 * so we verify:
 *  - Empty buffer returns [ExportResult.EmptyBuffer]
 *  - Single-chunk fast-path copies the file (no mux needed)
 *  - Multi-chunk path calls the muxer delegate
 *  - Output file is placed in cacheDir/exports/
 *
 * The muxer itself is tested via integration on a real device (androidTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BufferExporterTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val context: Context = mockk(relaxed = true)
    private val repo: ChunkRepository = mockk()
    private val muxerDelegate: AacMuxerDelegate = mockk()

    private lateinit var exporter: BufferExporter
    private lateinit var cacheDir: File
    private lateinit var exportDir: File

    @Before
    fun setUp() {
        cacheDir = tmpFolder.newFolder("cache")
        exportDir = File(cacheDir, "exports").also { it.mkdirs() }
        every { context.cacheDir } returns cacheDir
        exporter = BufferExporter(context, repo, muxerDelegate)
    }

    @After
    fun tearDown() = unmockkAll()

    // ── empty buffer ─────────────────────────────────────────────────────────

    @Test
    fun `export returns EmptyBuffer when no chunks exist`() = runTest {
        coEvery { repo.allChunks } returns flowOf(emptyList())

        val result = exporter.export()

        assertThat(result).isInstanceOf(ExportResult.EmptyBuffer::class.java)
    }

    // ── single-chunk fast-path ────────────────────────────────────────────────

    @Test
    fun `export returns Success with file URI for single chunk`() = runTest {
        val chunkFile = tmpFolder.newFile("0.aac").also { it.writeBytes(ByteArray(100)) }
        val chunk = chunk(startMs = 0, endMs = 30_000, filePath = chunkFile.absolutePath)
        coEvery { repo.allChunks } returns flowOf(listOf(chunk))

        val result = exporter.export()

        assertThat(result).isInstanceOf(ExportResult.Success::class.java)
    }

    // ── output file location ──────────────────────────────────────────────────

    @Test
    fun `export writes output file inside cacheDir exports directory`() = runTest {
        val chunkFile = tmpFolder.newFile("0.aac").also { it.writeBytes(ByteArray(100)) }
        coEvery { repo.allChunks } returns flowOf(listOf(
            chunk(0, 30_000, chunkFile.absolutePath)
        ))

        val result = exporter.export() as ExportResult.Success
        assertThat(result.outputFile.parentFile?.name).isEqualTo("exports")
        assertThat(result.outputFile.parentFile?.parentFile?.absolutePath)
            .isEqualTo(cacheDir.absolutePath)
    }

    // ── multi-chunk delegates to muxer ────────────────────────────────────────

    @Test
    fun `export delegates to muxer when more than one chunk exists`() = runTest {
        val file1 = tmpFolder.newFile("0.aac").also { it.writeBytes(ByteArray(100)) }
        val file2 = tmpFolder.newFile("30000.aac").also { it.writeBytes(ByteArray(100)) }
        val chunks = listOf(
            chunk(0, 30_000, file1.absolutePath),
            chunk(30_000, 60_000, file2.absolutePath),
        )
        coEvery { repo.allChunks } returns flowOf(chunks)

        val outputFile = File(exportDir, "out.aac")
        coEvery { muxerDelegate.mux(any(), any()) } returns outputFile

        val result = exporter.export()

        assertThat(result).isInstanceOf(ExportResult.Success::class.java)
        io.mockk.coVerify { muxerDelegate.mux(any(), any()) }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun chunk(startMs: Long, endMs: Long, filePath: String = "/buf/$startMs.aac") =
        ChunkEntity(startMs = startMs, endMs = endMs, filePath = filePath, sizeBytes = 240_000L)
}
