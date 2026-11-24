package peakfinds.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import peakfinds.auth.PrivilegeSys;
import peakfinds.auth.Session;
import peakfinds.auth.SessionManager;
import peakfinds.auth.ValidationException;
import peakfinds.ui.Layout;

/**
 * The gatekeeper every request passes through (the "Form and Page Manager" plus the role check).
 * Pages are registered with a URL pattern and, for protected pages, the permission they need:
 *
 *   router.get("/owner/products", Action.MANAGE_PRODUCTS, req -> ...);   // owner only
 *   router.get("/catalogue", req -> ...);                               // public
 *   router.get("/owner/orders/{id}", Action.MANAGE_ORDERS, req -> ... req.pathLong("id") ...);
 *
 * For a protected page the router checks, in order: logged in? (else go to /login), role allowed? (else 403),
 * and for POST requests, a valid anti-forgery token. Registering the same pattern twice replaces the first,
 * which is how a feature owner swaps the placeholder page for the real one.
 */
public final class Router implements HttpHandler {
    public static final String SESSION_COOKIE = "PF_SESSION";
    /** Pages that may be POSTed without an anti-forgery token (the login form has no session yet). */
    private static final Set<String> CSRF_EXEMPT = Set.of("/login");

    /** A page handler. It may throw; the router turns exceptions into error pages. */
    @FunctionalInterface
    public interface Handler {
        Response handle(Request request) throws Exception;
    }

    private record Route(String method, String pattern, String[] segments, String action, Handler handler) {
        int literalCount() {
            int n = 0;
            for (String s : segments) {
                if (!s.startsWith("{")) {
                    n++;
                }
            }
            return n;
        }
    }

    private record Match(Route route, Map<String, String> params) {}

    private final Map<String, Route> routes = new LinkedHashMap<>();
    private final SessionManager sessions;
    private final PrivilegeSys privileges;

    public Router(SessionManager sessions, PrivilegeSys privileges) {
        this.sessions = sessions;
        this.privileges = privileges;
    }

    /** Registers a public GET page. */
    public Router get(String pattern, Handler handler) {
        return add("GET", pattern, null, handler);
    }

    /** Registers a GET page that needs the given permission (see Constants.Action). */
    public Router get(String pattern, String action, Handler handler) {
        return add("GET", pattern, action, handler);
    }

    /** Registers a public POST handler. */
    public Router post(String pattern, Handler handler) {
        return add("POST", pattern, null, handler);
    }

    /** Registers a POST handler that needs the given permission. */
    public Router post(String pattern, String action, Handler handler) {
        return add("POST", pattern, action, handler);
    }

    private Router add(String method, String pattern, String action, Handler handler) {
        Route route = new Route(method, pattern, split(pattern), action, handler);
        if (routes.put(method + " " + pattern, route) != null) {
            System.out.println("Route replaced: " + method + " " + pattern);
        }
        return this;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            Request request = new Request(exchange);
            dispatch(request).send(exchange);
        } catch (Throwable t) {
            t.printStackTrace();
            try {
                errorPage(null, 500, "Something went wrong", "Please try again in a moment.").send(exchange);
            } catch (Exception ignored) {
                // the connection is probably already gone
            }
        } finally {
            exchange.close();
        }
    }

    private Response dispatch(Request req) {
        String path = req.path();

        if (path.startsWith("/static/")) {
            Response file = "GET".equals(req.method()) ? StaticFiles.serve(path) : null;
            return file != null ? file : errorPage(null, 404, "Page not found", "That page does not exist.");
        }

        String token = req.cookie(SESSION_COOKIE);
        Session session = token == null ? null : sessions.get(token);
        req.attach(session);

        try {
            boolean pathExists = false;
            Match best = null;
            String[] segments = split(path);
            for (Route route : routes.values()) {
                Map<String, String> params = match(route.segments(), segments);
                if (params == null) {
                    continue;
                }
                pathExists = true;
                if (route.method().equals(req.method())
                        && (best == null || route.literalCount() > best.route().literalCount())) {
                    best = new Match(route, params);
                }
            }
            if (best == null) {
                return pathExists
                        ? errorPage(session, 405, "Not allowed", "That page does not accept this kind of request.")
                        : errorPage(session, 404, "Page not found", "That page does not exist.");
            }

            Route route = best.route();
            if (route.action() != null) {
                if (session == null) {
                    String next = "GET".equals(req.method()) ? req.pathAndQuery() : "/";
                    return Response.redirect("/login?next=" + URLEncoder.encode(next, StandardCharsets.UTF_8));
                }
                if (!privileges.checkPermission(session.role(), route.action())) {
                    return errorPage(session, 403, "Access denied", "You do not have permission to open that page.");
                }
            }

            boolean changesData = !"GET".equals(req.method());
            if (changesData && session != null && !CSRF_EXEMPT.contains(path) && !tokenMatches(req, session)) {
                return errorPage(session, 403, "Form expired",
                        "That form is no longer valid. Go back, refresh the page and try again.");
            }

            req.attachPathParams(best.params());
            return route.handler().handle(req);
        } catch (ValidationException | BadRequestException e) {
            return errorPage(session, 400, "Please check your details", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return errorPage(session, 500, "Something went wrong", "Please try again in a moment.");
        }
    }

    /** Builds an error page in the normal site layout. */
    public static Response errorPage(Session session, int status, String title, String message) {
        String content = "<h1>" + Html.esc(title) + "</h1><p>" + Html.esc(message) + "</p>"
                + "<p><a href=\"/\">Back to the home page</a></p>";
        return Response.html(status, Layout.page(session, title, content));
    }

    private static boolean tokenMatches(Request req, Session session) {
        String sent = req.csrfToken();
        return sent != null && MessageDigest.isEqual(
                sent.getBytes(StandardCharsets.UTF_8), session.csrfToken().getBytes(StandardCharsets.UTF_8));
    }

    private static String[] split(String path) {
        String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return trimmed.equals("/") ? new String[0] : trimmed.substring(1).split("/", -1);
    }

    /** Returns the {name} values if the URL matches the pattern, otherwise null. */
    private static Map<String, String> match(String[] pattern, String[] actual) {
        if (pattern.length != actual.length) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < pattern.length; i++) {
            if (pattern[i].startsWith("{") && pattern[i].endsWith("}")) {
                if (actual[i].isEmpty()) {
                    return null;
                }
                params.put(pattern[i].substring(1, pattern[i].length() - 1), actual[i]);
            } else if (!pattern[i].equals(actual[i])) {
                return null;
            }
        }
        return params;
    }
}
