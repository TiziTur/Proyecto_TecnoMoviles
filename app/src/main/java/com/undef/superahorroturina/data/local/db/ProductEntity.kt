package com.undef.superahorroturina.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(
        entity = PurchaseEntity::class,
        parentColumns = ["id"],
        childColumns = ["purchaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("purchaseId")]
)
data class ProductEntity(
    @PrimaryKey val id: Int,
    val purchaseId: Int,
    val code: String,
    val name: String,
    val description: String,
    val price: Double,
    val quantity: Int
)
