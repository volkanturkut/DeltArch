package com.swordfish.lemuroid.app.shared.game

import kotlin.time.Duration.Companion.milliseconds

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.swordfish.lemuroid.app.mobile.feature.game.GameActivity
import com.swordfish.lemuroid.app.mobile.feature.game.GameService
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.ImmersiveActivity
import com.swordfish.lemuroid.app.shared.coreoptions.CoreOption
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.game.viewmodel.GameViewModelSideEffects
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.rumble.RumbleManager
import com.swordfish.lemuroid.app.shared.settings.ControllerConfigsManager
import com.swordfish.lemuroid.app.tv.game.TVGameActivity
import com.swordfish.lemuroid.common.animationDuration
import com.swordfish.lemuroid.common.coroutines.launchOnState
import com.swordfish.lemuroid.common.displayToast
import com.swordfish.lemuroid.common.dump
import com.swordfish.lemuroid.common.kotlin.overrideTransition
import com.swordfish.lemuroid.common.kotlin.serializable
import com.swordfish.lemuroid.lib.core.CoreVariablesManager
import com.swordfish.lemuroid.lib.game.GameLoader
import com.swordfish.lemuroid.lib.library.ExposedSetting
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.SavesManager
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import dagger.Lazy
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
abstract class BaseGameActivity : ImmersiveActivity() {
    protected lateinit var game: Game
    private lateinit var system: GameSystem
    protected lateinit var systemCoreConfig: SystemCoreConfig

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var savesManager: SavesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    @Inject
    lateinit var coreVariablesManager: CoreVariablesManager

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var gameLoader: GameLoader

    @Inject
    lateinit var controllerConfigsManager: ControllerConfigsManager

    @Inject
    lateinit var rumbleManager: RumbleManager

    @Inject
    lateinit var sharedPreferences: Lazy<SharedPreferences>

    private lateinit var baseGameScreenViewModel: BaseGameScreenViewModel

    private val startGameTime = System.currentTimeMillis()
    private var finishTriggered = false
    private var quitReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpExceptionsHandler()
        val processIndex = getProcessIndex(applicationContext)
        val serviceClass = when (processIndex) {
            1 -> com.swordfish.lemuroid.app.mobile.feature.game.GameService::class.java
            2 -> com.swordfish.lemuroid.app.mobile.feature.game.GameService2::class.java
            3 -> com.swordfish.lemuroid.app.mobile.feature.game.GameService3::class.java
            else -> com.swordfish.lemuroid.app.mobile.feature.game.GameService::class.java
        }
        val serviceIntent = Intent(applicationContext, serviceClass).apply {
            putExtra("EXTRA_GAME_ACTIVITY_INTENT", intent)
            putExtra("EXTRA_NEW_WINDOW", intent.getBooleanExtra("EXTRA_NEW_WINDOW", false))
        }
        applicationContext.startService(serviceIntent)
        game = intent.serializable<Game>(EXTRA_GAME)!!
        systemCoreConfig = intent.serializable<SystemCoreConfig>(EXTRA_SYSTEM_CORE_CONFIG)!!
        system = GameSystem.findById(game.systemId)

