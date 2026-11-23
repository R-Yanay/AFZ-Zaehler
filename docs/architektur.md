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
