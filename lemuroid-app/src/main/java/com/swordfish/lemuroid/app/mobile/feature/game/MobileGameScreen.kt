@file:Suppress("all")

package com.swordfish.lemuroid.app.mobile.feature.game

import android.graphics.RectF
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.swordfish.lemuroid.app.shared.game.BaseGameScreenViewModel
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelTouchControls.Companion.MENU_LOADING_ANIMATION_MILLIS
import com.swordfish.lemuroid.app.shared.settings.HapticFeedbackMode
import com.swordfish.lemuroid.lib.controller.ControllerConfig
import com.swordfish.touchinput.controller.R
import com.swordfish.touchinput.radial.LemuroidPadTheme
import com.swordfish.touchinput.radial.LocalLemuroidPadTheme
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import com.swordfish.touchinput.radial.settings.TouchControllerSettingsManager
import com.swordfish.touchinput.radial.settings.TouchControllerID
import android.view.KeyEvent
import com.swordfish.lemuroid.app.shared.skins.SkinManager
import com.swordfish.lemuroid.app.shared.skins.DeltaSkinView
import com.swordfish.lemuroid.app.shared.skins.SkinAssetLoader
import com.swordfish.lemuroid.app.shared.skins.SkinPackage
import com.swordfish.lemuroid.app.shared.skins.models.LayoutInfo
import com.swordfish.lemuroid.lib.library.SystemID
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import gg.padkit.inputevents.InputEvent
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.touchinput.radial.ui.GlassSurface
import com.swordfish.touchinput.radial.ui.LemuroidButtonPressFeedback
import gg.padkit.PadKit
import gg.padkit.config.HapticFeedbackType
import gg.padkit.inputstate.InputState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MobileGameScreen(viewModel: BaseGameScreenViewModel) {
    val skinBitmapLoaded = remember { mutableStateOf(false) }
    val initialSkinLoaded = remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = constraints.maxWidth > constraints.maxHeight

        LaunchedEffect(isLandscape) {
            val orientation =
                if (isLandscape) {
                    TouchControllerSettingsManager.Orientation.LANDSCAPE
                } else {
                    TouchControllerSettingsManager.Orientation.PORTRAIT
                }
            viewModel.onScreenOrientationChanged(orientation)
        }

        val controllerConfigState = viewModel.getTouchControllerConfig().collectAsState(null)
        val touchControlsVisibleState = viewModel.isTouchControllerVisible().collectAsState(false)
        val touchControllerSettingsState =
            viewModel
                .getTouchControlsSettings(LocalDensity.current, WindowInsets.displayCutout)
                .collectAsState(null)

        val touchControllerSettings = touchControllerSettingsState.value
        val currentControllerConfig = controllerConfigState.value

        val context = LocalContext.current
        val skinManager = remember { SkinManager(context) }
        val systemId = remember(viewModel.system) {
            when (viewModel.system.id) {
                SystemID.NES -> "nes"
                SystemID.SNES -> "snes"
                SystemID.GB, SystemID.GBC -> "gbc"
                SystemID.GBA -> "gba"
                SystemID.N64 -> "n64"
                SystemID.NDS -> "nds"
                SystemID.GENESIS -> "genesis"
                else -> null
            }
        }
        val currentDensity = LocalDensity.current
        val activeSkin by produceState<SkinPackage?>(null, systemId) {
            value = withContext(Dispatchers.IO) {
                systemId?.let { id ->
                    val skin = skinManager.getSelectedSkin(id)
                    if (skin != null) {
                        // Prioritize current orientation
                        val currentLayout = skinManager.getLayoutForSkin(skin, isLandscape = isLandscape, isTablet = false)
                        currentLayout?.let { layout ->
                            SkinAssetLoader.resolveAssetFile(skin, layout)?.let { file ->
                                SkinAssetLoader.loadBitmap(file, currentDensity.density)
                            }
                        }
                        
                        // Load the other orientation in background
                        this@produceState.launch {
                            val otherLayout = skinManager.getLayoutForSkin(skin, isLandscape = !isLandscape, isTablet = false)
                            otherLayout?.let { layout ->
                                SkinAssetLoader.resolveAssetFile(skin, layout)?.let { file ->
                                    SkinAssetLoader.loadBitmap(file, currentDensity.density)
                                }
                            }
                        }
                    }
                    skin
                }
            }
        }
        val skinLayout = remember(activeSkin, isLandscape) {
            activeSkin?.let { skin ->
                skinManager.getLayoutForSkin(skin, isLandscape = isLandscape, isTablet = false)
            }
        }

        val tiltConfiguration = viewModel.getTiltConfiguration().collectAsState(TiltConfiguration.Disabled)
        val tiltSimulatedStates = viewModel.getSimulatedTiltEvents().collectAsState(InputState())
        val tiltSimulatedControls = remember { derivedStateOf { tiltConfiguration.value.controlIds() } }

        val touchGamePads = currentControllerConfig?.getTouchControllerConfig()
        val leftGamePad = touchGamePads?.leftComposable
        val rightGamePad = touchGamePads?.rightComposable

        val hapticFeedbackMode =
            viewModel
                .getTouchHapticFeedbackMode()
                .collectAsState(HapticFeedbackMode.NONE)

        val padHapticFeedback =
            when (hapticFeedbackMode.value) {
                HapticFeedbackMode.NONE -> HapticFeedbackType.NONE
                HapticFeedbackMode.PRESS -> HapticFeedbackType.PRESS
                HapticFeedbackMode.PRESS_RELEASE -> HapticFeedbackType.PRESS_RELEASE
            }

        PadKit(
            modifier = Modifier.fillMaxSize(),
            onInputEvents = { viewModel.handleVirtualInputEvent(it) },
            hapticFeedbackType = padHapticFeedback,
            simulatedState = tiltSimulatedStates,
            simulatedControlIds = tiltSimulatedControls,
        ) {
            val localContext = LocalContext.current
            val lifecycle = LocalLifecycleOwner.current

            val fullScreenPosition = remember { mutableStateOf<Rect?>(null) }
            val viewportPosition = remember { mutableStateOf<Rect?>(null) }
            val customViewportPosition = remember { mutableStateOf<Rect?>(null) }

            AndroidView(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { fullScreenPosition.value = it.boundsInRoot() },
                factory = {
                    viewModel.createRetroView(localContext, lifecycle).apply {
                        alpha = 0f // Start invisible, sync with skin
                    }
                },
                update = { view ->
                    // Show game view as soon as ANY skin is loaded (even if it's the cached one from last orientation)
                    if (skinBitmapLoaded.value || initialSkinLoaded.value) {
                        view.alpha = 1f
                        initialSkinLoaded.value = true
                    }
                }
            )

            val fullPos = fullScreenPosition.value
            val viewPos = customViewportPosition.value ?: viewportPosition.value

            LaunchedEffect(fullPos, viewPos) {
                val gameView = viewModel.retroGameView.retroGameViewFlow()
                if (fullPos == null || viewPos == null) return@LaunchedEffect
                val viewport =
                    RectF(
                        (viewPos.left - fullPos.left) / fullPos.width,
                        (viewPos.top - fullPos.top) / fullPos.height,
                        (viewPos.right - fullPos.left) / fullPos.width,
                        (viewPos.bottom - fullPos.top) / fullPos.height,
                    )
                gameView.viewport = viewport
            }

            val isVisible =
                touchControllerSettings != null &&
                    currentControllerConfig != null &&
                    touchControlsVisibleState.value &&
                    systemId != null

            val selectedSkin = activeSkin
            if (selectedSkin != null && skinLayout != null && isVisible) {
                DeltaSkinView(
                    modifier = Modifier.fillMaxSize(),
                    skinPackage = selectedSkin,
                    layout = skinLayout,
                    onBitmapLoaded = { skinBitmapLoaded.value = it },
                    onButtonStateChanged = { keyCode, pressed ->
                        if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
                            viewModel.handleVirtualInputEvent(listOf(InputEvent.Button(KeyEvent.KEYCODE_BUTTON_MODE, pressed)))
                        } else {
                            viewModel.retroGameView.retroGameView?.sendKeyEvent(
                                if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP,
                                keyCode
                            )
                        }
                    },
                    onDpadStateChanged = { xAxis, yAxis ->
                        viewModel.retroGameView.retroGameView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_DPAD,
                            xAxis,
                            yAxis
                        )
                    },
                    onLeftAnalogStateChanged = { xAxis, yAxis ->
                        viewModel.retroGameView.retroGameView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                            xAxis,
                            yAxis
                        )
                    },
                    onRightAnalogStateChanged = { xAxis, yAxis ->
                        viewModel.retroGameView.retroGameView?.sendMotionEvent(
                            GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                            xAxis,
                            yAxis
                        )
                    },
                    onPointerEvent = { x, y, pressed ->
                        viewModel.retroGameView.sendPointerEvent(x, y, pressed)
                    },
                    onScreenGapChanged = { gap ->
                        viewModel.onScreenGapChanged(gap)
                    },
                    onViewportPositioned = { rect ->
                        customViewportPosition.value = rect
                    }
                )
            }

            ConstraintLayout(
                modifier = Modifier.fillMaxSize(),
                constraintSet =
                    GameScreenLayout.buildConstraintSet(
                        isLandscape,
                        currentControllerConfig?.allowTouchOverlay ?: true,
                    ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .layoutId(GameScreenLayout.CONSTRAINTS_GAME_VIEW)
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
                            .onGloballyPositioned { viewportPosition.value = it.boundsInRoot() },
                )

                if (isVisible) {
                    CompositionLocalProvider(LocalLemuroidPadTheme provides LemuroidPadTheme()) {
                        if (activeSkin != null && skinLayout != null) {
                            // Only show skin, no central menu button
                        } else if (systemId == null) {
                            // Only show default pads if we are NOT planning to load a skin
                            if (!isLandscape) {
                                PadContainer(
                                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_BOTTOM_CONTAINER),
                                )
                            } else if (!currentControllerConfig!!.allowTouchOverlay) {
                                PadContainer(
                                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_CONTAINER),
                                )
                                PadContainer(
                                    modifier = Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_CONTAINER),
                                )
                            }

                            leftGamePad?.invoke(
                                this,
                                Modifier.layoutId(GameScreenLayout.CONSTRAINTS_LEFT_PAD),
                                touchControllerSettings!!,
                            )
                            rightGamePad?.invoke(
                                this,
                                Modifier.layoutId(GameScreenLayout.CONSTRAINTS_RIGHT_PAD),
                                touchControllerSettings!!,
                            )

                            GameScreenRunningCentralMenu()
                        }
                    }
                }
            }
        }

        // Remove the isLoading overlay box to show the skin immediately
    }
}

