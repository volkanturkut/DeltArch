@file:Suppress("all")

package com.swordfish.lemuroid.app.shared.game.viewmodel

import kotlin.time.Duration.Companion.milliseconds

import android.content.Context
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.common.graphics.GraphicsUtils
import com.swordfish.lemuroid.common.graphics.takeScreenshot
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.IncompatibleStateException
import com.swordfish.lemuroid.lib.saves.SaveState
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

class GameViewModelSaves(
    private val appContext: Context,
    private val system: GameSystem,
    private val game: Game,
    private val systemCoreConfig: SystemCoreConfig,
    private val retroGameView: GameViewModelRetroGameView,
    private val settingsManager: SettingsManager,
    private val savesManager: SavesManager,
    private val statesManager: StatesManager,
    private val statesPreviewManager: StatesPreviewManager,
    private val sideEffects: GameViewModelSideEffects,
) {
    private var currentQuickSave: SaveState? = null

    data class SaveSnapshot(
        val sram: ByteArray,
        val autoSave: SaveState?,
    )

    suspend fun saveSlot(index: Int) {
        val sharedPref = appContext.getSharedPreferences("locked_save_states", Context.MODE_PRIVATE)
        val isLocked = sharedPref.getBoolean("locked_${game.id}_slot_$index", false)
        if (isLocked) {
            sideEffects.showToast("This slot is locked and cannot be overwritten.")
            return
        }
        getCurrentSaveState()?.let {
            statesManager.setSlotSave(game, it, systemCoreConfig.coreID, index)
            runCatching {
                takeScreenshotPreview(index)
            }
        }
    }

    suspend fun loadSlot(index: Int) {
        val sharedPref = appContext.getSharedPreferences("locked_save_states", Context.MODE_PRIVATE)
        val isIncompatible = sharedPref.getBoolean("incompatible_${game.id}_slot_$index", false)
        if (isIncompatible) {
            sideEffects.showToast(appContext.getString(R.string.error_message_incompatible_state))
            return
        }
        try {
            statesManager.getSlotSave(game, systemCoreConfig.coreID, index)?.let {
                val loaded =
                    withContext(Dispatchers.IO) {
                        loadSaveState(it)
                    }

                if (!loaded) {
                    sideEffects.showToast(appContext.getString(R.string.game_toast_load_state_failed))
                }
            }
        } catch (e: Throwable) {
            val errorMessageId =
                when (e) {
                    is IncompatibleStateException -> R.string.error_message_incompatible_state
                    else -> R.string.game_toast_load_state_failed
                }
            sideEffects.showToast(appContext.getString(errorMessageId))
        }
    }

    suspend fun captureSaveSnapshot(useEmulationThread: Boolean): SaveSnapshot? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val sramState = retroGameView.serializeSRAM(useEmulationThread)
        val autoSaveState = if (isAutoSaveEnabled()) {
            val state = getCurrentSaveState(useEmulationThread)
            if (state != null) {
                runCatching {
                    takeScreenshotPreviewForAutoSave()
                }
            }
            state
        } else null
        return SaveSnapshot(sramState, autoSaveState)
    }

    suspend fun writeSaveSnapshot(snapshot: SaveSnapshot?) {
        if (snapshot == null) return
        Timber.i(
            "GameViewModelSaves.write game=%s core=%s writingSram=%s writingAutoSave=%s",
            game.id,
            systemCoreConfig.coreID,
            true,
            snapshot.autoSave != null,
        )
        savesManager.setSaveRAM(game, snapshot.sram)
        snapshot.autoSave?.let { statesManager.setAutoSave(game, systemCoreConfig.coreID, it) }
    }

    // On some cores unserialize fails with no reason. So we need to try multiple times.
    suspend fun restoreAutoSaveAsync(saveState: SaveState) {
        // PPSSPP and Mupen64 initialize some state while rendering the first frame, so we have to wait before restoring
        // the autosave. Do not change thread here. Stick to the GL one to avoid issues with PPSSPP.
        if (!isAutoSaveEnabled()) return

        try {
            retroGameView.waitGLEvent<GLRetroView.GLRetroEvents.FrameRendered>()
            restoreQuickSave(saveState)
        } catch (e: Throwable) {
            Timber.e(e, "Error while loading auto-save")
        }
    }

    private fun getCurrentSaveState(useEmulationThread: Boolean = true): SaveState? {
        val retroGameView = retroGameView.retroGameView ?: return null
        val currentDisk =
            if (system.hasMultiDiskSupport) {
                retroGameView.getCurrentDisk(useEmulationThread)
            } else {
                0
            }
        return SaveState(
            retroGameView.serializeState(useEmulationThread),
            SaveState.Metadata(currentDisk, systemCoreConfig.statesVersion),
        )
    }

    private suspend fun isAutoSaveEnabled(): Boolean {
        return systemCoreConfig.statesSupported && settingsManager.autoSave()
    }

    private suspend fun takeScreenshotPreview(index: Int) {
        val sizeInDp = StatesPreviewManager.PREVIEW_SIZE_DP
        val previewSize = GraphicsUtils.convertDpToPixel(sizeInDp, appContext).roundToInt()
        val preview = retroGameView.retroGameView?.takeScreenshot(previewSize, 3)
        if (preview != null) {
            statesPreviewManager.setPreviewForSlot(game, preview, systemCoreConfig.coreID, index)
        }
    }

    suspend fun takeScreenshotPreviewForAutoSave() {
        val sizeInDp = StatesPreviewManager.PREVIEW_SIZE_DP
        val previewSize = GraphicsUtils.convertDpToPixel(sizeInDp, appContext).roundToInt()
        val preview = retroGameView.retroGameView?.takeScreenshot(previewSize, 3)
        if (preview != null) {
            statesPreviewManager.setPreviewForAutoSave(game, preview, systemCoreConfig.coreID)
        }
    }

    // Now that we wait for the first rendered frame this is probably no longer needed, but we'll keep it just to be sure
    private suspend fun restoreQuickSave(saveState: SaveState) {
        var times = 10

        while (!loadSaveState(saveState) && times > 0) {
            delay(200.milliseconds)
            times--
        }
    }

    private fun loadSaveState(saveState: SaveState): Boolean {
        val retroGameView = retroGameView.retroGameView ?: return false

        if (systemCoreConfig.statesVersion != saveState.metadata.version) {
            throw IncompatibleStateException()
        }

        if (system.hasMultiDiskSupport &&
            retroGameView.getAvailableDisks() > 1 &&
            retroGameView.getCurrentDisk() != saveState.metadata.diskIndex
        ) {
            retroGameView.changeDisk(saveState.metadata.diskIndex)
        }

        return retroGameView.unserializeState(saveState.state)
    }

    fun saveQuickSave() {
        currentQuickSave = getCurrentSaveState()
        sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_saved))
    }

    fun loadQuickSave() {
        loadSaveState(currentQuickSave ?: return)
        sideEffects.showToast(appContext.getString(R.string.game_toast_quick_save_loaded))
    }
}
