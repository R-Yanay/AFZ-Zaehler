# Architektur

```
 Fahrzeugsteuerung ──UDP (TRDP-aehnlich)──┐
                                          ▼
 Zaehlsensoren ─────MQTT──────────────► Bordgateway ──MQTT/REST──► Backend (Spring Boot) ──► Dashboard
```

Stand Etappe 3: Der Simulator spielt Fahrzeugsteuerung und Zählsensoren. Den Türstatus empfängt vorerst der
Monitor im Modul `gateway`. Das Backend abonniert die Zählwerte direkt beim Broker, bis das Gateway kommt (Etappe 4).

| Komponente | Modul | Etappe |
|---|---|---|
| Zaehlsensoren | `scripts/sensor-sim.sh` (Einzeltests), `simulator` | 1, 3 |
| Backend ([Doku](backend.md)) | `backend` | 2 |
| Fahrzeugsteuerung (Tuersignal, [Doku](tuerstatus-trdp.md)) | `simulator`, Telegramm in `common` | 3 |
| Tuerstatus-Monitor | `gateway` (`DoorMonitorApp`) | 3 |
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
| Türsignal | Zyklische Prozessdaten (100 ms, UDP 17224), Header wie TRDP-PD | Ausfall nach 300 ms erkennbar; Wireshark zerlegt den Header. Siehe [Analyse 03](analysen/03-trdp-tuerstatus.md) |
| Verlust beim Türsignal | Erkennen (Sequenznummer), nicht nachsenden | Ein Zustand ist nach einem Zyklus veraltet |
| Header-Prüfsumme | CRC32 über die ersten 36 Byte, selbst geprüft | Wiresharks TRDP-Dissector prüft die FCS nicht |
| Adressierung | Unicast an `localhost` (einstellbar) | Einfach unter Windows; Multicast wie im Zug wäre später möglich |
| Zählwerte | Summe pro Tür beim Schliessen, nie mehr Aus- als Einsteiger | Plausible Besetzung; Zuordnung zur Haltestelle möglich |
