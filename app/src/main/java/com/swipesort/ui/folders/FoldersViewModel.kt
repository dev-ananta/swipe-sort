package com.swipesort.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swipesort.SwipeSortApp
import com.swipesort.data.FileRepository
import com.swipesort.data.Settings
import com.swipesort.domain.FileItem
import com.swipesort.domain.SortFolder
import com.swipesort.domain.TrashEntry
import com.swipesort.ui.Transfer
import com.swipesort.ui.UiEvent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The "Folders" tab: the user's collections, what's inside each, and Recently deleted. */
class FoldersViewModel(private val repo: FileRepository, val settings: Settings) : ViewModel() {

    val folders: StateFlow<List<SortFolder>> = repo.folders
    val trash: StateFlow<List<TrashEntry>> = repo.trashEntries

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // ---- Inside one folder -----------------------------------------------------------------
    private var current: SortFolder? = null
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _moveTarget = MutableStateFlow<FileItem?>(null)
    val moveTarget: StateFlow<FileItem?> = _moveTarget.asStateFlow()

    private val _transfer = MutableStateFlow<Transfer?>(null)
    val transfer: StateFlow<Transfer?> = _transfer.asStateFlow()

    fun refresh() = viewModelScope.launch { repo.refreshFolders(); repo.refreshTrash() }

    fun open(folder: SortFolder) {
        if (current?.path != folder.path) _files.value = emptyList()
        current = folder
        reload()
    }

    private fun reload() {
        val f = current ?: return
        viewModelScope.launch {
            _loading.value = true
            _files.value = runCatching { repo.listFolder(f) }.getOrDefault(emptyList())
            _loading.value = false
        }
    }

    // ---- Folder management -----------------------------------------------------------------

    fun create(name: String) = viewModelScope.launch {
        runCatching { repo.createFolder(name) }
            .onSuccess { _events.send(UiEvent("Created ${it.name}")) }
            .onFailure { _events.send(UiEvent(it.message ?: "Couldn't create the folder")) }
    }

    fun rename(folder: SortFolder, newName: String, onRenamed: (SortFolder) -> Unit = {}) = viewModelScope.launch {
        runCatching { repo.renameFolder(folder, newName) }
            .onSuccess {
                val renamed = repo.folders.value.firstOrNull { it.name == newName.trim() }
                if (renamed != null) { if (current?.path == folder.path) current = renamed; onRenamed(renamed) }
                reload()
            }
            .onFailure { _events.send(UiEvent(it.message ?: "Couldn't rename")) }
    }

    fun delete(folder: SortFolder) = viewModelScope.launch {
        runCatching { repo.deleteFolder(folder) }
            .onSuccess { n -> _events.send(UiEvent("Deleted ${folder.name}" + if (n > 0) " · $n files in Recently deleted" else "")) }
            .onFailure { _events.send(UiEvent("Couldn't delete: ${it.message}")) }
    }

    // ---- Swipes inside a folder (same gestures as the main list) ---------------------------

    fun deleteFile(item: FileItem) {
        _files.update { list -> list.filterNot { it.path == item.path } }
        viewModelScope.launch {
            runCatching { repo.trashFile(item.path, item.mime) }
                .onSuccess { entry ->
                    _events.send(UiEvent("Deleted ${item.name}") {
                        viewModelScope.launch { runCatching { repo.restore(entry) }; reload() }
                    })
                }
                .onFailure { reload(); _events.send(UiEvent("Couldn't delete: ${it.message}")) }
        }
    }

    fun onSwipeRight(item: FileItem) { if (_moveTarget.value == null) _moveTarget.value = item }
    fun dismissMoveSheet() { _moveTarget.value = null }

    fun moveTo(item: FileItem, folder: SortFolder, copy: Boolean) {
        _moveTarget.value = null
        if (!copy) _files.update { list -> list.filterNot { it.path == item.path } }
        viewModelScope.launch {
            _transfer.value = Transfer(item.name, folder.name, copy, 0f)
            runCatching { repo.moveToFolder(item.path, folder, copy) { p -> _transfer.value = Transfer(item.name, folder.name, copy, p) } }
                .onSuccess { newFile ->
                    _events.send(UiEvent((if (copy) "Copied to " else "Moved to ") + folder.name) {
                        viewModelScope.launch {
                            runCatching { if (copy) repo.discardCopy(newFile.absolutePath) else repo.moveBack(newFile, item.path) }
                            reload()
                        }
                    })
                }
                .onFailure { reload(); _events.send(UiEvent("Couldn't move: ${it.message}")) }
            _transfer.value = null
        }
    }

    fun createFolderAndMove(item: FileItem, name: String, copy: Boolean) = viewModelScope.launch {
        runCatching { repo.createFolder(name) }
            .onSuccess { moveTo(item, it, copy) }
            .onFailure { _events.send(UiEvent(it.message ?: "Couldn't create the folder")) }
    }

    // ---- Recently deleted ------------------------------------------------------------------

    fun restore(entry: TrashEntry) = viewModelScope.launch {
        runCatching { repo.restore(entry) }
            .onSuccess { _events.send(UiEvent("Restored ${entry.name}")) }
            .onFailure { _events.send(UiEvent("Couldn't restore: ${it.message}")) }
    }

    fun deleteForever(entry: TrashEntry) = viewModelScope.launch { repo.deleteForever(entry) }

    fun emptyTrash() = viewModelScope.launch {
        val n = repo.emptyTrash()
        _events.send(UiEvent("Permanently deleted $n ${if (n == 1) "item" else "items"}"))
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SwipeSortApp
                FoldersViewModel(app.repository, app.settings)
            }
        }
    }
}
