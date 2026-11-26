package com.pesca.pontos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SpotDao {
    @Query("SELECT * FROM spots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Spot>>

    @Query("SELECT * FROM spots WHERE id = :id")
    fun observe(id: Long): Flow<Spot?>

    @Query("SELECT * FROM spots WHERE id = :id")
    suspend fun get(id: Long): Spot?

    @Query("SELECT * FROM spots")
    suspend fun listAll(): List<Spot>

    @Query("SELECT * FROM spots WHERE weatherFetched = 0")
    suspend fun listWithoutWeather(): List<Spot>

    @Query("SELECT DISTINCT mainFish FROM spots WHERE mainFish != '' ORDER BY mainFish")
    fun observeMainFish(): Flow<List<String>>

    @Query("DELETE FROM spots")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(spots: List<Spot>)

    @Insert
    suspend fun insert(spot: Spot): Long

    @Update
    suspend fun update(spot: Spot)

    @Delete
    suspend fun delete(spot: Spot)
}

@Dao
interface CatchDao {
    @Query("SELECT * FROM catches WHERE spotId = :spotId ORDER BY caughtAt DESC")
    fun observeForSpot(spotId: Long): Flow<List<Catch>>

    @Query("SELECT * FROM catches WHERE spotId = :spotId")
    suspend fun listForSpot(spotId: Long): List<Catch>

    @Query("SELECT COUNT(*) AS catchCount, MAX(caughtAt) AS lastCatchAt FROM catches WHERE spotId = :spotId")
    fun observeSummary(spotId: Long): Flow<SpotSummary>

    @Query("SELECT * FROM catches WHERE id = :id")
    suspend fun get(id: Long): Catch?

    @Query("SELECT * FROM catches")
    suspend fun listAll(): List<Catch>

    @Query("SELECT * FROM catches WHERE weatherFetched = 0")
    suspend fun listWithoutWeather(): List<Catch>

    @Query("DELETE FROM catches")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(catches: List<Catch>)

    @Query("SELECT spotId, COUNT(*) AS n FROM catches GROUP BY spotId")
    fun observeCounts(): Flow<List<SpotCount>>

    @Query("SELECT DISTINCT species FROM catches ORDER BY species")
    suspend fun allSpecies(): List<String>

    @Insert
    suspend fun insert(c: Catch): Long

    @Update
    suspend fun update(c: Catch)

    @Delete
    suspend fun delete(c: Catch)
}
