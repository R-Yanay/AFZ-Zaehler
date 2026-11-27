# Analyse 03 – Türstatus per UDP (TRDP-ähnlich) im Vergleich zu MQTT

**Datei:** `03-trdp-tuerstatus.pcapng` · 480 Pakete · Dauer 42,4 s

## Ziel

Das zyklische UDP-Türsignal auf Paketebene ansehen und mit dem ereignisgesteuerten MQTT-Verkehr der
Zählsensoren vergleichen: Takt, Grösse, Bestätigungen. Dazu prüfen, ob der eigene Lua-Dissector die
Telegramme korrekt zerlegt.

## Aufbau

```
Simulator tram-7 ──UDP :17224 (PD, 100 ms)──► Monitor (gateway)
        └────────MQTT :1883 (QoS 1)────────► Mosquitto ──► Backend
```

- Simulator: `--vehicle=tram-7 --stops=4 --drive=6s --dwell=4s --seed=3`, 2 Türen
- Monitor: `DoorMonitorApp`, Backend mit In-Memory-Datenbank, Broker `afz-mosquitto`
- Alles unter Windows auf `127.0.0.1`. Aufgezeichnet mit dem **Npcap-Loopback-Adapter**:
  `tshark -i \Device\NPF_Loopback -f "udp port 17224 or tcp port 1883" -a duration:45`
- Ausgewertet mit `-X lua_script:tools/wireshark/afz_door_status.lua`

## Capture-Filter

`udp port 17224 or tcp port 1883`. Anzeigefilter: `trdp`, `afz_door.any_open == 1`, `mqtt`.

## Beobachtung

**Protokollverteilung**

| | Pakete | Bytes | Anteil Bytes |
|---|---|---|---|
| UDP / TRDP-PD | 425 | 44 200 | 91 % |
| TCP / MQTT (inkl. ACKs) | 55 | 4 236 | 9 % |

**Takt:** 424 Abstände zwischen den PD-Telegrammen, im Mittel **100,00 ms**, min. 94,2 ms, max. 106,0 ms,
Standardabweichung 1,0 ms. Jedes Telegramm ist 104 Byte gross
(4 Byte Loopback + 20 IP + 8 UDP + 40 TRDP-Header + 32 Dataset).

**Dissector:** Wiresharks eingebauter Dissector erkennt Typ „Pd“, ComId 1001 und die Sequenznummern 0, 1, 2 …
Der Lua-Dissector zeigt darunter z. B. für Paket 70:
`tram-7, Haltestelle 1, 0.0 km/h, Tür 1 offen: True, Tür 2 offen: True`. Die Header-FCS ist bei allen
Telegrammen korrekt (`afz_trdp_fcs.ok == 1`).

**Türwechsel** (nur Pakete, in denen sich `doorMask` ändert):

| Paket | Zeit | Seq | Halt | Türen |
|---|---|---|---|---|
| 70 | 6,9 s | 69 | 1 | beide offen |
| 110 | 10,9 s | 109 | 1 | zu |
| 213 | 18,9 s | 189 | 2 | beide offen |
| 253 | 22,9 s | 229 | 2 | zu |
| 349 | 30,9 s | 309 | 3 | beide offen |
| 389 | 34,9 s | 349 | 3 | zu |

Zwischen „offen“ und „zu“ liegen genau 40 Telegramme bzw. 4,0 s (`--dwell=4s`). Die Paketnummern laufen der
Sequenznummer davon, weil dazwischen MQTT-Pakete liegen.

**MQTT:** Erst beim Schliessen der Türen (11,26 s, 22,90 s, 34,90 s) entstehen je 2 PUBLISH (eine pro Tür)
über **eine** dauerhafte Verbindung des Simulators. Der Broker leitet beide an das Backend weiter, jeweils
mit QoS 1 und PUBACK auf beiden Teilstrecken. Die Packet-IDs sind pro Verbindung verschieden
(Simulator 1, 2 …, Broker → Backend 16, 17 …), wie in Analyse 02.

**Auffällig:** Das Backend bestätigt das allererste PUBLISH erst nach **430 ms** (Paket 124 → 137),
alle späteren nach rund 10 ms. Weil das PUBACK erst nach dem Speichern rausgeht (siehe `docs/backend.md`),
misst man hier direkt die Verarbeitungszeit. Der erste Zugriff auf Hibernate/H2 braucht etwas Aufwärmzeit.

## Erkenntnis

- **Zyklisch statt ereignisgesteuert:** Der Türstatus belegt 91 % der Bytes, obwohl sich die Türen in 42 s nur
  sechsmal ändern. Das ist der Preis dafür, dass der Empfänger einen Ausfall nach 300 ms sicher erkennt.
  MQTT sendet nur bei Ereignissen, bemerkt ein stilles Gerät aber erst über das Keep-Alive (bis 90 s, Analyse 01).
- **UDP hat keinen Overhead pro Nachricht:** kein Handshake, kein ACK, 72 Byte Nutzlast pro Telegramm.
  Bei MQTT kommen auf 2 Zählwerte 4 PUBLISH und 4 PUBACK plus TCP-ACKs.
- **Der Takt ist stabil** (±6 ms bei Java unter Windows). Für Türsignale reicht das. Für harte Echtzeit nimmt man
  in echten Fahrzeugen ein Echtzeit-Betriebssystem und das Fahrzeugnetz.
- **Die Sequenznummer ist die einzige Verlusterkennung:** Wireshark und der Monitor erkennen Lücken nur über sie.
  Mit `--drop=0.05` zeigt der Monitor „LUECKE“, mit einem gekippten Bit „Header-CRC falsch“.
- **Eigene Dissectors lohnen sich:** Statt 32 Byte Hex sieht man Fahrzeug, Halt und Türen und kann direkt filtern,
  z. B. `afz_door.door1 == 1 && afz_door.speed > 0` für „Tür offen während der Fahrt“.
- **Gateway (Etappe 4):** Türstatus und Zählwerte kommen über verschiedene Wege mit verschiedener Zeitbasis.
  Das Gateway muss sie über Fahrzeug und Zeitstempel zusammenführen. Zählwerte zu einer Haltestelle, an der laut
  Türstatus keine Tür offen war, sind dann unplausibel.
