#include <WiFi.h>
#include <WiFiManager.h>
#include <PubSubClient.h>
#include <Preferences.h>

#define RESET_PIN 0   // BOOT button

WiFiManager wm;
WiFiClient espClient;
PubSubClient client(espClient);

Preferences preferences;

char mqtt_server[40] = "raspberrypi.local";
char mqtt_port[6]   = "1883";

void saveConfig() {
  preferences.begin("mqtt", false);
  preferences.putString("server", mqtt_server);
  preferences.putString("port", mqtt_port);
  preferences.end();
}

void loadConfig() {
  preferences.begin("mqtt", true);
  String server = preferences.getString("server", "raspberrypi.local");
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

// void reconnectMQTT() {
//   static unsigned long lastAttempt = 0;
//   if (!client.connected() && millis() - lastAttempt > 5000) { // retry every 5s
//     lastAttempt = millis();

//     Serial.print("Connecting to MQTT...");
//     if (client.connect("ESP32Client")) {
//       Serial.println("connected");
//       client.publish("test/topic", "Hello from ESP32!");
//     } else {
//       Serial.print("failed, rc=");
//       Serial.print(client.state());
//       Serial.println(" retying in 5s");
//     }
//   }
// }

void setup() {
  Serial.begin(115200);
  pinMode(RESET_PIN, INPUT_PULLUP);

  loadConfig();       // Load previously saved settings
  setupWifi();        // Start WiFi + captive portal if needed

  client.setServer(mqtt_server, atoi(mqtt_port));
}

void loop() {
  
  if (digitalRead(RESET_PIN) == LOW) {
    Serial.println("Resetting WiFi and restarting...");
    wm.resetSettings();
    delay(1000);
    ESP.restart();
  }

  if (client.connected()) {
    client.loop();
  }
}
