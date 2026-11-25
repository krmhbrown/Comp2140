package peakfinds.services;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import peakfinds.Constants;
import peakfinds.auth.ValidationException;
import peakfinds.auth.Validator;
import peakfinds.database.Database;
import peakfinds.database.Rows;
import peakfinds.models.DeliveryModel;

public class DeliveryService {

    private final Database db;
    private final DeliveryModel deliveryModel;

    public DeliveryService(Database db) {
        this.db = db;
        this.deliveryModel = new DeliveryModel(db);
    }

    public List<Map<String, Object>> getAllDeliveries() {
        return deliveryModel.getAllDeliveries();
    }

    public List<Map<String, Object>> getBearerDeliveries(long bearerId) {
        if (bearerId <= 0) {
            throw new ValidationException("Invalid bearer ID.");
        }
        return deliveryModel.getDeliveriesByBearer(bearerId);
    }

    public Optional<Map<String, Object>> getDelivery(long deliveryId) {
        if (deliveryId <= 0) {
            throw new ValidationException("Invalid delivery ID.");
        }
        return deliveryModel.getDeliveryById(deliveryId);
    }

    public List<Map<String, Object>> getDeliveryHistory(long deliveryId) {
        if (deliveryModel.getDeliveryById(deliveryId).isEmpty()) {
            throw new ValidationException("Delivery not found.");
        }
        return deliveryModel.getDeliveryHistory(deliveryId);
    }

    public List<Map<String, Object>> getActiveBearers() {
        return deliveryModel.getActiveBearers();
    }

    public List<Map<String, Object>> getUnreadEmailFailureNotifications() {
        return deliveryModel.getUnreadEmailFailureNotifications();
    }

    public Map<String, String> buildDeliverySummary(long deliveryId) {
        Map<String, Object> delivery = deliveryModel
                .getDeliveryById(deliveryId)
                .orElseThrow(() -> new ValidationException("Delivery not found."));

        Long orderId = Rows.longOrNull(delivery, "order_id");
        String itemsSummary = "None";
        if (orderId != null) {
            List<Map<String, Object>> items = deliveryModel.getOrderItems(orderId);
            if (!items.isEmpty()) {
                itemsSummary = items.stream()
                        .map(item -> Rows.str(item, "product_name") + " x" + Rows.str(item, "quantity"))
                        .collect(Collectors.joining(", "));
            }
        }

        String paymentMethod = Rows.str(delivery, "payment_method");
        String paymentStatus = Rows.str(delivery, "payment_status");
        BigDecimal amountToCollect = BigDecimal.ZERO.setScale(2);
        if (Constants.PAYMENT_CASH_ON_DELIVERY.equals(paymentMethod)
                && !Constants.PAYMENT_STATUS_PAID.equals(paymentStatus)) {
            amountToCollect = money(delivery, "total_amount").add(money(delivery, "delivery_fee"))
                    .subtract(money(delivery, "payment_collected")).max(BigDecimal.ZERO);
        }

        Map<String, String> summary = new LinkedHashMap<>();
        summary.put("order_number", Rows.str(delivery, "order_id"));
        summary.put("customer_name", Rows.str(delivery, "customer_name"));
        summary.put("phone", Rows.str(delivery, "contact_number"));
        summary.put("address", Rows.str(delivery, "delivery_address"));
        summary.put("items", itemsSummary);
        summary.put("payment_method", paymentMethod);
        summary.put("amount_to_collect", amountToCollect.setScale(2).toPlainString());
        return summary;
    }

    public List<Map<String, Object>> getAssignableOrders() {
        return deliveryModel.getAssignableOrders();
    }

    // Input is the ORDER ID, not a delivery ID. The delivery row is created here.
    public long assignDeliveryToBearer(long orderId, long bearerId,
                                       String expectedDate, long changedBy) {
        validateIds(orderId, bearerId, changedBy);
        return assign(orderId, bearerId, Constants.METHOD_BEARER,
                null, validateDate(expectedDate), changedBy);
    }

    public long assignDeliveryToKnutsford(long orderId, String billNumber,
                                          String expectedDate, long changedBy) {
        validateIds(orderId, changedBy);
        String date = validateDate(Validator.required(expectedDate, "Expected date"));
        return assign(orderId, null, Constants.METHOD_KNUTSFORD,
                Validator.text(billNumber, "Knutsford waybill", 80), date, changedBy);
    }

