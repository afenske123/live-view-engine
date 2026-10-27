package de.fenske.liveview.ai;

import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;

/**
 * Minimax-Suche ohne jede Optimierung -- die direkte Umsetzung der
 * <b>Rueckwaertsinduktion</b> aus der Theorie.
 *
 * <p>Zwei Formulierungen, die dasselbe berechnen:
 *
 * <p><b>(a) Klassisch (MAX/MIN).</b> Spieler 0 maximiert, Spieler 1 minimiert:
 * <pre>
 *   V(s) = u(s)                      falls s terminal
 *   V(s) = max_{s'} V(s')            falls Spieler 0 am Zug
 *   V(s) = min_{s'} V(s')            falls Spieler 1 am Zug
 * </pre>
 *
 * <p><b>(b) Negamax.</b> Nutzt die Nullsummeneigenschaft u_1 = -u_0:
 * <pre>
 *   V(s) = u(s, Spieler am Zug)      falls s terminal
 *   V(s) = max_{s'} ( -V(s') )       sonst
 * </pre>
 * Vorteil: eine einzige Codezeile statt zweier symmetrischer Faelle.
 *
 * <p><b>Komplexitaet.</b> Bei Verzweigungsgrad b und Tiefe d werden O(b^d) Knoten
 * besucht -- exponentiell. Bei Tic-Tac-Toe ist das noch machbar (ca. 550.000 Knoten),
 * bei Schach (b ca. 35) laengst nicht mehr.
 */
public final class Minimax {

    private Minimax() { }

    // ------------------------------------------------------------------
    // (a) Klassische MAX/MIN-Formulierung, Werte immer aus Sicht von Spieler 0
    // ------------------------------------------------------------------
    public static int valueClassic(GameState s, SearchStats st) {
        st.nodes++;
        if (s.isTerminal()) {
            st.leaves++;
            return s.utility(0);
        }
        if (s.playerToMove() == 0) {                 // MAX-Knoten
            int best = Integer.MIN_VALUE;
            for (Move m : s.legalMoves()) {
                best = Math.max(best, valueClassic(s.apply(m), st));
            }
            return best;
        } else {                                     // MIN-Knoten
            int best = Integer.MAX_VALUE;
            for (Move m : s.legalMoves()) {
                best = Math.min(best, valueClassic(s.apply(m), st));
            }
            return best;
        }
    }

    // ------------------------------------------------------------------
    // (b) Negamax, Werte aus Sicht des Spielers am Zug
    // ------------------------------------------------------------------
    public static int value(GameState s, SearchStats st) {
        st.nodes++;
        if (s.isTerminal()) {
            st.leaves++;
            return s.utility(s.playerToMove());
        }
        int best = Integer.MIN_VALUE;
        for (Move m : s.legalMoves()) {
            best = Math.max(best, -value(s.apply(m), st));
        }
        return best;
    }

    /** Bester Zug (irgendeiner der optimalen) samt zugehoerigem Wert. */
    public record Decision(Move move, int value) { }

    public static Decision bestMove(GameState s, SearchStats st) {
        long t0 = System.nanoTime();
        Move bestMove = null;
        int bestValue = Integer.MIN_VALUE;
        for (Move m : s.legalMoves()) {
            int v = -value(s.apply(m), st);
            if (v > bestValue) { bestValue = v; bestMove = m; }
        }
        st.elapsedNanos += System.nanoTime() - t0;
        return new Decision(bestMove, bestValue);
    }
}
