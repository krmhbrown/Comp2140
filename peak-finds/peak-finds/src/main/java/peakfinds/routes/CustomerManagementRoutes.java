
package peakfinds.routes;

import java.util.List;
import java.util.Map;

import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.ui.Layout;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;

public final class CustomerManagementRoutes {

    private CustomerManagementRoutes() {
    }

    public static void register(AppContext ctx) {

        // Owner: view all customers
        ctx.router().get(
                "/owner/customers",
                Action.MANAGE_CUSTOMERS,
                req -> showCustomers(ctx, req)
        );

        // Owner: view customer interaction history
        ctx.router().get(
                "/owner/customers/history",
                Action.MANAGE_CUSTOMERS,
                req -> showHistory(ctx, req)
        );

        // Owner: deactivate customer account
        ctx.router().post(
                "/owner/customers/deactivate",
                Action.MANAGE_CUSTOMERS,
                req -> deactivateCustomer(ctx, req)
        );
    }

    // ---------------- SECURITY ----------------

    private static boolean isOwner(Request req) {
        return req.session() != null
                && "owner".equals(req.session().role());
    }

    private static Response forbidden(Request req) {

        return Response.html(
                403,
                Layout.page(
                        req.session(),
                        "Access Denied",
                        """
                        <section class="narrow">
                            <h1>Access Denied</h1>

                            <p>
                                Only the store owner can
                                manage customer accounts.
                            </p>

                            <a href="/">Back to Home</a>
                        </section>
                        """
                )
        );
    }

    // ---------------- FEEDBACK POPUPS ----------------

    // Reuses the CSS from the My Account success popup.
    private static String feedbackPopup(
            String title,
            String message,
            String returnUrl) {

        return """
                <div class="pf-popup-overlay">

                    <div class="pf-popup"
                         role="alertdialog"
                         aria-modal="true"
                         aria-labelledby="feedbackTitle"
                         aria-describedby="feedbackMessage">

                        <div class="pf-popup-check"
                             aria-hidden="true">
                            &#10003;
                        </div>

                        <h2 id="feedbackTitle">
                            %s
                        </h2>

                        <p id="feedbackMessage">
                            %s
                        </p>

                        <a href="%s"
                           class="pf-popup-button">
                            OK
                        </a>

                    </div>

                </div>
                """.formatted(
                Html.esc(title),
                Html.esc(message),
                Html.esc(returnUrl)
        );
    }

    private static String errorPopup(
            String title,
            String message,
            String returnUrl) {

        return """
                <div class="pf-popup-overlay">

                    <div class="pf-popup"
                         role="alertdialog"
                         aria-modal="true"
                         aria-labelledby="errorTitle"
                         aria-describedby="errorMessage">

                        <div class="pf-popup-check"
                             aria-hidden="true">
                            !
                        </div>

                        <h2 id="errorTitle">
                            %s
                        </h2>

                        <p id="errorMessage">
                            %s
                        </p>

                        <a href="%s"
                           class="pf-popup-button">
                            OK
                        </a>

                    </div>

                </div>
                """.formatted(
                Html.esc(title),
                Html.esc(message),
                Html.esc(returnUrl)
        );
    }

    private static Response badRequest(Request req) {

        String popup = errorPopup(
                "Invalid Customer",
                "Please select a valid customer and try again.",
                "/owner/customers"
        );

        return Response.html(
                400,
                Layout.page(
                        req.session(),
                        "Invalid Customer",
                        """
                        <section class="narrow">
                            <h1>Manage Customers</h1>
                            <p>The requested customer could not be found.</p>
                        </section>
                        """ + popup
                )
        );
    }

    private static Response accountUnavailable(Request req) {

        String popup = errorPopup(
                "Account Unavailable",
                "This customer account is already deactivated or cannot be deactivated.",
                "/owner/customers"
        );

        return Response.html(
                400,
                Layout.page(
                        req.session(),
                        "Account Unavailable",
                        """
                        <section class="narrow">
                            <h1>Manage Customers</h1>
                        </section>
                        """ + popup
                )
        );
    }

    private static long parseCustomerId(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException();
        }

        long id = Long.parseLong(value);

        if (id <= 0) {
            throw new IllegalArgumentException();
        }

