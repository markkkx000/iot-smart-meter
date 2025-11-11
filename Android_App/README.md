# IoT Smart Meter

A distributed energy monitoring and automation system built with ESP32 microcontrollers, Raspberry Pi, and MQTT for real-time power consumption tracking, remote appliance control, and automated scheduling.

## Table of Contents

- [Architecture](#architecture)
- [Components](#components)
  - [ESP32 Energy Meter Node](#esp32-energy-meter-node)
  - [Raspberry Pi MQTT Broker & Automation Hub](#raspberry-pi-mqtt-broker--automation-hub)
  - [Android Application](#android-application)
- [Hardware Requirements](#hardware-requirements)
- [Software Setup](#software-setup)
  - [ESP32 Setup](#esp32)
  - [Raspberry Pi Setup](#raspberry-pi)
- [MQTT Topic Structure](#mqtt-topic-structure)
- [REST API Documentation](#rest-api-documentation)
- [Configuration](#configuration)
- [Database Schema](#database-schema)
- [Design Decisions](#design-decisions)
- [Development Status](#development-status)
- [Troubleshooting](#troubleshooting)
- [License](#license)
- [Contributing](#contributing)
- [Acknowledgments](#acknowledgments)

## Architecture

```
┌─────────────┐       MQTT       ┌──────────────┐       MQTT       ┌─────────────┐
│   ESP32     │ <--------------> │ Raspberry Pi │ <--------------> │   Android   │
│   Nodes     │      (WiFi)      │ MQTT Broker  │      (WiFi)      │     App     │
└─────────────┘                  └──────────────┘                  └─────────────┘
      │                                 │                                 │
 - PZEM-004T                      - Mosquitto MQTT              - Real-time Dashboard
 - Relay Control                  - Scheduler Service           - Schedule Management
 - OLED Display                   - REST API                    - Threshold Alerts
                                  - Energy Database             - Device Control
```

## Components

### ESP32 Energy Meter Node
- Measures voltage, current, power, and energy consumption using PZEM-004T v3.0 sensor
- Controls relay (GPIO 4) for remote switching of connected appliances
- 128x64 OLED display (SH1106) showing:
  - Device ID and connection status
  - WiFi and MQTT connectivity
  - Real-time power consumption
  - Relay state (ON/OFF)
  - IP address when connected
- Publishes telemetry data to MQTT broker at configurable intervals:
  - Real-time metrics (voltage, current, power): every 3 seconds (default)
  - Cumulative energy readings: every 60 seconds (default)
  - Heartbeat messages: every 30 seconds
- Supports remote configuration via MQTT (adjust reporting intervals)
- WiFi provisioning via captive portal (WiFiManager)
- Automatic reconnection with mDNS support for broker discovery
- Factory reset via BOOT button (GPIO 0)
- WiFi event handling for connection state tracking

**MQTT Topics (Published):**
- `dev/<CLIENT_ID>/pzem/metrics` - Real-time voltage/current/power (JSON)
- `dev/<CLIENT_ID>/pzem/energy` - Cumulative energy consumption (kWh)
- `dev/<CLIENT_ID>/relay/state` - Relay status (0/1, retained)
- `dev/<CLIENT_ID>/status` - Device online/offline status (retained, with LWT)
- `dev/<CLIENT_ID>/heartbeat` - Keep-alive messages

**MQTT Topics (Subscribed):**
- `dev/<CLIENT_ID>/relay/commands` - Relay control (RELAY_ON/RELAY_OFF)
- `dev/<CLIENT_ID>/relay/state` - Relay state synchronization
- `dev/<CLIENT_ID>/pzem/config` - Configuration updates (JSON)

### Raspberry Pi MQTT Broker & Automation Hub

#### 1. MQTT Broker (Mosquitto)
- Central message router for all ESP32 devices and clients
- Handles device registration via retained status messages
- Supports LWT (Last Will and Testament) for device disconnection detection

#### 2. WiFi Management System
- Self-healing WiFi configuration with automatic fallback to AP mode
- Captive portal for easy network setup (no keyboard/monitor needed)
- Web interface for WiFi management:
  - Scan and connect to available networks
  - Manage saved network credentials
  - Visual signal strength indicators
  - Automatic password validation with retry
  - Auto-restart hotspot on failed connection attempts
  - Support for both secured and open networks

**WiFi Fallback Mechanism:**
- On boot, checks for network connectivity (15-second timeout)
- If no connection found, automatically creates AP hotspot (`Pi_AP`)
- Launches captive portal on `192.168.4.1` for configuration
- Supports Android, iOS, and Windows captive portal detection
- After successful connection, reboots and connects to configured network
- Ethernet connection bypasses WiFi fallback

#### 3. Smart Meter Scheduler Service
- Automated relay control based on schedules and energy thresholds
- Monitors energy consumption from all ESP32 devices
- Stores historical data in SQLite database
- APScheduler-based job execution with background scheduling

**Features:**
- **Daily Schedules**: Turn relays ON/OFF at specific times (recurring)
  - Supports day-of-week filtering (e.g., weekdays only: "0,1,2,3,4")
  - APScheduler format: 0=Monday, 6=Sunday
  - If days_of_week not specified, runs every day
  - Separate ON and OFF cron jobs
- **Timer Schedules**: One-time countdown timers for temporary operations
  - Starts immediately upon creation
  - Automatically turns relay OFF after specified duration
  - Self-deletes after execution using DateTrigger
- **Energy Thresholds**: Auto-shutoff when consumption exceeds limits
  - Daily, weekly, or monthly reset periods
  - Automatic relay disconnect on threshold breach
  - Alert notifications via MQTT (retained messages)
  - Must be manually re-enabled after triggering (prevents repeated shutoffs)
  - Threshold monitoring runs every 60 seconds
- **Schedule Management**: Create, update, and delete schedules via REST API
  - Partial updates supported (modify only specific fields)
  - Time format validation (HH:MM)
  - Automatic scheduler service restart on changes (via systemctl)

#### 4. REST API Service
- Flask-based API for Android app integration
- Manages schedules, thresholds, and energy data
- CORS-enabled for cross-origin requests
- Runs on port 5001
- Includes health check endpoint

### Android Application
*(In development)*
- Real-time dashboard with energy consumption graphs
- Device discovery and status monitoring (via `dev/+/status` wildcard)
- Schedule management (daily schedules, timers)
- Energy threshold configuration
- Remote appliance control via relay switching
- Historical data visualization
- Push notifications for threshold alerts

## Hardware Requirements

### ESP32 Node
- ESP32 development board
- PZEM-004T v3.0 energy meter module
- Relay module (active-LOW, connected to GPIO 4)
- SH1106 128x64 OLED display (I2C, address 0x3C)
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

OLED (I2C)     ESP32
----------     -----
SDA      <->   GPIO 22
SCL      <->   GPIO 23
VCC      -->   3.3V
GND      -->   GND

BOOT Button    GPIO 0 (built-in, for factory reset)
```

### Raspberry Pi
- Raspberry Pi 3/4 (any model with WiFi)
- MicroSD card (16GB+ recommended for data storage)
- 5V power supply (2.5A minimum)

## Software Setup

### ESP32

1. **Install Arduino IDE and required libraries:**
   - WiFiManager by tzapu
   - PubSubClient by Nick O'Leary
   - PZEM-004Tv30 by Jakub Mandula
   - ArduinoJson by Benoit Blanchon
   - U8g2 by oliver
   - ESP32 board support (via Boards Manager)

2. **Configure and upload sketch:**
   - Open `ESP32/sketch_sep4a/sketch_sep4a.ino`
   - Verify GPIO pins match your hardware:
     ```cpp
     #define RESET_PIN 0       // BOOT button
     #define RELAY_PIN 4
     #define PZEM_RX 16
     #define PZEM_TX 17
     #define OLED_SDA 22
     #define OLED_SCL 23
     ```
   - Upload to ESP32

3. **Initial WiFi Configuration:**
   - On first boot, ESP32 creates `ESP32_AP` access point
   - Connect to `ESP32_AP` with your phone/laptop
   - Captive portal opens automatically (or navigate to `192.168.4.1`)
   - Configure WiFi credentials
   - Set MQTT broker address: `mqttpi.local` (or Pi's IP address)
   - Set MQTT port: `1883`

4. **Verify Operation:**
   - OLED should show "Connected" status with IP address
   - Device publishes to MQTT broker
   - Check serial monitor at 115200 baud for logs

**Factory Reset:**
- Press and hold BOOT button (GPIO 0) to reset WiFi credentials
- Device will restart and create `ESP32_AP` again

### Raspberry Pi

#### 1. Base System Setup

Install Raspberry Pi OS (Lite or Desktop) and boot up.

#### 2. Install System Packages

```bash
sudo apt update
sudo apt install -y \
    mosquitto mosquitto-clients \
    python3-flask python3-flask-cors \
    python3-paho-mqtt python3-apscheduler \
    network-manager dnsmasq avahi-daemon \
    sqlite3
```

**Package Breakdown:**
- `mosquitto`, `mosquitto-clients` - MQTT broker and CLI tools
- `python3-flask`, `python3-flask-cors` - Web framework for REST API and captive portal
- `python3-paho-mqtt` - MQTT client library for Python
- `python3-apscheduler` - Job scheduling library
- `network-manager`, `dnsmasq` - Network management and DNS/DHCP for captive portal
- `avahi-daemon` - mDNS (Bonjour) for `mqttpi.local` hostname
- `sqlite3` - Database for energy readings and schedules

#### 3. Configure mDNS Hostname

```bash
# Set hostname to mqttpi
sudo hostnamectl set-hostname mqttpi

# Enable and start Avahi
sudo systemctl enable avahi-daemon
sudo systemctl start avahi-daemon

# Verify mDNS is working
avahi-browse -a
```

Your Pi will now be accessible at `mqttpi.local` on the network.

#### 4. Copy Project Files

```bash
# Create application directory
mkdir -p /home/$USER/smart_meter
mkdir -p /home/$USER/captive_portal

# Copy systemd service files
sudo cp Raspberry_Pi/etc/systemd/system/*.service /etc/systemd/system/

# Copy WiFi fallback script
sudo cp Raspberry_Pi/usr/local/bin/wifi-fallback.sh /usr/local/bin/
sudo chmod +x /usr/local/bin/wifi-fallback.sh

# Copy WiFi fallback configuration
sudo cp Raspberry_Pi/etc/default/wifi-fallback /etc/default/

# Copy dnsmasq captive portal config
sudo mkdir -p /etc/NetworkManager/dnsmasq-shared.d
sudo cp Raspberry_Pi/etc/NetworkManager/dnsmasq-shared.d/captive.conf \
    /etc/NetworkManager/dnsmasq-shared.d/

# Copy captive portal application
cp -r Raspberry_Pi/home/sabado/captive_portal/* /home/$USER/captive_portal/

# Copy smart meter scheduler and API
cp -r Raspberry_Pi/home/sabado/smart_meter/* /home/$USER/smart_meter/
```

**Note:** Update service files to use your username instead of `sabado`:
```bash
sudo sed -i "s|/home/sabado|/home/$USER|g" /etc/systemd/system/captive-portal.service
sudo sed -i "s|/home/sabado|/home/$USER|g" /etc/systemd/system/smart-meter-*.service
sudo sed -i "s|User=sabado|User=$USER|g" /etc/systemd/system/smart-meter-*.service
```

#### 5. Configure Passwordless Sudo for Service Management

The REST API needs to restart the scheduler service when schedules are modified. Configure passwordless sudo access:

```bash
# Allow user to manage smart-meter-scheduler service without password
echo "$USER ALL=(ALL) NOPASSWD: /bin/systemctl restart smart-meter-scheduler.service" | sudo tee /etc/sudoers.d/smart-meter-scheduler
echo "$USER ALL=(ALL) NOPASSWD: /bin/systemctl start smart-meter-scheduler.service" | sudo tee -a /etc/sudoers.d/smart-meter-scheduler
echo "$USER ALL=(ALL) NOPASSWD: /bin/systemctl stop smart-meter-scheduler.service" | sudo tee -a /etc/sudoers.d/smart-meter-scheduler

# Set proper permissions (sudoers files must be 0440)
sudo chmod 0440 /etc/sudoers.d/smart-meter-scheduler

# Verify the configuration
sudo visudo -c
```

**Important:** This allows the API service to restart the scheduler without prompting for a password, enabling automatic schedule updates.

#### 6. Enable and Start Services

```bash
# Reload systemd to recognize new services
sudo systemctl daemon-reload

# Enable services to start on boot
sudo systemctl enable mosquitto
sudo systemctl enable wifi-fallback
sudo systemctl enable wifi-powersaver-off
sudo systemctl enable captive-portal
sudo systemctl enable smart-meter-scheduler
sudo systemctl enable smart-meter-api

# Start services
sudo systemctl start mosquitto
sudo systemctl start smart-meter-scheduler
sudo systemctl start smart-meter-api

# Verify services are running
sudo systemctl status mosquitto
sudo systemctl status smart-meter-scheduler
sudo systemctl status smart-meter-api
```

**Note:** `wifi-fallback` and `captive-portal` will start automatically only when needed (no WiFi connection at boot).

#### 7. Initial WiFi Connection

If Pi doesn't connect to a saved network on reboot, it will automatically:
1. Create `Pi_AP` hotspot (SSID: `Pi_AP`, Password: `12345678`)
2. Launch captive portal at `192.168.4.1`
3. Connect to `Pi_AP` and configure WiFi through the web interface
4. Pi reboots and connects to your network

#### 8. Verify Installation

```bash
# Check if MQTT broker is running
mosquitto_sub -h localhost -t '#' -v

# Test REST API
curl http://localhost:5001/api/health

# View scheduler logs
sudo journalctl -u smart-meter-scheduler -f

# Test mDNS resolution
ping mqttpi.local
```

## MQTT Topic Structure

```
dev/
└── <CLIENT_ID>/                    # e.g., ESP32-fa641d44
    ├── status                      # "Online"/"Offline" (retained, LWT)
    ├── heartbeat                   # "Alive" (every 30s)
    ├── relay/
    │   ├── state                   # "0" or "1" (retained)
    │   └── commands                # RELAY_ON / RELAY_OFF
    ├── pzem/
    │   ├── metrics                 # {"voltage":220.5,"current":1.2,"power":264.6}
    │   ├── energy                  # Cumulative kWh (retained)
    │   └── config                  # {"metrics":5000,"energy":120000}
    └── threshold/
        └── alert                   # Threshold exceeded notification (retained)
```

### Wildcard Subscriptions for Multi-Device Monitoring

To monitor all ESP32 devices from a single subscription (Android app):

- `dev/+/status` - Get status updates from all devices
- `dev/+/heartbeat` - Monitor heartbeats from all devices
- `dev/+/pzem/metrics` - Receive metrics from all devices
- `dev/+/pzem/energy` - Track energy from all devices
- `dev/+/relay/state` - Monitor all relay states

Each device maintains its own independent topics, ensuring status updates don't overwrite each other.

## REST API Documentation

The REST API runs on port `5001` and provides programmatic access to the scheduler system.

**Base URL:** `http://mqttpi.local:5001/api`

### Health Check

**GET** `/api/health`

Check if API is running.

**Response:**
```json
{
  "success": true,
  "service": "Smart Meter Scheduler API",
  "version": "1.0",
  "timestamp": "2025-10-30T14:35:00"
}
```

---

### Device Management

#### Get All Devices

**GET** `/api/devices`

Returns list of all known ESP32 devices based on energy readings.

**Response:**
```json
{
  "success": true,
  "devices": [
    {
      "client_id": "ESP32-fa641d44",
      "last_seen": "2025-10-30 14:30:00",
      "current_energy_kwh": 123.45
    }
  ]
}
```

---

### Schedule Management

#### Get All Schedules

**GET** `/api/schedules`

Returns all schedules across all devices.

**Response:**
```json
{
  "success": true,
  "schedules": [
    {
      "id": 1,
      "client_id": "ESP32-fa641d44",
      "schedule_type": "daily",
      "start_time": "08:00",
      "end_time": "20:00",
      "days_of_week": "0,1,2,3,4",
      "enabled": 1,
      "created_at": "2025-10-30 10:00:00"
    }
  ]
}
```

---

#### Get Device Schedules

**GET** `/api/schedules/<client_id>`

Returns schedules for a specific device.

**Response:**
```json
{
  "success": true,
  "client_id": "ESP32-fa641d44",
  "schedules": [...]
}
```

---

#### Create Schedule

**POST** `/api/schedules`

Create a new schedule.

**Request Body (Daily Schedule):**
```json
{
  "client_id": "ESP32-fa641d44",
  "schedule_type": "daily",
  "start_time": "08:00",
  "end_time": "20:00",
  "days_of_week": "0,1,2,3,4"  // Optional: 0=Mon, 6=Sun
}
```

**Days of Week Format:**
- Uses APScheduler convention: 0=Monday, 6=Sunday
- Examples:
  - Monday-Friday: `"0,1,2,3,4"`
  - Weekends only: `"5,6"`
  - Tuesday/Thursday: `"1,3"`
  - All days: Omit field or set to `null`

**Request Body (Timer Schedule):**
```json
{
  "client_id": "ESP32-fa641d44",
  "schedule_type": "timer",
  "duration_seconds": 3600
}
```

**Response:**
```json
{
  "success": true,
  "schedule_id": 5,
  "scheduler_restarted": true,
  "message": "Schedule created and scheduler restarted successfully!"
}
```

---

#### Update Schedule

**PUT** `/api/schedules/<schedule_id>`

Update an existing schedule. All fields are optional - only provided fields will be updated.

**Request Body:**
```json
{
  "start_time": "09:00",
  "end_time": "21:00",
  "duration_seconds": 7200,
  "days_of_week": "5,6"
}
```

**Response:**
```json
{
  "success": true,
  "schedule_id": 5,
  "scheduler_restarted": true,
  "message": "Schedule updated and scheduler restarted successfully!"
}
```

**Notes:**
- Time format must be HH:MM (24-hour format)
- For daily schedules: Can update start_time, end_time, and days_of_week
- For timer schedules: Can update duration_seconds
- Scheduler service automatically restarts via systemctl to apply changes

---

#### Delete Schedule

**DELETE** `/api/schedules/<schedule_id>`

Delete a schedule by ID.

**Response:**
```json
{
  "success": true,
  "scheduler_restarted": true,
  "message": "Schedule deleted and scheduler restarted successfully!"
}
```

---

### Energy Threshold Management

#### Get Device Threshold

**GET** `/api/thresholds/<client_id>`

Get energy threshold for a specific device.

**Response:**
```json
{
  "success": true,
  "threshold": {
    "id": 1,
    "client_id": "ESP32-fa641d44",
    "limit_kwh": 5.0,
    "reset_period": "daily",
    "enabled": 1,
    "last_reset": "2025-10-30 00:00:00",
    "created_at": "2025-10-30 10:00:00"
  }
}
```

---

#### Set/Update Threshold

**PUT** `/api/thresholds/<client_id>`

Set or update energy threshold for a device.

**Request Body:**
```json
{
  "limit_kwh": 5.0,
  "reset_period": "daily"  // "daily", "weekly", or "monthly"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Threshold set successfully"
}
```

**Behavior:**
- When consumption exceeds `limit_kwh`, relay automatically turns OFF
- Threshold is disabled after triggering to prevent repeated shutoffs
- **Must be manually re-enabled** by deleting and recreating the threshold
- Alert published to `dev/<CLIENT_ID>/threshold/alert` (retained)
- This design prevents the relay from repeatedly cycling if consumption remains high
- Reset periods determine when consumption calculation starts:
  - `daily` - Resets at midnight (00:00)
  - `weekly` - Resets every Monday at midnight
  - `monthly` - Resets on the 1st of each month at midnight

---

#### Delete Threshold

**DELETE** `/api/thresholds/<client_id>`

Remove energy threshold for a device.

**Response:**
```json
{
  "success": true,
  "message": "Threshold deleted successfully"
}
```

---

### Energy Data Queries

#### Get Energy Readings

**GET** `/api/energy/<client_id>?limit=100&period=day`

Get energy readings or consumption for a device.

**Query Parameters:**
- `limit` (optional): Number of readings to return (default: 100)
- `period` (optional): Aggregate by period - `"day"`, `"week"`, or `"month"`

**Response (Recent Readings):**
```json
{
  "success": true,
  "client_id": "ESP32-fa641d44",
  "readings": [
    {
      "energy_kwh": 123.45,
      "timestamp": "2025-10-30 14:30:00"
    }
  ]
}
```

**Response (Aggregated by Period):**
```json
{
  "success": true,
  "client_id": "ESP32-fa641d44",
  "period": "day",
  "consumption_kwh": 2.345
}
```

---

### Error Responses

All endpoints return errors in this format:

```json
{
  "success": false,
  "error": "Error message describing what went wrong"
}
```

**Common HTTP Status Codes:**
- `200 OK` - Request successful
- `201 Created` - Resource created successfully
- `400 Bad Request` - Invalid request parameters
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server error

---

## Configuration

### Adjust ESP32 Reporting Intervals

Publish JSON to `dev/<CLIENT_ID>/pzem/config`:
```json
{
  "metrics": 5000,   // milliseconds between metrics updates (default: 3000)
  "energy": 120000   // milliseconds between energy updates (default: 60000)
}
```

Example using mosquitto_pub:
```bash
mosquitto_pub -h mqttpi.local -t "dev/ESP32-fa641d44/pzem/config" \
  -m '{"metrics":5000,"energy":120000}'
```

Configuration is saved to ESP32 Preferences and persists across reboots.

### Customize WiFi Fallback

Edit `/etc/default/wifi-fallback`:
```bash
HOTSPOT_NAME="Hotspot"        # NetworkManager connection name
HOTSPOT_SSID="Pi_AP"          # AP SSID
HOTSPOT_PASS="12345678"       # AP password
WIFI_INTERFACE="wlan0"        # WiFi interface
ETH_INTERFACE="eth0"          # Ethernet interface
CHECK_TIMEOUT=15              # Seconds to wait for WiFi connection
```

After editing:
```bash
sudo systemctl restart wifi-fallback
```

### Mosquitto MQTT Broker Configuration

Default configuration is located at `/etc/mosquitto/mosquitto.conf`.

To enable authentication:
```bash
# Create password file
sudo mosquitto_passwd -c /etc/mosquitto/passwd username

# Edit /etc/mosquitto/mosquitto.conf
allow_anonymous false
password_file /etc/mosquitto/passwd

# Restart broker
sudo systemctl restart mosquitto
```

## Database Schema

The scheduler uses SQLite database at `/home/<user>/smart_meter/scheduler.db`.

### Tables

**schedules**
- `id` - Schedule ID (primary key)
- `client_id` - ESP32 device ID
- `schedule_type` - "daily" or "timer"
- `start_time` - Daily schedule start time (HH:MM format, 24-hour)
- `end_time` - Daily schedule end time (HH:MM format, 24-hour)
- `duration_seconds` - Timer duration (only for timer type)
- `days_of_week` - Comma-separated days (0=Mon, 6=Sun, e.g., "0,1,2,3,4")
- `enabled` - Active status (1/0)
- `created_at` - Creation timestamp

**thresholds**
- `id` - Threshold ID (primary key)
- `client_id` - ESP32 device ID (unique)
- `limit_kwh` - Consumption limit
- `reset_period` - "daily", "weekly", or "monthly"
- `enabled` - Active status (1/0)
- `last_reset` - Last reset timestamp
- `created_at` - Creation timestamp

**energy_readings**
- `id` - Reading ID (primary key)
- `client_id` - ESP32 device ID
- `energy_kwh` - Cumulative energy reading
- `timestamp` - Reading timestamp

**schedule_log**
- `id` - Log entry ID
- `schedule_id` - Related schedule
- `action` - "ON" or "OFF"
- `executed_at` - Execution timestamp

### Query Examples

```bash
# View all schedules
sqlite3 /home/$USER/smart_meter/scheduler.db "SELECT * FROM schedules;"

# View energy readings
sqlite3 /home/$USER/smart_meter/scheduler.db \
  "SELECT * FROM energy_readings WHERE client_id='ESP32-fa641d44' ORDER BY timestamp DESC LIMIT 10;"

# Check threshold status
sqlite3 /home/$USER/smart_meter/scheduler.db "SELECT * FROM thresholds;"

# View schedule execution log
sqlite3 /home/$USER/smart_meter/scheduler.db \
  "SELECT * FROM schedule_log ORDER BY executed_at DESC LIMIT 20;"
```

## Design Decisions

### Timer Schedules Start Immediately
Timer schedules (`schedule_type: "timer"`) are designed for immediate countdown operations. When created, they:
- Start counting down from the moment of creation
- Turn the relay OFF after `duration_seconds` expires
- Automatically delete themselves from the database after execution
- Use APScheduler's DateTrigger for one-time execution

**Use case:** "Turn off the device in 2 hours from now"

For scheduled future actions at specific times, use daily schedules instead.

### Thresholds Require Manual Re-enablement
When an energy threshold is exceeded:
1. Relay automatically turns OFF via MQTT command
2. Alert is published to `dev/<CLIENT_ID>/threshold/alert` (retained)
3. **Threshold is disabled** (`enabled` set to 0)

To re-enable monitoring, you must delete and recreate the threshold. This prevents:
- Repeated relay cycling if consumption stays high
- Nuisance shutoffs without user acknowledgment
- Unintended behavior during high-load periods

The user must actively address the high consumption before re-enabling automated monitoring.

### All Energy Readings Are Stored
Every energy reading from ESP32 devices is stored in the database without deduplication or filtering. This approach:
- Preserves complete historical data for analysis
- Allows accurate consumption calculations using `MAX(energy_kwh) - MIN(energy_kwh)`
- Enables detailed usage pattern analysis
- PZEM readings are cumulative (always increasing), so duplicates don't affect calculations

### Relay State Synchronization
The ESP32 subscribes to both:
- `dev/<CLIENT_ID>/relay/commands` - For command execution (RELAY_ON/RELAY_OFF)
- `dev/<CLIENT_ID>/relay/state` - For state synchronization across devices

This dual-subscription allows the ESP32 to:
- Execute commands from the scheduler/API
- Synchronize with retained state when reconnecting
- Maintain consistent state across MQTT clients

### WiFi Event-Driven Status Updates
The ESP32 uses WiFi event handlers to update connection status:
- `WiFiStationConnected` - Triggered when AP connection succeeds
- `WiFiGotIP` - Updates status when IP address is obtained
- `WiFiStationDisconnected` - Handles disconnections and auto-reconnect

This ensures the OLED display and MQTT status accurately reflect network state.

### Captive Portal Network Detection
The captive portal handles open networks (no password) differently:
- Detects if network security is "Open" or empty
- Hides password field for open networks
- Two-step connection process: create profile, then activate
- Automatic hotspot restart on failed connection attempts
- Validates connection for 10 seconds before considering it successful

### Scheduler Service Restart on Changes
The API automatically restarts the scheduler service (`smart-meter-scheduler.service`) using systemctl when schedules are created, updated, or deleted. This ensures:
- APScheduler reloads all jobs from the database
- Changes take effect immediately
- No manual intervention required
- Graceful restart with minimal downtime

---

## Development Status

### Completed
- ✅ ESP32 WiFi and MQTT reconnection with mDNS
- ✅ PZEM-004T energy monitoring (voltage, current, power, energy)
- ✅ MQTT telemetry publishing with configurable intervals
- ✅ Relay control via MQTT commands and state synchronization
- ✅ Device heartbeat and status reporting with LWT
- ✅ OLED status display with connection info and IP address
- ✅ WiFi event-driven status updates
- ✅ Configuration persistence (Preferences library)
- ✅ Raspberry Pi MQTT broker setup (Mosquitto)
- ✅ WiFi fallback with captive portal and ethernet detection
- ✅ Web-based WiFi management interface with open network support
- ✅ Automatic hotspot recovery on failed connections
- ✅ Smart meter scheduler service with APScheduler
- ✅ Daily schedules with day-of-week filtering
- ✅ Timer-based schedules with automatic cleanup
- ✅ Energy consumption thresholds with auto-disable
- ✅ REST API for remote management with CORS
- ✅ SQLite database for historical data
- ✅ Schedule update endpoint with partial updates
- ✅ Automatic scheduler restart on API changes
- ✅ Threshold alert publishing (retained MQTT messages)

### In Progress
- 🔄 Android application development
  - Device discovery and monitoring
  - Schedule management UI
  - Real-time dashboard
  - Threshold configuration

### Planned
- 📋 Power-off handling (UPS integration or safe shutdown)
- 📋 Web dashboard for Raspberry Pi (alternative to Android app)
- 📋 Energy cost calculations and billing estimates
- 📋 Usage alerts and push notifications
- 📋 Data export (CSV, JSON)
- 📋 Multi-level user authentication for API
- 📋 Schedule templates and presets
- 📋 Energy consumption analytics and insights
- 📋 MQTT authentication support

## Troubleshooting

### ESP32 Issues

**ESP32 won't connect to WiFi:**
- Press and hold BOOT button (GPIO 0) to reset WiFi credentials
- Connect to `ESP32_AP` and reconfigure
- Check if WiFi credentials are correct
- Verify WiFi network is 2.4GHz (ESP32 doesn't support 5GHz)
- Check serial monitor for detailed error messages

**OLED display not working:**
- Verify I2C wiring (SDA=GPIO22, SCL=GPIO23)
- Check OLED I2C address (default: 0x3C, shifted to 0x78 in code)
- Test with I2C scanner sketch
- Verify Wire.begin() is called with correct pins

**PZEM sensor not reading:**
- Verify TX/RX connections (PZEM TX→GPIO16, PZEM RX→GPIO17)
- Check PZEM power supply (5V)
- Ensure PZEM is properly connected to AC load
- Check serial2 baud rate (9600 for PZEM-004T v3.0)
- Monitor serial output for NaN values

**ESP32 can't find MQTT broker:**
- Verify Pi hostname: `avahi-browse -a | grep mqttpi`
- Try using Pi's IP address instead of `mqttpi.local`
- Check Mosquitto: `sudo systemctl status mosquitto`
- Test MQTT: `mosquitto_sub -h mqttpi.local -t '#' -v`
- Check mDNS resolution logs in serial monitor

**Display shows wrong status:**
- Check WiFi event handlers are properly registered
- Verify display update interval (1 second default)
- Check if status strings are being updated in event callbacks
- Monitor serial output for connection state changes

### Raspberry Pi Issues

**Can't access captive portal:**
- Check if `Pi_AP` network is visible
- Manually navigate to `http://192.168.4.1` in browser
- Verify captive portal service: `sudo systemctl status captive-portal`
- Check logs: `sudo journalctl -u captive-portal -n 50`
- Ensure dnsmasq configuration is correct

**Hotspot doesn't restart after wrong password:**
- Check logs: `sudo journalctl -u captive-portal -f`
- Manually restart: `sudo nmcli connection up Hotspot`
- Verify wifi-fallback script: `sudo systemctl status wifi-fallback`
- Check NetworkManager connection profiles: `nmcli connection show`

**Captive portal can't connect to open networks:**
- Verify password field is hidden for open networks
- Check JavaScript network selection logic
- Monitor Flask logs for connection attempts
- Ensure security field is properly detected

**Scheduler not executing jobs:**
- Check scheduler logs: `sudo journalctl -u smart-meter-scheduler -f`
- Verify schedules in database: `sqlite3 /home/$USER/smart_meter/scheduler.db "SELECT * FROM schedules;"`
- Restart scheduler: `sudo systemctl restart smart-meter-scheduler`
- Check MQTT connectivity: `mosquitto_sub -h localhost -t 'dev/#' -v`
- Verify APScheduler jobs: Look for "Added daily schedule" or "Added timer" in logs

**Timer schedules not working:**
- Verify `duration_seconds` is set correctly
- Check DateTrigger execution time in logs
- Ensure schedule is created successfully in database
- Monitor for automatic deletion after execution

**Daily schedules not respecting days_of_week:**
- Verify days are formatted as comma-separated string: "0,1,2,3,4"
- Check CronTrigger parameters in logs
- Remember: 0=Monday, 6=Sunday (APScheduler convention)
- If days_of_week is NULL, schedule runs every day

**REST API not responding:**
- Check API status: `sudo systemctl status smart-meter-api`
- Test health endpoint: `curl http://localhost:5001/api/health`
- View logs: `sudo journalctl -u smart-meter-api -n 50`
- Verify port 5001 is not in use: `sudo netstat -tulpn | grep 5001`
- Check CORS configuration if accessing from browser

**Scheduler restart fails from API:**
- Verify user has sudo privileges for systemctl
- Check sudoers configuration for passwordless systemctl
- Test manual restart: `sudo systemctl restart smart-meter-scheduler`
- Review API logs for subprocess errors

**Database errors:**
- Check database permissions: `ls -l /home/$USER/smart_meter/scheduler.db`
- Verify SQLite installation: `sqlite3 --version`
- Backup and recreate database:
  ```bash
  cd /home/$USER/smart_meter
  cp scheduler.db scheduler.db.backup
  rm scheduler.db
  python3 -c "from database import Database; Database('scheduler.db')"
  ```

**Energy readings not being stored:**
- Verify MQTT subscription to `dev/+/pzem/energy`
- Check MQTT client connection in scheduler logs
- Test MQTT publishing: `mosquitto_pub -h localhost -t "dev/ESP32-test/pzem/energy" -m "123.45"`
- Query database: `sqlite3 /home/$USER/smart_meter/scheduler.db "SELECT * FROM energy_readings;"`

**Threshold not triggering:**
- Verify threshold is enabled: `SELECT * FROM thresholds WHERE enabled=1;`
- Check consumption calculation in logs
- Ensure period_start is calculated correctly
- Monitor threshold check execution (every 60 seconds)
- Verify relay OFF command is published

**mDNS not working (`mqttpi.local` not resolving):**
- Check Avahi daemon: `sudo systemctl status avahi-daemon`
- Restart Avahi: `sudo systemctl restart avahi-daemon`
- Test resolution: `avahi-resolve -n mqttpi.local`
- Use IP address as fallback: `hostname -I`
- Check firewall rules for mDNS (port 5353 UDP)

### General Debugging

**View all service logs:**
```bash
# Real-time combined logs
sudo journalctl -u mosquitto -u smart-meter-scheduler -u smart-meter-api -f

# Last 100 lines from specific service
sudo journalctl -u smart-meter-scheduler -n 100

# Logs since last boot
sudo journalctl -b -u captive-portal

# Filter by error level
sudo journalctl -u smart-meter-scheduler -p err
```

**Check network connectivity:**
```bash
# WiFi status
iwconfig wlan0

# IP addresses
ip addr show

# Active connections
nmcli connection show --active

# MQTT broker connectivity
mosquitto_sub -h localhost -t '$SYS/#' -v

# Test mDNS
avahi-browse -a
```

**Test MQTT publishing:**
```bash
# Turn relay ON
mosquitto_pub -h mqttpi.local -t "dev/ESP32-fa641d44/relay/commands" -m "RELAY_ON"

# Turn relay OFF
mosquitto_pub -h mqttpi.local -t "dev/ESP32-fa641d44/relay/commands" -m "RELAY_OFF"

# Update PZEM config
mosquitto_pub -h mqttpi.local -t "dev/ESP32-fa641d44/pzem/config" \
  -m '{"metrics":5000,"energy":120000}'

# Subscribe to all topics
mosquitto_sub -h mqttpi.local -t '#' -v

# Subscribe to specific device
mosquitto_sub -h mqttpi.local -t 'dev/ESP32-fa641d44/#' -v
```

**Test REST API:**
```bash
# Health check
curl http://mqttpi.local:5001/api/health

# Get all devices
curl http://mqttpi.local:5001/api/devices

# Get schedules for device
curl http://mqttpi.local:5001/api/schedules/ESP32-fa641d44

# Create daily schedule
curl -X POST http://mqttpi.local:5001/api/schedules \
  -H "Content-Type: application/json" \
  -d '{"client_id":"ESP32-fa641d44","schedule_type":"daily","start_time":"08:00","end_time":"20:00","days_of_week":"0,1,2,3,4"}'

# Create timer schedule
curl -X POST http://mqttpi.local:5001/api/schedules \
  -H "Content-Type: application/json" \
  -d '{"client_id":"ESP32-fa641d44","schedule_type":"timer","duration_seconds":3600}'

# Update schedule
curl -X PUT http://mqttpi.local:5001/api/schedules/1 \
  -H "Content-Type: application/json" \
  -d '{"start_time":"09:00","end_time":"21:00"}'

# Delete schedule
curl -X DELETE http://mqttpi.local:5001/api/schedules/1

# Set threshold
curl -X PUT http://mqttpi.local:5001/api/thresholds/ESP32-fa641d44 \
  -H "Content-Type: application/json" \
  -d '{"limit_kwh":5.0,"reset_period":"daily"}'

# Get energy data
curl http://mqttpi.local:5001/api/energy/ESP32-fa641d44?limit=10
curl http://mqttpi.local:5001/api/energy/ESP32-fa641d44?period=day
```

**Check database contents:**
```bash
# Open database
sqlite3 /home/$USER/smart_meter/scheduler.db

# Inside sqlite3 shell:
.tables
.schema schedules
SELECT * FROM schedules;
SELECT * FROM thresholds;
SELECT * FROM energy_readings ORDER BY timestamp DESC LIMIT 10;
SELECT * FROM schedule_log ORDER BY executed_at DESC LIMIT 20;
.quit
```

**Monitor ESP32 serial output:**
```bash
# Using Arduino IDE Serial Monitor (115200 baud)
# Or using screen:
screen /dev/ttyUSB0 115200

# Or using minicom:
minicom -D /dev/ttyUSB0 -b 115200
```

**Reset and clean slate:**
```bash
# Reset WiFi credentials on ESP32: Hold BOOT button

# Reset Pi WiFi:
sudo nmcli connection delete Hotspot
sudo nmcli connection show  # List all connections
sudo nmcli connection delete <connection-name>  # Remove specific network

# Restart all services:
sudo systemctl restart mosquitto
sudo systemctl restart smart-meter-scheduler
sudo systemctl restart smart-meter-api
sudo systemctl restart captive-portal

# Clear database (backup first!):
cd /home/$USER/smart_meter
cp scheduler.db scheduler.db.backup
rm scheduler.db
python3 -c "from database import Database; Database('scheduler.db')"
```

## License

This project is open source and available for educational and personal use.

## Contributing

This is a personal learning project, but suggestions and improvements are welcome via issues or pull requests.

## Acknowledgments

- PZEM-004T library by Jakub Mandula
- WiFiManager by tzapu
- U8g2 OLED library by oliver
- Eclipse Mosquitto MQTT broker
- APScheduler by Alex Grönholm
- Flask web framework
- NetworkManager for Linux network management
