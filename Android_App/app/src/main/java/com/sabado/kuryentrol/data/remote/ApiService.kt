package com.sabado.kuryentrol.data.remote

import com.sabado.kuryentrol.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for Smart Meter REST API
 * Base URL: http://mqttpi.local:5001/api/
 */
interface ApiService {

    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>

    @GET("devices")
    suspend fun getDevices(): Response<DevicesResponse>

    @GET("energy/{clientId}")
    suspend fun getEnergyReadings(
        @Path("clientId") clientId: String,
        @Query("limit") limit: Int? = null,
        @Query("period") period: String? = null
    ): Response<EnergyResponse>

    @GET("energy/{clientId}/range")
    suspend fun getEnergyReadingsByRange(
        @Path("clientId") clientId: String,
        @Query("start") start: String,
        @Query("end") end: String
    ): Response<EnergyReadingsResponse>


    @GET("energy/{clientId}")
    suspend fun getAggregateConsumption(
        @Path("clientId") clientId: String,
        @Query("period") period: String
    ): Response<AggregateConsumptionResponse>


    @GET("schedules/{clientId}")
    suspend fun getSchedules(
        @Path("clientId") clientId: String
    ): Response<SchedulesResponse>

    @POST("schedules")
    suspend fun createSchedule(
        @Body schedule: Map<String, String>
    ): Response<ApiSuccessResponse>

    @PUT("schedules/{scheduleId}")
    suspend fun updateSchedule(
        @Path("scheduleId") scheduleId: Int,
        @Body updates: Map<String, String>
    ): Response<ApiSuccessResponse>

    @DELETE("schedules/{scheduleId}")
    suspend fun deleteSchedule(
        @Path("scheduleId") scheduleId: Int
    ): Response<ApiSuccessResponse>

    @GET("thresholds/{clientId}")
    suspend fun getThreshold(
        @Path("clientId") clientId: String
    ): Response<ThresholdResponse>

    @PUT("thresholds/{clientId}")
    suspend fun setThreshold(
        @Path("clientId") clientId: String,
        @Body threshold: Map<String, String>
    ): Response<ApiSuccessResponse>

    @DELETE("thresholds/{clientId}")
    suspend fun deleteThreshold(
        @Path("clientId") clientId: String
    ): Response<ApiSuccessResponse>
}
