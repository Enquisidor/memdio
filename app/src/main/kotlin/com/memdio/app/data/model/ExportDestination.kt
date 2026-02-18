package com.memdio.app.data.model

/** Where the exported audio file is sent after the buffer is concatenated. */
enum class ExportDestination {
    /** System share-sheet — always available, no auth required. */
    SHARE_SHEET,

    /** Google Drive — requires user sign-in via GoogleSignIn. */
    GOOGLE_DRIVE,

    /** Dropbox — requires PKCE OAuth flow. */
    DROPBOX,

    /** SAF-picked folder in the user-visible file system. */
    LOCAL_FILES,
}
