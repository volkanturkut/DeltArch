package com.swordfish.lemuroid.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

@Composable
fun Int.pxToDp() = this.pxToDp(LocalDensity.current)

fun Int.pxToDp(density: Density) = with(density) { this@pxToDp.toDp() }

@Composable
fun Dp.textUnit(): TextUnit {
    return this.textUnit(LocalDensity.current)
}

fun Dp.textUnit(density: Density): TextUnit {
    return with(density) { this@textUnit.toSp() }
}

@Composable
fun TextUnit.dp(): Dp {
    return this.dp(LocalDensity.current)
}

fun TextUnit.dp(density: Density): Dp {
    return with(density) { this@dp.toDp() }
}
