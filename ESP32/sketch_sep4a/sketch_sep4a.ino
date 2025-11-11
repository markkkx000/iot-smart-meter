#include <WiFi.h>
#include <WiFiManager.h>
#include <ESPmDNS.h>
#include <PubSubClient.h>
#include <Preferences.h>
#include <PZEM004Tv30.h>
#include <ArduinoJson.h>
#include <U8g2lib.h>
#include <Wire.h>
#include <ESPAsyncWebServer.h>
#include <ElegantOTA.h>

#define RESET_PIN 0       // BOOT button
#define RELAY_PIN 4
#define PZEM_RX 16
#define PZEM_TX 17
#define OLED_SDA 22
#define OLED_SCL 23

WiFiManager wm;
WiFiClient espClient;
PubSubClient client(espClient);
PZEM004Tv30 pzem(Serial2, PZEM_RX, PZEM_TX);
U8G2_SH1106_128X64_NONAME_F_HW_I2C display(U8G2_R0, U8X8_PIN_NONE, OLED_SCL, OLED_SDA);
AsyncWebServer server(80);

char mqtt_server[40] = "mqttpi.local";
char mqtt_port[6]    = "1883";

unsigned long lastMDNSRetry = 0;
const unsigned long MDNS_RETRY_INTERVAL = 5000;
bool mdnsResolved = false;

// --- Heartbeat timing ---
const unsigned long heartbeatInterval = 30000;
unsigned long lastHeartbeat = 0;
unsigned long lastReconnect = 0;

// --- PZEM timing (defaults) ---
unsigned long metricsInterval = 3000;   // 3 seconds
unsigned long energyInterval  = 60000;  // 60 seconds
unsigned long lastMetricsPublish = 0;
unsigned long lastEnergyPublish  = 0;

// --- Display timing --- ADD THIS SECTION
unsigned long lastDisplayUpdate = 0;
const unsigned long displayUpdateInterval = 1000;
float currentPower = 0.0;
String wifiStatus = "Disconnected";
String mqttStatus = "Disconnected";

// ===================================================
// ---------------- HELPERS --------------------------
// ===================================================

String getUID() {
  return String((uint32_t)ESP.getEfuseMac(), HEX);
}

void saveIntervals() {
  Preferences pref;
  if (pref.begin("intervals", false)) {
    pref.putULong("metrics", metricsInterval);
    pref.putULong("energy", energyInterval);
    pref.end();
  }
}

void loadIntervals() {
  Preferences pref;
  if (pref.begin("intervals", true)) {
    metricsInterval = pref.getULong("metrics", 3000);
    energyInterval  = pref.getULong("energy", 60000);
    pref.end();
  }
}

// ===================================================
// ---------------- OLED DISPLAY ---------------------
// ===================================================

void updateDisplay() {
  display.clearBuffer();

  display.setFont(u8g2_font_5x7_tf);
  String deviceID = "ESP32-" + getUID();
  display.drawStr(0, 8, deviceID.c_str());

  display.setFont(u8g2_font_6x10_tf);
  display.drawStr(0, 20, "WiFi:");
  display.drawStr(35, 20, wifiStatus.c_str());

  display.drawStr(0, 32, "MQTT:");
  display.drawStr(35, 32, mqttStatus.c_str());

  if (WiFi.status() == WL_CONNECTED && wifiStatus != "AP Mode") {
    display.setFont(u8g2_font_5x7_tf);
    String ip = WiFi.localIP().toString();
    display.drawStr(0, 42, ip.c_str());
    display.setFont(u8g2_font_6x10_tf);
  }

  display.drawStr(0, 52, "Power:");
  char powerStr[16];
  snprintf(powerStr, sizeof(powerStr), "%.1fW", currentPower);
  display.drawStr(45, 52, powerStr);

  display.drawStr(0, 62, "Relay:");
  bool relayState = (digitalRead(RELAY_PIN) == LOW);
  display.drawStr(45, 62, relayState ? "ON" : "OFF");

  display.sendBuffer();
}

// ===================================================
// ---------------- TOPICS ---------------------------
// ===================================================

String clientId        = "ESP32-" + getUID();
String relayStateTopic = "dev/" + clientId + "/relay/state";
String devStatusTopic  = "dev/" + clientId + "/status";
String commandsTopic   = "dev/" + clientId + "/relay/commands";
String heartbeatTopic  = "dev/" + clientId + "/heartbeat";
String configTopic     = "dev/" + clientId + "/pzem/config";
String metricsTopic    = "dev/" + clientId + "/pzem/metrics";
String energyTopic     = "dev/" + clientId + "/pzem/energy";

