package peakfinds.routes;

import java.util.Map;
import peakfinds.AppContext;
import peakfinds.Constants;
import peakfinds.Constants.Action;
import peakfinds.auth.Session;
import peakfinds.auth.Validator;
import peakfinds.auth.ValidationException;
import peakfinds.database.Rows;
import peakfinds.services.DeliveryService;
import peakfinds.ui.Layout;
import static peakfinds.ui.DeliveryViews.*;
import peakfinds.web.Html;
import peakfinds.web.Response;

/** Owner-only delivery assignment, tracking, summary and COD confirmation. */
public final class DeliveryAdminRoutes {
    private DeliveryAdminRoutes() {}

    public static void register(AppContext ctx) {
        register(ctx, new DeliveryService(ctx.db()));
    }

    public static void register(AppContext ctx, DeliveryService service) {
        ctx.router().get("/owner/deliveries", Action.MANAGE_DELIVERIES, req -> {
            Session session = req.session();
            var orders = service.getAssignableOrders();
            var deliveries = service.getAllDeliveries();
            var bearers = service.getActiveBearers();
            long completed = deliveries.stream().filter(d -> delivered(d)).count();
            StringBuilder html = new StringBuilder("<section class=\"dm-page\"><h1>Delivery Management</h1>"
                    + "<p>Assign confirmed orders, track delivery progress and confirm cash payments.</p>"
                    + "<p><a class=\"dm-link\" href=\"/owner/bearers\">Manage bearers</a> · <a href=\"/owner\">Back to Owner Dashboard</a></p>"
                    + "<div class=\"card-grid dm-stats\">");
            html.append(stat("Awaiting assignment", orders.size())).append(stat("Delivery records", deliveries.size()))
                    .append(stat("Delivered", completed)).append(stat("Active bearers", bearers.size())).append("</div>");
            html.append(failureAlerts(service));
            html.append("<h2>Confirmed, unassigned orders</h2><div class=\"card-grid dm-grid\">");
            StringBuilder options = new StringBuilder("<option value=\"\">Select a bearer</option>");
            for (var b : bearers) options.append("<option value=\"").append(e(b,"bearer_id")).append("\">").append(e(b,"name")).append("</option>");
            if (orders.isEmpty()) html.append(empty("No orders awaiting assignment."));
            for (var o : orders) {
                String id = e(o,"order_id");
                html.append("<section class=\"card dm-card\"><div class=\"dm-card-head\"><h2>Order #").append(id).append("</h2>")
                        .append(status("Confirmed")).append("</div>")
                        .append(detail("Customer",field(o,"customer_name")))
                        .append(detail("Address",field(o,"delivery_address")))
                        .append(detail("Payment method",field(o,"payment_method")))
                        .append(detail("Order total", "J$" + field(o,"total_amount")));
                if (!bearers.isEmpty()) {
                    html.append(form(session,"/owner/deliveries/assign-bearer","order_id",id))
                            .append("<label>Bearer<select name=\"bearer_id\" required>").append(options)
                            .append("</select></label><label>Expected date<input type=\"date\" name=\"expected_date\"></label>")
                            .append("<button class=\"button\" type=\"submit\">Assign bearer</button></form>");
                } else html.append("<p class=\"notice\">No active bearers. Add or reactivate a bearer first.</p>");
                boolean unpaidCod = Constants.PAYMENT_CASH_ON_DELIVERY.equals(Rows.str(o,"payment_method"))
                        && !Constants.PAYMENT_STATUS_PAID.equals(Rows.str(o,"payment_status"));
                if (!unpaidCod) html.append("<details class=\"dm-alternative\"><summary>Assign to Knutsford Express</summary>")
                        .append(form(session,"/owner/deliveries/assign-knutsford","order_id",id))
                        .append("<label>Waybill<input name=\"bill_number\" required maxlength=\"80\"></label>")
                        .append("<label>Expected date<input type=\"date\" name=\"expected_date\" required></label>")
                        .append("<button class=\"button\" type=\"submit\">Assign to Knutsford Express</button></form></details>");
                else html.append("<p class=\"dm-muted\">Unpaid COD orders need a bearer to collect cash.</p>");
                html.append("</section>");
            }
            html.append("</div><h2>Assigned and completed deliveries</h2><div class=\"card-grid dm-grid\">");
            if (deliveries.isEmpty()) html.append(empty("No deliveries have been assigned yet."));
            for (var d : deliveries) {
                String id = e(d,"delivery_id");
                html.append("<section class=\"card dm-card\"><div class=\"dm-card-head\"><h2>Order #").append(e(d,"order_id")).append("</h2>")
                        .append(status(Rows.str(d,"delivery_status"))).append("</div>")
                        .append(detail("Customer",field(d,"customer_name")))
                        .append(detail("Method",field(d,"delivery_method")))
                        .append(detail("Address",field(d,"delivery_address")))
                        .append(detail("Expected date",field(d,"expected_date")));
                boolean partner = Constants.METHOD_KNUTSFORD.equals(Rows.str(d,"delivery_method"));
                html.append(partner ? detail("Waybill",field(d,"bill_number")) : detail("Bearer",field(d,"bearer_name")))
                        .append(detail("Payment",status(Rows.str(d,"payment_status"))))
                        .append(detail("Collected",collected(d)))
                        .append("<p><a href=\"/owner/deliveries/").append(id).append("/summary\">View summary and history</a></p>");
                if (partner) html.append(statusForm(session,"/owner/deliveries/partner-status",d,"Update partner status"));
                if (delivered(d) && pendingCod(d)) {
                    if (outstanding(d).signum() == 0) html.append(form(session,"/owner/deliveries/confirm-payment","delivery_id",id))
                            .append("<button class=\"button\" type=\"submit\">Confirm COD payment</button></form>");
                    else html.append("<p class=\"notice\">Awaiting full cash collection.</p>");
                }
                html.append("</section>");
            }
            html.append("</div></section>");
            return Response.html(Layout.page(session, "Delivery Management", html.toString()));
        });

        ctx.router().post("/owner/deliveries/assign-bearer", Action.MANAGE_DELIVERIES, req -> {
            long id = service.assignDeliveryToBearer(id(req.form("order_id")), id(req.form("bearer_id")),
                    req.form("expected_date"), req.session().userId());
            return Response.redirect("/owner/deliveries/" + id + "/summary");
        });
        ctx.router().post("/owner/deliveries/assign-knutsford", Action.MANAGE_DELIVERIES, req -> {
            long id = service.assignDeliveryToKnutsford(id(req.form("order_id")), req.form("bill_number"),
                    req.form("expected_date"), req.session().userId());
            return Response.redirect("/owner/deliveries/" + id + "/summary");
        });
        ctx.router().post("/owner/deliveries/partner-status", Action.MANAGE_DELIVERIES, req -> {
            service.updatePartnerStatus(id(req.form("delivery_id")), req.form("status"), req.form("reason"), req.session().userId());
            return Response.redirect("/owner/deliveries");
        });
        ctx.router().post("/owner/deliveries/confirm-payment", Action.CONFIRM_PAYMENT, req -> {
            service.confirmCodPayment(id(req.form("delivery_id")), req.session().userId());
            return Response.redirect("/owner/deliveries");
        });
        ctx.router().get("/owner/deliveries/{id}/summary", Action.MANAGE_DELIVERIES, req -> {
            long id = req.pathLong("id");
            StringBuilder html = new StringBuilder(summaryHtml(service.buildDeliverySummary(id)));
            html.append("<h2>Status history</h2><div class=\"card dm-history\"><ul>");
            for (var row : service.getDeliveryHistory(id)) {
                html.append("<li>").append(e(row, "status")).append(" — ").append(e(row, "reason"))
                        .append(" — ").append(e(row, "changed_at")).append(" — User #")
                        .append(e(row, "changed_by")).append("</li>");
            }
            html.append("</ul></div><p><a href=\"/owner/deliveries\">Back to deliveries</a></p>");
            return Response.html(Layout.page(req.session(), "Delivery summary", html.toString()));
        });
    }

