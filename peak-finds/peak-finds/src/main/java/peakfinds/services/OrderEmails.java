package peakfinds.services;

import peakfinds.AppContext;
import peakfinds.models.Order;

/**
 * Feature 1 - Order Management emails (Arriyanna): the invoice when an order is placed and the
 * notice when it is cancelled. The email is queued in Davaun's delivery_email_outbox, and his
 * DeliveryEmailWorker sends it through Gmail within a few seconds and records it in email_log.
 */
public final class OrderEmails {
    private OrderEmails() {}

    /** Emails the customer their invoice once the order is placed. */
    public static void sendInvoice(AppContext ctx, Order order) {
        queue(ctx, order, "Peak Finds invoice for order " + order.displayId(),
                "Thank you for shopping with Peak Finds!\n\n" + order.generateInvoice());
    }

    /** Emails the customer that their order was cancelled. */
    public static void sendCancellation(AppContext ctx, Order order) {
        queue(ctx, order, "Peak Finds order " + order.displayId() + " cancelled",
                "Your order " + order.displayId() + " has been cancelled.\n\n" + order.generateInvoice());
    }

    private static void queue(AppContext ctx, Order order, String subject, String body) {
        ctx.db().queryOne(
                        "SELECT u.email FROM customers c JOIN users u ON u.user_id = c.user_id WHERE c.customer_id = ?",
                        order.getCustomerId())
                .ifPresent(row -> ctx.db().insert(
                        "INSERT INTO delivery_email_outbox (order_id, recipient, subject, body) VALUES (?, ?, ?, ?)",
                        order.getOrderId(), String.valueOf(row.get("email")), subject, body));
    }
}
