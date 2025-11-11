package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a device returned by `GET /api/devices`.
 */
data class Device(
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("last_seen")
    val lastSeen: String,
    @SerializedName("current_energy_kwh")
    val currentEnergyKwh: Float
)

/**
 * Represents a schedule returned by `GET /api/schedules`.
 */
data class Schedule(
    val id: Int,
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("schedule_type")
    val scheduleType: String, // "daily" or "timer"
    @SerializedName("start_time")
    val startTime: String?,
    @SerializedName("end_time")
    val endTime: String?,
    @SerializedName("days_of_week")
    val daysOfWeek: String?,
    val enabled: Int,
    @SerializedName("created_at")
    val createdAt: String
)

/**
 * Represents a threshold returned by `GET /api/thresholds/<client_id>`.
 */
data class Threshold(
    val id: Int,
    @SerializedName("client_id")
    val clientId: String,
    @SerializedName("limit_kwh")
    val limitKwh: Float,
    @SerializedName("reset_period")
    val resetPeriod: String, // "daily", "weekly", or "monthly"
    val enabled: Int,
    @SerializedName("last_reset")
    val lastReset: String,
    @SerializedName("created_at")
    val createdAt: String
)

/**
 * Represents a single energy reading from `GET /api/energy/<client_id>`.
 */
data class EnergyReading(
    @SerializedName("energy_kwh")
    val energyKwh: Float,
    val timestamp: String
)
