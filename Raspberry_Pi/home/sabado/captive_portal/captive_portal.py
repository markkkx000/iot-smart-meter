#!/usr/bin/env python3
from flask import Flask, render_template, request, redirect, url_for
import subprocess
import logging
import os
import time

app = Flask(__name__)

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

def add_network(ssid, password):
    """Add/connect WiFi via NetworkManager"""
    log.info("Adding network: %s", ssid)
    subprocess.run(["sudo", "nmcli", "dev", "wifi", "rescan"])
    result = subprocess.run(
        ["sudo", "nmcli", "dev", "wifi", "connect", ssid, "password", password],
        capture_output=True, text=True
    )

    if result.returncode == 0:
        check = subprocess.run(
            ["nmcli", "-t", "-f", "DEVICE,STATE", "dev"],
            capture_output=True, text=True
        )
        if "wlan0:connected" in check.stdout:
            log.info("Connection successful, rebooting")
            subprocess.Popen(["sudo", "shutdown", "-r", "now"])
            time.sleep(2)
            os._exit(0)
        else:
            log.warning("nmcli reported success but not connected yet")

def remove_network(ssid):
    """Remove saved WiFi connection"""
    log.info("Removing network: %s", ssid)
    subprocess.run(["sudo","nmcli", "connection", "delete", "id", ssid])

@app.route("/", methods=["GET", "POST"])
def index():
    if request.method == "POST":
        if "add" in request.form:
            ssid = request.form.get("ssid", "").strip()
            password = request.form.get("password", "").strip()
            if ssid and password:
                add_network(ssid, password)
        elif "remove" in request.form:
            ssid = request.form.get("remove")
            if ssid:
                remove_network(ssid)
        return redirect(url_for("index"))

    networks = list_networks()
    return render_template("index.html", networks=networks)

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
