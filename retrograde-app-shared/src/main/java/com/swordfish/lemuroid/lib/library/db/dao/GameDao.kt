/*
 * GameDao.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun selectById(id: Int): Game?

    @Query("SELECT * FROM games WHERE fileUri = :fileUri")
    fun selectByFileUri(fileUri: String): Game?

    @Query("SELECT * FROM games WHERE lastIndexedAt < :lastIndexedAt")
    fun selectByLastIndexedAtLessThan(lastIndexedAt: Long): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 GROUP BY title, systemId, coverFrontUrl ORDER BY title ASC")
    fun selectFavorites(): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 AND title LIKE :query GROUP BY title, systemId, coverFrontUrl ORDER BY title ASC")
    fun searchFavorites(query: String): PagingSource<Int, Game>

    @Query("SELECT count(DISTINCT title) FROM games WHERE isFavorite = 1")
    fun selectFavoritesCountFlow(): Flow<Int>

    @Query(
        """
        SELECT * FROM games WHERE lastPlayedAt IS NOT NULL AND isFavorite = 0 GROUP BY title, systemId, coverFrontUrl ORDER BY lastPlayedAt DESC LIMIT :limit
        """,
    )
    fun selectFirstUnfavoriteRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 GROUP BY title, systemId, coverFrontUrl ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavoritesRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL GROUP BY title, systemId, coverFrontUrl ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun asyncSelectFirstRecents(limit: Int): List<Game>

    @Query("SELECT count(DISTINCT title) FROM games WHERE lastPlayedAt IS NOT NULL")
    fun selectRecentsCountFlow(): Flow<Int>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL GROUP BY title, systemId, coverFrontUrl ORDER BY lastPlayedAt DESC")
    fun selectRecents(): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL AND title LIKE :query GROUP BY title, systemId, coverFrontUrl ORDER BY lastPlayedAt DESC")
    fun searchRecents(query: String): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 GROUP BY title, systemId, coverFrontUrl ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavorites(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NULL GROUP BY title, systemId, coverFrontUrl LIMIT :limit")
    fun selectFirstNotPlayed(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE systemId = :systemId GROUP BY title, systemId, coverFrontUrl ORDER BY title ASC, id DESC")
    fun selectBySystem(systemId: String): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE systemId = :systemId AND title LIKE :query GROUP BY title, systemId, coverFrontUrl ORDER BY title ASC, id DESC")
    fun searchBySystem(systemId: String, query: String): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE systemId IN (:systemIds) GROUP BY title, systemId, coverFrontUrl ORDER BY title ASC, id DESC")
    fun selectBySystems(systemIds: List<String>): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE systemId IN (:systemIds) AND title LIKE :query GROUP BY title, systemId, coverFrontUrl ORDER BY title ASC, id DESC")
    fun searchBySystems(systemIds: List<String>, query: String): PagingSource<Int, Game>

    @Query("SELECT DISTINCT systemId FROM games ORDER BY systemId ASC")
    suspend fun selectSystems(): List<String>

    @Query("SELECT count(DISTINCT title) count, systemId systemId FROM games GROUP BY systemId")
    fun selectSystemsWithCount(): Flow<List<SystemCount>>

    @Insert
    fun insert(games: List<Game>): List<Long>

    @Delete
    fun delete(games: List<Game>)

    @Update
    suspend fun update(game: Game)

    @Update
    fun update(games: List<Game>)
}

data class SystemCount(val systemId: String, val count: Int)
