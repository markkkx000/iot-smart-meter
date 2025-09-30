#!/usr/bin/env python3
from flask import Flask, render_template, request, redirect, url_for, jsonify, flash, session
import subprocess
import logging
import os
import time

app = Flask(__name__)
app.secret_key = os.urandom(24)  # For flash messages

logging.basicConfig(level=logging.INFO)
log = logging.getLogger("captive-portal")

def list_networks():
    """Get saved WiFi connections via NetworkManager"""
    result = subprocess.run(
        ["nmcli", "-t", "-f", "NAME,TYPE", "connection", "show"],
        capture_output=True, text=True
    )
    networks = []
    for line in result.stdout.strip().splitlines():
        if not line:
            continue
        parts = line.split(":")
        if len(parts) >= 2:
            name = ":".join(parts[:-1])
            ctype = parts[-1]
        else:
            continue
        if ctype == "802-11-wireless" and name != "Hotspot":
            networks.append(name)
    return networks

def scan_available_networks():
    """Scan for available WiFi networks"""
    log.info("Scanning for available networks...")
    subprocess.run(["nmcli", "dev", "wifi", "rescan"], capture_output=True)
    time.sleep(2)  # Give it time to scan
    
    result = subprocess.run(
        ["nmcli", "-t", "-f", "SSID,SIGNAL,SECURITY", "dev", "wifi", "list"],
        capture_output=True, text=True
    )
    
    networks = []
    seen_ssids = set()
    
    for line in result.stdout.strip().splitlines():
        if not line:
            continue
        parts = line.split(":")
        if len(parts) >= 3:
            ssid = parts[0].strip()
            signal = parts[1].strip()
            security = parts[2].strip()
            
            # Skip empty SSIDs and duplicates
            if ssid and ssid not in seen_ssids:
                seen_ssids.add(ssid)
                networks.append({
                    "ssid": ssid,
                    "signal": int(signal) if signal else 0,
                    "security": security if security else "Open"
                })
    
    # Sort by signal strength
    networks.sort(key=lambda x: x["signal"], reverse=True)
    return networks

def add_network(ssid, password):
    """Add/connect WiFi via NetworkManager using two-step process"""
    log.info("Attempting to add network: %s", ssid)
    
    # Step 1: Create the connection profile WITHOUT activating it
    # This keeps the hotspot running
    if password and password.strip():
        # Secured network with password
        create_cmd = [
            "sudo", "nmcli", "connection", "add",
            "type", "wifi",
            "con-name", ssid,
            "ssid", ssid,
            "wifi-sec.key-mgmt", "wpa-psk",
            "wifi-sec.psk", password
        ]
    else:
        # Open network
        create_cmd = [
            "sudo", "nmcli", "connection", "add",
            "type", "wifi",
            "con-name", ssid,
            "ssid", ssid
        ]
    
    log.info("Creating connection profile for: %s", ssid)
    result = subprocess.run(create_cmd, capture_output=True, text=True)
    
    if result.returncode != 0:
        error_msg = (result.stderr + "\n" + result.stdout).strip()
        log.error("Failed to create connection profile: %s", error_msg)
        
        if "already exists" in error_msg.lower():
            # Connection already exists, update the password
            log.info("Connection profile exists, updating password")
            if password and password.strip():
                update_result = subprocess.run(
                    ["sudo", "nmcli", "connection", "modify", ssid,
                     "wifi-sec.psk", password],
                    capture_output=True, text=True
                )
                if update_result.returncode != 0:
                    return False, "Failed to update network credentials."
        else:
            return False, "Failed to create network profile. Please try again."
    
    # Step 2: Now try to activate the connection
    log.info("Activating connection to: %s", ssid)
    activate_result = subprocess.run(
        ["sudo", "nmcli", "connection", "up", ssid],
        capture_output=True, text=True
    )
    
    if activate_result.returncode != 0:
        error_msg = (activate_result.stderr + "\n" + activate_result.stdout).strip()
        log.error("Connection activation failed: %s", error_msg)
        
        # Delete the failed connection profile
        log.info("Cleaning up failed connection profile for: %s", ssid)
        subprocess.run(
            ["sudo", "nmcli", "connection", "delete", "id", ssid],
            capture_output=True, text=True
        )
        
        # Restart the hotspot since we disconnected from it
        log.info("Restarting hotspot after failed connection attempt")
        restart_result = subprocess.run(
            ["sudo", "nmcli", "connection", "up", "Hotspot"],
            capture_output=True, text=True
        )
        
        if restart_result.returncode == 0:
            log.info("Hotspot restarted successfully")
        else:
            log.error("Failed to restart hotspot: %s", restart_result.stderr)
        
        # Parse error messages
        if "Secrets were required" in error_msg or "pre-shared key may be incorrect" in error_msg:
            return False, "Incorrect password. Please try again."
        elif "802-11-wireless-security" in error_msg:
            return False, "Incorrect password. Please try again."
        elif "No network with SSID" in error_msg or "not found" in error_msg.lower():
            return False, "Network not found. It may be out of range."
        elif "Connection activation failed" in error_msg:
            if "security" in error_msg.lower() or "auth" in error_msg.lower():
                return False, "Authentication failed. Please check your password."
            return False, "Failed to connect. Please check your password and try again."
        else:
            return False, "Connection failed. Please try again."
    
    # Connection activated successfully, verify it
    log.info("Connection activated, verifying...")
    for i in range(10):
        time.sleep(1)
        check = subprocess.run(
            ["nmcli", "-t", "-f", "DEVICE,STATE", "dev"],
            capture_output=True, text=True
        )
        
        if "wlan0:connected" in check.stdout:
            log.info("Connection verified! Rebooting...")
            subprocess.Popen(["sudo", "shutdown", "-r", "now"])
            time.sleep(2)
            os._exit(0)
    
    # Connection didn't verify in time
    log.warning("Connection activated but not showing as connected after 10 seconds")
    return False, "Connection established but taking longer than expected. The Pi will continue trying."

