@file:OptIn(ExperimentalMaterial3Api::class)

package com.swipesort.ui.folders

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipesort.domain.FileItem
import com.swipesort.domain.Names
import com.swipesort.domain.SortFolder
import com.swipesort.domain.TrashEntry
import com.swipesort.ui.EventSnackbars
import com.swipesort.ui.components.CenterMessage
import com.swipesort.ui.components.ConfirmDialog
import com.swipesort.ui.components.FileCell
import com.swipesort.ui.components.FileRow
import com.swipesort.ui.components.FileThumbnail
import com.swipesort.ui.components.FolderCover
import com.swipesort.ui.components.FolderNameDialog
import com.swipesort.ui.components.FolderPickerSheet
import com.swipesort.ui.components.SwipeToSort
import com.swipesort.ui.components.TransferBanner
import com.swipesort.util.FileIntents
import java.io.File

/** "Folders" tab with its own small back stack: list → one folder, or list → Recently deleted. */
@Composable
fun FoldersTab(vm: FoldersViewModel, snackbar: SnackbarHostState) {
    var openPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showTrash by rememberSaveable { mutableStateOf(false) }
    val folders by vm.folders.collectAsStateWithLifecycle()

    EventSnackbars(vm.events, snackbar)
    LaunchedEffect(Unit) { vm.refresh() }
    BackHandler(enabled = showTrash || openPath != null) { showTrash = false; openPath = null }

    val open = folders.firstOrNull { it.path == openPath }
    when {
        showTrash -> TrashScreen(vm, onBack = { showTrash = false })
        open != null -> FolderDetailScreen(vm, open, onBack = { openPath = null }, onRenamed = { openPath = it.path })
        else -> FolderListScreen(vm, folders, onOpen = { openPath = it.path }, onTrash = { showTrash = true })
    }
}

// =============================================================================================
// All folders
// =============================================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderListScreen(vm: FoldersViewModel, folders: List<SortFolder>, onOpen: (SortFolder) -> Unit, onTrash: () -> Unit) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SortFolder?>(null) }
    var deleting by remember { mutableStateOf<SortFolder?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Folders") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.CreateNewFolder, null) },
                text = { Text("New folder") },
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    onClick = onTrash,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.DeleteSweep, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Recently deleted", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (trash.isEmpty()) "Empty" else "${trash.size} ${if (trash.size == 1) "item" else "items"} · ${Names.formatSize(trash.sumOf { it.size })}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (folders.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CenterMessage(
                        "No folders yet",
                        "Swipe right on any file in the Files tab to create a folder and sort it in — or tap New folder.",
                    )
                }
            }
            items(folders, key = { it.path }) { f ->
                var menu by remember { mutableStateOf(false) }
                Card(Modifier.combinedClickable(onClick = { onOpen(f) }, onLongClick = { menu = true })) {
                    FolderCover(f, Modifier.fillMaxWidth().aspectRatio(1.4f))
                    Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${f.fileCount} ${if (f.fileCount == 1) "file" else "files"} · ${Names.formatSize(f.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Folder options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; renaming = f })
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; deleting = f })
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        FolderNameDialog("New folder", "", "Create", folders.map { it.name }, onDismiss = { creating = false }) {
            vm.create(it); creating = false
        }
    }
    renaming?.let { f ->
        FolderNameDialog("Rename folder", f.name, "Rename", folders.map { it.name }, onDismiss = { renaming = null }) {
            vm.rename(f, it); renaming = null
        }
    }
    deleting?.let { f ->
        ConfirmDialog(
            title = "Delete \"${f.name}\"?",
            message = if (f.fileCount == 0) "The folder is empty." else "Its ${f.fileCount} files go to Recently deleted for 30 days.",
            confirmLabel = "Delete",
            onDismiss = { deleting = null },
            onConfirm = { vm.delete(f); deleting = null },
        )
    }
}

// =============================================================================================
// Inside one folder
// =============================================================================================

