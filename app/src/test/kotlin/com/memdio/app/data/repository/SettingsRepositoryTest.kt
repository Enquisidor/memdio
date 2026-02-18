package com.memdio.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.memdio.app.data.model.ExportDestination
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * RED → GREEN: SettingsRepository reads/writes DataStore preferences.
 * Uses a real temporary DataStore file on JVM via Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tmpFolder.newFolder(), "settings.preferences_pb") }
        )
        repo = SettingsRepository(dataStore)
    }

    @After
    fun tearDown() {
        // DataStore keeps background coroutines alive inside testScope.
        // Cancelling here prevents UncompletedCoroutinesError after each test.
        testScope.cancel()
    }

    // ── bufferDurationMinutes ────────────────────────────────────────────────

    @Test
    fun `bufferDurationMinutes defaults to 60`() = testScope.runTest {
        repo.bufferDurationMinutes.test {
            assertThat(awaitItem()).isEqualTo(60)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setBufferDurationMinutes persists the value`() = testScope.runTest {
        repo.setBufferDurationMinutes(90)
        repo.bufferDurationMinutes.test {
            assertThat(awaitItem()).isEqualTo(90)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── audioBitrate ─────────────────────────────────────────────────────────

    @Test
    fun `audioBitrate defaults to 64000`() = testScope.runTest {
        repo.audioBitrate.test {
            assertThat(awaitItem()).isEqualTo(64_000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setAudioBitrate persists the value`() = testScope.runTest {
        repo.setAudioBitrate(128_000)
        repo.audioBitrate.test {
            assertThat(awaitItem()).isEqualTo(128_000)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── startOnBoot ──────────────────────────────────────────────────────────

    @Test
    fun `startOnBoot defaults to false`() = testScope.runTest {
        repo.startOnBoot.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setStartOnBoot persists the value`() = testScope.runTest {
        repo.setStartOnBoot(true)
        repo.startOnBoot.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── exportDestination ────────────────────────────────────────────────────

    @Test
    fun `exportDestination defaults to SHARE_SHEET`() = testScope.runTest {
        repo.exportDestination.test {
            assertThat(awaitItem()).isEqualTo(ExportDestination.SHARE_SHEET)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setExportDestination persists GOOGLE_DRIVE`() = testScope.runTest {
        repo.setExportDestination(ExportDestination.GOOGLE_DRIVE)
        repo.exportDestination.test {
            assertThat(awaitItem()).isEqualTo(ExportDestination.GOOGLE_DRIVE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── cloudAutoExport ──────────────────────────────────────────────────────

    @Test
    fun `cloudAutoExport defaults to false`() = testScope.runTest {
        repo.cloudAutoExport.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCloudAutoExport persists the value`() = testScope.runTest {
        repo.setCloudAutoExport(true)
        repo.cloudAutoExport.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── driveAccountEmail ────────────────────────────────────────────────────

    @Test
    fun `driveAccountEmail defaults to null`() = testScope.runTest {
        repo.driveAccountEmail.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDriveAccountEmail persists and clearDriveAccount nulls it`() = testScope.runTest {
        repo.setDriveAccountEmail("user@gmail.com")
        repo.driveAccountEmail.test {
            assertThat(awaitItem()).isEqualTo("user@gmail.com")
            cancelAndIgnoreRemainingEvents()
        }
        repo.clearDriveAccount()
        repo.driveAccountEmail.test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
