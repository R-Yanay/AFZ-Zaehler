# Wireshark-Analysen

Jede Analyse bekommt eine eigene Datei mit: Ziel, Aufbau, Capture-Filter, Beobachtung, Erkenntnis.
Die zugehörigen Aufzeichnungen (`.pcap`/`.pcapng`) liegen im selben Ordner.

| Nr. | Thema | Kernaussage |
|---|---|---|
| [01](01-mqtt-grundlagen.md) | MQTT-Grundlagen | CONNECT/PUBLISH/Keep-Alive im Klartext, hoher Overhead bei einer Verbindung pro Meldung |
| [02](02-mqtt-qos.md) | QoS 0/1/2 | Tatsächliche QoS = Minimum aus Publish- und Subscribe-QoS; QoS 1 für Zählwerte |

## Aufnehmen

```bash
./scripts/mqtt-capture.sh start 03-thema   # tcpdump-Container im Netzwerk des Brokers
./scripts/sensor-sim.sh bus-101 1 3        # Verkehr erzeugen
./scripts/mqtt-capture.sh stop             # sauber beenden, sonst ist das Dateiende defekt
```

Auswerten in Wireshark oder per CLI, z. B. `tshark -r 03-thema.pcap -Y mqtt`.
