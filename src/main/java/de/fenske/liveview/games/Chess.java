package de.fenske.liveview.games;

import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Schach als Extensivformspiel, auf demselben Abstraktionsniveau wie
 * {@link TicTacToe}, {@link Connect4} und {@link Othello} -- Zwei-Personen,
 * Nullsumme, vollstaendige Information -- damit dieselbe generische
 * {@link de.fenske.liveview.ai.AlphaBeta}-Suche wiederverwendet werden kann.
 *
 * <p><b>Vereinfachungen gegenueber den vollstaendigen FIDE-Regeln</b> (bewusst,
 * um das Projekt in einem sinnvollen Umfang zu halten):
 * <ul>
 *   <li>Bauern werden bei Erreichen der letzten Reihe automatisch zur Dame befoerdert
 *       (keine Auswahl von Turm/Laeufer/Springer).</li>
 *   <li>Remis durch dreifache Stellungswiederholung und die 50-Zuege-Regel werden
 *       nicht erkannt -- ein Spiel endet nur durch Matt oder Patt.</li>
 * </ul>
 * Rochade und En-Passant werden dagegen korrekt unterstuetzt.
 *
 * <p>Brettkodierung: {@code board[row][col]}, Reihe 0 = weisse Grundreihe (Rang 1),
 * Reihe 7 = schwarze Grundreihe (Rang 8), Spalte 0 = Linie a .. Spalte 7 = Linie h.
 * Figurenwerte: Bauer=1, Springer=2, Laeufer=3, Turm=4, Dame=5, Koenig=6,
 * positiv = Weiss, negativ = Schwarz, 0 = leer.
 */
public final class Chess implements GameState {

    public static final int EMPTY = 0;
    public static final int PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;

    /** Zug von (fromRow,fromCol) nach (toRow,toCol). Rochade/En-Passant/Promotion werden aus der Geometrie erkannt. */
    public record ChessMove(int fromRow, int fromCol, int toRow, int toCol) implements Move {
        @Override public String label() {
            return "" + square(fromRow, fromCol) + "-" + square(toRow, toCol);
        }
        private static String square(int row, int col) {
            return "" + (char) ('a' + col) + (row + 1);
        }
    }

    private final int[][] board;
    private final int toMove; // 0 = Weiss, 1 = Schwarz
    private final boolean whiteKingMoved, blackKingMoved;
    private final boolean whiteRookAMoved, whiteRookHMoved, blackRookAMoved, blackRookHMoved;
    private final int enPassantCol; // Spalte des Bauern, der im letzten Zug 2 Felder zog, sonst -1
    private final long key;

    public Chess() {
        board = new int[8][8];
        int[] backRank = {ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK};
        for (int c = 0; c < 8; c++) {
            board[0][c] = backRank[c];
            board[1][c] = PAWN;
            board[6][c] = -PAWN;
            board[7][c] = -backRank[c];
        }
        toMove = 0;
        whiteKingMoved = blackKingMoved = false;
        whiteRookAMoved = whiteRookHMoved = blackRookAMoved = blackRookHMoved = false;
        enPassantCol = -1;
        key = computeKeyFromScratch();
    }

    private Chess(int[][] board, int toMove, boolean whiteKingMoved, boolean blackKingMoved,
                  boolean whiteRookAMoved, boolean whiteRookHMoved, boolean blackRookAMoved, boolean blackRookHMoved,
                  int enPassantCol) {
        this.board = board;
        this.toMove = toMove;
        this.whiteKingMoved = whiteKingMoved;
        this.blackKingMoved = blackKingMoved;
        this.whiteRookAMoved = whiteRookAMoved;
        this.whiteRookHMoved = whiteRookHMoved;
        this.blackRookAMoved = blackRookAMoved;
        this.blackRookHMoved = blackRookHMoved;
        this.enPassantCol = enPassantCol;
        this.key = computeKeyFromScratch();
    }

