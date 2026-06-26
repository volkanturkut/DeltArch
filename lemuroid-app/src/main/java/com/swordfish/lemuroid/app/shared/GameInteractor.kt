@file:Suppress("all")

package com.swordfish.lemuroid.app.shared

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(DelicateCoroutinesApi::class)
class GameInteractor(
    private val activity: BusyActivity,
    private val retrogradeDb: RetrogradeDatabase,
    private val useLeanback: Boolean,
    private val shortcutsGenerator: ShortcutsGenerator,
    private val gameLauncher: GameLauncher,
    private val statesManager: com.swordfish.lemuroid.lib.saves.StatesManager,
    private val statesPreviewManager: com.swordfish.lemuroid.lib.saves.StatesPreviewManager,
    private val coresSelection: com.swordfish.lemuroid.lib.core.CoresSelection,
) {
    fun onGamePlay(game: Game) {
        if (!ensureNotBusy()) {
            return
        }
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        // Update lastPlayedAt immediately when the game is launched
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(lastPlayedAt = System.currentTimeMillis()))
        }
        gameLauncher.launchGameAsync(activity.activity(), game, true, useLeanback)
    }

    fun onGamePlayNewWindow(game: Game) {
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        // Update lastPlayedAt immediately when the game is launched
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(lastPlayedAt = System.currentTimeMillis()))
        }
        gameLauncher.launchGameAsync(activity.activity(), game, true, useLeanback, newWindow = true)
    }

    fun onGameRestart(game: Game) {
        if (!ensureNotBusy()) {
            return
        }
        if (!ensureNotificationsPermissionAvailable()) {
            return
        }
        // Update lastPlayedAt immediately when the game is launched
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(lastPlayedAt = System.currentTimeMillis()))
        }
        gameLauncher.launchGameAsync(activity.activity(), game, false, useLeanback)
    }

    fun onFavoriteToggle(
        game: Game,
        isFavorite: Boolean,
    ) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(isFavorite = isFavorite))
        }
    }

    fun onCreateShortcut(game: Game) {
        GlobalScope.launch {
            shortcutsGenerator.pinShortcutForGame(game)
        }
    }

    fun supportShortcuts(): Boolean {
        return shortcutsGenerator.supportShortcuts()
    }

    suspend fun getLatestSaveStatePreview(game: Game, context: android.content.Context): android.graphics.Bitmap? {
        val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId) ?: return null
        val coreConfig = coresSelection.getCoreConfigForSystem(system) ?: return null
        val coreID = coreConfig.coreID
        
        var latestIndex = -1
        var latestDate = 0L
        val savedSlots = statesManager.getSavedSlotsInfo(game, coreID)
        for (i in savedSlots.indices) {
            val info = savedSlots[i]
            if (info.exists && info.date > latestDate) {
                latestDate = info.date
                latestIndex = i
            }
        }
        
        if (latestIndex != -1) {
            val imageSize = Math.round(com.swordfish.lemuroid.common.graphics.GraphicsUtils.convertDpToPixel(com.swordfish.lemuroid.lib.saves.StatesPreviewManager.PREVIEW_SIZE_DP, context))
            return statesPreviewManager.getPreviewForSlot(game, coreID, latestIndex, imageSize)
        }
        return null
    }

    private fun ensureNotificationsPermissionAvailable(): Boolean {
        if (useLeanback || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val permissionResult =
            ContextCompat.checkSelfPermission(
                activity.activity(),
                Manifest.permission.POST_NOTIFICATIONS,
            )

        if (permissionResult == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        activity.activity().displayToast(R.string.game_interactor_notification_permission_required)
        return false
    }

    private fun ensureNotBusy(): Boolean {
        if (activity.isBusy()) {
            activity.activity().displayToast(R.string.game_interactory_busy)
            return false
        }
        return true
    }

    fun renameGame(game: Game, newTitle: String) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(title = newTitle))
        }
    }

    fun changeArtwork(game: Game, newCoverUrl: String) {
        GlobalScope.launch {
            retrogradeDb.gameDao().update(game.copy(coverFrontUrl = newCoverUrl))
        }
    }

    fun deleteGame(game: Game) {
        GlobalScope.launch {
            retrogradeDb.gameDao().delete(listOf(game))
        }
    }

    fun renameSaveState(game: Game, item: com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem, newName: String) {
        GlobalScope.launch {
            val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId) ?: return@launch
            val coreConfig = coresSelection.getCoreConfigForSystem(system) ?: return@launch
            val coreID = coreConfig.coreID
            if (!item.isAutoSave) {
                val slotIndex = item.id.removePrefix("slot_").toIntOrNull() ?: return@launch
                statesManager.renameSlotSave(game, coreID, slotIndex, newName)
            }
        }
    }

    fun getStatesManager(): com.swordfish.lemuroid.lib.saves.StatesManager = statesManager
    fun getStatesPreviewManager(): com.swordfish.lemuroid.lib.saves.StatesPreviewManager = statesPreviewManager

    suspend fun importSaveState(game: Game, item: com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem, bytes: ByteArray) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId) ?: return@withContext
        val coreConfig = coresSelection.getCoreConfigForSystem(system) ?: return@withContext
        val coreID = coreConfig.coreID
        if (!item.isAutoSave) {
            val slotIndex = item.id.removePrefix("slot_").toIntOrNull() ?: return@withContext
            statesManager.importSlotSave(game, coreID, slotIndex, bytes)
            statesPreviewManager.deletePreviewForSlot(game, coreID, slotIndex)
        }
    }

    suspend fun exportSaveState(game: Game, item: com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem): ByteArray? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId) ?: return@withContext null
        val coreConfig = coresSelection.getCoreConfigForSystem(system) ?: return@withContext null
        val coreID = coreConfig.coreID
        if (!item.isAutoSave) {
            val slotIndex = item.id.removePrefix("slot_").toIntOrNull() ?: return@withContext null
            return@withContext statesManager.exportSlotSave(game, coreID, slotIndex)
        }
        return@withContext null
    }

    fun deleteSaveState(game: Game, item: com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem) {
        GlobalScope.launch {
            val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId) ?: return@launch
            val coreConfig = coresSelection.getCoreConfigForSystem(system) ?: return@launch
            val coreID = coreConfig.coreID
            if (!item.isAutoSave) {
                val slotIndex = item.id.removePrefix("slot_").toIntOrNull() ?: return@launch
                val sharedPref = activity.activity().getSharedPreferences("locked_save_states", android.content.Context.MODE_PRIVATE)
                val isLocked = sharedPref.getBoolean("locked_${game.id}_${item.id}", false)
                if (isLocked) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        activity.activity().displayToast("Locked slots cannot be deleted.")
                    }
                    return@launch
                }
                statesManager.deleteSlotSave(game, coreID, slotIndex)
                statesPreviewManager.deletePreviewForSlot(game, coreID, slotIndex)
            }
        }
    }

    suspend fun getAllSaveStates(game: Game, context: android.content.Context): List<com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem> {
        val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId) ?: return emptyList()
        val coreConfig = coresSelection.getCoreConfigForSystem(system) ?: return emptyList()
        val coreID = coreConfig.coreID
        
        val items = mutableListOf<com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem>()
        
        val autoSaveInfo = statesManager.getAutoSaveInfo(game, coreID)
        if (autoSaveInfo.exists) {
            val imageSize = Math.round(com.swordfish.lemuroid.common.graphics.GraphicsUtils.convertDpToPixel(com.swordfish.lemuroid.lib.saves.StatesPreviewManager.PREVIEW_SIZE_DP, context))
            val bitmap = statesPreviewManager.getPreviewForAutoSave(game, coreID, imageSize)
            items.add(com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem(
                id = "autosave",
                title = "Auto Save",
                date = autoSaveInfo.date,
                isAutoSave = true,
                bitmap = bitmap
            ))
        }

        val savedSlots = statesManager.getSavedSlotsInfo(game, coreID)
        for (i in savedSlots.indices) {
            val info = savedSlots[i]
            if (info.exists) {
                val imageSize = Math.round(com.swordfish.lemuroid.common.graphics.GraphicsUtils.convertDpToPixel(com.swordfish.lemuroid.lib.saves.StatesPreviewManager.PREVIEW_SIZE_DP, context))
                val bitmap = statesPreviewManager.getPreviewForSlot(game, coreID, i, imageSize)
                val customName = statesManager.getSlotSaveName(game, coreID, i)
                items.add(com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem(
                    id = "slot_$i",
                    title = customName ?: "Slot ${i + 1}",
                    date = info.date,
                    isAutoSave = false,
                    bitmap = bitmap
                ))
            }
        }
        
        return items
    }

    fun loadSaveState(game: Game, item: com.swordfish.lemuroid.app.mobile.shared.compose.ui.SaveStateItem) {
        GlobalScope.launch {
            val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId) ?: return@launch
            val coreConfig = coresSelection.getCoreConfigForSystem(system) ?: return@launch
            val coreID = coreConfig.coreID
            
            val sharedPref = activity.activity().getSharedPreferences("locked_save_states", android.content.Context.MODE_PRIVATE)
            val isIncompatible = sharedPref.getBoolean("incompatible_${game.id}_${item.id}", false)
            if (isIncompatible) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    activity.activity().displayToast(activity.activity().getString(R.string.error_message_incompatible_state))
                }
                return@launch
            }

            if (!item.isAutoSave) {
                val slotIndex = item.id.removePrefix("slot_").toIntOrNull() ?: return@launch
                val slotState = statesManager.getSlotSave(game, coreID, slotIndex) ?: return@launch
                statesManager.setAutoSave(game, coreID, slotState)
                statesPreviewManager.copySlotPreviewToAutoSave(game, coreID, slotIndex)
            }
            // Launch the game telling it to load the auto save
            onGamePlay(game)
        }
    }
}
