package com.swordfish.lemuroid.app.mobile.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.imePadding
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.lib.library.db.entity.Game
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    retrogradeDb: com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase,
    metaSystemsViewModel: com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsViewModel,
    searchQuery: String = "",
    onUpdateQueryString: (String) -> Unit = {},
    onSearchFocused: () -> Unit = {},
    onSearchUnfocused: () -> Unit = {},
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onOpenCoreSelection: () -> Unit,
    onSystemTitleChanged: (String?) -> Unit = {},
    onTopBarAction: (com.swordfish.lemuroid.app.mobile.feature.main.MainRoute) -> Unit = {}
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                viewModel.updatePermissions(applicationContext)
            }
            else -> { }
        }
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ -> }

    val state by viewModel.getViewStates().collectAsState(HomeViewModel.UIState())

    LaunchedEffect(state) {
        if (state.showNoNotificationPermissionCard && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (state.showNoMicrophonePermissionCard) {
            permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var isSearchInputFocused by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        val systems by metaSystemsViewModel.availableMetaSystems.collectAsState(initial = null)
        val favoritesCount by retrogradeDb.gameDao().selectFavoritesCountFlow().collectAsState(initial = null)
        val recentCount by retrogradeDb.gameDao().selectRecentsCountFlow().collectAsState(initial = null)

        if (systems == null || favoritesCount == null || recentCount == null) {
            // Show loading or just empty box while DB queries first emit
            return
        }

        val homeCategories = remember(systems, recentCount) {
            val list = mutableListOf<HomeCategory>()
            if (systems!!.isNotEmpty()) {
                list.add(HomeCategory.Favorites)
                if (recentCount!! > 0) {
                    list.add(HomeCategory.Recent)
                }
                list.addAll(systems!!.map { HomeCategory.System(it) })
            }
            list
        }

        // Shared PagerState
        val LOOP_MULTIPLIER = 1000
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = {
                val cats = homeCategories
                val targetIdx = when {
                    cats.any { it is HomeCategory.Recent } ->
                        cats.indexOfFirst { it is HomeCategory.Recent }
                    else ->
                        cats.indexOfFirst { it is HomeCategory.System }.coerceAtLeast(0)
                }
                (LOOP_MULTIPLIER / 2) * cats.size + targetIdx
            }(),
            pageCount = { if (homeCategories.isNotEmpty()) homeCategories.size * LOOP_MULTIPLIER else 1 }
        )
        val coroutineScope = rememberCoroutineScope()

        // 1. Always show the library categories (unless completely empty)
        Column(modifier = Modifier.fillMaxSize()) {
            if (homeCategories.isEmpty()) {
                LaunchedEffect(Unit) {
                    onSystemTitleChanged("Games")
                }
                Box(modifier = Modifier.weight(1f)) {
                    com.swordfish.lemuroid.app.mobile.shared.compose.ui.DeltArchEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        onLearnMoreClick = { onTopBarAction(com.swordfish.lemuroid.app.mobile.feature.main.MainRoute.SETTINGS) }
                    )
                }
            } else {
                val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                
                LaunchedEffect(pagerState.currentPage, homeCategories) {
                    focusManager.clearFocus()
                }

                LaunchedEffect(pagerState.currentPage, homeCategories) {
                    if (homeCategories.isNotEmpty()) {
                        val actualPage = pagerState.currentPage % homeCategories.size
                        val title = when (val cat = homeCategories[actualPage]) {
                            is HomeCategory.Favorites -> context.getString(R.string.favorites)
                            is HomeCategory.Recent -> "Recently Played"
                            is HomeCategory.System -> cat.metaSystemInfo.getName(context)
                        }
                        onSystemTitleChanged(title)
                    }
                }

                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    if (homeCategories.isNotEmpty()) {
                        val actualPage = page % homeCategories.size
                        when (val cat = homeCategories[actualPage]) {
                            is HomeCategory.System -> {
                                val metaSystemId = cat.metaSystemInfo.metaSystem
                                com.swordfish.lemuroid.app.mobile.feature.games.GamesScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = viewModel(
                                        key = metaSystemId.name,
                                        factory = com.swordfish.lemuroid.app.mobile.feature.games.GamesViewModel.Factory(
                                            retrogradeDb,
                                            metaSystemId
                                        )
                                    ),
                                    onGameClick = onGameClick,
                                    onGameLongClick = onGameLongClick,
                                    onGameFavoriteToggle = { _, _ -> }
                                )
                            }
                            is HomeCategory.Favorites -> {
                                com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = viewModel(
                                        factory = com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesViewModel.Factory(retrogradeDb)
                                    ),
                                    onGameClick = onGameClick,
                                    onGameLongClick = onGameLongClick
                                )
                            }
                            is HomeCategory.Recent -> {
                                com.swordfish.lemuroid.app.mobile.feature.favorites.RecentScreen(
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = viewModel(
                                        factory = com.swordfish.lemuroid.app.mobile.feature.favorites.RecentViewModel.Factory(retrogradeDb)
                                    ),
                                    onGameClick = onGameClick,
                                    onGameLongClick = onGameLongClick
                                )
                            }
                        }
                    }
                }
                
                // Spacer for controls
                Box(modifier = Modifier.height(130.dp).fillMaxWidth())
            }
        }

        // 2. Overlay Search Results if query is active
        if (searchQuery.isNotBlank()) {
            val searchViewModel: com.swordfish.lemuroid.app.mobile.feature.search.SearchViewModel = viewModel(
                factory = com.swordfish.lemuroid.app.mobile.feature.search.SearchViewModel.Factory(retrogradeDb)
            )
            LaunchedEffect(searchQuery) {
                searchViewModel.queryString.value = searchQuery
            }
            val searchGames = searchViewModel.searchResults.collectAsLazyPagingItems()

            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (searchGames.itemCount == 0) {
                    com.swordfish.lemuroid.app.mobile.shared.compose.ui.DeltArchEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        title = "No Results",
                        message = "No games found for '$searchQuery'.",
                        onLearnMoreClick = null
                    )
                } else {
                    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                        modifier = Modifier.fillMaxSize(),
                        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            count = searchGames.itemCount,
                            key = { searchGames.peek(it)?.id?.toString() ?: "search_$it" }
                        ) { index ->
                            val game = searchGames[index] ?: return@items
                            LemuroidGameCard(
                                game = game,
                                onClick = { onGameClick(game) },
                                onLongClick = { onGameLongClick(game) },
                            )
                        }
                    }
                }
            }
        }

        // 3. Persistent Controls Column (Dots + Search)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 8.dp)
        ) {
            // Category dots
            if (homeCategories.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CategoryDotsIndicator(
                        categories = homeCategories,
                        currentPage = pagerState.currentPage % homeCategories.size,
                        onDotClick = { index ->
                            coroutineScope.launch {
                                val currentVirtual = pagerState.currentPage
                                val currentReal = currentVirtual % homeCategories.size
                                val diff = index - currentReal
                                val adjustedDiff = when {
                                    diff > homeCategories.size / 2 -> diff - homeCategories.size
                                    diff < -homeCategories.size / 2 -> diff + homeCategories.size
                                    else -> diff
                                }
                                pagerState.scrollToPage(currentVirtual + adjustedDiff)
                            }
                        }
                    )
                }
            }

            // Search bar
            com.swordfish.lemuroid.app.mobile.shared.compose.ui.DeltArchBottomSearchBar(
                searchQuery = searchQuery,
                onUpdateQueryString = onUpdateQueryString,
                onSearchFocused = {
                    isSearchInputFocused = true
                    onSearchFocused()
                },
                onSearchUnfocused = {
                    isSearchInputFocused = false
                    onSearchUnfocused()
                }
            )
        }
    }
}

