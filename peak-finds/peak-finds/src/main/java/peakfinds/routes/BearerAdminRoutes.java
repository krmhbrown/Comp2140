package peakfinds.routes;

import java.util.Map;
import peakfinds.AppContext;
import peakfinds.Constants;
import peakfinds.Constants.Action;
import peakfinds.auth.Validator;
import peakfinds.auth.ValidationException;
import peakfinds.database.Database;
import peakfinds.database.Rows;
import peakfinds.ui.Layout;
import static peakfinds.ui.DeliveryViews.*;
import peakfinds.web.Html;
import peakfinds.web.Response;

/** Owner-only administration of delivery bearer accounts. */
public final class BearerAdminRoutes {
    private BearerAdminRoutes() {}

    public static void register(AppContext ctx) {
        Database db = ctx.db();
        ctx.router().get("/owner/bearers", Action.MANAGE_BEARERS, req -> {
            var bearers = db.query("""
                SELECT b.bearer_id, b.name, b.phone_number, b.is_active,
                       u.email, u.is_active AS account_active
                FROM bearers b JOIN users u ON u.user_id = b.user_id
                ORDER BY b.is_active DESC, b.bearer_id
                """);
            long active = bearers.stream().filter(b -> Rows.bool(b, "is_active")).count();
            String csrf = Layout.csrfField(req.session());
            StringBuilder html = new StringBuilder("<section class=\"dm-page\"><h1>Bearer Management</h1><p>Manage delivery personnel and their contact details.</p>");
            html.append("<div class=\"card-grid dm-stats\">").append(stat("Active bearers (max 5)",active))
                .append(stat("Available positions",Math.max(0,Constants.MAX_ACTIVE_BEARERS-active)))
                .append(stat("Registered bearers",bearers.size())).append("</div>")
                .append("<p><a href=\"/owner/deliveries\">Back to deliveries</a></p>");
            if (active < Constants.MAX_ACTIVE_BEARERS) {
                html.append("""
                    <section class="card dm-card"><h2>Add bearer</h2>
                    <form class="dm-form" method="post" action="/owner/bearers/add">
                    %s
                    <label>Full name <input name="name" required maxlength="100"></label>
                    <label>Phone <input name="phone" type="tel" required maxlength="20"></label>
                    <label>Email <input name="email" type="email" required maxlength="254"></label>
                    <label>Initial password <input name="password" type="password" required minlength="8" maxlength="128" autocomplete="new-password"></label>
                    <button class="button" type="submit">Add bearer</button></form></section>
                    """.formatted(csrf));
            } else {
                html.append("<p>The maximum of five active bearers has been reached.</p>");
            }
            html.append("<h2>Existing bearers</h2><div class=\"card-grid dm-grid\">");
            if (bearers.isEmpty()) html.append(empty("No bearer accounts found."));
            for (var b : bearers) {
                long id = Rows.longOf(b, "bearer_id");
                boolean enabled = Rows.bool(b, "is_active") && Rows.bool(b, "account_active");
                html.append("""
                    <section class="card dm-card"><div class="dm-card-head"><h2>%s</h2>%s</div><p>Login: %s</p>
                    <form class="dm-form" method="post" action="/owner/bearers/%d/edit">
                    %s
                    <label>Name <input name="name" value="%s" required maxlength="100"></label>
                    <label>Phone <input name="phone" type="tel" value="%s" required maxlength="20"></label>
                    <button class="button" type="submit">Save details</button></form>
                    <form class="dm-form" method="post" action="/owner/bearers/%d/status">
                    %s<input type="hidden" name="active" value="%s">
                    <button class="button" type="submit">%s</button></form></section>
                    """.formatted(e(b,"name"), status(enabled ? "Active" : "Inactive"), e(b,"email"),
                        id, csrf, e(b,"name"), e(b,"phone_number"), id, csrf,
                        enabled ? "0" : "1", enabled ? "Deactivate" : "Reactivate"));
            }
            return Response.html(Layout.page(req.session(), "Bearer Management", html.append("</div></section>").toString()));
        });

        ctx.router().post("/owner/bearers/add", Action.MANAGE_BEARERS, req -> {
            String name = Validator.text(req.form("name"), "Name", 100);
            String phone = Validator.phone(req.form("phone"));
            String email = Validator.email(req.form("email"));
            String password = Validator.newPassword(req.form("password"));
            db.transaction(() -> {
                requireCapacity(db);
                long userId = ctx.auth().createUser(email, password, Constants.ROLE_BEARER);
                db.insert("INSERT INTO bearers(user_id, name, phone_number) VALUES (?, ?, ?)",
                    userId, name, phone);
                return null;
            });
            return Response.redirect("/owner/bearers");
        });

        ctx.router().post("/owner/bearers/{id}/edit", Action.MANAGE_BEARERS, req -> {
            long id = req.pathLong("id");
            String name = Validator.text(req.form("name"), "Name", 100);
            String phone = Validator.phone(req.form("phone"));
            if (db.update("UPDATE bearers SET name = ?, phone_number = ? WHERE bearer_id = ?", name, phone, id) != 1)
                throw new ValidationException("Bearer not found.");
            return Response.redirect("/owner/bearers");
        });

        ctx.router().post("/owner/bearers/{id}/status", Action.MANAGE_BEARERS, req -> {
            long id = req.pathLong("id");
            int active = Validator.intInRange(req.form("active"), "Active status", 0, 1);
            long userId = db.transaction(() -> {
                var b = db.queryOne("SELECT user_id, is_active FROM bearers WHERE bearer_id = ?", id)
                    .orElseThrow(() -> new ValidationException("Bearer not found."));
                long uid = Rows.longOf(b, "user_id");
                if (active == 1 && !Rows.bool(b, "is_active")) requireCapacity(db);
                if (active == 0 && db.queryOne("""
                    SELECT 1 FROM deliveries d JOIN orders o ON o.order_id = d.order_id
                    WHERE d.bearer_id = ? AND (
                        d.delivery_status NOT IN ('Delivered', 'Returned') OR
                        (d.delivery_status = 'Delivered' AND o.payment_method = 'Cash on Delivery'
                         AND o.payment_status = 'Pending')) LIMIT 1
                    """, id).isPresent()) {
                    throw new ValidationException("Complete or return outstanding deliveries and settle COD payments before deactivating this bearer.");
                }
                db.update("UPDATE bearers SET is_active = ? WHERE bearer_id = ?", active, id);
                db.update("UPDATE users SET is_active = ? WHERE user_id = ? AND role = ?", active, uid, Constants.ROLE_BEARER);
                return uid;
            });
            if (active == 0) ctx.sessions().destroyForUser(userId);
            return Response.redirect("/owner/bearers");
        });
    }

    private static void requireCapacity(Database db) {
        long count = Rows.longOf(db.queryOne("SELECT COUNT(*) AS n FROM bearers WHERE is_active = 1").orElseThrow(), "n");
        if (count >= Constants.MAX_ACTIVE_BEARERS)
            throw new ValidationException("A maximum of five bearers can be active at once.");
    }

    private static String e(Map<String, Object> row, String key) {
        return Html.esc(Rows.str(row, key));
    }
}
