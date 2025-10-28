# IoT Smart Meter

A distributed energy monitoring and automation system built with ESP32 microcontrollers, Raspberry Pi, and MQTT for real-time power consumption tracking, remote appliance control, and automated scheduling.

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
- Publishes telemetry data to MQTT broker at configurable intervals:
  - Real-time metrics (voltage, current, power): every 3 seconds
  - Cumulative energy readings: every 60 seconds
  - Heartbeat messages: every 30 seconds
- Supports remote configuration via MQTT (adjust reporting intervals)
- WiFi provisioning via captive portal (WiFiManager)
- Automatic reconnection with mDNS support for broker discovery
- Factory reset via BOOT button (GPIO 0)

**MQTT Topics (Published):**
- `dev/<CLIENT_ID>/pzem/metrics` - Real-time voltage/current/power (JSON)
- `dev/<CLIENT_ID>/pzem/energy` - Cumulative energy consumption (kWh)
- `dev/<CLIENT_ID>/relay/state` - Relay status (0/1, retained)
- `dev/<CLIENT_ID>/status` - Device online/offline status (retained, with LWT)
- `dev/<CLIENT_ID>/heartbeat` - Keep-alive messages

**MQTT Topics (Subscribed):**
- `dev/<CLIENT_ID>/relay/commands` - Relay control (RELAY_ON/RELAY_OFF)
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

**WiFi Fallback Mechanism:**
- On boot, checks for network connectivity (15-second timeout)
- If no connection found, automatically creates AP hotspot (`Pi_AP`)
- Launches captive portal on `192.168.4.1` for configuration
- Supports Android, iOS, and Windows captive portal detection
- After successful connection, reboots and connects to configured network

#### 3. Smart Meter Scheduler Service
- Automated relay control based on schedules and energy thresholds
- Monitors energy consumption from all ESP32 devices
- Stores historical data in SQLite database
- APScheduler-based job execution

**Features:**
- **Daily Schedules**: Turn relays ON/OFF at specific times (recurring)
  - Supports day-of-week filtering (e.g., weekdays only: "1,2,3,4,5")
  - APScheduler format: 0=Monday, 6=Sunday
  - If not specified, runs every day
- **Timer Schedules**: One-time countdown timers for temporary operations
  - Starts immediately upon creation
  - Automatically turns relay OFF after specified duration
  - Self-deletes after execution
- **Energy Thresholds**: Auto-shutoff when consumption exceeds limits
  - Daily, weekly, or monthly reset periods
  - Automatic relay disconnect on threshold breach
  - Alert notifications via MQTT
  - Must be manually re-enabled after triggering (prevents repeated shutoffs)
- **Schedule Management**: Create, update, and delete schedules via REST API
  - Partial updates supported (modify only specific fields)
  - Time format validation (HH:MM)
  - Automatic scheduler restart on changes

#### 4. REST API Service
- Flask-based API for Android app integration
- Manages schedules, thresholds, and energy data
- CORS-enabled for cross-origin requests
- Runs on port 5001

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
- PZEM-004T v3.0/v4.0 energy meter module
- Relay module (active-LOW, connected to GPIO 4)
- SH1106 128x64 OLED display (I2C)
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
   - Verify GPIO pins match your hardware
   - Upload to ESP32

