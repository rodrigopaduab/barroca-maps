package com.pesca.pontos.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Um ponto de pesca marcado no mapa. As fotos são caminhos de arquivos dentro do app. */
@Entity(tableName = "spots")
data class Spot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String = "",
    val latitude: Double,
    val longitude: Double,
    val photos: List<String> = emptyList(),
    /** Índice em PIN_COLORS (0 = laranja da marca). */
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Um peixe pego em um ponto. */
@Entity(
    tableName = "catches",
    foreignKeys = [
        ForeignKey(
            entity = Spot::class,
            parentColumns = ["id"],
            childColumns = ["spotId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("spotId")],
)
data class Catch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotId: Long,
    val species: String,
    val weightKg: Double? = null,
    val lengthCm: Double? = null,
    val caughtAt: Long = System.currentTimeMillis(),
    val bait: String = "",
    val notes: String = "",
    val released: Boolean = false,
    val photos: List<String> = emptyList(),
)

/** Resumo usado no cartão do mapa. */
data class SpotSummary(
    val catchCount: Int,
    val lastCatchAt: Long?,
)
