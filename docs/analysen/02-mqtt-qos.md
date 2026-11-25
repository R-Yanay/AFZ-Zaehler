# Analyse 02 – MQTT Quality of Service (QoS 0, 1, 2)

**Datei:** `02-mqtt-qos.pcap` · 72 Pakete · Dauer 7,05 s

## Ziel

Die drei QoS-Stufen auf Paketebene vergleichen: Welche Bestätigungen werden ausgetauscht,
und was bekommt ein Abonnent, der eine tiefere QoS-Stufe angefordert hat?

## Aufbau

```
sensor-sim.sh (QoS 0 / 1 / 2) ──► Mosquitto :1883 ──┬──► backend-sub (QoS 2, im Container, ::1)
                                                    └──► mosquitto_sub (QoS 0, vom Host, 172.18.0.1)
```

- Aufnahme mit `scripts/mqtt-capture.sh`: Ein tcpdump-Container teilt sich das Netzwerk des Brokers
  und zeichnet mit `tcpdump -i any -U` auf.
- Abonnent 1: `mosquitto_sub -q 2 -i backend-sub -t 'afz/#' -C 3` im Broker-Container (IPv6-Loopback `::1`)
- Abonnent 2: ein `mosquitto_sub` vom Host mit QoS 0, der bereits lief (über Docker-NAT `172.18.0.1`)
- Publisher: je eine Meldung mit `MQTT_QOS=0`, `1` und `2` im Abstand von etwa 2 s

## Capture-Filter

`tcp port 1883`. Anzeigefilter: `mqtt`, für die Bestätigungen `mqtt.msgtype >= 4 && mqtt.msgtype <= 7`.

## Beobachtung

**Anmeldung des Abonnenten** (Pakete 4–9): CONNECT mit Client-ID `backend-sub`, dann SUBSCRIBE (id=1) auf `afz/#` mit
gewünschter QoS 2 und SUBACK (id=1), in dem der Broker QoS 2 gewährt.

**Vergleich der Stufen** (nur MQTT-Pakete zwischen Publisher, Broker und `backend-sub`):

| | Publisher → Broker | Broker → backend-sub | MQTT-Pakete pro Meldung |
|---|---|---|---|
| **QoS 0** (Paket 18) | PUBLISH | PUBLISH | 2 |
| **QoS 1** (Pakete 37–43) | PUBLISH (id=1) → PUBACK | PUBLISH (id=1) → PUBACK | 4 |
| **QoS 2** (Pakete 55–68) | PUBLISH (id=1) → PUBREC → PUBREL → PUBCOMP | PUBLISH (id=2) → PUBREC → PUBREL → PUBCOMP | 8 |

- Ab QoS 1 hat die PUBLISH-Nachricht eine **Packet-ID** und ist dadurch 2 Byte länger (Restlänge 132 statt 130).
- Die Packet-ID gilt **pro Verbindung**: Der Publisher sendet id=1, der Broker vergibt gegenüber `backend-sub` eine eigene id=2.
- Bei QoS 2 leitet der Broker die Nachricht erst weiter, nachdem PUBREL vom Publisher angekommen ist (Paket 57, dann Paket 60).
  Das Weiterleiten läuft als eigenständiger QoS-2-Ablauf zwischen Broker und Abonnent.
- Der Ablauf ist sehr schnell: QoS 2 dauert vom PUBLISH bis zum letzten PUBCOMP 0,7 ms (lokal ohne Netzwerkverzögerung).

**Herabstufen:** Der zweite Abonnent (`172.18.0.1:48650`, QoS 0) erhält alle drei Meldungen als **QoS 0**
(Pakete 20, 38, 58), auch die mit QoS 1 und 2 veröffentlichten. Er sendet nur PINGREQ (Paket 27), keine Bestätigungen.

## Erkenntnis

- **Die tatsächliche QoS ist das Minimum aus Publish-QoS und Subscribe-QoS.** Die QoS-Stufe gilt für jede Teilstrecke
  einzeln (Publisher ↔ Broker, Broker ↔ Abonnent), nicht durchgehend.
- QoS 1 garantiert „mindestens einmal“: Fehlt das PUBACK, wird die Nachricht erneut gesendet, und es kann
  **Duplikate** geben. Der Empfänger muss also idempotent sein, z. B. Duplikate über Fahrzeug, Tür und Zeitstempel erkennen.
- QoS 2 garantiert „genau einmal“, braucht dafür aber 4 statt 2 Pakete pro Teilstrecke und hält Zustand im Broker.
- **Für die Fahrgastzählung:** Zählwerte sollen nicht verloren gehen, Duplikate lassen sich im Backend erkennen.
  → **QoS 1** ist der sinnvolle Kompromiss. Das Backend (Etappe 2) muss dabei selbst mit QoS 1 abonnieren,
  sonst wird auf der letzten Teilstrecke auf QoS 0 herabgestuft.
