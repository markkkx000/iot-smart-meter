#!/usr/bin/env python3

import paho.mqtt.client as mqtt
import json
import logging
from datetime import datetime

log = logging.getLogger("mqtt-client")

class MQTTSchedulerClient:
    def __init__(self, broker, port):
        self.broker = broker
        self.port = port

        self.client = mqtt.Client(client_id="scheduler-service")

        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message

        # Callback placeholders
        self.on_energy_reading = None
 
    def connect(self):
        """Connect to MQTT broker"""
        log.info(f"Connecting to MQTT broker at {self.broker}:{self.port}")
        self.client.connect(self.broker, self.port, 60)
        self.client.loop_start()

    def disconnect(self):
        """Disconnect from broker"""
        self.client.loop_stop()
        self.client.disconnect()

    def _on_connect(self, client, userdata, flags, rc):
        """Callback when connected"""
        if rc == 0:
            log.info("Connected to MQTT broker")
            # Subscribe to all energy readings using wildcard
            self.client.subscribe("dev/+/pzem/energy")
            log.info("Subscribed to dev/+/pzem/energy")
        else:
            log.error(f"Failed to connect, return code {rc}")

    def _on_message(self, client, userdata, msg):
        """Handle incoming MQTT messages"""
        topic = msg.topic
        payload = msg.payload.decode()

        # Parse energy readings: dev/<CLIENT_ID>/pzem/energy
        if '/pzem/energy' in topic:
            client_id = topic.split('/')[1]  # Extract ESP32-XXXXXXXX

            try:
                energy_kwh = float(payload)
                log.debug(f"Energy reading from {client_id}: {energy_kwh} kWh")

                if self.on_energy_reading:
                    self.on_energy_reading(client_id, energy_kwh)
            except ValueError:
                log.error(f"Invalid energy value: {payload}")

    def subscribe_to_configs(self):
        """Subscribe to configuration topics (future use)"""
        # For future: listen to schedule config commands from Android app
        self.client.subscribe("scheduler/+/schedule/add")
        self.client.subscribe("scheduler/+/threshold/set")

    def publish_relay_command(self, client_id, command):
        """
        Publish relay command to ESP32
        Command: RELAY_ON or RELAY_OFF
        """
        topic = f"dev/{client_id}/relay/commands"
        self.client.publish(topic, command, qos=1)
        log.info(f"Published {command} to {topic}")

    def publish_threshold_alert(self, client_id, consumption, limit):
        """Publish threshold alert"""
        topic = f"dev/{client_id}/threshold/alert"
        alert = {
            "consumption_kwh": round(consumption, 3),
            "limit_kwh": limit,
            "exceeded_at": datetime.now().isoformat()
        }
        self.client.publish(topic, json.dumps(alert), qos=1, retain=True)
        log.info(f"Published threshold alert for {client_id}")
