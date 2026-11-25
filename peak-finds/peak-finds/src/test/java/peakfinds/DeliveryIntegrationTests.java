package peakfinds;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import peakfinds.auth.ValidationException;
import peakfinds.database.Database;
import peakfinds.database.DatabaseInitializer;
import peakfinds.database.Rows;
import peakfinds.routes.DeliveryAdminRoutes;
import peakfinds.services.DeliveryEmailWorker;
import peakfinds.services.DeliveryService;
import static peakfinds.Constants.*;

/** Plain Java integration tests using a disposable SQLite database. */
public final class DeliveryIntegrationTests {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
    private static void reject(Runnable work, String message) {
        try { work.run(); } catch (ValidationException e) { check(true, message); return; }
        throw new AssertionError("Expected validation error: " + message);
    }
    // Emulates Order Management: a confirmed order reserves stock in the same transaction.
    private static long order(Database db, String status, String method, String payment) {
        return db.transaction(() -> {
            long id = db.insert("""
                INSERT INTO orders (customer_id, customer_name, delivery_address, contact_number,
                    total_amount, payment_method, payment_status, order_status)
                VALUES (1, 'Aaliyah Test', '12 Hope Road', '876-555-0101', 7500, ?, ?, ?)
                """, method, payment, status);
            db.insert("""
                INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity)
                VALUES (?, 1, 'Black Juicy Bag', 7500, 1)
                """, id);
            if (ORDER_CONFIRMED.equals(status)) {
                db.update("UPDATE products SET stock_quantity=stock_quantity-1 WHERE product_id=1");
            }
            return id;
        });
    }
    private static long stock(Database db) {
        return Rows.longOf(db.queryOne("SELECT stock_quantity FROM products WHERE product_id=1").orElseThrow(), "stock_quantity");
    }
    public static void main(String[] args) throws Exception {
        var file = Files.createTempFile("peakfinds-delivery-", ".db");
        try {
            Database db = new Database("jdbc:sqlite:" + file);
            if (!db.connect()) throw new IllegalStateException("SQLite JDBC driver unavailable");
            DatabaseInitializer.initialise(db, file.toString());
            db.update("UPDATE products SET stock_quantity=20 WHERE product_id=1");
            DeliveryService s = new DeliveryService(db);
            long awaiting = order(db, ORDER_AWAITING_PAYMENT, PAYMENT_BANK_TRANSFER, PAYMENT_STATUS_PENDING);
            long o = order(db, ORDER_CONFIRMED, PAYMENT_CASH_ON_DELIVERY, PAYMENT_STATUS_PENDING);
            long start = System.nanoTime();
            long d = s.assignDeliveryToBearer(o, 1, "2027-01-01", 1);
            Map<String,String> summary = s.buildDeliverySummary(d);
            check(summary.keySet().equals(java.util.Set.of("order_number", "customer_name", "phone", "address", "items", "payment_method", "amount_to_collect"))
                    && summary.values().stream().allMatch(v -> v != null && !v.isBlank())
                    && summary.get("amount_to_collect").equals("7500.00"), "TS-1 seven summary fields");
            check((System.nanoTime()-start)/1_000_000 < 5000, "AC-1 sample assignment + summary below 5s");
            reject(() -> s.assignDeliveryToBearer(awaiting, 1, null, 1), "TS-2 awaiting payment refused");
            reject(() -> s.assignDeliveryToBearer(o, 2, null, 1), "TS-2 duplicate refused");
            reject(() -> s.updateDeliveryStatus(d, 2, DELIVERY_OUT_FOR_DELIVERY, null, 5), "Other bearer denied");
            List<String> messages = new ArrayList<>();
            DeliveryEmailWorker worker = new DeliveryEmailWorker(db, (recipient, subject, body) -> messages.add(body));
            start = System.nanoTime();
            s.updateDeliveryStatus(d, 1, DELIVERY_OUT_FOR_DELIVERY, null, 4);
            check((System.nanoTime()-start)/1_000_000 < 3000, "AC-2 sample status save below 3s");
            worker.processPending();
            check(messages.size()==1 && messages.get(0).contains("Order #"+o)
                    && messages.get(0).contains(DELIVERY_OUT_FOR_DELIVERY) && messages.get(0).contains("Black Juicy Bag x1")
                    && messages.get(0).contains("Bearer One") && messages.get(0).contains("876-555-0201"), "TS-5 bearer email body");
            s.updateDeliveryStatus(d, 1, DELIVERY_DELIVERED, null, 4);
            worker.processPending();
            check(messages.size()==2 && messages.get(1).contains(DELIVERY_DELIVERED), "Delivered email queued and sent to fake transport");
            reject(() -> s.confirmCodPayment(d, 1), "TS-3 cannot confirm before collection");
            s.recordPayment(d, 1, 5000, 4);
            reject(() -> s.confirmCodPayment(d, 1), "TS-3 partial payment cannot be confirmed");
            reject(() -> s.recordPayment(d, 1, 2501, 4), "Overpayment refused");
            s.recordPayment(d, 1, 2500, 4);
            check(PAYMENT_STATUS_PENDING.equals(Rows.str(s.getDelivery(d).orElseThrow(), "payment_status")), "TS-3 collection does not mark Paid");
            reject(() -> s.confirmCodPayment(d, 4), "Bearer cannot confirm Paid");
            s.confirmCodPayment(d, 1);
            check(PAYMENT_STATUS_PAID.equals(Rows.str(s.getDelivery(d).orElseThrow(), "payment_status")), "TS-3 owner confirmation marks Paid");
            check(s.getDeliveryHistory(d).size()==3 && s.getDeliveryHistory(d).stream().allMatch(r -> r.get("changed_at") != null && r.get("changed_by") != null), "AC-2 dated account history");
            long returnedOrder = order(db, ORDER_CONFIRMED, PAYMENT_CASH_ON_DELIVERY, PAYMENT_STATUS_PENDING);
            long returned = s.assignDeliveryToBearer(returnedOrder, 1, null, 1);
            long before = stock(db);
            reject(() -> s.updateDeliveryStatus(returned, 1, DELIVERY_RETURNED, " ", 4), "TS-4 blank return reason refused");
            reject(() -> s.updateDeliveryStatus(returned, 1, DELIVERY_FAILED, null, 4), "Blank failed reason refused");
            check(stock(db)==before, "TS-4 rejected return changes no stock");
            s.updateDeliveryStatus(returned, 1, DELIVERY_RETURNED, "Customer refused", 4);
            check(stock(db)==before+1, "TS-4 returned item restored");
            reject(() -> s.updateDeliveryStatus(returned, 1, DELIVERY_RETURNED, "Repeated", 4), "Duplicate return refused");
            check(stock(db)==before+1, "Stock restored exactly once");
            long failedOrder = order(db, ORDER_CONFIRMED, PAYMENT_CASH_ON_DELIVERY, PAYMENT_STATUS_PENDING);
            long f = s.assignDeliveryToBearer(failedOrder, 1, null, 1);
            s.updateDeliveryStatus(f, 1, DELIVERY_OUT_FOR_DELIVERY, null, 4);
            new DeliveryEmailWorker(db, (recipient, subject, body) -> { throw new java.io.IOException("SMTP offline"); }).processPending();
            check(DELIVERY_OUT_FOR_DELIVERY.equals(Rows.str(s.getDelivery(f).orElseThrow(), "delivery_status")), "TS-6 status survives SMTP failure");
            check(db.queryOne("SELECT * FROM email_log WHERE order_id=? AND status='Failed'", failedOrder).isPresent(), "TS-6 failure logged");
            check(DeliveryAdminRoutes.failureAlerts(s).contains("Email delivery failures"), "TS-6 owner dashboard alert content");
            long partnerOrder = order(db, ORDER_CONFIRMED, PAYMENT_BANK_TRANSFER, PAYMENT_STATUS_PAID);
            long p = s.assignDeliveryToKnutsford(partnerOrder, "KE-123", "2027-01-01", 1);
            s.updatePartnerStatus(p, DELIVERY_OUT_FOR_DELIVERY, null, 1);
            worker.processPending();
            check(messages.get(messages.size()-1).contains("KE-123") && messages.get(messages.size()-1).contains("2027-01-01"), "Partner contact email");
            System.out.println("All delivery integration assertions passed.");
        } finally { Files.deleteIfExists(file); }
    }
}
