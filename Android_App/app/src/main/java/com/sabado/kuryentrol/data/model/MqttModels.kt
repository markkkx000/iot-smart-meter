package com.sabado.kuryentrol.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents the real-time metrics payload from `dev/<CLIENT_ID>/pzem/metrics`.
 */
data class PzemMetrics(
    val voltage: Float,
    val current: Float,
    val power: Float
)

/**
 * Represents the device status from `dev/<CLIENT_ID>/status`.
 */
enum class DeviceStatus {
    ONLINE,
    OFFLINE
}

/**
 * Represents the relay state from `dev/<CLIENT_ID>/relay/state`.
 */
enum class RelayState {
    ON,
    OFF;

    companion object {
        fun from(value: String): RelayState {
            return if (value == "1") ON else OFF
        }
    }
}