    // Synchronization protects concurrent assignment attempts in this app instance.
    // The database UNIQUE(order_id) constraint is the final duplicate guard.
    private synchronized long assign(long orderId, Long bearerId, String method,
                                     String bill, String date, long changedBy) {
        return db.transaction(() -> {
            requireOwner(changedBy);
            Map<String, Object> order = deliveryModel.getOrder(orderId)
                    .orElseThrow(() -> new ValidationException("Order not found."));
            if (!Constants.ORDER_CONFIRMED.equals(Rows.str(order, "order_status"))) {
                throw new ValidationException("Only Order Confirmed orders can be assigned.");
            }
            if (db.queryOne("SELECT delivery_id FROM deliveries WHERE order_id = ?", orderId).isPresent()) {
                throw new ValidationException("This order is already assigned.");
            }
            if (deliveryModel.getOrderItems(orderId).isEmpty()) {
                throw new ValidationException("The order has no items.");
            }
            if (bearerId != null) {
                requireActiveBearer(bearerId, null);
            }
            // Pending COD needs a bearer to collect payment. Partner COD is not defined
            // by the current requirements; use a paid order or a bearer instead.
            if (bearerId == null
                    && Constants.PAYMENT_CASH_ON_DELIVERY.equals(Rows.str(order, "payment_method"))
                    && !Constants.PAYMENT_STATUS_PAID.equals(Rows.str(order, "payment_status"))) {
                throw new ValidationException("Unpaid COD orders require a bearer.");
            }
            long id = deliveryModel.createDelivery(orderId, bearerId, method,
                    Rows.str(order, "delivery_address"), bill, date);
            deliveryModel.addStatusHistory(id, Constants.DELIVERY_ASSIGNED,
                    "Assigned to " + method, changedBy);
            return id;
        });
    }

    private void requireOwner(long userId) {
        if (db.queryOne("SELECT user_id FROM users WHERE user_id = ? AND role = ? AND is_active = 1",
                userId, Constants.ROLE_OWNER).isEmpty()) {
            throw new ValidationException("An active owner account is required.");
        }
    }

    private void requireActiveBearer(long bearerId, Long userId) {
        Map<String, Object> bearer = deliveryModel.getBearerById(bearerId)
                .orElseThrow(() -> new ValidationException("Bearer not found."));
        if (!Rows.bool(bearer, "is_active") || !Rows.bool(bearer, "account_active")
                || (userId != null && Rows.longOf(bearer, "user_id") != userId)) {
            throw new ValidationException("An active assigned bearer account is required.");
        }
    }

    public synchronized void updateDeliveryStatus(
            long deliveryId,
            long bearerId,
            String newStatus,
            String reason,
            long changedBy) {

        validateIds(deliveryId, bearerId, changedBy);
        updateStatus(deliveryId, bearerId, newStatus, reason, changedBy);
    }

    public synchronized void updatePartnerStatus(long deliveryId, String newStatus,
                                                  String reason, long changedBy) {
        validateIds(deliveryId, changedBy);
        updateStatus(deliveryId, null, newStatus, reason, changedBy);
    }

    private void updateStatus(long deliveryId, Long bearerId, String newStatus,
                              String reason, long changedBy) {
        String status = Validator.oneOf(
                newStatus, "delivery status", Constants.DELIVERY_STATUSES);

        boolean requiresReason = Constants.DELIVERY_FAILED.equals(status)
                || Constants.DELIVERY_RETURNED.equals(status);

        String finalReason = null;
        if (requiresReason) {
            finalReason = Validator.text(Validator.clean(reason), "Reason", 500);
        }

        final String reasonToSave = finalReason;

        db.transaction(() -> {
            Map<String, Object> delivery = deliveryModel
                    .getDeliveryById(deliveryId)
                    .orElseThrow(() -> new ValidationException("Delivery not found."));

            if (bearerId == null) {
                requireOwner(changedBy);
                if (!Constants.METHOD_KNUTSFORD.equals(Rows.str(delivery, "delivery_method"))) {
                    throw new ValidationException("Only partner deliveries can be updated by the owner here.");
                }
            } else {
                requireActiveBearer(bearerId, changedBy);
                if (!bearerId.equals(Rows.longOrNull(delivery, "bearer_id"))) {
                    throw new ValidationException("You are not assigned to this delivery.");
                }
            }

            String currentStatus = Rows.str(delivery, "delivery_status");
            if (!isAllowedTransition(currentStatus, status)) {
                throw new ValidationException(
                        "Invalid status transition from " + currentStatus + " to " + status + ".");
            }

            int updated = deliveryModel.updateDeliveryStatus(
                    deliveryId, status, reasonToSave);
            if (updated != 1) {
                throw new ValidationException("Unable to update delivery status.");
            }

            if (Constants.DELIVERY_RETURNED.equals(status)) {
                Long orderId = Rows.longOrNull(delivery, "order_id");
                if (orderId != null) {
                    deliveryModel.restoreReturnedOrderStock(orderId);
                }
            }

            deliveryModel.addStatusHistory(deliveryId, status, reasonToSave, changedBy);

            if (Constants.DELIVERY_OUT_FOR_DELIVERY.equals(status)
                    || Constants.DELIVERY_DELIVERED.equals(status)) {
                queueCustomerStatusEmail(delivery, status);
            }
            return null;
        });
    }

