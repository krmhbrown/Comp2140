package peakfinds.routes;

import java.util.Map;
import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.ui.Layout;
import peakfinds.ui.StubPages;
import peakfinds.web.Response;
import peakfinds.web.TemplateRenderer;

/** The bearer's home page plus a placeholder for the assigned-deliveries list. */
public final class DeliveryRoutes {
    private DeliveryRoutes() {}

    public static void register(AppContext ctx) {
        ctx.router().get("/delivery", Action.VIEW_DELIVERY_DASHBOARD, req -> Response.html(Layout.page(
                req.session(), "My deliveries",
                TemplateRenderer.render("delivery/home.html", Map.of()))));

        StubPages.register(ctx.router(), "/delivery/assigned", Action.VIEW_ASSIGNED_DELIVERIES, "Assigned deliveries");
    }
}
