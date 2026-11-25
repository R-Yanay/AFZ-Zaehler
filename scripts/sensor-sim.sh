#!/usr/bin/env bash
# Simuliert einen Zaehlsensor an einer Fahrzeugtuer und publiziert Zaehlwerte via MQTT.
# Nutzt mosquitto_pub, falls installiert (sudo apt install mosquitto-clients),
# sonst die Clients im Broker-Container (docker exec afz-mosquitto ...).
# Aufruf: ./scripts/sensor-sim.sh [FAHRZEUG] [TUER] [ANZAHL_MELDUNGEN]
# Umgebungsvariablen: MQTT_HOST (Default localhost), MQTT_QOS (0, 1 oder 2, Default 0),
#                     SIM_INTERVAL (Sekunden zwischen Meldungen, Default 2)
set -euo pipefail

VEHICLE="${1:-bus-101}"
DOOR="${2:-1}"
COUNT="${3:-10}"
BROKER="${MQTT_HOST:-localhost}"
QOS="${MQTT_QOS:-0}"
INTERVAL="${SIM_INTERVAL:-2}"
CONTAINER="afz-mosquitto"
TOPIC="afz/vehicle/${VEHICLE}/door/${DOOR}/count"

if [[ ! "$QOS" =~ ^[012]$ ]]; then
    echo "MQTT_QOS muss 0, 1 oder 2 sein (ist: $QOS)" >&2
    exit 1
fi

if command -v mosquitto_pub >/dev/null 2>&1; then
    PUB=(mosquitto_pub -h "$BROKER")
else
    # Im Container ist der Broker unter localhost erreichbar
    PUB=(docker exec "$CONTAINER" mosquitto_pub -h localhost)
fi

for ((i = 1; i <= COUNT; i++)); do
    boarding=$((RANDOM % 6))
    alighting=$((RANDOM % 6))
    ts=$(date -u +%Y-%m-%dT%H:%M:%SZ)
    payload="{\"vehicleId\":\"${VEHICLE}\",\"doorId\":${DOOR},\"boarding\":${boarding},\"alighting\":${alighting},\"timestamp\":\"${ts}\"}"
    "${PUB[@]}" -q "$QOS" -t "$TOPIC" -m "$payload"
    echo "[$i/$COUNT] QoS $QOS $TOPIC -> $payload"
    if ((i < COUNT)); then
        sleep "$INTERVAL"
    fi
done
