package com.swordfish.lemuroid.app.mobile.feature.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.DeltArchEmptyState
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.lib.library.db.entity.Game

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GamesScreen(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    val games = viewModel.games.collectAsLazyPagingItems()

    if (games.itemCount == 0 && games.loadState.refresh is androidx.paging.LoadState.NotLoading) {
        DeltArchEmptyState(
            modifier = modifier.fillMaxSize(),
            title = "No Games",
            message = "Import ROMs using the + button.",
            onLearnMoreClick = null
        )
        return
    } else if (games.itemCount == 0) {
        // Show an empty box while loading for blazingly fast appearance
        androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize())
        return
    }

    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = games.itemCount,
            key = { games.peek(it)?.id?.toString() ?: "game_$it" }
        ) { index ->
            val game = games[index] ?: return@items
            LemuroidGameCard(
                game = game,
                onClick = { onGameClick(game) },
                onLongClick = { onGameLongClick(game) },
            )
        }
    }
}
