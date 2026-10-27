package de.fenske.liveview.ai;

import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;

import java.util.ArrayList;
import java.util.List;

/**
 * Negamax mit <b>Alpha-Beta-Pruning</b>, optionaler Zugsortierung und
 * optionaler Transpositionstabelle.
 *
 * <p><b>Idee des Prunings.</b> alpha = bisher garantierter Wert fuer den Spieler am Zug,
 * beta = Wert, den der Gegner hoechstens zulaesst. Sobald ein Zug einen Wert
 * &gt;= beta liefert, wird der Rest des Teilbaums <b>nicht mehr betrachtet</b>:
 * Der Gegner wuerde diesen Ast ohnehin nie zulassen (er hat schon eine
 * bessere Alternative). Das nennt man einen <i>Beta-Schnitt</i>.
 *
 * <p><b>Korrektheit.</b> Alpha-Beta liefert exakt denselben Wert wie Minimax --
 * es werden nur Knoten uebersprungen, die den Wurzelwert nicht beeinflussen koennen.
 *
 * <p><b>Komplexitaet.</b>
 * <pre>
 *   schlechteste Zugsortierung: O(b^d)        (kein Gewinn)
 *   beste Zugsortierung       : O(b^(d/2))    (Knuth/Moore 1975)
 * </pre>
 * Effektiver Verzweigungsgrad sinkt also von b auf sqrt(b) -- bei gleicher Rechenzeit
 * ist damit <b>doppelt so tiefe</b> Suche moeglich. Deshalb ist Zugsortierung so wichtig.
 */
public final class AlphaBeta {

    public static final int INF = 1_000_000;

    private final boolean useOrdering;
    private final TranspositionTable tt;   // null = keine Tabelle
    private final SearchStats stats = new SearchStats();

    public AlphaBeta(boolean useOrdering, boolean useTT) {
        this.useOrdering = useOrdering;
        this.tt = useTT ? new TranspositionTable() : null;
    }

    public SearchStats stats() { return stats; }
    public TranspositionTable table() { return tt; }

    /** @param move bester Zug, @param value Wert aus Sicht des Spielers am Zug */
    public record Decision(Move move, int value) { }

    /** Sucht den besten Zug in der Wurzelstellung (volle Tiefe). */
    public Decision bestMove(GameState s) {
        return bestMove(s, Integer.MAX_VALUE);
    }

    /** Sucht den besten Zug mit Tiefenbegrenzung (danach greift {@code evaluate()}). */
    public Decision bestMove(GameState s, int maxDepth) {
        stats.reset();
        long t0 = System.nanoTime();

        Move best = null;
        int alpha = -INF, beta = INF;
        for (Move m : orderedMoves(s, maxDepth)) {
            int v = -search(s.apply(m), maxDepth - 1, -beta, -alpha);
            if (v > alpha || best == null) { alpha = v; best = m; }
        }
        stats.elapsedNanos = System.nanoTime() - t0;
        return new Decision(best, alpha);
    }

    /** Nur der Wert der Stellung (aus Sicht des Spielers am Zug). */
    public int value(GameState s) {
        stats.reset();
        long t0 = System.nanoTime();
        int v = search(s, Integer.MAX_VALUE, -INF, INF);
        stats.elapsedNanos = System.nanoTime() - t0;
        return v;
    }

    // ------------------------------------------------------------------
    // Kern: Negamax mit Alpha-Beta
    // ------------------------------------------------------------------
    private int search(GameState s, int depth, int alpha, int beta) {
        stats.nodes++;

        final int alphaOrig = alpha;

        // --- 1) Transpositionstabelle abfragen ---------------------------------
        TranspositionTable.Entry entry = null;
        if (tt != null) {
            entry = tt.get(s.key());
            if (entry != null && entry.depth() >= depth) {
                stats.ttHits++;
                switch (entry.flag()) {
                    case EXACT -> { return entry.value(); }
                    case LOWER -> alpha = Math.max(alpha, entry.value());
                    case UPPER -> beta  = Math.min(beta,  entry.value());
                }
                if (alpha >= beta) return entry.value();
            }
        }

        // --- 2) Abbruchbedingungen ---------------------------------------------
        if (s.isTerminal()) {
            stats.leaves++;
            return s.utility(s.playerToMove());
        }
        if (depth <= 0) {
            stats.leaves++;
            return s.evaluate();
        }

        // --- 3) Zuege durchgehen ------------------------------------------------
        List<Move> moves = orderedMoves(s, depth);
        if (entry != null && entry.best() != null) {
            // Der beste Zug aus einer frueheren (flacheren) Suche zuerst -> mehr Schnitte
            moves = new ArrayList<>(moves);
            if (moves.remove(entry.best())) moves.add(0, entry.best());
        }

        int best = -INF - 1;
        Move bestMove = null;
        for (Move m : moves) {
            int v = -search(s.apply(m), depth - 1, -beta, -alpha);
            if (v > best) { best = v; bestMove = m; }
            if (best > alpha) alpha = best;
            if (alpha >= beta) {                 // Beta-Schnitt
                stats.cutoffs++;
                break;
            }
        }

        // --- 4) Ergebnis in der Tabelle ablegen --------------------------------
        if (tt != null) {
            TranspositionTable.Flag flag =
                    (best <= alphaOrig) ? TranspositionTable.Flag.UPPER
                    : (best >= beta)    ? TranspositionTable.Flag.LOWER
                    :                     TranspositionTable.Flag.EXACT;
            tt.put(s.key(), new TranspositionTable.Entry(depth, best, flag, bestMove));
        }
        return best;
    }

    /** Zugliste, ggf. sortiert. Die Spielklassen liefern bereits eine sinnvolle Reihenfolge. */
    private List<Move> orderedMoves(GameState s, int depth) {
        List<Move> moves = s.legalMoves();
        if (!useOrdering) {
            // Ohne Sortierung: bewusst umdrehen, um die "gute" Default-Ordnung zu zerstoeren
            List<Move> copy = new ArrayList<>(moves);
            java.util.Collections.reverse(copy);
            return copy;
        }
        return moves;
    }
}
