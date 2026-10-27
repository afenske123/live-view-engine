package de.fenske.liveview.ai;

import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;

import java.util.List;
import java.util.Random;

/**
 * Ein Agent waehlt in einer Stellung einen Zug. Ueber dieses Interface lassen sich
 * verschiedene Strategien (zufaellig, optimal, tiefenbegrenzt) austauschbar testen.
 */
public interface Agent {

    Move chooseMove(GameState s);

    String name();

    /** Zufallsagent -- Referenzgegner ("Baseline") fuer Turniere. */
    final class RandomAgent implements Agent {
        private final Random rnd;
        public RandomAgent(long seed) { this.rnd = new Random(seed); }
        @Override public Move chooseMove(GameState s) {
            List<Move> moves = s.legalMoves();
            return moves.get(rnd.nextInt(moves.size()));
        }
        @Override public String name() { return "Zufall"; }
    }

    /** Optimal spielender Agent auf Basis von Alpha-Beta. */
    final class AlphaBetaAgent implements Agent {
        private final int maxDepth;
        private final boolean useTT;
        public AlphaBetaAgent(int maxDepth, boolean useTT) {
            this.maxDepth = maxDepth;
            this.useTT = useTT;
        }
        @Override public Move chooseMove(GameState s) {
            AlphaBeta ab = new AlphaBeta(true, useTT);
            return ab.bestMove(s, maxDepth).move();
        }
        @Override public String name() {
            return "Alpha-Beta(d=" + maxDepth + (useTT ? ",TT" : "") + ")";
        }
    }

    /** Spielt eine Partie und liefert die Auszahlung fuer Spieler 0 (+1 / 0 / -1). */
    static int playMatch(GameState start, Agent p0, Agent p1) {
        GameState s = start;
        while (!s.isTerminal()) {
            Agent a = (s.playerToMove() == 0) ? p0 : p1;
            s = s.apply(a.chooseMove(s));
        }
        return s.utility(0);
    }
}
