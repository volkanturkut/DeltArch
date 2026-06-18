package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
) {
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f), // Ensure all game cards are the exact same 1:1 size container
        contentAlignment = Alignment.BottomCenter // Align to bottom so the gap to the text underneath is always identical
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(game.coverFrontUrl)
                .build(),
            contentDescription = game.title,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)), // Clips the actual image bounds, perfectly rounding the visible edges!
            contentScale = ContentScale.Fit, // Keep aspect ratio perfectly
            error = {
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.lemuroid_launcher),
                        contentDescription = "No Cover",
                        colorFilter = ColorFilter.colorMatrix(grayscaleMatrix),
                        modifier = Modifier.fillMaxSize(0.7f).clip(RoundedCornerShape(4.dp)), // scale it down slightly inside the box
                        contentScale = ContentScale.Fit
                    )
                }
            }
        )
    }
}
