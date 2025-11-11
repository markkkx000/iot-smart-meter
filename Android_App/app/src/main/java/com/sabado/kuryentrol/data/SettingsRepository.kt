package com.sabado.kuryentrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val mqttBrokerKey = stringPreferencesKey("mqtt_broker_address")
    private val restApiKey = stringPreferencesKey("rest_api_url")

    val mqttBrokerAddress: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[mqttBrokerKey] ?: "tcp://mqttpi.local:1883" // Default value
        }

    val restApiUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[restApiKey] ?: "http://mqttpi.local:5001/api/" // Default value
        }

    suspend fun saveMqttBrokerAddress(address: String) {
        context.dataStore.edit { settings ->
            settings[mqttBrokerKey] = address
        }
    }

    suspend fun saveRestApiUrl(url: String) {
        context.dataStore.edit { settings ->
            settings[restApiKey] = url
        }
    }
}
