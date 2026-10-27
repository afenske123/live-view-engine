package de.fenske.liveview.games;

import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Vier gewinnt auf einem 7x6-Brett, als Extensivformspiel (Zwei-Personen,
 * Nullsumme, vollstaendige Information) im selben Stil wie {@link TicTacToe},
 * damit es dieselbe {@link de.fenske.liveview.ai.AlphaBeta}-Suche wiederverwenden kann.
 *
 * <p>Brettkodierung: Spalte 0..6, Zeile 0 (unten) .. 5 (oben). Ein Zug waehlt
 * eine Spalte; der Stein faellt auf die erste freie Zeile ("Schwerkraft").
 * Da der volle Spielbaum zu gross fuer erschoepfende Suche ist (Verzweigungsgrad
 * bis 7, Tiefe bis 42), liefert {@link #evaluate()} eine heuristische Bewertung
 * fuer tiefenbegrenzte Suche.
 */
public final class Connect4 implements GameState {

    public static final int WIDTH = 7;
    public static final int HEIGHT = 6;
    public static final int EMPTY = -1;

    /** Zug = Einwurf in Spalte {@code column}. */
    public record Drop(int column) implements Move {
        @Override public String label() { return "Spalte " + (column + 1); }
    }

    private static final long[][][] ZOBRIST = new long[WIDTH][HEIGHT][2];
    static {
        Random rnd = new Random(20240816L);
        for (int c = 0; c < WIDTH; c++)
            for (int r = 0; r < HEIGHT; r++)
                for (int p = 0; p < 2; p++)
                    ZOBRIST[c][r][p] = rnd.nextLong();
    }

    private final int[][] board; // [column][row], EMPTY/0/1
    private final int[] heights; // naechste freie Zeile je Spalte
    private final int toMove;
    private final long key;
    private final int lastColumn, lastRow; // fuer schnelle Sieger-Pruefung, -1 wenn kein Zug bisher

    public Connect4() {
        this.board = new int[WIDTH][HEIGHT];
        for (int[] col : board) java.util.Arrays.fill(col, EMPTY);
        this.heights = new int[WIDTH];
        this.toMove = 0;
        this.key = 0L;
        this.lastColumn = -1;
        this.lastRow = -1;
    }

    private Connect4(int[][] board, int[] heights, int toMove, long key, int lastColumn, int lastRow) {
        this.board = board;
        this.heights = heights;
        this.toMove = toMove;
        this.key = key;
        this.lastColumn = lastColumn;
        this.lastRow = lastRow;
    }

    @Override public int playerToMove() { return toMove; }
    @Override public long key() { return key; }

    public int at(int column, int row) { return board[column][row]; }

    private boolean columnFull(int column) { return heights[column] >= HEIGHT; }

    @Override
    public List<Move> legalMoves() {
        List<Move> moves = new ArrayList<>(WIDTH);
        if (winnerAt(lastColumn, lastRow) != -1) return moves;
        // Zugsortierung: Mitte zuerst -> bessere Alpha-Beta-Schnitte (wie bei TicTacToe)
        int[] order = {3, 2, 4, 1, 5, 0, 6};
        for (int c : order) if (!columnFull(c)) moves.add(new Drop(c));
        return moves;
    }

    @Override
    public GameState apply(Move m) {
        int c = ((Drop) m).column();
        if (columnFull(c)) throw new IllegalArgumentException("Spalte " + c + " ist voll.");
        int r = heights[c];
        int[][] nb = new int[WIDTH][];
        for (int i = 0; i < WIDTH; i++) nb[i] = board[i].clone();
        nb[c][r] = toMove;
        int[] nh = heights.clone();
        nh[c] = r + 1;
        return new Connect4(nb, nh, 1 - toMove, key ^ ZOBRIST[c][r][toMove], c, r);
    }

    /** Gewinner (0/1) falls bei (column, row) eine 4er-Reihe endet, sonst -1. */
    private int winnerAt(int column, int row) {
        if (column < 0) return -1;
        int player = board[column][row];
        if (player == EMPTY) return -1;
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : directions) {
            int count = 1;
            count += countDirection(column, row, d[0], d[1], player);
            count += countDirection(column, row, -d[0], -d[1], player);
            if (count >= 4) return player;
        }
        return -1;
    }

    private int countDirection(int column, int row, int dc, int dr, int player) {
        int count = 0;
        int c = column + dc, r = row + dr;
        while (c >= 0 && c < WIDTH && r >= 0 && r < HEIGHT && board[c][r] == player) {
            count++;
            c += dc;
            r += dr;
        }
        return count;
    }

    private boolean isFull() {
        for (int h : heights) if (h < HEIGHT) return false;
        return true;
    }

    @Override public boolean isTerminal() { return winnerAt(lastColumn, lastRow) != -1 || isFull(); }

    @Override
    public int utility(int player) {
        int w = winnerAt(lastColumn, lastRow);
        if (w == -1) return 0;
        return (w == player) ? 1 : -1;
    }

    /**
     * Heuristische Bewertung aus Sicht des Spielers am Zug: zaehlt, wie viele
     * potenzielle 4er-Fenster jedem Spieler noch offenstehen, gewichtet danach,
     * wie viele eigene Steine schon darin liegen. Wird nur bei Tiefenbegrenzung
     * gebraucht (siehe {@link de.fenske.liveview.ai.AlphaBeta#bestMove(GameState, int)}).
     */
    @Override
    public int evaluate() {
        int me = toMove;
        int opp = 1 - toMove;
        int score = 0;
        score += windowScore(me) - windowScore(opp);
        return score;
    }

    private int windowScore(int player) {
        int total = 0;
        int[][] directions = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int c = 0; c < WIDTH; c++) {
            for (int r = 0; r < HEIGHT; r++) {
                for (int[] d : directions) {
                    total += windowValue(c, r, d[0], d[1], player);
                }
            }
        }
        return total;
    }

    private int windowValue(int c, int r, int dc, int dr, int player) {
        int endC = c + 3 * dc, endR = r + 3 * dr;
        if (endC < 0 || endC >= WIDTH || endR < 0 || endR >= HEIGHT) return 0;
        int mine = 0, theirs = 0;
        for (int i = 0; i < 4; i++) {
            int v = board[c + i * dc][r + i * dr];
            if (v == player) mine++;
            else if (v != EMPTY) theirs++;
        }
        if (theirs > 0) return 0; // Fenster durch Gegner blockiert
        return switch (mine) {
            case 4 -> 1000;
            case 3 -> 20;
            case 2 -> 5;
            default -> 0;
        };
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (int r = HEIGHT - 1; r >= 0; r--) {
            for (int c = 0; c < WIDTH; c++) {
                int v = board[c][r];
                sb.append(v == 0 ? " X " : v == 1 ? " O " : " . ");
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String label() {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < WIDTH; c++) sb.append(heights[c]);
        return sb.toString();
    }
}
