package com.memdio.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import com.memdio.app.data.model.ExportDestination
import com.memdio.app.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * RED → GREEN: [ExportDispatcher] routes an exported file to the correct backend.
 *
 * Integration with actual cloud APIs is tested via androidTest / manual QA.
 * Here we verify routing decisions only — each cloud client is mocked.
 */
class ExportDispatcherTest {

    private val context: Context = mockk(relaxed = true)
    private val settingsRepo: SettingsRepository = mockk()
    private val driveClient: DriveUploadClient = mockk(relaxed = true)
    private val dropboxClient: DropboxUploadClient = mockk(relaxed = true)

    private lateinit var dispatcher: ExportDispatcher
    private val outputFile = File("/cache/exports/12345.aac")
    private val shareUri: Uri = mockk()

    @Before
    fun setUp() {
        dispatcher = ExportDispatcher(context, settingsRepo, driveClient, dropboxClient)
    }

    // ── SHARE_SHEET ──────────────────────────────────────────────────────────

    @Test
    fun `dispatch triggers share sheet when destination is SHARE_SHEET`() = runTest {
        coEvery { settingsRepo.exportDestination } returns flowOf(ExportDestination.SHARE_SHEET)

        val result = dispatcher.dispatch(outputFile, shareUri)

        assertThat(result).isInstanceOf(DispatchResult.ShareSheetLaunched::class.java)
    }

    // ── GOOGLE_DRIVE ─────────────────────────────────────────────────────────

    @Test
    fun `dispatch delegates to DriveUploadClient when destination is GOOGLE_DRIVE`() = runTest {
        coEvery { settingsRepo.exportDestination } returns flowOf(ExportDestination.GOOGLE_DRIVE)
        coEvery { driveClient.upload(outputFile) } returns DriveUploadResult.Success("fileId123")

        val result = dispatcher.dispatch(outputFile, shareUri)

        coVerify { driveClient.upload(outputFile) }
        assertThat(result).isInstanceOf(DispatchResult.CloudUploadSuccess::class.java)
    }

    @Test
    fun `dispatch returns CloudUploadFailure when Drive upload fails`() = runTest {
        coEvery { settingsRepo.exportDestination } returns flowOf(ExportDestination.GOOGLE_DRIVE)
        val error = RuntimeException("no network")
        coEvery { driveClient.upload(outputFile) } returns DriveUploadResult.Failure(error)

        val result = dispatcher.dispatch(outputFile, shareUri)

        assertThat(result).isInstanceOf(DispatchResult.CloudUploadFailure::class.java)
        assertThat((result as DispatchResult.CloudUploadFailure).cause).isEqualTo(error)
    }

    // ── DROPBOX ──────────────────────────────────────────────────────────────

    @Test
    fun `dispatch delegates to DropboxUploadClient when destination is DROPBOX`() = runTest {
        coEvery { settingsRepo.exportDestination } returns flowOf(ExportDestination.DROPBOX)
        coEvery { dropboxClient.upload(outputFile) } returns DropboxUploadResult.Success("/Memdio/12345.aac")

        val result = dispatcher.dispatch(outputFile, shareUri)

        coVerify { dropboxClient.upload(outputFile) }
        assertThat(result).isInstanceOf(DispatchResult.CloudUploadSuccess::class.java)
    }

    // ── LOCAL_FILES ──────────────────────────────────────────────────────────

    @Test
    fun `dispatch returns LocalSaveRequested when destination is LOCAL_FILES`() = runTest {
        coEvery { settingsRepo.exportDestination } returns flowOf(ExportDestination.LOCAL_FILES)

        val result = dispatcher.dispatch(outputFile, shareUri)

        assertThat(result).isInstanceOf(DispatchResult.LocalSaveRequested::class.java)
    }
}
