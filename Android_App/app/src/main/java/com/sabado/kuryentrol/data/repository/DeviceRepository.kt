package com.sabado.kuryentrol.data.repository

import com.sabado.kuryentrol.data.model.*
import com.sabado.kuryentrol.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for device-related API operations
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun checkHealth(): Result<HealthResponse> {
        return try {
            val response = apiService.getHealth()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("API health check failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDevices(): Result<List<Device>> {
        return try {
            val response = apiService.getDevices()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.devices)
            } else {
                Result.failure(Exception("Failed to fetch devices: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getEnergyReadings(
        clientId: String,
        limit: Int? = null,
        period: String? = null
    ): Result<List<EnergyReading>> {
        return try {
            val response = apiService.getEnergyReadings(clientId, limit, period)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.readings)
            } else {
                Result.failure(Exception("Failed to fetch energy readings: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSchedules(clientId: String): Result<List<Schedule>> {
        return try {
            val response = apiService.getSchedules(clientId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.schedules)
            } else {
                Result.failure(Exception("Failed to fetch schedules: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSchedule(schedule: Map<String, Any>): Result<String> {
        return try {
            val response = apiService.createSchedule(schedule)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Schedule created successfully")
            } else {
                Result.failure(Exception("Failed to create schedule: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSchedule(scheduleId: Int, updates: Map<String, Any>): Result<String> {
        return try {
            val response = apiService.updateSchedule(scheduleId, updates)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Schedule updated successfully")
            } else {
                Result.failure(Exception("Failed to update schedule: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: Int): Result<String> {
        return try {
            val response = apiService.deleteSchedule(scheduleId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Schedule deleted successfully")
            } else {
                Result.failure(Exception("Failed to delete schedule: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getThreshold(clientId: String): Result<Threshold?> {
        return try {
            val response = apiService.getThreshold(clientId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.threshold)
            } else {
                Result.failure(Exception("Failed to fetch threshold: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setThreshold(clientId: String, threshold: Map<String, Any>): Result<String> {
        return try {
            val response = apiService.setThreshold(clientId, threshold)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Threshold set successfully")
            } else {
                Result.failure(Exception("Failed to set threshold: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteThreshold(clientId: String): Result<String> {
        return try {
            val response = apiService.deleteThreshold(clientId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.message ?: "Threshold deleted successfully")
            } else {
                Result.failure(Exception("Failed to delete threshold: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
