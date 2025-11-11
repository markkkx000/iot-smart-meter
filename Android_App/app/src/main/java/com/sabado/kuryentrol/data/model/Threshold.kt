package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Energy threshold information from REST API
 * Received via: GET /api/thresholds/{client_id}
 */
data class Threshold(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("client_id")
    val clientId: String,
    
    @SerializedName("limit_kwh")
    val limitKwh: Float,
    
    @SerializedName("reset_period")
    val resetPeriod: String, // "daily", "weekly", or "monthly"
    
    @SerializedName("enabled")
    val enabled: Int,
    
    @SerializedName("last_reset")
    val lastReset: String,
    
    @SerializedName("created_at")
    val createdAt: String
)
