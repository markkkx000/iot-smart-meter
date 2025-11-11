package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Device information from REST API
 * Received via: GET /api/devices
 */
data class Device(
    @SerializedName("client_id")
    val clientId: String,
    
    @SerializedName("last_seen")
    val lastSeen: String,
    
    @SerializedName("current_energy_kwh")
    val currentEnergyKwh: Float
)
