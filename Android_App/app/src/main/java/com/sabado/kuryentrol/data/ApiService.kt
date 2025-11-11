package com.sabado.kuryentrol.data

import com.sabado.kuryentrol.data.model.Device
import com.sabado.kuryentrol.data.model.EnergyReading
import com.sabado.kuryentrol.data.model.Schedule
import com.sabado.kuryentrol.data.model.Threshold
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // region Health Check
    @GET("health")
    suspend fun getHealthStatus(): Response<HealthResponse>
    // endregion

    // region Devices
    @GET("devices")
    suspend fun getAllDevices(): Response<ApiResponse<List<Device>>>
    // endregion

    // region Schedules
    @GET("schedules/{clientId}")
    suspend fun getSchedulesForDevice(@Path("clientId") clientId: String): Response<ApiResponse<List<Schedule>>>

    @POST("schedules")
    suspend fun createSchedule(@Body schedule: Schedule): Response<ApiResponse<Any>>

    @PUT("schedules/{scheduleId}")
    suspend fun updateSchedule(@Path("scheduleId") scheduleId: Int, @Body schedule: Schedule): Response<ApiResponse<Any>>

    @DELETE("schedules/{scheduleId}")
    suspend fun deleteSchedule(@Path("scheduleId") scheduleId: Int): Response<ApiResponse<Any>>
    // endregion

    // region Thresholds
    @GET("thresholds/{clientId}")
    suspend fun getThreshold(@Path("clientId") clientId: String): Response<ApiResponse<Threshold>>

    @PUT("thresholds/{clientId}")
    suspend fun setThreshold(@Path("clientId") clientId: String, @Body threshold: Threshold): Response<ApiResponse<Any>>

    @DELETE("thresholds/{clientId}")
    suspend fun deleteThreshold(@Path("clientId") clientId: String): Response<ApiResponse<Any>>
    // endregion

    // region Energy Data
    @GET("energy/{clientId}")
    suspend fun getEnergyReadings(
        @Path("clientId") clientId: String,
        @Query("limit") limit: Int? = null,
        @Query("period") period: String? = null // "day", "week", or "month"
    ): Response<ApiResponse<List<EnergyReading>>>
    // endregion
}

/**
 * A generic wrapper for API responses, based on the success/error format.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: String?
)

/**
 * Represents the response from the /api/health endpoint.
 */
data class HealthResponse(
    val success: Boolean,
    val service: String,
    val version: String,
    val timestamp: String
)
