// Minimal client for the LiveView server-sent-events protocol.
//
// The server (LiveView.java / Clerk.java) pushes events of the shape
//   data: TYPE:base64Data\n\n
// over a single long-lived SSE connection at /events. This script decodes and
// applies them; loaded scripts talk back to the server with plain POST requests
// to whatever path a view registered with Clerk.write()/createResponseContext().

(function () {
    const eventsContainer = document.getElementById("events");
    const loadedLibraries = new Set();

    function decode(base64) {
        return decodeURIComponent(escape(window.atob(base64)));
    }

    function handleWrite(html) {
        const wrapper = document.createElement("div");
        wrapper.innerHTML = html;
        eventsContainer.appendChild(wrapper);
    }

    function handleCall(code) {
        try {
            (0, eval)(code);
        } catch (e) {
            console.error("LiveView CALL failed:", code, e);
        }
    }

    function handleScript(code) {
        const tag = document.createElement("script");
        tag.textContent = code;
        document.body.appendChild(tag);
    }

    function handleLoad(path) {
        // Two forms are possible: a single path, or "onlinePath, offlinePath".
        const [primary] = path.split(",").map(s => s.trim());
        if (loadedLibraries.has(primary)) {
            acknowledgeLoad();
            return;
        }
        const tag = document.createElement("script");
        tag.src = primary;
        tag.onload = () => {
            loadedLibraries.add(primary);
            acknowledgeLoad();
        };
        tag.onerror = () => {
            console.error("LiveView LOAD failed for", primary);
            acknowledgeLoad(); // acknowledge anyway so the server does not hang
        };
        document.body.appendChild(tag);
    }

    function acknowledgeLoad() {
        fetch("/loaded", { method: "POST" });
    }

    function handleClear() {
        eventsContainer.innerHTML = "";
    }

    function handleRelease(id) {
        document.dispatchEvent(new CustomEvent("liveview-release", { detail: { id } }));
    }

    const source = new EventSource("/events");
    source.onmessage = (event) => {
        const sepIndex = event.data.indexOf(":");
        const type = event.data.substring(0, sepIndex);
        const payload = decode(event.data.substring(sepIndex + 1));
        switch (type) {
            case "WRITE": handleWrite(payload); break;
            case "CALL": handleCall(payload); break;
            case "SCRIPT": handleScript(payload); break;
            case "LOAD": handleLoad(payload); break;
            case "CLEAR": handleClear(); break;
            case "RELEASE": handleRelease(payload); break;
            default: console.warn("Unknown LiveView event type:", type);
        }
    };
    source.onerror = () => console.warn("LiveView SSE connection lost, browser will retry automatically.");

    // Small helper every view's inline <script> uses to talk back to the server.
    window.liveViewPost = function (path, data) {
        fetch(path, { method: "POST", body: String(data) });
    };
})();
