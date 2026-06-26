package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LemuroidGameCard(
    modifier: Modifier = Modifier,
    game: Game,
    onClick: () -> Unit = { },
    onLongClick: () -> Unit = { },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val actions = LocalGameActions.current
    
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            actions?.onImportSave?.invoke(game, uri)
        }
    }
    
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            actions?.onExportSave?.invoke(game, uri)
        }
    }
    var showContextMenu by remember { mutableStateOf(false) }
    var isMenuVisible by remember { mutableStateOf(false) }
    var showManageSavePopup by remember { mutableStateOf(false) }
    val manageSaveItemScreenYState = remember { mutableStateOf(0f) }
    LaunchedEffect(showContextMenu) {
        if (showContextMenu) {
            isMenuVisible = true
        } else {
            showManageSavePopup = false
        }
    }
    val rawAnimationProgress by animateFloatAsState(
        targetValue = if (showContextMenu) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "previewAnimation",
        finishedListener = { progress ->
            if (progress == 0f) {
                isMenuVisible = false
            }
        }
    )
    val animationProgress = rawAnimationProgress.coerceIn(0f, 1f)
    val isElevated = isPressed
    val scale by animateFloatAsState(if (isElevated) 1.05f else 1f, label = "scale")
    val shadowElevation by animateDpAsState(if (isElevated) 8.dp else 0.dp, label = "shadow")
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showChangeArtworkPopup by remember { mutableStateOf(false) }
    var showGameSettingsPopup by remember { mutableStateOf(false) }
    var showSaveStatesPopup by remember { mutableStateOf(false) }
    var cardX by remember { mutableStateOf(0f) }
    var cardY by remember { mutableStateOf(0f) }
    var cardWidth by remember { mutableStateOf(0) }
    var cardHeight by remember { mutableStateOf(0) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()

    if (showRenameDialog) {
        val initialRenameTitle = remember { game.title.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]|\\.[a-zA-Z0-9]+$"), "").trim() }
        var newTitle by remember { mutableStateOf(initialRenameTitle) }
        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

        androidx.compose.ui.window.Dialog(onDismissRequest = { showRenameDialog = false }) {
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
                        text = "Rename Game",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    androidx.compose.material3.OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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
                        androidx.compose.material3.Button(
                            onClick = { showRenameDialog = false },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Cancel")
                        }
                        
                        androidx.compose.material3.Button(
                            onClick = {
                                actions?.onRename?.invoke(game, newTitle)
                                showRenameDialog = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Rename")
                        }
                    }
                }
            }
        }
    }

    var showError by remember { mutableStateOf(false) }
    var showGameDatabaseSearch by remember { mutableStateOf(false) }

    if (showError) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text("Unable to Change artwork") },
            text = { Text("The image might be corrupted or in an unsupported format.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showError = false }) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    val cleanTitle = game.title.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]|\\.[a-zA-Z0-9]+$"), "").trim()

    if (showGameDatabaseSearch) {
        GameDatabaseSearchDialog(
            initialQuery = cleanTitle,
            onDismissRequest = { showGameDatabaseSearch = false },
            onGameSelected = { url ->
                actions?.onChangeArtwork?.invoke(game, url)
                showGameDatabaseSearch = false
            }
        )
    }



    if (showDeleteDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showDeleteDialog = false }) {
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
                        text = "Are you sure you want to delete this game?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                    
                    Text(
                        text = "All associated data, such as saves, save states, and cheat codes, will also be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        androidx.compose.material3.Button(
                            onClick = { showDeleteDialog = false },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Cancel")
                        }
                        
                        androidx.compose.material3.Button(
                            onClick = {
                                actions?.onDelete?.invoke(game)
                                showDeleteDialog = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.DarkGray,
                                contentColor = Color.Red
                            )
                        ) {
                            Text("Delete Game")
                        }
                    }
                }
            }
        }
    }

        val gridCardAlpha = if (isMenuVisible) 0f else 1f
        Column(
            modifier =
                modifier
                    .graphicsLayer {
                        alpha = gridCardAlpha
                    }
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        cardX = coordinates.positionInWindow().x
                        cardY = coordinates.positionInWindow().y
                        cardWidth = coordinates.size.width
                        cardHeight = coordinates.size.height
                    }
                    .combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = {
                            showContextMenu = true
                        },
                    ),
        ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            LemuroidGameImage(
                game = game,
                showGameplayPreview = false,
                imageModifier = Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .shadow(
                        elevation = shadowElevation,
                        shape = RoundedCornerShape(4.dp),
                        clip = false
                    )
            )
            if (showChangeArtworkPopup) {
                ChangeArtworkDialog(
                    onDismissRequest = { showChangeArtworkPopup = false },
                    onUriSelected = { uri ->
                        actions?.onChangeArtwork?.invoke(game, uri.toString())
                        showChangeArtworkPopup = false
                    },
                    onOpenGameDatabase = {
                        showChangeArtworkPopup = false
                        showGameDatabaseSearch = true
                    },
                    onShowError = { showError = true }
                )
            }
        }

        // Full game title — centered alignment
        Text(
            text = cleanTitle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = Color.White,
            lineHeight = 14.sp
        )
        
        if (showSaveStatesPopup) {
            ViewSaveStatesDialog(
                game = game,
                onDismissRequest = { showSaveStatesPopup = false }
            )
        }



        if (isMenuVisible) {
            val config = androidx.compose.ui.platform.LocalConfiguration.current
            val screenWidthDp = config.screenWidthDp.dp
            val screenHeightDp = config.screenHeightDp.dp
            val density = androidx.compose.ui.platform.LocalDensity.current
            val cardXDp = with(density) { cardX.toDp() }
            val cardYDp = with(density) { cardY.toDp() }
            val cardWidthDp = with(density) { cardWidth.toDp() }
            
            val aspectRatio = gameAspectRatioForSystem(game.systemId)
            val previewScale = 2.2f
            val targetWidthDp = cardWidthDp * previewScale
            val targetHeightDp = targetWidthDp / aspectRatio

            val defaultTargetXOffset = (cardWidthDp - targetWidthDp) / 2
            val expandedScreenX = cardXDp + defaultTargetXOffset
            val shiftX = when {
                expandedScreenX < 16.dp -> 16.dp - expandedScreenX
                expandedScreenX + targetWidthDp > screenWidthDp - 16.dp -> (screenWidthDp - 16.dp) - (expandedScreenX + targetWidthDp)
                else -> 0.dp
            }
            val targetXOffset = defaultTargetXOffset + shiftX

            val defaultTargetYOffset = (cardWidthDp - targetHeightDp) / 2
            val expandedScreenY = cardYDp + defaultTargetYOffset
            val shiftY = when {
                expandedScreenY < 80.dp -> 80.dp - expandedScreenY
                expandedScreenY + targetHeightDp > screenHeightDp - 40.dp -> (screenHeightDp - 40.dp) - (expandedScreenY + targetHeightDp)
                else -> 0.dp
            }
            val targetYOffset = defaultTargetYOffset + shiftY

            val currentWidth = cardWidthDp + (targetWidthDp - cardWidthDp) * animationProgress
            val currentAspectRatio = 1f + (aspectRatio - 1f) * animationProgress
            val currentHeight = currentWidth / currentAspectRatio

            val currentScreenX = cardXDp + targetXOffset * animationProgress
            val currentScreenY = cardYDp + targetYOffset * animationProgress
            
            val isBottomHalf = cardYDp > screenHeightDp * 0.45f

            androidx.compose.ui.window.Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(-cardX.toInt(), -cardY.toInt()),
                onDismissRequest = { showContextMenu = false },
                properties = androidx.compose.ui.window.PopupProperties(
                    focusable = true,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true,
                    clippingEnabled = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(screenWidthDp, screenHeightDp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showContextMenu = false }
                        )
                ) {
                    val roundedCornerRadius = 4.dp + (24.dp - 4.dp) * animationProgress
                    val manageSaveItemYSnapshot = manageSaveItemScreenYState.value

                    androidx.compose.ui.layout.Layout(
                        content = {
                            LemuroidGameImage(
                                game = game,
                                showGameplayPreview = showContextMenu && isMenuVisible,
                                onClick = {
                                    showContextMenu = false
                                    onClick()
                                },
                                cornerRadius = roundedCornerRadius,
                                aspectRatio = currentAspectRatio,
                                modifier = Modifier.width(currentWidth),
                                imageModifier = Modifier
                                    .shadow(16.dp * animationProgress, shape = RoundedCornerShape(roundedCornerRadius), clip = false)
                            )

                            if (animationProgress > 0.3f) {
                                val menuAlpha = (animationProgress - 0.3f) / 0.7f
                                val menuWidthDp = 260.dp
                                val maxMenuHeightDp = if (isBottomHalf) {
                                    currentScreenY - 80.dp
                                } else {
                                    screenHeightDp - (currentScreenY + currentHeight) - 40.dp
                                }
                                val safeMaxMenuHeightDp = maxMenuHeightDp.coerceAtLeast(100.dp)

                                // Main context menu — always shown
                                Surface(
                                    modifier = Modifier
                                        .width(menuWidthDp)
                                        .heightIn(max = safeMaxMenuHeightDp)
                                        .graphicsLayer { alpha = menuAlpha },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color(0xFF212121),
                                    tonalElevation = 8.dp
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        val menuTitle = cleanTitle

                                        Text(
                                            text = menuTitle,
                                            color = Color.Gray,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp, end = 12.dp)
                                        )

                                        ContextActionEntry(
                                            label = "Open in New Window",
                                            icon = Icons.Default.ExitToApp,
                                            onClick = {
                                                showContextMenu = false
                                                actions?.onLaunchGameNewWindow?.invoke(game)
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                                        if (game.isFavorite) {
                                            ContextActionEntry(
                                                label = stringResource(id = R.string.game_context_menu_remove_from_favorites),
                                                icon = Icons.Default.Star,
                                                onClick = {
                                                    showContextMenu = false
                                                    actions?.onFavoriteToggle?.invoke(game, false)
                                                }
                                            )
                                        } else {
                                            ContextActionEntry(
                                                label = stringResource(id = R.string.game_context_menu_add_to_favorites),
                                                icon = Icons.Default.StarBorder,
                                                onClick = {
                                                    showContextMenu = false
                                                    actions?.onFavoriteToggle?.invoke(game, true)
                                                }
                                            )
                                        }

                                        ContextActionEntry(
                                            label = stringResource(id = R.string.game_context_menu_rename),
                                            icon = Icons.Default.Edit,
                                            onClick = {
                                                showContextMenu = false
                                                showRenameDialog = true
                                            }
                                        )

                                        ContextActionEntry(
                                            label = stringResource(id = R.string.game_context_menu_change_artwork),
                                            icon = Icons.Default.Image,
                                            onClick = {
                                                showContextMenu = false
                                                showChangeArtworkPopup = true
                                            }
                                        )

                                        ContextActionEntry(
                                            label = stringResource(id = R.string.game_context_menu_share),
                                            icon = Icons.Default.Share,
                                            onClick = {
                                                showContextMenu = false
                                                actions?.onShare?.invoke(game)
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                                        ContextActionEntry(
                                            label = stringResource(id = R.string.game_context_menu_game_settings),
                                            icon = Icons.Default.Settings,
                                            onClick = {
                                                showContextMenu = false
                                                actions?.onGameSettings?.invoke(game)
                                            }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                                        ContextActionEntry(
                                            label = stringResource(id = R.string.game_context_menu_view_save_states),
                                            icon = Icons.Default.FileCopy,
                                            onClick = {
                                                showContextMenu = false
                                                showSaveStatesPopup = true
                                            }
                                        )

                                        // Track screen Y of this row to anchor the save panel beside it
                                        ContextActionEntry(
                                            modifier = Modifier
                                                .onGloballyPositioned { coords ->
                                                    manageSaveItemScreenYState.value = coords.positionInWindow().y
                                                }
                                                .then(
                                                    if (showManageSavePopup)
                                                        Modifier
                                                            .clip(RoundedCornerShape(percent = 50))
                                                            .background(Color.White.copy(alpha = 0.10f))
                                                    else
                                                        Modifier
                                                ),
                                            label = stringResource(id = R.string.game_context_menu_manage_save_file),
                                            icon = Icons.Default.Save,
                                            trailingIcon = Icons.Default.ChevronRight,
                                            onClick = { showManageSavePopup = !showManageSavePopup }
                                        )

                                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), color = Color.Gray.copy(alpha = 0.2f))

                                        ContextActionEntry(
                                            label = stringResource(id = R.string.game_context_menu_delete),
                                            icon = Icons.Default.Delete,
                                            textColor = Color.Red,
                                            iconColor = Color.Red,
                                            onClick = {
                                                showContextMenu = false
                                                showDeleteDialog = true
                                            }
                                        )
                                    }
                                }

                                // Save panel — shown alongside the menu, anchored to the manage save row
                                if (showManageSavePopup) {
                                    Surface(
                                        modifier = Modifier
                                            .width(180.dp)
                                            .graphicsLayer { alpha = menuAlpha },
                                        shape = RoundedCornerShape(24.dp),
                                        color = Color(0xFF212121),
                                        tonalElevation = 8.dp
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            ContextActionEntry(
                                                label = "Import Save",
                                                icon = Icons.Default.Download,
                                                onClick = {
                                                    showContextMenu = false
                                                    importLauncher.launch(arrayOf("application/octet-stream", "application/x-srm", "application/x-sav", "application/x-state", "*/*"))
                                                }
                                            )
                                            ContextActionEntry(
                                                label = "Export Save",
                                                icon = Icons.Default.Upload,
                                                onClick = {
                                                    showContextMenu = false
                                                    exportLauncher.launch("${cleanTitle}.srm")
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    ) { measurables, constraints ->
                        val previewPlaceable = measurables[0].measure(constraints)
                        val menuPlaceable = if (measurables.size > 1) measurables[1].measure(constraints) else null
                        val savePanelPlaceable = if (measurables.size > 2) measurables[2].measure(constraints) else null

                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val previewX = with(density) { currentScreenX.roundToPx() }
                            val previewY = with(density) { currentScreenY.roundToPx() }
                            val spacing = with(density) { 12.dp.roundToPx() }

                            // Place preview
                            previewPlaceable.place(previewX, previewY)

                            // Place context menu above or below the preview
                            if (menuPlaceable != null) {
                                val menuX = previewX
                                val menuY = if (isBottomHalf) {
                                    previewY - menuPlaceable.height - spacing
                                } else {
                                    previewY + previewPlaceable.height + spacing
                                }
                                menuPlaceable.place(menuX, menuY)

                                // Place save panel next to the "Manage Save File" row
                                if (savePanelPlaceable != null) {
                                    val isOnRightHalf = cardX > constraints.maxWidth / 2f
                                    val savePanelX = if (isOnRightHalf) {
                                        menuX - savePanelPlaceable.width - spacing
                                    } else {
                                        menuX + menuPlaceable.width + spacing
                                    }
                                    // Anchor Y to the manage save item row; fall back to mid-menu
                                    val anchorY = if (manageSaveItemYSnapshot > 0f) {
                                        manageSaveItemYSnapshot.toInt()
                                    } else {
                                        menuY + menuPlaceable.height / 2
                                    }
                                    savePanelPlaceable.place(
                                        savePanelX.coerceIn(spacing, constraints.maxWidth - savePanelPlaceable.width - spacing),
                                        anchorY.coerceIn(
                                            with(density) { 80.dp.roundToPx() },
                                            constraints.maxHeight - savePanelPlaceable.height - with(density) { 40.dp.roundToPx() }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun gameAspectRatioForSystem(systemId: String): Float = when (systemId) {
    "gb", "gbc" -> 10f / 9f
    "gba" -> 3f / 2f
    "psp" -> 16f / 9f
    "gg" -> 4f / 3f
    "lynx" -> 8f / 5f
    "ngp" -> 20f / 19f
    "ws", "wsc" -> 14f / 9f
    "nds" -> 2f / 3f
    "3ds" -> 20f / 27f
    else -> 4f / 3f
}

@Composable
private fun ContextActionEntry(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    trailingIcon: ImageVector? = null,
    textColor: Color = Color.White,
    iconColor: Color = Color.White,
    trailingIconColor: Color = Color.White,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
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
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = trailingIconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
