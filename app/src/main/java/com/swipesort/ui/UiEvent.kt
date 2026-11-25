package com.swipesort.ui

/** A one-off message for the snackbar. If [undo] is set, the snackbar shows an "Undo" button. */
data class UiEvent(val message: String, val undo: (() -> Unit)? = null)

/** A move/copy in progress, for the progress banner. */
data class Transfer(val fileName: String, val folderName: String, val copying: Boolean, val progress: Float)
