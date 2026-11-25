# Architektur

```
 Fahrzeugsteuerung ──UDP (TRDP-aehnlich)──┐
                                          ▼
 Zaehlsensoren ─────MQTT──────────────► Bordgateway ──MQTT/REST──► Backend (Spring Boot) ──► Dashboard
```

Bis zum Gateway (Etappe 4) abonniert das Backend die Sensor-Topics direkt beim Broker.

| Komponente | Modul | Etappe |
|---|---|---|
| Zaehlsensoren | `scripts/sensor-sim.sh`, `simulator` | 1 |
| Backend ([Doku](backend.md)) | `backend` | 2 |
| Fahrzeugsteuerung (Tuersignal) | `simulator` | 3 |
| Bordgateway | `gateway` | 4 |

## Entscheidungen

| Thema | Entscheidung | Begründung |
|---|---|---|
| MQTT-Topic | `afz/vehicle/<fahrzeug>/door/<tür>/count`, Payload JSON wie `CountEvent` | Abonnieren pro Fahrzeug/Tür per Wildcard möglich |
| MQTT-QoS für Zählwerte | **QoS 1** (Publisher und Backend-Abo) | Kein Datenverlust; Duplikate erkennt das Backend. Siehe [Analyse 02](analysen/02-mqtt-qos.md) |
| MQTT-Verbindung | Dauerhaft offen statt pro Meldung neu | Pro Meldung ~1 kB Overhead bei 96 Byte Nutzdaten. Siehe [Analyse 01](analysen/01-mqtt-grundlagen.md) |
| MQTT-Session des Backends | Feste Client-ID, `cleanSession=false` | Broker puffert QoS-1-Meldungen, solange das Backend offline ist |
| Bestätigung | PUBACK erst nach dem Speichern | Ende-zu-Ende „mindestens einmal“: Bei einem Fehler stellt der Broker erneut zu |
| MQTT-Bibliothek | Eclipse Paho (direkt, ohne Spring Integration) | Verbindung, Abo und Bestätigung bleiben im Code sichtbar |
| Datenhaltung | Spring Data JPA mit H2 (Datei) | Keine zusätzliche Infrastruktur; Wechsel zu PostgreSQL nur per Konfiguration |
| Ungültige Meldungen | Mit Grund in `rejected_message` speichern statt verwerfen | Fehler am Sensor bleiben nachvollziehbar |
