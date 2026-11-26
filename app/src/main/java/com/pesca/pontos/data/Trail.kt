package com.pesca.pontos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Caminho gravado pelo GPS. "points" é a linha codificada (formato polyline, precisão 1e6). */
@Entity(
    tableName = "trails",
    foreignKeys = [
        ForeignKey(entity = Spot::class, parentColumns = ["id"], childColumns = ["spotId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("spotId")],
)
data class Trail(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Ponto ao qual o caminho leva (pode ser nenhum). */
    val spotId: Long?,
    val createdAt: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val distanceM: Double,
    val points: String,
)

/** Marcação no caminho: porteira, atoleiro, texto livre... */
@Entity(
    tableName = "trail_marks",
    foreignKeys = [
        ForeignKey(entity = Trail::class, parentColumns = ["id"], childColumns = ["trailId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("trailId")],
)
data class TrailMark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trailId: Long,
    val latitude: Double,
    val longitude: Double,
    /** PORTEIRA, ATOLEIRO, CARRO, PONTE, SEM_SINAL ou TEXTO */
    val type: String,
    /** Nome mostrado (para TEXTO, o que a pessoa escreveu). */
    val label: String,
    val at: Long = System.currentTimeMillis(),
)

@Dao
interface TrailDao {
    @Query("SELECT * FROM trails ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Trail>>

    @Query("SELECT * FROM trails WHERE spotId = :spotId ORDER BY createdAt DESC")
    fun observeForSpot(spotId: Long): Flow<List<Trail>>

    @Query("SELECT * FROM trails WHERE id = :id")
    suspend fun get(id: Long): Trail?

    @Query("SELECT * FROM trails")
    suspend fun listAll(): List<Trail>

    @Query("SELECT * FROM trail_marks WHERE trailId = :trailId ORDER BY at")
    suspend fun marks(trailId: Long): List<TrailMark>

    @Query("SELECT * FROM trail_marks")
    suspend fun listAllMarks(): List<TrailMark>

    @Query("SELECT * FROM trail_marks")
    fun observeAllMarks(): Flow<List<TrailMark>>

    @Query("UPDATE trails SET spotId = NULL WHERE spotId = :spotId")
    suspend fun unlinkSpot(spotId: Long)

    @Insert
    suspend fun insert(trail: Trail): Long

    @Insert
    suspend fun insertMarks(marks: List<TrailMark>)

    @Insert
    suspend fun insertAll(trails: List<Trail>)

    @Delete
    suspend fun delete(trail: Trail)

    @Query("DELETE FROM trails")
    suspend fun deleteAll()

    @Query("DELETE FROM trail_marks")
    suspend fun deleteAllMarks()
}
