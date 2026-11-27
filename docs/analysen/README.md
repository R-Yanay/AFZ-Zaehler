# Wireshark-Analysen

Jede Analyse bekommt eine eigene Datei mit: Ziel, Aufbau, Capture-Filter, Beobachtung, Erkenntnis.
Die zugehörigen Aufzeichnungen (`.pcap`/`.pcapng`) liegen im selben Ordner.

| Nr. | Thema | Kernaussage |
|---|---|---|
| [01](01-mqtt-grundlagen.md) | MQTT-Grundlagen | CONNECT/PUBLISH/Keep-Alive im Klartext, hoher Overhead bei einer Verbindung pro Meldung |
| [02](02-mqtt-qos.md) | QoS 0/1/2 | Tatsächliche QoS = Minimum aus Publish- und Subscribe-QoS; QoS 1 für Zählwerte |
| [03](03-trdp-tuerstatus.md) | Türstatus UDP vs. MQTT | Zyklisches PD-Signal: stabiler 100-ms-Takt, 91 % der Bytes, Ausfall nach 300 ms erkennbar |

## Aufnehmen

**MQTT im Broker-Container** (geht auch ohne Npcap):

```bash
./scripts/mqtt-capture.sh start 03-thema   # tcpdump-Container im Netzwerk des Brokers
./scripts/sensor-sim.sh bus-101 1 3        # Verkehr erzeugen
./scripts/mqtt-capture.sh stop             # sauber beenden, sonst ist das Dateiende defekt
```

**Alles auf dem Windows-Rechner** (Türstatus per UDP und MQTT über den Docker-Port), mit dem Npcap-Loopback-Adapter:

```powershell
& "C:\Program Files\Wireshark\tshark.exe" -i \Device\NPF_Loopback -f "udp port 17224 or tcp port 1883" -a duration:45 -w docs\analysen\04-thema.pcapng
```

Auswerten in Wireshark oder per CLI, z. B. `tshark -r 03-thema.pcap -Y mqtt`.
Für den Türstatus den Lua-Dissector laden: `tshark -X lua_script:tools/wireshark/afz_door_status.lua -r ...`
(oder einmalig nach `%APPDATA%\Wireshark\plugins\` kopieren).
