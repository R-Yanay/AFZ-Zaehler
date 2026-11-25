# Backend (Etappe 2)

Spring Boot 4.2 · Java 25 · Eclipse Paho (MQTT 3.1.1) · Spring Data JPA / Hibernate 7 · H2

## Ablauf einer Meldung

```
MQTT afz/vehicle/+/door/+/count (QoS 1)
   │
   ▼
MqttCountSubscriber ──► CountIngestService
                           1. CountMessageParser    Topic + JSON → CountEvent        → UNGUELTIGES_FORMAT
                           2. PlausibilityChecker   Grenzwerte, Zeitstempel           → UNPLAUSIBEL
                           3. Duplikatpruefung      Fahrzeug + Tür + Messzeitpunkt    → DUPLIKAT
                           4. speichern             count_record  /  rejected_message
   │
   ▼
PUBACK an den Broker (erst nach dem Speichern)
```

## Plausibilitätsregeln

| Regel | Grund | Konfiguration |
|---|---|---|
| Topic hat das Format `afz/vehicle/<id>/door/<n>/count` | sonst unbekannte Quelle | – |
| JSON vollständig, Felder mit richtigem Typ | `"3"` statt `3` ist ein Sensorfehler | – |
| Fahrzeug/Tür im Topic = im Payload | falsch konfigurierter Sensor | – |
| Ein- und Aussteiger ≥ 0 | physikalisch unmöglich (`CountEvent`) | – |
| Ein- bzw. Aussteiger ≤ 50 pro Tür und Meldung | Zählfehler | `afz.plausibility.max-persons-per-door` |
| Zeitstempel höchstens 1 min in der Zukunft | Uhr des Sensors falsch | `afz.plausibility.max-future-skew` |
| Zeitstempel höchstens 24 h alt | veraltete Daten | `afz.plausibility.max-age` |
| Fahrzeug + Tür + Zeitstempel noch nicht vorhanden | Duplikat durch QoS 1 | Unique-Constraint in `count_record` |

Abgelehnte Meldungen werden **mit Originalpayload und Grund** in `rejected_message` gespeichert, nicht verworfen.

## REST-API

**Startseite:** http://localhost:8080/ listet alle Endpunkte mit klickbaren Links. Die Liste wird automatisch aus der
OpenAPI-Beschreibung erzeugt. **Swagger UI** zum Ausprobieren: http://localhost:8080/swagger-ui.html,
OpenAPI als JSON/YAML: `/v3/api-docs`, `/v3/api-docs.yaml` (springdoc 3.2).

| Methode | Pfad | Inhalt |
|---|---|---|
| GET | `/api/vehicles` | Fahrzeuge mit Zählwerten |
| GET | `/api/vehicles/{id}/counts?limit=50` | Letzte Zählwerte, neueste zuerst |
| GET | `/api/vehicles/{id}/occupancy` | Summen und Besetzung (Ein- minus Aussteiger); `plausible=false` bei negativer Besetzung; 404 ohne Daten |
| GET | `/api/rejected?limit=50` | Abgelehnte Meldungen mit Grund |
| GET | `/actuator/health` | Status von Anwendung und Datenbank |

Beispiel:

```bash
curl localhost:8080/api/vehicles/bus-101/occupancy
# {"vehicleId":"bus-101","boarding":12,"alighting":11,"occupancy":1,"plausible":true,"messages":5,...}
```

## Starten

```bash
docker compose -f infra/docker-compose.yml up -d      # Broker
mvn -q package                                        # baut alle Module inkl. Tests und Checkstyle
cd backend && java -jar target/backend-0.1.0-SNAPSHOT.jar
MQTT_QOS=1 ./scripts/sensor-sim.sh bus-101 1 5        # in einem zweiten Fenster, im Projektordner
```

Die H2-Datenbank liegt in `data/` relativ zum Startordner (gitignored). Zurücksetzen: Backend stoppen, `data/` löschen.
Ist der Broker beim Start nicht erreichbar, läuft das Backend trotzdem und versucht es alle 5 s erneut.

## Getestetes Verhalten

- 22 automatische Tests (`mvn verify`): Parser, Plausibilitätsregeln, Duplikaterkennung, REST-API, Health,
  Startseite und OpenAPI.
  MQTT ist in den Tests abgeschaltet, die Datenbank ist H2 in-memory.
- Manuell mit echtem Broker: 5 gültige Meldungen werden akzeptiert. Die Meldungen mit 80 Einsteigern und mit kaputtem JSON werden mit Grund abgelehnt.
- **Offline-Pufferung:** Backend gestoppt, 3 Meldungen mit QoS 1 gesendet, Backend gestartet. Danach lagen alle 3 Meldungen vor.
  Das funktioniert, weil eine persistente Session (`cleanSession=false`, feste Client-ID `afz-backend`) zusammen mit QoS 1 dafür sorgt, dass der Broker die Meldungen aufhebt.

## Bekannte Grenzen (spätere Etappen)

- Der Simulator würfelt Ein- und Aussteiger unabhängig. Die Besetzung kann deshalb negativ werden (`plausible=false`).
  Erst das Gateway (Etappe 4) zählt pro Haltestelle und Türöffnung.
- Der Broker puffert nur im Arbeitsspeicher: Ein Neustart von Mosquitto verwirft die gepufferten Meldungen. Abhilfe wäre `persistence true` (Etappe 6).
- Ein Duplikat mit gleichem Zeitstempel, aber anderen Werten wird ebenfalls als Duplikat abgelehnt. Ein Sensor mit
  Sekundenauflösung könnte so eine echte zweite Meldung verlieren. Eine Sequenznummer im Payload wäre robuster.
