# IoT Smart Meter

A distributed energy monitoring system built with ESP32 microcontrollers, Raspberry Pi, and MQTT for real-time power consumption tracking and remote appliance control.

## Architecture

```
┌─────────────┐       MQTT       ┌──────────────┐       MQTT       ┌─────────────┐
│   ESP32     │ <--------------> │ Raspberry Pi │ <--------------> │   Android   │
│   Nodes     │      (WiFi)      │ MQTT Broker  │      (WiFi)      │     App     │
└─────────────┘                  └──────────────┘                  └─────────────┘
      │                                                                   │
 - PZEM-004T                                                       - Dashboard
 - Relay Control                                                   - Control Panel
```

## Components

### ESP32 Energy Meter Node
- Measures voltage, current, power, and energy consumption using PZEM-004T v3.0 sensor
- Controls relay (GPIO 4) for remote switching of connected appliances
- Publishes telemetry data to MQTT broker at configurable intervals (defaults shown):
  - Real-time metrics (voltage, current, power): every 5 seconds (5000 ms)
  - Cumulative energy readings: every 60 seconds (60000 ms)
  - Heartbeat messages: every 30 seconds (30000 ms)
- Supports remote configuration via MQTT (adjust reporting intervals)
- WiFi provisioning via captive portal (WiFiManager)
- Automatic reconnection with mDNS support for broker discovery
- Factory reset via BOOT button (GPIO 0)

**Topics:**
- `dev/<CLIENT_ID>/pzem/metrics` - Real-time voltage/current/power (JSON)
- `dev/<CLIENT_ID>/pzem/energy` - Cumulative energy consumption (retained)
- `dev/<CLIENT_ID>/relay/state` - Relay status (0/1)
- `dev/<CLIENT_ID>/relay/commands` - Relay control (RELAY_ON/RELAY_OFF)
- `dev/<CLIENT_ID>/pzem/config` - Configuration updates (JSON)
- `dev/<CLIENT_ID>/heartbeat` - Keep-alive messages
- `dev/<CLIENT_ID>/status` - Device online/offline status (retained, with LWT)

### Raspberry Pi MQTT Broker
- Runs Mosquitto MQTT broker for message routing
- Self-healing WiFi configuration with automatic fallback to AP mode
- Captive portal for easy network setup (no keyboard/monitor needed)
- Web interface for WiFi management:
  - Scan and connect to available networks
  - Manage saved network credentials
  - Visual signal strength indicators
  - Automatic password validation with retry

**WiFi Fallback Mechanism:**
- On boot, checks for network connectivity (15-second timeout)
- If no connection found, automatically creates AP hotspot (`Pi_AP`)
- Launches captive portal on `192.168.4.1` for configuration
- Supports Android, iOS, and Windows captive portal detection
- After successful connection, reboots and connects to configured network

### Raspberry Pi Smart Meter Service (new)
A new smart_meter component is included under `Raspberry_Pi/home/sabado/smart_meter` (added in recent commits). This component runs on the Raspberry Pi and provides the following features:

- Scheduling: configurable measurement/processing schedules and automated tasks
- Thresholds & Alerts: user-configurable thresholds for power/energy values and triggers for alerts or relay actions
- REST API: simple HTTP API for querying recent measurements, status, and for issuing control commands
- SQLite database: stores metrics and events locally in an SQLite database for historical data and offline operation
- Systemd services: service unit files are provided under `Raspberry_Pi/etc/systemd/system/` to run the smart_meter components (API, scheduler, and worker services). Copy these service files to `/etc/systemd/system/` and enable them to run at boot.

Suggested deployment steps (on the Pi):

1. Copy smart_meter application files:
```bash
sudo cp -r Raspberry_Pi/home/sabado/smart_meter /home/sabado/
sudo chown -R sabado:sabado /home/sabado/smart_meter
```

