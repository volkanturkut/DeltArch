@file:Suppress("all")

package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesScreen
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesViewModel
import com.swordfish.lemuroid.app.mobile.feature.games.GamesScreen
import com.swordfish.lemuroid.app.mobile.feature.games.GamesViewModel
import com.swordfish.lemuroid.app.mobile.feature.home.HomeScreen
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.search.SearchScreen
import com.swordfish.lemuroid.app.mobile.feature.search.SearchViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.advanced.AdvancedSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsScreen
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.common.view.disableTouchEvents
import com.swordfish.lemuroid.ext.feature.review.ReviewManager
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Provides
import de.charlex.compose.material3.HtmlText
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterIsInstance
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity, com.swordfish.lemuroid.app.shared.game.GameLaunchDelegate {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var gameLoader: com.swordfish.lemuroid.lib.game.GameLoader

    @Inject
    lateinit var settingsManager: com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager

    @Inject
    lateinit var coreVariablesManager: com.swordfish.lemuroid.lib.core.CoreVariablesManager

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var savesManager: com.swordfish.lemuroid.lib.saves.SavesManager

    private val reviewManager = ReviewManager()

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())

        GlobalScope.safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        setContent {
            val navController = rememberNavController()
            MainScreen(navController)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val currentRoute =
                currentDestination?.route
                    ?.let { MainRoute.findByRoute(it) }
                    ?: MainRoute.HOME

            val infoDialogDisplayed =
                remember {
                    mutableStateOf(false)
                }

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }

            val onGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            val onGameClick = { game: Game ->
                gameInteractor.onGamePlay(game)
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            val onHelpPressed = {
                infoDialogDisplayed.value = true
            }

            val mainUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value

            val currentSystemTitle = remember { mutableStateOf<String?>(null) }
            val isSearchFocused = remember { mutableStateOf(false) }

            val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()

            val coroutineScope = rememberCoroutineScope()
            var activePreviewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            var activeRetroView by remember { mutableStateOf<com.swordfish.libretrodroid.GLRetroView?>(null) }

            val gameActions = remember {
                com.swordfish.lemuroid.app.mobile.shared.compose.ui.GameActions(
                    onPlay = { gameInteractor.onGamePlay(it) },
                    onLaunchGameNewWindow = { gameInteractor.onGamePlayNewWindow(it) },
                    onRestart = { gameInteractor.onGameRestart(it) },
                    onFavoriteToggle = { game, isFavorite -> gameInteractor.onFavoriteToggle(game, isFavorite) },
                    onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
                    shortcutSupported = gameInteractor.supportShortcuts(),
                    getLatestSaveStatePreview = { gameInteractor.getLatestSaveStatePreview(it, applicationContext) },
                    getGameSaveStates = { gameInteractor.getAllSaveStates(it, applicationContext) },
                    onRename = { game, newTitle -> gameInteractor.renameGame(game, newTitle) },
                    onChangeArtwork = { game, newCoverUrl -> gameInteractor.changeArtwork(game, newCoverUrl) },
                    onShare = { game -> 
                        val uriString = game.fileUri
                        val cleanTitle = game.title.replace(Regex("\\([^)]*\\)|\\[[^\\]]*\\]|\\.[a-zA-Z0-9]+$"), "").trim()
                        val extension = try {
                            val path = android.net.Uri.parse(uriString).path ?: uriString
                            val dotIndex = path.lastIndexOf('.')
                            if (dotIndex != -1) path.substring(dotIndex) else ""
                        } catch (e: Exception) {
                            ""
                        }
                        val sharedFileName = "${cleanTitle}${extension}".replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val cacheFile = java.io.File(applicationContext.cacheDir, sharedFileName)
                        
                        try {
                            applicationContext.contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { input ->
                                java.io.FileOutputStream(cacheFile).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        } catch (e: Exception) {
                            try {
                                val sourceFile = java.io.File(android.net.Uri.parse(uriString).path ?: uriString)
                                sourceFile.copyTo(cacheFile, overwrite = true)
                            } catch (ex: Exception) {
                                // Ignore
                            }
                        }

                        val uriToShare = androidx.core.content.FileProvider.getUriForFile(
                            applicationContext,
                            "${applicationContext.packageName}.fileprovider",
                            cacheFile
                        )
                        val sendIntent: android.content.Intent = android.content.Intent().apply {
                            action = android.content.Intent.ACTION_SEND
                            putExtra(android.content.Intent.EXTRA_STREAM, uriToShare)
                            type = "application/octet-stream"
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
                        applicationContext.startActivity(shareIntent.apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    },
                    onGameSettings = { /* TODO */ },
                    onViewSaveState = { /* TODO */ },
                    onLoadSaveState = { game, item -> gameInteractor.loadSaveState(game, item) },
                    onRenameSaveState = { game, item, newName -> gameInteractor.renameSaveState(game, item, newName) },
                    onDeleteSaveState = { game, item -> gameInteractor.deleteSaveState(game, item) },
                    onImportSaveState = { game, item, bytes -> gameInteractor.importSaveState(game, item, bytes) },
                    onExportSaveState = { game, item -> gameInteractor.exportSaveState(game, item) },
                    onImportSave = { game, uri ->
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                contentResolver.openInputStream(uri)?.use { stream ->
                                    val bytes = stream.readBytes()
                                    savesManager.setSaveRAM(game, bytes)
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(applicationContext, "Save imported successfully", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to import save")
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(applicationContext, "Failed to import save", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onExportSave = { game, uri ->
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId)
                                val coreConfig = coresSelection.getCoreConfigForSystem(system)
                                val bytes = savesManager.getSaveRAM(game, coreConfig)
                                if (bytes != null) {
                                    contentResolver.openOutputStream(uri)?.use { stream ->
                                        stream.write(bytes)
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            android.widget.Toast.makeText(applicationContext, "Save exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(applicationContext, "No save file found to export", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                                timber.log.Timber.e(e, "Failed to export save")
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(applicationContext, "Failed to export save", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onDelete = { game -> gameInteractor.deleteGame(game) },
                    startPreview = { game, onPlay, onReady ->
                        activePreviewJob?.cancel()
                        activeRetroView?.let { view ->
                            this@MainActivity.lifecycle.removeObserver(view)
                            val observer = view as? androidx.lifecycle.DefaultLifecycleObserver
                            observer?.onPause(this@MainActivity)
                            observer?.onStop(this@MainActivity)
                            observer?.onDestroy(this@MainActivity)
                            activeRetroView = null
                        }
                        activePreviewJob = coroutineScope.launch {
                            try {
                                val system = com.swordfish.lemuroid.lib.library.GameSystem.findById(game.systemId) ?: return@launch
                                val coreConfig = coresSelection.getCoreConfigForSystem(system) ?: return@launch
                                
                                val autoSaveEnabled = settingsManager.autoSave()
                                val filter = settingsManager.screenFilter()
                                val hdMode = settingsManager.hdMode()
                                val hdModeQuality = settingsManager.hdModeQuality()
                                val lowLatencyAudio = settingsManager.lowLatencyAudio()
                                val directLoad = settingsManager.allowDirectGameLoad()
                                
                                gameLoader.load(
                                    applicationContext,
                                    game,
                                    autoSaveEnabled,
                                    coreConfig,
                                    directLoad
                                )
                                .flowOn(kotlinx.coroutines.Dispatchers.IO)
                                .collect { loadingState ->
                                    if (loadingState is com.swordfish.lemuroid.lib.game.GameLoader.LoadingState.Ready) {
                                        val retroViewData = com.swordfish.libretrodroid.GLRetroViewData(applicationContext).apply {
                                            coreFilePath = loadingState.gameData.coreLibrary
                                            when (val gameFiles = loadingState.gameData.gameFiles) {
                                                is com.swordfish.lemuroid.lib.storage.RomFiles.Standard -> {
                                                    gameFilePath = gameFiles.files.first().absolutePath
                                                }
                                                is com.swordfish.lemuroid.lib.storage.RomFiles.Virtual -> {
                                                    gameVirtualFiles = gameFiles.files.map { com.swordfish.libretrodroid.VirtualFile(it.filePath, it.fd) }
                                                }
                                            }
                                            systemDirectory = loadingState.gameData.systemDirectory.absolutePath
                                            savesDirectory = loadingState.gameData.savesDirectory.absolutePath
                                            variables = loadingState.gameData.coreVariables.map { com.swordfish.libretrodroid.Variable(it.key, it.value) }.toTypedArray()
                                            saveRAMState = loadingState.gameData.saveRAMData
                                            shader = com.swordfish.lemuroid.app.shared.game.ShaderChooser.getShaderForSystem(
                                                applicationContext,
                                                hdMode,
                                                hdModeQuality,
                                                filter,
                                                system
                                            )
                                            preferLowLatencyAudio = lowLatencyAudio
                                            rumbleEventsEnabled = false
                                            skipDuplicateFrames = coreConfig.skipDuplicateFrames
                                            enableMicrophone = false
                                            immersiveMode = null
                                        }
                                        
                                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            val gestureDetector = androidx.core.view.GestureDetectorCompat(
                                                applicationContext,
                                                object : android.view.GestureDetector.SimpleOnGestureListener() {
                                                    override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                                                        onPlay()
                                                        return true
                                                    }
                                                }
                                            )
                                            val view = com.swordfish.libretrodroid.GLRetroView(applicationContext, retroViewData).apply {
                                                isFocusable = false
                                                isFocusableInTouchMode = false
                                                audioEnabled = false
                                                setOnTouchListener { _, event ->
                                                    gestureDetector.onTouchEvent(event)
                                                    true
                                                }
                                            }
                                            this@MainActivity.lifecycle.addObserver(view)
                                            activeRetroView = view
                                            
                                            // Restore custom preview save state if set
                                            coroutineScope.launch {
                                                try {
                                                    val sharedPref = applicationContext.getSharedPreferences("locked_save_states", android.content.Context.MODE_PRIVATE)
                                                    val previewSlotId = sharedPref.getString("preview_${game.id}", null)
                                                    if (previewSlotId != null) {
                                                        // Wait for first frame to be rendered
                                                        view.getGLRetroEvents()
                                                            .filterIsInstance<com.swordfish.libretrodroid.GLRetroView.GLRetroEvents.FrameRendered>()
                                                            .first()
                                                        
                                                        val statesManager = gameInteractor.getStatesManager()
                                                        val slotState = if (previewSlotId == "autosave") {
                                                            statesManager.getAutoSave(game, coreConfig.coreID)
                                                        } else {
                                                            val index = previewSlotId.removePrefix("slot_").toIntOrNull()
                                                            if (index != null) {
                                                                statesManager.getSlotSave(game, coreConfig.coreID, index)
                                                            } else {
                                                                null
                                                            }
                                                        }
                                                        
                                                        if (slotState != null) {
                                                            view.unserializeState(slotState.state)
                                                        }
                                                    }
                                                } catch (e: Exception) {
                                                    timber.log.Timber.e(e, "Failed to restore preview save state")
                                                }
                                            }

                                            onReady(view)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("LemuroidPreview", "Failed to load preview for game: ${game.title}", e)
                            }
                        }
                    },
                    stopPreview = {
                        activePreviewJob?.cancel()
                        activePreviewJob = null
                        activeRetroView?.let { view ->
                            this@MainActivity.lifecycle.removeObserver(view)
                            val observer = view as? androidx.lifecycle.DefaultLifecycleObserver
                            observer?.onPause(this@MainActivity)
                            observer?.onStop(this@MainActivity)
                            observer?.onDestroy(this@MainActivity)
                            activeRetroView = null
                        }
                    }
                )
            }

            androidx.compose.runtime.CompositionLocalProvider(
                com.swordfish.lemuroid.app.mobile.shared.compose.ui.LocalGameActions provides gameActions
            ) {
                Scaffold(
                    modifier = Modifier
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    topBar = {
                        MainTopBar(
                            currentRoute = currentRoute,
                            navController = navController,
                            onHelpPressed = onHelpPressed,
                            mainUIState = mainUIState,
                            onUpdateQueryString = { mainViewModel.changeQueryString(it) },
                            dynamicTitle = currentSystemTitle.value,
                            scrollBehavior = scrollBehavior,
                            isSearchFocused = isSearchFocused.value
                        )
                    },
                ) { padding ->
                    androidx.navigation.compose.NavHost(
                        modifier = Modifier.fillMaxSize().consumeWindowInsets(padding),
                        navController = navController,
                        startDestination = MainRoute.HOME.route,
                    ) {
                        composable(MainRoute.HOME) {
                            HomeScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            HomeViewModel.Factory(
                                                applicationContext,
                                                retrogradeDb,
                                                coresSelection,
                                            ),
                                    ),
                                metaSystemsViewModel = 
                                    viewModel(
                                        factory =
                                            MetaSystemsViewModel.Factory(
                                                retrogradeDb,
                                                applicationContext,
                                            ),
                                    ),
                                retrogradeDb = retrogradeDb,
                                searchQuery = mainUIState.searchQuery,
                                onUpdateQueryString = { mainViewModel.changeQueryString(it) },
                                onGameClick = { gameInteractor.onGamePlay(it) },
                                onGameLongClick = { gameInteractor.onGamePlay(it) },
                                onOpenCoreSelection = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
                                onSystemTitleChanged = { currentSystemTitle.value = it },
                                onSearchFocused = { isSearchFocused.value = true },
                                onSearchUnfocused = { isSearchFocused.value = false },
                            )
                        }
                        composable(MainRoute.FAVORITES) {
                            FavoritesScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory = FavoritesViewModel.Factory(retrogradeDb),
                                    ),
                                onGameClick = { gameInteractor.onGamePlay(it) },
                                onGameLongClick = { gameInteractor.onGamePlay(it) },
                            )
                        }
                        composable(MainRoute.SEARCH) {
                            SearchScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory = SearchViewModel.Factory(retrogradeDb),
                                    ),
                                searchQuery = mainUIState.searchQuery,
                                onGameClick = { gameInteractor.onGamePlay(it) },
                                onGameLongClick = { gameInteractor.onGamePlay(it) },
                                onGameFavoriteToggle = { game, isFavorite -> gameInteractor.onFavoriteToggle(game, isFavorite) },
                                onResetSearchQuery = { mainViewModel.changeQueryString("") },
                                onUpdateQueryString = { mainViewModel.changeQueryString(it) }
                            )
                        }
                        composable(MainRoute.SYSTEMS) {
                            MetaSystemsScreen(
                                modifier = Modifier.padding(padding),
                                navController = navController,
                                viewModel =
                                    viewModel(
                                        factory =
                                            MetaSystemsViewModel.Factory(
                                                retrogradeDb,
                                                applicationContext,
                                            ),
                                    ),
                            )
                        }
                        composable(MainRoute.SYSTEM_GAMES) { entry ->
                            val metaSystemId = entry.arguments?.getString("metaSystemId")
                            GamesScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            GamesViewModel.Factory(
                                                retrogradeDb,
                                                MetaSystemID.valueOf(metaSystemId!!),
                                            ),
                                    ),
                                onGameClick = { gameInteractor.onGamePlay(it) },
                                onGameLongClick = { gameInteractor.onGamePlay(it) },
                                onGameFavoriteToggle = { game, isFavorite -> gameInteractor.onFavoriteToggle(game, isFavorite) },
                            )
                        }
                        composable(MainRoute.SETTINGS) {
                            SettingsScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            SettingsViewModel.Factory(
                                                applicationContext,
                                                settingsInteractor,
                                                saveSyncManager,
                                                SharedPreferencesHelper.allowDiskOperations {
                                                    FlowSharedPreferences(
                                                        SharedPreferencesHelper.getLegacySharedPreferences(
                                                            applicationContext,
                                                        ),
                                                    )
                                                },
                                            ),
                                    ),
                                navController = navController,
                            )
                        }
                        composable(MainRoute.SETTINGS_ADVANCED) {
                            AdvancedSettingsScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            AdvancedSettingsViewModel.Factory(
                                                applicationContext,
                                                settingsInteractor,
                                            ),
                                    ),
                                navController = navController,
                            )
                        }
                        composable(MainRoute.SETTINGS_BIOS) {
                            BiosScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory = BiosSettingsViewModel.Factory(biosManager),
                                    ),
                            )
                        }
                        composable(MainRoute.SETTINGS_CORES_SELECTION) {
                            CoresSelectionScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            CoresSelectionViewModel.Factory(
                                                applicationContext,
                                                coresSelection,
                                            ),
                                    ),
                            )
                        }
                        composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                            InputDevicesSettingsScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            InputDevicesSettingsViewModel.Factory(
                                                applicationContext,
                                                inputDeviceManager,
                                            ),
                                    ),
                            )
                        }
                        composable(MainRoute.SETTINGS_SAVE_SYNC) {
                            SaveSyncSettingsScreen(
                                modifier = Modifier.padding(padding),
                                viewModel =
                                    viewModel(
                                        factory =
                                            SaveSyncSettingsViewModel.Factory(
                                                application,
                                                saveSyncManager,
                                            ),
                                    ),
                            )
                        }
                    }
                }

                if (infoDialogDisplayed.value) {
                    val message =
                        remember {
                            val systemFolders =
                                SystemID.entries
                                    .joinToString(", ") { "<i>${it.dbname}</i>" }

                            getString(R.string.lemuroid_help_content)
                                .replace("\$SYSTEMS", systemFolders)
                        }

                    AlertDialog(
                        text = { HtmlText(text = message) },
                        onDismissRequest = { infoDialogDisplayed.value = false },
                        confirmButton = { },
                    )
                }
            }
        }
    }

    private val playGameLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            GlobalScope.safeLaunch {
                gameLaunchTaskHandler.handleGameFinish(
                    true,
                    this@MainActivity,
                    result.resultCode,
                    result.data,
                )
            }
        }

    override fun launchGameIntent(intent: Intent) {
        playGameLauncher.launch(intent)
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false



    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                statesManager: com.swordfish.lemuroid.lib.saves.StatesManager,
                statesPreviewManager: com.swordfish.lemuroid.lib.saves.StatesPreviewManager,
                coresSelection: com.swordfish.lemuroid.lib.core.CoresSelection,
            ) = GameInteractor(activity, retrogradeDb, false, shortcutsGenerator, gameLauncher, statesManager, statesPreviewManager, coresSelection)
        }
    }
}
