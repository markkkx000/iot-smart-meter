package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Wrapper classes for API responses based on REST API documentation
 */

data class DevicesResponse(
    val success: Boolean,
    val devices: List<Device>
)

data class EnergyResponse(
    val success: Boolean,
    @SerializedName("client_id")
    val clientId: String,
    val readings: List<EnergyReading>
)

data class SchedulesResponse(
    val success: Boolean,
    @SerializedName("client_id")
    val clientId: String?,
    val schedules: List<Schedule>
)

data class ThresholdResponse(
    val success: Boolean,
    val threshold: Threshold?
)

data class ApiSuccessResponse(
    val success: Boolean,
    val message: String?
)

data class HealthResponse(
    val success: Boolean,
    val service: String,
    val version: String,
    val timestamp: String
)
