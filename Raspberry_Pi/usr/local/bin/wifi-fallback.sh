#!/bin/bash
# wifi-fallback.sh

set -e
exec >>/var/log/wifi-fallback.log 2>&1
echo "[$(date)] Starting WiFi fallback script..."

WIFI_INTERFACE="wlan0"
ETH_INTERFACE="eth0"
CHECK_TIMEOUT=30
HOTSPOT_NAME="Hotspot"
HOTSPOT_SSID="Pi_AP"
HOTSPOT_PASS="12345678"

has_ip() {
    ip addr show "$1" | grep -q "inet "
}

check_eth() {
    if has_ip "$ETH_INTERFACE"; then
        echo "[wifi-fallback] Ethernet ($ETH_INTERFACE) connected, exiting."
        exit 0
    fi
}

check_eth

echo "[wifi-fallback] Waiting $CHECK_TIMEOUT seconds for WiFi..."
for i in $(seq 1 $CHECK_TIMEOUT); do
    if iwgetid -r >/dev/null 2>&1; then
        if has_ip "$WIFI_INTERFACE"; then
            echo "[wifi-fallback] WiFi connected to $(iwgetid -r)"
            exit 0
        fi
    fi

    check_eth

    sleep 1
done

echo "[wifi-fallback] No network detected, enabling Access Point mode..."

# Create hotspot
nmcli dev wifi hotspot ifname "$WIFI_INTERFACE" ssid "$HOTSPOT_SSID" password "$HOTSPOT_PASS" || true

nmcli connection modify "$HOTSPOT_NAME" connection.autoconnect no
nmcli connection modify "$HOTSPOT_NAME" ipv4.addresses 192.168.4.1/24
nmcli connection modify "$HOTSPOT_NAME" ipv4.method shared
nmcli connection down "$HOTSPOT_NAME" || true
nmcli connection up "$HOTSPOT_NAME"

# Launch captive portal
systemctl restart captive-portal

echo "[wifi-fallback] Captive portal launched."
