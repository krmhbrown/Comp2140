//Author: Karimah Brown
//Date of Creation: 24/11/2026
//Date of last modification: 25/11/2026


package peakfinds.routes;

import java.util.ArrayList;
import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.database.Database;
import peakfinds.models.Product;
import peakfinds.models.RestockOrder;
import peakfinds.models.Supplier;
import peakfinds.ui.Layout;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;
import peakfinds.web.Router;

public class SupplierRoutes implements Router.Handler {
    private Database db;

    //Constructor to create instance of the supplier pages.
    public SupplierRoutes(AppContext ctx) {
        this.db = ctx.db();
    }

    //Adds every supplier page to the website. Only the owner is allowed to open them.
    public static void register(AppContext ctx) {
        SupplierRoutes pages = new SupplierRoutes(ctx);
        Router r = ctx.router();
        r.get("/owner/suppliers", Action.MANAGE_SUPPLIERS, pages);
        r.get("/owner/suppliers/add", Action.MANAGE_SUPPLIERS, pages);
        r.post("/owner/suppliers/add", Action.MANAGE_SUPPLIERS, pages);
        r.get("/owner/suppliers/{id}/edit", Action.MANAGE_SUPPLIERS, pages);
        r.post("/owner/suppliers/{id}/edit", Action.MANAGE_SUPPLIERS, pages);
        r.get("/owner/suppliers/{id}/remove", Action.MANAGE_SUPPLIERS, pages);
        r.post("/owner/suppliers/{id}/remove", Action.MANAGE_SUPPLIERS, pages);
        r.get("/owner/suppliers/{id}/products", Action.MANAGE_SUPPLIERS, pages);
        r.post("/owner/suppliers/{id}/products", Action.MANAGE_SUPPLIERS, pages);
        r.get("/owner/suppliers/{id}/restock", Action.MANAGE_RESTOCK_ORDERS, pages);
        r.post("/owner/suppliers/{id}/restock", Action.MANAGE_RESTOCK_ORDERS, pages);
        r.get("/owner/restock-orders", Action.MANAGE_RESTOCK_ORDERS, pages);
        r.post("/owner/restock-orders/{id}/received", Action.MANAGE_RESTOCK_ORDERS, pages);
        r.get("/owner/restock-orders/{id}/cancel", Action.MANAGE_RESTOCK_ORDERS, pages);
        r.post("/owner/restock-orders/{id}/cancel", Action.MANAGE_RESTOCK_ORDERS, pages);
    }

    //Picks which page to show from the web address. POST means a form was submitted.
    public Response handle(Request req) {
        String path = req.path();
        boolean formSubmitted = req.method().equals("POST");

        if (path.equals("/owner/suppliers")) {
            return supplierList(req);
        }
        if (path.equals("/owner/suppliers/add")) {
            if (formSubmitted) {
                return addSubmit(req);
            }
            return supplierForm(req, "Add supplier", "/owner/suppliers/add", new Supplier("", "", "", ""), "");
        }
        if (path.equals("/owner/restock-orders")) {
            return restockHistory(req);
        }
        if (path.endsWith("/received")) {
            return receivedSubmit(req);
        }
        if (path.endsWith("/cancel")){
            if (formSubmitted){
                return cancelSubmit(req);
            }
            return cancelConfirm(req);
        }
        

        //The rest of the pages are for one supplier, e.g. /owner/suppliers/3/edit
        Supplier supplier = Supplier.findById(db, getId(req));
        if (supplier == null || !supplier.isActive()) {
            return message(req, "Supplier not found", "That supplier does not exist or has been removed.");
        }
        if (path.endsWith("/edit")) {
            if (formSubmitted) {
                return editSubmit(req, supplier);
            }
            return supplierForm(req, "Edit supplier", "/owner/suppliers/" + supplier.getId() + "/edit", supplier, "");
        }
        if (path.endsWith("/remove")) {
            if (formSubmitted) {
                return removeSubmit(req, supplier);
            }
            return removeConfirm(req, supplier);
        }
        if (path.endsWith("/products")) {
            if (formSubmitted) {
                return linkSubmit(req, supplier);
            }
            return productsPage(req, supplier);
        }
        if (path.endsWith("/restock")) {
            if (formSubmitted) {
                return restockSubmit(req, supplier);
            }
            return restockForm(req, supplier, "");
        }
        return message(req, "Page not found", "That page does not exist.");
    }

