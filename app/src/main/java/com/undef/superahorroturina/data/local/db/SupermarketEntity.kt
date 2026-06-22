package com.undef.superahorroturina.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "supermarkets")
data class SupermarketEntity(
    @PrimaryKey val name: String
)
