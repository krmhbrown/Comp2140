//Author: Karimah Brown
//Date of Creation: 23/11/2026
//Date of last modification: 24/11/2026

/*SupplierRoutes class that builds the owner's pages for Supplier Management
and Supplier Product Management. Each page is built as HTML text, and the
forms on the pages call SupplierManager and RestockOrderManager to save the
information. */

package peakfinds.routes;

import java.util.ArrayList;
import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.models.ProductSummary;
import peakfinds.models.RestockOrder;
import peakfinds.models.RestockOrderManager;
import peakfinds.models.Supplier;
import peakfinds.models.SupplierManager;
import peakfinds.ui.Layout;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;
import peakfinds.web.Router;

public class SupplierRoutes implements Router.Handler {
    private SupplierManager supplierManager;
    private RestockOrderManager restockManager;

    //Constructor to create instance of the supplier pages.
    public SupplierRoutes(AppContext ctx) {
        this.supplierManager = new SupplierManager(ctx.db());
        this.restockManager = new RestockOrderManager(ctx.db());
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

        //The rest of the pages are for one supplier, e.g. /owner/suppliers/3/edit
        Supplier supplier = supplierManager.getSupplier(getId(req));
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

        ArrayList<Supplier> suppliers = supplierManager.getAllSuppliers();
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
        if (supplierManager.addSupplier(supplier)) {
            return Response.redirect("/owner/suppliers");
        }
        return supplierForm(req, "Add supplier", "/owner/suppliers/add", supplier, MISSING_FIELDS);
    }

    //Saves the edited details, or shows the form again if a required field is missing.
    private Response editSubmit(Request req, Supplier supplier) {
        supplier.editSupplierInfo(req.form("name"), req.form("phone"), req.form("email"), req.form("location"));
        if (supplierManager.editSupplier(supplier)) {
            return Response.redirect("/owner/suppliers");
        }
        return supplierForm(req, "Edit supplier", "/owner/suppliers/" + supplier.getId() + "/edit", supplier, MISSING_FIELDS);
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
        if (!supplierManager.canRemove(supplier.getId())) {
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
        if (supplierManager.removeSupplier(supplier.getId())) {
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

        ArrayList<ProductSummary> products = supplierManager.getProducts(supplier.getId());
        if (products.isEmpty()) {
            html += "<p>No products are linked to this supplier yet.</p>";
        } else {
            html += "<table class=\"table\"><tr><th>ID</th><th>Product</th><th>In stock</th><th>All suppliers</th></tr>";
            for (int i = 0; i < products.size(); i++) {
                ProductSummary p = products.get(i);
                //Every supplier of this product, to show a product can have more than one
                String names = "";
                ArrayList<Supplier> others = supplierManager.getSuppliersForProduct(p.getId());
                for (int j = 0; j < others.size(); j++) {
                    if (j > 0) {
                        names += ", ";
                    }
                    names += Html.esc(others.get(j).getName());
                }
                html += "<tr><td>" + p.getProductID() + "</td><td>" + Html.esc(p.getName()) + "</td>"
                        + "<td>" + p.getStockQuantity() + "</td><td>" + names + "</td></tr>";
            }
            html += "</table>";
        }

        ArrayList<ProductSummary> unlinked = supplierManager.getUnlinkedProducts(supplier.getId());
        if (!unlinked.isEmpty()) {
            html += "<h2>Link a product</h2>"
                    + "<form method=\"post\" action=\"/owner/suppliers/" + supplier.getId() + "/products\">"
                    + Layout.csrfField(req.session()) + "<select name=\"productId\">";
            for (int i = 0; i < unlinked.size(); i++) {
                ProductSummary p = unlinked.get(i);
                html += "<option value=\"" + p.getId() + "\">" + p.getProductID() + " " + Html.esc(p.getName()) + "</option>";
            }
            html += "</select> <button type=\"submit\" class=\"button\">Link</button></form>";
        }
        return page(req, "Supplier products", html);
    }

    private Response linkSubmit(Request req, Supplier supplier) {
        int productId = toNumber(req.form("productId"));
        if (productId < 1 || !supplierManager.linkProduct(supplier.getId(), productId)) {
            return message(req, "Could not link product", "Please choose a product from the list.");
        }
        return Response.redirect("/owner/suppliers/" + supplier.getId() + "/products");
    }

    //Form for a new restock order: one quantity box for each product this supplier supplies.
    private Response restockForm(Request req, Supplier supplier, String error) {
        String html = "<h1>New restock order: " + Html.esc(supplier.getName()) + "</h1>";
        ArrayList<ProductSummary> products = supplierManager.getProducts(supplier.getId());
        if (products.isEmpty()) {
            html += "<p>Link a product to this supplier first.</p>";
            return page(req, "New restock order", html);
        }
        if (!error.isEmpty()) {
            html += "<p class=\"error\">" + error + "</p>";
        }
        html += "<p>Enter a quantity for each product you are ordering. Leave the rest blank.</p>"
                + "<form class=\"form\" method=\"post\" action=\"/owner/suppliers/" + supplier.getId() + "/restock\">"
                + Layout.csrfField(req.session());
        for (int i = 0; i < products.size(); i++) {
            ProductSummary p = products.get(i);
            html += "<label>" + p.getProductID() + " " + Html.esc(p.getName()) + " (in stock: " + p.getStockQuantity() + ")"
                    + "<input name=\"qty" + p.getId() + "\" type=\"number\"></label>";
        }
        html += "<button type=\"submit\" class=\"button\">Save restock order</button> "
                + "<a href=\"/owner/suppliers\">Cancel</a></form>";
        return page(req, "New restock order", html);
    }

    //Saves the restock order, or shows the form again if a quantity is below 1.
    private Response restockSubmit(Request req, Supplier supplier) {
        RestockOrder order = new RestockOrder(supplier.getId());
        ArrayList<ProductSummary> products = supplierManager.getProducts(supplier.getId());
        for (int i = 0; i < products.size(); i++) {
            String typed = req.form("qty" + products.get(i).getId());
            if (typed != null && !typed.trim().isEmpty()) {
                order.addProduct(products.get(i).getId(), toNumber(typed));
            }
        }
        if (order.getProductsOrdered().isEmpty()) {
            return restockForm(req, supplier, "Enter a quantity for at least one product.");
        }
        if (!restockManager.createRestockOrder(order)) {
            return restockForm(req, supplier, "Each quantity must be a whole number of at least 1.");
        }
        return Response.redirect("/owner/restock-orders");
    }

    //Page that lists restock orders from newest to oldest, with a Mark received button.
    private Response restockHistory(Request req) {
        String html = "<h1>Restock orders</h1><p><a href=\"/owner/suppliers\">Back to suppliers</a></p>";
        ArrayList<RestockOrder> orders = restockManager.getAllRestockOrders();
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
                    + Layout.csrfField(req.session()) + "<button type=\"submit\" class=\"button\">Mark received</button></form>";
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
        if (restockManager.markAsReceived(getId(req))) {
            return Response.redirect("/owner/restock-orders");
        }
        return message(req, "Already received", "This restock order has already been marked as received.");
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