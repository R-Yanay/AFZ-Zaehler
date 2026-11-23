#!/usr/bin/env bash
# Simuliert einen Zaehlsensor an einer Fahrzeugtuer und publiziert Zaehlwerte via MQTT.
# Voraussetzung: mosquitto-clients (sudo apt install mosquitto-clients)
# Aufruf: ./scripts/sensor-sim.sh [FAHRZEUG] [TUER] [ANZAHL_MELDUNGEN]
set -euo pipefail

VEHICLE="${1:-bus-101}"
DOOR="${2:-1}"
COUNT="${3:-10}"
BROKER="${MQTT_HOST:-localhost}"
TOPIC="afz/vehicle/${VEHICLE}/door/${DOOR}/count"

for ((i = 1; i <= COUNT; i++)); do
    boarding=$((RANDOM % 6))
    alighting=$((RANDOM % 6))
    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    payload="{\"vehicleId\":\"${VEHICLE}\",\"doorId\":${DOOR},\"boarding\":${boarding},\"alighting\":${alighting},\"timestamp\":\"${ts}\"}"
    mosquitto_pub -h "$BROKER" -t "$TOPIC" -m "$payload"
    echo "[$i/$COUNT] $TOPIC -> $payload"
    sleep 2
done
