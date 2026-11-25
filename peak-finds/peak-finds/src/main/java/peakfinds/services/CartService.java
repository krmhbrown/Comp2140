
package peakfinds.services;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import peakfinds.auth.ValidationException;
import peakfinds.database.Database;

public final class CartService {

    private final Database db;

    public CartService(Database db) {
        this.db = db;
    }

    public record CartItem(
            long productId,
            String name,
            BigDecimal price,
            int quantity,
            int availableStock,
            boolean active
    ) {
        public BigDecimal subtotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public List<CartItem> getItems(long customerId) {

        return db.query(
                """
                SELECT
                    p.product_id,
                    p.name,
                    p.price,
                    p.stock_quantity,
                    p.is_active,
                    ci.quantity
                FROM carts c
                JOIN cart_items ci ON c.cart_id = ci.cart_id
                JOIN products p ON ci.product_id = p.product_id
                WHERE c.customer_id = ?
                ORDER BY p.name
                """,
                customerId
        ).stream().map(row -> new CartItem(
                longValue(row, "product_id"),
                stringValue(row, "name"),
                money(row.get("price")),
                intValue(row, "quantity"),
                intValue(row, "stock_quantity"),
                intValue(row, "is_active") == 1
        )).toList();
    }

    public List<Map<String, Object>> availableProducts() {

        return db.query(
                """
                SELECT product_id, name, price, stock_quantity
                FROM products
                WHERE is_active = 1 AND stock_quantity > 0
                ORDER BY name
                """
        );
    }

    public BigDecimal getTotal(long customerId) {

        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : getItems(customerId)) {
            total = total.add(item.subtotal());
        }

        return total;
    }

    public String getPaymentMethod(long customerId) {

        return db.queryOne(
                "SELECT payment_method FROM carts WHERE customer_id = ?",
                customerId
        ).map(row -> stringValue(row, "payment_method"))
         .orElse("");
    }

    public void addProduct(
            long customerId,
            long productId,
            int quantity) {

        requirePositive(quantity);

        db.transaction(() -> {

            Map<String, Object> product =
                    activeProduct(productId);

            long cartId = ensureCart(customerId);

            int current = db.queryOne(
                    """
                    SELECT quantity
                    FROM cart_items
                    WHERE cart_id = ? AND product_id = ?
                    """,
                    cartId, productId
            ).map(row -> intValue(row, "quantity"))
             .orElse(0);

            long newQuantity = (long) current + quantity;

            if (newQuantity > intValue(product, "stock_quantity")) {
                throw new ValidationException(
                        "The requested quantity exceeds available stock."
                );
            }

            if (current == 0) {
                db.insert(
                        """
                        INSERT INTO cart_items
                            (cart_id, product_id, quantity)
                        VALUES (?, ?, ?)
                        """,
                        cartId, productId, quantity
                );
            } else {
                db.update(
                        """
                        UPDATE cart_items
                        SET quantity = ?
                        WHERE cart_id = ? AND product_id = ?
                        """,
                        (int) newQuantity, cartId, productId
                );
            }

            touch(cartId);
            return null;
        });
    }

    public void updateQuantity(
            long customerId,
            long productId,
            int quantity) {

        if (quantity < 0) {
            throw new ValidationException(
                    "Quantity cannot be negative."
            );
        }

        db.transaction(() -> {

            long cartId = findCart(customerId);

            if (cartId == 0 || db.queryOne(
                    """
                    SELECT quantity FROM cart_items
                    WHERE cart_id = ? AND product_id = ?
                    """,
                    cartId, productId
            ).isEmpty()) {
                throw new ValidationException(
                        "This product is not in your cart."
                );
            }

            if (quantity == 0) {
                db.update(
                        """
                        DELETE FROM cart_items
                        WHERE cart_id = ? AND product_id = ?
                        """,
                        cartId, productId
                );
            } else {
                Map<String, Object> product =
                        activeProduct(productId);

                if (quantity > intValue(product, "stock_quantity")) {
                    throw new ValidationException(
                            "The requested quantity exceeds available stock."
                    );
                }

                db.update(
                        """
                        UPDATE cart_items SET quantity = ?
                        WHERE cart_id = ? AND product_id = ?
                        """,
                        quantity, cartId, productId
                );
            }

            touch(cartId);
            return null;
        });
    }

