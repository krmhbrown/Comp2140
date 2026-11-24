package peakfinds.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the server sends back: a status code, headers and a body. Build one with the static helpers. */
public final class Response {
    private int status;
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    private byte[] body;

    private Response(int status, String contentType, byte[] body) {
        this.status = status;
        this.body = body;
        if (contentType != null) {
            header("Content-Type", contentType);
        }
    }

    /** A 200 HTML page. */
    public static Response html(String html) {
        return html(200, html);
    }

    /** An HTML page with a chosen status code (e.g. 401, 403, 404). */
    public static Response html(int status, String html) {
        return new Response(status, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
    }

    /** A JSON reply (the SDS says data between browser and server is JSON). Pass already-built JSON text. */
    public static Response json(int status, String json) {
        return new Response(status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    /** Plain text. */
    public static Response text(int status, String text) {
        return new Response(status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    /** Sends the browser to another page (303 See Other, which is correct after a form POST). */
    public static Response redirect(String location) {
        return new Response(303, null, new byte[0]).header("Location", location);
    }

    /** A file's bytes with the given content type. */
    public static Response bytes(int status, String contentType, byte[] data) {
        return new Response(status, contentType, data);
    }

    /** Adds a header (use for Set-Cookie too; calling twice sends two). */
    public Response header(String name, String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        return this;
    }

    public int status() {
        return status;
    }

    public String firstHeader(String name) {
        List<String> v = headers.get(name);
        return v == null || v.isEmpty() ? null : v.get(0);
    }

    /** Writes this response to the browser, adding the standard security headers. */
    void send(HttpExchange ex) throws IOException {
        headers.putIfAbsent("X-Content-Type-Options", List.of("nosniff"));
        headers.putIfAbsent("X-Frame-Options", List.of("DENY"));
        headers.putIfAbsent("Referrer-Policy", List.of("same-origin"));
        headers.putIfAbsent("Content-Security-Policy",
                List.of("default-src 'self'; img-src 'self' data:; frame-ancestors 'none'"));
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            for (String value : e.getValue()) {
                ex.getResponseHeaders().add(e.getKey(), value);
            }
        }
        if (body.length == 0) {
            ex.sendResponseHeaders(status, -1);
        } else {
            ex.sendResponseHeaders(status, body.length);
            try (OutputStream out = ex.getResponseBody()) {
                out.write(body);
            }
        }
    }
}
