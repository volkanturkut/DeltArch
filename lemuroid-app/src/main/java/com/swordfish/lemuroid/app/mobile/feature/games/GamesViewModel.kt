@file:Suppress("all")

package com.swordfish.lemuroid.app.mobile.feature.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.swordfish.lemuroid.common.paging.buildFlowPaging
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class GamesViewModel(
    private val retrogradeDb: RetrogradeDatabase,
    initialMetaSystem: MetaSystemID,
) : ViewModel() {
    class Factory(
        private val retrogradeDb: RetrogradeDatabase,
        private val initialMetaSystem: MetaSystemID,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GamesViewModel(retrogradeDb, initialMetaSystem) as T
        }
    }

    private val metaSystemId = MutableStateFlow(initialMetaSystem)
    private val searchQuery = MutableStateFlow("")

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val games: Flow<PagingData<Game>> =
        kotlinx.coroutines.flow.combine(metaSystemId, searchQuery) { metaSystem, query ->
            Pair(metaSystem.systemIDs.map { it.dbname }, query.trim())
        }.flatMapLatest { (systemIds, query) ->
            when (systemIds.size) {
                0 -> emptyFlow()
                1 -> buildFlowPaging(60, viewModelScope) { 
                    if (query.isBlank()) retrogradeDb.gameDao().selectBySystem(systemIds.first())
                    else retrogradeDb.gameDao().searchBySystem(systemIds.first(), "%$query%")
                }
                else -> buildFlowPaging(60, viewModelScope) { 
                    if (query.isBlank()) retrogradeDb.gameDao().selectBySystems(systemIds)
                    else retrogradeDb.gameDao().searchBySystems(systemIds, "%$query%")
                }
            }
        }
}
