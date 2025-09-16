#include <WiFi.h>
#include <WiFiManager.h>
#include <ESPmDNS.h>
#include <PubSubClient.h>
#include <Preferences.h>

#define RESET_PIN 0       // BOOT button

WiFiManager wm;
WiFiClient espClient;
PubSubClient client(espClient);

Preferences preferences;

char mqtt_server[40] = "mqttpi.local";
char mqtt_port[6]   = "1883";

unsigned long lastMDNSRetry = 0;
const unsigned long MDNS_RETRY_INTERVAL = 5000;
bool mdnsResolved = false;

void mqttCallback(char* topic, byte* message, unsigned int length) {
  Serial.print("Message arrived on topic: ");
  Serial.print(topic);
  Serial.print(" | Message: ");

  for (int i = 0; i < length; i++) {
    Serial.print((char)message[i]);
  }
  Serial.println();
}

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

String getUID() {
  return String((uint32_t)ESP.getEfuseMac(), HEX);
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

void connectMQTT() {
  if (client.connected()) {
    Serial.println("Already connected to server... Nothing to do");
    return;
  }
  
  String clientId = "ESP32Client-" + getUID();
  Serial.print("Connecting to MQTT...");
  if (client.connect(clientId.c_str())) {
    Serial.println("connected");
    client.publish("mnode/status", "Online");

    client.subscribe("mnode/commands");
    Serial.println("Subscribed to mnode/commands");
  } else {
    Serial.print("failed, rc=");
    Serial.println(client.state());

    // Retry mDNS if resolution was missing
    if (String(mqtt_server).endsWith(".local")) {
      setupmDNS();
    }
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(RESET_PIN, INPUT_PULLUP);

  loadConfig();       // Load previously saved settings
  setupWifi();        // Start WiFi + captive portal if needed

  client.setCallback(mqttCallback);

  setupmDNS();        // Resolve mDNS
}

void loop() {
  if (digitalRead(RESET_PIN) == LOW) {
    Serial.println("Resetting WiFi and restarting...");
    wm.resetSettings();
    delay(1000);
    ESP.restart();
  }

  static unsigned long lastReconnect = 0;
  if (!client.connected() && millis() - lastReconnect > 5000) {
    lastReconnect = millis();
    connectMQTT();
  }

  if (!mdnsResolved && String(mqtt_server).endsWith(".local")) {
    if (millis() - lastMDNSRetry > MDNS_RETRY_INTERVAL) {
      lastMDNSRetry = millis();
      setupmDNS();
    }
  }

  if (client.connected()) {
    client.loop();
  }

  // add relay switching logic here //
  /*---TO-DO: publish energy readings from PZEM-004T to broker---*/
}
