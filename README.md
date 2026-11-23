# AFZ-Lab – Simulation einer automatischen Fahrgastzählung

Lern- und Portfolio-Projekt: Ein Fahrzeug mit Türsteuerung, Zählsensoren und Bordgateway wird simuliert.
Ein Backend prüft die Daten auf Plausibilität und überwacht die Verfügbarkeit der Geräte.

**Technologien:** Java 25, Maven, Spring Boot, MQTT (Mosquitto), UDP (TRDP-ähnlich), Linux (WSL2), Bash, Docker, Wireshark

> Hinweis: TRDP und ITxPT werden hier vereinfacht nachgebildet, um die Konzepte zu lernen und zu zeigen.

## Struktur

| Ordner | Inhalt |
|---|---|
| `common` | Gemeinsame Datenmodelle |
| `simulator` | Türsteuerung (UDP) und Zählsensoren (MQTT) |
| `gateway` | Bordgateway |
| `backend` | Spring-Boot-Backend (ab Etappe 2) |
| `scripts` | Bash-Skripte für Simulation, Betrieb und Analyse |
| `infra` | Docker Compose, Mosquitto-Konfiguration |
| `docs` | Architektur, Protokolle, Konzepte, Wireshark-Analysen |

## Schnellstart

```bash
# MQTT-Broker starten (Docker Desktop muss laufen)
docker compose -f infra/docker-compose.yml up -d

# In WSL: Nachrichten mitlesen
mosquitto_sub -h localhost -t 'afz/#' -v

# In einem zweiten WSL-Fenster: Sensor simulieren
./scripts/sensor-sim.sh bus-101 1 10
```

## Fortschritt

- [x] Etappe 0: Setup und Projektstruktur
- [ ] Etappe 1: MQTT-Grundlagen und erste Wireshark-Analyse
- [ ] Etappe 2: Spring-Boot-Backend
- [ ] Etappe 3: TRDP-ähnliches Türsignal
- [ ] Etappe 4: Gateway-Logik
- [ ] Etappe 5: Qualität und Verfügbarkeit
- [ ] Etappe 6: Betrieb und Automatisierung
- [ ] Etappe 7: Störfallanalysen
- [ ] Etappe 8: Abschluss und Demo
