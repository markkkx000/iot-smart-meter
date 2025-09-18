#!/bin/bash
# wifi-fallback.sh

set -e
exec >>/var/log/wifi-fallback.log 2>&1
echo "[$(date)] Starting WiFi fallback script..."

WIFI_INTERFACE="wlan0"
ETH_INTERFACE="eth0"
CHECK_TIMEOUT=30
CAPTIVE_PORTAL_DIR="/home/sabado/captive_portal"

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

# Disconnect wlan0 from NetworkManager
nmcli device disconnect $WIFI_INTERFACE || true

# Flush and configure static IP
ip addr flush dev $WIFI_INTERFACE
ip addr add 192.168.4.1/24 dev $WIFI_INTERFACE
ip link set $WIFI_INTERFACE up

# Start AP services
systemctl restart hostapd
systemctl restart dnsmasq

# Launch captive portal
systemctl restart captive-portal

echo "[wifi-fallback] Captive portal launched."
