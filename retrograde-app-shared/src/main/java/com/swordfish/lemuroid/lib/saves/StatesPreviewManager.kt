@file:Suppress("all")
package com.swordfish.lemuroid.lib.saves

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.swordfish.lemuroid.common.graphics.cropBlackBars

class StatesPreviewManager(private val directoriesManager: DirectoriesManager) {
    private fun scaleBitmapPreservingAspectRatio(bitmap: Bitmap, size: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxDim = maxOf(width, height)
        if (maxDim <= size) return bitmap
        val scale = size.toFloat() / maxDim
        val targetWidth = Math.round(width * scale)
        val targetHeight = Math.round(height * scale)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    suspend fun getPreviewForSlot(
        game: Game,
        coreID: CoreID,
        index: Int,
        size: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val screenshotName = getSlotScreenshotName(game, index)
            val file = getPreviewFile(screenshotName, coreID.coreName)
            if (!file.exists()) return@withContext null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) return@withContext null
            val cropped = cropBlackBars(bitmap)
            scaleBitmapPreservingAspectRatio(cropped, size)
        }

    suspend fun getPreviewForAutoSave(
        game: Game,
        coreID: CoreID,
        size: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            val screenshotName = "${game.fileName}.state.jpg"
            val file = getPreviewFile(screenshotName, coreID.coreName)
            if (!file.exists()) return@withContext null
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap == null) return@withContext null
            val cropped = cropBlackBars(bitmap)
            scaleBitmapPreservingAspectRatio(cropped, size)
        }

    suspend fun setPreviewForSlot(
        game: Game,
        bitmap: Bitmap,
        coreID: CoreID,
        index: Int,
    ) = withContext(Dispatchers.IO) {
        val screenshotName = getSlotScreenshotName(game, index)
        val file = getPreviewFile(screenshotName, coreID.coreName)
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
    }

    suspend fun deletePreviewForSlot(
        game: Game,
        coreID: CoreID,
        index: Int
    ) = withContext(Dispatchers.IO) {
        val screenshotName = getSlotScreenshotName(game, index)
        val file = getPreviewFile(screenshotName, coreID.coreName)
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun setPreviewForAutoSave(
        game: Game,
        bitmap: Bitmap,
        coreID: CoreID,
    ) = withContext(Dispatchers.IO) {
        val screenshotName = "${game.fileName}.state.jpg"
        val file = getPreviewFile(screenshotName, coreID.coreName)
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
        }
    }

    suspend fun deletePreviewForAutoSave(
        game: Game,
        coreID: CoreID,
    ) = withContext(Dispatchers.IO) {
        val screenshotName = "${game.fileName}.state.jpg"
        val file = getPreviewFile(screenshotName, coreID.coreName)
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun copySlotPreviewToAutoSave(game: Game, coreID: CoreID, index: Int) = withContext(Dispatchers.IO) {
        val slotScreenshotName = getSlotScreenshotName(game, index)
        val slotFile = getPreviewFile(slotScreenshotName, coreID.coreName)
        val autoSaveScreenshotName = "${game.fileName}.state.jpg"
        val autoSaveFile = getPreviewFile(autoSaveScreenshotName, coreID.coreName)
        if (slotFile.exists()) {
            slotFile.copyTo(autoSaveFile, overwrite = true)
        } else {
            if (autoSaveFile.exists()) {
                autoSaveFile.delete()
            }
        }
    }

    private fun getPreviewFile(
        fileName: String,
        coreName: String,
    ): File {
        val statesDirectories = File(directoriesManager.getStatesPreviewDirectory(), coreName)
        statesDirectories.mkdirs()
        return File(statesDirectories, fileName)
    }

    private fun getSlotScreenshotName(
        game: Game,
        index: Int,
    ) = "${game.fileName}.slot${index + 1}.jpg"

    companion object {
        val PREVIEW_SIZE_DP = 240f
    }
}
