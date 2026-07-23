// Author: Othmane

package web.server;

import com.sun.net.httpserver.HttpExchange;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP and Server-Sent Events plumbing shared by the endpoints. Nothing here
 * knows about VRP instances or the solver, so handlers stay free of transport
 * details.
 *
 * @author Othmane EL YAAKOUBI
 */
final class Http {

    private Http() {
        // Static helpers only.
    }

    /**
     * Parses the query string of the request into a key-value map.
     *
     * @param exchange the HTTP exchange
     * @return the parsed query parameters
     */
    static Map<String, String> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        Map<String, String> params = new HashMap<>();
        if (raw != null) {
            for (String pair : raw.split("&")) {
                int i = pair.indexOf('=');
                if (i > 0) {
                    params.put(pair.substring(0, i), URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
                }
            }
        }
        return params;
    }

    /**
     * Sends the given strings as a JSON string array, escaping quotes and
     * backslashes.
     *
     * @param ex the HTTP exchange
     * @param items the strings to encode
     * @throws IOException when writing the response fails
     */
    static void json(HttpExchange ex, List<String> items) throws IOException {
        String body = "[" + items.stream()
                .map(s -> "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "]";
        send(ex, 200, "application/json", body.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sends a file as the response body, or a 404 when it is unresolved or
     * missing.
     *
     * @param ex the HTTP exchange
     * @param f the file to serve, possibly {@code null}
     * @param type the MIME type to set
     * @throws IOException when reading the file or writing the response fails
     */
    static void serveFile(HttpExchange ex, File f, String type) throws IOException {
        if (f == null || !f.exists()) {
            notFound(ex);
            return;
        }
        send(ex, 200, type, Files.readAllBytes(f.toPath()));
    }

    /**
     * Sends a plain-text 404 response.
     *
     * @param ex the HTTP exchange
     * @throws IOException when writing the response fails
     */
    static void notFound(HttpExchange ex) throws IOException {
        send(ex, 404, "text/plain", "Not found".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sends an HTTP response with the given status, content type, and body.
     *
     * @param ex the HTTP exchange
     * @param code the HTTP status code
     * @param type the content type
     * @param body the response body
     * @throws IOException when writing the response fails
     */
    static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    /**
     * Writes a single Server-Sent Event to the output stream.
     *
     * @param out the output stream
     * @param event the event name
     * @param data the event data, possibly multi-line
     * @throws IOException when writing fails
     */
    static void sse(OutputStream out, String event, String data) throws IOException {
        StringBuilder sb = new StringBuilder("event: ").append(event).append('\n');
        for (String line : data.split("\n", -1))
            sb.append("data: ").append(line).append('\n');
        sb.append('\n');
        // Locked so the watchdog's ping cannot interleave with a solver log line.
        synchronized (out) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
