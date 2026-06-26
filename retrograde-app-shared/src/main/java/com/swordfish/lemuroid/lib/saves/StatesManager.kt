@file:Suppress("all")
package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.readBytesUncompressedAtomic
import com.swordfish.lemuroid.common.kotlin.readTextAtomic
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.common.kotlin.writeBytesCompressedAtomic
import com.swordfish.lemuroid.common.kotlin.writeBytesAtomic
import com.swordfish.lemuroid.common.kotlin.writeTextAtomic
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class StatesManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int,
    ): SaveState? =
        withContext(Dispatchers.IO) {
            assert(index in 0 until MAX_STATES)
            getSaveState(getSlotSaveFileName(game, index), coreID.coreName)
        }

    suspend fun setSlotSave(
        game: Game,
        saveState: SaveState,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        setSaveState(getSlotSaveFileName(game, index), coreID.coreName, saveState)
    }

    suspend fun getAutoSaveInfo(
        game: Game,
        coreID: CoreID,
    ): SaveInfo =
        withContext(Dispatchers.IO) {
            val autoSaveFile = getStateFile(getAutoSaveFileName(game), coreID.coreName)
            val autoSaveHasData = autoSaveFile.length() > 0
            SaveInfo(autoSaveFile.exists() && autoSaveHasData, autoSaveFile.lastModified())
        }

    suspend fun getAutoSave(
        game: Game,
        coreID: CoreID,
    ) = withContext(Dispatchers.IO) {
        getSaveState(getAutoSaveFileName(game), coreID.coreName)
    }

    suspend fun setAutoSave(
        game: Game,
        coreID: CoreID,
        saveState: SaveState,
    ) = withContext(Dispatchers.IO) {
        setSaveState(getAutoSaveFileName(game), coreID.coreName, saveState)
    }

    suspend fun deleteSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int
    ) = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        val fileName = getSlotSaveFileName(game, index)
        val saveFile = getStateFile(fileName, coreID.coreName)
        val metadataFile = getMetadataStateFile(fileName, coreID.coreName)
        if (saveFile.exists()) saveFile.delete()
        if (metadataFile.exists()) metadataFile.delete()
    }

    suspend fun renameSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int,
        newName: String
    ) = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        val currentState = getSlotSave(game, coreID, index)
        if (currentState != null) {
            val updatedMetadata = currentState.metadata.copy(name = newName)
            val updatedState = SaveState(currentState.state, updatedMetadata)
            setSlotSave(game, updatedState, coreID, index)
        }
    }

    suspend fun getSavedSlotsInfo(
        game: Game,
        coreID: CoreID,
    ): List<SaveInfo> =
        withContext(Dispatchers.IO) {
            (0 until MAX_STATES)
                .map { getStateFile(getSlotSaveFileName(game, it), coreID.coreName) }
                .map { SaveInfo(it.exists(), it.lastModified()) }
                .toList()
        }

    suspend fun getSlotSaveName(
        game: Game,
        coreID: CoreID,
        index: Int
    ): String? = withContext(Dispatchers.IO) {
        val fileName = getSlotSaveFileName(game, index)
        val metadataFile = getMetadataStateFile(fileName, coreID.coreName)
        if (metadataFile.exists()) {
            val stateMetadata = runCatching {
                Json.Default.decodeFromString(
                    SaveState.Metadata.serializer(),
                    metadataFile.readTextAtomic(),
                )
            }.getOrNull()
            stateMetadata?.name
        } else {
            null
        }
    }

    private suspend fun getSaveState(
        fileName: String,
        coreName: String,
    ): SaveState? {
        return runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val saveFile = getStateFile(fileName, coreName)
            val metadataFile = getMetadataStateFile(fileName, coreName)
            if (saveFile.exists()) {
                val byteArray = saveFile.readBytesUncompressedAtomic()
                val stateMetadata =
                    runCatching {
                        Json.Default.decodeFromString(
                            SaveState.Metadata.serializer(),
                            metadataFile.readTextAtomic(),
                        )
                    }
                SaveState(byteArray, stateMetadata.getOrNull() ?: SaveState.Metadata())
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun setSaveState(
        fileName: String,
        coreName: String,
        saveState: SaveState,
    ) {
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            writeStateToDisk(fileName, coreName, saveState.state)
            writeMetadataToDisk(fileName, coreName, saveState.metadata)
        }
    }

    private fun writeMetadataToDisk(
        fileName: String,
        coreName: String,
        metadata: SaveState.Metadata,
    ) {
        val metadataFile = getMetadataStateFile(fileName, coreName)
        metadataFile.writeTextAtomic(Json.encodeToString(SaveState.Metadata.serializer(), metadata))
    }

    private fun writeStateToDisk(
        fileName: String,
        coreName: String,
        stateArray: ByteArray,
    ) {
        val saveFile = getStateFile(fileName, coreName)
        saveFile.writeBytesCompressedAtomic(stateArray)
    }

    private fun getStateFile(
        fileName: String,
        coreName: String,
    ): File {
        val statesDirectories = File(directoriesManager.getStatesDirectory(), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getMetadataStateFile(
        stateFileName: String,
        coreName: String,
    ): File {
        val statesDirectories = File(directoriesManager.getStatesDirectory(), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, "$stateFileName.metadata")
    }

    suspend fun importSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int,
        stateBytes: ByteArray
    ) = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        val fileName = getSlotSaveFileName(game, index)
        val saveFile = getStateFile(fileName, coreID.coreName)
        saveFile.writeBytesAtomic(stateBytes)
        
        // Also write metadata
        val metadata = SaveState.Metadata(name = "Imported Slot ${index + 1}")
        val metadataFile = getMetadataStateFile(fileName, coreID.coreName)
        metadataFile.writeTextAtomic(kotlinx.serialization.json.Json.encodeToString(SaveState.Metadata.serializer(), metadata))
    }

    suspend fun exportSlotSave(
        game: Game,
        coreID: CoreID,
        index: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        assert(index in 0 until MAX_STATES)
        val fileName = getSlotSaveFileName(game, index)
        val saveFile = getStateFile(fileName, coreID.coreName)
        if (saveFile.exists()) {
            saveFile.readBytes()
        } else {
            null
        }
    }

    private fun getAutoSaveFileName(game: Game) = "${game.fileName}.state"

    private fun getSlotSaveFileName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}"

    companion object {
        const val MAX_STATES = 4
        private const val FILE_ACCESS_RETRIES = 3
    }
}
