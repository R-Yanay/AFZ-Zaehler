#!/usr/bin/env bash
# Zeichnet den MQTT-Verkehr des Brokers mit tcpdump auf, ohne dass tcpdump lokal installiert sein muss.
# Ein Alpine-Container teilt sich dazu das Netzwerk des Broker-Containers.
# Aufruf: ./scripts/mqtt-capture.sh start NAME   -> schreibt docs/analysen/NAME.pcap
#         ./scripts/mqtt-capture.sh stop         -> beendet sauber (Puffer wird geschrieben)
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash unter Windows: Pfade nicht umschreiben

BROKER="afz-mosquitto"
SIDECAR="afz-capture"
cd "$(dirname "$0")/../docs/analysen"
# Git Bash: Windows-Pfad (C:/...) fuer Docker; unter Linux/WSL normaler Pfad
OUT_DIR="$(pwd -W 2>/dev/null || pwd)"

case "${1:-}" in
    start)
        NAME="${2:?Name der Aufzeichnung fehlt, z. B. 03-mqtt-retain}"
        docker run -d --rm --name "$SIDECAR" --net "container:$BROKER" -v "$OUT_DIR:/cap" alpine:3 \
            sh -c "apk add -q --no-cache tcpdump && exec tcpdump -i any -U -w /cap/$NAME.pcap tcp port 1883" >/dev/null
        for _ in $(seq 1 30); do
            docker logs "$SIDECAR" 2>&1 | grep -q listening && break
            sleep 1
        done
        echo "Aufzeichnung laeuft -> docs/analysen/$NAME.pcap (stoppen mit: $0 stop)"
        ;;
    stop)
        # docker stop sendet SIGTERM: tcpdump schreibt den Puffer und schliesst die Datei korrekt
        docker stop "$SIDECAR" >/dev/null
        echo "Aufzeichnung beendet"
        ;;
    *)
        echo "Aufruf: $0 start NAME | stop" >&2
        exit 1
        ;;
esac
