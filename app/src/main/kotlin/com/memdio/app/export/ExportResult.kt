package com.memdio.app.export

import java.io.File

/** Outcome of a [BufferExporter.export] call. */
sealed class ExportResult {

    /** Buffer has no chunks yet — disable the export button rather than showing an error. */
    data object EmptyBuffer : ExportResult()

    /** Concatenation succeeded; [outputFile] is in cacheDir and ready to share/upload. */
    data class Success(val outputFile: File) : ExportResult()

    /** An unexpected error occurred during muxing or I/O. */
    data class Failure(val cause: Throwable) : ExportResult()
}