    public static String failureAlerts(DeliveryService service) {
        var alerts = service.getUnreadEmailFailureNotifications();
        if (alerts.isEmpty()) return "";
        StringBuilder html = new StringBuilder("<section class=\"notice dm-alert\" role=\"alert\"><h2>Email delivery failures</h2><ul>");
        for (var a : alerts) html.append("<li>").append(e(a, "message")).append(" — ").append(e(a, "created_at")).append("</li>");
        return html.append("</ul></section>").toString();
    }

    static String summaryHtml(Map<String, String> summary) {
        StringBuilder html = new StringBuilder("<h1>Delivery summary</h1><section class=\"card dm-summary\"><dl>");
        String[] keys = {"order_number", "customer_name", "phone", "address", "items", "payment_method", "amount_to_collect"};
        String[] labels = {"Order number", "Customer name", "Phone", "Address", "Items", "Payment method", "Amount to collect (JMD)"};
        for (int i = 0; i < keys.length; i++) html.append("<dt>").append(labels[i]).append("</dt><dd>")
                .append(Html.esc(summary.get(keys[i]))).append("</dd>");
        return html.append("</dl></section>").toString();
    }

    private static String e(Map<String, Object> row, String key) { return Html.esc(Rows.str(row, key)); }
    private static long id(String value) {
        try {
            long id = Long.parseLong(Validator.required(value, "ID"));
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {}
        throw new ValidationException("Invalid ID.");
    }
}
