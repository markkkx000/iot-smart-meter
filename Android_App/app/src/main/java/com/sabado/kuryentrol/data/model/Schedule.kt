package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Schedule information from REST API
 * Received via: GET /api/schedules/{client_id}
 */
data class Schedule(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("client_id")
    val clientId: String,
    
    @SerializedName("schedule_type")
    val scheduleType: String, // "daily" or "timer"
    
    @SerializedName("start_time")
    val startTime: String?, // e.g., "08:00"
    
    @SerializedName("end_time")
    val endTime: String?, // e.g., "20:00"
    
    @SerializedName("days_of_week")
    val daysOfWeek: String?, // e.g., "0,1,2,3,4"
    
    @SerializedName("duration_seconds")
    val durationSeconds: Int?, // For timer schedules
    
    @SerializedName("enabled")
    val enabled: Int,
    
    @SerializedName("created_at")
    val createdAt: String
)
