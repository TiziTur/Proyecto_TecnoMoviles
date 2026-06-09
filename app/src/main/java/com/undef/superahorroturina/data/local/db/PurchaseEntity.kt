package com.undef.superahorroturina.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "purchases")
data class PurchaseEntity(
    @PrimaryKey val id: Int,
    val purchaseDate: String,
    val purchaseTime: String,
    val supermarket: String,
    val total: Double,
    val productCount: Int
)
