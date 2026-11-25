# Architektur

```
 Fahrzeugsteuerung ──UDP (TRDP-aehnlich)──┐
                                          ▼
 Zaehlsensoren ─────MQTT──────────────► Bordgateway ──MQTT/REST──► Backend (Spring Boot) ──► Dashboard
```

| Komponente | Modul | Etappe |
|---|---|---|
| Zaehlsensoren | `scripts/sensor-sim.sh`, `simulator` | 1 |
| Backend | `backend` | 2 |
| Fahrzeugsteuerung (Tuersignal) | `simulator` | 3 |
| Bordgateway | `gateway` | 4 |

## Entscheidungen

| Thema | Entscheidung | Begründung |
|---|---|---|
| MQTT-Topic | `afz/vehicle/<fahrzeug>/door/<tür>/count`, Payload JSON wie `CountEvent` | Abonnieren pro Fahrzeug/Tür per Wildcard möglich |
| MQTT-QoS für Zählwerte | **QoS 1** (Publisher und Backend-Abo) | Kein Datenverlust; Duplikate erkennt das Backend. Siehe [Analyse 02](analysen/02-mqtt-qos.md) |
| MQTT-Verbindung | Dauerhaft offen statt pro Meldung neu | Pro Meldung ~1 kB Overhead bei 96 Byte Nutzdaten. Siehe [Analyse 01](analysen/01-mqtt-grundlagen.md) |
