package com.swipesort.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.swipesort.SwipeSortApp
import com.swipesort.data.FileRepository
import com.swipesort.data.FilesPagingSource
import com.swipesort.data.Settings
import com.swipesort.domain.FileFilter
import com.swipesort.domain.FileItem
import com.swipesort.domain.FileQuery
import com.swipesort.domain.SortFolder
import com.swipesort.domain.SortOrder
import com.swipesort.ui.Transfer
import com.swipesort.ui.UiEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * The main "Files" screen: a paged, filterable, searchable list of every file, plus the swipe
 * actions. A swiped item is hidden from the list immediately (optimistic UI), the file operation
 * runs in the background, and the snackbar offers Undo.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class BrowseViewModel(private val repo: FileRepository, val settings: Settings) : ViewModel() {

    val filter = MutableStateFlow(FileFilter.ALL)
    val search = MutableStateFlow("")
    val sort = MutableStateFlow(SortOrder.NEWEST)
    val folders: StateFlow<List<SortFolder>> = repo.folders

    /** Items already swiped away; hidden at once, before MediaStore catches up. */
    private val hidden = MutableStateFlow<Set<Long>>(emptySet())
    private var source: FilesPagingSource? = null

    private val query = combine(filter, search.debounce(300), sort) { f, s, o -> FileQuery(f, s, o) }
        .distinctUntilChanged()

    val files: Flow<PagingData<FileItem>> = query
        .flatMapLatest { q ->
            Pager(
                config = PagingConfig(
                    pageSize = FilesPagingSource.PAGE,
                    initialLoadSize = FilesPagingSource.PAGE,
                    prefetchDistance = FilesPagingSource.PAGE / 2,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = { FilesPagingSource(repo, q).also { source = it } },
            ).flow
        }
        .cachedIn(viewModelScope)
        .combine(hidden) { data, h -> if (h.isEmpty()) data else data.filter { it.id !in h } }

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** The file whose "Add to folder" sheet is open (right swipe). */
    private val _moveTarget = MutableStateFlow<FileItem?>(null)
    val moveTarget: StateFlow<FileItem?> = _moveTarget.asStateFlow()

    /** The file waiting for "Delete?" confirmation (only when that setting is on). */
    private val _pendingDelete = MutableStateFlow<FileItem?>(null)
    val pendingDelete: StateFlow<FileItem?> = _pendingDelete.asStateFlow()

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer.asStateFlow()

    init {
        viewModelScope.launch { repo.startup() }
        // Anything that changes files (here or on the Folders tab) re-queries the list.
        viewModelScope.launch { repo.version.drop(1).collect { refresh() } }
    }

    fun refresh() { source?.invalidate() }

    // ---- Left swipe: delete -----------------------------------------------------------------

    /** Left swipe. Returns true if the row should slide away now (false = snap back). */
    fun onSwipeLeft(item: FileItem): Boolean {
        if (item.id in hidden.value) return true
        return if (settings.confirmDelete.value) {
            _pendingDelete.value = item
            false
        } else {
            delete(item); true
        }
    }

    fun confirmPendingDelete() { _pendingDelete.value?.let { delete(it) }; _pendingDelete.value = null }
    fun cancelPendingDelete() { _pendingDelete.value = null }

    fun delete(item: FileItem) {
        hidden.update { it + item.id }
        viewModelScope.launch {
            runCatching { repo.trashFile(item.path, item.mime) }
                .onSuccess { entry ->
                    _events.send(UiEvent("Deleted ${item.name}") {
                        viewModelScope.launch {
                            runCatching { repo.restore(entry) }
                                .onSuccess { hidden.update { it - item.id } }
                                .onFailure { _events.send(UiEvent("Couldn't restore: ${it.message}")) }
                        }
                    })
                }
                .onFailure {
                    hidden.update { h -> h - item.id }
                    _events.send(UiEvent("Couldn't delete: ${it.message}"))
                }
        }
    }

    // ---- Right swipe: add to folder ---------------------------------------------------------

    /** Right swipe: open the folder sheet. The row itself snaps back until a folder is chosen. */
    fun onSwipeRight(item: FileItem) {
        if (_moveTarget.value == null && item.id !in hidden.value) _moveTarget.value = item
    }

    fun dismissMoveSheet() { _moveTarget.value = null }

    fun moveTo(item: FileItem, folder: SortFolder, copy: Boolean) {
        _moveTarget.value = null
        hidden.update { it + item.id } // sorted files leave the main list
        viewModelScope.launch {
            _transfer.value = Transfer(item.name, folder.name, copy, 0f)
            runCatching {
                repo.moveToFolder(item.path, folder, copy) { p ->
                    _transfer.value = Transfer(item.name, folder.name, copy, p)
                }
            }.onSuccess { newFile ->
                _events.send(UiEvent((if (copy) "Copied to " else "Moved to ") + folder.name) { undoMove(item, newFile, copy) })
            }.onFailure {
                hidden.update { h -> h - item.id }
                _events.send(UiEvent("Couldn't ${if (copy) "copy" else "move"}: ${it.message}"))
            }
            _transfer.value = null
        }
    }

    /** "New folder" in the sheet: create it, then sort the file into it. */
    fun createFolderAndMove(item: FileItem, name: String, copy: Boolean) {
        viewModelScope.launch {
            runCatching { repo.createFolder(name) }
                .onSuccess { moveTo(item, it, copy) }
                .onFailure { _events.send(UiEvent(it.message ?: "Couldn't create the folder")) }
        }
    }

    private fun undoMove(item: FileItem, newFile: File, copied: Boolean) {
        viewModelScope.launch {
            runCatching { if (copied) repo.discardCopy(newFile.absolutePath) else repo.moveBack(newFile, item.path) }
                .onSuccess { hidden.update { it - item.id } }
                .onFailure { _events.send(UiEvent("Couldn't undo: ${it.message}")) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SwipeSortApp
                BrowseViewModel(app.repository, app.settings)
            }
        }
    }
}
