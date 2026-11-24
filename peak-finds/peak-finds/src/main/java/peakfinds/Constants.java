package peakfinds;

import java.util.List;

/**
 * Every role, status, payment label and limit used across the app.
 * Nobody types these strings by hand: use the constants so spelling never drifts.
 * The wording matches the SRS exactly, and database/schema.sql CHECK constraints use the same text.
 */
public final class Constants {
    private Constants() {}

    // ---- Roles (users.role) ----
    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_CUSTOMER = "customer";
    public static final String ROLE_BEARER = "bearer";
    public static final List<String> ROLES = List.of(ROLE_OWNER, ROLE_CUSTOMER, ROLE_BEARER);

    // ---- Order statuses (orders.order_status) ----
    public static final String ORDER_AWAITING_PAYMENT = "Awaiting Payment";
    public static final String ORDER_CONFIRMED = "Order Confirmed";
    public static final String ORDER_PACKAGING = "Order Packaging";
    public static final String ORDER_CANCELLED = "Cancelled";
    public static final List<String> ORDER_STATUSES =
            List.of(ORDER_AWAITING_PAYMENT, ORDER_CONFIRMED, ORDER_PACKAGING, ORDER_CANCELLED);

    /** SRS Feature 01 UR3 AC-2: cancellation is only allowed while "Order Confirmed" or being processed. */
    public static final List<String> CANCELLABLE_ORDER_STATUSES = List.of(ORDER_CONFIRMED, ORDER_PACKAGING);

    // ---- Delivery statuses (deliveries.delivery_status) ----
    public static final String DELIVERY_ASSIGNED = "Assigned";
    public static final String DELIVERY_OUT_FOR_DELIVERY = "Out for Delivery";
    public static final String DELIVERY_DELIVERED = "Delivered";
    public static final String DELIVERY_FAILED = "Failed";
    public static final String DELIVERY_RETURNED = "Returned";
    public static final List<String> DELIVERY_STATUSES = List.of(
            DELIVERY_ASSIGNED, DELIVERY_OUT_FOR_DELIVERY, DELIVERY_DELIVERED, DELIVERY_FAILED, DELIVERY_RETURNED);

    // ---- Delivery methods (deliveries.delivery_method) ----
    public static final String METHOD_BEARER = "Bearer";
    public static final String METHOD_KNUTSFORD = "Knutsford Express";

    // ---- Payment (orders.payment_method / orders.payment_status) ----
    public static final String PAYMENT_CASH_ON_DELIVERY = "Cash on Delivery";
    public static final String PAYMENT_BANK_TRANSFER = "Bank Transfer";
    public static final List<String> PAYMENT_METHODS = List.of(PAYMENT_CASH_ON_DELIVERY, PAYMENT_BANK_TRANSFER);
    public static final String PAYMENT_STATUS_PENDING = "Pending";
    public static final String PAYMENT_STATUS_PAID = "Paid";

    // ---- Restock orders (restock_orders.status) ----
    public static final String RESTOCK_ORDERED = "Ordered";
    public static final String RESTOCK_RECEIVED = "Received";

    // ---- Business limits (SRS assumptions A-2 and A-3) ----
    public static final int MAX_ACTIVE_BEARERS = 5;
    public static final int MAX_ACTIVE_PRODUCTS = 100;
    public static final int MAX_IMAGES_PER_PRODUCT = 5;

    /**
     * Permission names checked by PrivilegeSys. Every protected route is registered with one of these,
     * e.g. router.get("/owner/products", Action.MANAGE_PRODUCTS, handler).
     */
    public static final class Action {
        private Action() {}

        // Owner
        public static final String VIEW_OWNER_DASHBOARD = "view_owner_dashboard";
        public static final String MANAGE_ORDERS = "manage_orders";
        public static final String CONFIRM_PAYMENT = "confirm_payment";
        public static final String MANAGE_CUSTOMERS = "manage_customers";
        public static final String MANAGE_PRODUCTS = "manage_products";
        public static final String VIEW_LOW_STOCK_ALERTS = "view_low_stock_alerts";
        public static final String MANAGE_SUPPLIERS = "manage_suppliers";
        public static final String MANAGE_RESTOCK_ORDERS = "manage_restock_orders";
        public static final String MANAGE_DELIVERIES = "manage_deliveries";
        public static final String MANAGE_BEARERS = "manage_bearers";

        // Customer
        public static final String VIEW_CUSTOMER_HOME = "view_customer_home";
        public static final String MANAGE_OWN_ACCOUNT = "manage_own_account";
        public static final String MANAGE_CART = "manage_cart";
        public static final String PLACE_ORDER = "place_order";
        public static final String VIEW_OWN_ORDERS = "view_own_orders";
        public static final String CANCEL_OWN_ORDER = "cancel_own_order";

        // Bearer
        public static final String VIEW_DELIVERY_DASHBOARD = "view_delivery_dashboard";
        public static final String VIEW_ASSIGNED_DELIVERIES = "view_assigned_deliveries";
        public static final String UPDATE_DELIVERY_STATUS = "update_delivery_status";
        public static final String RECORD_PAYMENT = "record_payment";
    }
}
