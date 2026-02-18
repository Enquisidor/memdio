package com.memdio.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memdio.app.data.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restarts [BufferService] after device reboot if the user enabled "Start on boot".
 * Also handles MY_PACKAGE_REPLACED so the service resumes after an app update.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        // goAsync() keeps the process alive while we check DataStore
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (settingsRepository.startOnBoot.first()) {
                    context.startForegroundService(BufferService.startIntent(context))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
