package peakfinds.routes;

import java.util.Map;
import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.ui.Layout;
import peakfinds.ui.StubPages;
import peakfinds.web.Response;
import peakfinds.web.TemplateRenderer;

/** The customer's home page plus placeholders for the catalogue, sign-up, cart, orders and account pages. */
public final class CustomerRoutes {
    private CustomerRoutes() {}

    public static void register(AppContext ctx) {
        ctx.router().get("/customer", Action.VIEW_CUSTOMER_HOME, req -> Response.html(Layout.page(
                req.session(), "Welcome",
                TemplateRenderer.render("customer/home.html", Map.of()))));

        StubPages.register(ctx.router(), "/catalogue", null, "Catalogue");          // public
        StubPages.register(ctx.router(), "/signup", null, "Sign up");               // public
        StubPages.register(ctx.router(), "/cart", Action.MANAGE_CART, "Cart");
        StubPages.register(ctx.router(), "/orders", Action.VIEW_OWN_ORDERS, "My orders");
        StubPages.register(ctx.router(), "/account", Action.MANAGE_OWN_ACCOUNT, "My account");
    }
}
