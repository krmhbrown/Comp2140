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

/** Customer shopping bag. Uses CartService for all business logic and persistence. */
public final class CartRoutes {
    private CartRoutes() {}

    public static void register(AppContext ctx) {
        CartService cart = new CartService(ctx.db());
        ctx.router().get("/cart", Action.MANAGE_CART, req -> showCart(ctx, cart, req));
        ctx.router().post("/cart/add", Action.MANAGE_CART, req -> change(ctx, cart, req, "added"));
        ctx.router().post("/cart/update", Action.MANAGE_CART, req -> change(ctx, cart, req, "updated"));
        ctx.router().post("/cart/remove", Action.MANAGE_CART, req -> change(ctx, cart, req, "removed"));
        ctx.router().post("/cart/clear", Action.MANAGE_CART, req -> change(ctx, cart, req, "cleared"));
        ctx.router().post("/cart/payment", Action.MANAGE_CART, req -> change(ctx, cart, req, "payment"));
    }

    private static Response change(AppContext ctx, CartService cart, Request req, String action) {
        if (req.session() == null || req.session().customerId() == null) {
            return Response.redirect("/login");
        }
        long customerId = req.session().customerId();
        try {
            switch (action) {
                case "added" -> cart.addProduct(customerId, id(req.form("product_id")), quantity(req.form("quantity")));
                case "updated" -> cart.updateQuantity(customerId, id(req.form("product_id")), quantity(req.form("quantity")));
                case "removed" -> {
                    checkConfirmation(req);
                    cart.removeProduct(customerId, id(req.form("product_id")));
                }
                case "cleared" -> {
                    checkConfirmation(req);
                    cart.clearCart(customerId);
                }
                case "payment" -> cart.selectPaymentMethod(customerId, req.form("payment_method"));
                default -> throw new ValidationException("Invalid cart action.");
            }
            return Response.redirect("/cart?feedback=" + action);
        } catch (ValidationException | IllegalArgumentException e) {
            return Response.html(400, Layout.page(req.session(), "Shopping Bag",
                    popup("Couldn't Complete That Action", e.getMessage(), true) + cartPage(ctx, cart, req)));
        }
    }

    private static void checkConfirmation(Request req) {
        if (!"yes".equals(req.form("confirm"))) {
            throw new ValidationException("Please confirm the removal first.");
        }
    }

    private static Response showCart(AppContext ctx, CartService cart, Request req) {
        if (req.session() == null || req.session().customerId() == null) {
            return Response.redirect("/login");
        }
        String feedback = req.query("feedback");
        String notice = switch (feedback == null ? "" : feedback) {
            case "added" -> popup("Added to Your Bag!", "Your product was added successfully.", false);
            case "updated" -> popup("Quantity Updated!", "Your shopping bag has been updated.", false);
            case "removed" -> popup("Item Removed!", "The product was removed from your bag.", false);
            case "cleared" -> popup("Shopping Bag Cleared!", "All products have been removed from your bag.", false);
            case "payment" -> popup("Payment Method Saved!", "Your payment preference has been saved.", false);
            default -> "";
        };
        return Response.html(Layout.page(req.session(), "Shopping Bag", notice + cartPage(ctx, cart, req)));
    }

