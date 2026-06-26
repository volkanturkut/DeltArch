package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.db.entity.Game

@Composable
fun LemuroidGameImage(
    modifier: Modifier = Modifier,
    game: Game,
    showGameplayPreview: Boolean = false,
    onClick: () -> Unit = {},
    cornerRadius: androidx.compose.ui.unit.Dp = 4.dp,
    aspectRatio: Float = 1f,
    imageModifier: Modifier = Modifier,
    fallbackBitmap: android.graphics.Bitmap? = null,
    tempPreviewSlotId: String? = null
) {
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }

    val actions = LocalGameActions.current
    var previewView by remember { mutableStateOf<android.view.View?>(null) }
    val context = LocalContext.current

    DisposableEffect(showGameplayPreview, game) {
        var originalPreviewId: String? = null
        val sharedPref = context.getSharedPreferences("locked_save_states", android.content.Context.MODE_PRIVATE)
        if (showGameplayPreview) {
            if (tempPreviewSlotId != null) {
                originalPreviewId = sharedPref.getString("preview_${game.id}", null)
                sharedPref.edit().putString("preview_${game.id}", tempPreviewSlotId).apply()
            }
            actions?.startPreview?.invoke(game, onClick) { view ->
                previewView = view
            }
        }
        onDispose {
            if (showGameplayPreview) {
                actions?.stopPreview?.invoke()
                previewView = null
                if (tempPreviewSlotId != null) {
                    if (originalPreviewId != null) {
                        sharedPref.edit().putString("preview_${game.id}", originalPreviewId).apply()
                    } else {
                        sharedPref.edit().remove("preview_${game.id}").apply()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        contentAlignment = Alignment.BottomCenter
    ) {
        Crossfade(
            targetState = if (showGameplayPreview) previewView else null,
            modifier = Modifier.fillMaxSize(),
            label = "previewCrossfade"
        ) { activeView ->
            if (activeView != null) {
                // Game is running — fill the container with no black bars and support outline clipping for SurfaceView
                val density = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        (activeView.parent as? android.view.ViewGroup)?.removeView(activeView)
                        android.widget.FrameLayout(context).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            addView(
                                activeView,
                                android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )
                            clipToOutline = true
                        }
                    },
                    update = { container ->
                        val radiusPx = with(density) { cornerRadius.toPx() }
                        clipViewHierarchy(container, radiusPx)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .then(imageModifier)
                        .clip(RoundedCornerShape(cornerRadius))
                )
            } else if (showGameplayPreview) {
                // Game is loading — show fallback bitmap if available, otherwise a clean white screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(cornerRadius))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (fallbackBitmap != null) {
                        Image(
                            bitmap = fallbackBitmap.asImageBitmap(),
                            contentDescription = game.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            } else {
                // Normal grid card — show box art or fallback bitmap
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (fallbackBitmap != null) {
                        Image(
                            bitmap = fallbackBitmap.asImageBitmap(),
                            contentDescription = game.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(imageModifier)
                                .clip(RoundedCornerShape(cornerRadius)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        SubcomposeAsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(game.coverFrontUrl)
                                .build(),
                            contentDescription = game.title,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(imageModifier)
                                .clip(RoundedCornerShape(cornerRadius)),
                            contentScale = ContentScale.Fit,
                            alignment = Alignment.BottomCenter,
                            error = {
                                Box(
                                    contentAlignment = Alignment.BottomCenter,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Image(
                                        painter = painterResource(id = R.mipmap.lemuroid_launcher),
                                        contentDescription = "No Cover",
                                        colorFilter = ColorFilter.colorMatrix(grayscaleMatrix),
                                        modifier = Modifier
                                            .fillMaxSize(0.7f)
                                            .clip(RoundedCornerShape(cornerRadius)),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun clipViewHierarchy(view: android.view.View, radiusPx: Float) {
    view.clipToOutline = true
    view.outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(v: android.view.View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
        }
    }
    view.invalidateOutline()
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            clipViewHierarchy(view.getChildAt(i), radiusPx)
        }
    }
}
