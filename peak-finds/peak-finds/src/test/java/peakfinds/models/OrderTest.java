package peakfinds.models;

import java.nio.file.Files;
import java.nio.file.Path;
import peakfinds.Constants;
import peakfinds.auth.ValidationException;
import peakfinds.database.Database;
import peakfinds.database.Rows;

/**
 * Plain Java checks for Feature 1 - Order Management (no JUnit), author: Arriyanna.
 * Uses the seed data: customer 1 is Aaliyah Test; P003 has 2 in stock ($9,500), P005 has 1 ($6,500),
 * P007 has 1 ($13,500), P008 has 1 ($14,500), P001 is out of stock.
 * Run: mvn test-compile exec:java -Dexec.mainClass=peakfinds.models.OrderTest -Dexec.classpathScope=test
 */
public class OrderTest {
    private static int passed = 0, failed = 0;
    private static final long CUSTOMER_USER = 2, OWNER_USER = 1;

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + label);
        if (ok) passed++; else failed++;
    }

    /** True if placing/doing the thing throws a ValidationException (the order is refused). */
    private static boolean refused(Runnable action) {
        try { action.run(); return false; } catch (ValidationException e) { return true; }
    }

    private static int stock(Database db, long productId) {
        return Rows.intOf(db.queryOne("SELECT stock_quantity FROM products WHERE product_id = ?", productId).get(), "stock_quantity");
    }

    private static int orderCount(Database db) {
        return db.query("SELECT order_id FROM orders").size();
    }

    private static Order newOrder(String payment) {
        return new Order(1, "Aaliyah Test", "12 Hope Road, Kingston", "876-555-0101", payment);
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("peakfinds-order-test", ".db");
        Database db = new Database("jdbc:sqlite:" + file);
        check("database connects", db.connect());
        db.executeScript(new String(OrderTest.class.getResourceAsStream("/database/schema.sql").readAllBytes()));
        db.executeScript(new String(OrderTest.class.getResourceAsStream("/database/seed.sql").readAllBytes()));

        // ---------- placing a Cash on Delivery order ----------
        Order cod = newOrder(Constants.PAYMENT_CASH_ON_DELIVERY);
        cod.addItem(Product.findActiveById(db, 3), 1);
        cod.addItem(Product.findActiveById(db, 5), 1);
        long codId = cod.placeOrder(db, CUSTOMER_USER);
        Order saved = Order.findById(db, codId);
        check("order ID shows as ORD0001", saved.displayId().equals("ORD0001"));
        check("cash on delivery starts as Order Confirmed", saved.getOrderStatus().equals(Constants.ORDER_CONFIRMED));
        check("payment status starts as Pending", saved.getPaymentStatus().equals(Constants.PAYMENT_STATUS_PENDING));
        check("total is $9,500 + $6,500 = $16,000", saved.getTotalAmount() == 16000);
        check("order has 2 items saved", saved.getItems().size() == 2);
        check("stock reserved: P003 2 -> 1", stock(db, 3) == 1);
        check("stock reserved: P005 1 -> 0", stock(db, 5) == 0);
        check("low-stock alert raised for P005", !db.query("SELECT * FROM notifications WHERE product_id = 5").isEmpty());
        check("status history has the first row", saved.getStatusHistory().size() == 1);
        check("invoice shows the order ID and total",
                saved.generateInvoice().contains("ORD0001") && saved.generateInvoice().contains("$16,000.00"));

        // ---------- placing a Bank Transfer order ----------
        Order bank = newOrder(Constants.PAYMENT_BANK_TRANSFER);
        bank.addItem(Product.findActiveById(db, 7), 1);
        long bankId = bank.placeOrder(db, CUSTOMER_USER);
        check("bank transfer starts as Awaiting Payment",
                Order.findById(db, bankId).getOrderStatus().equals(Constants.ORDER_AWAITING_PAYMENT));
        check("no stock taken before payment: P007 stays 1", stock(db, 7) == 1);

        Order toConfirm = Order.findById(db, bankId);
        toConfirm.confirmPayment(db, OWNER_USER);
        Order confirmed = Order.findById(db, bankId);
        check("confirming payment sets Paid", confirmed.getPaymentStatus().equals(Constants.PAYMENT_STATUS_PAID));
        check("confirming payment sets Order Confirmed", confirmed.getOrderStatus().equals(Constants.ORDER_CONFIRMED));
        check("stock reserved after payment: P007 1 -> 0", stock(db, 7) == 0);
        check("payment can't be confirmed twice", refused(() -> confirmed.confirmPayment(db, OWNER_USER)));
        check("cash on delivery payment can't be confirmed as a transfer", refused(() -> saved.confirmPayment(db, OWNER_USER)));

        // ---------- invalid orders are refused ----------
        int before = orderCount(db);
        check("order with no items is refused", refused(() -> newOrder(Constants.PAYMENT_CASH_ON_DELIVERY).placeOrder(db, CUSTOMER_USER)));
        Order tooMany = newOrder(Constants.PAYMENT_CASH_ON_DELIVERY);
        tooMany.addItem(Product.findActiveById(db, 3), 5);
        check("ordering more than is in stock is refused", refused(() -> tooMany.placeOrder(db, CUSTOMER_USER)));
        Order outOfStock = newOrder(Constants.PAYMENT_CASH_ON_DELIVERY);
        outOfStock.addItem(Product.findActiveById(db, 1), 1);
        check("ordering an out-of-stock bag is refused", refused(() -> outOfStock.placeOrder(db, CUSTOMER_USER)));
        Order noAddress = new Order(1, "Aaliyah Test", " ", "876-555-0101", Constants.PAYMENT_CASH_ON_DELIVERY);
        noAddress.addItem(Product.findActiveById(db, 3), 1);
        check("order with no delivery address is refused", refused(() -> noAddress.placeOrder(db, CUSTOMER_USER)));
        check("zero quantity is refused", refused(() -> newOrder(Constants.PAYMENT_CASH_ON_DELIVERY).addItem(Product.findActiveById(db, 3), 0)));
        check("refused orders are not saved", orderCount(db) == before);
        check("refused orders don't change stock: P003 still 1", stock(db, 3) == 1);

        // ---------- status updates ----------
        Order pack = Order.findById(db, codId);
        pack.updateStatus(db, Constants.ORDER_PACKAGING, OWNER_USER);
        check("Order Confirmed -> Order Packaging", Order.findById(db, codId).getOrderStatus().equals(Constants.ORDER_PACKAGING));
        check("history now has 2 rows", Order.findById(db, codId).getStatusHistory().size() == 2);

        // ---------- cancelling ----------
        Order toCancel = Order.findById(db, codId);
        check("an order in packaging can be cancelled", toCancel.canCancel(db));
        String msg = toCancel.cancelOrder(db, CUSTOMER_USER);
        check("cancel message names the order", msg.contains("ORD0001"));
        check("order is now Cancelled", Order.findById(db, codId).getOrderStatus().equals(Constants.ORDER_CANCELLED));
        check("stock restored: P003 1 -> 2", stock(db, 3) == 2);
        check("stock restored: P005 0 -> 1", stock(db, 5) == 1);
        Order cancelled = Order.findById(db, codId);
        check("a cancelled order can't be cancelled again", refused(() -> cancelled.cancelOrder(db, CUSTOMER_USER)));
        check("a cancelled order's status can't change", refused(() -> cancelled.updateStatus(db, Constants.ORDER_PACKAGING, OWNER_USER)));

        Order unpaid = newOrder(Constants.PAYMENT_BANK_TRANSFER);
        unpaid.addItem(Product.findActiveById(db, 8), 1);
        long unpaidId = unpaid.placeOrder(db, CUSTOMER_USER);
        Order.findById(db, unpaidId).cancelOrder(db, CUSTOMER_USER);
        check("cancelling an unpaid bank transfer order keeps P008 stock at 1", stock(db, 8) == 1);

        // ---------- once the order is in delivery ----------
        db.insert("INSERT INTO deliveries (order_id, delivery_method, delivery_address) VALUES (?, 'Knutsford Express', ?)",
                bankId, "12 Hope Road, Kingston");
        Order inDelivery = Order.findById(db, bankId);
        check("order in delivery can't be cancelled", !inDelivery.canCancel(db) && refused(() -> inDelivery.cancelOrder(db, CUSTOMER_USER)));
        check("tracking shows the delivery status", inDelivery.trackOrder(db).equals("In delivery: Assigned"));
        check("stock stays taken for the delivered order: P007 is 0", stock(db, 7) == 0);

        // ---------- saved prices and lookups ----------
        Product p3 = Product.findActiveById(db, 3);
        p3.setPrice(1);
        p3.editProduct(db);
        check("past orders keep the price they were placed at", Order.findById(db, codId).getItems().get(0).getUnitPrice() == 9500);
        check("customer 1 sees their 3 orders", Order.findForCustomer(db, 1).size() == 3);
        check("customer 2 sees no orders", Order.findForCustomer(db, 2).isEmpty());
        check("owner filter finds 2 cancelled orders", Order.findAll(db, Constants.ORDER_CANCELLED).size() == 2);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        Files.deleteIfExists(file);
        System.exit(failed == 0 ? 0 : 1);
    }
}
