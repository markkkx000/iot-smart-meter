#!/usr/bin/env python3
from flask import Flask, render_template, request, redirect, url_for
import subprocess

app = Flask(__name__)

def list_networks():
    """Get saved WiFi connections via NetworkManager"""
    result = subprocess.run(
        ["nmcli", "-t", "-f", "NAME,TYPE", "connection", "show"],
        capture_output=True, text=True
    )
    networks = []
    for line in result.stdout.strip().splitlines():
        name, ctype = line.split(":")
        if ctype == "802-11-wireless":  # only Wi-Fi
            networks.append(name)
    return networks

def add_network(ssid, password):
    """Add/connect WiFi via NetworkManager"""
    subprocess.run(["sudo","nmcli", "dev", "wifi", "connect", ssid, "password", password])

def remove_network(ssid):
    """Remove saved WiFi connection"""
    subprocess.run(["sudo","nmcli", "connection", "delete", "id", ssid])

@app.route("/", methods=["GET", "POST"])
def index():
    if request.method == "POST":
        if "add" in request.form:
            ssid = request.form["ssid"].strip()
            password = request.form["password"].strip()
            if ssid and password:
                add_network(ssid, password)
        elif "remove" in request.form:
            ssid = request.form["remove"]
            remove_network(ssid)
        return redirect(url_for("index"))

    networks = list_networks()
    return render_template("index.html", networks=networks)

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=80)
