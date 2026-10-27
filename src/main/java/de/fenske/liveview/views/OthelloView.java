package de.fenske.liveview.views;

import de.fenske.liveview.Clerk;
import de.fenske.liveview.LiveView;
import de.fenske.liveview.ai.AlphaBeta;
import de.fenske.liveview.core.GameState;
import de.fenske.liveview.core.Move;
import de.fenske.liveview.games.Othello;

/** Browser view for {@link Othello}. Click a square to place a disc; passing happens automatically. */
public class OthelloView {

    private static final int AI_DEPTH = 5;
    private static final int CELL = 50;

    private final String id;
    private final LiveView view;
    private final boolean vsComputer;
    private GameState state = new Othello();

    public OthelloView(LiveView view, boolean vsComputer) {
        this.view = view;
        this.vsComputer = vsComputer;
        this.id = Clerk.getHashID(this);
        int size = Othello.SIZE * CELL;

        Clerk.write(view, """
                <div class="status" id="status%s">Othello %s &mdash; Schwarz ist am Zug.</div>
                <canvas id="oth-%s" width="%d" height="%d"></canvas>
                """.formatted(id, vsComputer ? "(gegen den Computer)" : "(zwei Spieler)", id, size, size));

        Clerk.script(view, """
                (function() {
                    const CELL = %2$d, SIZE = %3$d;
                    const canvas = document.getElementById('oth-%1$s');
                    const ctx = canvas.getContext('2d');
                    window.renderOth%1$s = function(board) {
                        ctx.fillStyle = '#1f7a3f';
                        ctx.fillRect(0, 0, canvas.width, canvas.height);
                        ctx.strokeStyle = '#0f3d20';
                        for (let i = 0; i <= SIZE; i++) {
                            ctx.beginPath(); ctx.moveTo(i*CELL, 0); ctx.lineTo(i*CELL, SIZE*CELL); ctx.stroke();
                            ctx.beginPath(); ctx.moveTo(0, i*CELL); ctx.lineTo(SIZE*CELL, i*CELL); ctx.stroke();
                        }
                        for (let r = 0; r < SIZE; r++) {
                            for (let c = 0; c < SIZE; c++) {
                                const v = board[r][c];
                                if (v === -1) continue;
                                ctx.beginPath();
                                ctx.arc(c*CELL + CELL/2, r*CELL + CELL/2, CELL/2 - 5, 0, Math.PI*2);
                                ctx.fillStyle = v === 0 ? '#101010' : '#f2f2f2';
                                ctx.fill();
                            }
                        }
                    };
                    canvas.addEventListener('click', (e) => {
                        const rect = canvas.getBoundingClientRect();
                        const col = Math.floor((e.clientX - rect.left) / CELL);
                        const row = Math.floor((e.clientY - rect.top) / CELL);
                        window.liveViewPost('/oth-%1$s', row + ',' + col);
                    });
                })();
                """.formatted(id, CELL, Othello.SIZE));

        Clerk.onPost(view, "/oth-" + id, this::handleClick);
        render();
    }

    public OthelloView(LiveView view) {
        this(view, false);
    }

    private void handleClick(String response) {
        if (state.isTerminal()) return;
        String[] parts = response.trim().split(",");
        int row = Integer.parseInt(parts[0]), col = Integer.parseInt(parts[1]);
        Othello.Place move = new Othello.Place(row, col);
        if (!state.legalMoves().contains(move)) return;
        applyAndMaybePass(move);
    }

    private void applyAndMaybePass(Move move) {
        state = state.apply(move);
        // Falls der Gegner keinen Zug hat, setzt er automatisch aus (per Regel).
        while (!state.isTerminal() && state.legalMoves().size() == 1
                && state.legalMoves().get(0) instanceof Othello.Pass) {
            state = state.apply(state.legalMoves().get(0));
        }
        render();

        if (!state.isTerminal() && vsComputer && state.playerToMove() == 1) {
            AlphaBeta ab = new AlphaBeta(true, true);
            var decision = ab.bestMove(state, AI_DEPTH);
            applyAndMaybePass(decision.move());
        }
    }

    private void render() {
        Othello o = (Othello) state;
        StringBuilder json = new StringBuilder("[");
        for (int r = 0; r < Othello.SIZE; r++) {
            json.append("[");
            for (int c = 0; c < Othello.SIZE; c++) {
                json.append(o.at(r, c));
                if (c < Othello.SIZE - 1) json.append(",");
            }
            json.append("]");
            if (r < Othello.SIZE - 1) json.append(",");
        }
        json.append("]");
        Clerk.call(view, "window.renderOth" + id + "(" + json + ")");

        String status;
        if (state.isTerminal()) {
            int blackUtility = o.utility(0);
            status = blackUtility == 0 ? "Unentschieden." : (blackUtility > 0 ? "Schwarz gewinnt!" : "Weiß gewinnt!");
        } else {
            status = (state.playerToMove() == 0 ? "Schwarz" : "Weiß") + " ist am Zug.";
        }
        Clerk.call(view, "document.getElementById('status" + id + "').textContent = " + jsString(status));
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
