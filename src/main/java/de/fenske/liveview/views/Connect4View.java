package de.fenske.liveview.views;

import de.fenske.liveview.Clerk;
import de.fenske.liveview.LiveView;
import de.fenske.liveview.ai.AlphaBeta;
import de.fenske.liveview.core.GameState;
import de.fenske.liveview.games.Connect4;

import java.util.List;

/** Browser view for {@link Connect4}: click a column to drop a disc, same LiveView pattern as {@link TicTacToeView}. */
public class Connect4View {

    private static final int AI_DEPTH = 6;
    private static final int CELL = 60;

    private final String id;
    private final LiveView view;
    private final boolean vsComputer;
    private GameState state = new Connect4();

    public Connect4View(LiveView view, boolean vsComputer) {
        this.view = view;
        this.vsComputer = vsComputer;
        this.id = Clerk.getHashID(this);
        int w = Connect4.WIDTH * CELL, h = Connect4.HEIGHT * CELL;

        Clerk.write(view, """
                <div class="status" id="status%s">Vier gewinnt %s &mdash; Rot ist am Zug.</div>
                <canvas id="c4-%s" width="%d" height="%d"></canvas>
                """.formatted(id, vsComputer ? "(gegen den Computer)" : "(zwei Spieler)", id, w, h));

        Clerk.script(view, """
                (function() {
                    const CELL = %2$d, COLS = %3$d, ROWS = %4$d;
                    const canvas = document.getElementById('c4-%1$s');
                    const ctx = canvas.getContext('2d');
                    window.renderC4%1$s = function(board) {
                        ctx.fillStyle = '#2b5cad';
                        ctx.fillRect(0, 0, canvas.width, canvas.height);
                        for (let c = 0; c < COLS; c++) {
                            for (let r = 0; r < ROWS; r++) {
                                const v = board[c][ROWS - 1 - r];
                                ctx.beginPath();
                                ctx.arc(c * CELL + CELL/2, r * CELL + CELL/2, CELL/2 - 6, 0, Math.PI * 2);
                                ctx.fillStyle = v === 0 ? '#ff5a5a' : v === 1 ? '#ffd75a' : '#1b1f24';
                                ctx.fill();
                            }
                        }
                    };
                    canvas.addEventListener('click', (e) => {
                        const rect = canvas.getBoundingClientRect();
                        const col = Math.floor((e.clientX - rect.left) / CELL);
                        window.liveViewPost('/c4-%1$s', col);
                    });
                })();
                """.formatted(id, CELL, Connect4.WIDTH, Connect4.HEIGHT));

        Clerk.onPost(view, "/c4-" + id, response -> handleClick(Integer.parseInt(response.trim())));
        render();
    }

    public Connect4View(LiveView view) {
        this(view, false);
    }

    private void handleClick(int column) {
        if (state.isTerminal()) return;
        Connect4.Drop move = new Connect4.Drop(column);
        if (!state.legalMoves().contains(move)) return;
        state = state.apply(move);
        render();

        if (!state.isTerminal() && vsComputer) {
            AlphaBeta ab = new AlphaBeta(true, true);
            List<de.fenske.liveview.core.Move> legal = state.legalMoves();
            var decision = ab.bestMove(state, AI_DEPTH);
            state = state.apply(decision.move() != null ? decision.move() : legal.get(0));
            render();
        }
    }

    private void render() {
        Connect4 c4 = (Connect4) state;
        StringBuilder json = new StringBuilder("[");
        for (int c = 0; c < Connect4.WIDTH; c++) {
            json.append("[");
            for (int r = 0; r < Connect4.HEIGHT; r++) {
                json.append(c4.at(c, r));
                if (r < Connect4.HEIGHT - 1) json.append(",");
            }
            json.append("]");
            if (c < Connect4.WIDTH - 1) json.append(",");
        }
        json.append("]");
        Clerk.call(view, "window.renderC4" + id + "(" + json + ")");

        String status;
        if (state.isTerminal()) {
            int redUtility = c4.utility(0);
            status = redUtility == 0 ? "Unentschieden." : (redUtility > 0 ? "Rot gewinnt!" : "Gelb gewinnt!");
        } else {
            status = (state.playerToMove() == 0 ? "Rot" : "Gelb") + " ist am Zug.";
        }
        Clerk.call(view, "document.getElementById('status" + id + "').textContent = " + jsString(status));
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
