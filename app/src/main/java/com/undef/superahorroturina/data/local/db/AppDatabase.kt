package com.undef.superahorroturina.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [PurchaseEntity::class, ProductEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun purchaseDao(): PurchaseDao
    abstract fun productDao(): ProductDao
}
