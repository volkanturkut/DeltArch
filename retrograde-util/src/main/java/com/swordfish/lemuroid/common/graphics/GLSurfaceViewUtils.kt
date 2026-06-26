package com.swordfish.lemuroid.common.graphics

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import android.view.PixelCopy
import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

suspend fun GLSurfaceView.takeScreenshot(
    maxResolution: Int,
    retries: Int = 1,
): Bitmap? =
    withContext(Dispatchers.Main) {
        runCatchingWithRetry(retries) {
            takeScreenshot(maxResolution)
        }.getOrNull()
    }

private suspend fun GLSurfaceView.takeScreenshot(maxResolution: Int): Bitmap? =
    suspendCancellableCoroutine { cont ->
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        queueEvent {
            try {
                val outputScaling = maxResolution / maxOf(width, height).toFloat()
                val inputScaling = outputScaling * 2

                val inputBitmap =
                    createBitmap(
                        (width * inputScaling).roundToInt(),
                        (height * inputScaling).roundToInt(),
                        Bitmap.Config.ARGB_8888,
                    )

                val onCompleted = { result: Int ->
                    if (result == PixelCopy.SUCCESS) {
                        // This rescaling limits the artifacts introduced by shaders.
                        val scaledBitmap =
                            inputBitmap.scale(
                                (width * outputScaling).roundToInt(),
                                (height * outputScaling).roundToInt(),
                            )

                        val glRetroViewClass = try {
                            Class.forName("com.swordfish.libretrodroid.GLRetroView")
                        } catch (e: Exception) {
                            null
                        }

                        val viewport = if (glRetroViewClass != null && glRetroViewClass.isInstance(this@takeScreenshot)) {
                            try {
                                val getViewportMethod = glRetroViewClass.getMethod("getViewport")
                                getViewportMethod.invoke(this@takeScreenshot) as? android.graphics.RectF
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }

                        val croppedBitmap = if (viewport != null) {
                            val W = scaledBitmap.width
                            val H = scaledBitmap.height

                            val leftX = minOf(viewport.left, viewport.right)
                            val rightX = maxOf(viewport.left, viewport.right)
                            val topY = minOf(viewport.top, viewport.bottom)
                            val bottomY = maxOf(viewport.top, viewport.bottom)

                            val cropLeft = (leftX * W).roundToInt().coerceIn(0, W)
                            val cropRight = (rightX * W).roundToInt().coerceIn(0, W)
                            val cropWidth = (cropRight - cropLeft).coerceAtLeast(1)

                            val cropTop = (topY * H).roundToInt().coerceIn(0, H)
                            val cropBottom = (bottomY * H).roundToInt().coerceIn(0, H)
                            val cropHeight = (cropBottom - cropTop).coerceAtLeast(1)

                            val safeWidth = minOf(cropWidth, W - cropLeft)
                            val safeHeight = minOf(cropHeight, H - cropTop)

                            Bitmap.createBitmap(scaledBitmap, cropLeft, cropTop, safeWidth, safeHeight)
                        } else {
                            scaledBitmap
                        }

                        val finalBitmap = cropBlackBars(croppedBitmap)
                        cont.resume(finalBitmap)
                    } else {
                        cont.resumeWithException(RuntimeException("Cannot take screenshot. Error code: $result"))
                    }
                }
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                PixelCopy.request(this, inputBitmap, onCompleted, mainHandler)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }

fun cropBlackBars(bitmap: Bitmap): Bitmap {
    val W = bitmap.width
    val H = bitmap.height
    val pixels = IntArray(W * H)
    bitmap.getPixels(pixels, 0, W, 0, 0, W, H)

    var top = 0
    var bottom = H - 1
    var left = 0
    var right = W - 1

    fun isBlack(color: Int): Boolean {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return r < 15 && g < 15 && b < 15
    }

    // Find top boundary
    while (top < H) {
        var rowIsBlack = true
        val offset = top * W
        for (x in 0 until W) {
            if (!isBlack(pixels[offset + x])) {
                rowIsBlack = false
                break
            }
        }
        if (!rowIsBlack) break
        top++
    }

    // Find bottom boundary
    while (bottom > top) {
        var rowIsBlack = true
        val offset = bottom * W
        for (x in 0 until W) {
            if (!isBlack(pixels[offset + x])) {
                rowIsBlack = false
                break
            }
        }
        if (!rowIsBlack) break
        bottom--
    }

    // Find left boundary
    while (left < W) {
        var colIsBlack = true
        for (y in top..bottom) {
            if (!isBlack(pixels[y * W + left])) {
                colIsBlack = false
                break
            }
        }
        if (!colIsBlack) break
        left++
    }

    // Find right boundary
    while (right > left) {
        var colIsBlack = true
        for (y in top..bottom) {
            if (!isBlack(pixels[y * W + right])) {
                colIsBlack = false
                break
            }
        }
        if (!colIsBlack) break
        right--
    }

    val cropWidth = right - left + 1
    val cropHeight = bottom - top + 1

    if (cropWidth < 20 || cropHeight < 20) {
        return bitmap
    }

    return Bitmap.createBitmap(bitmap, left, top, cropWidth, cropHeight)
}
