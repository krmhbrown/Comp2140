
package peakfinds.routes;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import peakfinds.AppContext;
import peakfinds.Constants;
import peakfinds.Constants.Action;
import peakfinds.auth.Session;
import peakfinds.auth.ValidationException;
import peakfinds.auth.Validator;
import peakfinds.database.Rows;
import peakfinds.models.Order;
import peakfinds.models.Product;
import peakfinds.services.CartService;
import peakfinds.services.CartService.CartItem;
import peakfinds.ui.Layout;
import peakfinds.web.BadRequestException;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;

public final class OrderRoutes {
    private final AppContext ctx;

    private OrderRoutes(AppContext ctx) {
        this.ctx = ctx;
    }

    public static void register(AppContext ctx) {
        OrderRoutes r = new OrderRoutes(ctx);
        var router = ctx.router();

        router.get("/orders", Action.VIEW_OWN_ORDERS, r::myOrders);
        router.get("/orders/new", Action.PLACE_ORDER, r::newOrderForm);
        router.post("/orders/new", Action.PLACE_ORDER, r::placeOrder);
        router.get("/orders/{id}", Action.VIEW_OWN_ORDERS, r::myOrder);
        router.get("/orders/{id}/cancel", Action.CANCEL_OWN_ORDER,
                req -> r.confirmCancel(req, false));
        router.post("/orders/{id}/cancel", Action.CANCEL_OWN_ORDER,
                req -> r.cancel(req, false));

        router.get("/owner/orders", Action.MANAGE_ORDERS, r::ownerList);
        router.get("/owner/orders/{id}", Action.MANAGE_ORDERS, r::ownerOrder);
        router.post("/owner/orders/{id}/confirm-payment",
                Action.CONFIRM_PAYMENT, r::confirmPayment);
        router.post("/owner/orders/{id}/packaging",
                Action.MANAGE_ORDERS, r::packaging);
        router.get("/owner/orders/{id}/cancel", Action.MANAGE_ORDERS,
                req -> r.confirmCancel(req, true));
        router.post("/owner/orders/{id}/cancel", Action.MANAGE_ORDERS,
                req -> r.cancel(req, true));
    }

    private Response myOrders(Request req) {
        Session s = req.session();
        StringBuilder html = new StringBuilder(
                "<h1>My orders</h1>"
                + "<p><a class=\"button\" href=\"/cart\">Place a new order</a></p>");

        List<Order> orders = Order.findForCustomer(ctx.db(), customerId(s));

        if (orders.isEmpty()) {
            html.append("<p>You have not placed any orders yet.</p>");
        } else {
            html.append(orderTable(orders, "/orders/", false));
        }

        return Response.html(Layout.page(s, "My orders", html.toString()));
    }

    private Response newOrderForm(Request req) {
        Session s = req.session();
        long customerId = customerId(s);

        if (!"1".equals(req.query("from_cart"))) {
            return Response.redirect("/cart");
        }

        CartService cart = new CartService(ctx.db());

        // Reject checkout when the cart is empty, stock is invalid,
        // or a valid payment method has not been saved.
        cart.validateForCheckout(customerId);

        String payment = Validator.oneOf(
                cart.getPaymentMethod(customerId),
                "payment method",
                Constants.PAYMENT_METHODS
        );

        Map<String, Object> customer = customerRow(customerId);

        StringBuilder f = new StringBuilder(
                "<h1>Place an order</h1>"
                + "<p>Review your shopping bag and enter your delivery details.</p>"
        );

        f.append("<form method=\"post\" action=\"/orders/new\">")
                .append(Layout.csrfField(s))
                .append("<input type=\"hidden\" name=\"from_cart\" value=\"1\">");

        f.append("<table><tr>")
                .append("<th>ID</th><th>Bag</th><th>Price</th>")
                .append("<th>In stock</th><th>Quantity</th>")
                .append("</tr>");

        boolean any = false;

        for (CartItem item : cart.getItems(customerId)) {
            any = true;

            f.append("<tr><td>")
                    .append(String.format("P%03d", item.productId()))
                    .append("</td><td>")
                    .append(Html.esc(item.name()))
                    .append("</td><td>")
                    .append(money(item.price().doubleValue()))
                    .append("</td><td>")
                    .append(item.availableStock())
                    .append("</td><td>")
                    .append(item.quantity())
                    .append("</td></tr>");
        }

        f.append("</table>");

        if (!any) {
            return Response.redirect("/cart");
        }

        f.append("<p><strong>Items total:</strong> ")
                .append(money(cart.getTotal(customerId).doubleValue()))
                .append("</p>");

        f.append("<p>Delivery charges are not included in this item total.</p>");

        f.append("<label>Delivery address<br>")
                .append("<input class=\"full-width\" name=\"address\" required value=\"")
                .append(Html.esc(nz(Rows.str(customer, "delivery_address"))))
                .append("\"></label><br>");

        f.append("<label>Contact number<br>")
                .append("<input name=\"phone\" required value=\"")
                .append(Html.esc(nz(Rows.str(customer, "phone_number"))))
                .append("\"></label>");

        // Always display the payment method saved in the cart.
        // Do not default to Cash on Delivery.
        f.append("<p><strong>Payment method:</strong> ")
                .append(Html.esc(payment))
                .append("</p>");

        f.append("<p><a href=\"/cart\">Change payment method in shopping bag</a></p>");

        f.append("<button class=\"button\" type=\"submit\">Place order</button>")
                .append(" <a href=\"/cart\">Cancel</a></form>");

        return Response.html(Layout.page(s, "Place an order", f.toString()));
    }