        SystemProcessLock.acquire(applicationContext, game.systemId, game.title)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val targetSystemId = intent?.getStringExtra("systemId")
                if (targetSystemId == game.systemId) {
                    finish()
                }
            }
        }
        quitReceiver = receiver
        val filter = IntentFilter("com.swordfish.lemuroid.action.QUIT_GAME")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        val viewModel by viewModels<BaseGameScreenViewModel> {
            BaseGameScreenViewModel.Factory(
                applicationContext,
                game,
                settingsManager,
                inputDeviceManager,
                controllerConfigsManager,
                system,
                systemCoreConfig,
                sharedPreferences.get(),
                savesManager,
                statesManager,
                statesPreviewManager,
                coreVariablesManager,
                rumbleManager,
            )
        }

        baseGameScreenViewModel = viewModel

        lifecycle.addObserver(baseGameScreenViewModel)

        setContent {
            AppTheme {
                BaseGameScreen(viewModel = baseGameScreenViewModel) {
                    GameScreen(viewModel)
                }
            }
        }

        lifecycleScope.launch {
            baseGameScreenViewModel.loadGame(
                applicationContext,
                game,
                systemCoreConfig,
                gameLoader,
                intent.getBooleanExtra(EXTRA_LOAD_SAVE, false),
            )
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Do nothing to ignore Android system back gestures
                }
            },
        )

        setUpGestureExclusion()
        initialiseFlows()
    }

    @Composable
    abstract fun GameScreen(viewModel: BaseGameScreenViewModel)

    private fun initialiseFlows() {
        launchOnState(Lifecycle.State.CREATED) {
            initializeViewModelsEffectsFlow()
        }
    }

    private fun setUpExceptionsHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            performUnexpectedErrorFinish(exception)
        }
    }

    private fun transformExposedSetting(
        exposedSetting: ExposedSetting,
        coreOptions: List<CoreOption>,
    ): LemuroidCoreOption? {
        return coreOptions
            .firstOrNull { it.variable.key == exposedSetting.key }
            ?.let { LemuroidCoreOption(exposedSetting, it) }
    }

    private fun displayOptionsDialog(
        currentTiltConfiguration: TiltConfiguration,
        tiltConfigurations: List<TiltConfiguration>,
    ) {
        if (baseGameScreenViewModel.loadingState.value) {
            return
        }

        val coreOptions = getCoreOptions()

        val options =
            systemCoreConfig.exposedSettings
                .mapNotNull { transformExposedSetting(it, coreOptions) }

        val advancedOptions =
            systemCoreConfig.exposedAdvancedSettings
                .mapNotNull { transformExposedSetting(it, coreOptions) }

        val intent =
            Intent(this, getDialogClass()).apply {
                this.putExtra(GameMenuContract.EXTRA_CORE_OPTIONS, options.toTypedArray())
                this.putExtra(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS, advancedOptions.toTypedArray())
                this.putExtra(
                    GameMenuContract.EXTRA_CURRENT_DISK,
                    baseGameScreenViewModel.retroGameView.retroGameView?.getCurrentDisk() ?: 0,
                )
                this.putExtra(
                    GameMenuContract.EXTRA_DISKS,
                    baseGameScreenViewModel.retroGameView.retroGameView?.getAvailableDisks() ?: 0,
                )
                this.putExtra(GameMenuContract.EXTRA_GAME, game)
                this.putExtra(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                this.putExtra(
                    GameMenuContract.EXTRA_AUDIO_ENABLED,
                    baseGameScreenViewModel.retroGameView.retroGameView?.audioEnabled,
                )
                this.putExtra(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, system.fastForwardSupport)
                this.putExtra(
                    GameMenuContract.EXTRA_FAST_FORWARD,
                    (baseGameScreenViewModel.retroGameView.retroGameView?.frameSpeed ?: 1) > 1,
                )
                this.putExtra(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG, currentTiltConfiguration)
                val hasPhysicalGamePad = inputDeviceManager.getAllGamePads().isNotEmpty()
                if (!hasPhysicalGamePad) {
                    this.putExtra(GameMenuContract.EXTRA_TILT_ALL_CONFIGS, tiltConfigurations.toTypedArray())
                }
            }
        startActivityForResult(intent, DIALOG_REQUEST)
        overrideTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    protected abstract fun getDialogClass(): Class<out Activity>

    private fun getCoreOptions(): List<CoreOption> {
        return baseGameScreenViewModel.retroGameView.retroGameView?.getVariables()
            ?.mapNotNull {
                val coreOptionResult =
                    runCatching {
                        CoreOption.fromLibretroDroidVariable(it)
                    }
                coreOptionResult.getOrNull()
            } ?: listOf()
    }

    private suspend fun initializeViewModelsEffectsFlow() {
        baseGameScreenViewModel.getSideEffects()
            .collect {
                when (it) {
                    is GameViewModelSideEffects.UiEffect.ShowMenu ->
                        displayOptionsDialog(
                            it.currentTiltConfiguration,
                            it.tiltConfigurations,
                        )
                    is GameViewModelSideEffects.UiEffect.ShowToast -> displayToast(it.message)
                    is GameViewModelSideEffects.UiEffect.SuccessfulFinish -> performSuccessfulActivityFinish()
                    is GameViewModelSideEffects.UiEffect.FailureFinish -> performErrorFinish(it.message)
                    is GameViewModelSideEffects.UiEffect.SaveQuickSave -> performSaveQuickSave()
                    is GameViewModelSideEffects.UiEffect.LoadQuickSave -> performLoadQuickSave()
                    is GameViewModelSideEffects.UiEffect.ToggleFastForward -> performToggleFastForward()
                }
            }
    }

    private fun performSaveQuickSave() {
        baseGameScreenViewModel.saveQuickSave()
    }

    private fun performLoadQuickSave() {
        baseGameScreenViewModel.loadQuickSave()
    }

    private fun performToggleFastForward() {
        baseGameScreenViewModel.toggleFastForward()
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = baseGameScreenViewModel.sendMotionEvent(event)
        if (handled) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true // Consume back key down during gameplay
        }
        val handled = baseGameScreenViewModel.sendKeyEvent(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent,
    ): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true // Consume back key up during gameplay
        }
        val handled = baseGameScreenViewModel.sendKeyEvent(keyCode, event)
        if (handled) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun performSuccessfulActivityFinish() {
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_SESSION_DURATION, System.currentTimeMillis() - startGameTime)
                putExtra(PLAY_GAME_RESULT_GAME, intent.serializable<Game>(EXTRA_GAME))
                putExtra(PLAY_GAME_RESULT_GAME_ID, game.id)
                putExtra(PLAY_GAME_RESULT_LEANBACK, intent.getBooleanExtra(EXTRA_LEANBACK, false))
            }

        setResult(RESULT_OK, resultIntent)
        finishAndExitProcess()
    }

    private fun performUnexpectedErrorFinish(exception: Throwable) {
        Timber.e(exception, "Handling java exception in BaseGameActivity")
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_ERROR, exception.message)
            }

        setResult(RESULT_UNEXPECTED_ERROR, resultIntent)
        finishAndExitProcess()
    }

    private fun performErrorFinish(message: String) {
        val resultIntent =
            Intent().apply {
                putExtra(PLAY_GAME_RESULT_ERROR, message)
            }

        setResult(RESULT_ERROR, resultIntent)
        finishAndExitProcess()
    }

    private fun finishAndExitProcess() {
        onFinishTriggered()
        GlobalScope.launch {
            delay(animationDuration().milliseconds)
            GameService.requestTermination()
        }
        finish()
        overrideTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    open fun onFinishTriggered() {
        finishTriggered = true
    }

    override fun onPause() {
        if (!finishTriggered && !isFinishing && !isChangingConfigurations) {
            baseGameScreenViewModel.captureAutoSaveScreenshot()
        }
        super.onPause()
    }

    override fun onStop() {
        if (!finishTriggered && !isFinishing && !isChangingConfigurations) {
            baseGameScreenViewModel.requestBackgroundSave()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            val processIndex = getProcessIndex(applicationContext)
            val serviceClass = when (processIndex) {
                1 -> com.swordfish.lemuroid.app.mobile.feature.game.GameService::class.java
                2 -> com.swordfish.lemuroid.app.mobile.feature.game.GameService2::class.java
                3 -> com.swordfish.lemuroid.app.mobile.feature.game.GameService3::class.java
                else -> com.swordfish.lemuroid.app.mobile.feature.game.GameService::class.java
            }
            try {
                // Call requestTermination equivalent or directly call tasks channel?
                // Wait! Since GameService has companion method requestTermination,
                // and it is loaded in the current process's JVM, GameService.requestTermination()
                // will target the companion object in THIS process's JVM.
                // So calling GameService.requestTermination() works perfectly regardless of the subclass!
                GameService.requestTermination()
            } catch (e: Exception) {
                // Ignore
            }
            SystemProcessLock.release(game.systemId)
        }
        quitReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // Ignore
            }
        }
        super.onDestroy()
    }

    private fun getProcessIndex(context: Context): Int {
        val processName = retrieveProcessName(context) ?: return 1
        return when {
            processName.endsWith(":game2") -> 2
            processName.endsWith(":game3") -> 3
            else -> 1
        }
    }

    private fun retrieveProcessName(context: Context): String? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            return dagger.android.support.DaggerApplication.getProcessName()
        }
        val currentPID = android.os.Process.myPid()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.runningAppProcesses
            ?.firstOrNull { it.pid == currentPID }
            ?.processName
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DIALOG_REQUEST) {
            Timber.i("Game menu dialog response: ${data?.extras.dump()}")
            if (data?.getBooleanExtra(GameMenuContract.RESULT_RESET, false) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.reset()
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_SAVE) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.saveSlot(data.getIntExtra(GameMenuContract.RESULT_SAVE, 0))
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_LOAD) == true) {
                GlobalScope.launch {
                    baseGameScreenViewModel.loadSlot(data.getIntExtra(GameMenuContract.RESULT_LOAD, 0))
                }
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_QUIT, false) == true) {
                baseGameScreenViewModel.requestFinish()
            }
            if (data?.hasExtra(GameMenuContract.RESULT_CHANGE_DISK) == true) {
                val index = data.getIntExtra(GameMenuContract.RESULT_CHANGE_DISK, 0)
                baseGameScreenViewModel.retroGameView.retroGameView?.changeDisk(index)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_ENABLE_AUDIO) == true) {
                baseGameScreenViewModel.retroGameView.retroGameView?.apply {
                    this.audioEnabled =
                        data.getBooleanExtra(
                            GameMenuContract.RESULT_ENABLE_AUDIO,
                            true,
                        )
                }
            }
            if (data?.hasExtra(GameMenuContract.RESULT_ENABLE_FAST_FORWARD) == true) {
                baseGameScreenViewModel.retroGameView.retroGameView?.apply {
                    val fastForwardEnabled =
                        data.getBooleanExtra(
                            GameMenuContract.RESULT_ENABLE_FAST_FORWARD,
                            false,
                        )
                    this.frameSpeed = if (fastForwardEnabled) 2 else 1
                }
            }
            if (data?.getBooleanExtra(GameMenuContract.RESULT_EDIT_TOUCH_CONTROLS, false) == true) {
                baseGameScreenViewModel.showEditControls(true)
            }
            if (data?.hasExtra(GameMenuContract.RESULT_CHANGE_TILT_CONFIG) == true) {
                val tiltConfig = data.serializable<TiltConfiguration>(GameMenuContract.RESULT_CHANGE_TILT_CONFIG)
                baseGameScreenViewModel.changeTiltConfiguration(tiltConfig!!)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val decorView = window.decorView
            val displayMetrics = resources.displayMetrics
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            if (width > 0 && height > 0) {
                val density = displayMetrics.density
                val exclusionHeight = (200 * density).toInt()
                val touchY = ev.y.toInt()
                val top = (touchY - exclusionHeight / 2).coerceIn(0, height - exclusionHeight)
                val bottom = top + exclusionHeight
                
                val rectWidth = (60 * density).toInt() // Wider zone (60dp)
                
                val leftRect = Rect(-100, top, rectWidth, bottom)
                val rightRect = Rect(width - rectWidth, top, width + 100, bottom)
                
                decorView.systemGestureExclusionRects = listOf(leftRect, rightRect)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setUpGestureExclusion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val decorView = window.decorView
            decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val displayMetrics = resources.displayMetrics
                    val width = displayMetrics.widthPixels
                    val height = displayMetrics.heightPixels
                    if (width > 0 && height > 0) {
                        val density = displayMetrics.density
                        val maxExclusionHeight = (200 * density).toInt()
                        val exclusionHeight = minOf(height, maxExclusionHeight)
                        val top = (height - exclusionHeight) / 2
                        val bottom = top + exclusionHeight
                        
                        val rectWidth = (60 * density).toInt() // Wider zone (60dp)
                        
                        val leftRect = Rect(-100, top, rectWidth, bottom)
                        val rightRect = Rect(width - rectWidth, top, width + 100, bottom)
                        
                        decorView.systemGestureExclusionRects = listOf(leftRect, rightRect)
                    }
                }
            })
        }
    }

    companion object {
        const val DIALOG_REQUEST = 100

        private const val EXTRA_GAME = "GAME"
        private const val EXTRA_LOAD_SAVE = "LOAD_SAVE"
        private const val EXTRA_LEANBACK = "LEANBACK"
        private const val EXTRA_SYSTEM_CORE_CONFIG = "EXTRA_SYSTEM_CORE_CONFIG"

        const val REQUEST_PLAY_GAME = 1001
        const val PLAY_GAME_RESULT_SESSION_DURATION = "PLAY_GAME_RESULT_SESSION_DURATION"
        const val PLAY_GAME_RESULT_GAME = "PLAY_GAME_RESULT_GAME"
        const val PLAY_GAME_RESULT_GAME_ID = "PLAY_GAME_RESULT_GAME_ID"
        const val PLAY_GAME_RESULT_LEANBACK = "PLAY_GAME_RESULT_LEANBACK"
        const val PLAY_GAME_RESULT_ERROR = "PLAY_GAME_RESULT_ERROR"

        const val RESULT_ERROR = Activity.RESULT_FIRST_USER + 2
        const val RESULT_UNEXPECTED_ERROR = Activity.RESULT_FIRST_USER + 3

        fun launchGame(
            activity: Activity,
            systemCoreConfig: SystemCoreConfig,
            game: Game,
            loadSave: Boolean,
            useLeanback: Boolean,
            newWindow: Boolean = false,
        ) {
            val index = getFreeProcessIndex(activity.applicationContext)
            val gameActivity =
                if (useLeanback) {
                    when (index) {
                        1 -> TVGameActivity::class.java
                        2 -> com.swordfish.lemuroid.app.tv.game.TVGameActivity2::class.java
                        3 -> com.swordfish.lemuroid.app.tv.game.TVGameActivity3::class.java
                        else -> TVGameActivity::class.java
                    }
                } else {
                    when (index) {
                        1 -> GameActivity::class.java
                        2 -> com.swordfish.lemuroid.app.mobile.feature.game.GameActivity2::class.java
                        3 -> com.swordfish.lemuroid.app.mobile.feature.game.GameActivity3::class.java
                        else -> GameActivity::class.java
                    }
                }
            val intent = Intent(activity, gameActivity).apply {
                if (newWindow) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                }
                putExtra(EXTRA_GAME, game)
                putExtra(EXTRA_LOAD_SAVE, loadSave)
                putExtra(EXTRA_LEANBACK, useLeanback)
                putExtra(EXTRA_SYSTEM_CORE_CONFIG, systemCoreConfig)
                putExtra("EXTRA_NEW_WINDOW", newWindow)
            }
            if (newWindow) {
                activity.startActivity(intent)
            } else {
                activity.startActivityForResult(intent, REQUEST_PLAY_GAME)
            }
            activity.overrideTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        private fun getFreeProcessIndex(context: Context): Int {
            if (!GameProcessLock.isProcessLocked(context, 1)) return 1
            if (!GameProcessLock.isProcessLocked(context, 2)) return 2
            if (!GameProcessLock.isProcessLocked(context, 3)) return 3
            return 1 // Fallback to 1
        }
    }
}
