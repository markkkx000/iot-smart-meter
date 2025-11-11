package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Energy reading from REST API
 * Received via: GET /api/energy/{client_id}
 */
data class EnergyReading(
    @SerializedName("energy_kwh")
    val energyKwh: Float,
    
    @SerializedName("timestamp")
    val timestamp: String // Format: "2025-11-11 19:22:28"
)
