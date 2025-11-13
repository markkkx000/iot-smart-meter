package com.sabado.kuryentrol.data.model

/**
 * Energy reading by range from REST API
 * Received via: GET /api/energy/{client_id}/range?start={start}&end={end}
 */
data class EnergyReadingsResponse(
    val success: Boolean,
    val clientId: String,
    val readings: List<EnergyReading>
)