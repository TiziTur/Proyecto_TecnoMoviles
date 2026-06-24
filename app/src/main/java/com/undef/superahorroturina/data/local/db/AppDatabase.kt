package com.undef.superahorroturina.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PurchaseEntity::class, ProductEntity::class, SupermarketEntity::class, PriceComparisonEntity::class, TicketPhotoEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
    abstract fun productDao(): ProductDao
    abstract fun supermarketDao(): SupermarketDao
    abstract fun priceComparisonDao(): PriceComparisonDao
    abstract fun ticketPhotoDao(): TicketPhotoDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN seedProductName TEXT")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS supermarkets (name TEXT NOT NULL PRIMARY KEY)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS price_comparisons (
                        productName TEXT NOT NULL PRIMARY KEY,
                        brand TEXT NOT NULL,
                        category TEXT NOT NULL,
                        pricesJson TEXT NOT NULL,
                        cheapestAt TEXT NOT NULL,
                        cheapestPrice REAL NOT NULL,
                        maxSavings REAL NOT NULL,
                        savingsPct INTEGER NOT NULL
                    )"""
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // SQL extraído literal del schema generado por Room (kapt -Aroom.schemaLocation)
                // para esta entidad, no escrito a mano: una CREATE TABLE sin el FOREIGN KEY no
                // matchea lo que Room espera tras migrar, y con fallbackToDestructiveMigration()
                // eso se traduce en un wipe silencioso de toda la base en el próximo upgrade real.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ticket_photos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `purchaseId` INTEGER NOT NULL, `filePath` TEXT NOT NULL, `displayOrder` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL, FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ticket_photos_purchaseId` ON `ticket_photos` (`purchaseId`)")
            }
        }
    }
}
