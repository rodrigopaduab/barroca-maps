package com.pesca.pontos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Spot::class, Catch::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spotDao(): SpotDao
    abstract fun catchDao(): CatchDao

    companion object {
        /** 0.1 -> 0.2: peixe principal e clima no ponto. Mantém tudo que já foi cadastrado. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE spots ADD COLUMN mainFish TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE spots ADD COLUMN weatherTemp REAL")
                db.execSQL("ALTER TABLE spots ADD COLUMN weatherCode INTEGER")
                db.execSQL("ALTER TABLE spots ADD COLUMN weatherWind REAL")
                db.execSQL("ALTER TABLE spots ADD COLUMN weatherHumidity INTEGER")
                db.execSQL("ALTER TABLE spots ADD COLUMN weatherFetched INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** 0.3 -> 0.4: clima em cada peixe. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catches ADD COLUMN weatherTemp REAL")
                db.execSQL("ALTER TABLE catches ADD COLUMN weatherCode INTEGER")
                db.execSQL("ALTER TABLE catches ADD COLUMN weatherWind REAL")
                db.execSQL("ALTER TABLE catches ADD COLUMN weatherHumidity INTEGER")
                db.execSQL("ALTER TABLE catches ADD COLUMN weatherFetched INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pesca.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
