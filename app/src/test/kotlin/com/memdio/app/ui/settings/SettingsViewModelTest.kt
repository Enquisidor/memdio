package com.memdio.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.memdio.app.data.model.ExportDestination
import com.memdio.app.data.repository.SettingsRepository
import com.memdio.app.export.BufferExporter
import com.memdio.app.export.DispatchResult
import com.memdio.app.export.ExportDispatcher
import com.memdio.app.export.ExportResult
import com.memdio.app.service.BufferService
import com.memdio.app.service.RecordingState
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * RED → GREEN: [SettingsViewModel] state transitions and export flow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val instantTaskRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private val context: Context = mockk(relaxed = true)
    private val settingsRepo: SettingsRepository = mockk()
    private val exporter: BufferExporter = mockk()
    private val dispatcher: ExportDispatcher = mockk()

    private val serviceStateFlow = MutableStateFlow<RecordingState>(RecordingState.Idle)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        coEvery { settingsRepo.bufferDurationMinutes } returns flowOf(60)
        coEvery { settingsRepo.audioBitrate } returns flowOf(64_000)
        coEvery { settingsRepo.startOnBoot } returns flowOf(false)
        coEvery { settingsRepo.exportDestination } returns flowOf(ExportDestination.SHARE_SHEET)
        coEvery { settingsRepo.cloudAutoExport } returns flowOf(false)
        coEvery { settingsRepo.driveAccountEmail } returns flowOf(null)

        mockkObject(BufferService.Companion)
        every { BufferService.state } returns serviceStateFlow

        viewModel = SettingsViewModel(context, settingsRepo, exporter, dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // ── isRecording reflects service state ────────────────────────────────────

    @Test
    fun `isRecording is false when service is Idle`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.isRecording.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isRecording is true when service is Active`() = runTest {
        serviceStateFlow.value = RecordingState.Active(bufferDurationMs = 30_000, storageBytesUsed = 240_000)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.isRecording.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── bufferDurationMs ─────────────────────────────────────────────────────

    @Test
    fun `bufferDurationMs returns 0 when idle`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.bufferDurationMs.test {
            assertThat(awaitItem()).isEqualTo(0L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `bufferDurationMs reflects active state`() = runTest {
        serviceStateFlow.value = RecordingState.Active(bufferDurationMs = 120_000L, storageBytesUsed = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.bufferDurationMs.test {
            assertThat(awaitItem()).isEqualTo(120_000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── export flow ───────────────────────────────────────────────────────────

    @Test
    fun `exportState starts as Idle`() = runTest {
        viewModel.exportState.test {
            assertThat(awaitItem()).isEqualTo(ExportState.Idle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `triggerExport sets state to Exporting then Done on success`() = runTest {
        val outputFile = File("/cache/exports/1234.aac")
        coEvery { exporter.export() } returns ExportResult.Success(outputFile)
        val shareIntent: android.content.Intent = mockk()
        coEvery { dispatcher.dispatch(any(), any()) } returns DispatchResult.ShareSheetLaunched(shareIntent)

        viewModel.exportState.test {
            assertThat(awaitItem()).isEqualTo(ExportState.Idle)

            viewModel.triggerExport()
            testDispatcher.scheduler.advanceUntilIdle()

            val exporting = awaitItem()
            assertThat(exporting).isInstanceOf(ExportState.Exporting::class.java)

            val done = awaitItem()
            assertThat(done).isInstanceOf(ExportState.Done::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `triggerExport sets state to Error when export fails`() = runTest {
        coEvery { exporter.export() } returns ExportResult.Failure(RuntimeException("disk full"))

        viewModel.exportState.test {
            awaitItem() // Idle
            viewModel.triggerExport()
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() // Exporting
            val error = awaitItem()
            assertThat(error).isInstanceOf(ExportState.Error::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `triggerExport sets EmptyBuffer state when buffer is empty`() = runTest {
        coEvery { exporter.export() } returns ExportResult.EmptyBuffer

        viewModel.exportState.test {
            awaitItem() // Idle
            viewModel.triggerExport()
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() // Exporting
            val state = awaitItem()
            assertThat(state).isEqualTo(ExportState.EmptyBuffer)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── settings delegation ───────────────────────────────────────────────────

    @Test
    fun `setBufferDurationMinutes delegates to repository`() = runTest {
        coJustRun { settingsRepo.setBufferDurationMinutes(90) }
        viewModel.setBufferDurationMinutes(90)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { settingsRepo.setBufferDurationMinutes(90) }
    }

    @Test
    fun `setExportDestination delegates to repository`() = runTest {
        coJustRun { settingsRepo.setExportDestination(ExportDestination.GOOGLE_DRIVE) }
        viewModel.setExportDestination(ExportDestination.GOOGLE_DRIVE)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { settingsRepo.setExportDestination(ExportDestination.GOOGLE_DRIVE) }
    }
}
