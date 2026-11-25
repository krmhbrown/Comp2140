package peakfinds.ui;

import java.util.List;
import java.util.Map;
import peakfinds.Constants.Action;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;
import peakfinds.web.TemplateRenderer;
import peakfinds.services.DeliveryService;
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

        public OwnerDashboard(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
}

    /** Builds the dashboard page. */
    public Response page(Request req) {
        StringBuilder cards = new StringBuilder();
        for (MenuItem item : MENU) {
            cards.append("<a class=\"card\" href=\"").append(item.path()).append("\"><h2>")
                    .append(Html.esc(item.label())).append("</h2><p>")
                    .append(Html.esc(item.description())).append("</p></a>");
        }
        String content = TemplateRenderer.render("owner/dashboard.html",Map.of("cards", cards.toString(),"deliveryAlerts",DeliveryAdminRoutes.failureAlerts(deliveryService)));
        return Response.html(Layout.page(req.session(), "Owner dashboard", content));
    }
}
