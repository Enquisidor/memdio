package com.memdio.app.ui.settings

import android.content.Intent

/** UI-observable state for the export button. */
sealed class ExportState {
    data object Idle : ExportState()
    data object Exporting : ExportState()
    data object EmptyBuffer : ExportState()
    /** Share sheet ready to show, or cloud upload succeeded. */
    data class Done(val shareIntent: Intent? = null) : ExportState()
    data class Error(val message: String) : ExportState()
}
