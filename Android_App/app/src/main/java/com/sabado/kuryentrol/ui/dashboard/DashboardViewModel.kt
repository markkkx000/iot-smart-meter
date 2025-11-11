package com.sabado.kuryentrol.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.sabado.kuryentrol.data.model.PzemMetrics
import com.sabado.kuryentrol.service.MqttManager
import com.sabado.kuryentrol.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * DashboardViewModel: handles device discovery/status/metrics for dashboard.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val mqttManager: MqttManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // Device state maps (clientId -> ...)
    private val _statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val statuses: StateFlow<Map<String, String>> = _statuses.asStateFlow()

    private val _relays = MutableStateFlow<Map<String, String>>(emptyMap())
    val relays: StateFlow<Map<String, String>> = _relays.asStateFlow()

    private val _metrics = MutableStateFlow<Map<String, PzemMetrics>>(emptyMap())
    val metrics: StateFlow<Map<String, PzemMetrics>> = _metrics.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.brokerUrl.collectLatest { brokerUrl ->
                connectMqtt(brokerUrl)
            }
        }
    }

    private fun connectMqtt(brokerUrl: String) {
        val clientId = "AndroidDashboard-" + System.currentTimeMillis()
        mqttManager.connect(brokerUrl, clientId) { connected ->
            _isConnected.value = connected
            if (connected) {
                subscribeToTopics()
            }
        }
        viewModelScope.launch {
            mqttManager.messageFlow.collect { (topic, payload) ->
                handleIncoming(topic, payload)
            }
        }
    }

    private fun subscribeToTopics() {
        mqttManager.subscribe("dev/+/status")
        mqttManager.subscribe("dev/+/relay/state")
        mqttManager.subscribe("dev/+/pzem/metrics")
    }

    // Parse incoming topic
    private fun handleIncoming(topic: String, payload: ByteArray) {
        val parts = topic.split("/")
        if (parts.size < 3 || parts[0] != "dev") return
        val clientId = parts[1]
        when (parts[2]) {
            "status" -> {
                _statuses.update { map ->
                    map.toMutableMap().apply { put(clientId, String(payload)) }
                }
            }
            "relay" -> if (parts.size >= 4 && parts[3] == "state") {
                _relays.update { map ->
                    map.toMutableMap().apply { put(clientId, String(payload)) }
                }
            }
            "pzem" -> if (parts.size >= 4 && parts[3] == "metrics") {
                val json = String(payload)
                val metricsObj = try { Gson().fromJson(json, PzemMetrics::class.java) } catch (_: Exception) { null }
                if (metricsObj != null) {
                    _metrics.update { map ->
                        map.toMutableMap().apply { put(clientId, metricsObj) }
                    }
                }
            }
        }
    }

    // Toggle relay state
    fun setRelay(clientId: String, on: Boolean) {
        val cmd = if (on) "RELAY_ON" else "RELAY_OFF"
        mqttManager.publishString("dev/$clientId/relay/commands", cmd)
    }
}