    //Page that lists all suppliers.
    private Response supplierList(Request req) {
        String html = "<h1>Suppliers</h1>"
                + "<p><a class=\"button\" href=\"/owner/suppliers/add\">Add supplier</a> "
                + "<a href=\"/owner/restock-orders\">Restock orders</a></p>";

        ArrayList<Supplier> suppliers = Supplier.findAll(db);
        if (suppliers.isEmpty()) {
            html += "<p>No suppliers yet.</p>";
            return page(req, "Suppliers", html);
        }
        html += "<table class=\"table\"><tr><th>ID</th><th>Name</th><th>Phone</th><th>Email</th><th>Location</th><th></th></tr>";
        for (int i = 0; i < suppliers.size(); i++) {
            Supplier s = suppliers.get(i);
            String link = "/owner/suppliers/" + s.getId();
            String location = s.getLocation();
            if (location.isEmpty()) {
                location = "Not provided";
            }
            html += "<tr><td>" + s.getSupplierID() + "</td>"
                    + "<td>" + Html.esc(s.getName()) + "</td>"
                    + "<td>" + Html.esc(s.getPhone()) + "</td>"
                    + "<td>" + Html.esc(s.getEmail()) + "</td>"
                    + "<td>" + Html.esc(location) + "</td>"
                    + "<td><a href=\"" + link + "/edit\">Edit</a> | "
                    + "<a href=\"" + link + "/products\">Products</a> | "
                    + "<a href=\"" + link + "/restock\">New restock order</a> | "
                    + "<a href=\"" + link + "/remove\">Remove</a></td></tr>";
        }
        html += "</table>";
        return page(req, "Suppliers", html);
    }

    //Saves a new supplier, or shows the form again if a required field is missing.
    private Response addSubmit(Request req) {
        Supplier supplier = new Supplier(req.form("name"), req.form("phone"), req.form("email"), req.form("location"));
        if (supplier.addSupplier(db)) {
            return Response.redirect("/owner/suppliers");
        }
        return supplierForm(req, "Add supplier", "/owner/suppliers/add", supplier, MISSING_FIELDS);
    }

    //Saves the edited details, or shows the form again if a required field is missing.
    private Response editSubmit(Request req, Supplier supplier) {
        if (supplier.editSupplierInfo(db, req.form("name"), req.form("phone"), req.form("email"), req.form("location"))) {
            return Response.redirect("/owner/suppliers");
        }
        //Shows the form again with what was typed, so the owner can fix it
        Supplier typed = new Supplier(supplier.getId(), req.form("name"), req.form("phone"), req.form("email"), req.form("location"), true);
        return supplierForm(req, "Edit supplier", "/owner/suppliers/" + supplier.getId() + "/edit", typed, MISSING_FIELDS);
    }

    //Form used for both adding and editing a supplier. Cancel goes back without saving.
    private Response supplierForm(Request req, String title, String action, Supplier s, String error) {
        String html = "<h1>" + title + "</h1>";
        if (!error.isEmpty()) {
            html += "<p class=\"error\">" + error + "</p>";
        }
        html += "<form class=\"form\" method=\"post\" action=\"" + action + "\">"
                + Layout.csrfField(req.session())
                + "<label>Supplier name *<input name=\"name\" value=\"" + Html.esc(s.getName()) + "\" required></label>"
                + "<label>Phone number *<input name=\"phone\" value=\"" + Html.esc(s.getPhone()) + "\" required></label>"
                + "<label>Email *<input name=\"email\" type=\"email\" value=\"" + Html.esc(s.getEmail()) + "\" required></label>"
                + "<label>Location (optional)<input name=\"location\" value=\"" + Html.esc(s.getLocation()) + "\"></label>"
                + "<button type=\"submit\" class=\"button\">Save</button> "
                + "<a href=\"/owner/suppliers\">Cancel</a></form>";
        return page(req, title, html);
    }

    private static final String MISSING_FIELDS = "Please enter the supplier name, phone number and email.";

    //Asks the owner to confirm before removing a supplier.
    private Response removeConfirm(Request req, Supplier supplier) {
        if (!supplier.canRemove(db)) {
            return message(req, "Cannot remove supplier", CANNOT_REMOVE);
        }
        String html = "<h1>Remove supplier</h1>"
                + "<p>Remove " + Html.esc(supplier.getName()) + " (" + supplier.getSupplierID() + ")? "
                + "Old restock orders will still show this supplier.</p>"
                + "<form method=\"post\" action=\"/owner/suppliers/" + supplier.getId() + "/remove\">"
                + Layout.csrfField(req.session())
                + "<button type=\"submit\" class=\"button\">Yes, remove</button> "
                + "<a href=\"/owner/suppliers\">Cancel</a></form>";
        return page(req, "Remove supplier", html);
    }

