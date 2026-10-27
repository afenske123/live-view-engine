# LiveView Engine

Ein kleiner, selbstgeschriebener Java-Webserver, der eine Browser-Ansicht in Echtzeit
steuert — ohne Frontend-Framework, ohne Page-Reloads. Entstanden im Rahmen von
"Programmieren 2" (THM Gießen, Live View Programming) und hier zu einem eigenständigen
Gradle-Projekt mit vier vollständig spielbaren Brettspielen ausgebaut.

![Java](https://img.shields.io/badge/Java-21-orange) ![Gradle](https://img.shields.io/badge/Build-Gradle-blue)

## Wie es funktioniert

`LiveView` startet einen kleinen HTTP-Server (`com.sun.net.httpserver.HttpServer`) und
hält eine dauerhafte [Server-Sent-Events](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)-Verbindung
zum Browser offen. Java-Code kann darüber jederzeit HTML schreiben, JavaScript ausführen
oder Skripte nachladen — die Klasse `Clerk` bietet dafür kleine, lesbare Wrapper-Methoden
(`Clerk.write(...)`, `Clerk.call(...)`, `Clerk.script(...)`). Klicks im Browser werden per
einfachem `POST` an einen von der jeweiligen View registrierten Pfad zurückgeschickt.

```
Java-Objekt  --SSE-->  Browser (script.js)  --POST-->  Java-Objekt
   (LiveView.sendServerEvent)                  (LiveView.createResponseContext)
```

Diese Basis-Infrastruktur (`LiveView`, `Clerk`, das SSE-Protokoll) stammt aus der
Programmieren-2-Übung; `web/script.js` (der Client, der das Protokoll versteht) und alle
vier Spiele in `games/`/`views/` sind eigene Weiterentwicklung.

## Spiele

Alle vier Spiele implementieren dasselbe kleine `GameState`/`Move`-Interface (Zwei-Personen-
Nullsummenspiel mit vollständiger Information) und laufen deshalb über **dieselbe**
KI-Engine unter `ai/` (Negamax mit Alpha-Beta-Pruning, Zugsortierung und Transpositionstabelle
via Zobrist-Hashing):

| Spiel | Board | Suchtiefe (KI) | Besonderheit |
|---|---|---|---|
| Tic-Tac-Toe | 3×3 | vollständig | Ursprüngliche LVP-Übung |
| Vier gewinnt | 7×6 | 6 | Heuristische Fenster-Bewertung für Tiefenbegrenzung |
| Othello/Reversi | 8×8 | 5 | Ecken-Gewichtung, Zwangs-Passen |
| Schach | 8×8 | 3 | Rochade, En-Passant, Automatik-Promotion zur Dame |

**Bekannte Vereinfachungen bei Schach:** Bauern werden immer automatisch zur Dame
befördert (keine Auswahl); Remis durch dreifache Stellungswiederholung und die
50-Züge-Regel werden nicht erkannt (nur Matt/Patt beenden die Partie).

## Starten

```bash
./gradlew run
```

Danach `http://localhost:50001` im Browser öffnen — alle vier Spiele erscheinen
untereinander, jeweils gegen den Computer spielbar (Klick auf Feld/Spalte).

## Tests

```bash
./gradlew test
```

`GamesTest` deckt zufälliges Selbstspiel für alle vier Spiele (keine Exceptions, immer ein
legaler Zug verfügbar) sowie gezielte Schach-Szenarien ab: Schäfermatt (Checkmate-Erkennung),
Rochade, En-Passant und Bauernumwandlung.

## Projektstruktur

```
src/main/java/de/fenske/liveview/
  LiveView.java, Clerk.java, SSEType.java   -- Server-Infrastruktur (LVP-Übung)
  core/          GameState.java, Move.java  -- generisches Spielbaum-Interface
  ai/            AlphaBeta, Minimax, ...     -- Suche (wiederverwendet aus einem früheren Projekt)
  games/         TicTacToe, Connect4, ...    -- Spielregeln je Spiel
  views/         *View.java                  -- Browser-Anbindung (Canvas + Klicks)
web/             index.html, script.js       -- SSE-Client
```
