# Analyse 01 – MQTT-Grundlagen

**Datei:** `01-mqtt-grundlagen.pcap` · 610 Pakete · Dauer ca. 97 min (5814 s)

## Ziel

Den Ablauf einer MQTT-Verbindung auf Paketebene verstehen: Wie meldet sich ein Client am Broker an,
wie sieht eine PUBLISH-Nachricht aus, und wie hält ein Abonnent seine Verbindung offen?

## Aufbau

```
sensor-sim.sh (mosquitto_pub) ──► Mosquitto :1883 ──► mosquitto_sub -t 'afz/#'
```

- Broker: `eclipse-mosquitto:2` aus `infra/docker-compose.yml`, Port 1883, ohne TLS und ohne Login
- Publisher: `scripts/sensor-sim.sh`, zwei Läufe mit je 3 Meldungen (bei ca. 5,7–9,8 s und 35,4–39,4 s)
- Abonnent: `mosquitto_sub`, war bereits vor dem Start der Aufzeichnung verbunden (Client-Port 51840)
- Aufgezeichnet mit `tcpdump -i any` (Link-Type „Linux cooked v2“), alles über Loopback `127.0.0.1`

## Capture-Filter

Es ist nur Verkehr auf Port 1883 enthalten, der Filter war also vermutlich `tcp port 1883`.
Anzeigefilter in Wireshark: `mqtt`, nur Nutzdaten: `mqtt.msgtype == 3`.

## Beobachtung

**Ablauf pro Meldung** (Beispiel Pakete 3–16, ein `mosquitto_pub`-Aufruf):

| Paket | Richtung | Inhalt |
|---|---|---|
| 3–5 | Pub → Broker | TCP-Handshake (SYN, SYN/ACK, ACK) |
| 6 | Pub → Broker | **CONNECT**: Protokoll „MQTT“ Level 4 (= MQTT 3.1.1), Clean Session, Keep-Alive 60 s, leere Client-ID |
| 8 | Broker → Pub | **CONNACK** `20 02 00 00`: Session Present = 0, Return Code 0 (akzeptiert) |
| 10 | Pub → Broker | **PUBLISH** auf `afz/vehicle/bus-101/door/1/count`, QoS 0 |
| 11 | Pub → Broker | **DISCONNECT** |
| 13 | Broker → Sub | Broker leitet PUBLISH an den Abonnenten weiter (ca. 0,35 ms später) |
| 15–16 | | TCP-Abbau (FIN) |

**PUBLISH im Detail** (Paket 10, Hex `30 82 01 00 20 61 66 7a …`):
- `0x30`: Pakettyp 3 (PUBLISH), DUP = 0, QoS = 0, Retain = 0
- `0x82 0x01`: Restlänge als *Variable Byte Integer* = 2 + 1 × 128 = **130 Byte**
- `0x00 0x20`: Topic-Länge 32, danach das Topic als Klartext
- Keine Packet-ID, weil sie bei QoS 0 entfällt. Danach folgen direkt 96 Byte JSON-Payload, lesbar im Klartext:
  `{"vehicleId":"bus-101","doorId":1,"boarding":1,"alighting":4,"timestamp":"2026-11-23T18:55:11Z"}`

**Overhead:** Jede Meldung braucht eine eigene TCP-Verbindung: 12 Pakete mit 1033 Byte, davon nur 96 Byte Nutzdaten (rund 9 %).

**Keep-Alive des Abonnenten:** Er sendet 58 × PINGREQ und erhält 58 × PINGRESP, im Abstand von 60,08 s.
Das entspricht dem Keep-Alive von 60 s aus dem CONNECT. Sonst ist die Verbindung während der ganzen Aufzeichnung ruhig.

**Statistik MQTT-Pakete:** 6 CONNECT, 6 CONNACK, 12 PUBLISH (6 rein, 6 weitergeleitet), 6 DISCONNECT, 58 PINGREQ, 58 PINGRESP.

**Artefakte:**
- Paket 2 „ACKed unseen segment“ und Paket 13 „Previous segment not captured“: Die Verbindung des Abonnenten bestand schon vor dem Start der Aufzeichnung. Wireshark fehlt deshalb der Anfang der TCP-Sequenz. Das ist kein Netzwerkfehler.
- Das letzte Paket der ursprünglichen Datei war nur halb geschrieben, weil tcpdump nicht sauber beendet wurde und der Puffer nicht auf die Platte kam. Die Datei wurde mit `editcap` repariert, dabei blieben alle 610 vollständigen Pakete erhalten.

## Erkenntnis

- MQTT ist sehr schlank: Der feste Header ist nur 2 Byte gross, die Länge wird platzsparend als Variable Byte Integer kodiert.
- **Ohne TLS ist alles im Klartext lesbar**: Topics, Fahrzeug-IDs und Zählwerte. Im echten Fahrzeug wäre TLS (Port 8883) mit Authentifizierung Pflicht.
- QoS 0 heisst „fire and forget“: Der Broker bestätigt nichts. Geht ein Paket verloren, merkt das niemand.
- Ein neuer `mosquitto_pub`-Aufruf pro Meldung kostet rund 10-mal mehr Bytes als die Nutzdaten selbst.
  → Der Java-Simulator bzw. das Gateway soll **eine dauerhafte Verbindung** offen halten.
- Der Broker erkennt einen verstummten Client spätestens nach 1,5 × Keep-Alive (90 s) und trennt ihn.
  Das ist relevant für die Verfügbarkeitsüberwachung (Etappe 5).
- tcpdump immer sauber beenden (SIGTERM bzw. Ctrl+C), sonst ist das Dateiende beschädigt.
  Ab jetzt `scripts/mqtt-capture.sh start|stop` verwenden.
