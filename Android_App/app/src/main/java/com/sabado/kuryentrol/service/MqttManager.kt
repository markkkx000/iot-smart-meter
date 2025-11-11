package com.sabado.kuryentrol.service

import android.content.Context
import android.util.Log
import info.mqtt.android.service.MqttAndroidClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.*

class MqttManager(private val context: Context) {

    private val _connectionStatus = MutableStateFlow(MqttConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<MqttConnectionStatus> = _connectionStatus

    private val _incomingMessages = MutableStateFlow<Pair<String, MqttMessage>?>(null)
    val incomingMessages: StateFlow<Pair<String, MqttMessage>?> = _incomingMessages

    private lateinit var mqttClient: MqttAndroidClient

    fun connect(serverUri: String) {
        if (this::mqttClient.isInitialized && mqttClient.isConnected) {
            Log.d("MqttManager", "Already connected to $serverUri")
            return
        }

        val clientId = MqttClient.generateClientId()
        mqttClient = MqttAndroidClient(context, serverUri, clientId)

        mqttClient.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                _connectionStatus.value = MqttConnectionStatus.CONNECTED
                Log.d("MqttManager", "Connected to: $serverURI")
            }

            override fun connectionLost(cause: Throwable?) {
                _connectionStatus.value = MqttConnectionStatus.DISCONNECTED
                Log.e("MqttManager", "Connection lost: ${cause?.message}")
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                if (topic != null && message != null) {
                    _incomingMessages.value = Pair(topic, message)
                    Log.d("MqttManager", "Message arrived on topic $topic: ${message.payload.toString(Charsets.UTF_8)}")
                }
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                // Not used
            }
        })

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
        }

        try {
            _connectionStatus.value = MqttConnectionStatus.CONNECTING
            mqttClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d("MqttManager", "Connection success!")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    _connectionStatus.value = MqttConnectionStatus.ERROR
                    Log.e("MqttManager", "Connection failure: ${exception?.message}")
                }
            })
        } catch (e: MqttException) {
            _connectionStatus.value = MqttConnectionStatus.ERROR
            Log.e("MqttManager", "Error connecting: ${e.message}")
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        if (this::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                mqttClient.subscribe(topic, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d("MqttManager", "Subscribed to $topic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MqttManager", "Failed to subscribe to $topic: ${exception?.message}")
                    }
                })
            } catch (e: MqttException) {
                Log.e("MqttManager", "Error subscribing to $topic: ${e.message}")
            }
        }
    }

    fun publish(topic: String, message: String, qos: Int = 1) {
        if (this::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                val mqttMessage = MqttMessage()
                mqttMessage.payload = message.toByteArray()
                mqttClient.publish(topic, mqttMessage, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d("MqttManager", "Message published to $topic")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.e("MqttManager", "Failed to publish to $topic: ${exception?.message}")
                    }
                })
            } catch (e: MqttException) {
                Log.e("MqttManager", "Error publishing to $topic: ${e.message}")
            }
        }
    }

    fun disconnect() {
        if (this::mqttClient.isInitialized && mqttClient.isConnected) {
            try {
                mqttClient.disconnect()
                _connectionStatus.value = MqttConnectionStatus.DISCONNECTED
                Log.d("MqttManager", "Disconnected")
            } catch (e: MqttException) {
                Log.e("MqttManager", "Error disconnecting: ${e.message}")
            }
        }
    }
}

enum class MqttConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR
}
