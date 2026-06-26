package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

class BubbleShape(
    val arrowPosition: String,
    val arrowHeight: Float,
    val arrowWidth: Float,
    val cornerRadius: Float,
    val arrowX: Float,
    val arrowY: Float
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val rectPath = Path().apply {
            val arrowW = arrowWidth
            val radius = cornerRadius
            if (arrowPosition == "LEFT") {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(arrowW, 0f, size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                    )
                )
            } else if (arrowPosition == "RIGHT") {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width - arrowW, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                    )
                )
            } else { // "TOP"
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(0f, arrowW, size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius)
                    )
                )
            }
        }
        
        val trianglePath = Path().apply {
            val arrowH = arrowHeight
            val arrowW = arrowWidth
            if (arrowPosition == "LEFT") {
                moveTo(arrowW, arrowY - arrowH / 2f)
                lineTo(0f, arrowY)
                lineTo(arrowW, arrowY + arrowH / 2f)
                close()
            } else if (arrowPosition == "RIGHT") {
                val rectRight = size.width - arrowW
                moveTo(rectRight, arrowY - arrowH / 2f)
                lineTo(size.width, arrowY)
                lineTo(rectRight, arrowY + arrowH / 2f)
                close()
            } else { // "TOP"
                moveTo(arrowX - arrowH / 2f, arrowW)
                lineTo(arrowX, 0f)
                lineTo(arrowX + arrowH / 2f, arrowW)
                close()
            }
        }
        
        val combinedPath = Path.combine(PathOperation.Union, rectPath, trianglePath)
        return Outline.Generic(combinedPath)
    }
}

class BubblePopupPositionProvider(
    val density: Density,
    val onPositionCalculated: (arrowX: Float, arrowY: Float, arrowPosition: String) -> Unit
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val marginPx = with(density) { 8.dp.toPx() }
        
        val fitsOnRight = anchorBounds.right + marginPx + popupContentSize.width <= windowSize.width - marginPx
        val fitsOnLeft = anchorBounds.left - marginPx - popupContentSize.width >= marginPx

        val arrowPosition: String
        val x: Int
        val y: Int
        val arrowX: Float
        val arrowY: Float

        if (fitsOnRight) {
            arrowPosition = "LEFT"
            x = anchorBounds.right + marginPx.toInt()
            y = anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
            val clampedY = y.coerceIn(16, windowSize.height - popupContentSize.height - 16)
            arrowX = 0f
            arrowY = popupContentSize.height / 2f
            onPositionCalculated(arrowX, arrowY, arrowPosition)
            return IntOffset(x, clampedY)
        } else if (fitsOnLeft) {
            arrowPosition = "RIGHT"
            x = anchorBounds.left - marginPx.toInt() - popupContentSize.width
            y = anchorBounds.top + anchorBounds.height / 2 - popupContentSize.height / 2
            val clampedY = y.coerceIn(16, windowSize.height - popupContentSize.height - 16)
            arrowX = 0f
            arrowY = popupContentSize.height / 2f
            onPositionCalculated(arrowX, arrowY, arrowPosition)
            return IntOffset(x, clampedY)
        } else {
            arrowPosition = "TOP"
            x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
                .coerceIn(16, windowSize.width - popupContentSize.width - 16)
            y = anchorBounds.bottom
            arrowX = popupContentSize.width / 2f
            arrowY = 0f
            onPositionCalculated(arrowX, arrowY, arrowPosition)
            return IntOffset(x, y)
        }
    }
}

@Composable
fun ChangeArtworkDialog(
    onDismissRequest: () -> Unit,
    onUriSelected: (Uri) -> Unit,
    onOpenGameDatabase: () -> Unit,
    onShowError: () -> Unit
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                onUriSelected(uri)
            }
            onDismissRequest()
        }
    )

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                onUriSelected(uri)
            }
            onDismissRequest()
        }
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    
    val arrowHeightPx = with(density) { 40.dp.toPx() } // Widened base width
    val arrowWidthPx = with(density) { 16.dp.toPx() }
    val cornerRadiusPx = with(density) { 24.dp.toPx() }
    
    var arrowXState by remember { mutableStateOf(0f) }
    var arrowYState by remember { mutableStateOf(100f) }
    var arrowPositionState by remember { mutableStateOf("LEFT") }
    
    val bubbleShape = remember(arrowPositionState, arrowHeightPx, arrowWidthPx, cornerRadiusPx, arrowXState, arrowYState) {
        BubbleShape(
            arrowPosition = arrowPositionState,
            arrowHeight = arrowHeightPx,
            arrowWidth = arrowWidthPx,
            cornerRadius = cornerRadiusPx,
            arrowX = arrowXState,
            arrowY = arrowYState
        )
    }
    
    val popupPositionProvider = remember {
        BubblePopupPositionProvider(density) { arrowX, arrowY, arrowPosition ->
            arrowXState = arrowX
            arrowYState = arrowY
            arrowPositionState = arrowPosition
        }
    }

    Popup(
        popupPositionProvider = popupPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .width(240.dp)
                .shadow(8.dp, shape = bubbleShape)
                .clip(bubbleShape)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(
                    start = if (arrowPositionState == "LEFT") 28.dp else 12.dp,
                    end = if (arrowPositionState == "RIGHT") 28.dp else 12.dp,
                    top = if (arrowPositionState == "TOP") 28.dp else 12.dp,
                    bottom = 12.dp
                )
        ) {
            Column {
                ArtworkOption(
                    text = "Clipboard",
                    icon = Icons.Default.ContentPaste,
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clipData = clipboard.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val uri = clipData.getItemAt(0).uri
                            if (uri != null) {
                                onUriSelected(uri)
                                onDismissRequest()
                                return@ArtworkOption
                            }
                        }
                        onShowError()
                        onDismissRequest()
                    }
                )

                ArtworkOption(
                    text = "Photo Library",
                    icon = Icons.Default.PhotoLibrary,
                    onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )

                ArtworkOption(
                    text = "Game Database",
                    icon = Icons.Default.Storage,
                    onClick = {
                        onDismissRequest()
                        onOpenGameDatabase()
                    }
                )

                ArtworkOption(
                    text = "Files",
                    icon = Icons.Default.Folder,
                    onClick = {
                        filePickerLauncher.launch(arrayOf("image/*"))
                    }
                )
            }
        }
    }
}

@Composable
private fun ArtworkOption(
    modifier: Modifier = Modifier,
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF333333))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp
        )
    }
}
