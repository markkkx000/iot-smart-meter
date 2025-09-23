#include <WiFi.h>
#include <WiFiManager.h>
#include <ESPmDNS.h>
#include <PubSubClient.h>
#include <Preferences.h>

#define RESET_PIN 0       // BOOT button
#define RELAY_PIN 26

WiFiManager wm;
WiFiClient espClient;
PubSubClient client(espClient);
Preferences preferences;

char mqtt_server[40] = "mqttpi.local";
char mqtt_port[6]   = "1883";

unsigned long lastMDNSRetry = 0;
const unsigned long MDNS_RETRY_INTERVAL = 5000;
bool mdnsResolved = false;

// Heartbeat timing
const unsigned long heartbeatInterval = 30000;
unsigned long lastHeartbeat = 0;
unsigned long lastReconnect = 0;

String getUID() {
  return String((uint32_t)ESP.getEfuseMac(), HEX);
}

// ============= TOPICS ============= //
String clientId        = "ESP32-" + getUID();
String relayStateTopic = "dev/" + clientId + "/relay/state";
String devStatusTopic  = "dev/all/status";
String commandsTopic   = "dev/" + clientId + "/relay/commands";
String heartbeatTopic  = "dev/" + clientId + "/heartbeat";

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
  } else {
    Serial.println("⚠️ No handler registered for this topic");
  }
}

// ===================================================
// ----------------- CONFIG --------------------------
// ===================================================

void saveConfig() {
  preferences.begin("mqtt", false);
  preferences.putString("server", mqtt_server);
  preferences.putString("port", mqtt_port);
  preferences.end();
}

void loadConfig() {
  preferences.begin("mqtt", true);
  String server = preferences.getString("server", "mqttpi.local");
  String port   = preferences.getString("port", "1883");
  preferences.end();

  server.toCharArray(mqtt_server, sizeof(mqtt_server));
  port.toCharArray(mqtt_port, sizeof(mqtt_port));
}

void setupWifi() {
  WiFiManagerParameter custom_mqtt_server("server", "MQTT Server", mqtt_server, 40);
  WiFiManagerParameter custom_mqtt_port("port", "MQTT Port", mqtt_port, 6);

  wm.addParameter(&custom_mqtt_server);
  wm.addParameter(&custom_mqtt_port);

  if (!wm.autoConnect("ESP32_AP")) {
    Serial.println("Failed to connect, restarting...");
    delay(3000);
    ESP.restart();
  }

  strcpy(mqtt_server, custom_mqtt_server.getValue());
  strcpy(mqtt_port, custom_mqtt_port.getValue());
  saveConfig();

  Serial.println("WiFi connected!");
  Serial.print("IP Address: "); Serial.println(WiFi.localIP());
  Serial.print("MQTT Server: "); Serial.println(mqtt_server);
  Serial.print("MQTT Port: "); Serial.println(mqtt_port);
}

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
      connectMQTT();
    } else {
      Serial.println("mDNS resolution failed, will retry later...");
      mdnsResolved = false;
    }
  } else {
    Serial.println("WARNING: using bare IP address of server, consider using mDNS to resolve dynamic IP address.");
    client.setServer(mqtt_server, atoi(mqtt_port));
    mdnsResolved = true;
  }
}

// ===================================================
// ----------------- MQTT ----------------------------
// ===================================================

void connectMQTT() {
  if (client.connected()) {
    Serial.println("Already connected to server... Nothing to do");
    return;
  }

  String offlineMsg = clientId + ": Offline";
  String onlineMsg  = clientId + ": Online";

  Serial.print("Connecting to MQTT...");

  if (client.connect(
        clientId.c_str(), NULL, NULL,
        devStatusTopic.c_str(),
        1, true,
        offlineMsg.c_str()
      )) 
  {
    Serial.println("connected");

    client.publish(devStatusTopic.c_str(), onlineMsg.c_str(), true);
    Serial.printf("Published Message: \"%s\" to %s\n", onlineMsg.c_str(), devStatusTopic.c_str());

    client.subscribe(commandsTopic.c_str());
    Serial.print("Subscribed to: "); Serial.println(commandsTopic);

    client.subscribe(relayStateTopic.c_str());
    Serial.print("Subscribed to: "); Serial.println(relayStateTopic);
  } else {
    Serial.print("failed, rc="); Serial.println(client.state());

    // Retry mDNS if resolution was missing
    if (String(mqtt_server).endsWith(".local")) {
      setupmDNS();
    }
  }
}

void publishHeartbeat() {
  if (!client.connected()) return;

  unsigned long now = millis();
  if (now - lastHeartbeat >= heartbeatInterval) {
    lastHeartbeat = now;

    String heartbeatMsg = clientId + ": Alive";
    client.publish(heartbeatTopic.c_str(), heartbeatMsg.c_str(), true);

    Serial.print("Published heartbeat: ");
    Serial.println(heartbeatMsg);
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
// ----------------- SETUP / LOOP --------------------
// ===================================================

void setup() {
  Serial.begin(115200);
  pinMode(RESET_PIN, INPUT_PULLUP);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, HIGH);

  loadConfig();       // Load saved settings
  setupWifi();        // WiFi + captive portal if needed

  client.setCallback(mqttCallback);
  setupmDNS();        // Resolve broker IP via mDNS
}

void loop() {
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
  }

  /*---TO-DO: publish energy readings from PZEM-004T to broker---*/
}
