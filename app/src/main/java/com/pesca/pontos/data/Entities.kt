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
    /** Cor do pino: 0..7 = cores prontas antigas (versão 0.1); outros valores = cor ARGB livre. */
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Peixe principal do ponto (usado também como filtro). */
    val mainFish: String = "",
    /** Clima na hora em que o ponto foi criado (Open-Meteo). */
    val weatherTemp: Double? = null,
    val weatherCode: Int? = null,
    val weatherWind: Double? = null,
    val weatherHumidity: Int? = null,
    /** true quando o clima já foi buscado (ou desistimos); false = buscar quando tiver internet. */
    val weatherFetched: Boolean = false,
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
    /** Clima na hora em que o peixe foi pego (Open-Meteo). */
    val weatherTemp: Double? = null,
    val weatherCode: Int? = null,
    val weatherWind: Double? = null,
    val weatherHumidity: Int? = null,
    val weatherFetched: Boolean = false,
)

/** Resumo usado no cartão do mapa. */
data class SpotSummary(
    val catchCount: Int,
    val lastCatchAt: Long?,
)

/** Quantos peixes cada ponto tem (lista de pontos). */
data class SpotCount(
    val spotId: Long,
    val n: Int,
)
