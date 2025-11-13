package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Aggregate energy consumption reading from REST API
 * Received via: GET /api/energy/{client_id}?period={period}
 */
data class AggregateConsumptionResponse(
    val success: Boolean,
    @SerializedName("client_id")
    val clientId: String,
    val period: String,
    @SerializedName("consumption_kwh")
    val consumptionKwh: Float
)