// ===================================================
// ---------------- HANDLERS -------------------------
// ===================================================

void handleRelayCommand(const String& msg) {
  if (msg.equalsIgnoreCase("RELAY_ON")) {
    digitalWrite(RELAY_PIN, LOW);   // active-LOW
    client.publish(relayStateTopic.c_str(), "1", true);
    Serial.println("Relay switched ON (via commandsTopic)");
  } else if (msg.equalsIgnoreCase("RELAY_OFF")) {
    digitalWrite(RELAY_PIN, HIGH);
    client.publish(relayStateTopic.c_str(), "0", true);
    Serial.println("Relay switched OFF (via commandsTopic)");
  }
}

void handleRelayState(const String& msg) {
  if (msg == "1") {
    digitalWrite(RELAY_PIN, LOW);
    Serial.println("Relay switched ON (via relayStateTopic sync)");
  } else if (msg == "0") {
    digitalWrite(RELAY_PIN, HIGH);
    Serial.println("Relay switched OFF (via relayStateTopic sync)");
  } else {
    Serial.println("Invalid state, defaulting OFF");
    digitalWrite(RELAY_PIN, HIGH);
  }
}

void handleConfig(const String& msg) {
  // Example JSON: {"metrics":5000,"energy":120000}
  StaticJsonDocument<128> doc;
  DeserializationError error = deserializeJson(doc, msg);

  if (!error) {
    if (doc.containsKey("metrics")) {
      metricsInterval = doc["metrics"];
      Serial.printf("Updated metrics interval: %lu ms\n", metricsInterval);
    }
    if (doc.containsKey("energy")) {
      energyInterval = doc["energy"];
      Serial.printf("Updated energy interval: %lu ms\n", energyInterval);
    }
    saveIntervals();
  } else {
    Serial.println("⚠️ Failed to parse config JSON");
  }
}

// ===================================================
// ---------------- DISPATCHER -----------------------
// ===================================================

void mqttCallback(char* topic, byte* message, unsigned int length) {
  String msg;
  for (int i = 0; i < length; i++) {
    msg += (char)message[i];
  }

  String t = String(topic);
  Serial.printf("Message arrived [%s] => %s\n", topic, msg.c_str());

  if (t == commandsTopic) {
    handleRelayCommand(msg);
  } else if (t == relayStateTopic) {
    handleRelayState(msg);
  } else if (t == configTopic) {
    handleConfig(msg);
  } else {
    Serial.println("⚠️ No handler registered for this topic");
  }
}

// ===================================================
// ---------------- PZEM PUBLISHERS ------------------
// ===================================================

void publishMetrics() {
  if (!client.connected()) return;
  unsigned long now = millis();
  if (now - lastMetricsPublish < metricsInterval) return;
  lastMetricsPublish = now;

  float voltage = pzem.voltage();
  float current = pzem.current();
  float power   = pzem.power();

  if (isnan(voltage) || isnan(current) || isnan(power)) {
    Serial.println("⚠️ Metrics reading error, skipping...");
    currentPower = 0.0;
    return;
  }

  currentPower = power;

  StaticJsonDocument<128> doc;
  doc["voltage"] = voltage;
  doc["current"] = current;
  doc["power"]   = power;

  char buffer[128];
  size_t n = serializeJson(doc, buffer);
  client.publish(metricsTopic.c_str(), buffer, n);

  Serial.printf("Published Metrics JSON to %s => %s\n", metricsTopic.c_str(), buffer);
}

void publishEnergy() {
  if (!client.connected()) return;

  unsigned long now = millis();
  if (now - lastEnergyPublish < energyInterval) return;
  lastEnergyPublish = now;

  float energy = pzem.energy();
  if (isnan(energy)) {
    Serial.println("⚠️ Energy reading error, skipping...");
    return;
  }

  char energyBuf[32];
  dtostrf(energy, 1, 2, energyBuf);
  client.publish(energyTopic.c_str(), energyBuf, true);

  Serial.printf("Published Energy to %s => %s\n", energyTopic.c_str(), energyBuf);
}

// ===================================================
// ---------------- CONFIG ---------------------------
// ===================================================

void saveConfig() {
  Preferences pref;
  if (pref.begin("mqtt", false)) {
    pref.putString("server", mqtt_server);
    pref.putString("port", mqtt_port);
    pref.end();
  }
}

