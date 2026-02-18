package com.memdio.app.export

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concatenates a list of AAC ADTS files into a single AAC file using
 * [MediaExtractor] + [MediaMuxer] (lossless, no re-encoding).
 *
 * Extracted as an interface so [BufferExporter] can be tested without NDK codecs.
 */
interface AacMuxerDelegate {
    /**
     * Muxes [inputFiles] into [outputFile] in order.
     * @return the [outputFile] on success
     * @throws Exception on any muxing failure
     */
    suspend fun mux(inputFiles: List<File>, outputFile: File): File
}

@Singleton
class RealAacMuxerDelegate @Inject constructor() : AacMuxerDelegate {

    override suspend fun mux(inputFiles: List<File>, outputFile: File): File {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxTrackIndex = -1
        var muxStarted = false
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val bufferInfo = MediaCodec.BufferInfo()
        var presentationOffsetUs = 0L

        try {
            for (inputFile in inputFiles) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(inputFile.absolutePath)

                    // Find the first audio track
                    var extractorTrackIndex = -1
                    for (i in 0 until extractor.trackCount) {
                        val fmt = extractor.getTrackFormat(i)
                        if (fmt.getString(android.media.MediaFormat.KEY_MIME)
                                ?.startsWith("audio/") == true
                        ) {
                            extractorTrackIndex = i
                            break
                        }
                    }
                    if (extractorTrackIndex < 0) continue // skip non-audio files

                    extractor.selectTrack(extractorTrackIndex)
                    val format = extractor.getTrackFormat(extractorTrackIndex)

                    if (muxTrackIndex < 0) {
                        muxTrackIndex = muxer.addTrack(format)
                        muxer.start()
                        muxStarted = true
                    }

                    var lastPresentationUs = 0L
                    while (true) {
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break

                        bufferInfo.apply {
                            this.size = size
                            this.offset = 0
                            this.presentationTimeUs = extractor.sampleTime + presentationOffsetUs
                            this.flags = extractor.sampleFlags.toCodecBufferFlags()
                        }
                        lastPresentationUs = extractor.sampleTime
                        muxer.writeSampleData(muxTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                    // Advance the offset so the next file continues from where this one ended
                    presentationOffsetUs += lastPresentationUs
                } finally {
                    extractor.release()
                }
            }
        } finally {
            if (muxStarted) muxer.stop()
            muxer.release()
        }

        return outputFile
    }

    companion object {
        private const val BUFFER_SIZE = 1024 * 1024 // 1 MB read buffer

        /**
         * Maps [MediaExtractor] sample flags to [MediaCodec] buffer flags.
         *
         * The two flag namespaces have distinct numeric values so a direct assignment
         * would fail [android.media.MediaCodec.BufferInfo.flags]'s @IntDef lint check.
         *  - SAMPLE_FLAG_SYNC (1)          → BUFFER_FLAG_KEY_FRAME (1)
         *  - SAMPLE_FLAG_PARTIAL_FRAME (4) → BUFFER_FLAG_PARTIAL_FRAME (8)
         * SAMPLE_FLAG_ENCRYPTED has no public BufferInfo counterpart and is skipped.
         */
        private fun Int.toCodecBufferFlags(): Int {
            var flags = 0
            if (this and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            if (this and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME != 0)
                flags = flags or MediaCodec.BUFFER_FLAG_PARTIAL_FRAME
            return flags
        }
    }
}
