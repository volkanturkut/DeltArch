package com.swordfish.lemuroid.app.shared.skins

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.swordfish.lemuroid.app.shared.skins.models.AssetsInfo
import com.swordfish.lemuroid.app.shared.skins.models.LayoutInfo
import java.io.File

object SkinAssetLoader {
    private val bitmapCache = LruCache<String, Bitmap>(20)

    fun resolveAssetName(assets: AssetsInfo): String? {
        return assets.resizable ?: assets.large ?: assets.medium ?: assets.small
    }

    fun resolveAssetFile(
        skinPackage: SkinPackage,
        layout: LayoutInfo,
    ): File? {
        val assetName = resolveAssetName(layout.assets) ?: return null
        return resolveAssetFileByName(skinPackage, assetName)
    }

    fun resolveAssetFileByName(
        skinPackage: SkinPackage,
        assetName: String,
    ): File? {
        return File(skinPackage.dir, assetName).takeIf { it.exists() && it.isFile }
    }

    fun loadBitmap(file: File, scale: Float = 1f): Bitmap? {
        val cacheKey = getCacheKey(file, scale)
        bitmapCache.get(cacheKey)?.let { return it }

        val bitmap = if (file.extension.equals("pdf", ignoreCase = true)) {
            renderPdf(file, scale)
        } else {
            BitmapFactory.decodeFile(file.absolutePath)
        }

        if (bitmap != null) {
            bitmapCache.put(cacheKey, bitmap)
        }
        return bitmap
    }

    fun getCachedBitmap(skinPackage: SkinPackage, layout: LayoutInfo, scale: Float): Bitmap? {
        val assetName = resolveAssetName(layout.assets) ?: return null
        val file = File(skinPackage.dir, assetName)
        return bitmapCache.get(getCacheKey(file, scale))
    }

    private fun getCacheKey(file: File, scale: Float): String {
        return "${file.absolutePath}_$scale"
    }

    private fun renderPdf(file: File, scale: Float = 1f): Bitmap? {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount == 0) return null

                    renderer.openPage(0).use { page ->
                        // Scale up the bitmap for higher resolution
                        val width = (page.width * scale * 2f).toInt()
                        val height = (page.height * scale * 2f).toInt()
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }
}
