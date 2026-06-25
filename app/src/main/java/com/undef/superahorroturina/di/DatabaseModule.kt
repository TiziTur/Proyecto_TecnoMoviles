package com.undef.superahorroturina.di

import android.content.Context
import androidx.room.Room
import com.undef.superahorroturina.data.local.db.AppDatabase
import com.undef.superahorroturina.data.local.db.PriceComparisonDao
import com.undef.superahorroturina.data.local.db.ProductDao
import com.undef.superahorroturina.data.local.db.PurchaseDao
import com.undef.superahorroturina.data.local.db.SupermarketDao
import com.undef.superahorroturina.data.local.db.TicketPhotoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "klarity_db")
            .addMigrations(
                AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePurchaseDao(db: AppDatabase): PurchaseDao = db.purchaseDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()

    @Provides
    fun provideSupermarketDao(db: AppDatabase): SupermarketDao = db.supermarketDao()

    @Provides
    fun providePriceComparisonDao(db: AppDatabase): PriceComparisonDao = db.priceComparisonDao()

    @Provides
    fun provideTicketPhotoDao(db: AppDatabase): TicketPhotoDao = db.ticketPhotoDao()
}
