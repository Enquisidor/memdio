package com.memdio.app.export

import android.content.Context
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.oauth.DbxCredential
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.WriteMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

sealed class DropboxUploadResult {
    data class Success(val remotePath: String) : DropboxUploadResult()
    data class Failure(val cause: Throwable) : DropboxUploadResult()
}

interface DropboxUploadClient {
    suspend fun upload(file: File): DropboxUploadResult
    fun isAuthenticated(context: Context): Boolean
}

@Singleton
class RealDropboxUploadClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : DropboxUploadClient {

    private val requestConfig = DbxRequestConfig.newBuilder("Memdio/1.0").build()

    override suspend fun upload(file: File): DropboxUploadResult = withContext(Dispatchers.IO) {
        runCatching {
            val credential = loadCredential()
                ?: return@runCatching DropboxUploadResult.Failure(
                    IllegalStateException("Not authenticated with Dropbox")
                )

            val client = DbxClientV2(requestConfig, credential)
            val remotePath = "/Memdio/${file.name}"

            FileInputStream(file).use { stream ->
                client.files()
                    .uploadBuilder(remotePath)
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(stream)
            }
            DropboxUploadResult.Success(remotePath)
        }.getOrElse { DropboxUploadResult.Failure(it) }
    }

    override fun isAuthenticated(context: Context): Boolean = loadCredential() != null

    private fun loadCredential(): DbxCredential? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = prefs.getString(KEY_CREDENTIAL, null) ?: return null
        return runCatching { DbxCredential.Reader.readFully(serialized) }.getOrNull()
    }

    companion object {
        const val PREFS_NAME = "dropbox_prefs"
        const val KEY_CREDENTIAL = "credential"
        // The app key is embedded at build time; no client secret is needed for PKCE.
        const val APP_KEY = "YOUR_DROPBOX_APP_KEY"
    }
}
