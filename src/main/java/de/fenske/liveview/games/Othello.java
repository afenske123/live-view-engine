package de.fenske.liveview.games;

import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Othello/Reversi auf einem 8x8-Brett, als drittes Extensivformspiel neben
 * {@link TicTacToe} und {@link Connect4} -- auf demselben Abstraktionsniveau
 * (Zwei-Personen, Nullsumme, vollstaendige Information), damit dieselbe
 * {@link de.fenske.liveview.ai.AlphaBeta}-Suche direkt wiederverwendbar ist.
 *
 * <p>Besonderheit gegenueber den beiden anderen Spielen: Wer keinen legalen Zug hat,
 * muss aussetzen ({@code legalMoves()} liefert dann genau einen {@link Pass}-Zug);
 * das Spiel endet erst, wenn beide Seiten hintereinander passen muessen.
 */
public final class Othello implements GameState {

    public static final int SIZE = 8;
    public static final int EMPTY = -1;

    private static final int[][] DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
    };

    /** Zug = Stein auf (row, col) legen. */
    public record Place(int row, int col) implements Move {
        @Override public String label() { return "" + (char) ('A' + col) + (row + 1); }
    }

    /** Zug = Aussetzen, weil kein legaler Zug existiert. */
    public record Pass() implements Move {
        @Override public String label() { return "Passen"; }
    }

    private static final long[][][] ZOBRIST = new long[SIZE][SIZE][2];
    static {
        Random rnd = new Random(20240817L);
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                for (int p = 0; p < 2; p++)
                    ZOBRIST[r][c][p] = rnd.nextLong();
    }

    private final int[][] board; // [row][col]
    private final int toMove;
    private final long key;
    private final boolean previousWasPass;

    public Othello() {
        this.board = new int[SIZE][SIZE];
        for (int[] row : board) java.util.Arrays.fill(row, EMPTY);
        board[3][3] = 1; board[3][4] = 0;
        board[4][3] = 0; board[4][4] = 1;
        this.toMove = 0;
        this.key = ZOBRIST[3][3][1] ^ ZOBRIST[3][4][0] ^ ZOBRIST[4][3][0] ^ ZOBRIST[4][4][1];
        this.previousWasPass = false;
    }

    private Othello(int[][] board, int toMove, long key, boolean previousWasPass) {
        this.board = board;
        this.toMove = toMove;
        this.key = key;
        this.previousWasPass = previousWasPass;
    }

    @Override public int playerToMove() { return toMove; }
    @Override public long key() { return key; }
    public int at(int row, int col) { return board[row][col]; }

    private List<int[]> flipsFor(int row, int col, int player) {
        if (board[row][col] != EMPTY) return List.of();
        List<int[]> allFlips = new ArrayList<>();
        int opponent = 1 - player;
        for (int[] d : DIRECTIONS) {
            List<int[]> lineFlips = new ArrayList<>();
            int r = row + d[0], c = col + d[1];
            while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == opponent) {
                lineFlips.add(new int[]{r, c});
                r += d[0];
                c += d[1];
            }
            if (!lineFlips.isEmpty() && r >= 0 && r < SIZE && c >= 0 && c < SIZE && board[r][c] == player) {
                allFlips.addAll(lineFlips);
            }
        }
        return allFlips;
    }

    private boolean hasAnyMove(int player) {
        for (int r = 0; r < SIZE; r++)
            for (int c = 0; c < SIZE; c++)
                if (!flipsFor(r, c, player).isEmpty()) return true;
        return false;
    }

    @Override
    public List<Move> legalMoves() {
        List<Move> moves = new ArrayList<>();
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (!flipsFor(r, c, toMove).isEmpty()) moves.add(new Place(r, c));
            }
        }
        if (moves.isEmpty() && !isTerminal()) moves.add(new Pass());
        return moves;
    }

    @Override
    public GameState apply(Move m) {
        if (m instanceof Pass) {
            return new Othello(board, 1 - toMove, key, true);
        }
        Place p = (Place) m;
        List<int[]> flips = flipsFor(p.row(), p.col(), toMove);
        if (flips.isEmpty()) throw new IllegalArgumentException("Kein legaler Zug: " + p);

        int[][] nb = new int[SIZE][];
        for (int i = 0; i < SIZE; i++) nb[i] = board[i].clone();
        long nk = key;

        nb[p.row()][p.col()] = toMove;
        nk ^= ZOBRIST[p.row()][p.col()][toMove];
        for (int[] f : flips) {
            int opponent = 1 - toMove;
            nk ^= ZOBRIST[f[0]][f[1]][opponent]; // altes (Gegner-)Feld entfernen
            nb[f[0]][f[1]] = toMove;
            nk ^= ZOBRIST[f[0]][f[1]][toMove];   // neues Feld setzen
        }
        return new Othello(nb, 1 - toMove, nk, false);
    }

    private int countDiscs(int player) {
        int n = 0;
        for (int[] row : board) for (int v : row) if (v == player) n++;
        return n;
    }

    private boolean boardFull() {
        for (int[] row : board) for (int v : row) if (v == EMPTY) return false;
        return true;
    }

    @Override
    public boolean isTerminal() {
        if (boardFull()) return true;
        // Ende, wenn BEIDE Spieler keinen Zug haben (nicht nur der aktuelle).
        return !hasAnyMove(0) && !hasAnyMove(1);
    }

    @Override
    public int utility(int player) {
        int mine = countDiscs(player);
        int theirs = countDiscs(1 - player);
        if (mine == theirs) return 0;
        return mine > theirs ? 1 : -1;
    }

    /**
     * Heuristik fuer tiefenbegrenzte Suche: Eckenfelder sind unschlagbar (koennen
     * nie zurueckerobert werden) und deutlich hoeher gewichtet als die Scheibenzahl,
     * die erst im Endspiel wirklich zaehlt.
     */
    @Override
    public int evaluate() {
        int me = toMove, opp = 1 - toMove;
        int score = 0;
        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                int weight = cellWeight(r, c);
                if (board[r][c] == me) score += weight;
                else if (board[r][c] == opp) score -= weight;
            }
        }
        return score;
    }

    private int cellWeight(int r, int c) {
        boolean edgeRow = (r == 0 || r == SIZE - 1);
        boolean edgeCol = (c == 0 || c == SIZE - 1);
        boolean corner = edgeRow && edgeCol;
        boolean nextToCorner = (r <= 1 || r >= SIZE - 2) && (c <= 1 || c >= SIZE - 2) && !corner;
        if (corner) return 25;
        if (nextToCorner) return -10; // Felder direkt neben einer Ecke oeffnen dem Gegner die Ecke
        if (edgeRow || edgeCol) return 5;
        return 1;
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder("  A B C D E F G H\n");
        for (int r = 0; r < SIZE; r++) {
            sb.append(r + 1).append(' ');
            for (int c = 0; c < SIZE; c++) {
                int v = board[r][c];
                sb.append(v == 0 ? 'X' : v == 1 ? 'O' : '.').append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String label() {
        StringBuilder sb = new StringBuilder();
        for (int[] row : board)
            for (int v : row)
                sb.append(v == 0 ? 'X' : v == 1 ? 'O' : '.');
        return sb.toString();
    }
}
