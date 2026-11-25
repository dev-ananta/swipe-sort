package com.swipesort.ui.permission

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.swipesort.R
import com.swipesort.data.StorageAccess

/**
 * ── All-files-access flow (what the user sees) ──────────────────────────────────────────────
 * 1. This screen explains what SwipeSort needs and why, before any system prompt appears.
 * 2. "Allow access" first shows the normal pop-up(s): photos/videos/music on Android 13+,
 *    or storage on older versions.
 * 3. On Android 11+ it then opens Settings › "All files access" for SwipeSort. The user flips the
 *    switch and comes back; MainActivity re-checks access every time the app resumes, so this
 *    screen disappears by itself once access is granted.
 * 4. If they come back without granting it (or deny the pop-up for good), the screen switches to a
 *    "denied" explanation with ways to fix it, instead of failing silently.
 */
@Composable
fun PermissionScreen() {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        asked = true
        if (StorageAccess.usesAllFilesAccessSettings) {
            // Android 11+: media pop-ups are a bonus; the key switch lives in Settings.
            StorageAccess.openAllFilesAccessSettings(context)
        } else {
            val denied = result.filterValues { !it }.keys
            // "Don't ask again": the system no longer shows the pop-up, so point to App info instead.
            val activity = context as? Activity
            permanentlyDenied = denied.isNotEmpty() && activity != null &&
                denied.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        }
    }

    fun requestAccess() {
        val missing = StorageAccess.missingRuntimePermissions(context)
        when {
            missing.isNotEmpty() -> runtimeLauncher.launch(missing)
            StorageAccess.usesAllFilesAccessSettings -> { asked = true; StorageAccess.openAllFilesAccessSettings(context) }
            else -> { asked = true; permanentlyDenied = true }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.size(12.dp))
            Box(
                Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
            }
            Text("Welcome to SwipeSort", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Sort every photo, video, song and document on your phone with a swipe.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (asked) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (StorageAccess.usesAllFilesAccessSettings)
                                "SwipeSort still can't see your files. In the Settings page that opens, find SwipeSort and turn on \"Allow access to manage all files\", then come back."
                            else if (permanentlyDenied)
                                "Storage permission is turned off. Open App info › Permissions › Files and media, choose \"Allow\", then come back."
                            else
                                "SwipeSort needs storage permission to show and sort your files.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Why SwipeSort needs \"All files access\"", fontWeight = FontWeight.SemiBold)
                    Reason(Icons.Filled.FolderOpen, "See everything", "Photos, GIFs, videos, music and documents — wherever they are on your phone.")
                    Reason(Icons.Filled.CreateNewFolder, "Sort into folders", "Moving a file into one of your folders needs permission to change it.")
                    Reason(Icons.Filled.Delete, "Delete safely", "Deleted files go to Recently deleted for 30 days, so a mistaken swipe can be undone.")
                    Reason(Icons.Filled.Lock, "Stays on your phone", "SwipeSort has no internet permission. Your files are never uploaded anywhere.")
                }
            }

            Button(onClick = { requestAccess() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (asked) "Try again" else "Allow access")
            }
            if (asked) {
                OutlinedButton(
                    onClick = { StorageAccess.tryStart(context, StorageAccess.appSettingsIntent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open App info") }
            }
        }
    }
}

@Composable
private fun Reason(icon: ImageVector, title: String, body: String) {
    Row {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
