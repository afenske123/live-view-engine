package de.fenske.liveview.core;

/**
 * Ein Zug im Spielbaum -- also eine Kante von einem Knoten zu einem Kindknoten.
 *
 * <p>Marker-Interface mit einer Methode: Jede Spielimplementierung liefert ihre
 * eigene Zug-Repraesentation (z.B. Feldnummer bei Tic-Tac-Toe, (Haufen, Anzahl) bei Nim).
 */
public interface Move {
    /** Kurze, lesbare Bezeichnung fuer Ausgaben, z.B. "B2" oder "H1-3". */
    String label();
}
