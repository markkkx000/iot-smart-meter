#include <WiFiManager.h>
#include <WiFi.h>

#define RESET_PIN 0   // BOOT button

WiFiManager wm;

void setup() {
  Serial.begin(115200);
  pinMode(RESET_PIN, INPUT_PULLUP);

  if (digitalRead(RESET_PIN) == LOW) {
    Serial.println("resetting WiFi settings...");
    wm.resetSettings();
    delay(1000);
  }

  if (!wm.autoConnect("ESP32_AP", "12345678")) {
    Serial.println("Failed to connect and hit timeout, restarting...");
    ESP.restart();
  }

  Serial.println("Connected to WiFi!");
  Serial.print("ESP32 IP Address: ");
  Serial.println(WiFi.localIP());
}

void loop() {
  if (digitalRead(RESET_PIN) == LOW) {
    Serial.println("Button pressed → resetting WiFi and restarting...");
    wm.resetSettings();
    delay(1000);
    ESP.restart();
  }

  // Your normal logic here (MQTT, sensors, etc.)
}
