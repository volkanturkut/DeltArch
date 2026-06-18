package com.swordfish.lemuroid.app.mobile.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.swordfish.lemuroid.common.paging.buildFlowPaging
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow

@OptIn(ExperimentalCoroutinesApi::class)
class RecentViewModel(
    retrogradeDb: RetrogradeDatabase,
) : ViewModel() {
    class Factory(
        private val retrogradeDb: RetrogradeDatabase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecentViewModel(retrogradeDb) as T
        }
    }

    private val searchQuery = MutableStateFlow("")

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    val recents: Flow<PagingData<Game>> =
        searchQuery.flatMapLatest { query ->
            buildFlowPaging(60, viewModelScope) { 
                if (query.isBlank()) retrogradeDb.gameDao().selectRecents()
                else retrogradeDb.gameDao().searchRecents("%${query.trim()}%")
            }
        }
}
