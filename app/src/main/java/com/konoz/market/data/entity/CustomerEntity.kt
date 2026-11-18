package com.konoz.market.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A saved customer. [phone] is stored as WhatsApp digits (country code first, no plus; see PhoneNumbers.toWhatsApp),
 * so one phone is one customer however it was typed.
 */
@Entity(
    tableName = "customers",
    indices = [Index("phone", unique = true), Index("name")],
)
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val createdAt: Long,
    val updatedAt: Long,
)
