package com.swordfish.lemuroid.app.shared.skins

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.swordfish.lemuroid.app.shared.skins.models.ItemInfo
import com.swordfish.lemuroid.app.shared.skins.models.LayoutInfo
import com.swordfish.lemuroid.app.shared.skins.models.RectInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@Composable
fun DeltaSkinView(
    modifier: Modifier = Modifier,
    skinPackage: SkinPackage,
    layout: LayoutInfo,
    onBitmapLoaded: (Boolean) -> Unit = {},
    onButtonStateChanged: (keyCode: Int, pressed: Boolean) -> Unit,
    onDpadStateChanged: (xAxis: Float, yAxis: Float) -> Unit,
    onLeftAnalogStateChanged: (xAxis: Float, yAxis: Float) -> Unit = { _, _ -> },
    onRightAnalogStateChanged: (xAxis: Float, yAxis: Float) -> Unit = { _, _ -> },
    onPointerEvent: (x: Float, y: Float, pressed: Boolean) -> Unit = { _, _, _ -> },
    onScreenGapChanged: (Int) -> Unit = { _ -> },
    onViewportPositioned: (Rect?) -> Unit
) {
    val systemId = skinPackage.systemId
    val density = androidx.compose.ui.platform.LocalDensity.current

    val bitmap by produceState<android.graphics.Bitmap?>(null, skinPackage, layout) {
        onBitmapLoaded(false)
        val loadedBitmap = withContext(Dispatchers.IO) {
            SkinAssetLoader.resolveAssetFile(skinPackage, layout)?.let { SkinAssetLoader.loadBitmap(it, density.density) }
        }
        value = loadedBitmap
        if (loadedBitmap != null) {
            onBitmapLoaded(true)
        }
    }

    val thumbstickBitmaps = remember(skinPackage, layout) { mutableStateMapOf<String, android.graphics.Bitmap?>() }
    LaunchedEffect(skinPackage, layout) {
        withContext(Dispatchers.IO) {
            layout.items.forEach { item ->
                item.thumbstick?.let { ts ->
                    if (!thumbstickBitmaps.containsKey(ts.name)) {
                        SkinAssetLoader.resolveAssetFileByName(skinPackage, ts.name)?.let {
                            thumbstickBitmaps[ts.name] = SkinAssetLoader.loadBitmap(it, density.density)
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val W = constraints.maxWidth.toFloat()
        val H = constraints.maxHeight.toFloat()

        val layoutW = layout.mappingSize.width
        val layoutH = layout.mappingSize.height

        val scale = minOf(W / layoutW, H / layoutH)
        val offsetX = (W - layoutW * scale) / 2
        val offsetY =
            if (layout.screens.isEmpty() && layout.gameScreenFrame == null && H > W) {
                H - layoutH * scale
            } else {
                (H - layoutH * scale) / 2
            }

        // Report emulated screen position if screens are defined in this layout
        LaunchedEffect(W, H, scale, offsetX, offsetY, layout) {
            val screens = if (layout.screens.isNotEmpty()) {
                layout.screens.mapNotNull { it.outputFrame }
            } else if (layout.gameScreenFrame != null) {
                listOf(layout.gameScreenFrame)
            } else {
                emptyList()
            }

            if (screens.isNotEmpty()) {
                if (systemId == "nds" && screens.size >= 2) {
                    val gap = if (H > W) {
                        // Portrait: top-bottom
                        val top = screens[0]
                        val bottom = screens[1]
                        val gapPx = (bottom.y - (top.y + top.height))
                        val dsPixelScale = 192f / top.height
                        (gapPx * dsPixelScale).toInt()
                    } else {
                        // Landscape: side-by-side
                        val left = screens[0]
                        val right = screens[1]
                        val gapPx = (right.x - (left.x + left.width))
                        // NDS screen width is 256
                        val dsPixelScale = 256f / left.width
                        (gapPx * dsPixelScale).toInt()
                    }
                    // Some cores might treat gap differently in landscape, 
                    // but we report what we calculate from the skin.
                    onScreenGapChanged(gap.coerceIn(0, 126))
                }
                onViewportPositioned(screens.toUnionRect(scale, offsetX, offsetY))
            } else {
                onViewportPositioned(getFallbackViewport(W, H, systemId, offsetY))
            }
        }

        // Draw background image
        val renderedBitmap = bitmap
        if (renderedBitmap != null) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
            ) {
                drawImage(
                    image = renderedBitmap.asImageBitmap(),
                    dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                    dstSize = IntSize((layoutW * scale).toInt(), (layoutH * scale).toInt()),
                )

                val screenRects = if (layout.screens.isNotEmpty()) {
                    layout.screens.mapNotNull { it.outputFrame }
                } else if (layout.gameScreenFrame != null) {
                    listOf(layout.gameScreenFrame)
                } else {
                    emptyList()
                }

                screenRects.forEach { screen ->
                    drawRect(
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        topLeft = Offset(
                            x = screen.x * scale + offsetX,
                            y = screen.y * scale + offsetY,
                        ),
                        size = Size(
                            width = screen.width * scale,
                            height = screen.height * scale,
                        ),
                        blendMode = BlendMode.Clear,
                    )
                }
            }
        }

        // Multi-touch tracking overlay
        val activeTouches = remember { mutableStateMapOf<PointerId, Offset>() }
        val pressedButtons = remember { mutableStateMapOf<Int, Boolean>() }
        var currentDpadX by remember { mutableStateOf(0f) }
        var currentDpadY by remember { mutableStateOf(0f) }
        var currentLeftAnalogX by remember { mutableStateOf(0f) }
        var currentLeftAnalogY by remember { mutableStateOf(0f) }
        var currentRightAnalogX by remember { mutableStateOf(0f) }
        var currentRightAnalogY by remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(layout, scale, offsetX, offsetY) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            // Update active touches map
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    activeTouches[change.id] = change.position
                                } else {
                                    activeTouches.remove(change.id)
                                }
                            }

                            // Evaluate pressed buttons and DPAD from all active touches
                            val newPressedButtons = mutableSetOf<Int>()
                            var newDpadX = 0f
                            var newDpadY = 0f
                            var newLeftAnalogX = 0f
                            var newLeftAnalogY = 0f
                            var newRightAnalogX = 0f
                            var newRightAnalogY = 0f
                            var screenTouch: Offset? = null

                            activeTouches.values.forEach { touchPos ->
                                var touchConsumedByItem = false
                                val virtualX = (touchPos.x - offsetX) / scale
                                val virtualY = (touchPos.y - offsetY) / scale

                                layout.items.forEach { item ->
                                    if (isTouchInItem(virtualX, virtualY, item)) {
                                        val inputs = item.inputs
                                        if (inputs != null) {
                                            if (inputs.isTouchScreen()) {
                                                screenTouch = touchPos
                                                touchConsumedByItem = true
                                            } else {
                                                touchConsumedByItem = true
                                                if (inputs.buttons.isNotEmpty()) {
                                                    inputs.buttons.forEach { btnName ->
                                                        mapButtonNameToKeyCode(btnName)?.let { keyCode ->
                                                            newPressedButtons.add(keyCode)
                                                        }
                                                    }
                                                }
                                                if (inputs.isDpad()) {
                                                    val (xAxis, yAxis) = item.directionFor(virtualX, virtualY, discrete = true)
                                                    newDpadX = xAxis
                                                    newDpadY = yAxis
                                                }
                                                if (inputs.isLeftAnalog()) {
                                                    val (xAxis, yAxis) = item.directionFor(virtualX, virtualY, discrete = false)
                                                    newLeftAnalogX = xAxis
                                                    newLeftAnalogY = yAxis
                                                }
                                                if (inputs.isRightAnalog()) {
                                                    val (xAxis, yAxis) = item.directionFor(virtualX, virtualY, discrete = false)
                                                    newRightAnalogX = xAxis
                                                    newRightAnalogY = yAxis
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!touchConsumedByItem) {
                                    screenTouch = touchPos
                                }
                            }

                            // Send release events for buttons that are no longer pressed
                            pressedButtons.keys.toList().forEach { keyCode ->
                                if (!newPressedButtons.contains(keyCode)) {
                                    onButtonStateChanged(keyCode, false)
                                    pressedButtons.remove(keyCode)
                                }
                            }

                            // Send press events for newly pressed buttons
                            newPressedButtons.forEach { keyCode ->
                                if (!pressedButtons.containsKey(keyCode)) {
                                    onButtonStateChanged(keyCode, true)
                                    pressedButtons[keyCode] = true
                                }
                            }

                            // Send DPAD changes if updated
                            if (newDpadX != currentDpadX || newDpadY != currentDpadY) {
                                currentDpadX = newDpadX
                                currentDpadY = newDpadY
                                onDpadStateChanged(newDpadX, newDpadY)
                            }

                            currentLeftAnalogX = newLeftAnalogX
                            currentLeftAnalogY = newLeftAnalogY
                            currentRightAnalogX = newRightAnalogX
                            currentRightAnalogY = newRightAnalogY

                            onLeftAnalogStateChanged(newLeftAnalogX, newLeftAnalogY)
                            onRightAnalogStateChanged(newRightAnalogX, newRightAnalogY)

                            // Forward touch events for screens
                            if (screenTouch != null) {
                                onPointerEvent(screenTouch.x, screenTouch.y, true)
                            } else {
                                // Send release event (using dummy coordinates out of screen as GLRetroView does)
                                onPointerEvent(-1000f, -1000f, false)
                            }
                        }
                    }
                }
        ) {
            // Render thumbsticks
            layout.items.forEach { item ->
                item.thumbstick?.let { ts ->
                    val tsBitmap = thumbstickBitmaps[ts.name] ?: return@let
                    val centerX = item.frame.x * scale + offsetX + item.frame.width * scale / 2
                    val centerY = item.frame.y * scale + offsetY + item.frame.height * scale / 2
                    
                    val analogX = if (item.inputs?.isLeftAnalog() == true) currentLeftAnalogX else if (item.inputs?.isRightAnalog() == true) currentRightAnalogX else 0f
                    val analogY = if (item.inputs?.isLeftAnalog() == true) currentLeftAnalogY else if (item.inputs?.isRightAnalog() == true) currentRightAnalogY else 0f
                    
                    val maxOffset = item.frame.width * scale / 2
                    val drawX = centerX + analogX * maxOffset - ts.width * scale / 2
                    val drawY = centerY + analogY * maxOffset - ts.height * scale / 2
                    
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            val shrink = 0.75f
                            val w = ts.width * scale * shrink
                            val h = ts.height * scale * shrink
                            val dx = drawX + (ts.width * scale - w) / 2
                            val dy = drawY + (ts.height * scale - h) / 2
                            addOval(Rect(dx, dy, dx + w, dy + h))
                        }
                        drawIntoCanvas { canvas ->
                            canvas.save()
                            canvas.clipPath(path)
                            drawImage(
                                image = tsBitmap.asImageBitmap(),
                                dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                                dstSize = IntSize((ts.width * scale).toInt(), (ts.height * scale).toInt()),
                            )
                            canvas.restore()
                        }
                    }
                }
            }
        }
    }
}

