package de.fenske.liveview.games;

import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tic-Tac-Toe (Drei gewinnt) als Extensivformspiel.
 *
 * <p>Brettkodierung: Feld 0..8, zeilenweise:
 * <pre>
 *    0 | 1 | 2
 *   ---+---+---
 *    3 | 4 | 5
 *   ---+---+---
 *    6 | 7 | 8
 * </pre>
 * Inhalt: {@code EMPTY=-1}, Spieler 0 = 'X', Spieler 1 = 'O'.
 *
 * <p>Groesse des Spielbaums: 9! = 362.880 Zugfolgen ohne vorzeitiges Ende,
 * tatsaechlich 549.946 Knoten (inkl. Wurzel), 255.168 Blaetter.
 * Spielwert bei beidseitig optimalem Spiel: <b>0 (Remis)</b> -- Satz von Zermelo.
 */
public final class TicTacToe implements GameState {

    public static final int EMPTY = -1;

    /** Zug = Setzen auf Feld {@code cell}. */
    public record Cell(int cell) implements Move {
        @Override public String label() {
            return "" + (char) ('A' + cell % 3) + (cell / 3 + 1);   // A1..C3
        }
    }

    /** Alle 8 Gewinnlinien (3 Zeilen, 3 Spalten, 2 Diagonalen). */
    private static final int[][] LINES = {
            {0,1,2},{3,4,5},{6,7,8},      // Zeilen
            {0,3,6},{1,4,7},{2,5,8},      // Spalten
            {0,4,8},{2,4,6}               // Diagonalen
    };

    /** Zobrist-Tabelle: fuer jedes Feld und jeden Spieler eine feste Zufallszahl. */
    private static final long[][] ZOBRIST = new long[9][2];
    static {
        Random rnd = new Random(20240815L);   // fester Seed -> reproduzierbare Laeufe
        for (int c = 0; c < 9; c++) {
            for (int p = 0; p < 2; p++) ZOBRIST[c][p] = rnd.nextLong();
        }
    }

    private final int[] board;
    private final int toMove;
    private final long key;

    public TicTacToe() {
        this.board = new int[9];
        java.util.Arrays.fill(this.board, EMPTY);
        this.toMove = 0;
        this.key = 0L;
    }

    private TicTacToe(int[] board, int toMove, long key) {
        this.board = board;
        this.toMove = toMove;
        this.key = key;
    }

    /** Aufbau aus einem String wie "X.O|.X.|..O" -- praktisch fuer Tests. */
    public static TicTacToe fromString(String s, int toMove) {
        String clean = s.replace("|", "").replace(" ", "");
        if (clean.length() != 9) throw new IllegalArgumentException("Brett muss 9 Felder haben.");
        int[] b = new int[9];
        long k = 0L;
        for (int i = 0; i < 9; i++) {
            char c = clean.charAt(i);
            b[i] = (c == 'X') ? 0 : (c == 'O') ? 1 : EMPTY;
            if (b[i] != EMPTY) k ^= ZOBRIST[i][b[i]];
        }
        return new TicTacToe(b, toMove, k);
    }

    @Override public int playerToMove() { return toMove; }
    @Override public long key() { return key; }

    /** Inhalt von Feld {@code cell} (0..8): {@link #EMPTY}, 0 (X) oder 1 (O). */
    public int at(int cell) { return board[cell]; }

    /** Index des Gewinners (0/1) oder -1, falls niemand gewonnen hat. */
    public int winner() {
        for (int[] l : LINES) {
            int v = board[l[0]];
            if (v != EMPTY && v == board[l[1]] && v == board[l[2]]) return v;
        }
        return -1;
    }

    private boolean isFull() {
        for (int v : board) if (v == EMPTY) return false;
        return true;
    }

    @Override public boolean isTerminal() { return winner() != -1 || isFull(); }

    @Override
    public List<Move> legalMoves() {
        List<Move> moves = new ArrayList<>(9);
        if (isTerminal()) return moves;
        // Zugsortierung: Mitte, dann Ecken, dann Kanten -> bessere Alpha-Beta-Schnitte
        final int[] order = { 4, 0, 2, 6, 8, 1, 3, 5, 7 };
        for (int c : order) if (board[c] == EMPTY) moves.add(new Cell(c));
        return moves;
    }

    @Override
    public GameState apply(Move m) {
        int c = ((Cell) m).cell();
        if (board[c] != EMPTY) throw new IllegalArgumentException("Feld " + c + " ist belegt.");
        int[] nb = board.clone();
        nb[c] = toMove;
        return new TicTacToe(nb, 1 - toMove, key ^ ZOBRIST[c][toMove]);
    }

    @Override
    public int utility(int player) {
        int w = winner();
        if (w == -1) return 0;             // Remis
        return (w == player) ? 1 : -1;
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 3; r++) {
            sb.append(" ");
            for (int c = 0; c < 3; c++) {
                int v = board[r * 3 + c];
                sb.append(v == 0 ? 'X' : v == 1 ? 'O' : '.');
                if (c < 2) sb.append(" | ");
            }
            sb.append("\n");
            if (r < 2) sb.append("---+---+---\n");
        }
        return sb.toString();
    }

    @Override
    public String label() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            sb.append(board[i] == 0 ? 'X' : board[i] == 1 ? 'O' : '.');
            if (i % 3 == 2 && i < 8) sb.append('/');
        }
        return sb.toString();
    }
}
