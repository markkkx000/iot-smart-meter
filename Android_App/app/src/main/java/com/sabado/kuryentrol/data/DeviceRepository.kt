package com.sabado.kuryentrol.data

import com.sabado.kuryentrol.data.model.EnergyReading
import com.sabado.kuryentrol.data.model.Schedule
import com.sabado.kuryentrol.data.model.Threshold
import kotlin.system.measureTimeMillis

/**
 * A simple Result wrapper for repository calls.
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
}

class DeviceRepository(private val apiService: ApiService) {

    suspend fun getSchedulesForDevice(clientId: String): Result<List<Schedule>> {
        return try {
            val response = apiService.getSchedulesForDevice(clientId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(response.body()?.data ?: emptyList())
            } else {
                Result.Error(Exception(response.body()?.error ?: "Failed to get schedules"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun createSchedule(schedule: Schedule): Result<Unit> {
        return try {
            val response = apiService.createSchedule(schedule)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception(response.body()?.error ?: "Failed to create schedule"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateSchedule(scheduleId: Int, schedule: Schedule): Result<Unit> {
        return try {
            val response = apiService.updateSchedule(scheduleId, schedule)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception(response.body()?.error ?: "Failed to update schedule"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: Int): Result<Unit> {
        return try {
            val response = apiService.deleteSchedule(scheduleId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception(response.body()?.error ?: "Failed to delete schedule"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    suspend fun getEnergyReadings(clientId: String, period: String?): Result<List<EnergyReading>> {
        return try {
            val response = apiService.getEnergyReadings(clientId, period = period)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(response.body()?.data ?: emptyList())
            } else {
                Result.Error(Exception(response.body()?.error ?: "Failed to get energy readings"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
    
    suspend fun getThreshold(clientId: String): Result<Threshold?> {
        return try {
            val response = apiService.getThreshold(clientId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(response.body()?.data)
            } else {
                Result.Error(Exception(response.body()?.error ?: "Failed to get threshold"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun setThreshold(clientId: String, threshold: Threshold): Result<Unit> {
        return try {
            val response = apiService.setThreshold(clientId, threshold)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception(response.body()?.error ?: "Failed to set threshold"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteThreshold(clientId: String): Result<Unit> {
        return try {
            val response = apiService.deleteThreshold(clientId)
            if (response.isSuccessful && response.body()?.success == true) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception(response.body()?.error ?: "Failed to delete threshold"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun pingApi(): Result<Long> {
        return try {
            val time = measureTimeMillis {
                apiService.getHealthStatus()
            }
            Result.Success(time)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
