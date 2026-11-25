package peakfinds.routes;

import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.ui.OwnerDashboard;
import peakfinds.ui.StubPages;
import peakfinds.services.DeliveryService;
import peakfinds.services.Storage;

/** The owner's dashboard plus a placeholder page for every owner menu item. */
public final class OwnerRoutes {
    private OwnerRoutes() {}

    public static void register(AppContext ctx) {
        OwnerDashboard dashboard = new OwnerDashboard(new DeliveryService(ctx.db()), new Storage(ctx.db()));
        ctx.router().get("/owner", Action.VIEW_OWNER_DASHBOARD, dashboard::page);
        for (OwnerDashboard.MenuItem item : OwnerDashboard.MENU) {
            StubPages.register(ctx.router(), item.path(), item.action(), item.label());
        }
    }
}