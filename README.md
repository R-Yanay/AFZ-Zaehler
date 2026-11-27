# AFZ-Lab – Simulation einer automatischen Fahrgastzählung

Lern- und Portfolio-Projekt: Ein Fahrzeug mit Türsteuerung, Zählsensoren und Bordgateway wird simuliert.
Ein Backend prüft die Daten auf Plausibilität und überwacht die Verfügbarkeit der Geräte.

**Technologien:** Java 25, Maven, Spring Boot, MQTT (Mosquitto), UDP (TRDP-ähnlich), Linux (WSL2), Bash, Docker, Wireshark

> Hinweis: TRDP und ITxPT werden hier vereinfacht nachgebildet, um die Konzepte zu lernen und zu zeigen.

## Struktur

| Ordner | Inhalt |
|---|---|
| `common` | Gemeinsame Datenmodelle, TRDP-ähnliches PD-Telegramm und dessen Überwachung |
| `simulator` | Fahrzeug: Türsteuerung (UDP, TRDP-ähnlich) und Zählsensoren (MQTT) ([Doku](docs/tuerstatus-trdp.md)) |
| `gateway` | Bordgateway (Etappe 4); bis dahin Monitor für den Türstatus |
| `backend` | Spring-Boot-Backend: MQTT-Empfang, Plausibilitätsprüfung, REST-API ([Doku](docs/backend.md)) |
| `scripts` | Bash-Skripte für Simulation, Betrieb und Analyse |
| `infra` | Docker Compose, Mosquitto-Konfiguration |
| `tools` | Wireshark-Dissector (Lua) für den Türstatus |
| `docs` | Architektur, Protokolle, Konzepte, Wireshark-Analysen |

## Schnellstart

```bash
# MQTT-Broker starten (Docker Desktop muss laufen)
docker compose -f infra/docker-compose.yml up -d

# Nachrichten mitlesen (WSL mit mosquitto-clients, oder ohne Installation über den Container)
mosquitto_sub -h localhost -t 'afz/#' -v
docker exec afz-mosquitto mosquitto_sub -t 'afz/#' -v

# In einem zweiten Fenster (WSL oder Git Bash): Sensor simulieren, optional mit QoS
./scripts/sensor-sim.sh bus-101 1 10
MQTT_QOS=1 ./scripts/sensor-sim.sh bus-101 1 10

# Alles bauen (inkl. Tests und Checkstyle)
mvn -q package

# Backend starten (Übersicht aller Endpunkte auf http://localhost:8080/)
cd backend && java -jar target/backend-0.1.0-SNAPSHOT.jar

# Je in einem eigenen Fenster, im Projektordner: Türstatus-Monitor und Fahrzeugsimulator
java -jar gateway/target/gateway-0.1.0-SNAPSHOT.jar
java -jar simulator/target/simulator-0.1.0-SNAPSHOT.jar --vehicle=tram-7

# MQTT-Verkehr für Wireshark aufzeichnen
./scripts/mqtt-capture.sh start 03-thema
./scripts/mqtt-capture.sh stop
```

Wireshark-Analysen: [`docs/analysen/`](docs/analysen/README.md)

## Fortschritt

- [x] Etappe 0: Setup und Projektstruktur
- [x] Etappe 1: MQTT-Grundlagen und erste Wireshark-Analyse
- [x] Etappe 2: Spring-Boot-Backend
- [x] Etappe 3: TRDP-ähnliches Türsignal
- [ ] Etappe 4: Gateway-Logik
- [ ] Etappe 5: Qualität und Verfügbarkeit
- [ ] Etappe 6: Betrieb und Automatisierung
- [ ] Etappe 7: Störfallanalysen
- [ ] Etappe 8: Abschluss und Demo
