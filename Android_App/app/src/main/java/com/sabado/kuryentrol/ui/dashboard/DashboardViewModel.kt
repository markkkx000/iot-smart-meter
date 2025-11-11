package com.sabado.kuryentrol.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabado.kuryentrol.data.DeviceNameRepository
import com.sabado.kuryentrol.data.DeviceRepository
import com.sabado.kuryentrol.data.Result
import com.sabado.kuryentrol.data.SettingsRepository
import com.sabado.kuryentrol.data.model.DeviceStatus
import com.sabado.kuryentrol.data.model.PzemMetrics
import com.sabado.kuryentrol.data.model.RelayState
import com.sabado.kuryentrol.service.MqttManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DeviceUiState(
    val clientId: String,
    val customName: String? = null,
    val status: DeviceStatus = DeviceStatus.OFFLINE,
    val metrics: PzemMetrics? = null,
    val energy: Float? = null,
    val relayState: RelayState = RelayState.OFF
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val mqttManager: MqttManager,
    private val settingsRepository: SettingsRepository,
    private val deviceNameRepository: DeviceNameRepository,
    private val deviceRepository: DeviceRepository // Added DeviceRepository
) : ViewModel() {

    private val _mqttDevices = MutableStateFlow<Map<String, DeviceUiState>>(emptyMap())
    private val _customDeviceNames = deviceNameRepository.getAllDeviceNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val devices: StateFlow<List<DeviceUiState>> = combine(
        _mqttDevices,
        _customDeviceNames
    ) { mqttDevices, customNames ->
        mqttDevices.values.map { mqttDevice ->
            val customName = customNames.firstOrNull { it.clientId == mqttDevice.clientId }?.customName
            mqttDevice.copy(customName = customName)
        }.sortedBy { it.customName ?: it.clientId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStatus = mqttManager.connectionStatus

    private val _networkLatency = MutableStateFlow<Long?>(null)
    val networkLatency: StateFlow<Long?> = _networkLatency.asStateFlow()

    init {
        // Connect to broker on ViewModel initialization
        viewModelScope.launch {
            settingsRepository.mqttBrokerAddress.firstOrNull()?.let { brokerUrl ->
                mqttManager.connect(brokerUrl)
            }
        }

        // Subscribe to topics when connected
        viewModelScope.launch {
            connectionStatus.collect { status ->
                if (status == com.sabado.kuryentrol.service.MqttConnectionStatus.CONNECTED) {
                    subscribeToTopics()
                }
            }
        }

        // Process incoming messages
        viewModelScope.launch {
            mqttManager.incomingMessages.filterNotNull().collect { (topic, message) ->
                processMqttMessage(topic, message.payload.toString(Charsets.UTF_8))
            }
        }

        // Start periodic ping for network latency
        viewModelScope.launch {
            while (true) {
                val result = deviceRepository.pingApi()
                _networkLatency.value = when (result) {
                    is Result.Success -> result.data
                    is Result.Error -> null // Indicate error or no ping
                }
                delay(5000) // Ping every 5 seconds
            }
        }
    }

    private fun subscribeToTopics() {
        mqttManager.subscribe("dev/+/status")
        mqttManager.subscribe("dev/+/pzem/metrics")
        mqttManager.subscribe("dev/+/pzem/energy")
        mqttManager.subscribe("dev/+/relay/state")
    }
    
    private fun processMqttMessage(topic: String, payload: String) {
        val topicParts = topic.split('/')
        if (topicParts.size < 3) return
        val clientId = topicParts[1]

        _mqttDevices.update { currentDevices ->
            val device = currentDevices[clientId] ?: DeviceUiState(clientId = clientId)
            val updatedDevice = when (topicParts[2]) {
                "status" -> device.copy(status = if (payload.equals("Online", true)) DeviceStatus.ONLINE else DeviceStatus.OFFLINE)
                "pzem" -> when (topicParts.getOrNull(3)) {
                    "metrics" -> {
                        // Implement robust JSON parsing
                        val metrics = PzemMetrics(0f,0f,0f) // Replace with actual parsing
                        device.copy(metrics = metrics)
                    }
                    "energy" -> device.copy(energy = payload.toFloatOrNull())
                    else -> device
                }
                "relay" -> if (topicParts.getOrNull(3) == "state") {
                    device.copy(relayState = RelayState.from(payload))
                } else device
                else -> device
            }
            currentDevices + (clientId to updatedDevice)
        }
    }

    fun saveDeviceCustomName(clientId: String, customName: String) {
        viewModelScope.launch {
            deviceNameRepository.saveDeviceName(clientId, customName)
        }
    }

    fun toggleRelay(clientId: String, currentState: RelayState) {
        val command = if (currentState == RelayState.ON) "RELAY_OFF" else "RELAY_ON"
        mqttManager.publish("dev/$clientId/relay/commands", command)
    }

    override fun onCleared() {
        mqttManager.disconnect()
        super.onCleared()
    }
}
