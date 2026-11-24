package peakfinds.ui;

import peakfinds.web.Html;
import peakfinds.web.Response;
import peakfinds.web.Router;

/**
 * Placeholder pages so every menu link works from day one. A feature owner replaces a stub by registering
 * the same URL from their own routes class (the router keeps the later registration).
 */
public final class StubPages {
    private StubPages() {}

    /** Registers a placeholder GET page. Pass a null action to make it public. */
    public static void register(Router router, String path, String action, String title) {
        Router.Handler handler = req -> Response.html(Layout.page(req.session(), title,
                "<h1>" + Html.esc(title) + "</h1>"
                        + "<p class=\"notice\">This page is a placeholder from the shared setup. "
                        + "Its feature owner will replace it.</p>"));
        if (action == null) {
            router.get(path, handler);
        } else {
            router.get(path, action, handler);
        }
    }
}