    @Override public int playerToMove() { return toMove; }
    @Override public long key() { return key; }
    public int at(int row, int col) { return board[row][col]; }

    private static int colorOf(int piece) { return piece > 0 ? 0 : 1; }
    private static int typeOf(int piece) { return Math.abs(piece); }
    private boolean isOwn(int piece, int player) { return piece != EMPTY && colorOf(piece) == player; }
    private boolean isEnemy(int piece, int player) { return piece != EMPTY && colorOf(piece) != player; }
    private static boolean onBoard(int r, int c) { return r >= 0 && r < 8 && c >= 0 && c < 8; }

    // ------------------------------------------------------------------
    // Pseudo-legale Zuege (ohne Beruecksichtigung von Selbst-Schach)
    // ------------------------------------------------------------------
    private List<ChessMove> pseudoLegalMoves(int player) {
        List<ChessMove> moves = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c];
                if (!isOwn(piece, player)) continue;
                switch (typeOf(piece)) {
                    case PAWN -> pawnMoves(r, c, player, moves);
                    case KNIGHT -> knightMoves(r, c, player, moves);
                    case BISHOP -> slidingMoves(r, c, player, moves, DIAGONALS);
                    case ROOK -> slidingMoves(r, c, player, moves, ORTHOGONALS);
                    case QUEEN -> slidingMoves(r, c, player, moves, ALL_DIRECTIONS);
                    case KING -> kingMoves(r, c, player, moves);
                    default -> { }
                }
            }
        }
        return moves;
    }

    private static final int[][] ORTHOGONALS = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
    private static final int[][] DIAGONALS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] ALL_DIRECTIONS = {
            {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };
    private static final int[][] KNIGHT_OFFSETS = {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}
    };

    private void pawnMoves(int r, int c, int player, List<ChessMove> moves) {
        int dir = (player == 0) ? 1 : -1;
        int startRow = (player == 0) ? 1 : 6;
        int oneStep = r + dir;
        if (onBoard(oneStep, c) && board[oneStep][c] == EMPTY) {
            moves.add(new ChessMove(r, c, oneStep, c));
            int twoStep = r + 2 * dir;
            if (r == startRow && board[twoStep][c] == EMPTY) {
                moves.add(new ChessMove(r, c, twoStep, c));
            }
        }
        for (int dc : new int[]{-1, 1}) {
            int captureCol = c + dc;
            if (!onBoard(oneStep, captureCol)) continue;
            if (isEnemy(board[oneStep][captureCol], player)) {
                moves.add(new ChessMove(r, c, oneStep, captureCol));
            } else if (board[oneStep][captureCol] == EMPTY && captureCol == enPassantCol
                    && r == (player == 0 ? 4 : 3)) {
                moves.add(new ChessMove(r, c, oneStep, captureCol)); // En passant
            }
        }
    }

    private void knightMoves(int r, int c, int player, List<ChessMove> moves) {
        for (int[] o : KNIGHT_OFFSETS) {
            int nr = r + o[0], nc = c + o[1];
            if (onBoard(nr, nc) && !isOwn(board[nr][nc], player)) moves.add(new ChessMove(r, c, nr, nc));
        }
    }

    private void slidingMoves(int r, int c, int player, List<ChessMove> moves, int[][] directions) {
        for (int[] d : directions) {
            int nr = r + d[0], nc = c + d[1];
            while (onBoard(nr, nc)) {
                if (isOwn(board[nr][nc], player)) break;
                moves.add(new ChessMove(r, c, nr, nc));
                if (board[nr][nc] != EMPTY) break; // Zug hat einen Gegner geschlagen -> Linie endet hier
                nr += d[0];
                nc += d[1];
            }
        }
    }

    private void kingMoves(int r, int c, int player, List<ChessMove> moves) {
        for (int[] d : ALL_DIRECTIONS) {
            int nr = r + d[0], nc = c + d[1];
            if (onBoard(nr, nc) && !isOwn(board[nr][nc], player)) moves.add(new ChessMove(r, c, nr, nc));
        }
        // Rochade
        boolean kingMoved = (player == 0) ? whiteKingMoved : blackKingMoved;
        if (!kingMoved && !isSquareAttacked(r, c, 1 - player)) {
            boolean rookHMoved = (player == 0) ? whiteRookHMoved : blackRookHMoved;
            if (!rookHMoved && board[r][5] == EMPTY && board[r][6] == EMPTY
                    && typeOf(board[r][7]) == ROOK && isOwn(board[r][7], player)
                    && !isSquareAttacked(r, 5, 1 - player) && !isSquareAttacked(r, 6, 1 - player)) {
                moves.add(new ChessMove(r, c, r, 6)); // kurze Rochade
            }
            boolean rookAMoved = (player == 0) ? whiteRookAMoved : blackRookAMoved;
            if (!rookAMoved && board[r][1] == EMPTY && board[r][2] == EMPTY && board[r][3] == EMPTY
                    && typeOf(board[r][0]) == ROOK && isOwn(board[r][0], player)
                    && !isSquareAttacked(r, 3, 1 - player) && !isSquareAttacked(r, 2, 1 - player)) {
                moves.add(new ChessMove(r, c, r, 2)); // lange Rochade
            }
        }
    }

    private int[] findKing(int[][] b, int player) {
        int king = (player == 0) ? KING : -KING;
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (b[r][c] == king) return new int[]{r, c};
        throw new IllegalStateException("Kein Koenig fuer Spieler " + player + " gefunden.");
    }

    /** true, wenn (row, col) von einer Figur des Spielers {@code byPlayer} angegriffen wird. */
    private boolean isSquareAttacked(int row, int col, int byPlayer) {
        // Bauern
        int pawnDir = (byPlayer == 0) ? -1 : 1; // ein weisser Bauer greift von "unten" nach "oben" an
        for (int dc : new int[]{-1, 1}) {
            int r = row + pawnDir, c = col + dc;
            if (onBoard(r, c) && board[r][c] == (byPlayer == 0 ? PAWN : -PAWN)) return true;
        }
        // Springer
        for (int[] o : KNIGHT_OFFSETS) {
            int r = row + o[0], c = col + o[1];
            if (onBoard(r, c) && board[r][c] == (byPlayer == 0 ? KNIGHT : -KNIGHT)) return true;
        }
        // Koenig
        for (int[] d : ALL_DIRECTIONS) {
            int r = row + d[0], c = col + d[1];
            if (onBoard(r, c) && board[r][c] == (byPlayer == 0 ? KING : -KING)) return true;
        }
        // Laeufer/Dame (diagonal)
        if (attackedAlongRays(row, col, byPlayer, DIAGONALS, BISHOP)) return true;
        // Turm/Dame (orthogonal)
        return attackedAlongRays(row, col, byPlayer, ORTHOGONALS, ROOK);
    }

    private boolean attackedAlongRays(int row, int col, int byPlayer, int[][] directions, int slidingType) {
        for (int[] d : directions) {
            int r = row + d[0], c = col + d[1];
            while (onBoard(r, c)) {
                int piece = board[r][c];
                if (piece != EMPTY) {
                    if (colorOf(piece) == byPlayer) {
                        int t = typeOf(piece);
                        if (t == slidingType || t == QUEEN) return true;
                    }
                    break;
                }
                r += d[0];
                c += d[1];
            }
        }
        return false;
    }

    public boolean isInCheck(int player) {
        int[] king = findKing(board, player);
        return isSquareAttacked(king[0], king[1], 1 - player);
    }

    // ------------------------------------------------------------------
    // Legale Zuege: pseudo-legal, aber ohne dass der eigene Koenig danach im Schach steht
    // ------------------------------------------------------------------
    @Override
    public List<Move> legalMoves() {
        List<Move> legal = new ArrayList<>();
        for (ChessMove m : pseudoLegalMoves(toMove)) {
            Chess next = (Chess) applyInternal(m);
            if (!next.isSquareAttackedByOpponentKing(toMove)) legal.add(m);
        }
        return legal;
    }

    // Nach dem (probeweisen) Zug: steht der Spieler, der gerade gezogen hat (also NICHT next.toMove), im Schach?
    private boolean isSquareAttackedByOpponentKing(int playerWhoJustMoved) {
        int[] king = findKing(board, playerWhoJustMoved);
        return isSquareAttacked(king[0], king[1], 1 - playerWhoJustMoved);
    }

    @Override
    public GameState apply(Move m) {
        ChessMove cm = (ChessMove) m;
        Chess result = (Chess) applyInternal(cm);
        if (result.isSquareAttackedByOpponentKing(toMove)) {
            throw new IllegalArgumentException("Zug " + cm.label() + " laesst den eigenen Koenig im Schach.");
        }
        return result;
    }

    private GameState applyInternal(ChessMove m) {
        int[][] nb = new int[8][];
        for (int i = 0; i < 8; i++) nb[i] = board[i].clone();

        int piece = nb[m.fromRow()][m.fromCol()];
        int type = typeOf(piece);
        int player = colorOf(piece);
        int nextEnPassantCol = -1;

        // En passant: diagonaler Bauernzug auf ein leeres Feld -> geschlagener Bauer steht in der Ausgangsreihe
        if (type == PAWN && m.fromCol() != m.toCol() && board[m.toRow()][m.toCol()] == EMPTY) {
            nb[m.fromRow()][m.toCol()] = EMPTY;
        }
        // Doppelschritt merken (fuer En passant im naechsten Zug)
        if (type == PAWN && Math.abs(m.toRow() - m.fromRow()) == 2) {
            nextEnPassantCol = m.fromCol();
        }

        nb[m.fromRow()][m.fromCol()] = EMPTY;
        nb[m.toRow()][m.toCol()] = piece;

        // Promotion: Bauer erreicht letzte Reihe -> automatisch Dame
        if (type == PAWN && (m.toRow() == 0 || m.toRow() == 7)) {
            nb[m.toRow()][m.toCol()] = (player == 0) ? QUEEN : -QUEEN;
        }

        // Rochade: Koenig zieht zwei Felder -> passenden Turm mitziehen
        boolean nwKingMoved = whiteKingMoved, nbKingMoved = blackKingMoved;
        boolean nwRookA = whiteRookAMoved, nwRookH = whiteRookHMoved, nbRookA = blackRookAMoved, nbRookH = blackRookHMoved;
        if (type == KING) {
            if (player == 0) nwKingMoved = true; else nbKingMoved = true;
            if (Math.abs(m.toCol() - m.fromCol()) == 2) {
                int row = m.fromRow();
                if (m.toCol() == 6) { // kurz
                    nb[row][5] = nb[row][7];
                    nb[row][7] = EMPTY;
                } else if (m.toCol() == 2) { // lang
                    nb[row][3] = nb[row][0];
                    nb[row][0] = EMPTY;
                }
            }
        }
        if (type == ROOK) {
            if (player == 0 && m.fromRow() == 0 && m.fromCol() == 0) nwRookA = true;
            if (player == 0 && m.fromRow() == 0 && m.fromCol() == 7) nwRookH = true;
            if (player == 1 && m.fromRow() == 7 && m.fromCol() == 0) nbRookA = true;
            if (player == 1 && m.fromRow() == 7 && m.fromCol() == 7) nbRookH = true;
        }
        // Wird ein Turm auf seinem Startfeld geschlagen, verliert die jeweilige Seite das Rochaderecht.
        if (m.toRow() == 0 && m.toCol() == 0) nwRookA = true;
        if (m.toRow() == 0 && m.toCol() == 7) nwRookH = true;
        if (m.toRow() == 7 && m.toCol() == 0) nbRookA = true;
        if (m.toRow() == 7 && m.toCol() == 7) nbRookH = true;

        return new Chess(nb, 1 - toMove, nwKingMoved, nbKingMoved, nwRookA, nwRookH, nbRookA, nbRookH, nextEnPassantCol);
    }

    @Override
    public boolean isTerminal() {
        return legalMoves().isEmpty();
    }

    @Override
    public int utility(int player) {
        // Wird nur in Endzustaenden aufgerufen: kein legaler Zug mehr fuer toMove.
        if (isInCheck(toMove)) {
            // toMove ist matt -> der jeweils ANDERE Spieler hat gewonnen.
            return (player == toMove) ? -1 : 1;
        }
        return 0; // Patt
    }

    private static final int[] PIECE_VALUE = {0, 100, 320, 330, 500, 900, 0};

    /** Materialwert + kleine Mobilitaets- und Zentrumsbonus, aus Sicht des Spielers am Zug. */
    @Override
    public int evaluate() {
        int score = 0;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c];
                if (piece == EMPTY) continue;
                int value = PIECE_VALUE[typeOf(piece)];
                value += centerBonus(r, c, typeOf(piece));
                score += (colorOf(piece) == toMove) ? value : -value;
            }
        }
        int mobility = pseudoLegalMoves(toMove).size() - pseudoLegalMoves(1 - toMove).size();
        score += 2 * mobility;
        return score;
    }

    private int centerBonus(int r, int c, int type) {
        if (type != PAWN && type != KNIGHT) return 0;
        double distanceFromCenter = Math.abs(r - 3.5) + Math.abs(c - 3.5);
        return (int) Math.round((7 - distanceFromCenter) * 2);
    }

    @Override
    public String render() {
        StringBuilder sb = new StringBuilder();
        String symbols = " PNBRQK";
        for (int r = 7; r >= 0; r--) {
            sb.append(r + 1).append(' ');
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c];
                char ch = piece == EMPTY ? '.' : symbols.charAt(typeOf(piece));
                if (piece < 0) ch = Character.toLowerCase(ch);
                sb.append(ch).append(' ');
            }
            sb.append('\n');
        }
        sb.append("  a b c d e f g h\n");
        return sb.toString();
    }

    @Override
    public String label() {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--)
            for (int c = 0; c < 8; c++)
                sb.append(board[r][c]).append(',');
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Zobrist-Hashing fuer die Transpositionstabelle
    // ------------------------------------------------------------------
    private static final long[][][] ZOBRIST_PIECES = new long[8][8][13]; // Index 6+piece, piece in [-6,6]
    private static final long ZOBRIST_SIDE;
    private static final long[] ZOBRIST_CASTLING = new long[4];
    private static final long[] ZOBRIST_EN_PASSANT = new long[8];
    static {
        Random rnd = new Random(20240818L);
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                for (int p = 0; p < 13; p++)
                    ZOBRIST_PIECES[r][c][p] = rnd.nextLong();
        ZOBRIST_SIDE = rnd.nextLong();
        for (int i = 0; i < 4; i++) ZOBRIST_CASTLING[i] = rnd.nextLong();
        for (int i = 0; i < 8; i++) ZOBRIST_EN_PASSANT[i] = rnd.nextLong();
    }

    private long computeKeyFromScratch() {
        long k = 0L;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int piece = board[r][c];
                if (piece != EMPTY) k ^= ZOBRIST_PIECES[r][c][piece + 6];
            }
        }
        if (toMove == 1) k ^= ZOBRIST_SIDE;
        if (whiteKingMoved) k ^= ZOBRIST_CASTLING[0];
        if (blackKingMoved) k ^= ZOBRIST_CASTLING[1];
        if (whiteRookAMoved || whiteRookHMoved) k ^= ZOBRIST_CASTLING[2];
        if (blackRookAMoved || blackRookHMoved) k ^= ZOBRIST_CASTLING[3];
        if (enPassantCol >= 0) k ^= ZOBRIST_EN_PASSANT[enPassantCol];
        return k;
    }
}
