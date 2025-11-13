package com.sabado.kuryentrol.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SettingsData(
    val brokerIp: String = "",
    val brokerPort: String = "",
    val apiIp: String = "",
    val apiPort: String = "",
    val pricePerKwh: Float = 10f
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val Context.dataStore by preferencesDataStore(name = "settings")

    companion object {
        val BROKER_IP = stringPreferencesKey("broker_ip")
        val BROKER_PORT = stringPreferencesKey("broker_port")
        val API_IP = stringPreferencesKey("api_ip")
        val API_PORT = stringPreferencesKey("api_port")
        val PRICE_PER_KWH = floatPreferencesKey("price_per_kwh")
    }

    val settingsFlow: Flow<SettingsData> = context.dataStore.data.map { prefs ->
        SettingsData(
            brokerIp = prefs[BROKER_IP] ?: "",
            brokerPort = prefs[BROKER_PORT] ?: "",
            apiIp = prefs[API_IP] ?: "",
            apiPort = prefs[API_PORT] ?: "",
            pricePerKwh = prefs[PRICE_PER_KWH] ?: 10f
        )
    }

    suspend fun getSettings(): SettingsData {
        return settingsFlow.first()
    }

    suspend fun saveSettings(
        brokerIp: String,
        brokerPort: String,
        apiIp: String,
        apiPort: String,
        pricePerKwh: Float
    ) {
        context.dataStore.edit { prefs ->
            prefs[BROKER_IP] = brokerIp
            prefs[BROKER_PORT] = brokerPort
            prefs[API_IP] = apiIp
            prefs[API_PORT] = apiPort
            prefs[PRICE_PER_KWH] = pricePerKwh
        }
    }
}