    public synchronized void recordPayment(
            long deliveryId,
            long bearerId,
            double amount,
            long changedBy) {

        validateIds(deliveryId, bearerId, changedBy);

        if (!Double.isFinite(amount) || amount <= 0) {
            throw new ValidationException("Payment amount must be greater than zero.");
        }

        db.transaction(() -> {
            Map<String, Object> delivery = deliveryModel
                    .getDeliveryById(deliveryId)
                    .orElseThrow(() -> new ValidationException("Delivery not found."));

            requireActiveBearer(bearerId, changedBy);

            Long assignedBearer = Rows.longOrNull(delivery, "bearer_id");
            if (assignedBearer == null || assignedBearer.longValue() != bearerId) {
                throw new ValidationException("You are not assigned to this delivery.");
            }

            String method = Rows.str(delivery, "delivery_method");
            if (!Constants.METHOD_BEARER.equals(method)) {
                throw new ValidationException(
                        "Payment collection is only allowed for Bearer deliveries.");
            }

            String status = Rows.str(delivery, "delivery_status");
            if (!Constants.DELIVERY_DELIVERED.equals(status)) {
                throw new ValidationException(
                        "Payment can only be recorded once the delivery is Delivered.");
            }

            String paymentMethod = Rows.str(delivery, "payment_method");
            if (!Constants.PAYMENT_CASH_ON_DELIVERY.equalsIgnoreCase(paymentMethod)) {
                throw new ValidationException(
                        "Cash collection is only for Cash on Delivery orders.");
            }

            if (Constants.PAYMENT_STATUS_PAID.equals(Rows.str(delivery, "payment_status"))) {
                throw new ValidationException("This order is already paid.");
            }
            BigDecimal payment = BigDecimal.valueOf(amount);
            if (payment.stripTrailingZeros().scale() > 2) {
                throw new ValidationException("Use at most two decimal places.");
            }
            BigDecimal collected = money(delivery, "payment_collected");
            BigDecimal due = money(delivery, "total_amount").add(money(delivery, "delivery_fee"));
            if (collected.add(payment).compareTo(due) > 0) {
                throw new ValidationException("Payment cannot exceed the outstanding amount.");
            }
            if (deliveryModel.addPaymentCollected(deliveryId, amount) != 1) {
                throw new ValidationException("Unable to record payment.");
            }
            db.insert("""
                INSERT INTO delivery_payment_log (delivery_id, amount, recorded_by)
                VALUES (?, ?, ?)
                """, deliveryId, amount, changedBy);

            return null;
        });
    }

    public synchronized void confirmCodPayment(long deliveryId, long changedBy) {
        validateIds(deliveryId, changedBy);

        db.transaction(() -> {
            requireOwner(changedBy);
            Map<String, Object> delivery = deliveryModel
                    .getDeliveryById(deliveryId)
                    .orElseThrow(() -> new ValidationException("Delivery not found."));

            String paymentMethod = Rows.str(delivery, "payment_method");
            if (!Constants.PAYMENT_CASH_ON_DELIVERY.equalsIgnoreCase(paymentMethod)) {
                throw new ValidationException("Only Cash on Delivery orders use COD confirmation.");
            }

            String deliveryStatus = Rows.str(delivery, "delivery_status");
            if (!Constants.DELIVERY_DELIVERED.equals(deliveryStatus)) {
                throw new ValidationException(
                        "Delivery must be Delivered before confirming payment.");
            }

            if (Constants.PAYMENT_STATUS_PAID.equals(Rows.str(delivery, "payment_status"))) {
                throw new ValidationException("This order is already paid.");
            }
            BigDecimal collected = money(delivery, "payment_collected");
            BigDecimal due = money(delivery, "total_amount").add(money(delivery, "delivery_fee"));
            if (collected.compareTo(due) < 0 || db.queryOne("""
                    SELECT payment_id FROM delivery_payment_log WHERE delivery_id = ? LIMIT 1
                    """, deliveryId).isEmpty()) {
                throw new ValidationException("The full payment must be recorded by the bearer first.");
            }

            Long orderId = Rows.longOrNull(delivery, "order_id");
            if (orderId == null) {
                throw new ValidationException("Associated order not found.");
            }

            int updated = deliveryModel.confirmOrderPaid(orderId);
            if (updated != 1) {
                throw new ValidationException("Unable to mark order as Paid.");
            }

            db.insert("""
                INSERT INTO order_status_history (order_id, status, changed_by, note)
                VALUES (?, ?, ?, ?)
                """, orderId, Rows.str(delivery, "order_status"), changedBy, "COD payment confirmed as Paid");
            return null;
        });
    }

