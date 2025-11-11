package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Real-time metrics from PZEM-004T sensor
 * Received via MQTT topic: dev/{CLIENT_ID}/pzem/metrics
 */
data class PzemMetrics(
    @SerializedName("voltage")
    val voltage: Float,
    
    @SerializedName("current")
    val current: Float,
    
    @SerializedName("power")
    val power: Float
)