        return id;
    }

    // ---------------- CUSTOMER LIST ----------------

    private static Response showCustomers(
            AppContext ctx,
            Request req) {

        if (!isOwner(req)) {
            return forbidden(req);
        }

        List<Map<String, Object>> customers = ctx.db().query(
                """
                SELECT
                    c.customer_id,
                    c.name,
                    u.email,
                    c.phone_number,
                    c.delivery_address,
                    c.is_active
                FROM customers c
                JOIN users u
                    ON c.user_id = u.user_id
                ORDER BY c.customer_id DESC
                """
        );

        StringBuilder body = new StringBuilder();

        body.append("""
                <section>

                    <h1>Manage Customers</h1>

                    <p>
                        View registered customers, review their
                        interaction history and manage
                        customer accounts.
                    </p>
                """);

        // Success popup after account deactivation
        if ("1".equals(req.query("deactivated"))) {

            body.append(
                    feedbackPopup(
                            "Customer Account Deactivated!",
                            "The customer account has been deactivated successfully.",
                            "/owner/customers"
                    )
            );
        }

        if (customers.isEmpty()) {

            body.append("""
                    <div class="card">
                        <p>
                            No customers have registered yet.
                        </p>
                    </div>
                    """);
        }

        body.append("<div class=\"card-grid\">");

        for (Map<String, Object> customer : customers) {

            long customerId =
                    number(customer, "customer_id");

            boolean active =
                    number(customer, "is_active") == 1;

            body.append("""
                    <div class="card">

                        <h2>%s</h2>

                        <p>
                            <strong>Customer ID:</strong> %d
                        </p>

                        <p>
                            <strong>Email:</strong> %s
                        </p>

                        <p>
                            <strong>Phone:</strong> %s
                        </p>

                        <p>
                            <strong>Address:</strong> %s
                        </p>

                        <p>
                            <strong>Status:</strong> %s
                        </p>

                        <p>
                            <a href="/owner/customers/history?id=%d">
                                View Interaction History
                            </a>
                        </p>
                    """.formatted(
                    safe(customer, "name"),
                    customerId,
                    safe(customer, "email"),
                    safe(customer, "phone_number"),
                    safe(customer, "delivery_address"),
                    active ? "Active" : "Deactivated",
                    customerId
            ));

            // Only active customer accounts can be deactivated.
            if (active) {

                body.append("""
                        <form method="post"
                              action="/owner/customers/deactivate">

                            <input type="hidden"
                                   name="_csrf"
                                   value="%s">

                            <input type="hidden"
                                   name="customer_id"
                                   value="%d">

                            <label>
                                <input type="checkbox"
                                       name="confirm"
                                       value="yes"
                                       required>

                                Confirm account deactivation
                            </label>

                            <button type="submit"
                                    class="button">
                                Deactivate Account
                            </button>

                        </form>
                        """.formatted(
                        Html.esc(req.session().csrfToken()),
                        customerId
                ));
            }

            body.append("</div>");
        }

        body.append("""
                </div>

                <p>
                    <a href="/owner">
                        Back to Owner Dashboard
                    </a>
                </p>

                </section>
                """);

        return Response.html(
                Layout.page(
                        req.session(),
                        "Manage Customers",
                        body.toString()
                )
        );
    }

    // ---------------- INTERACTION HISTORY ----------------

    private static Response showHistory(
            AppContext ctx,
            Request req) {

        if (!isOwner(req)) {
            return forbidden(req);
        }

        final long customerId;

        try {
            customerId = parseCustomerId(
                    req.query("id")
            );
        } catch (IllegalArgumentException e) {
            return badRequest(req);
        }

        var found = ctx.db().queryOne(
                """
                SELECT
                    c.customer_id,
                    c.name,
                    u.email
                FROM customers c
                JOIN users u
                    ON c.user_id = u.user_id
                WHERE c.customer_id = ?
                """,
                customerId
        );

        if (found.isEmpty()) {
            return badRequest(req);
        }

        Map<String, Object> customer = found.get();

        List<Map<String, Object>> orders = ctx.db().query(
                """
                SELECT
                    order_id,
                    order_date,
                    total_amount,
                    payment_method,
                    payment_status,
                    order_status
                FROM orders
                WHERE customer_id = ?
                ORDER BY
                    order_date DESC,
                    order_id DESC
                """,
                customerId
        );

        StringBuilder body = new StringBuilder();

        body.append("""
                <section>

                    <h1>Customer Interaction History</h1>

                    <div class="card">

                        <h2>%s</h2>

                        <p>
                            <strong>Customer ID:</strong> %d
                        </p>

                        <p>
                            <strong>Email:</strong> %s
                        </p>

                    </div>

                    <h2>Order History</h2>
                """.formatted(
                safe(customer, "name"),
                customerId,
                safe(customer, "email")
        ));

        if (orders.isEmpty()) {

            body.append("""
                    <div class="card">
                        <p>
                            This customer has no recorded orders.
                        </p>
                    </div>
                    """);
        }

        for (Map<String, Object> order : orders) {

            long orderId = number(order, "order_id");

            body.append("""
                    <div class="card">

                        <h2>Order #%d</h2>

                        <p>
                            <strong>Date:</strong> %s
                        </p>

                        <p>
                            <strong>Total:</strong> J$%s
                        </p>

                        <p>
                            <strong>Payment Method:</strong> %s
                        </p>

                        <p>
                            <strong>Payment Status:</strong> %s
                        </p>

                        <p>
                            <strong>Order Status:</strong> %s
                        </p>

                        <h3>Status Updates</h3>
                    """.formatted(
                    orderId,
                    safe(order, "order_date"),
                    safe(order, "total_amount"),
                    safe(order, "payment_method"),
                    safe(order, "payment_status"),
                    safe(order, "order_status")
            ));

            List<Map<String, Object>> history = ctx.db().query(
                    """
                    SELECT
                        status,
                        changed_at,
                        note
                    FROM order_status_history
                    WHERE order_id = ?
                    ORDER BY
                        changed_at DESC,
                        history_id DESC
                    """,
                    orderId
            );

            if (history.isEmpty()) {

                body.append("""
                        <p>
                            No additional status updates recorded.
                        </p>
                        """);
            }

            for (Map<String, Object> item : history) {

                body.append("""
                        <p>
                            <strong>%s</strong> — %s
                        </p>
                        """.formatted(
                        safe(item, "status"),
                        safe(item, "changed_at")
                ));

                String note = value(item, "note");

                if (!note.isBlank()) {

                    body.append(
                            "<p>Note: "
                            + Html.esc(note)
                            + "</p>"
                    );
                }
            }

            body.append("</div>");
        }

        body.append("""
                <p>
                    <a href="/owner/customers">
                        Back to Manage Customers
                    </a>
                </p>

                </section>
                """);

        return Response.html(
                Layout.page(
                        req.session(),
                        "Customer Interaction History",
                        body.toString()
                )
        );
    }

    // ---------------- DEACTIVATE CUSTOMER ----------------

    private static Response deactivateCustomer(
            AppContext ctx,
            Request req) {

        if (!isOwner(req)) {
            return forbidden(req);
        }

        // Require confirmation before deactivation.
        if (!"yes".equals(req.form("confirm"))) {
            return badRequest(req);
        }

        final long customerId;

        try {
            customerId = parseCustomerId(
                    req.form("customer_id")
            );
        } catch (IllegalArgumentException e) {
            return badRequest(req);
        }

        var customer = ctx.db().queryOne(
                """
                SELECT
                    c.customer_id
                FROM customers c
                JOIN users u
                    ON c.user_id = u.user_id
                WHERE c.customer_id = ?
                  AND c.is_active = 1
                  AND u.is_active = 1
                  AND u.role = 'customer'
                """,
                customerId
        );

        // Do not report success if account cannot be deactivated.
        if (customer.isEmpty()) {
            return accountUnavailable(req);
        }

        // Update both records in a single transaction.
        ctx.db().transaction(() -> {

            // Retain customer information and order history.
            ctx.db().update(
                    """
                    UPDATE customers
                    SET is_active = 0
                    WHERE customer_id = ?
                    """,
                    customerId
            );

            // Disable the customer's login account.
            ctx.db().update(
                    """
                    UPDATE users
                    SET is_active = 0
                    WHERE user_id = (
                        SELECT user_id
                        FROM customers
                        WHERE customer_id = ?
                    )
                    """,
                    customerId
            );

            return null;
        });

        // Show success feedback after deactivation.
        return Response.redirect(
                "/owner/customers?deactivated=1"
        );
    }

    // ---------------- HELPERS ----------------

    private static long number(
            Map<String, Object> row,
            String key) {

        return ((Number) row.get(key)).longValue();
    }

    private static String value(
            Map<String, Object> row,
            String key) {

        Object result = row.get(key);

        return result == null
                ? ""
                : result.toString();
    }

    private static String safe(
            Map<String, Object> row,
            String key) {

        return Html.esc(value(row, key));
    }
}
