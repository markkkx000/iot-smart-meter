#!/usr/bin/env python3
from flask import Flask, render_template, request, redirect, url_for
import subprocess
import logging

app = Flask(__name__)

# simple logging to stderr/journal
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
        # defensive in case NAME contains ":" (rare)
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
    subprocess.run(["sudo", "nmcli", "dev", "wifi", "connect", ssid, "password", password])

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
# Android probe
@app.route("/generate_204")
@app.route("/generate_204/")
def android_probe():
    # Android expects a redirect or non-204 to trigger captive portal
    log.info("Android probe hit, redirecting to portal")
    return redirect(url_for("index"), code=302)

# Apple probe
@app.route("/hotspot-detect.html")
@app.route("/hotspot-detect.html/")
def apple_probe():
    log.info("Apple probe hit, redirecting to portal")
    return redirect(url_for("index"), code=302)

# Windows probe
@app.route("/connecttest.txt")
@app.route("/connecttest.txt/")
def windows_probe():
    log.info("Windows probe hit, redirecting to portal")
    return redirect(url_for("index"), code=302)

# catch-all for any other path that might be requested by clients
# but avoid interfering with static files (like /static/...)
@app.route("/<path:path>")
def catch_all(path):
    # allow static assets, favicon, or files with an extension to be served normally
    if path.startswith("static/") or path == "favicon.ico" or "." in path:
        # let Flask serve static assets as usual (Flask will handle /static/* automatically)
        log.debug("Asset request: %s, letting Flask handle it", path)
        # returning a 404 here will let Flask's static route try first, but Flask's static
        # route usually takes precedence — this is just defensive.
        return redirect(url_for("index"))
    # otherwise redirect everything to portal
    log.info("Catch-all hit for path=%s, redirecting to portal", path)
    return redirect(url_for("index"), code=302)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
