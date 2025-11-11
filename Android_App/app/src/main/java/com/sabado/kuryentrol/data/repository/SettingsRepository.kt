package com.sabado.kuryentrol.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repository for managing app settings using DataStore
 * Stores MQTT broker URL and REST API URL
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val BROKER_URL_KEY = stringPreferencesKey("broker_url")
        private val API_URL_KEY = stringPreferencesKey("api_url")
        
        // Default values
        const val DEFAULT_BROKER_URL = "tcp://mqttpi.local:1883"
        const val DEFAULT_API_URL = "http://mqttpi.local:5001/api"
    }
    
    /**
     * Get broker URL as Flow
     */
    val brokerUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[BROKER_URL_KEY] ?: DEFAULT_BROKER_URL
    }
    
    /**
     * Get API URL as Flow
     */
    val apiUrl: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[API_URL_KEY] ?: DEFAULT_API_URL
    }
    
    /**
     * Save broker URL
     */
    suspend fun saveBrokerUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[BROKER_URL_KEY] = url
        }
    }
    
    /**
     * Save API URL
     */
    suspend fun saveApiUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[API_URL_KEY] = url
        }
    }
}
