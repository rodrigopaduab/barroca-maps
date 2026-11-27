package com.pesca.pontos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Spot::class, Catch::class, Trail::class, TrailMark::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun spotDao(): SpotDao
    abstract fun catchDao(): CatchDao
    abstract fun trailDao(): TrailDao

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

        /** 0.3 -> 0.4: caminhos gravados pelo GPS e suas marcações. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trails` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                        "`spotId` INTEGER, `createdAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `distanceM` REAL NOT NULL, " +
                        "`points` TEXT NOT NULL, FOREIGN KEY(`spotId`) REFERENCES `spots`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trails_spotId` ON `trails` (`spotId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `trail_marks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trailId` INTEGER NOT NULL, " +
                        "`latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `type` TEXT NOT NULL, `label` TEXT NOT NULL, `at` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`trailId`) REFERENCES `trails`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_trail_marks_trailId` ON `trail_marks` (`trailId`)")
            }
        }

        /** 0.5 -> 0.6: cor da linha do caminho. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trails ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pesca.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
