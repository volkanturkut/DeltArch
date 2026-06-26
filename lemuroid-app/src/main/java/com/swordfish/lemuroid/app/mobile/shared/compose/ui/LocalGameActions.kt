package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.runtime.compositionLocalOf
import com.swordfish.lemuroid.lib.library.db.entity.Game

data class GameActions(
    val onPlay: (Game) -> Unit,
    val onLaunchGameNewWindow: (Game) -> Unit,
    val onRestart: (Game) -> Unit,
    val onFavoriteToggle: (Game, Boolean) -> Unit,
    val onCreateShortcut: (Game) -> Unit,
    val shortcutSupported: Boolean,
    val getLatestSaveStatePreview: suspend (Game) -> android.graphics.Bitmap?,
    val getGameSaveStates: suspend (Game) -> List<SaveStateItem>,
    val onRename: (Game, String) -> Unit,
    val onChangeArtwork: (Game, String) -> Unit,
    val onShare: (Game) -> Unit,
    val onGameSettings: (Game) -> Unit,
    val onViewSaveState: (Game) -> Unit,
    val onLoadSaveState: (Game, SaveStateItem) -> Unit,
    val onRenameSaveState: (Game, SaveStateItem, String) -> Unit,
    val onDeleteSaveState: (Game, SaveStateItem) -> Unit,
    val onImportSaveState: suspend (Game, SaveStateItem, ByteArray) -> Unit,
    val onExportSaveState: suspend (Game, SaveStateItem) -> ByteArray?,
    val onImportSave: (Game, android.net.Uri) -> Unit,
    val onExportSave: (Game, android.net.Uri) -> Unit,
    val onDelete: (Game) -> Unit,
    val startPreview: (Game, onPlay: () -> Unit, (android.view.View) -> Unit) -> Unit = { _, _, _ -> },
    val stopPreview: () -> Unit = {},
)

val LocalGameActions = compositionLocalOf<GameActions?> { null }