private fun getFallbackViewport(
    screenWidth: Float,
    screenHeight: Float,
    systemId: String?,
    offsetY: Float,
): Rect? {
    val aspectRatio = when (systemId) {
        "gb", "gbc" -> 10f / 9f
        "gba" -> 3f / 2f
        "psp" -> 16f / 9f
        "nds" -> 2f / 3f
        "3ds" -> 20f / 27f
        else -> 4f / 3f
    }

    return if (screenHeight > screenWidth) {
        val targetH = screenWidth / aspectRatio
        val availableH = offsetY
        val top = (availableH - targetH) / 2
        Rect(0f, maxOf(0f, top), screenWidth, maxOf(0f, top) + targetH)
    } else {
        // Landscape: Center maintaining aspect ratio
        val targetH = screenHeight
        val targetW = targetH * aspectRatio
        val left = (screenWidth - targetW) / 2
        Rect(left, 0f, left + targetW, screenHeight)
    }
}

private fun List<RectInfo>.toUnionRect(
    scale: Float,
    offsetX: Float,
    offsetY: Float,
): Rect? {
    if (isEmpty()) return null

    val minX = minOf { it.x }
    val minY = minOf { it.y }
    val maxX = maxOf { it.x + it.width }
    val maxY = maxOf { it.y + it.height }

    return Rect(
        left = minX * scale + offsetX,
        top = minY * scale + offsetY,
        right = maxX * scale + offsetX,
        bottom = maxY * scale + offsetY,
    )
}

