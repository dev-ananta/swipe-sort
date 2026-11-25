@file:OptIn(ExperimentalMaterial3Api::class)

package com.swipesort.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.swipesort.domain.FileFilter
import com.swipesort.domain.FileItem
import com.swipesort.domain.SortOrder
import com.swipesort.ui.EventSnackbars
import com.swipesort.ui.components.CenterMessage
import com.swipesort.ui.components.ConfirmDialog
import com.swipesort.ui.components.FileCell
import com.swipesort.ui.components.FileRow
import com.swipesort.ui.components.FolderPickerSheet
import com.swipesort.ui.components.SwipeToSort
import com.swipesort.ui.components.TransferBanner
import com.swipesort.util.FileIntents

/** "Files" tab: everything on the device that hasn't been sorted yet. */
@Composable
fun BrowseScreen(vm: BrowseViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val items = vm.files.collectAsLazyPagingItems()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val search by vm.search.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val grid by vm.settings.grid.collectAsStateWithLifecycle()
    val hintSeen by vm.settings.swipeHintSeen.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val moveTarget by vm.moveTarget.collectAsStateWithLifecycle()
    val pendingDelete by vm.pendingDelete.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val copyByDefault by vm.settings.copyByDefault.collectAsStateWithLifecycle()

    EventSnackbars(vm.events, snackbar)

    val open: (FileItem) -> Unit = { FileIntents.open(context, it.path, it.mime) }
    val share: (FileItem) -> Unit = { FileIntents.share(context, it.path, it.mime) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { BrowseTopBar(vm, search, sort, grid) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            FilterRow(filter) { vm.filter.value = it }
            if (!hintSeen) SwipeHint { vm.settings.setSwipeHintSeen() }
            TransferBanner(transfer)

            PullToRefreshBox(
                isRefreshing = items.loadState.refresh is LoadState.Loading && items.itemCount > 0,
                onRefresh = { items.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                val refresh = items.loadState.refresh
                when {
                    refresh is LoadState.Loading && items.itemCount == 0 ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                    refresh is LoadState.Error && items.itemCount == 0 ->
                        CenterMessage("Couldn't read your files", refresh.error.message ?: "Unknown error", Modifier.fillMaxSize()) {
                            Button(onClick = { items.retry() }) { Text("Try again") }
                        }

                    items.itemCount == 0 -> CenterMessage(
                        title = if (search.isNotBlank()) "No matches" else "All sorted!",
                        body = when {
                            search.isNotBlank() -> "Nothing named like \"$search\"" + if (filter != FileFilter.ALL) " in ${filter.label}." else "."
                            filter != FileFilter.ALL -> "No unsorted ${filter.label.lowercase()} left."
                            else -> "Every file is in a folder. Find them on the Folders tab."
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    grid -> FileGrid(items, vm, open, share)
                    else -> FileList(items, vm, open, share)
                }
            }
        }
    }

    moveTarget?.let { item ->
        FolderPickerSheet(
            item = item,
            folders = folders,
            copyByDefault = copyByDefault,
            onDismiss = vm::dismissMoveSheet,
            onPick = { f, copy -> vm.moveTo(item, f, copy) },
            onCreateAndPick = { name, copy -> vm.createFolderAndMove(item, name, copy) },
        )
    }
    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete ${item.name}?",
            message = "It goes to Recently deleted (Folders tab) for 30 days, so you can still get it back.",
            confirmLabel = "Delete",
            onDismiss = vm::cancelPendingDelete,
            onConfirm = vm::confirmPendingDelete,
        )
    }
}

@Composable
private fun FileList(items: LazyPagingItems<FileItem>, vm: BrowseViewModel, open: (FileItem) -> Unit, share: (FileItem) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = items.itemCount, key = items.itemKey { it.id }, contentType = items.itemContentType { "file" }) { i ->
            val item = items[i] ?: return@items
            SwipeToSort(
                onSwipeLeft = { vm.onSwipeLeft(item) },
                onSwipeRight = { vm.onSwipeRight(item) },
                modifier = Modifier.animateItem(),
            ) {
                FileRow(item, onOpen = { open(item) }, onShare = { share(item) }, onSort = { vm.onSwipeRight(item) }, onDelete = { vm.onSwipeLeft(item) })
            }
        }
    }
}

@Composable
private fun FileGrid(items: LazyPagingItems<FileItem>, vm: BrowseViewModel, open: (FileItem) -> Unit, share: (FileItem) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(count = items.itemCount, key = items.itemKey { it.id }, contentType = items.itemContentType { "file" }) { i ->
            val item = items[i] ?: return@items
            SwipeToSort(
                onSwipeLeft = { vm.onSwipeLeft(item) },
                onSwipeRight = { vm.onSwipeRight(item) },
                modifier = Modifier.animateItem(),
                rightLabel = "Folder",
                leftLabel = "",
            ) {
                FileCell(item, onOpen = { open(item) }, onShare = { share(item) }, onSort = { vm.onSwipeRight(item) }, onDelete = { vm.onSwipeLeft(item) })
            }
        }
    }
}

@Composable
private fun BrowseTopBar(vm: BrowseViewModel, search: String, sort: SortOrder, grid: Boolean) {
    var searching by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val confirmDelete by vm.settings.confirmDelete.collectAsStateWithLifecycle()
    val copyByDefault by vm.settings.copyByDefault.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }

    TopAppBar(
        title = {
            if (searching) {
                TextField(
                    value = search,
                    onValueChange = { vm.search.value = it },
                    placeholder = { Text("Search file names") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (search.isNotEmpty()) IconButton(onClick = { vm.search.value = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            } else {
                Text("SwipeSort")
            }
        },
        navigationIcon = {
            if (searching) IconButton(onClick = { searching = false; vm.search.value = "" }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search")
            }
        },
        actions = {
            if (!searching) IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Search") }
            IconButton(onClick = { vm.settings.setGrid(!grid) }) {
                Icon(if (grid) Icons.Filled.ViewList else Icons.Filled.GridView, if (grid) "List view" else "Grid view")
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, "Sort") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortOrder.entries.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o.label) },
                            trailingIcon = { if (o == sort) Icon(Icons.Filled.Check, null) },
                            onClick = { vm.sort.value = o; sortMenu = false },
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Ask before deleting") },
                        trailingIcon = { if (confirmDelete) Icon(Icons.Filled.Check, null) },
                        onClick = { vm.settings.setConfirmDelete(!confirmDelete) },
                    )
                    DropdownMenuItem(
                        text = { Text("Keep originals (copy) by default") },
                        trailingIcon = { if (copyByDefault) Icon(Icons.Filled.Check, null) },
                        onClick = { vm.settings.setCopyByDefault(!copyByDefault) },
                    )
                    DropdownMenuItem(text = { Text("Refresh") }, onClick = { moreMenu = false; vm.refresh() })
                }
            }
        },
    )
}

@Composable
private fun FilterRow(selected: FileFilter, onSelect: (FileFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(FileFilter.entries) { f ->
            FilterChip(
                selected = f == selected,
                onClick = { onSelect(f) },
                label = { Text(f.label) },
                leadingIcon = if (f == selected) ({ Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) }) else null,
            )
        }
    }
}

@Composable
private fun SwipeHint(onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwipeLeft, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Swipe left to delete (you can undo)", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.size(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SwipeRight, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text("Swipe right to add to a folder", style = MaterialTheme.typography.bodyMedium)
                }
            }
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    }
}
