package de.fenske.liveview;

import de.fenske.liveview.views.ChessView;
import de.fenske.liveview.views.Connect4View;
import de.fenske.liveview.views.OthelloView;
import de.fenske.liveview.views.TicTacToeView;

/**
 * Starts the LiveView server and mounts all four demo games on one page.
 * Open http://localhost:50001 in a browser after running this.
 */
public class Main {
    public static void main(String[] args) {
        LiveView view = Clerk.view();

        new TicTacToeView(view, true);
        new Connect4View(view, true);
        new OthelloView(view, true);
        new ChessView(view, true);

        Runtime.getRuntime().addShutdownHook(new Thread(LiveView::shutdown));
    }
}
