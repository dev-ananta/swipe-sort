@file:OptIn(ExperimentalMaterial3Api::class)

package com.swipesort.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swipesort.domain.FileItem
import com.swipesort.domain.FileKind
import com.swipesort.domain.Names
import com.swipesort.domain.SortFolder
import com.swipesort.ui.Transfer
import kotlinx.coroutines.delay

/**
 * The right-swipe "Add to folder" sheet: pick one of your folders, or type a new name to create
 * one on the spot. "Keep original" copies instead of moving.
 */
@Composable
fun FolderPickerSheet(
    item: FileItem,
    folders: List<SortFolder>,
    copyByDefault: Boolean,
    excludePath: String? = null,
    onDismiss: () -> Unit,
    onPick: (SortFolder, copy: Boolean) -> Unit,
    onCreateAndPick: (name: String, copy: Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var copy by remember { mutableStateOf(copyByDefault) }
    var newName by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val choices = folders.filter { it.path != excludePath }
    val error = Names.validateFolderName(newName, folders.map { it.name })

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileThumbnail(item.path, item.kind, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)), iconSize = 24)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Add to folder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { copy = !copy }) {
                Column(Modifier.weight(1f)) {
                    Text("Keep original")
                    Text(
                        if (copy) "A copy goes in the folder; the original stays put" else "The file is moved into the folder",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = copy, onCheckedChange = { copy = it })
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // Create a folder on the spot
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it; showError = false },
                    label = { Text("New folder") },
                    singleLine = true,
                    isError = showError && error != null,
                    supportingText = { if (showError && error != null) Text(error) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (error == null) onCreateAndPick(newName.trim(), copy) else showError = true }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (error == null) onCreateAndPick(newName.trim(), copy) else showError = true },
                    enabled = newName.isNotBlank(),
                ) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text("Create") }
            }

            if (choices.isEmpty()) {
                Text(
                    "No folders yet — name one above to start sorting.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                Text("Your folders", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(choices, key = { it.path }) { f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(f, copy) }.padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FolderCover(f, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${f.fileCount} ${if (f.fileCount == 1) "file" else "files"} · ${Names.formatSize(f.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

/** A folder's cover: its newest photo/video, or a folder icon. */
@Composable
fun FolderCover(folder: SortFolder, modifier: Modifier = Modifier) {
    val cover = folder.coverPath
    if (cover != null) {
        FileThumbnail(cover, FileKind.from(null, cover), modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

/**
 * Shows progress for moves/copies. Same-storage moves finish instantly, so the banner only
 * appears if an operation takes longer than a moment (big copies, SD card ↔ phone).
 */
@Composable
fun TransferBanner(transfer: Transfer?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(transfer != null) {
        if (transfer != null) { delay(300); visible = true } else visible = false
    }
    AnimatedVisibility(visible = visible && transfer != null, modifier = modifier) {
        val t = transfer ?: return@AnimatedVisibility
        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "${if (t.copying) "Copying" else "Moving"} ${t.fileName} → ${t.folderName}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(6.dp))
                LinearProgressIndicator(progress = { t.progress }, modifier = Modifier.fillMaxWidth())
                Text("${(t.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

/** Create / rename a folder, with live validation. */
@Composable
fun FolderNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    existing: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val error = Names.validateFolderName(name, existing.filterNot { it.equals(initial, ignoreCase = true) })
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Folder name") },
                isError = name.isNotBlank() && error != null,
                supportingText = { if (name.isNotBlank() && error != null) Text(error) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(enabled = error == null && name.trim() != initial, onClick = { onConfirm(name.trim()) }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Empty / error placeholder. */
@Composable
fun CenterMessage(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(6.dp))
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (action != null) { Spacer(Modifier.size(16.dp)); action() }
    }
}
