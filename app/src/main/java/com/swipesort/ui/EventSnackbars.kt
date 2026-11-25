package com.swipesort.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Shows a ViewModel's [UiEvent]s as snackbars. A new event replaces the current snackbar right away
 * (so rapid swiping never builds a queue), and tapping "Undo" runs that event's own undo.
 */
@Composable
fun EventSnackbars(events: Flow<UiEvent>, host: SnackbarHostState) {
    LaunchedEffect(events, host) {
        events.collect { e ->
            host.currentSnackbarData?.dismiss()
            launch {
                val result = host.showSnackbar(
                    message = e.message,
                    actionLabel = if (e.undo != null) "Undo" else null,
                    withDismissAction = e.undo == null,
                    duration = if (e.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) e.undo?.invoke()
            }
        }
    }
}
