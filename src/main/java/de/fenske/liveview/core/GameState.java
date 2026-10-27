package de.fenske.liveview.core;

import java.util.List;

/**
 * Ein Knoten im Spielbaum eines <b>Zwei-Personen-Nullsummenspiels mit
 * vollstaendiger Information</b> (Extensivform).
 *
 * <p>Abbildung auf die Theorie:
 * <ul>
 *   <li>Knoten            = Spielzustand ({@code GameState})</li>
 *   <li>Kante             = Zug ({@code Move})</li>
 *   <li>Blatt             = Endknoten ({@code isTerminal() == true})</li>
 *   <li>Knotenbeschriftung= welcher Spieler am Zug ist ({@code playerToMove()})</li>
 *   <li>Blattbeschriftung = Auszahlungsvektor ({@code utility(player)})</li>
 * </ul>
 *
 * <p><b>Wichtige Design-Regel: Zustaende sind unveraenderlich (immutable).</b>
 * {@code apply(m)} veraendert das Objekt nicht, sondern liefert einen NEUEN Zustand.
 * Dadurch braucht die Suche kein "undoMove", kann Zustaende gefahrlos in Tabellen
 * ablegen und ist parallelisierbar.
 */
public interface GameState {

    /** true, wenn das Spiel zu Ende ist (Sieg, Niederlage oder Remis). */
    boolean isTerminal();

    /** 0 oder 1 -- wer am Zug ist. Bei Endzustaenden der "verlierende" Anziehende. */
    int playerToMove();

    /** Alle legalen Zuege; bei Endzustaenden die leere Liste. */
    List<Move> legalMoves();

    /** Neuer Zustand nach Ausfuehrung von m. Das aktuelle Objekt bleibt unveraendert. */
    GameState apply(Move m);

    /**
     * Auszahlung fuer {@code player} in einem <b>Endzustand</b>:
     * +1 Sieg, -1 Niederlage, 0 Remis.
     * Nullsummeneigenschaft: utility(0) + utility(1) == 0.
     */
    int utility(int player);

    /**
     * Heuristische Bewertung eines NICHT-terminalen Zustands aus Sicht des Spielers am Zug.
     * Wird nur bei Tiefenbegrenzung benoetigt. Default: 0 (neutral).
     */
    default int evaluate() { return 0; }

    /**
     * Hashschluessel fuer die Transpositionstabelle.
     * Muss gleiche Stellungen auf gleiche Schluessel abbilden (Zobrist-Hashing).
     */
    long key();

    /** Mehrzeilige ASCII-Darstellung der Stellung. */
    String render();

    /** Einzeilige Kurzbeschreibung fuer die Spielbaumausgabe. */
    String label();
}
