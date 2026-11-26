package com.pesca.pontos

import android.app.Application
import androidx.room.withTransaction
import com.pesca.pontos.data.AppDatabase
import com.pesca.pontos.data.Catch
import com.pesca.pontos.data.PhotoStore
import com.pesca.pontos.data.Spot

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
    suspend fun replaceAll(newSpots: List<Spot>, newCatches: List<Catch>) {
        db.withTransaction {
            catches.deleteAll()
            spots.deleteAll()
            spots.insertAll(newSpots)
            catches.insertAll(newCatches)
        }
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