    private static BigDecimal money(Map<String, Object> row, String column) {
        return BigDecimal.valueOf(Rows.doubleOf(row, column)).setScale(2, RoundingMode.HALF_UP);
    }

    private void queueCustomerStatusEmail(Map<String, Object> delivery, String status) {
        Long orderId = Rows.longOrNull(delivery, "order_id");
        String recipient = Rows.str(delivery, "customer_email");
        if (recipient == null) {
            recipient = "";
        }

        String itemsSummary = "None";
        if (orderId != null) {
            List<Map<String, Object>> items = deliveryModel.getOrderItems(orderId);
            if (!items.isEmpty()) {
                itemsSummary = items.stream()
                        .map(item -> Rows.str(item, "product_name") + " x" + Rows.str(item, "quantity"))
                        .collect(Collectors.joining(", "));
            }
        }

        String method = Rows.str(delivery, "delivery_method");
        String contactInfo = Constants.METHOD_KNUTSFORD.equals(method)
                ? "Knutsford Express Waybill: " + nonEmptyOrDash(Rows.str(delivery, "bill_number"))
                        + ", Expected Date: " + nonEmptyOrDash(Rows.str(delivery, "expected_date"))
                : "Bearer: " + nonEmptyOrDash(Rows.str(delivery, "bearer_name"))
                        + " (" + nonEmptyOrDash(Rows.str(delivery, "bearer_phone")) + ")";

        String subject = "Order #" + (orderId == null ? "" : orderId) + " - " + status;

        String body = "Order #" + orderId + "\nStatus: " + status
                + "\nItems: " + itemsSummary + "\nDelivery contact: " + contactInfo;
        db.insert("""
            INSERT INTO delivery_email_outbox (order_id, recipient, subject, body)
            VALUES (?, ?, ?, ?)
            """, orderId, recipient, subject, body);
    }

    private String nonEmptyOrDash(String value) {
        return (value == null || value.isBlank()) ? "-" : value;
    }

    private boolean isAllowedTransition(String currentStatus, String nextStatus) {
        if (currentStatus == null || currentStatus.isEmpty()) {
            return false;
        }
        switch (currentStatus) {
            case Constants.DELIVERY_ASSIGNED:
                return Constants.DELIVERY_OUT_FOR_DELIVERY.equals(nextStatus)
                        || Constants.DELIVERY_FAILED.equals(nextStatus)
                        || Constants.DELIVERY_RETURNED.equals(nextStatus);
            case Constants.DELIVERY_OUT_FOR_DELIVERY:
                return Constants.DELIVERY_DELIVERED.equals(nextStatus)
                        || Constants.DELIVERY_FAILED.equals(nextStatus)
                        || Constants.DELIVERY_RETURNED.equals(nextStatus);
            case Constants.DELIVERY_FAILED:
                return Constants.DELIVERY_RETURNED.equals(nextStatus);
            case Constants.DELIVERY_DELIVERED:
            case Constants.DELIVERY_RETURNED:
            default:
                return false;
        }
    }

    private void validateIds(long... ids) {
        for (long id : ids) {
            if (id <= 0) {
                throw new ValidationException("Invalid record ID.");
            }
        }
    }

    private String validateDate(String date) {
        String cleanedDate = Validator.clean(date);
        if (cleanedDate.isEmpty()) {
            return null;
        }
        try {
            LocalDate.parse(cleanedDate);
            return cleanedDate;
        } catch (DateTimeParseException e) {
            throw new ValidationException(
                    "Expected delivery date must use YYYY-MM-DD format.");
        }
    }
}