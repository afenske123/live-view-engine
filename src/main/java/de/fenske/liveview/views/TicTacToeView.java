package de.fenske.liveview.views;

import de.fenske.liveview.Clerk;
import de.fenske.liveview.LiveView;
import de.fenske.liveview.ai.AlphaBeta;
import de.fenske.liveview.core.GameState;
import de.fenske.liveview.games.TicTacToe;

/**
 * Browser view for {@link TicTacToe}, in the same style as the original LiveView
 * demo views: a canvas is written to the page, a click handler posts the clicked
 * cell back to a per-instance server path, and the server redraws the board.
 *
 * <p>This is the direct descendant of the original TicTacToe LiveView exercise
 * (recovered from personal study notes) -- rewritten against the {@link TicTacToe}
 * {@code GameState} so it can optionally be played against the {@link AlphaBeta} engine.
 */
public class TicTacToeView {

    private final String id;
    private final LiveView view;
    private final boolean vsComputer;
    private GameState state = new TicTacToe();

    public TicTacToeView(LiveView view, boolean vsComputer) {
        this.view = view;
        this.vsComputer = vsComputer;
        this.id = Clerk.getHashID(this);

        Clerk.write(view, """
                <div class="status" id="status%s">Tic-Tac-Toe %s &mdash; X ist am Zug.</div>
                <canvas id="ttt%s" width="300" height="300"></canvas>
                """.formatted(id, vsComputer ? "(gegen den Computer)" : "(zwei Spieler)", id));

        Clerk.script(view, """
                (function() {
                    const canvas = document.getElementById('ttt%1$s');
                    const ctx = canvas.getContext('2d');
                    window.renderTtt%1$s = function(fields) {
                        ctx.clearRect(0, 0, 300, 300);
                        ctx.strokeStyle = '#9aa4b2';
                        ctx.lineWidth = 2;
                        for (let i = 1; i < 3; i++) {
                            ctx.beginPath(); ctx.moveTo(i * 100, 0); ctx.lineTo(i * 100, 300); ctx.stroke();
                            ctx.beginPath(); ctx.moveTo(0, i * 100); ctx.lineTo(300, i * 100); ctx.stroke();
                        }
                        ctx.font = '48px sans-serif';
                        ctx.textAlign = 'center';
                        ctx.textBaseline = 'middle';
                        for (let i = 0; i < 9; i++) {
                            const cx = (i %% 3) * 100 + 50, cy = Math.floor(i / 3) * 100 + 50;
                            if (fields[i] === 0) { ctx.fillStyle = '#5aa0ff'; ctx.fillText('X', cx, cy); }
                            else if (fields[i] === 1) { ctx.fillStyle = '#ff8a5a'; ctx.fillText('O', cx, cy); }
                        }
                    };
                    canvas.addEventListener('click', (e) => {
                        const rect = canvas.getBoundingClientRect();
                        const col = Math.floor((e.clientX - rect.left) / 100);
                        const row = Math.floor((e.clientY - rect.top) / 100);
                        window.liveViewPost('/ttt%1$s', row * 3 + col);
                    });
                })();
                """.formatted(id));

        Clerk.onPost(view, "/ttt" + id, response -> handleClick(Integer.parseInt(response.trim())));

        render();
    }

    public TicTacToeView(LiveView view) {
        this(view, false);
    }

    private void handleClick(int cell) {
        if (state.isTerminal()) return;
        TicTacToe.Cell move = new TicTacToe.Cell(cell);
        if (!state.legalMoves().contains(move)) return;
        state = state.apply(move);
        render();

        if (!state.isTerminal() && vsComputer) {
            AlphaBeta ab = new AlphaBeta(true, true);
            state = state.apply(ab.bestMove(state).move());
            render();
        }
    }

    private void render() {
        int[] fields = new int[9];
        TicTacToe s = (TicTacToe) state;
        for (int i = 0; i < 9; i++) {
            int v = s.at(i);
            fields[i] = v;
        }
        Clerk.call(view, "window.renderTtt" + id + "(" + java.util.Arrays.toString(fields) + ")");

        String status;
        if (state.isTerminal()) {
            int w = s.winner();
            status = (w == -1) ? "Unentschieden." : ((w == 0 ? "X" : "O") + " gewinnt!");
        } else {
            status = (state.playerToMove() == 0 ? "X" : "O") + " ist am Zug.";
        }
        Clerk.call(view, "document.getElementById('status" + id + "').textContent = " + jsString(status));
    }

    private static String jsString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
