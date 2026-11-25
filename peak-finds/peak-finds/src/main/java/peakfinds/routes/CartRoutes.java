
package peakfinds.routes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.auth.ValidationException;
import peakfinds.services.CartService;
import peakfinds.services.CartService.CartItem;
import peakfinds.ui.Layout;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;

public final class CartRoutes {

    private CartRoutes() {
    }

    public static void register(AppContext ctx) {

        CartService service = new CartService(ctx.db());

        ctx.router().get(
                "/cart", Action.MANAGE_CART,
                req -> showCart(req, service)
        );

        ctx.router().post(
                "/cart/add", Action.MANAGE_CART,
                req -> changeCart(req, service, "added")
        );

        ctx.router().post(
                "/cart/update", Action.MANAGE_CART,
                req -> changeCart(req, service, "updated")
        );

        ctx.router().post(
                "/cart/remove", Action.MANAGE_CART,
                req -> changeCart(req, service, "removed")
        );

        ctx.router().post(
                "/cart/clear", Action.MANAGE_CART,
                req -> changeCart(req, service, "cleared")
        );

        ctx.router().post(
                "/cart/payment", Action.MANAGE_CART,
                req -> changeCart(req, service, "payment")
        );
    }

    private static Response changeCart(
            Request req, CartService service, String action) {

        if (req.session() == null
                || req.session().customerId() == null) {
            return Response.redirect("/login");
        }

        long customerId = req.session().customerId();

        try {
            switch (action) {

                case "added" -> service.addProduct(
                        customerId,
                        parseId(req.form("product_id")),
                        parseQuantity(req.form("quantity"))
                );

                case "updated" -> service.updateQuantity(
                        customerId,
                        parseId(req.form("product_id")),
                        parseQuantity(req.form("quantity"))
                );

                case "removed" -> service.removeProduct(
                        customerId,
                        parseId(req.form("product_id"))
                );

                case "cleared" -> service.clearCart(customerId);

                case "payment" -> service.selectPaymentMethod(
                        customerId,
                        req.form("payment_method")
                );

                default -> throw new ValidationException(
                        "Invalid cart action."
                );
            }

            return Response.redirect("/cart?feedback=" + action);

        } catch (ValidationException | IllegalArgumentException e) {

            return Response.html(
                    400,
                    Layout.page(
                            req.session(),
                            "Shopping Cart",
                            popup(
                                    "Unable to Complete Action",
                                    e.getMessage(),
                                    "/cart",
                                    true
                            ) + cartPage(req, service)
                    )
            );
        }
    }

    private static Response showCart(
            Request req, CartService service) {

        if (req.session() == null
                || req.session().customerId() == null) {
            return Response.redirect("/login");
        }

        String feedback = req.query("feedback");

        String title = null;
        String message = null;

        if ("added".equals(feedback)) {
            title = "Product Added!";
            message = "The product was added to your cart successfully.";
        } else if ("updated".equals(feedback)) {
            title = "Quantity Updated!";
            message = "Your cart quantity has been updated successfully.";
        } else if ("removed".equals(feedback)) {
            title = "Product Removed!";
            message = "The product was removed from your cart successfully.";
        } else if ("cleared".equals(feedback)) {
            title = "Cart Cleared!";
            message = "All products have been removed from your cart.";
        } else if ("payment".equals(feedback)) {
            title = "Payment Method Saved!";
            message = "Your selected payment method has been saved.";
        }

        String feedbackHtml = title == null ? ""
                : popup(title, message, "/cart", false);

        return Response.html(
                Layout.page(
                        req.session(),
                        "Shopping Cart",
                        feedbackHtml + cartPage(req, service)
                )
        );
    }

