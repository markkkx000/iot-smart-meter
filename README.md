# iot-smart-meter

## Description
This project consists of three main components:
- ESP32
- Raspberry Pi
- Android application

These components will communicate through [MQTT Protocol](https://mqtt.org/) via WiFi network.

## Components
ESP32
- main microcontroller for the energy measurement node.
- functions as a client in the MQTT network that publishes telemetry data to the MQTT Broker (Raspberry Pi).
- controls relay for remote switching of plugged in appliances.

Raspberry Pi
- hosts the MQTT Broker/Server.
- recieves messages from ESP32s or Android Devices.
- can also publish messages to ESP32s such as control commands from the user end (Android app).

Android application
- GUI for the whole system
- has a dashboard for the collected data presented in graphs or charts.
- has a control panel to set the various settings for the system's behaviour.

## Features
(TO-DO)

## Checklist
- ESP32
  - [x] WiFi and MQTT Broker reconnection
  - [x] Test MQTT
  - [ ] Read and format energy readings from PZEM-004T
  - [ ] Publish energy reading payloads to broker
  - [x] Subscibe to commands topic on MQTT
  - [x] Parse and execute received commands from broker
  - [x] Add esp32 status topics (heartbeat)
  - [ ] Add status indicators (on the OLED display)

- Raspberry Pi
  - [x] Configure MQTT Broker ([Mosquitto](https://github.com/eclipse-mosquitto/mosquitto))
  - [x] Add an easy way to connect the Pi to WiFi (captive portal)
  - [ ] Parse energy reading payloads
  - [ ] Add callbacks for ESP32 status and messages
  - [ ] ...
