package peakfinds.models;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import peakfinds.Constants;
import peakfinds.database.Database;

public class DeliveryModel {

    private final Database db;

    public DeliveryModel(Database db) {
        this.db = db;
    }

    public List<Map<String, Object>> getAllDeliveries() {
        String sql = """
            SELECT
                d.delivery_id,
                d.order_id,
                d.bearer_id,
                d.delivery_method,
                d.delivery_address,
                d.delivery_fee,
                d.delivery_status,
                d.failure_reason,
                d.bill_number,
                d.expected_date,
                d.payment_collected,
                o.customer_name,
                o.contact_number,
                o.total_amount,
                o.payment_method,
                o.payment_status,
                o.order_status,
                u.email AS customer_email,
                b.name AS bearer_name,
                b.phone_number AS bearer_phone
            FROM deliveries d
            JOIN orders o ON d.order_id = o.order_id
            LEFT JOIN customers c ON o.customer_id = c.customer_id
            LEFT JOIN users u ON c.user_id = u.user_id
            LEFT JOIN bearers b ON d.bearer_id = b.bearer_id
            ORDER BY d.delivery_id DESC
            """;

        return db.query(sql);
    }

    public List<Map<String, Object>> getDeliveriesByBearer(long bearerId) {
        String sql = """
            SELECT
                d.delivery_id,
                d.order_id,
                d.bearer_id,
                d.delivery_method,
                d.delivery_address,
                d.delivery_fee,
                d.delivery_status,
                d.failure_reason,
                d.bill_number,
                d.expected_date,
                d.payment_collected,
                o.customer_name,
                o.contact_number,
                o.total_amount,
                o.payment_method,
                o.payment_status,
                o.order_status,
                u.email AS customer_email,
                b.name AS bearer_name,
                b.phone_number AS bearer_phone
            FROM deliveries d
            JOIN orders o ON d.order_id = o.order_id
            LEFT JOIN customers c ON o.customer_id = c.customer_id
            LEFT JOIN users u ON c.user_id = u.user_id
            LEFT JOIN bearers b ON d.bearer_id = b.bearer_id
            WHERE d.bearer_id = ?
            ORDER BY d.delivery_id DESC
            """;

        return db.query(sql, bearerId);
    }

    public Optional<Map<String, Object>> getDeliveryById(long deliveryId) {
        String sql = """
            SELECT
                d.*,
                o.customer_name,
                o.contact_number,
                o.total_amount,
                o.payment_method,
                o.payment_status,
                o.order_status,
                u.email AS customer_email,
                b.name AS bearer_name,
                b.phone_number AS bearer_phone
            FROM deliveries d
            JOIN orders o ON d.order_id = o.order_id
            LEFT JOIN customers c ON o.customer_id = c.customer_id
            LEFT JOIN users u ON c.user_id = u.user_id
            LEFT JOIN bearers b ON d.bearer_id = b.bearer_id
            WHERE d.delivery_id = ?
            """;

        return db.queryOne(sql, deliveryId);
    }

    public List<Map<String, Object>> getOrderItems(long orderId) {
        String sql = """
            SELECT
                product_id,
                product_name,
                quantity,
                unit_price
            FROM order_items
            WHERE order_id = ?
            ORDER BY product_id
            """;

        return db.query(sql, orderId);
    }

    public List<Map<String, Object>> getActiveBearers() {
        String sql = """
            SELECT
                bearer_id,
                user_id,
                name,
                phone_number,
                is_active
            FROM bearers
            WHERE is_active = 1 AND user_id IN (SELECT user_id FROM users WHERE is_active = 1 AND role = 'bearer')
            ORDER BY name
            """;

        return db.query(sql);
    }

    public Optional<Map<String, Object>> getBearerById(long bearerId) {
        String sql = """
            SELECT b.*, u.is_active AS account_active
            FROM bearers b JOIN users u ON u.user_id = b.user_id
            WHERE b.bearer_id = ? AND u.role = 'bearer' 
            """;

        return db.queryOne(sql, bearerId);
    }

