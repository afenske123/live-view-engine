package de.fenske.liveview.views;

import de.fenske.liveview.Clerk;
import de.fenske.liveview.LiveView;
import de.fenske.liveview.ai.AlphaBeta;
import de.fenske.liveview.core.GameState;
import de.fenske.liveview.games.Chess;

/**
 * Browser view for {@link Chess}. Click a piece, then click its destination square;
 * illegal destinations are simply ignored (the piece stays selected so you can try again).
 */
public class ChessView {

    private static final int AI_DEPTH = 3;
    private static final int CELL = 64;

    private final String id;
    private final LiveView view;
    private final boolean vsComputer;
    private GameState state = new Chess();
    private Integer selectedRow = null, selectedCol = null;

    public ChessView(LiveView view, boolean vsComputer) {
        this.view = view;
        this.vsComputer = vsComputer;
        this.id = Clerk.getHashID(this);
        int size = 8 * CELL;

        Clerk.write(view, """
                <div class="status" id="status%s">Schach %s &mdash; Weiß ist am Zug.</div>
                <canvas id="chess-%s" width="%d" height="%d"></canvas>
                """.formatted(id, vsComputer ? "(gegen den Computer)" : "(zwei Spieler)", id, size, size));

        Clerk.script(view, """
                (function() {
                    const CELL = %2$d;
                    const GLYPHS = {1:'\\u2659',2:'\\u2658',3:'\\u2657',4:'\\u2656',5:'\\u2655',6:'\\u2654',
                                     '-1':'\\u265f','-2':'\\u265e','-3':'\\u265d','-4':'\\u265c','-5':'\\u265b','-6':'\\u265a'};
                    const canvas = document.getElementById('chess-%1$s');
                    const ctx = canvas.getContext('2d');
                    window.renderChess%1$s = function(board, selRow, selCol) {
                        for (let r = 0; r < 8; r++) {
                            for (let c = 0; c < 8; c++) {
                                const canvasRow = 7 - r;
                                const light = (r + c) %% 2 === 1;
                                ctx.fillStyle = (selRow === r && selCol === c) ? '#6fa8dc'
                                                 : (light ? '#eeeed2' : '#769656');
                                ctx.fillRect(c*CELL, canvasRow*CELL, CELL, CELL);
                                const v = board[r][c];
                                if (v !== 0) {
                                    ctx.font = (CELL - 12) + 'px serif';
                                    ctx.textAlign = 'center';
                                    ctx.textBaseline = 'middle';
                                    ctx.fillStyle = v > 0 ? '#ffffff' : '#101010';
                                    ctx.strokeStyle = v > 0 ? '#101010' : '#ffffff';
                                    ctx.lineWidth = 1;
                                    const glyph = GLYPHS[v];
                                    ctx.fillText(glyph, c*CELL + CELL/2, canvasRow*CELL + CELL/2 + 2);
                                }
                            }
                        }
                    };
                    canvas.addEventListener('click', (e) => {
                        const rect = canvas.getBoundingClientRect();
                        const col = Math.floor((e.clientX - rect.left) / CELL);
                        const canvasRow = Math.floor((e.clientY - rect.top) / CELL);
                        const row = 7 - canvasRow;
                        window.liveViewPost('/chess-%1$s', row + ',' + col);
                    });
                })();
                """.formatted(id, CELL));

        Clerk.onPost(view, "/chess-" + id, this::handleClick);
        render();
    }

    public ChessView(LiveView view) {
        this(view, false);
    }

    private void handleClick(String response) {
        if (state.isTerminal()) return;
        String[] parts = response.trim().split(",");
        int row = Integer.parseInt(parts[0]), col = Integer.parseInt(parts[1]);
        Chess board = (Chess) state;

        if (selectedRow == null) {
            int piece = board.at(row, col);
            if (piece != Chess.EMPTY && pieceOwner(piece) == state.playerToMove()) {
                selectedRow = row;
                selectedCol = col;
                render();
            }
            return;
        }

        Chess.ChessMove move = new Chess.ChessMove(selectedRow, selectedCol, row, col);
        if (state.legalMoves().contains(move)) {
            state = state.apply(move);
            selectedRow = null;
            selectedCol = null;
            render();

            if (!state.isTerminal() && vsComputer) {
                AlphaBeta ab = new AlphaBeta(true, true);
                var decision = ab.bestMove(state, AI_DEPTH);
                if (decision.move() != null) state = state.apply(decision.move());
                render();
            }
            return;
        }

        // Zweiter Klick war kein legaler Zug: entweder neue eigene Figur auswaehlen, oder Auswahl aufheben.
        int piece = board.at(row, col);
        if (piece != Chess.EMPTY && pieceOwner(piece) == state.playerToMove()) {
            selectedRow = row;
            selectedCol = col;
        } else {
            selectedRow = null;
            selectedCol = null;
        }
        render();
    }

    private static int pieceOwner(int piece) { return piece > 0 ? 0 : 1; }

    private void render() {
        Chess c = (Chess) state;
        StringBuilder json = new StringBuilder("[");
        for (int r = 0; r < 8; r++) {
            json.append("[");
            for (int col = 0; col < 8; col++) {
                json.append(c.at(r, col));
                if (col < 7) json.append(",");
            }
            json.append("]");
            if (r < 7) json.append(",");
        }
        json.append("]");
        String sel = (selectedRow == null) ? "null,null" : (selectedRow + "," + selectedCol);
        Clerk.call(view, "window.renderChess" + id + "(" + json + "," + sel + ")");

        String status;
        if (state.isTerminal()) {
            boolean check = c.isInCheck(state.playerToMove());
            if (check) {
                status = (state.playerToMove() == 0 ? "Schwarz" : "Weiß") + " gewinnt durch Schachmatt!";
            } else {
                status = "Patt — Unentschieden.";
            }
        } else {
            String side = state.playerToMove() == 0 ? "Weiß" : "Schwarz";
            status = side + " ist am Zug" + (c.isInCheck(state.playerToMove()) ? " (Schach!)" : "") + ".";
        }
        Clerk.call(view, "document.getElementById('status" + id + "').textContent = " + jsString(status));
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