    private Response removeSubmit(Request req, Supplier supplier) {
        if (supplier.removeSupplier(db)) {
            return Response.redirect("/owner/suppliers");
        }
        return message(req, "Cannot remove supplier", CANNOT_REMOVE);
    }

    private static final String CANNOT_REMOVE =
            "This supplier is the only supplier for at least one product. Link another supplier to that product first.";

    //Page that shows a supplier's products, and a list to link another product.
    private Response productsPage(Request req, Supplier supplier) {
        String html = "<h1>Products from " + Html.esc(supplier.getName()) + "</h1>"
                + "<p><a href=\"/owner/suppliers\">Back to suppliers</a></p>";

        ArrayList<Product> products = supplier.getProducts(db);
        if (products.isEmpty()) {
            html += "<p>No products are linked to this supplier yet.</p>";
        } else {
            html += "<table class=\"table\"><tr><th>ID</th><th>Product</th><th>In stock</th><th>All suppliers</th></tr>";
            for (int i = 0; i < products.size(); i++) {
                Product p = products.get(i);
                //Every supplier of this product, to show a product can have more than one
                String names = "";
                ArrayList<Supplier> others = Supplier.getSuppliersForProduct(db, p);
                for (int j = 0; j < others.size(); j++) {
                    if (j > 0) {
                        names += ", ";
                    }
                    names += Html.esc(others.get(j).getName());
                }
                html += "<tr><td>" + p.displayId() + "</td><td>" + Html.esc(p.getName()) + "</td>"
                        + "<td>" + p.getStockQuantity() + "</td><td>" + names + "</td></tr>";
            }
            html += "</table>";
        }

        ArrayList<Product> unlinked = supplier.getUnlinkedProducts(db);
        if (!unlinked.isEmpty()) {
            html += "<h2>Link a product</h2>"
                    + "<form method=\"post\" action=\"/owner/suppliers/" + supplier.getId() + "/products\">"
                    + Layout.csrfField(req.session()) + "<select name=\"productId\">";
            for (int i = 0; i < unlinked.size(); i++) {
                Product p = unlinked.get(i);
                html += "<option value=\"" + p.getProductId() + "\">" + p.displayId() + " " + Html.esc(p.getName()) + "</option>";
            }
            html += "</select> <button type=\"submit\" class=\"button\">Link</button></form>";
        }
        return page(req, "Supplier products", html);
    }

    private Response linkSubmit(Request req, Supplier supplier) {
        Product product = Product.findActiveById(db, toNumber(req.form("productId")));
        if (product == null || !supplier.linkProduct(db, product)) {
            return message(req, "Could not link product", "Please choose a product from the list.");
        }
        return Response.redirect("/owner/suppliers/" + supplier.getId() + "/products");
    }

    //Form for a new restock order: one quantity box for each product this supplier supplies.
    private Response restockForm(Request req, Supplier supplier, String error) {
        String html = "<h1>New restock order: " + Html.esc(supplier.getName()) + "</h1>";
        ArrayList<Product> products = supplier.getProducts(db);
        if (products.isEmpty()) {
            html += "<p>Link a product to this supplier first.</p>";
            return page(req, "New restock order", html);
        }
        if (!error.isEmpty()) {
            html += "<p class=\"error\">" + error + "</p>";
        }
        html += "<p>Enter a quantity for each product you are ordering.</p>"
                + "<form class=\"form\" method=\"post\" action=\"/owner/suppliers/" + supplier.getId() + "/restock\">"
                + Layout.csrfField(req.session());
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);

