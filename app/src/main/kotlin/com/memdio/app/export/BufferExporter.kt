package com.memdio.app.export

import android.content.Context
import com.memdio.app.data.repository.ChunkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concatenates all current buffer chunks into a single exportable AAC file.
 *
 * V1: full-buffer only — no trimming.
 * Single-chunk optimisation: skips the muxer and copies the file directly
 * (avoids re-muxing the ADTS container for a single segment).
 *
 * Output is written to [Context.cacheDir]/exports/{timestamp}.aac and is
 * ephemeral — cleared on cache eviction or the next export.
 */
@Singleton
class BufferExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: ChunkRepository,
    private val muxer: AacMuxerDelegate,
) {

    suspend fun export(): ExportResult {
        val chunks = repo.allChunks.first()
        if (chunks.isEmpty()) return ExportResult.EmptyBuffer

        val outputFile = prepareOutputFile()

        return runCatching {
            if (chunks.size == 1) {
                // Fast-path: single segment — just copy
                File(chunks.first().filePath).copyTo(outputFile, overwrite = true)
            } else {
                muxer.mux(chunks.map { File(it.filePath) }, outputFile)
            }
            ExportResult.Success(outputFile)
        }.getOrElse { ExportResult.Failure(it) }
    }

    private fun prepareOutputFile(): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        // Delete previous exports to avoid unbounded cache growth
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, "${System.currentTimeMillis()}.aac")
    }
}
