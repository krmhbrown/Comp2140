package peakfinds.ui;

import java.util.Map;
import peakfinds.Constants;
import peakfinds.auth.Session;
import peakfinds.web.Html;
import peakfinds.web.TemplateRenderer;

/**
 * The shared page frame (templates/base.html): header, role-specific menu, footer.
 * Every page goes through Layout.page so the whole site looks and behaves the same.
 */
public final class Layout {
    private Layout() {}

    /**
     * Wraps page content in the site frame.
     * @param session the logged-in user, or null for visitors (decides which menu is shown)
     * @param title   plain text for the browser tab (escaped for you)
     * @param content ready-made HTML for the page body (escape any user text in it with Html.esc)
     */
    public static String page(Session session, String title, String content) {
        return TemplateRenderer.render("base.html", Map.of(
                "title", Html.esc(title),
                "nav", nav(session),
                "content", content));
    }

    /**
     * The hidden anti-forgery field. Put it inside EVERY form that uses method="post" for a logged-in user:
     * {@code "<form method=\"post\" action=\"...\">" + Layout.csrfField(session) + ...}.
     * (For file-upload forms, put the token in the action instead: action="/owner/products/image?_csrf=" + token.)
     */
    public static String csrfField(Session session) {
        return session == null ? "" : "<input type=\"hidden\" name=\"_csrf\" value=\"" + Html.esc(session.csrfToken()) + "\">";
    }

    private static String nav(Session s) {
        StringBuilder nav = new StringBuilder();
        if (s == null) {
            link(nav, "/catalogue", "Catalogue");
            link(nav, "/signup", "Sign up");
            link(nav, "/login", "Log in");
            return nav.toString();
        }
        switch (s.role()) {
            case Constants.ROLE_OWNER -> {
                link(nav, "/owner", "Dashboard");
                link(nav, "/owner/orders", "Orders");
                link(nav, "/owner/customers", "Customers");
                link(nav, "/owner/products", "Products");
                link(nav, "/owner/suppliers", "Suppliers");
                link(nav, "/owner/deliveries", "Deliveries");
                link(nav, "/owner/bearers", "Bearers");
            }
            case Constants.ROLE_BEARER -> link(nav, "/delivery", "My deliveries");
            default -> {
                link(nav, "/catalogue", "Catalogue");
                link(nav, "/cart", "Cart");
                link(nav, "/orders", "My orders");
                link(nav, "/account", "My account");
            }
        }
        nav.append("<form class=\"inline\" method=\"post\" action=\"/logout\">")
                .append(csrfField(s))
                .append("<button type=\"submit\" class=\"link-button\">Log out (")
                .append(Html.esc(s.displayName()))
                .append(")</button></form>");
        return nav.toString();
    }

    private static void link(StringBuilder nav, String href, String label) {
        nav.append("<a href=\"").append(href).append("\">").append(Html.esc(label)).append("</a>");
    }
}
