package de.fenske.liveview;

import java.util.function.Consumer;

/**
 * Static convenience wrappers around {@link LiveView#sendServerEvent} that make it
 * pleasant to write a "view" class: a small class that owns a browser-rendered widget
 * and reacts to clicks/inputs coming back from it (see {@code views.*} for examples).
 */
public interface Clerk {

    static LiveView view(int port) {
        return LiveView.onPort(port);
    }

    static LiveView view() {
        return view(LiveView.getDefaultPort());
    }

    static String getHashID(Object o) {
        return Integer.toHexString(o.hashCode());
    }

    /** Sends raw HTML to the browser, where it is appended and rendered. */
    static void write(LiveView view, String html) {
        view.sendServerEvent(SSEType.WRITE, html);
    }

    /** Sends JavaScript to the browser for immediate execution. */
    static void call(LiveView view, String javascript) {
        view.sendServerEvent(SSEType.CALL, javascript);
    }

    /** Sends JavaScript that is wrapped in a script tag, inserted into the DOM and executed. */
    static void script(LiveView view, String javascript) {
        view.sendServerEvent(SSEType.SCRIPT, javascript);
    }

    /** Asks the browser to load a JavaScript library exactly once per view. */
    static void load(LiveView view, String path) {
        if (!view.paths.contains(path.trim())) view.sendServerEvent(SSEType.LOAD, path);
    }

    /** Clears everything previously written to the view. */
    static void clear(LiveView view) {
        view.sendServerEvent(SSEType.CLEAR, "");
    }

    static void clear() {
        clear(view());
    }

    /** Registers a handler for POSTs the browser sends back to {@code path} (e.g. a click). */
    static void onPost(LiveView view, String path, Consumer<String> delegate) {
        view.createResponseContext(path, delegate);
    }
}
