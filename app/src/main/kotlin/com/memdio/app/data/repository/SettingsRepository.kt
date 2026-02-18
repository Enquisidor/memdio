package com.memdio.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.memdio.app.data.model.ExportDestination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    // ── Keys ─────────────────────────────────────────────────────────────────

    private object Keys {
        val BUFFER_DURATION_MINUTES = intPreferencesKey("buffer_duration_minutes")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val EXPORT_DESTINATION = stringPreferencesKey("export_destination")
        val CLOUD_AUTO_EXPORT = booleanPreferencesKey("cloud_auto_export")
        val DRIVE_ACCOUNT_EMAIL = stringPreferencesKey("drive_account_email")
    }

    // ── Flows ────────────────────────────────────────────────────────────────

    val bufferDurationMinutes: Flow<Int> = dataStore.data
        .map { it[Keys.BUFFER_DURATION_MINUTES] ?: DEFAULT_BUFFER_MINUTES }

    val audioBitrate: Flow<Int> = dataStore.data
        .map { it[Keys.AUDIO_BITRATE] ?: DEFAULT_BITRATE }

    val startOnBoot: Flow<Boolean> = dataStore.data
        .map { it[Keys.START_ON_BOOT] ?: false }

    val exportDestination: Flow<ExportDestination> = dataStore.data
        .map {
            it[Keys.EXPORT_DESTINATION]
                ?.let { name -> ExportDestination.valueOf(name) }
                ?: ExportDestination.SHARE_SHEET
        }

    val cloudAutoExport: Flow<Boolean> = dataStore.data
        .map { it[Keys.CLOUD_AUTO_EXPORT] ?: false }

    /** Null when no Google account is connected. */
    val driveAccountEmail: Flow<String?> = dataStore.data
        .map { it[Keys.DRIVE_ACCOUNT_EMAIL] }

    // ── Writes ───────────────────────────────────────────────────────────────

    suspend fun setBufferDurationMinutes(minutes: Int) {
        dataStore.edit { it[Keys.BUFFER_DURATION_MINUTES] = minutes }
    }

    suspend fun setAudioBitrate(bitrate: Int) {
        dataStore.edit { it[Keys.AUDIO_BITRATE] = bitrate }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        dataStore.edit { it[Keys.START_ON_BOOT] = enabled }
    }

    suspend fun setExportDestination(destination: ExportDestination) {
        dataStore.edit { it[Keys.EXPORT_DESTINATION] = destination.name }
    }

    suspend fun setCloudAutoExport(enabled: Boolean) {
        dataStore.edit { it[Keys.CLOUD_AUTO_EXPORT] = enabled }
    }

    suspend fun setDriveAccountEmail(email: String) {
        dataStore.edit { it[Keys.DRIVE_ACCOUNT_EMAIL] = email }
    }

    suspend fun clearDriveAccount() {
        dataStore.edit { it.remove(Keys.DRIVE_ACCOUNT_EMAIL) }
    }

    companion object {
        const val DEFAULT_BUFFER_MINUTES = 60
        const val DEFAULT_BITRATE = 64_000
    }
}