    private Response placeOrder(Request req) {
        Session s = req.session();
        long customerId = customerId(s);

        // Prevent customers from bypassing the cart checkout process.
        if (!"1".equals(req.form("from_cart"))) {
            throw new ValidationException(
                    "Please select a payment method in your shopping bag before checkout."
            );
        }

        CartService cart = new CartService(ctx.db());

        // Server-side validation is required even if the checkout page
        // previously displayed correctly.
        cart.validateForCheckout(customerId);

        String payment = Validator.oneOf(
                cart.getPaymentMethod(customerId),
                "payment method",
                Constants.PAYMENT_METHODS
        );

        String address = Validator.text(
                req.form("address"), "Delivery address", 300
        );

        String phone = Validator.phone(req.form("phone"));

        Map<String, Object> customer = customerRow(customerId);

        Order order = new Order(
                customerId,
                Rows.str(customer, "name"),
                address,
                phone,
                payment
        );

        for (CartItem item : cart.getItems(customerId)) {
            Product product = Product.findActiveById(
                    ctx.db(), item.productId()
            );

            if (product == null) {
                throw new ValidationException(
                        "A product in your cart is no longer available."
                );
            }

            order.addItem(product, item.quantity());
        }

        // Only create the order after payment and stock validation.
        long id = order.placeOrder(ctx.db(), s.userId());

        // Clear the cart only after the order has been saved.
        cart.clearCart(customerId);

        // Reset the saved payment preference for the next order.
        ctx.db().update(
                "UPDATE carts SET payment_method = NULL WHERE customer_id = ?",
                customerId
        );

        return Response.redirect("/orders/" + id + "?placed=1");
    }

    private Response myOrder(Request req) {
        Session s = req.session();
        Order o = ownOrder(req);

        if (o == null) {
            return peakfinds.web.Router.errorPage(
                    s, 404, "Order not found", "That order does not exist."
            );
        }

        StringBuilder html = new StringBuilder(
                "<p><a href=\"/orders\">&larr; Back to my orders</a></p>"
        );

        if ("1".equals(req.query("placed"))) {
            html.append("<p class=\"notice\">Thank you! Your order ")
                    .append(o.displayId())
                    .append(" has been placed.");

            if (Constants.ORDER_AWAITING_PAYMENT.equals(o.getOrderStatus())) {
                html.append(
                        " Please make your bank transfer. "
                        + "Your order will be confirmed once payment is received."
                );
            }

            html.append("</p>");
        }

        if ("1".equals(req.query("cancelled"))) {
            html.append("<p class=\"notice\">Order ")
                    .append(o.displayId())
                    .append(" has been cancelled.</p>");
        }

        html.append(details(o));

        if (o.canCancel(ctx.db())) {
            html.append("<p><a class=\"danger\" href=\"/orders/")
                    .append(o.getOrderId())
                    .append("/cancel\">Cancel this order</a></p>");
        }

        return Response.html(
                Layout.page(s, "Order " + o.displayId(), html.toString())
        );
    }