3. **Initial WiFi Configuration:**
   - On first boot, ESP32 creates `ESP32_AP` access point
   - Connect to `ESP32_AP` with your phone/laptop
   - Captive portal opens automatically (or navigate to `192.168.4.1`)
   - Configure WiFi credentials
   - Set MQTT broker address: `mqttpi.local` (or Pi's IP address)
   - Set MQTT port: `1883`

4. **Verify Operation:**
   - OLED should show "Connected" status
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

# Update database paths in Python files
sed -i "s|/home/sabado/smart_meter|/home/$USER/smart_meter|g" /home/$USER/smart_meter/*.py
```

**Note:** Update service files to use your username instead of `sabado`:
```bash
sudo sed -i "s|/home/sabado|/home/$USER|g" /etc/systemd/system/captive-portal.service
sudo sed -i "s|/home/sabado|/home/$USER|g" /etc/systemd/system/smart-meter-*.service
sudo sed -i "s|User=sabado|User=$USER|g" /etc/systemd/system/smart-meter-*.service
```

#### 5. Enable and Start Services

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

#### 6. Initial WiFi Connection

If Pi doesn't connect to a saved network on reboot, it will automatically:
1. Create `Pi_AP` hotspot (SSID: `Pi_AP`, Password: `12345678`)
2. Launch captive portal at `192.168.4.1`
3. Connect to `Pi_AP` and configure WiFi through the web interface
4. Pi reboots and connects to your network

#### 7. Verify Installation

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

Returns list of all known ESP32 devices.

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
  "days_of_week": "0,1,2,3,4"  // Optional: 0=Mon, 6=Sun (e.g., "0,1,2,3,4" = Mon-Fri)
}
```

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
  "start_time": "09:00",          // Optional - Update start time
  "end_time": "21:00",            // Optional - Update end time
  "duration_seconds": 7200,     // Optional - Update timer duration
  "days_of_week": "5,6"       // Optional - Update days (0=Mon, 6=Sun)
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
- Scheduler service automatically restarts to apply changes

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
    "last_reset": "2025-10-30 00:00:00"
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

**GET** `/api/energy/<client_id>?limit=100`

Get recent energy readings for a device.

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

### Customize WiFi Fallback

Edit `/etc/default/wifi-fallback`:
```bash
HOTSPOT_SSID="Pi_AP"        # AP name
HOTSPOT_PASS="12345678"     # AP password
CHECK_TIMEOUT=15            # Seconds to wait for WiFi connection
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
- `days_of_week` - Comma-separated days for daily schedules (0=Mon, 6=Sun, e.g., "0,1,2,3,4" for weekdays)
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
```

## Design Decisions

### Timer Schedules Start Immediately
Timer schedules (`schedule_type: "timer"`) are designed for immediate countdown operations. When created, they:
- Start counting down from the moment of creation
- Turn the relay OFF after `duration_seconds` expires
- Automatically delete themselves after execution

**Use case:** "Turn off the device in 2 hours from now"

For scheduled future actions at specific times, use daily schedules instead.

### Thresholds Require Manual Re-enablement
When an energy threshold is exceeded:
1. Relay automatically turns OFF
2. Alert is published to MQTT topic `dev/<CLIENT_ID>/threshold/alert`
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

---

## Development Status

### Completed
- ✅ ESP32 WiFi and MQTT reconnection with mDNS
- ✅ PZEM-004T energy monitoring (voltage, current, power, energy)
- ✅ MQTT telemetry publishing with configurable intervals
- ✅ Relay control via MQTT commands
- ✅ Device heartbeat and status reporting
- ✅ OLED status display for ESP32
- ✅ Raspberry Pi MQTT broker setup (Mosquitto)
- ✅ WiFi fallback with captive portal
- ✅ Web-based WiFi management interface
- ✅ Automatic hotspot recovery on failed connections
- ✅ Smart meter scheduler service
- ✅ Daily and timer-based schedules
- ✅ Energy consumption thresholds
- ✅ REST API for remote management
- ✅ SQLite database for historical data

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

## Troubleshooting

### ESP32 Issues

**ESP32 won't connect to WiFi:**
- Press and hold BOOT button (GPIO 0) to reset WiFi credentials
- Connect to `ESP32_AP` and reconfigure
- Check if WiFi credentials are correct
- Verify WiFi network is 2.4GHz (ESP32 doesn't support 5GHz)

**OLED display not working:**
- Verify I2C wiring (SDA=GPIO22, SCL=GPIO23)
- Check OLED I2C address (default: 0x3C)
- Test with I2C scanner sketch

**PZEM sensor not reading:**
- Verify TX/RX connections (TX→GPIO16, RX→GPIO17)
- Check PZEM power supply (5V)
- Ensure PZEM is properly connected to AC load

**ESP32 can't find MQTT broker:**
- Verify Pi hostname: `avahi-browse -a | grep mqttpi`
- Try using Pi's IP address instead of `mqttpi.local`
- Check Mosquitto: `sudo systemctl status mosquitto`
- Test MQTT: `mosquitto_sub -h mqttpi.local -t '#' -v`

### Raspberry Pi Issues

**Can't access captive portal:**
- Check if `Pi_AP` network is visible
- Manually navigate to `http://192.168.4.1` in browser
- Verify captive portal service: `sudo systemctl status captive-portal`
- Check logs: `sudo journalctl -u captive-portal -n 50`

**Hotspot doesn't restart after wrong password:**
- Check logs: `sudo journalctl -u captive-portal -f`
- Manually restart: `sudo nmcli connection up Hotspot`
- Verify wifi-fallback script: `sudo systemctl status wifi-fallback`

**Scheduler not executing jobs:**
- Check scheduler logs: `sudo journalctl -u smart-meter-scheduler -f`
- Verify schedules in database: `sqlite3 /home/$USER/smart_meter/scheduler.db "SELECT * FROM schedules;"`
- Restart scheduler: `sudo systemctl restart smart-meter-scheduler`
- Check MQTT connectivity: `mosquitto_sub -h localhost -t 'dev/#' -v`

**REST API not responding:**
- Check API status: `sudo systemctl status smart-meter-api`
- Test health endpoint: `curl http://localhost:5001/api/health`
- View logs: `sudo journalctl -u smart-meter-api -n 50`
- Verify port 5001 is not in use: `sudo netstat -tulpn | grep 5001`

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

**mDNS not working (`mqttpi.local` not resolving):**
- Check Avahi daemon: `sudo systemctl status avahi-daemon`
- Restart Avahi: `sudo systemctl restart avahi-daemon`
- Test resolution: `avahi-resolve -n mqttpi.local`
- Use IP address as fallback: `hostname -I`

### General Debugging

**View all service logs:**
```bash
# Real-time combined logs
sudo journalctl -u mosquitto -u smart-meter-scheduler -u smart-meter-api -f

# Last 100 lines from specific service
sudo journalctl -u smart-meter-scheduler -n 100

# Logs since last boot
sudo journalctl -b -u captive-portal
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
```

**Test MQTT publishing:**
```bash
# Turn relay ON
mosquitto_pub -h mqttpi.local -t "dev/ESP32-fa641d44/relay/commands" -m "RELAY_ON"

# Turn relay OFF
mosquitto_pub -h mqttpi.local -t "dev/ESP32-fa641d44/relay/commands" -m "RELAY_OFF"

# Subscribe to all topics
mosquitto_sub -h mqttpi.local -t '#' -v
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