    private static String cartPage(AppContext ctx, CartService cart, Request req) {
        long customerId = req.session().customerId();
        List<CartItem> items = cart.getItems(customerId);
        int itemCount = items.stream().mapToInt(CartItem::quantity).sum();
        String csrf = Html.esc(req.session().csrfToken());
        String selectedMethod = cart.getPaymentMethod(customerId);

        StringBuilder html = new StringBuilder();
        html.append("""
            <section class="pf-bag-page">
              <div class="pf-bag-heading">
                <div>
                  <p class="pf-bag-eyebrow">PEAK FINDS</p>
                  <h1>My Shopping Bag</h1>
                  <p>Almost yours. Review your favourite finds before checking out.</p>
                </div>
                <span class="pf-bag-count">%d item%s</span>
              </div>
              <div class="pf-bag-layout">
                <div class="pf-bag-main">
                  <div class="pf-bag-panel">
                    <div class="pf-bag-panel-head">
                      <h2>Bag Items</h2>
                      <a href="/catalogue">Continue shopping &rarr;</a>
                    </div>
            """.formatted(itemCount, itemCount == 1 ? "" : "s"));

        if (items.isEmpty()) {
            html.append("""
                <div class="pf-bag-empty">
                  <div class="pf-bag-empty-icon" aria-hidden="true">&#128092;</div>
                  <h3>Your bag is waiting for its first find.</h3>
                  <p>Explore the collection and add a bag you love.</p>
                  <a class="pf-bag-primary" href="/catalogue">Explore the Catalogue</a>
                </div>
                """);
        } else {
            for (CartItem item : items) {
                String warning = (!item.active() || item.quantity() > item.availableStock())
                        ? "<p class=\"pf-bag-warning\">Unavailable or insufficient stock. Please update your bag.</p>"
                        : "<p class=\"pf-bag-stock\">In stock</p>";
                String photo = imageFor(ctx, item.productId(), item.name());
                html.append("""
                    <article class="pf-bag-item">
                      %s
                      <div class="pf-bag-item-body">
                        <a class="pf-bag-product-name" href="/products/%d">%s</a>
                        <p class="pf-bag-unit-price">J$%s each</p>
                        %s
                        <div class="pf-bag-item-actions">
                          <form method="post" action="/cart/update" class="pf-bag-qty-form">
                            <input type="hidden" name="_csrf" value="%s">
                            <input type="hidden" name="product_id" value="%d">
                            <label for="qty-%d">Qty</label>
                            <input id="qty-%d" name="quantity" type="number" min="0" max="%d" value="%d" required>
                            <button type="submit">Update</button>
                          </form>
                          <form method="post" action="/cart/remove" class="pf-bag-remove-form">
                            <input type="hidden" name="_csrf" value="%s">
                            <input type="hidden" name="product_id" value="%d">
                            <input type="hidden" name="confirm" value="yes">
                            <button type="submit" aria-label="Remove %s from bag">Remove</button>
                          </form>
                        </div>
                      </div>
                      <strong class="pf-bag-item-total">J$%s</strong>
                    </article>
                    """.formatted(
                        photo, item.productId(), Html.esc(item.name()), format(item.price()), warning,
                        csrf, item.productId(), item.productId(), item.productId(), Math.max(0, item.availableStock()),
                        item.quantity(), csrf, item.productId(), Html.esc(item.name()), format(item.subtotal())));
            }
        }
        html.append("</div>"); // close items panel

        if (!items.isEmpty()) {
            html.append("""
                <form class="pf-bag-clear" action="/cart/clear" method="post">
                  <input type="hidden" name="_csrf" value="%s">
                  <label><input type="checkbox" name="confirm" value="yes" required> I want to remove all items</label>
                  <button type="submit">Clear my bag</button>
                </form>
                """.formatted(csrf));
        }

        // A collapsed quick-add section preserves the existing add-to-cart feature
        // without crowding the primary shopping bag interface.
        html.append("""
            <details class="pf-bag-quick-add">
              <summary>Want to add another find? <span>Browse available bags</span></summary>
              <div class="pf-bag-quick-grid">
            """);
        List<Map<String, Object>> products = cart.availableProducts();
        if (products.isEmpty()) {
            html.append("<p>There are no available products right now.</p>");
        }
        for (Map<String, Object> product : products) {
            long productId = ((Number) product.get("product_id")).longValue();
            String name = String.valueOf(product.get("name"));
            html.append("""
                <div class="pf-bag-quick-card">
                  %s
                  <div><a href="/products/%d">%s</a><p>J$%s</p></div>
                  <form action="/cart/add" method="post">
                    <input type="hidden" name="_csrf" value="%s">
                    <input type="hidden" name="product_id" value="%d">
                    <label for="add-qty-%d">Qty</label>
                    <input id="add-qty-%d" name="quantity" type="number" value="1" min="1" max="%d" required>
                    <button type="submit">Add to bag</button>
                  </form>
                </div>
                """.formatted(imageFor(ctx, productId, name), productId, Html.esc(name),
                    format(new BigDecimal(product.get("price").toString())), csrf, productId,
                    productId, productId, ((Number) product.get("stock_quantity")).intValue()));
        }
        html.append("</div></details></div>"); // quick add, main column

        html.append("""
            <aside class="pf-bag-summary">
              <div class="pf-bag-summary-card">
                <h2>Order Summary</h2>
                <div class="pf-bag-summary-row"><span>Subtotal (%d items)</span><strong>J$%s</strong></div>
                <div class="pf-bag-summary-row"><span>Delivery</span><span>Calculated when ordering</span></div>
                <div class="pf-bag-summary-total"><span>Items total</span><strong>J$%s</strong></div>
                <p class="pf-bag-note">Delivery charges are not included in this item total.</p>
                <h3>Payment Preference</h3>
                <form method="post" action="/cart/payment" class="pf-bag-payment-form">
                  <input type="hidden" name="_csrf" value="%s">
                  <label for="payment_method">Select your payment method</label>
                  <select id="payment_method" name="payment_method" required>
                    <option value="">Choose a method</option>
                    <option value="Cash on Delivery" %s>Cash on Delivery</option>
                    <option value="Bank Transfer" %s>Bank Transfer</option>
                  </select>
                  <button type="submit" class="pf-bag-primary">Save Payment Method</button>
                </form>
                <p class="pf-bag-checkout-note">Checkout and order confirmation will be connected to Order Management. No order is placed by saving a payment method.</p>
                <a href="/orders" class="pf-bag-secondary-link">View My Orders &rarr;</a>
              </div>
            </aside>
            </div>
            </section>
            """.formatted(itemCount, format(cart.getTotal(customerId)), format(cart.getTotal(customerId)), csrf,
                "Cash on Delivery".equals(selectedMethod) ? "selected" : "",
                "Bank Transfer".equals(selectedMethod) ? "selected" : ""));
        return html.toString();
    }

