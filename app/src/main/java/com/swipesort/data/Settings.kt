package com.swipesort.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small persisted preferences, exposed as StateFlows for Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("swipesort", Context.MODE_PRIVATE)

    private fun flag(key: String, default: Boolean) = MutableStateFlow(prefs.getBoolean(key, default))

    private val _grid = flag("grid", true)
    val grid: StateFlow<Boolean> = _grid.asStateFlow()
    fun setGrid(v: Boolean) { _grid.value = v; prefs.edit().putBoolean("grid", v).apply() }

    /** Show "Delete?" before a left swipe takes effect (otherwise: instant + Undo). */
    private val _confirmDelete = flag("confirmDelete", false)
    val confirmDelete: StateFlow<Boolean> = _confirmDelete.asStateFlow()
    fun setConfirmDelete(v: Boolean) { _confirmDelete.value = v; prefs.edit().putBoolean("confirmDelete", v).apply() }

    /** Default for the "Keep original (copy instead of move)" switch in the folder sheet. */
    private val _copyByDefault = flag("copyByDefault", false)
    val copyByDefault: StateFlow<Boolean> = _copyByDefault.asStateFlow()
    fun setCopyByDefault(v: Boolean) { _copyByDefault.value = v; prefs.edit().putBoolean("copyByDefault", v).apply() }

    private val _swipeHintSeen = flag("swipeHintSeen", false)
    val swipeHintSeen: StateFlow<Boolean> = _swipeHintSeen.asStateFlow()
    fun setSwipeHintSeen() { _swipeHintSeen.value = true; prefs.edit().putBoolean("swipeHintSeen", true).apply() }
}