    private Response confirmCancel(Request req, boolean owner) {
        Order o = owner
                ? Order.findById(ctx.db(), req.pathLong("id"))
                : ownOrder(req);

        if (o == null) {
            throw new BadRequestException("That order does not exist.");
        }

        String base = (owner ? "/owner/orders/" : "/orders/")
                + o.getOrderId();

        if (!o.canCancel(ctx.db())) {
            String why = Constants.ORDER_CANCELLED.equals(o.getOrderStatus())
                    ? "This order is already cancelled."
                    : "This order can no longer be cancelled because it has entered delivery.";

            return Response.html(Layout.page(
                    req.session(),
                    "Cancel order",
                    "<h1>Cancel order</h1><p class=\"error\">"
                    + why
                    + "</p><p><a href=\""
                    + base
                    + "\">Back to the order</a></p>"
            ));
        }

        String html = "<h1>Cancel order?</h1>"
                + "<p>Cancel order <strong>"
                + o.displayId()
                + "</strong> ("
                + money(o.getTotalAmount())
                + ")?</p>"
                + "<form method=\"post\" action=\""
                + base + "/cancel\">"
                + Layout.csrfField(req.session())
                + "<button class=\"button\" type=\"submit\">"
                + "Yes, cancel the order</button> "
                + "<a href=\"" + base + "\">No, keep it</a></form>";

        return Response.html(
                Layout.page(req.session(), "Cancel order", html)
        );
    }

    private Response cancel(Request req, boolean owner) {
        Order o = owner
                ? Order.findById(ctx.db(), req.pathLong("id"))
                : ownOrder(req);

        if (o == null) {
            throw new BadRequestException("That order does not exist.");
        }

        o.cancelOrder(ctx.db(), req.session().userId());

        return Response.redirect(
                (owner ? "/owner/orders/" : "/orders/")
                + o.getOrderId() + "?cancelled=1"
        );
    }

    private Response ownerList(Request req) {
        Session s = req.session();
        String status = Validator.clean(req.query("status"));

        String filter = Constants.ORDER_STATUSES.contains(status)
                ? status : null;

        StringBuilder html = new StringBuilder(
                "<h1>Orders</h1><p>Show: <a href=\"/owner/orders\">All</a>"
        );

        for (String st : Constants.ORDER_STATUSES) {
            html.append(" | <a href=\"/owner/orders?status=")
                    .append(st.replace(" ", "+"))
                    .append("\">")
                    .append(Html.esc(st))
                    .append("</a>");
        }

        html.append("</p>");

        List<Order> orders = Order.findAll(ctx.db(), filter);

        html.append(
                orders.isEmpty()
                        ? "<p>No orders yet.</p>"
                        : orderTable(orders, "/owner/orders/", true)
        );

        return Response.html(
                Layout.page(s, "Orders", html.toString())
        );
    }

    private Response ownerOrder(Request req) {
        Session s = req.session();
        Order o = Order.findById(ctx.db(), req.pathLong("id"));

        if (o == null) {
            return peakfinds.web.Router.errorPage(
                    s, 404, "Order not found", "That order does not exist."
            );
        }

        StringBuilder html = new StringBuilder(
                "<p><a href=\"/owner/orders\">&larr; Back to orders</a></p>"
        );

        if ("1".equals(req.query("cancelled"))) {
            html.append("<p class=\"notice\">Order ")
                    .append(o.displayId())
                    .append(" has been cancelled.</p>");
        }

        html.append(details(o));
        html.append("<p>");

        if (Constants.ORDER_AWAITING_PAYMENT.equals(o.getOrderStatus())) {
            html.append(postButton(
                    s,
                    "/owner/orders/" + o.getOrderId() + "/confirm-payment",
                    "Confirm bank transfer received"
            ));
        }

        if (Constants.ORDER_CONFIRMED.equals(o.getOrderStatus())
                && !o.isInDelivery(ctx.db())) {
            html.append(postButton(
                    s,
                    "/owner/orders/" + o.getOrderId() + "/packaging",
                    "Mark as Order Packaging"
            ));
        }

        if (o.canCancel(ctx.db())) {
            html.append(" <a class=\"danger\" href=\"/owner/orders/")
                    .append(o.getOrderId())
                    .append("/cancel\">Cancel this order</a>");
        }

        html.append("</p>");

        return Response.html(
                Layout.page(s, "Order " + o.displayId(), html.toString())
        );
    }

    private Response confirmPayment(Request req) {
        Order o = Order.findById(ctx.db(), req.pathLong("id"));

        if (o == null) {
            throw new BadRequestException("That order does not exist.");
        }

        o.confirmPayment(ctx.db(), req.session().userId());

        return Response.redirect(
                "/owner/orders/" + o.getOrderId()
        );
    }

    private Response packaging(Request req) {
        Order o = Order.findById(ctx.db(), req.pathLong("id"));

        if (o == null) {
            throw new BadRequestException("That order does not exist.");
        }

        o.updateStatus(
                ctx.db(),
                Constants.ORDER_PACKAGING,
                req.session().userId()
        );

        return Response.redirect(
                "/owner/orders/" + o.getOrderId()
        );
    }