@Composable
private fun FolderDetailScreen(vm: FoldersViewModel, folder: SortFolder, onBack: () -> Unit, onRenamed: (SortFolder) -> Unit) {
    val context = LocalContext.current
    val files by vm.files.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val moveTarget by vm.moveTarget.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val grid by vm.settings.grid.collectAsStateWithLifecycle()
    val confirmDelete by vm.settings.confirmDelete.collectAsStateWithLifecycle()
    val copyByDefault by vm.settings.copyByDefault.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deletingFolder by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(folder.path, folder.fileCount) { vm.open(folder) }

    // Same gestures as the main list: left = delete (undo), right = move to another folder.
    val swipeLeft: (FileItem) -> Boolean = { item ->
        if (confirmDelete) { pendingDelete = item; false } else { vm.deleteFile(item); true }
    }
    val open: (FileItem) -> Unit = { FileIntents.open(context, it.path, it.mime) }
    val share: (FileItem) -> Unit = { FileIntents.share(context, it.path, it.mime) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${folder.fileCount} ${if (folder.fileCount == 1) "file" else "files"} · ${Names.formatSize(folder.totalBytes)}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.settings.setGrid(!grid) }) {
                        Icon(if (grid) Icons.Filled.ViewList else Icons.Filled.GridView, if (grid) "List view" else "Grid view")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename folder") }, onClick = { menu = false; renaming = true })
                            DropdownMenuItem(text = { Text("Delete folder") }, onClick = { menu = false; deletingFolder = true })
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TransferBanner(transfer)
            when {
                files.isEmpty() && !loading -> CenterMessage(
                    "This folder is empty",
                    "Swipe right on files in the Files tab and choose \"${folder.name}\" to fill it.",
                    Modifier.fillMaxSize(),
                )
                grid -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(files, key = { it.path }) { item ->
                        SwipeToSort(
                            onSwipeLeft = { swipeLeft(item) }, onSwipeRight = { vm.onSwipeRight(item) },
                            modifier = Modifier.animateItem(), rightLabel = "Move", rightIcon = Icons.Filled.DriveFileMove, leftLabel = "",
                        ) {
                            FileCell(item, { open(item) }, { share(item) }, { vm.onSwipeRight(item) }, { swipeLeft(item) })
                        }
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(files, key = { it.path }) { item ->
                        SwipeToSort(
                            onSwipeLeft = { swipeLeft(item) }, onSwipeRight = { vm.onSwipeRight(item) },
                            modifier = Modifier.animateItem(), rightLabel = "Move to folder", rightIcon = Icons.Filled.DriveFileMove,
                        ) {
                            FileRow(item, { open(item) }, { share(item) }, { vm.onSwipeRight(item) }, { swipeLeft(item) })
                        }
                    }
                }
            }
        }
    }

    moveTarget?.let { item ->
        FolderPickerSheet(
            item = item, folders = folders, copyByDefault = copyByDefault, excludePath = folder.path,
            onDismiss = vm::dismissMoveSheet,
            onPick = { f, copy -> vm.moveTo(item, f, copy) },
            onCreateAndPick = { name, copy -> vm.createFolderAndMove(item, name, copy) },
        )
    }
    pendingDelete?.let { item ->
        ConfirmDialog(
            "Delete ${item.name}?", "It goes to Recently deleted for 30 days.", "Delete",
            onDismiss = { pendingDelete = null }, onConfirm = { vm.deleteFile(item); pendingDelete = null },
        )
    }
    if (renaming) {
        FolderNameDialog("Rename folder", folder.name, "Rename", folders.map { it.name }, onDismiss = { renaming = false }) {
            vm.rename(folder, it, onRenamed); renaming = false
        }
    }
    if (deletingFolder) {
        ConfirmDialog(
            "Delete \"${folder.name}\"?",
            if (folder.fileCount == 0) "The folder is empty." else "Its ${folder.fileCount} files go to Recently deleted for 30 days.",
            "Delete",
            onDismiss = { deletingFolder = false },
            onConfirm = { deletingFolder = false; vm.delete(folder); onBack() },
        )
    }
}

// =============================================================================================
// Recently deleted
// =============================================================================================

@Composable
private fun TrashScreen(vm: FoldersViewModel, onBack: () -> Unit) {
    val entries by vm.trash.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    var forever by remember { mutableStateOf<TrashEntry?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Recently deleted") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { confirmEmpty = true }, enabled = entries.isNotEmpty()) { Text("Empty") } },
            )
        },
    ) { pad ->
        if (entries.isEmpty()) {
            CenterMessage("Nothing here", "Files you delete stay here for 30 days before they're gone for good.", Modifier.padding(pad).fillMaxSize())
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(pad).fillMaxSize(),
            ) {
                item {
                    Text(
                        "Swipe right to restore · swipe left to delete forever. Items are removed automatically after 30 days.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
                items(entries, key = { it.trashedPath }) { e ->
                    SwipeToSort(
                        onSwipeLeft = { forever = e; false },
                        onSwipeRight = { vm.restore(e) },
                        modifier = Modifier.animateItem(),
                        rightLabel = "Restore", rightIcon = Icons.Filled.Restore, leftLabel = "Delete forever",
                    ) { TrashRow(e) { vm.restore(e) } }
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            "Empty Recently deleted?", "${entries.size} items will be permanently deleted. This can't be undone.", "Delete forever",
            onDismiss = { confirmEmpty = false }, onConfirm = { vm.emptyTrash(); confirmEmpty = false },
        )
    }
    forever?.let { e ->
        ConfirmDialog(
            "Delete ${e.name} forever?", "This can't be undone.", "Delete forever",
            onDismiss = { forever = null }, onConfirm = { vm.deleteForever(e); forever = null },
        )
    }
}

@Composable
private fun TrashRow(e: TrashEntry, onRestore: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FileThumbnail(e.trashedPath, e.kind, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Deleted ${DateUtils.getRelativeTimeSpanString(e.deletedAt)} · from ${File(e.originalPath).parentFile?.name ?: "?"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRestore) { Icon(Icons.Filled.Restore, "Restore") }
        }
    }
}
