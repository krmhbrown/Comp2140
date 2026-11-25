package peakfinds.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import peakfinds.Constants;
import peakfinds.auth.ValidationException;
import peakfinds.database.Database;
import peakfinds.database.Rows;

/**
 * SDS class "Order" (Feature 1 - Order Management), author: Arriyanna.
 * One customer order. Data lives in the orders, order_items and order_status_history tables.
 * The database creates the numeric id; it is shown to people as ORD0001, ORD0002, ...
 *
 * Statuses (SRS wording, from Constants):
 *   Bank Transfer:    Awaiting Payment -> (owner confirms payment) -> Order Confirmed -> Order Packaging
 *   Cash on Delivery: Order Confirmed -> Order Packaging
 *   Any of these can become Cancelled while the order is still processing (SR-1.6).
 * Stock is reserved (taken out) once the order is Order Confirmed, and put back if it is cancelled.
 */
public class Order {

    /** One line on the order. The name and price are saved so past orders still read correctly. */
    public static class OrderItem {
        private final long productId;
        private final String productName;
        private final double unitPrice;
        private final int quantity;

        public OrderItem(long productId, String productName, double unitPrice, int quantity) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
        }

        public long getProductId() { return productId; }
        public String getProductName() { return productName; }
        public double getUnitPrice() { return unitPrice; }
        public int getQuantity() { return quantity; }
        public double lineTotal() { return unitPrice * quantity; }
    }

    /** One row of the status history (status, when, note). */
    public record StatusChange(String status, String changedAt, String note) {}

    /** Statuses where the order is still processing and can be cancelled (SR-1.6). */
    public static final List<String> PROCESSING_STATUSES = List.of(
            Constants.ORDER_AWAITING_PAYMENT, Constants.ORDER_CONFIRMED, Constants.ORDER_PACKAGING);

    private long orderId;
    private long customerId;
    private String customerName;
    private String orderDate;
    private String deliveryAddress;
    private String contactNumber;
    private double totalAmount;
    private String paymentMethod;
    private String paymentStatus = Constants.PAYMENT_STATUS_PENDING;
    private String orderStatus;
    private final List<OrderItem> items = new ArrayList<>();
    private final List<StatusChange> statusHistory = new ArrayList<>();

    public Order() {}

    public Order(long customerId, String customerName, String deliveryAddress, String contactNumber, String paymentMethod) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.deliveryAddress = deliveryAddress;
        this.contactNumber = contactNumber;
        this.paymentMethod = paymentMethod;
    }

    // ---------- getters ----------
    public long getOrderId() { return orderId; }
    public long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public String getOrderDate() { return orderDate; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getContactNumber() { return contactNumber; }
    public double getTotalAmount() { return totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getOrderStatus() { return orderStatus; }
    public List<OrderItem> getItems() { return items; }
    public List<StatusChange> getStatusHistory() { return statusHistory; }

    /** The id as people see it, e.g. ORD0001. */
    public String displayId() { return String.format("ORD%04d", orderId); }

    // ---------- plain logic (no database, easy to unit test) ----------

    /** Adds a product to the order at its current price. Adding the same product again adds to its quantity. */
    public void addItem(Product product, int quantity) {
        if (quantity <= 0) {
            throw new ValidationException("Quantity must be at least 1.");
        }
        for (int i = 0; i < items.size(); i++) {
            OrderItem it = items.get(i);
            if (it.getProductId() == product.getProductId()) {
                items.set(i, new OrderItem(it.getProductId(), it.getProductName(), it.getUnitPrice(), it.getQuantity() + quantity));
                return;
            }
        }
        items.add(new OrderItem(product.getProductId(), product.getName(), product.getPrice(), quantity));
    }

    /** calculateTotal(): adds up price x quantity for every item. */
    public double calculateTotal() {
        double total = 0;
        for (OrderItem it : items) {
            total += it.lineTotal();
        }
        totalAmount = total;
        return total;
    }

    /** True while the order is still processing (Awaiting Payment, Order Confirmed or Order Packaging). */
    public boolean isProcessingStage() {
        return PROCESSING_STATUSES.contains(orderStatus);
    }

    /** Stock has been taken out for this order (it is confirmed or being packed). */
    public boolean hasReservedStock() {
        return Constants.ORDER_CONFIRMED.equals(orderStatus) || Constants.ORDER_PACKAGING.equals(orderStatus);
    }

    /**
     * generateInvoice(): the invoice as plain text (shown on the order page and used for the email
     * once the email sender is set up).
     */
    public String generateInvoice() {
        StringBuilder sb = new StringBuilder();
        sb.append("PEAK FINDS - INVOICE\n");
        sb.append("Order ID: ").append(displayId()).append("\n");
        sb.append("Date: ").append(orderDate).append("\n");
        sb.append("Customer: ").append(customerName).append("\n");
        sb.append("Delivery address: ").append(deliveryAddress).append("\n");
        sb.append("Contact number: ").append(contactNumber).append("\n");
        for (OrderItem it : items) {
            sb.append(String.format("P%03d %s x%d @ $%,.2f = $%,.2f%n", it.getProductId(), it.getProductName(),
                    it.getQuantity(), it.getUnitPrice(), it.lineTotal()));
        }
        sb.append(String.format("Total: $%,.2f%n", totalAmount));
        sb.append("Payment method: ").append(paymentMethod).append("\n");
        sb.append("Payment status: ").append(paymentStatus).append("\n");
        sb.append("Order status: ").append(orderStatus).append("\n");
        return sb.toString();
    }

    // ---------- database operations ----------

    /**
     * validateOrder(): checks the order before it is saved. Throws ValidationException with a message
     * the customer can read: no items, missing address or phone, bad payment method, or not enough stock.
     */
    public void validateOrder(Database db) {
        if (items.isEmpty()) {
            throw new ValidationException("Add at least one bag to your order.");
        }
        if (deliveryAddress == null || deliveryAddress.isBlank()) {
            throw new ValidationException("Delivery address is required.");
        }
        if (contactNumber == null || contactNumber.isBlank()) {
            throw new ValidationException("Contact number is required.");
        }
        if (!Constants.PAYMENT_METHODS.contains(paymentMethod)) {
            throw new ValidationException("Choose Cash on Delivery or Bank Transfer.");
        }
        checkStock(db);
    }

    /** Every item must still be in the catalogue with enough stock (Product.checkAvailability). */
    private void checkStock(Database db) {
        for (OrderItem it : items) {
            Product p = Product.findActiveById(db, it.getProductId());
            if (p == null) {
                throw new ValidationException(it.getProductName() + " is no longer available.");
            }
            if (!p.checkAvailability(it.getQuantity())) {
                throw new ValidationException(p.isOutOfStock()
                        ? p.getName() + " is out of stock."
                        : "Only " + p.getStockQuantity() + " of " + p.getName() + " left in stock.");
            }
        }
    }

    /**
     * placeOrder(): validates and saves the order, its items and the first history row.
     * Bank Transfer starts as Awaiting Payment (no stock taken yet).
     * Cash on Delivery starts as Order Confirmed and the stock is reserved straight away.
     * Returns the new order id.
     */
    public long placeOrder(Database db, Long changedBy) {
        return db.transaction(() -> {
            validateOrder(db);
            calculateTotal();
            orderStatus = Constants.PAYMENT_BANK_TRANSFER.equals(paymentMethod)
                    ? Constants.ORDER_AWAITING_PAYMENT : Constants.ORDER_CONFIRMED;
            paymentStatus = Constants.PAYMENT_STATUS_PENDING;
            orderId = db.insert("INSERT INTO orders (customer_id, customer_name, delivery_address, contact_number,"
                            + " total_amount, payment_method, payment_status, order_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    customerId, customerName, deliveryAddress, contactNumber, totalAmount, paymentMethod, paymentStatus, orderStatus);
            for (OrderItem it : items) {
                db.insert("INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity) VALUES (?, ?, ?, ?, ?)",
                        orderId, it.getProductId(), it.getProductName(), it.getUnitPrice(), it.getQuantity());
            }
            if (Constants.ORDER_CONFIRMED.equals(orderStatus)) {
                reserveStock(db);
            }
            addHistory(db, orderStatus, changedBy, "Order placed (" + paymentMethod + ")");
            reloadDate(db);
            return orderId;
        });
    }

    /**
     * confirmPayment(): the owner confirms a bank transfer was received.
     * Awaiting Payment -> Paid + Order Confirmed, and the stock is reserved.
     */
    public void confirmPayment(Database db, Long changedBy) {
        if (!Constants.PAYMENT_BANK_TRANSFER.equals(paymentMethod)) {
            throw new ValidationException("Only bank transfer orders need their payment confirmed.");
        }
        if (!Constants.ORDER_AWAITING_PAYMENT.equals(orderStatus)) {
            throw new ValidationException("This order is not awaiting payment.");
        }
        db.transaction(() -> {
            checkStock(db);   // stock may have sold out while waiting for the transfer
            paymentStatus = Constants.PAYMENT_STATUS_PAID;
            orderStatus = Constants.ORDER_CONFIRMED;
            db.update("UPDATE orders SET payment_status = ?, order_status = ? WHERE order_id = ?",
                    paymentStatus, orderStatus, orderId);
            reserveStock(db);
            addHistory(db, orderStatus, changedBy, "Bank transfer payment confirmed");
            return null;
        });
    }

    /**
     * updateStatus(): moves the order to the next status. A cancelled order can't change (use cancelOrder to cancel).
     * Allowed: Order Confirmed -> Order Packaging.
     */
    public void updateStatus(Database db, String newStatus, Long changedBy) {
        if (Constants.ORDER_CANCELLED.equals(orderStatus)) {
            throw new ValidationException("This order was cancelled, so its status can't change.");
        }
        if (Constants.ORDER_CANCELLED.equals(newStatus)) {
            throw new ValidationException("Use Cancel order to cancel an order.");
        }
        if (!(Constants.ORDER_CONFIRMED.equals(orderStatus) && Constants.ORDER_PACKAGING.equals(newStatus))) {
            throw new ValidationException("An order can't go from " + orderStatus + " to " + newStatus + ".");
        }
        db.transaction(() -> {
            orderStatus = newStatus;
            db.update("UPDATE orders SET order_status = ? WHERE order_id = ?", orderStatus, orderId);
            addHistory(db, orderStatus, changedBy, null);
            return null;
        });
    }

    /** True once Delivery Management has a delivery for this order (it has left order processing). */
    public boolean isInDelivery(Database db) {
        return db.queryOne("SELECT 1 AS x FROM deliveries WHERE order_id = ?", orderId).isPresent();
    }

    /** The order can be cancelled: still processing and not handed over to delivery. */
    public boolean canCancel(Database db) {
        return isProcessingStage() && !isInDelivery(db);
    }

    /**
     * cancelOrder(): cancels the order if it is still processing and not in delivery (SR-1.6).
     * Reserved stock goes back into the catalogue. Returns the confirmation message.
     */
    public String cancelOrder(Database db, Long changedBy) {
        if (Constants.ORDER_CANCELLED.equals(orderStatus)) {
            throw new ValidationException("This order is already cancelled.");
        }
        if (!canCancel(db)) {
            throw new ValidationException("This order can no longer be cancelled because it has entered delivery.");
        }
        return db.transaction(() -> {
            boolean restore = hasReservedStock();
            boolean wasPaid = Constants.PAYMENT_STATUS_PAID.equals(paymentStatus);
            orderStatus = Constants.ORDER_CANCELLED;
            db.update("UPDATE orders SET order_status = ? WHERE order_id = ?", orderStatus, orderId);
            if (restore) {
                restoreStock(db);
            }
            addHistory(db, orderStatus, changedBy, wasPaid ? "Cancelled after payment - refund the customer" : null);
            return "Order " + displayId() + " has been cancelled."
                    + (restore ? " The items have been returned to stock." : "");
        });
    }

    /** trackOrder(): the status the customer sees, including the delivery status once it is out. */
    public String trackOrder(Database db) {
        return db.queryOne("SELECT delivery_status FROM deliveries WHERE order_id = ?", orderId)
                .map(row -> "In delivery: " + Rows.str(row, "delivery_status"))
                .orElse(orderStatus);
    }

    /** Takes the ordered quantities out of stock (Product.updateStock also raises low-stock alerts). */
    private void reserveStock(Database db) {
        for (OrderItem it : items) {
            Product p = Product.findActiveById(db, it.getProductId());
            if (p == null || !p.checkAvailability(it.getQuantity())) {
                throw new ValidationException("Not enough stock for " + it.getProductName() + ".");
            }
            p.updateStock(db, p.getStockQuantity() - it.getQuantity());
        }
    }

    /** Puts the ordered quantities back (works even if the product was removed from the catalogue). */
    private void restoreStock(Database db) {
        for (OrderItem it : items) {
            db.update("UPDATE products SET stock_quantity = stock_quantity + ? WHERE product_id = ?",
                    it.getQuantity(), it.getProductId());
        }
    }

    private void addHistory(Database db, String status, Long changedBy, String note) {
        db.insert("INSERT INTO order_status_history (order_id, status, changed_by, note) VALUES (?, ?, ?, ?)",
                orderId, status, changedBy, note);
        statusHistory.add(new StatusChange(status, null, note));
    }

    private void reloadDate(Database db) {
        db.queryOne("SELECT order_date FROM orders WHERE order_id = ?", orderId)
                .ifPresent(row -> orderDate = Rows.str(row, "order_date"));
    }

    // ---------- loading ----------

    /** One order with its items and history, or null if it doesn't exist. */
    public static Order findById(Database db, long id) {
        return db.queryOne("SELECT * FROM orders WHERE order_id = ?", id).map(row -> fromRow(db, row)).orElse(null);
    }

    /** A customer's orders, newest first. */
    public static List<Order> findForCustomer(Database db, long customerId) {
        List<Order> list = new ArrayList<>();
        for (Map<String, Object> row : db.query("SELECT * FROM orders WHERE customer_id = ? ORDER BY order_id DESC", customerId)) {
            list.add(fromRow(db, row));
        }
        return list;
    }

    /** Every order (owner view), newest first. Pass a status to filter, or null for all. */
    public static List<Order> findAll(Database db, String status) {
        List<Order> list = new ArrayList<>();
        List<Map<String, Object>> rows = status == null
                ? db.query("SELECT * FROM orders ORDER BY order_id DESC")
                : db.query("SELECT * FROM orders WHERE order_status = ? ORDER BY order_id DESC", status);
        for (Map<String, Object> row : rows) {
            list.add(fromRow(db, row));
        }
        return list;
    }

    private static Order fromRow(Database db, Map<String, Object> row) {
        Order o = new Order();
        o.orderId = Rows.longOf(row, "order_id");
        o.customerId = Rows.longOf(row, "customer_id");
        o.customerName = Rows.str(row, "customer_name");
        o.orderDate = Rows.str(row, "order_date");
        o.deliveryAddress = Rows.str(row, "delivery_address");
        o.contactNumber = Rows.str(row, "contact_number");
        o.totalAmount = Rows.doubleOf(row, "total_amount");
        o.paymentMethod = Rows.str(row, "payment_method");
        o.paymentStatus = Rows.str(row, "payment_status");
        o.orderStatus = Rows.str(row, "order_status");
        for (Map<String, Object> it : db.query("SELECT * FROM order_items WHERE order_id = ? ORDER BY product_id", o.orderId)) {
            o.items.add(new OrderItem(Rows.longOf(it, "product_id"), Rows.str(it, "product_name"),
                    Rows.doubleOf(it, "unit_price"), Rows.intOf(it, "quantity")));
        }
        for (Map<String, Object> h : db.query(
                "SELECT status, changed_at, note FROM order_status_history WHERE order_id = ? ORDER BY history_id", o.orderId)) {
            o.statusHistory.add(new StatusChange(Rows.str(h, "status"), Rows.str(h, "changed_at"), Rows.str(h, "note")));
        }
        return o;
    }
}
