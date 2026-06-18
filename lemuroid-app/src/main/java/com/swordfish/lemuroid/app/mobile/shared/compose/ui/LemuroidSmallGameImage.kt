package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.db.entity.Game

private fun coverAspectRatioForSystem(systemId: String): Float = when (systemId) {
    // Horizontal Cardboard Boxes
    "snes" -> 1.40f
    "n64" -> 1.43f
    "gg" -> 1.40f

    // Square Boxes (Handhelds & NA NES)
    "gb", "gbc", "gba" -> 1.0f
    "nes" -> 1.02f

    // Plastic Cases (Square-ish)
    "psx" -> 1.15f
    "nds", "3ds" -> 0.90f

    // Vertical Boxes/Cases
    "md", "sms" -> 0.70f
    "atari2600" -> 0.74f
    "fbneo", "mame2003plus" -> 0.71f
    "psp" -> 0.59f

    // Default to portrait 2:3
    else -> 2f / 3f
}

@Composable
fun LemuroidSmallGameImage(
    modifier: Modifier = Modifier,
    game: Game,
) {
    val context = LocalContext.current
    val grayscaleMatrix = remember {
        ColorMatrix().apply { setToSaturation(0f) }
    }
    val ratio = coverAspectRatioForSystem(game.systemId)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = Modifier
                .then(if (ratio > 1f) Modifier.fillMaxWidth() else Modifier.fillMaxHeight())
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(4.dp))
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(game.coverFrontUrl)
                    .build(),
                contentDescription = game.title,
                modifier = Modifier
                    .fillMaxSize(),
                error = painterResource(id = R.drawable.lemuroid_launcher_monochrome),
                fallback = painterResource(id = R.drawable.lemuroid_launcher_monochrome),
                contentScale = ContentScale.FillBounds,
                colorFilter = if (game.coverFrontUrl.isNullOrBlank()) {
                    ColorFilter.colorMatrix(grayscaleMatrix)
                } else {
                    null
                }
            )
        }
    }
}
