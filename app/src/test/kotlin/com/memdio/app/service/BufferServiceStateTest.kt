package com.memdio.app.service

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * RED → GREEN: Verifies recording-state transitions exposed by the service.
 *
 * Tests operate against [RecordingState] directly — no Android context needed.
 */
class BufferServiceStateTest {

    @Test
    fun `RecordingState Idle has isRecording false`() {
        val state = RecordingState.Idle
        assertThat(state.isRecording).isFalse()
    }

    @Test
    fun `RecordingState Active has isRecording true`() {
        val state = RecordingState.Active(bufferDurationMs = 60_000L, storageBytesUsed = 480_000L)
        assertThat(state.isRecording).isTrue()
    }

    @Test
    fun `state flow transitions from Idle to Active then back`() = runTest {
        val flow = MutableStateFlow<RecordingState>(RecordingState.Idle)

        flow.test {
            assertThat(awaitItem()).isEqualTo(RecordingState.Idle)

            flow.value = RecordingState.Active(bufferDurationMs = 30_000L, storageBytesUsed = 240_000L)
            val active = awaitItem() as RecordingState.Active
            assertThat(active.isRecording).isTrue()
            assertThat(active.bufferDurationMs).isEqualTo(30_000L)

            flow.value = RecordingState.Idle
            assertThat(awaitItem()).isEqualTo(RecordingState.Idle)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Active state bufferDurationMs and storageBytesUsed are preserved`() {
        val state = RecordingState.Active(bufferDurationMs = 3_600_000L, storageBytesUsed = 28_000_000L)
        assertThat(state.bufferDurationMs).isEqualTo(3_600_000L)
        assertThat(state.storageBytesUsed).isEqualTo(28_000_000L)
    }
}
