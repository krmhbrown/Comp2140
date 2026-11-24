package peakfinds.routes;

import peakfinds.AppContext;
import peakfinds.auth.LoginUI;
import peakfinds.auth.SessionManager;
import peakfinds.web.Request;
import peakfinds.web.Response;
import peakfinds.web.Router;

/** Pages for signing in and out: /, /login and /logout. */
public final class AuthRoutes {
    private AuthRoutes() {}

    public static void register(AppContext ctx) {
        Router router = ctx.router();
        LoginUI loginUI = new LoginUI(ctx.auth(), ctx.sessions(), ctx.config().secureCookies());

        // Home: send logged-in users to their dashboard, everyone else to the catalogue.
        router.get("/", req -> Response.redirect(
                req.session() == null ? "/catalogue" : LoginUI.homeFor(req.session().role())));

        router.get("/login", req -> req.session() != null
                ? Response.redirect(LoginUI.homeFor(req.session().role()))
                : loginUI.loginForm(req));

        router.post("/login", loginUI::submitInfo);

        router.post("/logout", req -> logout(ctx, req));
    }

    private static Response logout(AppContext ctx, Request req) {
        ctx.auth().logOut(ctx.sessions(), req.cookie(Router.SESSION_COOKIE));
        return Response.redirect("/login")
                .header("Set-Cookie", SessionManager.clearCookieHeader(ctx.config().secureCookies()));
    }
}
