package com.unpluck.app.data

import androidx.room.*
import com.unpluck.app.defs.Space
import kotlinx.coroutines.flow.Flow

@Dao
interface SpaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(space: Space)

    @Update
    suspend fun update(space: Space)

    @Query("SELECT * FROM spaces ORDER BY name ASC")
    fun getAllSpaces(): Flow<List<Space>>

    @Query("SELECT * FROM spaces WHERE id = :spaceId")
    fun getSpaceById(spaceId: String): Flow<Space?>
}