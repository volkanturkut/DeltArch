package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalGameActions
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionInRoot

enum class SortBy {
    NAME, DATE
}

enum class SortDirection {
    UPWARD, DOWNWARD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewSaveStatesDialog(
    game: Game,
    onDismissRequest: () -> Unit
) {
    val actions = LocalGameActions.current
    var saveStates by remember { mutableStateOf<List<SaveStateItem>>(emptyList()) }
    var sortBy by remember { mutableStateOf(SortBy.DATE) }
    var sortDirection by remember { mutableStateOf(SortDirection.DOWNWARD) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    var isVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedItemForImport by remember { mutableStateOf<SaveStateItem?>(null) }
    var selectedItemForExport by remember { mutableStateOf<SaveStateItem?>(null) }
    var selectedItemForPreview by remember { mutableStateOf<SaveStateItem?>(null) }
    var selectedItemForRename by remember { mutableStateOf<SaveStateItem?>(null) }
    var selectedItemForDelete by remember { mutableStateOf<SaveStateItem?>(null) }
    var showChangePreviewDialog by remember { mutableStateOf(false) }

    // Top-level states for context menu and live preview
    var selectedItemForMenu by remember { mutableStateOf<SaveStateItem?>(null) }
    var itemX by remember { mutableStateOf(0f) }
    var itemY by remember { mutableStateOf(0f) }
    var itemWidth by remember { mutableStateOf(0) }
    var showContextMenu by remember { mutableStateOf(false) }
    var isMenuVisible by remember { mutableStateOf(false) }

    // Root-level dialog triggers
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showContextMenu) {
        if (showContextMenu) {
            isMenuVisible = true
        }
    }
    val rawAnimProgress by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (showContextMenu) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "saveStatePreviewAnim",
        finishedListener = { progress ->
            if (progress == 0f) {
                isMenuVisible = false
                selectedItemForMenu = null
            }
        }
    )
    val animProgress = rawAnimProgress.coerceIn(0f, 1f)


    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val item = selectedItemForImport
        if (uri != null && item != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        actions?.onImportSaveState?.invoke(game, item, bytes)
                        android.widget.Toast.makeText(context, "Save state imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                        saveStates = actions?.getGameSaveStates?.invoke(game) ?: emptyList()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed to import save state", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val item = selectedItemForExport
        if (uri != null && item != null) {
            coroutineScope.launch {
                try {
                    val bytes = actions?.onExportSaveState?.invoke(game, item)
                    if (bytes != null) {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(bytes)
                            android.widget.Toast.makeText(context, "Save state exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "No save state data to export", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Failed to export save state", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        isVisible = true
        saveStates = actions?.getGameSaveStates?.invoke(game) ?: emptyList()
        isLoading = false
    }

    val sortedStates = remember(saveStates, sortBy, sortDirection) {
        val sorted = when (sortBy) {
            SortBy.NAME -> saveStates.sortedBy { it.title }
            SortBy.DATE -> saveStates.sortedBy { it.date }
        }
        if (sortDirection == SortDirection.UPWARD) sorted else sorted.reversed()
    }

    val autoSaves = sortedStates.filter { it.isAutoSave }
    val generalSaves = sortedStates.filter { !it.isAutoSave }

    val handleDismiss = {
        isVisible = false
        onDismissRequest()
    }

    Dialog(
        onDismissRequest = handleDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 300,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            ) + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = 250,
                    easing = androidx.compose.animation.core.FastOutLinearInEasing
                )
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 250)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            val view = LocalView.current
            DisposableEffect(view) {
                var parent = view.parent
                while (parent != null) {
                    if (parent is DialogWindowProvider) {
                        parent.window.setDimAmount(0f)
                        parent.window.setLayout(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        parent.window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                        
                        // Hide system UI (navigation/status bars) for the dialog's window
                        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(parent.window, false)
                        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(parent.window, parent.window.decorView)
                        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())

                        @Suppress("DEPRECATION")
                        parent.window.decorView.systemUiVisibility = (
                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        )
                        break
                    }
                    parent = parent.parent
                }
                onDispose {}
            }

            DisposableEffect(Unit) {
                onDispose {
                    if (!isVisible) {
                        onDismissRequest()
                    }
                }
            }

            // A surface with the full screen
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .systemBarsPadding()
                    ) {
                        TopAppBar(
                            title = {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Load State",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            },
                            navigationIcon = {
                                FilledTonalIconButton(
                                    onClick = handleDismiss,
                                    modifier = Modifier.padding(start = 4.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            },
                            actions = {
                                Box {
                                        FilledTonalIconButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier
                                                .padding(end = 4.dp)
                                                .graphicsLayer { alpha = if (showSortMenu) 0f else 1f },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Icon(Icons.Default.MoreHoriz, contentDescription = "Sort")
                                        }

                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false },
                                        modifier = Modifier,
                                        offset = androidx.compose.ui.unit.DpOffset(x = 100.dp, y = (-120).dp),
                                        shape = RoundedCornerShape(24.dp),
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 0.dp
                                    ) {
                                        Text(
                                            text = "Sort by...",
                                            color = Color.Gray,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp, end = 16.dp)
                                        )
                                        // Name Sort Option
                                        SortMenuOption(
                                            label = "Name",
                                            isSelected = sortBy == SortBy.NAME,
                                            isUpward = sortDirection == SortDirection.UPWARD,
                                            onClick = {
                                                if (sortBy == SortBy.NAME) {
                                                    sortDirection = if (sortDirection == SortDirection.UPWARD) SortDirection.DOWNWARD else SortDirection.UPWARD
                                                } else {
                                                    sortBy = SortBy.NAME
                                                    sortDirection = SortDirection.UPWARD
                                                }
                                                showSortMenu = false
                                            }
                                        )
                                        // Date Sort Option
                                        SortMenuOption(
                                            label = "Date",
                                            isSelected = sortBy == SortBy.DATE,
                                            isUpward = sortDirection == SortDirection.UPWARD,
                                            onClick = {
                                                if (sortBy == SortBy.DATE) {
                                                    sortDirection = if (sortDirection == SortDirection.UPWARD) SortDirection.DOWNWARD else SortDirection.UPWARD
                                                } else {
                                                    sortBy = SortBy.DATE
                                                    sortDirection = SortDirection.DOWNWARD
                                                }
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent
                            )
                        )

                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else if (saveStates.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "No Save States",
                                    fontSize = 20.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Create a new save state by pressing the Save State option in the pause menu.",
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (autoSaves.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = "Auto Save",
                                            fontSize = 18.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                                        )
                                    }
                                    items(autoSaves) { item ->
                                        SaveStateListItem(
                                            item = item,
                                            systemId = game.systemId,
                                            onClick = {
                                                actions?.onLoadSaveState?.invoke(game, item)
                                                handleDismiss()
                                            },
                                            onLongClick = null // Auto save has no context menu
                                        )
                                    }
                                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(modifier = Modifier.height(8.dp)) }
                                }

                                if (generalSaves.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Text(
                                            text = "General",
                                            fontSize = 18.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                                        )
                                    }
                                    items(generalSaves) { item ->
                                        var localX by remember { mutableStateOf(0f) }
                                        var localY by remember { mutableStateOf(0f) }
                                        var localWidth by remember { mutableStateOf(0) }

                                        val gridCardAlpha = if (isMenuVisible && selectedItemForMenu?.id == item.id) 0f else 1f

                                        Box(modifier = Modifier.graphicsLayer { alpha = gridCardAlpha }) {
                                            SaveStateListItem(
                                                item = item,
                                                systemId = game.systemId,
                                                onClick = {
                                                    actions?.onLoadSaveState?.invoke(game, item)
                                                    handleDismiss()
                                                },
                                                onLongClick = {
                                                    itemX = localX
                                                    itemY = localY
                                                    itemWidth = localWidth
                                                    selectedItemForMenu = item
                                                    showContextMenu = true
                                                },
                                                onPositioned = { x, y, w ->
                                                    localX = x
                                                    localY = y
                                                    localWidth = w
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Context Menu Popup
                    if (isMenuVisible && selectedItemForMenu != null) {
                        val item = selectedItemForMenu!!
                        val config = androidx.compose.ui.platform.LocalConfiguration.current
                        val screenWidthDp = config.screenWidthDp.dp
                        val screenHeightDp = config.screenHeightDp.dp
                        val popupDensity = androidx.compose.ui.platform.LocalDensity.current

                        val itemXDp = with(popupDensity) { itemX.toDp() }
                        val itemYDp = with(popupDensity) { itemY.toDp() }
                        val itemWidthDp = with(popupDensity) { itemWidth.toDp() }

                        val aspectRatio = saveStateAspectRatioForSystem(game.systemId)
                        val previewScale = 2.0f
                        val targetWidthDp = (itemWidthDp * previewScale).coerceAtMost(screenWidthDp - 32.dp)
                        val targetHeightDp = targetWidthDp / aspectRatio

                        // Center horizontally, clamp to screen edges
                        val defaultTargetXOffset = (itemWidthDp - targetWidthDp) / 2
                        val expandedScreenX = itemXDp + defaultTargetXOffset
                        val shiftX = when {
                            expandedScreenX < 16.dp -> 16.dp - expandedScreenX
                            expandedScreenX + targetWidthDp > screenWidthDp - 16.dp -> (screenWidthDp - 16.dp) - (expandedScreenX + targetWidthDp)
                            else -> 0.dp
                        }
                        val targetXOffset = defaultTargetXOffset + shiftX

                        // Center vertically
                        val itemHeightDp = itemWidthDp / aspectRatio
                        val defaultTargetYOffset = (itemHeightDp - targetHeightDp) / 2
                        val expandedScreenY = itemYDp + defaultTargetYOffset
                        val shiftY = when {
                            expandedScreenY < 80.dp -> 80.dp - expandedScreenY
                            expandedScreenY + targetHeightDp > screenHeightDp - 40.dp -> (screenHeightDp - 40.dp) - (expandedScreenY + targetHeightDp)
                            else -> 0.dp
                        }
                        val targetYOffset = defaultTargetYOffset + shiftY

                        val currentWidth = itemWidthDp + (targetWidthDp - itemWidthDp) * animProgress
                        val currentHeight = currentWidth / aspectRatio

                        val currentScreenX = itemXDp + targetXOffset * animProgress
                        val currentScreenY = itemYDp + targetYOffset * animProgress

                        // BackHandler to handle system back button to dismiss the overlay
                        androidx.activity.compose.BackHandler(enabled = showContextMenu) {
                            showContextMenu = false
                        }

                        // Context Menu Overlay (replaced Popup with standard Box to resolve WindowManager clipping and positioning issues)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = isMenuVisible,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f * animProgress))
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null,
                                        onClick = { showContextMenu = false }
                                    )
                            ) {
                                val roundedCornerRadius = 8.dp + (24.dp - 8.dp) * animProgress

                                androidx.compose.ui.layout.Layout(
                                    content = {
                                        // Save state live preview
                                        LemuroidGameImage(
                                            game = game,
                                            showGameplayPreview = showContextMenu && isMenuVisible,
                                            onClick = {
                                                showContextMenu = false
                                                actions?.onLoadSaveState?.invoke(game, item)
                                                handleDismiss()
                                            },
                                            cornerRadius = roundedCornerRadius,
                                            aspectRatio = aspectRatio,
                                            modifier = Modifier.width(currentWidth),
                                            imageModifier = Modifier
                                                .graphicsLayer {
                                                    shadowElevation = 16f * animProgress
                                                    shape = RoundedCornerShape(roundedCornerRadius)
                                                    clip = false
                                                },
                                            fallbackBitmap = item.bitmap,
                                            tempPreviewSlotId = item.id
                                        )

                                        // Context menu
                                        if (animProgress > 0.3f) {
                                            val menuAlpha = (animProgress - 0.3f) / 0.7f
                                            val menuWidthDp = 260.dp

                                            Surface(
                                                modifier = Modifier
                                                    .width(menuWidthDp)
                                                    .graphicsLayer { alpha = menuAlpha },
                                                shape = RoundedCornerShape(24.dp),
                                                color = Color(0xFF212121),
                                                tonalElevation = 8.dp
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                                ) {
                                                    val isDefaultSlotName = item.title.startsWith("Slot ") || item.title == "Auto Save"
                                                    val headerText = if (isDefaultSlotName) {
                                                        java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.date))
                                                    } else {
                                                        item.title
                                                    }
                                                    Text(
                                                        text = headerText,
                                                        color = Color.Gray,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp, end = 12.dp)
                                                    )

                                                    val sharedPref = context.getSharedPreferences("locked_save_states", android.content.Context.MODE_PRIVATE)
                                                    val isLocked = sharedPref.getBoolean("locked_${game.id}_${item.id}", false)
                                                    val isIncompatible = sharedPref.getBoolean("incompatible_${game.id}_${item.id}", false)

                                                    DropdownMenuEntry(
                                                        label = "Set as Preview Save State",
                                                        icon = Icons.Default.Visibility,
                                                        onClick = {
                                                            showContextMenu = false
                                                            selectedItemForPreview = item
                                                            showChangePreviewDialog = true
                                                        }
                                                    )

                                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                                                    DropdownMenuEntry(
                                                        label = if (isIncompatible) "Mark as Compatible" else "Mark as Incompatible",
                                                        icon = if (isIncompatible) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                        onClick = {
                                                            showContextMenu = false
                                                            sharedPref.edit().putBoolean("incompatible_${game.id}_${item.id}", !isIncompatible).apply()
                                                        }
                                                    )

                                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                                                    DropdownMenuEntry(
                                                        label = "Rename",
                                                        icon = Icons.Default.Edit,
                                                        onClick = {
                                                            showContextMenu = false
                                                            if (isLocked) {
                                                                android.widget.Toast.makeText(context, "Locked slots cannot be renamed.", android.widget.Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                selectedItemForRename = item
                                                                showRenameDialog = true
                                                            }
                                                        }
                                                    )

                                                    DropdownMenuEntry(
                                                        label = if (isLocked) "Unlock" else "Lock",
                                                        icon = if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                                        onClick = {
                                                            showContextMenu = false
                                                            sharedPref.edit().putBoolean("locked_${game.id}_${item.id}", !isLocked).apply()
                                                        }
                                                    )

                                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                                                    DropdownMenuEntry(
                                                        label = "İmport",
                                                        icon = Icons.Default.Download,
                                                        onClick = {
                                                            showContextMenu = false
                                                            selectedItemForImport = item
                                                            importLauncher.launch(arrayOf("*/*"))
                                                        }
                                                    )

                                                    DropdownMenuEntry(
                                                        label = "Export",
                                                        icon = Icons.Default.Upload,
                                                        onClick = {
                                                             showContextMenu = false
                                                             selectedItemForExport = item
                                                             val isDefaultSlotName = item.title.startsWith("Slot ") || item.title == "Auto Save"
                                                             val nameForFile = if (isDefaultSlotName) {
                                                                 java.text.SimpleDateFormat("dd.MM.yy, HHmm", java.util.Locale.getDefault()).format(java.util.Date(item.date))
                                                             } else {
                                                                 item.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                                             }
                                                             exportLauncher.launch("$nameForFile.state")
                                                        }
                                                    )

                                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                                                    DropdownMenuEntry(
                                                        label = "Delete",
                                                        icon = Icons.Default.Delete,
                                                        textColor = Color.Red,
                                                        iconColor = Color.Red,
                                                        onClick = {
                                                            showContextMenu = false
                                                            selectedItemForDelete = item
                                                            showDeleteDialog = true
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                ) { measurables, constraints ->
                                    val previewPlaceable = measurables[0].measure(constraints)
 
                                    val isBottomHalf = itemY > constraints.maxHeight * 0.45f
                                    val previewX = with(popupDensity) { currentScreenX.roundToPx() }
                                    val previewY = with(popupDensity) { currentScreenY.roundToPx() }
                                    val previewHeight = previewPlaceable.height
                                    val spacing = with(popupDensity) { 8.dp.roundToPx() }
                                    val topEdgeMargin = with(popupDensity) { 48.dp.roundToPx() }
                                    val bottomEdgeMargin = with(popupDensity) { 32.dp.roundToPx() }
 
                                    val minMenuHeight = with(popupDensity) { 240.dp.roundToPx() }
                                    val maxMenuHeight = if (isBottomHalf) {
                                        (previewY - topEdgeMargin - spacing).coerceAtLeast(minMenuHeight)
                                    } else {
                                        (constraints.maxHeight - (previewY + previewHeight) - bottomEdgeMargin - spacing).coerceAtLeast(minMenuHeight)
                                    }.coerceAtMost(constraints.maxHeight - topEdgeMargin - bottomEdgeMargin)
 
                                    val menuPlaceable = if (measurables.size > 1) {
                                        measurables[1].measure(
                                            constraints.copy(
                                                maxHeight = maxMenuHeight
                                            )
                                        )
                                    } else {
                                        null
                                    }
 
                                    layout(constraints.maxWidth, constraints.maxHeight) {
                                        previewPlaceable.place(previewX, previewY)
 
                                        if (menuPlaceable != null) {
                                            // Center menu horizontally under/above the preview
                                            val menuX = previewX + (previewPlaceable.width - menuPlaceable.width) / 2
                                            val menuY = if (isBottomHalf) {
                                                previewY - menuPlaceable.height - spacing
                                            } else {
                                                previewY + previewHeight + spacing
                                            }
                                            menuPlaceable.place(
                                                menuX.coerceIn(spacing, constraints.maxWidth - menuPlaceable.width - spacing),
                                                menuY.coerceIn(
                                                    topEdgeMargin,
                                                    constraints.maxHeight - menuPlaceable.height - bottomEdgeMargin
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Root-level confirmation & input dialogs
                    if (showChangePreviewDialog && selectedItemForPreview != null) {
                        val item = selectedItemForPreview!!
                        Dialog(onDismissRequest = { showChangePreviewDialog = false }) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Change Preview Save State?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = "The Preview Save State is loaded whenever you long press this game from the Main Menu. Are you sure you want to change it?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Button(
                                            onClick = { showChangePreviewDialog = false },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White)
                                        ) { Text("Cancel") }
                                        Button(
                                            onClick = {
                                                val sharedPref = context.getSharedPreferences("locked_save_states", android.content.Context.MODE_PRIVATE)
                                                sharedPref.edit().putString("preview_${game.id}", item.id).apply()
                                                showChangePreviewDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White)
                                        ) { Text("Change") }
                                    }
                                }
                            }
                        }
                    }

                    if (showRenameDialog && selectedItemForRename != null) {
                        val item = selectedItemForRename!!
                        val isDefaultSlotName = item.title.startsWith("Slot ") || item.title == "Auto Save"
                        val initialName = if (isDefaultSlotName) {
                            java.text.SimpleDateFormat("dd.MM.yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(item.date))
                        } else {
                            item.title
                        }
                        var newName by remember(item) { mutableStateOf(initialName) }
                        val focusRequester = remember { FocusRequester() }
                        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

                        Dialog(onDismissRequest = {
                            showRenameDialog = false
                            selectedItemForRename = null
                        }) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Rename Save State",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                    OutlinedTextField(
                                        value = newName,
                                        onValueChange = { newName = it },
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.DarkGray,
                                            unfocusedContainerColor = Color.DarkGray,
                                            focusedBorderColor = Color.Transparent,
                                            unfocusedBorderColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 24.dp)
                                            .focusRequester(focusRequester)
                                    )
                                    LaunchedEffect(Unit) {
                                        focusRequester.requestFocus()
                                        keyboardController?.show()
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Button(
                                            onClick = {
                                                showRenameDialog = false
                                                selectedItemForRename = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White)
                                        ) { Text("Cancel") }
                                        Button(
                                            onClick = {
                                                actions?.onRenameSaveState?.invoke(game, item, newName)
                                                coroutineScope.launch {
                                                    saveStates = actions?.getGameSaveStates?.invoke(game) ?: emptyList()
                                                }
                                                showRenameDialog = false
                                                selectedItemForRename = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White)
                                        ) { Text("Rename") }
                                    }
                                }
                            }
                        }
                    }

                    if (showDeleteDialog && selectedItemForDelete != null) {
                        val item = selectedItemForDelete!!
                        Dialog(onDismissRequest = {
                            showDeleteDialog = false
                            selectedItemForDelete = null
                        }) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Delete Save State?",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    )
                                    Text(
                                        text = "Are you sure you want to delete this save state? This cannot be undone.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Button(
                                            onClick = {
                                                showDeleteDialog = false
                                                selectedItemForDelete = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.White)
                                        ) { Text("Cancel") }
                                        Button(
                                            onClick = {
                                                actions?.onDeleteSaveState?.invoke(game, item)
                                                val sharedPref = context.getSharedPreferences("locked_save_states", android.content.Context.MODE_PRIVATE)
                                                if (sharedPref.getString("preview_${game.id}", null) == item.id) {
                                                    sharedPref.edit().remove("preview_${game.id}").apply()
                                                }
                                                sharedPref.edit()
                                                    .remove("locked_${game.id}_${item.id}")
                                                    .remove("incompatible_${game.id}_${item.id}")
                                                    .apply()
                                                coroutineScope.launch {
                                                    saveStates = actions?.getGameSaveStates?.invoke(game) ?: emptyList()
                                                }
                                                showDeleteDialog = false
                                                selectedItemForDelete = null
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray, contentColor = Color.Red)
                                        ) { Text("Delete") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenuOption(
    label: String,
    isSelected: Boolean,
    isUpward: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tick icon
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Upward/Downward Arrow
        if (isSelected) {
            Icon(if (isUpward) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        } else {
            Spacer(modifier = Modifier.width(20.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Text
        Text(text = label, color = Color.White, fontSize = 14.sp)
        Spacer(modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun DropdownMenuEntry(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    textColor: Color = Color.White,
    iconColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 14.sp
            )
        }
    }
}

private fun saveStateAspectRatioForSystem(systemId: String): Float = when (systemId) {
    "gb", "gbc" -> 10f / 9f
    "gba" -> 3f / 2f
    "psp" -> 16f / 9f
    "gg" -> 4f / 3f
    "lynx" -> 8f / 5f
    "ngp" -> 20f / 19f
    "ws", "wsc" -> 14f / 9f
    "nds" -> 1f / 2f
    "3ds" -> 20f / 27f
    else -> 4f / 3f
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaveStateListItem(
    item: SaveStateItem,
    systemId: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onPositioned: ((Float, Float, Int) -> Unit)? = null
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale.getDefault())
    val dateObj = Date(item.date)

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (isPressed && onLongClick != null) 1.05f else 1f)
    val indication = androidx.compose.foundation.LocalIndication.current

    val aspectRatio = saveStateAspectRatioForSystem(systemId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .then(
                if (onPositioned != null) {
                    Modifier.onGloballyPositioned { coordinates ->
                        val pos = coordinates.positionInRoot()
                        onPositioned(pos.x, pos.y, coordinates.size.width)
                    }
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            if (item.bitmap != null) {
                Image(
                    bitmap = item.bitmap.asImageBitmap(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text("No Image", color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        val isDefaultSlotName = item.title.startsWith("Slot ") || item.title == "Auto Save"
        val displayText = if (isDefaultSlotName) dateFormat.format(dateObj) else item.title
        Text(
            text = displayText,
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
    }
}