private fun isTouchInItem(vx: Float, vy: Float, item: ItemInfo): Boolean {
    val left = item.frame.x - (item.extendedEdges?.left ?: 0f)
    val top = item.frame.y - (item.extendedEdges?.top ?: 0f)
    val right = item.frame.x + item.frame.width + (item.extendedEdges?.right ?: 0f)
    val bottom = item.frame.y + item.frame.height + (item.extendedEdges?.bottom ?: 0f)
    return vx in left..right && vy in top..bottom
}

private fun ItemInfo.directionFor(
    virtualX: Float,
    virtualY: Float,
    discrete: Boolean,
): Pair<Float, Float> {
    val centerX = frame.x + frame.width / 2
    val centerY = frame.y + frame.height / 2
    val dx = virtualX - centerX
    val dy = virtualY - centerY
    val radius = minOf(frame.width, frame.height) / 2
    val deadzone = radius * 0.3f

    if (abs(dx) <= deadzone && abs(dy) <= deadzone) return 0f to 0f

    if (discrete) {
        var xAxis = 0f
        var yAxis = 0f
        if (abs(dx) > abs(dy) * 0.414f) xAxis = if (dx > 0) 1f else -1f
        if (abs(dy) > abs(dx) * 0.414f) yAxis = if (dy > 0) 1f else -1f
        return xAxis to yAxis
    }

    return (dx / radius).coerceIn(-1f, 1f) to (dy / radius).coerceIn(-1f, 1f)
}

