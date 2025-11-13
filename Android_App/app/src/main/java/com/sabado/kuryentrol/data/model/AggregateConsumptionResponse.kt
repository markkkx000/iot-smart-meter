package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

data class AggregateConsumptionResponse(
    val success: Boolean,
    @SerializedName("client_id") val clientId: String,
    val period: String,
    @SerializedName("consumption_kwh") val consumptionKwh: Float
)