    public List<Map<String, Object>> getAssignableOrders() {
        return db.query("""
            SELECT o.* FROM orders o
            WHERE o.order_status IN (?, ?) AND NOT EXISTS ( 
                SELECT 1 FROM deliveries d WHERE d.order_id = o.order_id)
            ORDER BY o.order_id
            """, Constants.ORDER_CONFIRMED, Constants.ORDER_PACKAGING);
    }

    public Optional<Map<String, Object>> getOrder(long orderId) {
        return db.queryOne("SELECT * FROM orders WHERE order_id = ?", orderId);
    }

    public long createDelivery(long orderId, Long bearerId, String method,
                               String address, String waybill, String expectedDate) {
        return db.insert("""
            INSERT INTO deliveries (order_id, bearer_id, delivery_method,
                delivery_address, delivery_status, bill_number, expected_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, orderId, bearerId, method, address,
                Constants.DELIVERY_ASSIGNED, waybill, expectedDate);
    }

    public int updateDeliveryStatus(
            long deliveryId,
            String newStatus,
            String failureReason) {
        String sql = """
            UPDATE deliveries
            SET delivery_status = ?,
                failure_reason = ?
            WHERE delivery_id = ?
            """;

        return db.update(sql, newStatus, failureReason, deliveryId);
    }

    public void addStatusHistory(
            long deliveryId,
            String status,
            String reason,
            long changedBy) {
        String sql = """
            INSERT INTO delivery_status_history
                (delivery_id, status, reason, changed_by)
            VALUES (?, ?, ?, ?)
            """;

        db.insert(sql, deliveryId, status, reason, changedBy);
    }

    public List<Map<String, Object>> getDeliveryHistory(long deliveryId) {
        String sql = """
            SELECT
                history_id,
                delivery_id,
                status,
                reason,
                changed_at,
                changed_by
            FROM delivery_status_history
            WHERE delivery_id = ?
            ORDER BY history_id DESC
            """;

        return db.query(sql, deliveryId);
    }

    public int addPaymentCollected(long deliveryId, double amount) {
        String sql = """
            UPDATE deliveries
            SET payment_collected = COALESCE(payment_collected, 0) + ?
            WHERE delivery_id = ?
            """;

        return db.update(sql, amount, deliveryId);
    }

    public int confirmOrderPaid(long orderId) {
        String sql = """
            UPDATE orders
            SET payment_status = ?
            WHERE order_id = ?
            """;

        return db.update(sql, Constants.PAYMENT_STATUS_PAID, orderId);
    }

    public void restoreReturnedOrderStock(long orderId) {
        String sql = """
            UPDATE products
            SET stock_quantity = stock_quantity + (
                SELECT COALESCE(SUM(quantity), 0)
                FROM order_items
                WHERE order_items.order_id = ?
                  AND order_items.product_id = products.product_id
            )
            WHERE product_id IN (
                SELECT product_id
                FROM order_items
                WHERE order_id = ?
            )
            """;

        db.update(sql, orderId, orderId);
    }

    public void logEmail(
            String recipient,
            String subject,
            Long orderId,
            String status,
            String error) {
        String sql = """
            INSERT INTO email_log (recipient, subject, order_id, status, error)
            VALUES (?, ?, ?, ?, ?)
            """;

        db.insert(sql, recipient, subject, orderId, status, error);
    }

    public void addOwnerNotification(String kind, String message, Long productId) {
        String sql = """
            INSERT INTO notifications (kind, message, product_id, is_read)
            VALUES (?, ?, ?, 0)
            """;

        db.insert(sql, kind, message, productId);
    }

    public List<Map<String, Object>> getUnreadEmailFailureNotifications() {
        String sql = """
            SELECT notification_id, kind, message, created_at
            FROM notifications
            WHERE kind = 'email_failed' AND is_read = 0
            ORDER BY notification_id DESC
            """;

        return db.query(sql);
    }
}