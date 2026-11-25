@file:OptIn(ExperimentalMaterial3Api::class)

package com.swipesort.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * ── Swipe gesture logic ──────────────────────────────────────────────────────────────────────
 * Wraps any row or grid cell in Material 3's SwipeToDismissBox:
 *
 *  • Swipe LEFT  (EndToStart)  → [onSwipeLeft]  — delete. The callback returns whether the item
 *    should slide away now (true: deleted, Undo offered) or snap back (false: e.g. a "Delete?"
 *    confirmation is showing).
 *  • Swipe RIGHT (StartToEnd)  → [onSwipeRight] — "Add to folder". Always snaps back: nothing
 *    has happened yet. The folder sheet opens; once a folder is picked, the item leaves the list.
 *
 * A swipe counts once it passes 35% of the item's width; shorter swipes spring back. The coloured
 * background (red bin / folder icon) shows which action you're about to trigger, and its icon grows
 * as you approach the threshold.
 */
@Composable
fun SwipeToSort(
    onSwipeLeft: () -> Boolean,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    rightLabel: String = "Add to folder",
    rightIcon: ImageVector = Icons.Filled.CreateNewFolder,
    leftLabel: String = "Delete",
    content: @Composable () -> Unit,
) {
    val left by rememberUpdatedState(onSwipeLeft)
    val right by rememberUpdatedState(onSwipeRight)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.EndToStart -> left()
                SwipeToDismissBoxValue.StartToEnd -> { right(); false }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = { SwipeBackground(state, shape, rightLabel, rightIcon, leftLabel) },
    ) { content() }
}

@Composable
private fun SwipeBackground(
    state: SwipeToDismissBoxState,
    shape: Shape,
    rightLabel: String,
    rightIcon: ImageVector,
    leftLabel: String,
) {
    val direction = state.dismissDirection
    val toFolder = direction == SwipeToDismissBoxValue.StartToEnd
    val toDelete = direction == SwipeToDismissBoxValue.EndToStart
    val bg = when {
        toFolder -> MaterialTheme.colorScheme.primary
        toDelete -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    val fg = when {
        toFolder -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onError
    }
    // Progress toward the action threshold (0..1) drives the icon size.
    val reached = state.targetValue != SwipeToDismissBoxValue.Settled
    val iconScale by animateFloatAsState(if (reached) 1.25f else 0.9f, label = "swipeIcon")

    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .background(bg)
            .padding(horizontal = 20.dp),
        contentAlignment = if (toFolder) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        if (toFolder || toDelete) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (toFolder) {
                    Icon(rightIcon, null, tint = fg, modifier = Modifier.scale(iconScale))
                    Spacer(Modifier.width(8.dp))
                    Text(rightLabel, color = fg, fontWeight = FontWeight.SemiBold)
                } else {
                    Text(leftLabel, color = fg, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Delete, null, tint = fg, modifier = Modifier.scale(iconScale))
                }
            }
        }
    }
}

/** Icon used for the Recently-deleted "restore" swipe. */
val RestoreIcon: ImageVector get() = Icons.Filled.Restore
