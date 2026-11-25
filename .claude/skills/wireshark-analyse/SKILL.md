---
name: wireshark-analyse
description: Dokumentiert eine Netzwerk-Aufzeichnung (.pcap/.pcapng) aus docs/analysen/ als Analyse-Datei nach der Projektvorlage. Nutzen, wenn eine neue Aufzeichnung ausgewertet oder beschrieben werden soll.
---

Ziel: Zur Aufzeichnung `$ARGUMENTS` (z. B. `01-mqtt-grundlagen`) die Datei `docs/analysen/<name>.md` anlegen.

1. Vorlage aus `docs/analysen/README.md` lesen. Pflichtabschnitte: **Ziel, Aufbau, Capture-Filter, Beobachtung, Erkenntnis**.
2. Pruefen, ob `tshark` verfuegbar ist (`where tshark`, typisch `C:\Program Files\Wireshark\tshark.exe`).
   - Falls ja, die Aufzeichnung auswerten, z. B.:
     - Uebersicht: `tshark -r <datei> -q -z io,phs`
     - MQTT-Pakete: `tshark -r <datei> -Y mqtt -T fields -e frame.time_relative -e ip.src -e ip.dst -e mqtt.msgtype -e mqtt.topic -e mqtt.msg`
     - Konversationen: `tshark -r <datei> -q -z conv,tcp`
   - Falls nein, den Benutzer nach Aufbau und Beobachtungen fragen und nur das Dokumentieren, nichts erfinden.
3. Aufbau aus dem Projekt ableiten: Broker `infra/docker-compose.yml` (Port 1883, unverschluesselt), Publisher `scripts/sensor-sim.sh`, Topic-Schema `afz/vehicle/<id>/door/<n>/count`.
4. Beobachtung mit konkreten Werten belegen (Paketnummern, MQTT-Nachrichtentypen wie CONNECT/CONNACK/PUBLISH/DISCONNECT, QoS, Groessen). Erkenntnis kurz und auf das Lernziel bezogen.
5. Datei in Markdown mit Umlauten schreiben, knapp halten. Nicht committen.
