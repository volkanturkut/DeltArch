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
                        val outputBitmap =
                            inputBitmap.scale(
                                (width * outputScaling).roundToInt(),
                                (height * outputScaling).roundToInt(),
                            )

                        cont.resume(outputBitmap)
                    } else {
                        cont.resumeWithException(RuntimeException("Cannot take screenshot. Error code: $result"))
                    }
                }
                PixelCopy.request(this, inputBitmap, onCompleted, handler)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }
    }
