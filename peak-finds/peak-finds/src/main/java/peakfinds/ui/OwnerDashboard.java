package peakfinds.ui;

import java.util.List;
import java.util.Map;
import peakfinds.Constants.Action;
import peakfinds.models.Product;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;
import peakfinds.web.TemplateRenderer;
import peakfinds.services.DeliveryService;
import peakfinds.services.Storage;
import peakfinds.routes.DeliveryAdminRoutes;

/**
 * The owner's home page and menu (SDS class "OwnerDashboard", shell only).
 * It shows one card per management area. The links go to placeholder pages until each feature owner
 * replaces them. To add a new owner page, add one line to MENU.
 */
public final class OwnerDashboard {

    /** One card on the dashboard: where it goes, the permission it needs, and a short description. */
    public record MenuItem(String label, String path, String action, String description) {}

    public static final List<MenuItem> MENU = List.of(
            new MenuItem("Orders", "/owner/orders", Action.MANAGE_ORDERS,
                    "View orders, confirm payments, update statuses and cancel orders."),
            new MenuItem("Customers", "/owner/customers", Action.MANAGE_CUSTOMERS,
                    "View customers and their order history."),
            new MenuItem("Products", "/owner/products", Action.MANAGE_PRODUCTS,
                    "Add, edit and remove bags, and manage stock levels."),
            new MenuItem("Low-stock alerts", "/owner/low-stock", Action.VIEW_LOW_STOCK_ALERTS,
                    "Products at or below their reorder level."),
            new MenuItem("Suppliers", "/owner/suppliers", Action.MANAGE_SUPPLIERS,
                    "Manage suppliers and link them to products."),
            new MenuItem("Restock orders", "/owner/restock-orders", Action.MANAGE_RESTOCK_ORDERS,
                    "Record restock orders and mark them received."),
            new MenuItem("Deliveries", "/owner/deliveries", Action.MANAGE_DELIVERIES,
                    "Assign orders to a bearer or Knutsford Express and track delivery."),
            new MenuItem("Bearers", "/owner/bearers", Action.MANAGE_BEARERS,
                    "Add, edit and deactivate delivery bearers (up to 5 active)."));

    private final DeliveryService deliveryService;
    private final Storage storage;

    public OwnerDashboard(DeliveryService deliveryService) {
        this(deliveryService, null);
    }

    public OwnerDashboard(DeliveryService deliveryService, Storage storage) {
        this.deliveryService = deliveryService;
        this.storage = storage;
    }

    /** Builds the dashboard page. */
    public Response page(Request req) {
        StringBuilder cards = new StringBuilder();
        for (MenuItem item : MENU) {
            cards.append("<a class=\"card\" href=\"").append(item.path()).append("\"><h2>")
                    .append(Html.esc(item.label())).append("</h2><p>")
                    .append(Html.esc(item.description())).append("</p></a>");
        }
        String content = TemplateRenderer.render("owner/dashboard.html", Map.of(
                "cards", cards.toString(),
                "deliveryAlerts", DeliveryAdminRoutes.failureAlerts(deliveryService),
                "lowStockAlerts", lowStockAlerts()));
        return Response.html(Layout.page(req.session(), "Owner dashboard", content));
    }

    /** A box at the top of the dashboard when any product is at or below its reorder level (SR-5.13). */
    private String lowStockAlerts() {
        if (storage == null) {
            return "";
        }
        List<Product> low = storage.lowStockProducts();
        if (low.isEmpty()) {
            return "";
        }
        StringBuilder html = new StringBuilder("<section class=\"notice dm-alert\" role=\"alert\"><h2>Low stock</h2><p>")
                .append(low.size()).append(low.size() == 1 ? " product needs" : " products need")
                .append(" reordering:</p><ul>");
        int shown = 0;
        for (Product p : low) {
            if (shown == 5) {
                break;
            }
            html.append("<li>").append(Html.esc(p.getName())).append(" (").append(p.displayId())
                    .append(") &ndash; ").append(p.getStockQuantity()).append(" left</li>");
            shown++;
        }
        if (low.size() > 5) {
            html.append("<li>and ").append(low.size() - 5).append(" more</li>");
        }
        return html.append("</ul><p><a href=\"/owner/low-stock\">View low-stock alerts</a></p></section>").toString();
    }
}