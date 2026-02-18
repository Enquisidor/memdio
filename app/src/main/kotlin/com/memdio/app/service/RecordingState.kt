package com.memdio.app.service

/** Observable state emitted by [BufferService] to the UI layer. */
sealed class RecordingState {

    abstract val isRecording: Boolean

    /** Service is not running or mic is paused. */
    data object Idle : RecordingState() {
        override val isRecording: Boolean = false
    }

    /**
     * Service is writing audio chunks to disk.
     * @param bufferDurationMs total duration of chunks currently stored
     * @param storageBytesUsed total disk space occupied by chunks
     */
    data class Active(
        val bufferDurationMs: Long,
        val storageBytesUsed: Long,
    ) : RecordingState() {
        override val isRecording: Boolean = true
    }
}
