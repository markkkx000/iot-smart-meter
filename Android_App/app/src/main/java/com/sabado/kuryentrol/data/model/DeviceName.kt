package com.sabado.kuryentrol.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_names")
data class DeviceName(
    @PrimaryKey
    val clientId: String,
    val customName: String
)