            //Keeps what the owner typed if the form is shown again with an error
            String typed = req.form("qty" + p.getProductId());
            if (typed == null) {
                typed = "";
            }
            html += "<label>" + p.displayId() + " " + Html.esc(p.getName()) + " (in stock: " + p.getStockQuantity() + ")"
                    + "<input name=\"qty" + p.getProductId() + "\" type=\"number\" min=\"1\" placeholder=\"Leave blank to skip\""
                    + " value=\"" + Html.esc(typed) + "\"></label>";
        }
        html += "<button type=\"submit\" class=\"button\">Save restock order</button> "
                + "<a href=\"/owner/suppliers\">Cancel</a></form>";
        return page(req, "New restock order", html);
    }

    //Saves the restock order, or shows the form again if a quantity is below 1.
    private Response restockSubmit(Request req, Supplier supplier) {
        RestockOrder order = new RestockOrder(supplier.getId());
        ArrayList<Product> products = supplier.getProducts(db);
        for (int i = 0; i < products.size(); i++) {
            String typed = req.form("qty" + products.get(i).getProductId());
            if (typed != null && !typed.trim().isEmpty()) {
                order.addProduct((int) products.get(i).getProductId(), toNumber(typed));
            }
        }
        if (order.getProductsOrdered().isEmpty()) {
            return restockForm(req, supplier, "Enter a quantity for at least one product.");
        }
        if (!order.createRestockOrder(db)) {
            return restockForm(req, supplier, "Each quantity must be a whole number of at least 1." 
                    + "Leave products you are not ordering blank.");
        }
        return Response.redirect("/owner/restock-orders");
    }

    //Page that lists restock orders from newest to oldest, with a Mark received button.
    private Response restockHistory(Request req) {
        String html = "<h1>Restock orders</h1><p><a href=\"/owner/suppliers\">Back to suppliers</a></p>";
        ArrayList<RestockOrder> orders = RestockOrder.findAll(db);
        if (orders.isEmpty()) {
            html += "<p>No restock orders yet.</p>";
            return page(req, "Restock orders", html);
        }
        html += "<table class=\"table\"><tr><th>Restock ID</th><th>Supplier</th><th>Date ordered</th>"
                + "<th>Products</th><th>Status</th><th></th></tr>";
        for (int i = 0; i < orders.size(); i++) {
            RestockOrder o = orders.get(i);
            String products = "";
            for (int j = 0; j < o.getProductsOrdered().size(); j++) {
                products += "P" + String.format("%03d", o.getProductsOrdered().get(j)) + " x " + o.getQuantitiesOrdered().get(j) + "<br>";
            }
            String status = "Ordered";
            String button = "<form method=\"post\" action=\"/owner/restock-orders/" + o.getId() + "/received\">"
                    + Layout.csrfField(req.session()) + "<button type=\"submit\" class=\"button\">Mark received</button></form>"
                    + "<a href=\"/owner/restock-orders/" + o.getId() + "/cancel\">Cancel order</a>";
            if (o.isReceived()) {
                status = "Received";
                button = "";
            }
            html += "<tr><td>" + o.getOrderID() + "</td><td>" + Html.esc(o.getSupplierName()) + "</td>"
                    + "<td>" + o.getDateOrdered() + "</td><td>" + products + "</td>"
                    + "<td>" + status + "</td><td>" + button + "</td></tr>";
        }
        html += "</table>";
        return page(req, "Restock orders", html);
    }

    private Response receivedSubmit(Request req) {
        RestockOrder order = RestockOrder.findById(db, getId(req));
        if (order != null && order.markAsReceived(db)) {
            return Response.redirect("/owner/restock-orders");
        }
        return message(req, "Already received", "This restock order has already been marked as received.");
    }
    
        //Asks the owner to confirm before cancelling a restock order.
    private Response cancelConfirm(Request req) {
        RestockOrder order = RestockOrder.findById(db, getId(req));
        if (order == null || order.isReceived()) {
            return message(req, "Cannot cancel", "Only restock orders that have not been received can be cancelled.");
        }
        String html = "<h1>Cancel restock order</h1>"
                + "<p>Cancel " + order.getOrderID() + " from " + Html.esc(order.getSupplierName()) + "? "
                + "It will be removed from the restock history.</p>"
                + "<form method=\"post\" action=\"/owner/restock-orders/" + order.getId() + "/cancel\">"
                + Layout.csrfField(req.session())
                + "<button type=\"submit\" class=\"button\">Yes, cancel order</button> "
                + "<a href=\"/owner/restock-orders\">Go back</a></form>";
        return page(req, "Cancel restock order", html);
    }

    private Response cancelSubmit(Request req) {
        RestockOrder order = RestockOrder.findById(db, getId(req));
        if (order != null && order.cancelOrder(db)) {
            return Response.redirect("/owner/restock-orders");
        }
        return message(req, "Cannot cancel", "Only restock orders that have not been received can be cancelled.");
    }

    //Puts the page inside the website's layout (header and menu).
    private Response page(Request req, String title, String html) {
        return Response.html(Layout.page(req.session(), title, html));
    }

    //A page with just a title, a message and a link back.
    private Response message(Request req, String title, String text) {
        return page(req, title, "<h1>" + title + "</h1><p class=\"error\">" + text + "</p>"
                + "<p><a href=\"/owner/suppliers\">Back to suppliers</a></p>");
    }

    //The number in the web address, e.g. 3 in /owner/suppliers/3/edit
    private int getId(Request req) {
        return toNumber(req.pathParam("id"));
    }

    //Turns typed text into a whole number. Returns -1 if it is not a whole number.
    private int toNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return -1;
        }
    }
}
