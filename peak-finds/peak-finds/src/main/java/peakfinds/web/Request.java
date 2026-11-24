package peakfinds.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import peakfinds.auth.Session;

/** One incoming browser request: method, path, URL values, form values, cookies and the logged-in session. */
public final class Request {
    private static final int MAX_BODY = 10 * 1024 * 1024;
    private static final int MAX_FORM = 1024 * 1024;

    private final HttpExchange exchange;
    private final Map<String, String> query;
    private Map<String, String> form;
    private Map<String, String> pathParams = Map.of();
    private byte[] body;
    private Session session;

    public Request(HttpExchange exchange) {
        this.exchange = exchange;
        this.query = parseEncoded(exchange.getRequestURI().getRawQuery());
    }

    /** GET, POST, etc. */
    public String method() {
        return exchange.getRequestMethod();
    }

    /** The URL path without the query string, e.g. /owner/orders/12. */
    public String path() {
        return exchange.getRequestURI().getPath();
    }

    /** The path plus ?query, used for "return to this page after login". */
    public String pathAndQuery() {
        String q = exchange.getRequestURI().getRawQuery();
        return path() + (q == null ? "" : "?" + q);
    }

    /** A value from the URL query string (?name=value), or null. */
    public String query(String name) {
        return query.get(name);
    }

    /** A value submitted from an HTML form (POST), or null. */
    public String form(String name) {
        return form().get(name);
    }

    /** A {id}-style value from the route pattern, or null. */
    public String pathParam(String name) {
        return pathParams.get(name);
    }

    /** A {id}-style value as a number. Throws BadRequestException (a 400 page) if it is not a number. */
    public long pathLong(String name) {
        String v = pathParams.get(name);
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException | NullPointerException e) {
            throw new BadRequestException("That link is not valid.");
        }
    }

    /** Looks for a value in the route pattern, then the form, then the query string. */
    public String param(String name) {
        String v = pathParams.get(name);
        if (v == null) {
            v = form().get(name);
        }
        return v != null ? v : query.get(name);
    }

    /** The logged-in user's session, or null if nobody is logged in. */
    public Session session() {
        return session;
    }

    /** A cookie sent by the browser, or null. */
    public String cookie(String name) {
        List<String> headers = exchange.getRequestHeaders().get("Cookie");
        if (headers == null) {
            return null;
        }
        for (String header : headers) {
            for (String pair : header.split(";")) {
                String p = pair.trim();
                int eq = p.indexOf('=');
                if (eq > 0 && p.substring(0, eq).equals(name)) {
                    return p.substring(eq + 1);
                }
            }
        }
        return null;
    }

    /** A request header, or null. */
    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    /**
     * The anti-forgery token the browser sent: X-CSRF-Token header, then ?_csrf= in the URL
     * (use this for file-upload forms), then the _csrf form field.
     */
    public String csrfToken() {
        String t = header("X-CSRF-Token");
        if (t == null) {
            t = query.get("_csrf");
        }
        if (t == null && isFormEncoded()) {
            t = form().get("_csrf");
        }
        return t;
    }

    /** The raw request body (for uploads), limited to 10 MB. */
    public byte[] bodyBytes() {
        if (body == null) {
            try {
                body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            } catch (IOException e) {
                throw new BadRequestException("Could not read the request.");
            }
            if (body.length > MAX_BODY) {
                throw new BadRequestException("That upload is too large.");
            }
        }
        return body;
    }

    // ---- used by the router ----

    void attach(Session session) {
        this.session = session;
    }

    void attachPathParams(Map<String, String> params) {
        this.pathParams = params;
    }

    // ---- internals ----

    private boolean isFormEncoded() {
        String type = header("Content-Type");
        return type != null && type.toLowerCase().startsWith("application/x-www-form-urlencoded");
    }

    private Map<String, String> form() {
        if (form == null) {
            if (isFormEncoded()) {
                byte[] bytes = bodyBytes();
                if (bytes.length > MAX_FORM) {
                    throw new BadRequestException("That form is too large.");
                }
                form = parseEncoded(new String(bytes, StandardCharsets.UTF_8));
            } else {
                form = Map.of();
            }
        }
        return form;
    }

    private static Map<String, String> parseEncoded(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            result.putIfAbsent(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("The request contained invalid text.");
        }
    }
}
