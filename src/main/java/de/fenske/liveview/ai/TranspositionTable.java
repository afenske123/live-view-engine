package de.fenske.liveview.ai;

import de.fenske.liveview.core.Move;

import java.util.HashMap;
import java.util.Map;

/**
 * Transpositionstabelle: Zwischenspeicher fuer bereits bewertete Stellungen.
 *
 * <p><b>Warum?</b> Der "Spielbaum" ist in Wahrheit ein <b>gerichteter azyklischer
 * Graph (DAG)</b>: Verschiedene Zugreihenfolgen fuehren zur selben Stellung
 * (= <i>Transposition</i>). Bei Tic-Tac-Toe erreicht man z.B. dieselbe Stellung
 * ueber X:Mitte, O:Ecke oder ueber O:Ecke, X:Mitte. Ohne Tabelle rechnet die
 * Suche jeden dieser Pfade neu -- mit Tabelle nur einmal.
 * Das ist nichts anderes als <b>Memoisierung</b> / dynamische Programmierung.
 *
 * <p><b>Flags.</b> Mit Alpha-Beta ist ein gespeicherter Wert oft nur eine Schranke:
 * <ul>
 *   <li>EXACT -- der Wert ist exakt (alpha &lt; v &lt; beta)</li>
 *   <li>LOWER -- der wahre Wert ist &gt;= v (Beta-Schnitt, "fail high")</li>
 *   <li>UPPER -- der wahre Wert ist &lt;= v (kein Zug hat alpha verbessert, "fail low")</li>
 * </ul>
 * Wer das ignoriert und immer EXACT annimmt, bekommt subtil falsche Ergebnisse!
 */
public final class TranspositionTable {

    public enum Flag { EXACT, LOWER, UPPER }

    /**
     * @param depth Resttiefe, mit der der Eintrag berechnet wurde
     * @param value gespeicherter Wert (aus Sicht des Spielers am Zug, Negamax)
     * @param flag  Art der Schranke
     * @param best  bester Zug in dieser Stellung (fuer die Zugsortierung)
     */
    public record Entry(int depth, int value, Flag flag, Move best) { }

    private final Map<Long, Entry> table = new HashMap<>();

    public Entry get(long key)                 { return table.get(key); }
    public void put(long key, Entry e) {
        Entry old = table.get(key);
        // Ersetzen nur, wenn der neue Eintrag mindestens so tief gerechnet wurde
        if (old == null || e.depth() >= old.depth()) table.put(key, e);
    }
    public int size()                          { return table.size(); }
    public void clear()                        { table.clear(); }
}