    public void removeProduct(long customerId, long productId) {

        db.transaction(() -> {

            long cartId = findCart(customerId);

            if (cartId == 0) {
                throw new ValidationException(
                        "Your cart is empty."
                );
            }

            if (db.queryOne(
                    """
                    SELECT product_id FROM cart_items
                    WHERE cart_id = ? AND product_id = ?
                    """,
                    cartId, productId
            ).isEmpty()) {
                throw new ValidationException(
                        "This product is not in your cart."
                );
            }

            db.update(
                    """
                    DELETE FROM cart_items
                    WHERE cart_id = ? AND product_id = ?
                    """,
                    cartId, productId
            );

            touch(cartId);
            return null;
        });
    }

    public void clearCart(long customerId) {

        db.transaction(() -> {

            long cartId = findCart(customerId);

            if (cartId != 0) {
                db.update(
                        "DELETE FROM cart_items WHERE cart_id = ?",
                        cartId
                );
                touch(cartId);
            }

            return null;
        });
    }

    public void selectPaymentMethod(
            long customerId, String method) {

        if (!"Cash on Delivery".equals(method)
                && !"Bank Transfer".equals(method)) {
            throw new ValidationException(
                    "Please select a valid payment method."
            );
        }

        db.transaction(() -> {

            long cartId = ensureCart(customerId);

            db.update(
                    """
                    UPDATE carts
                    SET payment_method = ?,
                        updated_at = datetime('now', 'localtime')
                    WHERE cart_id = ?
                    """,
                    method, cartId
            );

            return null;
        });
    }

    public void validateForCheckout(long customerId) {

        List<CartItem> items = getItems(customerId);

        if (items.isEmpty()) {
            throw new ValidationException(
                    "Your cart is empty."
            );
        }

        if (getPaymentMethod(customerId).isBlank()) {
            throw new ValidationException(
                    "Please select a payment method."
            );
        }

        for (CartItem item : items) {

            if (!item.active()) {
                throw new ValidationException(
                        "A product in your cart is unavailable."
                );
            }

            if (item.quantity() <= 0
                    || item.quantity() > item.availableStock()) {
                throw new ValidationException(
                        "Please review the available stock in your cart."
                );
            }

            if (item.price().signum() < 0) {
                throw new ValidationException(
                        "A product has an invalid price."
                );
            }
        }
    }

    private Map<String, Object> activeProduct(long productId) {

        return db.queryOne(
                """
                SELECT product_id, stock_quantity
                FROM products
                WHERE product_id = ? AND is_active = 1
                """,
                productId
        ).orElseThrow(() -> new ValidationException(
                "This product is no longer available."
        ));
    }

    private long findCart(long customerId) {

        return db.queryOne(
                "SELECT cart_id FROM carts WHERE customer_id = ?",
                customerId
        ).map(row -> longValue(row, "cart_id"))
         .orElse(0L);
    }

    private long ensureCart(long customerId) {

        long existing = findCart(customerId);

        if (existing != 0) {
            return existing;
        }

        return db.insert(
                "INSERT INTO carts (customer_id) VALUES (?)",
                customerId
        );
    }

    private void touch(long cartId) {

        db.update(
                """
                UPDATE carts
                SET updated_at = datetime('now', 'localtime')
                WHERE cart_id = ?
                """,
                cartId
        );
    }

    private static void requirePositive(int quantity) {

        if (quantity <= 0) {
            throw new ValidationException(
                    "Quantity must be at least one."
            );
        }
    }

    private static long longValue(
            Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private static int intValue(
            Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    private static String stringValue(
            Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private static BigDecimal money(Object value) {
        return new BigDecimal(value.toString());
    }
}