    private String details(Order o) {
        StringBuilder html = new StringBuilder(
                "<h1>Order " + o.displayId() + "</h1>"
        );

        html.append("<p><strong>Status:</strong> ")
                .append(Html.esc(o.trackOrder(ctx.db())))
                .append("<br><strong>Date:</strong> ")
                .append(Html.esc(nz(o.getOrderDate())))
                .append("<br><strong>Customer:</strong> ")
                .append(Html.esc(o.getCustomerName()))
                .append("<br><strong>Delivery address:</strong> ")
                .append(Html.esc(o.getDeliveryAddress()))
                .append("<br><strong>Contact number:</strong> ")
                .append(Html.esc(o.getContactNumber()))
                .append("<br><strong>Payment:</strong> ")
                .append(Html.esc(o.getPaymentMethod()))
                .append(" (")
                .append(Html.esc(o.getPaymentStatus()))
                .append(")</p>");

        html.append("<h2>Invoice</h2><table>")
                .append("<tr><th>ID</th><th>Bag</th>")
                .append("<th>Price</th><th>Qty</th><th>Line total</th></tr>");

        for (Order.OrderItem it : o.getItems()) {
            html.append("<tr><td>")
                    .append(String.format("P%03d", it.getProductId()))
                    .append("</td><td>")
                    .append(Html.esc(it.getProductName()))
                    .append("</td><td>")
                    .append(money(it.getUnitPrice()))
                    .append("</td><td>")
                    .append(it.getQuantity())
                    .append("</td><td>")
                    .append(money(it.lineTotal()))
                    .append("</td></tr>");
        }

        html.append("<tr><td colspan=\"4\"><strong>Total</strong></td>")
                .append("<td><strong>")
                .append(money(o.getTotalAmount()))
                .append("</strong></td></tr></table>");

        html.append("<h2>Status history</h2><ul>");

        for (Order.StatusChange h : o.getStatusHistory()) {
            html.append("<li>")
                    .append(Html.esc(h.status()))
                    .append(" &ndash; ")
                    .append(Html.esc(nz(h.changedAt())))
                    .append(h.note() == null
                            ? ""
                            : " (" + Html.esc(h.note()) + ")")
                    .append("</li>");
        }

        html.append("</ul>");

        return html.toString();
    }

    private String orderTable(
            List<Order> orders,
            String link,
            boolean showCustomer
    ) {
        StringBuilder t = new StringBuilder(
                "<table><tr><th>Order</th><th>Date</th>"
        );

        if (showCustomer) {
            t.append("<th>Customer</th>");
        }

        t.append("<th>Total</th><th>Payment</th><th>Status</th></tr>");

        for (Order o : orders) {
            t.append("<tr><td><a href=\"")
                    .append(link)
                    .append(o.getOrderId())
                    .append("\">")
                    .append(o.displayId())
                    .append("</a></td><td>")
                    .append(Html.esc(nz(o.getOrderDate())))
                    .append("</td>");

            if (showCustomer) {
                t.append("<td>")
                        .append(Html.esc(o.getCustomerName()))
                        .append("</td>");
            }

            t.append("<td>")
                    .append(money(o.getTotalAmount()))
                    .append("</td><td>")
                    .append(Html.esc(o.getPaymentMethod()))
                    .append(" (")
                    .append(Html.esc(o.getPaymentStatus()))
                    .append(")</td><td>")
                    .append(Html.esc(o.trackOrder(ctx.db())))
                    .append("</td></tr>");
        }

        return t.append("</table>").toString();
    }

    private Order ownOrder(Request req) {
        Order o = Order.findById(
                ctx.db(), req.pathLong("id")
        );

        if (o == null
                || !ctx.privileges().canAccessCustomerRecord(
                        req.session(), o.getCustomerId())) {
            return null;
        }

        return o;
    }

    private long customerId(Session s) {
        if (s.customerId() == null) {
            throw new BadRequestException(
                    "Only customer accounts can place orders."
            );
        }

        return s.customerId();
    }

    private Map<String, Object> customerRow(long customerId) {
        Optional<Map<String, Object>> row = ctx.db().queryOne(
                "SELECT * FROM customers WHERE customer_id = ?",
                customerId
        );

        if (row.isEmpty()) {
            throw new ValidationException(
                    "Your customer account was not found."
            );
        }

        return row.get();
    }

    private static String postButton(
            Session s,
            String action,
            String label
    ) {
        return "<form class=\"inline-form\" method=\"post\" action=\""
                + action + "\">"
                + Layout.csrfField(s)
                + "<button class=\"button\" type=\"submit\">"
                + Html.esc(label)
                + "</button></form> ";
    }

    private static String money(double amount) {
        return String.format("$%,.2f", amount);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
