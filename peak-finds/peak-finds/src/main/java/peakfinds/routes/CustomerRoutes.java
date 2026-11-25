
package peakfinds.routes;

import java.util.Map;
import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.auth.Session;
import peakfinds.auth.SessionManager;
import peakfinds.ui.Layout;
import peakfinds.ui.StubPages;
import peakfinds.web.Html;
import peakfinds.web.Response;

/** Customer home, account management and account deactivation. */
public final class CustomerRoutes {

    private CustomerRoutes() {}

    public static void register(AppContext ctx) {

        ctx.router().get("/customer", Action.VIEW_CUSTOMER_HOME,
                req -> Response.html(Layout.page(
                        req.session(),
                        "Welcome",
                        peakfinds.web.TemplateRenderer.render(
                                "customer/home.html", Map.of())
                )));

        StubPages.register(ctx.router(), "/catalogue", null, "Catalogue");
        StubPages.register(ctx.router(), "/signup", null, "Sign up");
        StubPages.register(ctx.router(), "/cart", Action.MANAGE_CART, "Cart");
        StubPages.register(ctx.router(), "/orders", Action.VIEW_OWN_ORDERS, "My orders");

        // Customer account page.
        ctx.router().get("/account", Action.MANAGE_OWN_ACCOUNT, req -> {

            Session s = req.session();

            String html = "<h1>My account</h1>"
                    + "<p><strong>Name:</strong> "
                    + Html.esc(s.displayName()) + "</p>"
                    + "<p><strong>Email:</strong> "
                    + Html.esc(s.email()) + "</p>"
                    + "<h2>Deactivate my account</h2>"
                    + "<p>Deactivation disables your sign-in. "
                    + "Your order history is retained for business records. "
                    + "Contact the store owner to reactivate your account.</p>"
                    + "<form method=\"post\" action=\"/account/deactivate\">"
                    + Layout.csrfField(s)
                    + "<label><input type=\"checkbox\" "
                    + "name=\"confirm\" value=\"yes\" required> "
                    + "I understand that I will be signed out.</label><br>"
                    + "<label>Type DEACTIVATE to confirm:<br>"
                    + "<input name=\"confirmation\" required "
                    + "autocomplete=\"off\"></label><br>"
                    + "<button class=\"button danger\" type=\"submit\">"
                    + "Deactivate my account</button>"
                    + "</form>";

            return Response.html(Layout.page(s, "My account", html));
        });

        // Deactivation confirmation page.
        ctx.router().get("/account/deactivate",
                Action.MANAGE_OWN_ACCOUNT, req -> {

            Session s = req.session();

            if (s == null || s.customerId() == null) {
                return Response.redirect("/login");
            }

            String html = """
                    <section class="narrow">
                        <h1>Deactivate My Account</h1>
                        <p>Are you sure you want to deactivate your account?</p>
                        <p>You will be signed out and unable to log in.
                           Your existing order history will be retained.</p>

                        <form method="post" action="/account/deactivate">
                            %s
                            <input type="hidden" name="confirm" value="yes">

                            <label for="confirmation">
                                Type DEACTIVATE to confirm:
                            </label>

                            <input id="confirmation"
                                   name="confirmation"
                                   required
                                   autocomplete="off">

                            <button type="submit" class="button">
                                Confirm Deactivation
                            </button>
                        </form>

                        <p>
                            <a href="/account">
                                Cancel and return to My Account
                            </a>
                        </p>
                    </section>
                    """.formatted(Layout.csrfField(s));

            return Response.html(
                    Layout.page(s, "Deactivate Account", html));
        });

        // Process customer account deactivation.
        ctx.router().post("/account/deactivate",
                Action.MANAGE_OWN_ACCOUNT, req -> {

            Session s = req.session();

            if (s == null
                    || s.customerId() == null
                    || !"customer".equals(s.role())) {

                return peakfinds.web.Router.errorPage(
                        s,
                        403,
                        "Access denied",
                        "Only a customer can deactivate their own account."
                );
            }

            // Require confirmation.
            if (!"yes".equals(req.form("confirm"))
                    || !"DEACTIVATE".equals(req.form("confirmation"))) {

                return peakfinds.web.Router.errorPage(
                        s,
                        400,
                        "Confirmation required",
                        "Tick the confirmation box and type DEACTIVATE exactly."
                );
            }

            // Check for active orders before deactivating.
            // The check and account updates run in one transaction.
            boolean deactivated = ctx.db().transaction(() -> {

                boolean hasActiveOrders = ctx.db().queryOne("""
                        SELECT o.order_id
                        FROM orders o
                        LEFT JOIN deliveries d
                            ON d.order_id = o.order_id
                        WHERE o.customer_id = ?
                          AND o.order_status <> 'Cancelled'
                          AND (
                              d.delivery_id IS NULL
                              OR d.delivery_status NOT IN (
                                  'Delivered',
                                  'Returned'
                              )
                          )
                        LIMIT 1
                        """, s.customerId()).isPresent();

                // Block deactivation if any order is still active.
                if (hasActiveOrders) {
                    return false;
                }

                // No active orders: deactivate customer account.
                int updated = ctx.db().update(
                        """
                        UPDATE customers
                        SET is_active = 0
                        WHERE customer_id = ?
                          AND user_id = ?
                          AND is_active = 1
                        """,
                        s.customerId(),
                        s.userId()
                );

                if (updated != 1) {
                    return false;
                }

                // Disable the associated login account.
                ctx.db().update(
                        """
                        UPDATE users
                        SET is_active = 0
                        WHERE user_id = ?
                          AND role = 'customer'
                        """,
                        s.userId()
                );

                return true;
            });

            // Show a message instead of deactivating.
            if (!deactivated) {

                String html = """
                        <section class="narrow">
                            <h1>Account Cannot Be Deactivated</h1>

                            <p>
                                Your account cannot be deactivated
                                because you have an active order
                                or your account is already inactive.
                            </p>

                            <p>
                                If you have an active order,
                                please wait until it has been
                                delivered, returned or cancelled.
                            </p>

                            <p>
                                <a href="/orders">View My Orders</a>
                            </p>

                            <p>
                                <a href="/account">Back to My Account</a>
                            </p>
                        </section>
                        """;

                return Response.html(
                        Layout.page(s, "Deactivation Blocked", html));
            }

            // Deactivation successful: terminate all sessions.
            ctx.sessions().destroyForUser(s.userId());

            return Response.redirect("/login")
                    .header(
                            "Set-Cookie",
                            SessionManager.clearCookieHeader(
                                    ctx.config().secureCookies())
                    );
        });
    }
}
