package com.memdio.app.export

import android.content.Context
import android.net.Uri
import com.memdio.app.data.model.ExportDestination
import com.memdio.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What happened after [ExportDispatcher.dispatch]. */
sealed class DispatchResult {
    /** Share-sheet intent is ready; the Activity must launch it. */
    data class ShareSheetLaunched(val intent: android.content.Intent) : DispatchResult()
    data class CloudUploadSuccess(val remoteId: String) : DispatchResult()
    data class CloudUploadFailure(val cause: Throwable) : DispatchResult()
    /** Activity must launch SAF CreateDocument contract with [mimeType]. */
    data class LocalSaveRequested(val sourceFile: File) : DispatchResult()
}

/**
 * Routes a completed export file to the destination configured in [SettingsRepository].
 *
 * ExportDispatcher knows nothing about audio — it only routes URIs and files.
 */
@Singleton
class ExportDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
    private val driveClient: DriveUploadClient,
    private val dropboxClient: DropboxUploadClient,
) {

    suspend fun dispatch(outputFile: File, fileUri: Uri): DispatchResult {
        return when (settingsRepo.exportDestination.first()) {
            ExportDestination.SHARE_SHEET -> launchShareSheet(fileUri)
            ExportDestination.GOOGLE_DRIVE -> uploadToDrive(outputFile)
            ExportDestination.DROPBOX -> uploadToDropbox(outputFile)
            ExportDestination.LOCAL_FILES -> DispatchResult.LocalSaveRequested(outputFile)
        }
    }

    private fun launchShareSheet(fileUri: Uri): DispatchResult {
        val intent = androidx.core.app.ShareCompat.IntentBuilder(context)
            .setType("audio/aac")
            .setStream(fileUri)
            .setChooserTitle("Export buffer")
            .createChooserIntent()
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        return DispatchResult.ShareSheetLaunched(intent)
    }

    private suspend fun uploadToDrive(file: File): DispatchResult =
        when (val result = driveClient.upload(file)) {
            is DriveUploadResult.Success -> DispatchResult.CloudUploadSuccess(result.fileId)
            is DriveUploadResult.Failure -> DispatchResult.CloudUploadFailure(result.cause)
        }

    private suspend fun uploadToDropbox(file: File): DispatchResult =
        when (val result = dropboxClient.upload(file)) {
            is DropboxUploadResult.Success -> DispatchResult.CloudUploadSuccess(result.remotePath)
            is DropboxUploadResult.Failure -> DispatchResult.CloudUploadFailure(result.cause)
        }
}
