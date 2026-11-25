---
name: etappe-abschliessen
description: Schliesst eine Projekt-Etappe ab (README-Fortschritt, Architektur-Doku, Build-Check, Commit-Vorschlag). Nur auf Aufruf durch den Benutzer.
disable-model-invocation: true
---

Etappe `$ARGUMENTS` abschliessen (z. B. `1`).

1. `git status` und `git log --oneline` ansehen: Was wurde in dieser Etappe gemacht?
2. Build pruefen: `mvn -q compile` (bzw. `mvn -q verify`, sobald Tests existieren). Bei Fehlern abbrechen und melden.
3. `README.md`: Checkbox der Etappe unter "Fortschritt" abhaken. Schnellstart/Struktur nur anpassen, wenn sich wirklich etwas geaendert hat.
4. `docs/architektur.md`: Komponenten-Tabelle und Diagramm aktualisieren, falls neue Komponenten oder Protokolle dazugekommen sind.
5. Wurden in `docs/analysen/` Aufzeichnungen ohne zugehoerige `.md`-Analyse abgelegt? Falls ja, darauf hinweisen (Skill `/wireshark-analyse`).
6. Commit-Nachricht vorschlagen im Stil `Etappe <n>: <Stichworte>` und die Dateien auflisten. **Erst nach ausdruecklichem OK committen.**
