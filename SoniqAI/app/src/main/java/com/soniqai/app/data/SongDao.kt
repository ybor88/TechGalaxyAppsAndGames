// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Song>>

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): Song?

    @Query("SELECT * FROM songs WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Song?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: Song)

    @Query("DELETE FROM songs WHERE id = :id")
    suspend fun deleteById(id: String)
}