    private static String cartPage(
            Request req, CartService service) {

        long customerId = req.session().customerId();

        List<CartItem> items = service.getItems(customerId);

        StringBuilder body = new StringBuilder();

        String csrf = Html.esc(req.session().csrfToken());

        body.append("""
                <section class="narrow">

                    <h1>My Shopping Cart</h1>

                    <p>
                        Your favourite finds, all in one place.
                        Review your items before checkout.
                    </p>
                """);

        if (items.isEmpty()) {
            body.append("""
                    <div class="card">
                        <h2>Your cart is empty</h2>
                        <p>Explore the available bags below.</p>
                    </div>
                    """);
        }

        for (CartItem item : items) {

            body.append("""
                    <div class="card">

                        <h2>%s</h2>

                        <p>Price: J$%s</p>
                        <p>Quantity: %d</p>
                        <p>Subtotal: J$%s</p>
                        <p>Available stock: %d</p>

                        %s

                        <form class="form"
                              method="post"
                              action="/cart/update">

                            <input type="hidden"
                                   name="_csrf"
                                   value="%s">

                            <input type="hidden"
                                   name="product_id"
                                   value="%d">

                            <label>Change Quantity</label>

                            <input type="number"
                                   name="quantity"
                                   min="0"
                                   value="%d"
                                   required>

                            <button class="button"
                                    type="submit">
                                Update Quantity
                            </button>

                        </form>

                        <form method="post"
                              action="/cart/remove">

                            <input type="hidden"
                                   name="_csrf"
                                   value="%s">

                            <input type="hidden"
                                   name="product_id"
                                   value="%d">

                            <label>
                                <input type="checkbox"
                                       name="confirm"
                                       required>
                                Confirm removal
                            </label>

                            <button class="button"
                                    type="submit">
                                Remove Product
                            </button>

                        </form>

                    </div>
                    """.formatted(
                    Html.esc(item.name()),
                    formatMoney(item.price()),
                    item.quantity(),
                    formatMoney(item.subtotal()),
                    item.availableStock(),
                    !item.active() || item.quantity() > item.availableStock()
                            ? "<p class=\"error\">Please update this item before checkout.</p>"
                            : "",
                    csrf,
                    item.productId(),
                    item.quantity(),
                    csrf,
                    item.productId()
            ));
        }

        body.append("""
                <div class="card">

                    <h2>Cart Total</h2>

                    <p><strong>J$%s</strong></p>

                </div>
                """.formatted(
                formatMoney(service.getTotal(customerId))
        ));

        if (!items.isEmpty()) {
            body.append("""
                    <form method="post"
                          action="/cart/clear"
                          class="form">

                        <input type="hidden"
                               name="_csrf"
                               value="%s">

                        <label>
                            <input type="checkbox"
                                   name="confirm"
                                   required>
                            Confirm clearing your cart
                        </label>

                        <button type="submit" class="button">
                            Clear Cart
                        </button>

                    </form>
                    """.formatted(csrf));
        }

        String selected = service.getPaymentMethod(customerId);

        body.append("""
                <h2>Payment Method</h2>

                <form class="form"
                      method="post"
                      action="/cart/payment">

                    <input type="hidden"
                           name="_csrf"
                           value="%s">

                    <label for="payment_method">
                        Choose Payment Method
                    </label>

                    <select id="payment_method"
                            name="payment_method"
                            required>

                        <option value="">
                            Select a method
                        </option>

                        <option value="Cash on Delivery" %s>
                            Cash on Delivery
                        </option>

                        <option value="Bank Transfer" %s>
                            Bank Transfer
                        </option>

                    </select>

                    <button type="submit" class="button">
                        Save Payment Method
                    </button>

                </form>

                <p>
                    Checkout will be connected to
                    Order Management. Saving a payment
                    method does not place an order.
                </p>
                """.formatted(
                csrf,
                "Cash on Delivery".equals(selected) ? "selected" : "",
                "Bank Transfer".equals(selected) ? "selected" : ""
        ));

        body.append("""
                <h2>Available Bags</h2>

                <p>Add a product to test the shopping cart.</p>
                """);

        List<Map<String, Object>> products =
                service.availableProducts();

        if (products.isEmpty()) {
            body.append(
                    "<p>No products are currently available.</p>"
            );
        }

        for (Map<String, Object> product : products) {

            long id = ((Number) product.get("product_id")).longValue();

            body.append("""
                    <div class="card">

                        <h2>%s</h2>
                        <p>J$%s</p>
                        <p>In stock: %s</p>

                        <form class="form"
                              action="/cart/add"
                              method="post">

                            <input type="hidden"
                                   name="_csrf"
                                   value="%s">

                            <input type="hidden"
                                   name="product_id"
                                   value="%d">

                            <label>Quantity</label>

                            <input type="number"
                                   name="quantity"
                                   min="1"
                                   value="1"
                                   required>

                            <button class="button"
                                    type="submit">
                                Add to Cart
                            </button>

                        </form>

                    </div>
                    """.formatted(
                    Html.esc(String.valueOf(product.get("name"))),
                    formatMoney(new BigDecimal(
                            product.get("price").toString()
                    )),
                    product.get("stock_quantity"),
                    csrf,
                    id
            ));
        }

        body.append("""
                <p>
                    <a href="/catalogue">
                        Browse Catalogue
                    </a>
                </p>

                <p>
                    <a href="/customer">
                        Back to Dashboard
                    </a>
                </p>

                </section>
                """);

        return body.toString();
    }

    private static String popup(
            String title,
            String message,
            String returnUrl,
            boolean error) {

        return """
                <div class="pf-popup-overlay">

                    <div class="pf-popup"
                         role="alertdialog"
                         aria-modal="true"
                         aria-labelledby="cartPopupTitle">

                        <div class="pf-popup-check"
                             aria-hidden="true">
                            %s
                        </div>

                        <h2 id="cartPopupTitle">%s</h2>

                        <p>%s</p>

                        <a href="%s" class="pf-popup-button">
                            OK
                        </a>

                    </div>

                </div>
                """.formatted(
                error ? "!" : "&#10003;",
                Html.esc(title),
                Html.esc(message),
                Html.esc(returnUrl)
        );
    }

    private static long parseId(String value) {

        try {
            long id = Long.parseLong(value);

            if (id > 0) {
                return id;
            }

        } catch (NumberFormatException | NullPointerException e) {
            // Invalid ID is handled below.
        }

        throw new ValidationException("Invalid product selected.");
    }

    private static int parseQuantity(String value) {

        try {
            return Integer.parseInt(value);

        } catch (NumberFormatException | NullPointerException e) {
            throw new ValidationException(
                    "Please enter a valid quantity."
            );
        }
    }

    private static String formatMoney(BigDecimal value) {

        return value.setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
    }
}
