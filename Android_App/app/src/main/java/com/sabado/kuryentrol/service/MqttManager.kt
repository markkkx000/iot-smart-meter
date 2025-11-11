package com.sabado.kuryentrol.service

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Singleton class for managing MQTT connections and message publish/subscribe using Paho
 */
@Singleton
class MqttManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mqttClient: MqttAndroidClient? = null
    private var connectToken: IMqttToken? = null

    // Channel for incoming messages (used to expose as a flow for the ViewModel/UI)
    private val messageChannel = Channel<Pair<String, ByteArray>>(capacity = Channel.BUFFERED)
    val messageFlow = messageChannel.receiveAsFlow()

    // --- Connection ---
    fun connect(brokerUrl: String, clientId: String = MqttClient.generateClientId(), onConnect: (Boolean) -> Unit = {}) {
        if (mqttClient?.isConnected == true) {
            onConnect(true)
            return
        }
        mqttClient = MqttAndroidClient(context, brokerUrl, clientId).apply {
            setCallback(object : MqttCallback {
                override fun messageArrived(topic: String, message: MqttMessage) {
                    messageChannel.trySend(topic to message.payload)
                }
                override fun connectionLost(cause: Throwable?) {
                    Log.w("MqttManager", "MQTT connection lost: ${cause?.localizedMessage}")
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {
                    // Optional, handle delivery confirmation if needed
                }
            })
        }
        try {
            connectToken = mqttClient?.connect(MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
            }, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    onConnect(true)
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    onConnect(false)
                    Log.e("MqttManager", "MQTT connection failed", exception)
                }
            })
        } catch (e: Exception) {
            onConnect(false)
            Log.e("MqttManager", "MQTT connect error", e)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (_: Exception) {}
    }

    // --- Subscribe ---
    fun subscribe(topic: String, qos: Int = 1, onComplete: (Boolean) -> Unit = {}) {
        try {
            mqttClient?.subscribe(topic, qos, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    onComplete(true)
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    onComplete(false)
                    Log.e("MqttManager", "MQTT subscribe fail $topic", exception)
                }
            })
        } catch (e: Exception) {
            onComplete(false)
            Log.e("MqttManager", "MQTT subscribe error", e)
        }
    }

    // --- Publish ---
    fun publish(topic: String, payload: ByteArray, qos: Int = 1, retained: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        try {
            val msg = MqttMessage(payload)
            msg.qos = qos
            msg.isRetained = retained
            mqttClient?.publish(topic, msg, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    onComplete(true)
                }
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    onComplete(false)
                    Log.e("MqttManager", "MQTT publish failed $topic", exception)
                }
            })
        } catch (e: Exception) {
            onComplete(false)
            Log.e("MqttManager", "MQTT publish error $topic", e)
        }
    }

    // Utility for publishing String
    fun publishString(topic: String, payload: String, qos: Int = 1, retained: Boolean = false, onComplete: (Boolean) -> Unit = {}) {
        publish(topic, payload.toByteArray(Charsets.UTF_8), qos, retained, onComplete)
    }
}