    /** Reuses the confirmed working site-wide popup classes. */
    private static String popup(String title, String message, boolean error) {
        return """
            <div class="pf-popup-overlay">
              <div class="pf-popup" role="alertdialog" aria-modal="true"
                   aria-labelledby="cartFeedbackTitle" aria-describedby="cartFeedbackText">
                <div class="pf-popup-check" aria-hidden="true">%s</div>
                <h2 id="cartFeedbackTitle">%s</h2>
                <p id="cartFeedbackText">%s</p>
                <a href="/cart" class="pf-popup-button">OK</a>
              </div>
            </div>
            """.formatted(error ? "!" : "&#10003;", Html.esc(title), Html.esc(message));
    }

    /** Match Product Management's existing /static/{image_path} convention. */
    private static String imageFor(AppContext ctx, long productId, String name) {
        String imagePath = ctx.db().queryOne(
                "SELECT image_path FROM product_images WHERE product_id = ? ORDER BY sort_order, image_id LIMIT 1",
                productId).map(row -> String.valueOf(row.get("image_path"))).orElse("");
        if (!imagePath.isBlank() && imagePath.matches("[A-Za-z0-9_./-]+")
                && !imagePath.contains("..") && !imagePath.startsWith("/")) {
            return "<img class=\"pf-bag-item-image\" src=\"/static/" + Html.esc(imagePath)
                    + "\" alt=\"" + Html.esc(name) + "\" loading=\"lazy\">";
        }
        return "<div class=\"pf-bag-photo-placeholder\" aria-label=\"No product photo\">&#128092;</div>";
    }

    private static long id(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) return parsed;
        } catch (NumberFormatException ignored) { }
        throw new ValidationException("Please choose a valid product.");
    }

    private static int quantity(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            throw new ValidationException("Please enter a valid quantity.");
        }
    }

    private static String format(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
