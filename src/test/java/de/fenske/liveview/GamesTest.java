package de.fenske.liveview;

import de.fenske.liveview.ai.AlphaBeta;
import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;
import de.fenske.liveview.games.Chess;
import de.fenske.liveview.games.Connect4;
import de.fenske.liveview.games.Othello;
import de.fenske.liveview.games.TicTacToe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sanity tests for the four {@link GameState} implementations: random self-play never
 * throws, the AlphaBeta engine always returns a move while the game is running, and a
 * handful of well-known chess situations (checkmate, castling, en passant, promotion)
 * are handled correctly.
 */
class GamesTest {

    @Test
    void ticTacToeRandomSelfPlayNeverThrows() {
        randomSelfPlay(new TicTacToe(), 20);
    }

    @Test
    void connect4RandomSelfPlayNeverThrows() {
        randomSelfPlay(new Connect4(), 60);
    }

    @Test
    void othelloRandomSelfPlayNeverThrows() {
        randomSelfPlay(new Othello(), 80);
    }

    @Test
    void chessRandomSelfPlayNeverThrows() {
        randomSelfPlay(new Chess(), 80);
    }

    @Test
    void alphaBetaAlwaysMovesUntilTerminal() {
        AlphaBeta ab = new AlphaBeta(true, true);
        GameState s = new Connect4();
        int plies = 0;
        while (!s.isTerminal() && plies < 20) {
            AlphaBeta.Decision d = ab.bestMove(s, 5);
            assertNotNull(d.move(), "bestMove() must not return null while the game is still running");
            s = s.apply(d.move());
            plies++;
        }
    }

    @Test
    void foolsMateIsDetectedAsCheckmate() {
        GameState g = new Chess();
        g = move(g, "f2", "f3");
        g = move(g, "e7", "e5");
        g = move(g, "g2", "g4");
        g = move(g, "d8", "h4"); // Qh4#
        assertTrue(g.isTerminal());
        assertEquals(1, g.utility(1), "Black should be recorded as the winner of fool's mate");
    }

    @Test
    void kingsideCastlingMovesRookToo() {
        GameState g = new Chess();
        g = move(g, "g1", "f3");
        g = move(g, "g8", "f6");
        g = move(g, "g2", "g3");
        g = move(g, "g7", "g6");
        g = move(g, "f1", "g2");
        g = move(g, "f8", "g7");
        g = move(g, "e1", "g1");
        Chess c = (Chess) g;
        assertEquals(Chess.KING, c.at(0, 6));
        assertEquals(Chess.ROOK, c.at(0, 5));
    }

    @Test
    void enPassantRemovesThePassedPawn() {
        GameState g = new Chess();
        g = move(g, "e2", "e4");
        g = move(g, "a7", "a6");
        g = move(g, "e4", "e5");
        g = move(g, "d7", "d5");
        g = move(g, "e5", "d6"); // en passant
        Chess c = (Chess) g;
        assertEquals(Chess.PAWN, c.at(5, 3));
        assertEquals(Chess.EMPTY, c.at(4, 3));
    }

    @Test
    void pawnPromotesToQueenOnLastRank() {
        GameState g = new Chess();
        g = move(g, "b2", "b4");
        g = move(g, "a7", "a5");
        g = move(g, "b4", "b5");
        g = move(g, "a5", "a4");
        g = move(g, "b5", "b6");
        g = move(g, "a4", "a3");
        g = move(g, "b6", "c7");
        g = move(g, "g8", "f6");
        g = move(g, "c7", "d8"); // captures the black queen and promotes
        assertEquals(Chess.QUEEN, ((Chess) g).at(7, 3));
    }

    private static void randomSelfPlay(GameState start, int maxPlies) {
        Random rnd = new Random(7);
        GameState s = start;
        int plies = 0;
        while (!s.isTerminal() && plies < maxPlies) {
            List<Move> moves = s.legalMoves();
            assertFalse(moves.isEmpty(), "legalMoves() must not be empty while the game is not terminal");
            s = s.apply(moves.get(rnd.nextInt(moves.size())));
            plies++;
        }
    }

    private static GameState move(GameState g, String from, String to) {
        int fromCol = from.charAt(0) - 'a', fromRow = from.charAt(1) - '1';
        int toCol = to.charAt(0) - 'a', toRow = to.charAt(1) - '1';
        for (Move m : g.legalMoves()) {
            Chess.ChessMove cm = (Chess.ChessMove) m;
            if (cm.fromRow() == fromRow && cm.fromCol() == fromCol && cm.toRow() == toRow && cm.toCol() == toCol) {
                return g.apply(cm);
            }
        }
        throw new AssertionError("Move " + from + "-" + to + " not found among legal moves.");
    }
}
