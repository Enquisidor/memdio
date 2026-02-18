package com.memdio.app.export

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.memdio.app.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed class DriveUploadResult {
    data class Success(val fileId: String) : DriveUploadResult()
    data class Failure(val cause: Throwable) : DriveUploadResult()
}

interface DriveUploadClient {
    suspend fun upload(file: File): DriveUploadResult
}

@Singleton
class RealDriveUploadClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository,
) : DriveUploadClient {

    override suspend fun upload(file: File): DriveUploadResult = withContext(Dispatchers.IO) {
        runCatching {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@runCatching DriveUploadResult.Failure(
                    IllegalStateException("Not signed in to Google")
                )

            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_FILE)
            ).also { it.selectedAccount = account.account }

            val service = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
                .setApplicationName("Memdio")
                .build()

            val folderId = getOrCreateMemdioFolder(service)
            val metadata = DriveFile().apply {
                name = file.name
                parents = listOf(folderId)
            }
            val content = FileContent("audio/aac", file)
            val uploaded = service.files().create(metadata, content)
                .setFields("id")
                .execute()

            DriveUploadResult.Success(uploaded.id)
        }.getOrElse { DriveUploadResult.Failure(it) }
    }

    /**
     * Returns the ID of the "Memdio" folder in Drive, creating it if absent.
     * We cache nothing — Drive API calls are fast and the folder rarely changes.
     */
    private fun getOrCreateMemdioFolder(service: Drive): String {
        val query = "mimeType='application/vnd.google-apps.folder' and name='Memdio' and trashed=false"
        val list = service.files().list().setQ(query).setFields("files(id)").execute()
        return list.files.firstOrNull()?.id ?: run {
            val folder = DriveFile().apply {
                name = "Memdio"
                mimeType = "application/vnd.google-apps.folder"
            }
            service.files().create(folder).setFields("id").execute().id
        }
    }
}
