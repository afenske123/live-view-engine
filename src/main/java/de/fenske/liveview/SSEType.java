package de.fenske.liveview;

/** The kinds of server-sent events the {@link LiveView} can push to the browser. */
enum SSEType {
    WRITE, CALL, SCRIPT, LOAD, CLEAR, RELEASE
}
