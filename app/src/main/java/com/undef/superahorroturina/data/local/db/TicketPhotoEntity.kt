package com.undef.superahorroturina.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ticket_photos",
    foreignKeys = [ForeignKey(
        entity = PurchaseEntity::class,
        parentColumns = ["id"],
        childColumns = ["purchaseId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("purchaseId")]
)
data class TicketPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Int,
    val filePath: String,
    val displayOrder: Int,
    val capturedAt: Long
)