void loadConfig() {
  Preferences pref;
  if (pref.begin("mqtt", true)) {
    String server = pref.getString("server", "mqttpi.local");
    String port   = pref.getString("port", "1883");
    pref.end();
    server.toCharArray(mqtt_server, sizeof(mqtt_server));
    port.toCharArray(mqtt_port, sizeof(mqtt_port));
  }
}

void setupWifi() {
  wifiStatus = "Connecting...";
  
  WiFiManagerParameter custom_mqtt_server("server", "MQTT Server", mqtt_server, 40);
  WiFiManagerParameter custom_mqtt_port("port", "MQTT Port", mqtt_port, 6);

  wm.addParameter(&custom_mqtt_server);
  wm.addParameter(&custom_mqtt_port);

  wm.setAPCallback([](WiFiManager *myWiFiManager) {
    display.clearBuffer();
    display.setFont(u8g2_font_6x10_tf);
    display.drawStr(25, 15, "SaBaDo Energy");
    display.drawStr(35, 28, "Meter v1.0");

    display.drawStr(0, 45, "AP Mode Active");
    display.setFont(u8g2_font_5x7_tf);
    display.drawStr(0, 56, "SSID: ESP32_AP");
    display.drawStr(0, 64, "IP: 192.168.4.1");
    display.sendBuffer();

    Serial.println("Entered config mode");
    Serial.println(WiFi.softAPIP());
  });

  if (!wm.autoConnect("ESP32_AP")) {
    Serial.println("Failed to connect, restarting...");
    delay(3000);
    ESP.restart();
  }

  strcpy(mqtt_server, custom_mqtt_server.getValue());
  strcpy(mqtt_port, custom_mqtt_port.getValue());
  saveConfig();

  wifiStatus = "Connected";
  Serial.println("WiFi connected!");
  Serial.print("IP Address: "); Serial.println(WiFi.localIP());
  Serial.print("MQTT Server: "); Serial.println(mqtt_server);
  Serial.print("MQTT Port: "); Serial.println(mqtt_port);
}

// ===================================================
// ---------------- mDNS -----------------------------
// ===================================================

void setupmDNS() {
  String hostname = "esp32node-" + getUID();
  if (!MDNS.begin(hostname.c_str())) {
    Serial.println("Error setting up mDNS responder");
  } else {
    Serial.print("mDNS responder started: ");
    Serial.println(hostname + ".local");
  }

  if (String(mqtt_server).endsWith(".local")) {
    Serial.print("Resolving mDNS for ");
    Serial.println(mqtt_server);

    String serverHostname = mqtt_server;
    serverHostname.replace(".local", "");

    IPAddress brokerIP = MDNS.queryHost(serverHostname);
    if (brokerIP != INADDR_NONE) {
      Serial.print("Resolved broker IP: ");
      Serial.println(brokerIP);
      client.setServer(brokerIP, atoi(mqtt_port));
      mdnsResolved = true;
      mqttStatus = "Resolved";
      connectMQTT();
    } else {
      Serial.println("mDNS resolution failed, will retry later...");
      mdnsResolved = false;
      mqttStatus = "DNS Fail";
    }
  } else {
    Serial.println("WARNING: using bare IP, consider mDNS for dynamic IPs");
    client.setServer(mqtt_server, atoi(mqtt_port));
    mdnsResolved = true;
  }
}

// ===================================================
// -------------- WIFI EVENT HANDLERS ----------------
// ===================================================

void WiFiStationConnected(WiFiEvent_t event, WiFiEventInfo_t info) {
  Serial.println("Connected to AP successfully!");
}

void WiFiGotIP(WiFiEvent_t event, WiFiEventInfo_t info) {
  wifiStatus = "Connected";
  Serial.println("WiFi connected");
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
}

void WiFiStationDisconnected(WiFiEvent_t event, WiFiEventInfo_t info) {
  wifiStatus = "Disconnected";
  mqttStatus = "Disconnected";
  Serial.println("Disconnected from WiFi access point");
  Serial.print("WiFi lost connection. Reason: ");
  Serial.println(info.wifi_sta_disconnected.reason);
  Serial.println("Trying to Reconnect");
  WiFi.begin();
}

// ===================================================
// ---------------- MQTT -----------------------------
// ===================================================

