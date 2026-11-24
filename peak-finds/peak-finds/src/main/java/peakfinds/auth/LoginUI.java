package peakfinds.auth;

import java.util.Map;
import java.util.Optional;
import peakfinds.Constants;
import peakfinds.ui.Layout;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;
import peakfinds.web.TemplateRenderer;

/**
 * The login page and the login step (SDS class "LoginUI"): shows the form, checks the details with
 * AdminAuth, starts the session, and sends each role to its own home page.
 */
public final class LoginUI {
    private final AdminAuth auth;
    private final SessionManager sessions;
    private final boolean secureCookies;
    private final LoginThrottle throttle = new LoginThrottle();

    public LoginUI(AdminAuth auth, SessionManager sessions, boolean secureCookies) {
        this.auth = auth;
        this.sessions = sessions;
        this.secureCookies = secureCookies;
    }

    /** Shows the login form. */
    public Response loginForm(Request req) {
        return render(200, req.query("next"), "", "");
    }

    /** Handles the submitted form: checks the details and either logs the user in or shows an error. */
    public Response submitInfo(Request req) {
        String email = Validator.clean(req.form("email"));
        String password = req.form("password") == null ? "" : req.form("password");
        String next = req.form("next");

        if (email.isEmpty() || password.isEmpty()) {
            return displayError(400, next, email, "Enter your email and password.");
        }
        String key = email.toLowerCase();
        if (throttle.isBlocked(key)) {
            return displayError(429, next, email, "Too many failed attempts. Please wait a few minutes and try again.");
        }

        Optional<Session> session = auth.authenticateUser(email, password);
        if (session.isEmpty()) {
            throttle.recordFailure(key);
            return displayError(401, next, email, "Incorrect email or password.");
        }

        throttle.clear(key);
        String token = sessions.create(session.get());   // a brand-new token on every login
        return Response.redirect(redirectUser(session.get().role(), next))
                .header("Set-Cookie", SessionManager.cookieHeader(token, secureCookies));
    }

    /** Shows the login form again with an error message. */
    public Response displayError(int status, String next, String email, String message) {
        return render(status, next, email, "<p class=\"error\" role=\"alert\">" + Html.esc(message) + "</p>");
    }

    /**
     * Where to send a user after login: the page they originally wanted if it is a safe page on this site
     * that their role can have, otherwise their role's home page.
     */
    public String redirectUser(String role, String next) {
        if (next != null && isSafeLocalPath(next) && fitsRole(role, next)) {
            return next;
        }
        return homeFor(role);
    }

    /** The home page for a role. */
    public static String homeFor(String role) {
        return switch (role) {
            case Constants.ROLE_OWNER -> "/owner";
            case Constants.ROLE_BEARER -> "/delivery";
            default -> "/customer";
        };
    }

    private static boolean isSafeLocalPath(String next) {
        // Must start with a single "/" so it can never point at another website.
        return next.startsWith("/") && !next.startsWith("//") && !next.contains("\\")
                && !next.contains("\r") && !next.contains("\n") && !next.startsWith("/login");
    }

    private static boolean fitsRole(String role, String next) {
        return switch (role) {
            case Constants.ROLE_OWNER -> next.startsWith("/owner");
            case Constants.ROLE_BEARER -> next.startsWith("/delivery");
            default -> !next.startsWith("/owner") && !next.startsWith("/delivery");
        };
    }

    private Response render(int status, String next, String email, String errorHtml) {
        String form = TemplateRenderer.render("auth/login.html", Map.of(
                "error", errorHtml,
                "email", Html.esc(email),
                "next", Html.esc(next == null ? "" : next)));
        return Response.html(status, Layout.page(null, "Log in", form));
    }
}