2. Install Python dependencies (if the component is Python-based):
```bash
cd /home/sabado/smart_meter
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

3. Install systemd service files and enable services:
```bash
sudo cp Raspberry_Pi/etc/systemd/system/smart_meter*.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now smart_meter.service
# enable any additional smart_meter-* services provided
```

4. Check service status and logs:
```bash
sudo systemctl status smart_meter.service
sudo journalctl -u smart_meter.service -f
```

Note: adjust service names above to match the exact unit filenames present in `Raspberry_Pi/etc/systemd/system/`.

### Android Application
*(In development)*
- Real-time dashboard with energy consumption graphs
- Remote appliance control via relay switching
- Historical data visualization
- System configuration and settings management

## Hardware Requirements

### ESP32 Node
- ESP32 development board
- PZEM-004T v3.0/v4.0 energy meter module
- Relay module (active-LOW, connected to GPIO 4)
- 5V AC-DC power supply

**Wiring:**
```
PZEM-004T      ESP32
---------      -----
TX       -->   GPIO 16 (RX)
RX       <--   GPIO 17 (TX)
VCC      -->   5V
GND      -->   GND

Relay          ESP32
-----          -----
IN       <--   GPIO 4
VCC      -->   5V
GND      -->   GND
```

### Raspberry Pi
- Raspberry Pi (any model with WiFi)
- MicroSD card (8GB+)
- Power supply

## Software Setup

### ESP32

1. Install required Arduino libraries:
   - WiFiManager
   - PubSubClient
   - PZEM-004Tv30
   - ArduinoJson
   - ESPmDNS

2. Upload `ESP32/sketch_sep4a/sketch_sep4a.ino`

3. On first boot, connect to `ESP32_AP` WiFi network
   - Configure WiFi credentials
   - Set MQTT broker address (use `mqttpi.local` for mDNS or Pi's IP)
   - Set MQTT port (default: 1883)

4. Device will connect to WiFi and start publishing data

### Raspberry Pi

1. Install Raspberry Pi OS (Lite or Desktop)

2. Install required packages:
```bash
sudo apt update
sudo apt install mosquitto mosquitto-clients python3-flask network-manager dnsmasq sqlite3
```

3. Copy system files:
```bash
# Copy systemd services
sudo cp Raspberry_Pi/etc/systemd/system/*.service /etc/systemd/system/

# Copy WiFi fallback script
sudo cp Raspberry_Pi/usr/local/bin/wifi-fallback.sh /usr/local/bin/
sudo chmod +x /usr/local/bin/wifi-fallback.sh

# Copy WiFi fallback configuration
sudo cp Raspberry_Pi/etc/default/wifi-fallback /etc/default/

# Copy dnsmasq captive portal config
sudo cp Raspberry_Pi/etc/NetworkManager/dnsmasq-shared.d/captive.conf /etc/NetworkManager/dnsmasq-shared.d/

# Copy captive portal application
sudo cp -r Raspberry_Pi/home/sabado/captive_portal /home/$USER/
```

4. Configure mDNS hostname:
```bash
# Set hostname to mqttpi
sudo hostnamectl set-hostname mqttpi

# Install avahi for mDNS
sudo apt install avahi-daemon
sudo systemctl enable avahi-daemon
```

5. Enable services:
```bash
sudo systemctl enable mosquitto
sudo systemctl enable wifi-fallback
sudo systemctl enable wifi-powersaver-off
sudo systemctl enable captive-portal
sudo systemctl start mosquitto
```

6. Reboot:
```bash
sudo reboot
```

7. If Pi doesn't connect to a saved network, it will automatically create `Pi_AP` hotspot:
   - SSID: `Pi_AP`
   - Password: `12345678`
   - Connect to this network and captive portal should open automatically
   - Select your WiFi network and enter password
   - Pi will reboot and connect to your network

## MQTT Topic Structure

```
dev/
└── <CLIENT_ID>/
    ├── status             # "Online"/"Offline" (retained, LWT)
    ├── heartbeat          # "Alive" (periodic keep-alive)
    ├── relay/
    │   ├── state          # Current relay status: "0" or "1" (retained)
    │   └── commands       # Control commands: RELAY_ON/RELAY_OFF
    └── pzem/
        ├── metrics        # {"voltage":220.5,"current":1.2,"power":264.6}
        ├── energy         # Cumulative kWh (retained)
        └── config         # {"metrics":5000,"energy":60000}
```

**Wildcard Subscriptions for Multi-Device Monitoring:**

To monitor all ESP32 devices from a single subscription (e.g., in the Android app):

- `dev/+/status` - Get status updates from all devices
- `dev/+/heartbeat` - Monitor heartbeats from all devices
- `dev/+/pzem/metrics` - Receive metrics from all devices
- `dev/+/pzem/energy` - Track energy from all devices
- `dev/+/relay/state` - Monitor all relay states

Each device maintains its own independent topics, ensuring status updates don't overwrite each other.

## Configuration

### Adjust ESP32 Reporting Intervals

Publish JSON to `dev/<CLIENT_ID>/pzem/config`:
```json
{
  "metrics": 5000,   // milliseconds between metrics updates (default: 5000)
  "energy": 60000    // milliseconds between energy updates (default: 60000)
}
```

### Customize WiFi Fallback

Edit `/etc/default/wifi-fallback`:
```bash
HOTSPOT_SSID="Pi_AP"        # AP name
HOTSPOT_PASS="12345678"     # AP password
CHECK_TIMEOUT=15            # Seconds to wait for connection
```

## Development Status

### Completed
- ✅ ESP32 WiFi and MQTT reconnection with mDNS
- ✅ PZEM-004T energy monitoring (voltage, current, power, energy)
- ✅ MQTT telemetry publishing
- ✅ Relay control via MQTT commands
- ✅ Device heartbeat and status reporting
- ✅ Remote configuration (adjustable intervals)
- ✅ Raspberry Pi MQTT broker setup
- ✅ WiFi fallback with captive portal
- ✅ Web-based WiFi management interface
- ✅ Automatic hotspot recovery on failed connections
- ✅ OLED status display for ESP32
- ✅ Raspberry Pi smart_meter service (scheduling, thresholds, REST API, SQLite DB)

### In Progress
- 🔄 Raspberry Pi data parsing and storage (integration with smart_meter)
- 🔄 ESP32 status callbacks on Pi
- 🔄 Android application development

### Planned
- 📋 Power-off handling (UPS integration or safe shutdown)
- 📋 Historical data storage (InfluxDB/SQLite) — expand or migrate from local SQLite
- 📋 Web dashboard for Raspberry Pi
- 📋 Multi-node support and aggregation
- 📋 Energy cost calculations
- 📋 Usage alerts and notifications

## Troubleshooting

**ESP32 won't connect to WiFi:**
- Press BOOT button to reset WiFi credentials
- Connect to `ESP32_AP` and reconfigure

**Can't access captive portal on Pi:**
- Check if `Pi_AP` network is visible
- Manually navigate to `http://192.168.4.1` in browser
- Verify captive portal service: `sudo systemctl status captive-portal`

**ESP32 can't find MQTT broker:**
- Verify Pi hostname: `avahi-browse -a | grep mqttpi`
- Try using Pi's IP address instead of `mqttpi.local`
- Check Mosquitto: `sudo systemctl status mosquitto`

**Hotspot doesn't restart after wrong password:**
- Check logs: `sudo journalctl -u captive-portal -n 50`
- Manually restart: `sudo nmcli connection up Hotspot`

## Changelog (recent README updates)
- 6cda13c — Update README.md (metadata/formatting)
- README updated to document the newly added Raspberry_Pi/home/sabado/smart_meter component (scheduling, thresholds, REST API, SQLite DB) and associated systemd services.

## License

This project is open source and available for educational and personal use.

## Contributing

This is a personal learning project, but suggestions and improvements are welcome via issues or pull requests.