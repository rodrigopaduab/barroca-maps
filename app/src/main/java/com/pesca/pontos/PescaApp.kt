package com.pesca.pontos

import android.app.Application
import androidx.room.withTransaction
import com.pesca.pontos.data.AppDatabase
import com.pesca.pontos.data.Catch
import com.pesca.pontos.data.PhotoStore
import com.pesca.pontos.data.Spot
import com.pesca.pontos.data.Trail
import com.pesca.pontos.data.TrailMark
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class PescaApp : Application() {
    lateinit var repo: Repository
        private set
    lateinit var settings: Settings
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(AppDatabase.create(this), PhotoStore(this))
        settings = Settings(this)
    }
}

/** Ponto único de acesso aos dados. Cuida também de apagar fotos que saíram de uso. */
class Repository(private val db: AppDatabase, val photos: PhotoStore) {
    /** Troca tudo do banco pelo conteúdo de um backup, de uma vez só (ou tudo, ou nada). */
    suspend fun replaceAll(
        newSpots: List<Spot>,
        newCatches: List<Catch>,
        newTrails: List<Trail> = emptyList(),
        newMarks: List<TrailMark> = emptyList(),
    ) {
        db.withTransaction {
            trails.deleteAllMarks()
            trails.deleteAll()
            catches.deleteAll()
            spots.deleteAll()
            spots.insertAll(newSpots)
            catches.insertAll(newCatches)
            trails.insertAll(newTrails)
            trails.insertMarks(newMarks)
        }
    }

    val trails get() = db.trailDao()

    /** Caminho que o mapa deve começar a seguir (pedido feito na tela do ponto). */
    var followTrailId by mutableStateOf<Long?>(null)

    /** Caminho lido de um QR, esperando confirmação. */
    var pendingTrailImport by mutableStateOf<com.pesca.pontos.trail.SharedTrail?>(null)

    suspend fun saveTrail(trail: Trail, marks: List<TrailMark>): Long = db.withTransaction {
        val id = trails.insert(trail)
        trails.insertMarks(marks.map { it.copy(trailId = id) })
        id
    }

    /** Importa um caminho recebido: cria o ponto de destino se ainda não existir um igual por perto. */
    suspend fun importTrail(t: com.pesca.pontos.trail.SharedTrail): Long {
        var spotId: Long? = null
        t.spot?.let { sp ->
            val existing = spots.listAll().firstOrNull {
                com.pesca.pontos.trail.Geo.dist(
                    com.pesca.pontos.trail.P(it.latitude, it.longitude),
                    com.pesca.pontos.trail.P(sp.latitude, sp.longitude),
                ) < 40
            }
            spotId = existing?.id ?: spots.insert(
                Spot(name = sp.name, notes = sp.notes, latitude = sp.latitude, longitude = sp.longitude, color = sp.color, mainFish = sp.mainFish)
            )
        }
        val pts = t.points
        return saveTrail(
            Trail(
                name = t.name, spotId = spotId, durationMs = t.durationMs,
                distanceM = com.pesca.pontos.trail.Geo.length(pts), points = com.pesca.pontos.trail.Geo.encode(pts),
            ),
            t.marks.map { TrailMark(trailId = 0, latitude = it.p.lat, longitude = it.p.lng, type = it.type, label = it.label) },
        )
    }

    val spots = db.spotDao()
    val catches = db.catchDao()

    /** Ponto lido de um QR code, esperando o usuário conferir e salvar. */
    var pendingImport: SharedSpot? = null

    suspend fun saveSpot(spot: Spot, originalPhotos: List<String>): Long {
        val id = if (spot.id == 0L) spots.insert(spot) else { spots.update(spot); spot.id }
        photos.delete(originalPhotos - spot.photos.toSet())
        return id
    }

    /** Preenche o clima dos pontos e peixes registrados sem internet. Para na primeira falha (provavelmente sem sinal). */
    suspend fun fillMissingWeather() {
        for (s in spots.listWithoutWeather()) {
            val w = Weather.fetch(s.latitude, s.longitude, s.createdAt) ?: return
            spots.update(
                s.copy(
                    weatherTemp = w.tempC, weatherCode = w.code, weatherWind = w.windKmh,
                    weatherHumidity = w.humidity, weatherFetched = true,
                )
            )
        }
        for (c in catches.listWithoutWeather()) {
            val s = spots.get(c.spotId) ?: continue
            val w = Weather.fetch(s.latitude, s.longitude, c.caughtAt) ?: return
            catches.update(
                c.copy(
                    weatherTemp = w.tempC, weatherCode = w.code, weatherWind = w.windKmh,
                    weatherHumidity = w.humidity, weatherFetched = true,
                )
            )
        }
    }

    suspend fun deleteSpot(spot: Spot) {
        trails.unlinkSpot(spot.id)
        val catchPhotos = catches.listForSpot(spot.id).flatMap { it.photos }
        spots.delete(spot) // capturas são apagadas em cascata
        photos.delete(spot.photos + catchPhotos)
    }

    suspend fun saveCatch(c: Catch, originalPhotos: List<String>): Long {
        val id = if (c.id == 0L) catches.insert(c) else { catches.update(c); c.id }
        photos.delete(originalPhotos - c.photos.toSet())
        return id
    }

    suspend fun deleteCatch(c: Catch) {
        catches.delete(c)
        photos.delete(c.photos)
    }
}
