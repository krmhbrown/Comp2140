package peakfinds.routes;

import java.util.List;
import java.util.Map;

import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.auth.Session;
import peakfinds.services.DeliveryService;
import peakfinds.ui.Layout;
import static peakfinds.ui.DeliveryViews.*;
import peakfinds.database.Rows;
import peakfinds.web.Html;
import peakfinds.web.Response;
import peakfinds.web.Router;
import peakfinds.Constants;
import peakfinds.auth.ValidationException;

public final class DeliveryRoutes {
    private DeliveryRoutes() {}

    public static void register(AppContext ctx) {
        register(ctx, new DeliveryService(ctx.db()));
    }

    public static void register(AppContext ctx, DeliveryService deliveryService) {

        // Bearer's home page.
        ctx.router().get("/delivery", Action.VIEW_DELIVERY_DASHBOARD, req ->
                Response.html(Layout.page(
                        req.session(),
                        "My deliveries",
                        """
                        <h1>My deliveries</h1>
                        <p>See the orders assigned to you and update their delivery status.</p>
                        <div class="card-grid">
                            <a class="card" href="/delivery/assigned">
                                <h2>Assigned deliveries</h2>
                                <p>View the orders assigned to you.</p>
                            </a>
                        </div>
                        """
                ))
        );

        // Shows only the deliveries assigned to the logged-in bearer.
        ctx.router().get("/delivery/assigned", Action.VIEW_ASSIGNED_DELIVERIES, req -> {
            Session session = req.session();

            // The route permission should ensure this is a bearer account.
            // This extra check prevents errors if the session has no bearer record.
            if (session.bearerId() == null) {
                return Router.errorPage(
                        session,
                        403,
                        "Bearer account not found",
                        "Your account is not linked to a delivery bearer record."
                );
            }

            List<Map<String, Object>> deliveries =
                    deliveryService.getBearerDeliveries(session.bearerId());

            StringBuilder rows = new StringBuilder("<section class=\"dm-page\"><h1>Assigned deliveries</h1>"
                    + "<p>View your assigned orders, update progress and record cash payments.</p>"
                    + "<div class=\"card-grid dm-stats\">");
            rows.append(stat("Total assigned",deliveries.size()))
                    .append(stat("Out for Delivery",deliveries.stream().filter(d -> Constants.DELIVERY_OUT_FOR_DELIVERY.equals(Rows.str(d,"delivery_status"))).count()))
                    .append(stat("Delivered",deliveries.stream().filter(d -> delivered(d)).count()))
                    .append("</div><div class=\"card-grid dm-grid\">");
            if (deliveries.isEmpty()) rows.append(empty("You do not have any assigned deliveries."));
            for (Map<String,Object> delivery : deliveries) {
                String id = field(delivery,"delivery_id");
                rows.append("<section class=\"card dm-card\"><div class=\"dm-card-head\"><h2>Order #")
                    .append(field(delivery,"order_id")).append("</h2>")
                    .append(status(Rows.str(delivery,"delivery_status"))).append("</div>")
                    .append(detail("Delivery ID",id))
                    .append(detail("Customer",field(delivery,"customer_name")))
                    .append(detail("Phone",field(delivery,"contact_number")))
                    .append(detail("Address",field(delivery,"delivery_address")))
                    .append(detail("Method",field(delivery,"delivery_method")))
                    .append(detail("Expected date",field(delivery,"expected_date")))
                    .append(detail("Payment method",field(delivery,"payment_method")))
                    .append(detail("Payment",status(Rows.str(delivery,"payment_status"))))
                    .append(detail("Collected",collected(delivery)))
                    .append("<div class=\"dm-actions\"><a href=\"/delivery/").append(id).append("/summary\">Delivery summary</a>")
                    .append("<a href=\"/delivery/").append(id).append("/history\">View history</a></div>")
                    .append(statusForm(session,"/delivery/update-status",delivery,"Update status"))
                    .append(paymentForm(session,delivery)).append("</section>");
            }
            String content = rows.append("</div><p><a href=\"/delivery\">Back to my deliveries</a></p></section>").toString();

            return Response.html(Layout.page(
                    session,
                    "Assigned deliveries",
                    content
            ));
        });
        ctx.router().get("/delivery/{id}/summary", Action.VIEW_ASSIGNED_DELIVERIES, req -> {
            long id = req.pathLong("id");
            var delivery = deliveryService.getDelivery(id)
                    .orElseThrow(() -> new ValidationException("Delivery not found."));
            Long assigned = peakfinds.database.Rows.longOrNull(delivery, "bearer_id");
            if (assigned == null || !ctx.privileges().canAccessBearerRecord(req.session(), assigned)) {
                return Router.errorPage(req.session(), 403, "Access denied", "Not your delivery.");
            }
            return Response.html(Layout.page(req.session(), "Delivery summary",
                    DeliveryAdminRoutes.summaryHtml(deliveryService.buildDeliverySummary(id))
                    + "<p><a href=\"/delivery/assigned\">Back</a></p>"));
        });

        // Allows a bearer to update the status of an assigned delivery.
        ctx.router().post(
                "/delivery/update-status",
                Action.UPDATE_DELIVERY_STATUS,
                req -> {
                    Session session = req.session();

                    if (session.bearerId() == null) {
                        return Router.errorPage(
                                session,
                                403,
                                "Bearer account not found",
                                "Your account is not linked to a delivery bearer record."
                        );
                    }

                    long deliveryId = parseLong(
                            req.form("delivery_id"),
                            "Delivery ID"
                    );

                    String status = req.form("status");
                    String reason = req.form("reason");

                    deliveryService.updateDeliveryStatus(
                            deliveryId,
                            session.bearerId(),
                            status,
                            reason,
                            session.userId()
                    );

                    return Response.redirect("/delivery/assigned");
                }
        );

        // Allows a bearer to record payment collected for an assigned delivery.
        ctx.router().post(
                "/delivery/record-payment",
                Action.RECORD_PAYMENT,
                req -> {
                    Session session = req.session();

                    if (session.bearerId() == null) {
                        return Router.errorPage(
                                session,
                                403,
                                "Bearer account not found",
                                "Your account is not linked to a delivery bearer record."
                        );
                    }

                    long deliveryId = parseLong(
                            req.form("delivery_id"),
                            "Delivery ID"
                    );

                    double amount;

                    try {
                        amount = Double.parseDouble(peakfinds.auth.Validator.required(req.form("amount"), "Amount"));
                    } catch (NumberFormatException e) {
                        throw new ValidationException(
                                "Invalid payment amount."
                        );
                    }

                    deliveryService.recordPayment(
                            deliveryId,
                            session.bearerId(),
                            amount,
                            session.userId()
                    );

                    return Response.redirect("/delivery/assigned");
                }
        );

        // Displays the status history of a delivery assigned to the bearer.
        ctx.router().get(
                "/delivery/{id}/history",
                Action.VIEW_ASSIGNED_DELIVERIES,
                req -> {
                    Session session = req.session();

                    if (session.bearerId() == null) {
                        return Router.errorPage(
                                session,
                                403,
                                "Bearer account not found",
                                "Your account is not linked to a delivery bearer record."
                        );
                    }

                    long deliveryId = req.pathLong("id");

                    Map<String, Object> delivery = deliveryService
                            .getDelivery(deliveryId)
                            .orElseThrow(() ->
                                    new ValidationException("Delivery not found."));

                    Object assignedBearer = delivery.get("bearer_id");

                    if (assignedBearer == null
                            || Long.parseLong(assignedBearer.toString())
                                    != session.bearerId()) {
                        return Router.errorPage(
                                session,
                                403,
                                "Access denied",
                                "You are not assigned to this delivery."
                        );
                    }

                    List<Map<String, Object>> history =
                            deliveryService.getDeliveryHistory(deliveryId);

                    StringBuilder historyRows = new StringBuilder();

                    for (Map<String, Object> entry : history) {
                        historyRows.append("<tr>")
                                .append("<td>").append(Html.esc(value(entry, "status"))).append("</td>")
                                .append("<td>").append(Html.esc(value(entry, "reason"))).append("</td>")
                                .append("<td>").append(Html.esc(value(entry, "changed_at"))).append("</td>")
                                .append("<td>").append(Html.esc(value(entry, "changed_by"))).append("</td>")
                                .append("</tr>");
                    }

                    if (history.isEmpty()) {
                        historyRows.append("""
                            <tr>
                                <td colspan="4">No status history is available for this delivery.</td>
                            </tr>
                            """);
                    }

                    String content = """
                        <h1>Delivery status history</h1>
                        <p>Delivery ID: %s</p>
                        <p>Order ID: %s</p>

                        <div class="card dm-history table-wrapper">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Status</th>
                                        <th>Reason</th>
                                        <th>Date and time</th>
                                        <th>Changed by (User ID)</th>
                                    </tr>
                                </thead>
                                <tbody>
                        """ .formatted(
                                Html.esc(value(delivery, "delivery_id")),
                                Html.esc(value(delivery, "order_id"))
                        ) + historyRows + """
                                </tbody>
                            </table>
                        </div>

                        <p><a href="/delivery/assigned">Back to assigned deliveries</a></p>
                        """;

                    return Response.html(Layout.page(
                            session,
                            "Delivery history",
                            content
                    ));
                }
        );
    }

    private static String value(Map<String, Object> row, String column) {
        Object result = row.get(column);
        return result == null ? "" : result.toString();
    }
        private static long parseLong(String value, String fieldName) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new ValidationException(
                    "Invalid " + fieldName + "."
            );
        }
    }
}