private fun com.swordfish.lemuroid.app.shared.skins.models.InputsInfo.isDpad(): Boolean {
    val dpadValues = setOf("up", "down", "left", "right")
    return dpad.values.any { it.lowercase() in dpadValues }
}

private fun com.swordfish.lemuroid.app.shared.skins.models.InputsInfo.isLeftAnalog(): Boolean {
    val analogValues = setOf("analogstickup", "analogstickdown", "analogstickleft", "analogstickright")
    return dpad.values.any { it.lowercase() in analogValues }
}

private fun com.swordfish.lemuroid.app.shared.skins.models.InputsInfo.isRightAnalog(): Boolean {
    val analogValues = setOf("cup", "cdown", "cleft", "cright")
    return dpad.values.any { it.lowercase() in analogValues }
}

private fun com.swordfish.lemuroid.app.shared.skins.models.InputsInfo.isTouchScreen(): Boolean {
    return dpad.values.any { it.lowercase().contains("touchscreen") }
}

private fun mapButtonNameToKeyCode(name: String): Int? {
    return when (name.lowercase()) {
        "a" -> KeyEvent.KEYCODE_BUTTON_A
        "b" -> KeyEvent.KEYCODE_BUTTON_Y
        "x" -> KeyEvent.KEYCODE_BUTTON_X
        "y" -> KeyEvent.KEYCODE_BUTTON_Y
        "l", "l1" -> KeyEvent.KEYCODE_BUTTON_L1
        "r", "r1" -> KeyEvent.KEYCODE_BUTTON_R1
        "l2", "z" -> KeyEvent.KEYCODE_BUTTON_L2
        "r2" -> KeyEvent.KEYCODE_BUTTON_R2
        "start" -> KeyEvent.KEYCODE_BUTTON_START
        "select" -> KeyEvent.KEYCODE_BUTTON_SELECT
        "menu" -> KeyEvent.KEYCODE_BUTTON_MODE
        "cup" -> KeyEvent.KEYCODE_DPAD_UP
        "cdown" -> KeyEvent.KEYCODE_DPAD_DOWN
        "cleft" -> KeyEvent.KEYCODE_DPAD_LEFT
        "cright" -> KeyEvent.KEYCODE_DPAD_RIGHT
        else -> null
    }
}
