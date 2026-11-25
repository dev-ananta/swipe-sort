package com.swipesort.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swipesort.domain.FileItem
import com.swipesort.domain.FileKind
import com.swipesort.domain.Names
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
fun formatDate(epochMillis: Long): String =
    if (epochMillis <= 0) "" else Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(dateFmt)

fun FileKind.icon(): ImageVector = when (this) {
    FileKind.IMAGE, FileKind.GIF -> Icons.Filled.Image
    FileKind.VIDEO -> Icons.Filled.Videocam
    FileKind.AUDIO -> Icons.Filled.AudioFile
    FileKind.PDF -> Icons.Filled.PictureAsPdf
    FileKind.TEXT -> Icons.Filled.Article
    FileKind.ARCHIVE -> Icons.Filled.FolderZip
    FileKind.APK -> Icons.Filled.Android
    FileKind.DOCUMENT -> Icons.Filled.Description
    FileKind.OTHER -> Icons.Filled.InsertDriveFile
}

/** A distinct tint per kind so documents are recognisable at a glance. */
fun FileKind.tint(): Color = when (this) {
    FileKind.IMAGE, FileKind.GIF -> Color(0xFF4F8EF7)
    FileKind.VIDEO -> Color(0xFFE25B8A)
    FileKind.AUDIO -> Color(0xFFF2A33A)
    FileKind.PDF -> Color(0xFFE5534B)
    FileKind.TEXT -> Color(0xFF7D8590)
    FileKind.ARCHIVE -> Color(0xFF9B6BDB)
    FileKind.APK -> Color(0xFF3DBE7A)
    FileKind.DOCUMENT -> Color(0xFF2F81F7)
    FileKind.OTHER -> Color(0xFF8B949E)
}

/** Photo/video thumbnail (Coil, decoded at display size) or a coloured type icon. */
@Composable
fun FileThumbnail(path: String, kind: FileKind, modifier: Modifier = Modifier, iconSize: Int = 28) {
    Box(modifier.background(kind.tint().copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
        if (kind.isVisual) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context).data(File(path)).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (kind == FileKind.VIDEO) {
                Box(
                    Modifier.size(26.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            }
            if (kind == FileKind.GIF) {
                Text(
                    "GIF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        } else {
            Icon(kind.icon(), null, tint = kind.tint(), modifier = Modifier.size(iconSize.dp))
        }
    }
}

/** Long-press menu: the same actions as the swipes, plus Open and Share (also for accessibility). */
@Composable
fun FileMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSort: () -> Unit,
    onDelete: () -> Unit,
    sortLabel: String = "Add to folder",
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("Open") }, leadingIcon = { Icon(Icons.Filled.OpenInNew, null) }, onClick = { onDismiss(); onOpen() })
        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Filled.Share, null) }, onClick = { onDismiss(); onShare() })
        DropdownMenuItem(text = { Text(sortLabel) }, leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) }, onClick = { onDismiss(); onSort() })
        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { onDismiss(); onDelete() })
    }
}

/** List-mode row: thumbnail, name, size · date. Tap = open, long-press = menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSort: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileThumbnail(item.path, item.kind, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${Names.formatSize(item.size)} · ${formatDate(item.modified)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FileMenu(menu, { menu = false }, onOpen, onShare, onSort, onDelete)
        }
    }
}

/** Grid-mode cell: square thumbnail with the name on a soft gradient. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileCell(
    item: FileItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSort: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onOpen, onLongClick = { menu = true }),
    ) {
        FileThumbnail(item.path, item.kind, Modifier.fillMaxSize(), iconSize = 40)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(item.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Names.formatSize(item.size), color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
        }
        FileMenu(menu, { menu = false }, onOpen, onShare, onSort, onDelete)
    }
}