@Composable
private fun CategoryDotsIndicator(
    categories: List<HomeCategory>,
    currentPage: Int,
    onDotClick: (Int) -> Unit,
) {
    if (categories.isEmpty()) return

    val activeIndex = currentPage % categories.size
    var rowWidth by remember { mutableStateOf(0) }
    var lastDragIndex by remember { mutableIntStateOf(-1) }

    Box(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .onSizeChanged { rowWidth = it.width }
            .pointerInput(categories.size) {
                detectDragGestures(
                    onDragStart = { _ -> lastDragIndex = -1 },
                    onDragEnd = { lastDragIndex = -1 },
                    onDragCancel = { lastDragIndex = -1 },
                    onDrag = { change, _ ->
                        if (rowWidth > 0 && categories.isNotEmpty()) {
                            val x = change.position.x.coerceIn(0f, rowWidth.toFloat() - 1f)
                            val index = (x / rowWidth * categories.size)
                                .toInt()
                                .coerceIn(0, categories.size - 1)
                            if (index != lastDragIndex) {
                                lastDragIndex = index
                                onDotClick(index)
                            }
                        }
                    }
                )
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEachIndexed { index, category ->
                val isActive = index == activeIndex
                val dotColor = if (isActive) Color.White else Color.White.copy(alpha = 0.40f)

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onDotClick(index)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isActive || category is HomeCategory.Favorites || category is HomeCategory.Recent) {
                        when (category) {
                            is HomeCategory.Favorites -> {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Favorites",
                                    modifier = Modifier.size(if (isActive) 18.dp else 12.dp),
                                    tint = dotColor
                                )
                            }
                            is HomeCategory.Recent -> {
                                Icon(
                                    imageVector = Icons.Filled.Schedule,
                                    contentDescription = "Recently Played",
                                    modifier = Modifier.size(if (isActive) 18.dp else 12.dp),
                                    tint = dotColor
                                )
                            }
                            is HomeCategory.System -> {
                                val imageRes = category.metaSystemInfo.metaSystem.imageResId
                                Image(
                                    painter = painterResource(id = imageRes),
                                    contentDescription = category.metaSystemInfo.metaSystem.name,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .size(6.dp)
                                .background(dotColor, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

internal sealed class HomeCategory {
    object Favorites : HomeCategory()
    object Recent : HomeCategory()
    data class System(val metaSystemInfo: com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo) : HomeCategory()
}
