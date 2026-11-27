# Türstatus (TRDP-ähnlich, Etappe 3)

Die Fahrzeugsteuerung meldet den Zustand der Türen als **Prozessdaten (PD)** nach dem Vorbild von TRDP
(Train Real-time Data Protocol, IEC 61375-2-3): zyklisch alle 100 ms per UDP an Port 17224, ohne Verbindung und
ohne Bestätigung. Der Header folgt dem echten TRDP-PD-Layout, deshalb erkennt Wiresharks eingebauter
TRDP-Dissector die Telegramme direkt.

> Vereinfachungen gegenüber echtem TRDP: keine Zugtopologie (Topo-Zähler = 0), kein Multicast, keine
> Pull-Anfragen, keine Message Data (MD). Das Dataset-Format ist ein eigenes.

## Telegramm (UDP-Payload, 72 Byte, Big-Endian)

**PD-Header (40 Byte)** – `common/.../trdp/PdTelegram.java`

| Offset | Feld | Typ | Wert |
|---|---|---|---|
| 0 | sequenceCounter | u32 | +1 pro Telegramm, Überlauf bei 2³² |
| 4 | protocolVersion | u16 | `0x0100` |
| 6 | msgType | u16 | `0x5064` = „Pd“ |
| 8 | comId | u32 | **1001** = Türstatus |
| 12 | etbTopoCnt / opTrnTopoCnt | 2 × u32 | 0 |
| 20 | datasetLength | u32 | 32 |
| 24 | reserved, replyComId, replyIpAddress | 3 × u32 | 0 |
| 36 | headerFcs | u32 | CRC32 über Byte 0–35 |

**Dataset „Türstatus“ (32 Byte)** – `common/.../trdp/DoorStatus.java`

| Offset | Feld | Typ | Bedeutung |
|---|---|---|---|
| 0 | vehicleId | char[16] | ASCII, mit 0 aufgefüllt |
| 16 | stopId | u16 | aktuelle bzw. letzte Haltestelle |
| 18 | speed | u16 | 0.1 km/h |
| 20 | doorCount | u8 | 1–8 |
| 21 | doorMask | u8 | Bit n = Tür n+1 offen |
| 22 | reserved | u16 | 0 |
| 24 | timestamp | i64 | ms seit 1970 (UTC) |

## Überwachung beim Empfänger

`PdSupervisor` (in `common`, wird vom Gateway in Etappe 4 wiederverwendet):

| Situation | Erkennung | Reaktion |
|---|---|---|
| Sender ausgefallen | > 300 ms (3 Zyklen) kein Telegramm | Daten **ungültig**, „AUSFALL“ |
| Telegramm verloren | Sequenznummer springt um > 1 | zählen, „LUECKE“; der nächste Wert gilt |
| Duplikat / falsche Reihenfolge | Sequenznummer ≤ letzte | verwerfen, „VERALTET“ |
| Bitfehler im Header | CRC32 stimmt nicht | verwerfen, „UNGUELTIG“ |

Anders als bei MQTT QoS 1 wird nichts nachgesendet: Ein verlorener Zustand ist nach 100 ms ohnehin veraltet.

## Simulator

`simulator` spielt ein Fahrzeug als Zustandsautomat:

```
FAHRT (Trapezprofil bis 50 km/h) ─► HALT (1 s) ─► TÜREN OFFEN ─► HALT (1 s) ─► FAHRT ...
```

- Türstatus alle 100 ms per UDP
- Beim Schliessen der Türen meldet jeder Zählsensor die Summe seiner Ein- und Aussteiger per MQTT (QoS 1,
  dauerhafte Verbindung, Puffer bei Broker-Ausfall).
- Nie mehr Aussteiger als Fahrgäste, Kapazitätsgrenze, an der Endhaltestelle steigen alle aus
  → die Besetzung im Backend bleibt plausibel.

```bash
java -jar simulator/target/simulator-0.1.0-SNAPSHOT.jar --vehicle=tram-7 --drive=6s --dwell=4s
```

Optionen: `--vehicle --doors --stops --capacity --drive --dwell --cycle --udp=host:port --mqtt=url|none --drop --seed`.
`--drop=0.05` verwirft absichtlich 5 % der Telegramme, um die Überwachung zu testen.

## Monitor

```bash
java -jar gateway/target/gateway-0.1.0-SNAPSHOT.jar            # Port 17224, Timeout 300 ms
```

Zeigt nur Änderungen (Türen, Haltestelle, Stillstand/Fahrt), alle 10 s eine Statistik sowie Lücken, Ausfälle
und ungültige Telegramme.

## Wireshark-Dissector

`tools/wireshark/afz_door_status.lua` hängt sich an Wiresharks TRDP-Dissector (Tabelle `trdp.comid`, ComId 1001):

- zerlegt das Dataset in Fahrzeug, Haltestelle, Geschwindigkeit, einzelne Türbits, Zeitstempel
- prüft die **Header-FCS** (Wireshark selbst tut das nicht) → Expert-Info bei Fehler
- warnt bei „Tür offen während der Fahrt“
- Anzeigefilter z. B. `afz_door.door1 == 1`, `afz_door.any_open == 1`, `afz_trdp_fcs.ok == 0`
  (bei Ja/Nein-Feldern immer `== 1` schreiben: `afz_door.any_open` allein prüft nur, ob das Feld existiert)

Installation: Datei nach `%APPDATA%\Wireshark\plugins\` (nur eigener Benutzer) oder
`C:\Program Files\Wireshark\plugins\` (alle Benutzer) kopieren und Wireshark neu starten.
Nach Änderungen an der Datei erneut kopieren.
Ohne Installation: `tshark -X lua_script:tools/wireshark/afz_door_status.lua -r datei.pcapng`.