@Composable
private fun PadContainer(modifier: Modifier = Modifier) {
    val theme = LocalLemuroidPadTheme.current
    GlassSurface(
        modifier = modifier,
        cornerRadius = theme.level0CornerRadius,
        fillColor = theme.level0Fill,
        shadowColor = theme.level0Shadow,
        shadowWidth = theme.level0ShadowWidth,
    )
}

@Composable
private fun GameScreenRunningCentralMenu() {
    // Menu is triggered immediately and icon is removed as requested
}

@Composable
private fun MenuEditTouchControls(
    viewModel: BaseGameScreenViewModel,
    controllerConfig: ControllerConfig,
    touchControllerSettings: TouchControllerSettingsManager.Settings,
) {
    val showEditControls = viewModel.isEditControlShown().collectAsState(false)
    if (!showEditControls.value) return

    Dialog(onDismissRequest = { viewModel.showEditControls(false) }) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MenuEditTouchControlRow(Icons.Default.OpenInFull, "Scale", 0f) {
                    Slider(
                        value = touchControllerSettings.scale,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(scale = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(Icons.Default.Height, "Horizontal Margin", 90f) {
                    Slider(
                        value = touchControllerSettings.marginX,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginX = it),
                            )
                        },
                    )
                }
                MenuEditTouchControlRow(Icons.Default.Height, "Vertical Margin", 0f) {
                    Slider(
                        value = touchControllerSettings.marginY,
                        onValueChange = {
                            viewModel.updateTouchControllerSettings(
                                touchControllerSettings.copy(marginY = it),
                            )
                        },
                    )
                }
                if (controllerConfig.allowTouchRotation) {
                    MenuEditTouchControlRow(Icons.AutoMirrored.Filled.RotateLeft, "Rotate", 0f) {
                        Slider(
                            value = touchControllerSettings.rotation,
                            onValueChange = {
                                viewModel.updateTouchControllerSettings(
                                    touchControllerSettings.copy(rotation = it),
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        onClick = { viewModel.resetTouchControls() },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_reset))
                    }
                    TextButton(
                        onClick = { viewModel.showEditControls(false) },
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Text(text = stringResource(R.string.touch_customize_button_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuEditTouchControlRow(
    icon: ImageVector,
    label: String,
    rotation: Float,
    slider: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            modifier = Modifier.rotate(rotation),
            imageVector = icon,
            contentDescription = label,
        )
        slider()
    }
}
