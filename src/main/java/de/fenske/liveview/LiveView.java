package de.fenske.liveview;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A minimal "Live View" server: a small HTTP server that pushes HTML and JavaScript
 * to a connected browser tab in real time using Server-Sent-Events (SSE), so that a
 * running Java program can drive a live-updating view without a page reload.
 *
 * <p>Originally built as part of a university programming project (THM Gießen,
 * "Programmieren 2" — Live View Programming). This version cleans up the recovered
 * source (from personal study notes) and moves it into a proper Gradle project.</p>
 */
public class LiveView {

    final HttpServer server;
    final int port;
    static int defaultPort = 50_001;
    static final String index = "./web/index.html";
    static final Map<Integer, LiveView> views = new ConcurrentHashMap<>();
    final List<String> paths = new ArrayList<>();

    final List<HttpExchange> sseClientConnections;

    // Replay log so a browser tab that connects *after* some events were already sent
    // (e.g. the game views were created right at server startup) still sees full state.
    private final List<String> eventLog = new CopyOnWriteArrayList<>();

    // Lock used to temporarily block processing while a fresh SSEType.LOAD is
    // waiting for the browser to acknowledge that a script has finished loading.
    private final Lock lock = new ReentrantLock();
    private final Condition loadEventOccurredCondition = lock.newCondition();
    private boolean loadEventOccurred = false;

    static void setDefaultPort(int port) {
        defaultPort = port != 0 ? Math.abs(port) : 50_001;
    }

    static int getDefaultPort() {
        return defaultPort;
    }

    static LiveView onPort(int port) {
        port = Math.abs(port);
        try {
            if (!views.containsKey(port)) {
                views.put(port, new LiveView(port));
            }
            return views.get(port);
        } catch (IOException e) {
            System.err.printf("Error starting Server: %s%n", e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    static LiveView onPort() {
        return onPort(defaultPort);
    }

    private LiveView(int port) throws IOException {
        this.port = port;
        this.sseClientConnections = new CopyOnWriteArrayList<>();

        server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        System.out.println("Open http://localhost:" + port + " in your browser");

        // Acknowledges that the browser finished processing an SSEType.LOAD event.
        server.createContext("/loaded", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
            lock.lock();
            try {
                loadEventOccurred = true;
                loadEventOccurredCondition.signalAll();
            } finally {
                lock.unlock();
            }
        });

        // The long-lived SSE connection the browser opens once on page load.
        server.createContext("/events", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().add("Cache-Control", "no-cache");
            exchange.getResponseHeaders().add("Connection", "keep-alive");
            exchange.sendResponseHeaders(200, 0);
            try {
                for (String logged : eventLog) {
                    exchange.getResponseBody().write(("data: " + logged + "\n\n").getBytes(StandardCharsets.UTF_8));
                }
                exchange.getResponseBody().flush();
            } catch (IOException e) {
                // Client disconnected before the replay finished; it will simply not be added below.
                return;
            }
            sseClientConnections.add(exchange);
        });

        // Serves the initial page and any static asset requested underneath it.
        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            final String path = exchange.getRequestURI().getPath().equals("/")
                    ? index
                    : "./web" + exchange.getRequestURI().getPath();
            try (InputStream stream = new FileInputStream(path)) {
                final byte[] bytes = stream.readAllBytes();
                String contentType = Files.probeContentType(Path.of(path));
                exchange.getResponseHeaders().add("Content-Type",
                        (contentType != null ? contentType : "application/octet-stream") + "; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().flush();
            } catch (IOException e) {
                exchange.sendResponseHeaders(404, -1);
            } finally {
                exchange.close();
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(5));
        server.start();
    }

    void sendServerEvent(SSEType sseType, String data) {
        byte[] binaryData = data.getBytes(StandardCharsets.UTF_8);
        String base64Data = Base64.getEncoder().encodeToString(binaryData);
        String logged = sseType + ":" + base64Data;

        // RELEASE just unblocks a specific in-flight request and CLEAR wipes prior state,
        // so neither belongs in the replay log the same way WRITE/CALL/SCRIPT/LOAD do.
        if (sseType == SSEType.CLEAR) {
            eventLog.clear();
        } else if (sseType != SSEType.RELEASE) {
            eventLog.add(logged);
        }

        List<HttpExchange> deadConnections = new ArrayList<>();
        for (HttpExchange connection : sseClientConnections) {
            if (sseType == SSEType.LOAD) lock.lock();
            try {
                String message = "data: " + logged + "\n\n";
                connection.getResponseBody().write(message.getBytes(StandardCharsets.UTF_8));
                connection.getResponseBody().flush();
                if (sseType == SSEType.LOAD && !loadEventOccurred) {
                    loadEventOccurredCondition.await(1_000, TimeUnit.MILLISECONDS);
                    if (loadEventOccurred) paths.add(data);
                    else System.err.println("LOAD-Timeout: " + data);
                }
            } catch (IOException e) {
                deadConnections.add(connection);
            } catch (InterruptedException e) {
                System.err.println("LOAD-Timeout: " + data + ", " + e);
            } finally {
                if (sseType == SSEType.LOAD) {
                    loadEventOccurred = false;
                    lock.unlock();
                }
            }
        }
        sseClientConnections.removeAll(deadConnections);
    }

    public void createResponseContext(String path, Consumer<String> delegate) {
        createResponseContext(path, delegate, "-1");
    }

    public void createResponseContext(String path, Consumer<String> delegate, String id) {
        server.createContext(path, exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String contentLength = exchange.getRequestHeaders().getFirst("Content-length");
            if (contentLength == null) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            try {
                int length = Integer.parseInt(contentLength);
                byte[] data = new byte[length];
                exchange.getRequestBody().read(data);
                delegate.accept(new String(data, StandardCharsets.UTF_8));
                sendServerEvent(SSEType.RELEASE, id);
            } catch (NumberFormatException e) {
                exchange.sendResponseHeaders(400, -1);
                return;
            }

            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
    }

    public void stop() {
        sseClientConnections.clear();
        views.remove(port);
        server.stop(0);
    }

    static void shutdown() {
        views.forEach((k, v) -> v.stop());
    }

    static String generateID(int n) {
        return new Random().ints(n, 0, 36)
                .mapToObj(i -> Integer.toString(i, 36))
                .collect(Collectors.joining());
    }
}