void connectMQTT() {
  if (client.connected()) {
    Serial.println("Already connected to server... Nothing to do");
    mqttStatus = "Connected";
    return;
  }

  String offlineMsg = "Offline";
  String onlineMsg  = "Online";

  mqttStatus = "Connecting...";
  Serial.print("Connecting to MQTT...");

  if (client.connect(
        clientId.c_str(), NULL, NULL,
        devStatusTopic.c_str(),
        1, true,
        offlineMsg.c_str()
      ))
  {
    Serial.println("connected");
    mqttStatus = "Connected";
    client.publish(devStatusTopic.c_str(), onlineMsg.c_str(), true);
    Serial.printf("Published Message: \"%s\" to %s\n", onlineMsg.c_str(), devStatusTopic.c_str());

    client.subscribe(commandsTopic.c_str());
    client.subscribe(relayStateTopic.c_str());
    client.subscribe(configTopic.c_str());
  } else {
    mqttStatus = "Failed";
    Serial.print("failed, rc="); Serial.println(client.state());
    if (String(mqtt_server).endsWith(".local")) setupmDNS();
  }
}

void publishHeartbeat() {
  if (!client.connected()) return;
  unsigned long now = millis();
  if (now - lastHeartbeat >= heartbeatInterval) {
    lastHeartbeat = now;
    String heartbeatMsg = "Alive";
    client.publish(heartbeatTopic.c_str(), heartbeatMsg.c_str(), true);
    Serial.printf("Published heartbeat: %s\n", heartbeatMsg.c_str());
  }
}

void handleReconnect() {
  if (!client.connected() && millis() - lastReconnect > 5000) {
    lastReconnect = millis();
    connectMQTT();
  }
}

void handleMDNS() {
  if (!mdnsResolved && String(mqtt_server).endsWith(".local")) {
    if (millis() - lastMDNSRetry > MDNS_RETRY_INTERVAL) {
      lastMDNSRetry = millis();
      setupmDNS();
    }
  }
}

// ===================================================
// ---------------- SETUP / LOOP ---------------------
// ===================================================

void setup() {
  Serial.begin(115200);
  Wire.begin(22, 23);
  delay(100);
  display.setI2CAddress(0x3C << 1);

  display.begin();
  display.clearBuffer();
  display.setFont(u8g2_font_6x10_tf);
  display.drawStr(25, 30, "SaBaDo Energy");
  display.drawStr(35, 45, "Meter v1.0");
  display.sendBuffer();
  delay(2000);

  pinMode(RESET_PIN, INPUT_PULLUP);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, HIGH);

  loadConfig();
  loadIntervals();

  WiFi.onEvent(WiFiStationConnected, ARDUINO_EVENT_WIFI_STA_CONNECTED);
  WiFi.onEvent(WiFiStationDisconnected, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);
  WiFi.onEvent(WiFiGotIP, ARDUINO_EVENT_WIFI_STA_GOT_IP);

  setupWifi();

  server.on("/", HTTP_GET, [](AsyncWebServerRequest *request) {
    String html = "<html><body style='font-family: Arial; margin: 40px;'>";
    html += "<h1>SaBaDo Energy Meter</h1>";
    html += "<p><strong>Device ID:</strong> " + clientId + "</p>";
    html += "<p><strong>IP Address:</strong> " + WiFi.localIP().toString() + "</p>";
    html += "<p><strong>WiFi Status:</strong> " + wifiStatus + "</p>";
    html += "<p><strong>MQTT Status:</strong> " + mqttStatus + "</p>";
    html += "<hr>";
    html += "<p><a href='/update' style='font-size: 18px;'>Firmware Update</a></p>";
    html += "</body></html>";
    request->send(200, "text/html", html);
  });

  ElegantOTA.begin(&server);  // Start ElegantOTA
  server.begin();
  Serial.println("HTTP server started - OTA available at /update");

  client.setCallback(mqttCallback);
  setupmDNS();
}

void loop() {
  ElegantOTA.loop();

  if (digitalRead(RESET_PIN) == LOW) {
    Serial.println("Resetting WiFi and restarting...");
    wm.resetSettings();
    delay(1000);
    ESP.restart();
  }

  handleReconnect();
  handleMDNS();

  if (client.connected()) {
    client.loop();
    publishHeartbeat();
    publishMetrics();
    publishEnergy();
  } else {
    mqttStatus = "Disconnected";
  }

  if (millis() - lastDisplayUpdate >= displayUpdateInterval) {
    lastDisplayUpdate = millis();
    updateDisplay();
  }
}