def remove_network(ssid):
    """Remove saved WiFi connection"""
    log.info("Removing network: %s", ssid)
    result = subprocess.run(
        ["sudo", "nmcli", "connection", "delete", "id", ssid],
        capture_output=True, text=True
    )
    if result.returncode == 0:
        return True, f"Network '{ssid}' removed successfully."
    else:
        return False, f"Failed to remove network '{ssid}'."

@app.route("/", methods=["GET", "POST"])
def index():
    if request.method == "POST":
        if "connect" in request.form:
            ssid = request.form.get("ssid", "").strip()
            password = request.form.get("password", "").strip()
            
            if not ssid:
                flash("Please select a network.", "error")
            else:
                success, message = add_network(ssid, password)
                if success:
                    flash(message, "success")
                else:
                    flash(message, "error")
                    
        elif "remove" in request.form:
            ssid = request.form.get("remove")
            if ssid:
                success, message = remove_network(ssid)
                flash(message, "success" if success else "error")
                
        return redirect(url_for("index"))

    saved_networks = list_networks()
    available_networks = scan_available_networks()
    return render_template("index.html", 
                         saved_networks=saved_networks,
                         available_networks=available_networks)

@app.route("/api/scan")
def api_scan():
    """API endpoint to refresh available networks"""
    networks = scan_available_networks()
    return jsonify(networks)

# --- captive portal probe handling for various OSes ---
@app.route("/generate_204")
@app.route("/generate_204/")
def android_probe():
    log.info("Android probe hit, redirecting to portal")
    return redirect(url_for("index"), code=302)

@app.route("/hotspot-detect.html")
@app.route("/hotspot-detect.html/")
def apple_probe():
    log.info("Apple probe hit, redirecting to portal")
    return redirect(url_for("index"), code=302)

@app.route("/connecttest.txt")
@app.route("/connecttest.txt/")
def windows_probe():
    log.info("Windows probe hit, redirecting to portal")
    return redirect(url_for("index"), code=302)

@app.route("/<path:path>")
def catch_all(path):
    if path.startswith("static/"):
        return "", 404
    if path == "favicon.ico":
        return "", 404
    if "." in path:
        return "", 404

    log.info("Catch-all hit for path=%s, redirecting to portal", path)
    return redirect(url_for("index"), code=302)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
