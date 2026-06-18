package com.swordfish.lemuroid.app.mobile.feature.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.DeltArchEmptyState
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameCard
import com.swordfish.lemuroid.lib.library.db.entity.Game

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentScreen(
    modifier: Modifier = Modifier,
    viewModel: RecentViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    val games = viewModel.recents.collectAsLazyPagingItems()

    if (games.itemCount == 0 && games.loadState.refresh is androidx.paging.LoadState.NotLoading) {
        DeltArchEmptyState(
            modifier = modifier,
            title = "No Recent Games",
            message = "Games you play will appear here.",
            onLearnMoreClick = null
        )
        return
    } else if (games.itemCount == 0) {
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
            key = { games.peek(it)?.id?.toString() ?: "recent_$it" }
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
