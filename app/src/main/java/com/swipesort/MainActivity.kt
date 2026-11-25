package com.swipesort

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swipesort.data.StorageAccess
import com.swipesort.ui.browse.BrowseScreen
import com.swipesort.ui.browse.BrowseViewModel
import com.swipesort.ui.folders.FoldersTab
import com.swipesort.ui.folders.FoldersViewModel
import com.swipesort.ui.permission.PermissionScreen
import com.swipesort.ui.theme.SwipeSortTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SwipeSortTheme { Root() } }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StorageAccess.hasFullAccess(context)) }

    // Re-check every time the app comes back to the foreground — this is how we notice that the user
    // flipped "All files access" in Settings (there is no callback for that switch), or revoked it.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hasAccess = StorageAccess.hasFullAccess(context) }

    if (!hasAccess) PermissionScreen() else MainTabs()
}

private enum class Tab(val label: String) { FILES("Files"), FOLDERS("Folders") }

@Composable
private fun MainTabs() {
    var tab by rememberSaveable { mutableStateOf(Tab.FILES) }
    val snackbar = remember { SnackbarHostState() }
    val browseVm: BrowseViewModel = viewModel(factory = BrowseViewModel.Factory)
    val foldersVm: FoldersViewModel = viewModel(factory = FoldersViewModel.Factory)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (t == Tab.FILES) Icons.Filled.PermMedia else Icons.Filled.Folder, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        // Top insets are handled by each screen's TopAppBar; here we only keep clear of the nav bar.
        Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
            when (tab) {
                Tab.FILES -> BrowseScreen(browseVm, snackbar)
                Tab.FOLDERS -> FoldersTab(foldersVm, snackbar)
            }
        }
    }
}
