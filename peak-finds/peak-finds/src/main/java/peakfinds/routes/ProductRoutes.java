package peakfinds.routes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import peakfinds.AppContext;
import peakfinds.Constants.Action;
import peakfinds.auth.Session;
import peakfinds.auth.ValidationException;
import peakfinds.auth.Validator;
import peakfinds.database.Rows;
import peakfinds.models.Product;
import peakfinds.services.Storage;
import peakfinds.ui.Layout;
import peakfinds.web.BadRequestException;
import peakfinds.web.Html;
import peakfinds.web.Request;
import peakfinds.web.Response;

/** Feature 5 - Product Management: public catalogue + details, and the owner's product pages. */
public final class ProductRoutes {
    private final AppContext ctx;
    private final Storage storage;

    private ProductRoutes(AppContext ctx) {
        this.ctx = ctx;
        this.storage = new Storage(ctx.db());
    }

    public static void register(AppContext ctx) {
        ProductRoutes r = new ProductRoutes(ctx);
        var router = ctx.router();
        router.get("/catalogue", r::catalogue);
        router.get("/products/{id}", r::details);
        router.get("/owner/products", Action.MANAGE_PRODUCTS, r::ownerList);
        router.get("/owner/products/new", Action.MANAGE_PRODUCTS, req -> r.form(req, null));
        router.post("/owner/products/new", Action.MANAGE_PRODUCTS, r::create);
        router.get("/owner/products/{id}/edit", Action.MANAGE_PRODUCTS, r::editForm);
        router.post("/owner/products/{id}/edit", Action.MANAGE_PRODUCTS, r::save);
        router.post("/owner/products/{id}/stock", Action.MANAGE_PRODUCTS, r::stock);
        router.get("/owner/products/{id}/remove", Action.MANAGE_PRODUCTS, r::confirmRemove);
        router.post("/owner/products/{id}/remove", Action.MANAGE_PRODUCTS, r::remove);
        router.get("/owner/low-stock", Action.VIEW_LOW_STOCK_ALERTS, r::lowStock);
    }

    // ---------------- public pages ----------------

    private Response catalogue(Request req) {
        StringBuilder cards = new StringBuilder("<div class=\"card-grid\">");
        List<Product> products = Product.findActive(ctx.db());
        for (Product p : products) {
            cards.append("<a class=\"card\" href=\"/products/").append(p.getProductId()).append("\">")
                    .append(image(p)).append("<h2>").append(Html.esc(p.getName())).append("</h2><p>")
                    .append(price(p)).append("</p>")
                    .append(p.isOutOfStock() ? "<p><strong>Out of Stock</strong></p>" : "")
                    .append("</a>");
        }
        cards.append("</div>");
        String body = "<h1>Catalogue</h1>" + (products.isEmpty() ? "<p>No bags are available right now.</p>" : cards);
        return Response.html(Layout.page(req.session(), "Catalogue", body));
    }

    
private Response details(Request req) {
    Product p = Product.findActiveById(ctx.db(), req.pathLong("id"));

    if (p == null) {
        return peakfinds.web.Router.errorPage(
                req.session(),
                404,
                "Product not found",
                "That bag is not available."
        );
    }

    StringBuilder html = new StringBuilder(
            "<p><a href=\"/catalogue\">&larr; Back to catalogue</a></p>"
    );

    html.append("<h1>")
            .append(Html.esc(p.getName()))
            .append("</h1>");

    // Display the product images.
    for (String path : p.getImages()) {
        html.append("<img class=\"product-thumb\" src=\"/static/")
                .append(Html.esc(path))
                .append("\" alt=\"")
                .append(Html.esc(p.getName()))
                .append("\">");
    }

    // Display the product information.
    html.append("<p>")
            .append(Html.esc(nz(p.getDescription())))
            .append("</p>")
            .append("<p><strong>Colour:</strong> ")
            .append(Html.esc(nz(p.getColour())))
            .append("<br>")
            .append("<strong>Category:</strong> ")
            .append(Html.esc(p.getCategory()))
            .append("<br>")
            .append("<strong>Price:</strong> ")
            .append(price(p))
            .append("<br>")
            .append("<strong>Availability:</strong> ")
            .append(p.isOutOfStock()
                    ? "Out of Stock"
                    : "In Stock (" + p.getStockQuantity() + " available)")
            .append("</p>");

    // Shopping Cart integration.
    if (p.isOutOfStock()) {

        html.append("<button class=\"button\" disabled>")
                .append("Out of Stock")
                .append("</button>");

    } else if (req.session() != null
            && req.session().customerId() != null) {

        html.append("<form method=\"post\" action=\"/cart/add\">")
                .append(Layout.csrfField(req.session()))
                .append("<input type=\"hidden\" name=\"product_id\" value=\"")
                .append(p.getProductId())
                .append("\">")
                .append("<p><label for=\"product-quantity\">Quantity</label></p>")
                .append("<input id=\"product-quantity\" type=\"number\" ")
                .append("name=\"quantity\" value=\"1\" min=\"1\" max=\"")
                .append(p.getStockQuantity())
                .append("\" required>")
                .append("<p><button type=\"submit\" class=\"button\">")
                .append("Add to Cart")
                .append("</button></p>")
                .append("</form>");

    } else {

        html.append("<p><a class=\"button\" href=\"/login\">")
                .append("Log in to Add to Cart")
                .append("</a></p>");
    }

    return Response.html(
            Layout.page(req.session(), p.getName(), html.toString())
    );
}


    // ---------------- owner pages ----------------

    private Response ownerList(Request req) {
        Session s = req.session();
        StringBuilder html = new StringBuilder("<h1>Products</h1><p><a class=\"button\" href=\"/owner/products/new\">Add a product</a></p>");
        html.append("<table><tr><th>ID</th><th>Name</th><th>Price</th><th>Stock</th><th>Reorder at</th><th>Suppliers</th><th></th></tr>");
        for (Product p : Product.findActive(ctx.db())) {
            html.append("<tr><td>").append(p.displayId()).append("</td><td>").append(Html.esc(p.getName()))
                    .append("</td><td>").append(price(p)).append("</td><td>")
                    .append("<form method=\"post\" action=\"/owner/products/").append(p.getProductId()).append("/stock\">")
                    .append(Layout.csrfField(s))
                    .append("<input type=\"number\" name=\"stock\" min=\"0\" value=\"").append(p.getStockQuantity())
                    .append("\" class=\"stock-input\"> <button class=\"button\" type=\"submit\">Update</button></form>")
                    .append(p.isOutOfStock() ? " <strong>Out of Stock</strong>" : "")
                    .append("</td><td>").append(p.getReorderLevel()).append("</td><td>")
                    .append(Html.esc(supplierText(p))).append("</td><td>")
                    .append("<a href=\"/owner/products/").append(p.getProductId()).append("/edit\">Edit</a> ")
                    .append("<a class=\"danger\" href=\"/owner/products/").append(p.getProductId()).append("/remove\">Remove</a>")
                    .append("</td></tr>");
        }
        html.append("</table>");
        return Response.html(Layout.page(s, "Products", html.toString()));
    }

    private Response editForm(Request req) {
        Product p = Product.findActiveById(ctx.db(), req.pathLong("id"));
        if (p == null) {
            return peakfinds.web.Router.errorPage(req.session(), 404, "Product not found", "That product does not exist.");
        }
        return form(req, p);
    }

    private Response form(Request req, Product p) {
        boolean edit = p != null;
        String action = edit ? "/owner/products/" + p.getProductId() + "/edit" : "/owner/products/new";
        StringBuilder f = new StringBuilder("<h1>").append(edit ? "Edit " + Html.esc(p.getName()) : "Add a product").append("</h1>");
        f.append("<form method=\"post\" action=\"").append(action).append("\">").append(Layout.csrfField(req.session()));
        f.append(field("Name", "name", edit ? p.getName() : "", "text"));
        f.append(field("Description", "description", edit ? nz(p.getDescription()) : "", "text"));
        f.append(field("Colour", "colour", edit ? nz(p.getColour()) : "", "text"));
        f.append(field("Category", "category", edit ? p.getCategory() : "", "text"));
        f.append(field("Price (JMD)", "price", edit ? plain(p.getPrice()) : "", "number\" step=\"0.01\" min=\"0"));
        if (!edit) {
            f.append(field("Starting stock", "stock", "0", "number\" min=\"0"));
        }
        f.append(field("Reorder level", "reorder", edit ? String.valueOf(p.getReorderLevel()) : "1", "number\" min=\"0"));
        f.append("<label>Photo paths (required, one per line, up to 5, e.g. images/P001.jpeg)<br><textarea class=\"full-width\" name=\"images\" rows=\"3\">")
                .append(Html.esc(edit ? String.join("\n", p.getImages()) : "")).append("</textarea></label>");
        f.append("<p><strong>Suppliers</strong></p>");
        for (Map<String, Object> s : ctx.db().query("SELECT supplier_id, name FROM suppliers WHERE is_active = 1 ORDER BY supplier_id")) {
            long id = Rows.longOf(s, "supplier_id");
            boolean on = edit && p.getSupplierIds().contains(id);
            f.append("<label><input type=\"checkbox\" name=\"sup_").append(id).append("\" value=\"1\"")
                    .append(on ? " checked" : "").append("> ").append(Html.esc(Rows.str(s, "name"))).append("</label><br>");
        }
        f.append("<button class=\"button\" type=\"submit\">Save</button> <a href=\"/owner/products\">Cancel</a></form>");
        return Response.html(Layout.page(req.session(), edit ? "Edit product" : "Add product", f.toString()));
    }

    private Response create(Request req) {
        Product p = read(req, new Product());
        p.setStockQuantity(Validator.intInRange(req.form("stock"), "Starting stock", 0, 100000));
        try {
            p.addProduct(ctx.db());
        } catch (RuntimeException e) {
            throw new ValidationException(friendly(e));
        }
        return Response.redirect("/owner/products");
    }

    private Response save(Request req) {
        Product existing = Product.findActiveById(ctx.db(), req.pathLong("id"));
        if (existing == null) {
            throw new BadRequestException("That product does not exist.");
        }
        Product p = read(req, existing);
        try {
            p.editProduct(ctx.db());
        } catch (RuntimeException e) {
            throw new ValidationException(friendly(e));
        }
        return Response.redirect("/owner/products");
    }

    private Response stock(Request req) {
        Product p = Product.findActiveById(ctx.db(), req.pathLong("id"));
        if (p == null) {
            throw new BadRequestException("That product does not exist.");
        }
        p.updateStock(ctx.db(), Validator.intInRange(req.form("stock"), "Stock", 0, 100000));
        return Response.redirect("/owner/products");
    }

    /** Asks "are you sure?" before removing. Cancel simply goes back to the table. */
    private Response confirmRemove(Request req) {
        Product p = Product.findActiveById(ctx.db(), req.pathLong("id"));
        if (p == null) {
            throw new BadRequestException("That product does not exist.");
        }
        String html = "<h1>Remove product?</h1><p>Remove <strong>" + Html.esc(p.getName()) + "</strong> (" + p.displayId()
                + ") from the catalogue? Past orders will keep its name and price.</p>"
                + "<form method=\"post\" action=\"/owner/products/" + p.getProductId() + "/remove\">"
                + Layout.csrfField(req.session())
                + "<button class=\"button\" type=\"submit\">Yes, remove</button> <a href=\"/owner/products\">Cancel</a></form>";
        return Response.html(Layout.page(req.session(), "Remove product", html));
    }

    private Response remove(Request req) {
        Product p = Product.findActiveById(ctx.db(), req.pathLong("id"));
        if (p == null) {
            throw new BadRequestException("That product does not exist.");
        }
        p.removeProduct(ctx.db());
        return Response.redirect("/owner/products");
    }

    private Response lowStock(Request req) {
        StringBuilder html = new StringBuilder("<h1>Low-stock alerts</h1><h2>Products at or below reorder level</h2><ul>");
        List<Product> low = storage.lowStockProducts();
        for (Product p : low) {
            html.append("<li>").append(Html.esc(p.lowStockAlertText(ctx.db()))).append("</li>");
        }
        html.append("</ul>");
        if (low.isEmpty()) {
            html = new StringBuilder("<h1>Low-stock alerts</h1><p>No products are low on stock.</p>");
        }
        return Response.html(Layout.page(req.session(), "Low-stock alerts", html.toString()));
    }

    // ---------------- helpers ----------------

    /** Validates the form fields and copies them into the product (stock is not touched). */
    private Product read(Request req, Product p) {
        String name = Validator.text(req.form("name"), "Name", 120);
        String category = Validator.text(req.form("category"), "Category", 60);
        double price = parsePrice(req.form("price"));
        int reorder = Validator.intInRange(req.form("reorder"), "Reorder level", 0, 100000);
        List<String> paths = new ArrayList<>();
        for (String line : Validator.clean(req.form("images")).split("\\R")) {
            if (!line.isBlank()) {
                String path = line.strip();
                if (path.contains("..") || path.startsWith("/") || path.contains("\\")) {
                    throw new ValidationException("Image paths must look like images/P001.jpeg.");
                }
                paths.add(path);
            }
        }
        if (paths.isEmpty()) {
            throw new ValidationException("A product needs at least one photo. Add an image path such as images/P001.jpeg.");
        }
        if (paths.size() > 5) {
            throw new ValidationException("A product can have at most 5 images.");
        }
        p.setName(name);
        p.setDescription(Validator.clean(req.form("description")));
        p.setColour(Validator.clean(req.form("colour")));
        p.setCategory(category);
        p.setPrice(price);
        p.setReorderLevel(reorder);
        p.getImages().clear();
        paths.forEach(p::addImage);
        p.getSupplierIds().clear();
        for (Map<String, Object> s : ctx.db().query("SELECT supplier_id FROM suppliers WHERE is_active = 1")) {
            long id = Rows.longOf(s, "supplier_id");
            if (req.form("sup_" + id) != null) {
                p.addSupplierId(id);
            }
        }
        return p;
    }

    private static double parsePrice(String text) {
        try {
            double d = Double.parseDouble(Validator.required(text, "Price"));
            if (d < 0 || Double.isNaN(d) || Double.isInfinite(d)) {
                throw new ValidationException("Price must be 0 or more.");
            }
            return d;
        } catch (NumberFormatException e) {
            throw new ValidationException("Price must be a number.");
        }
    }

    private static String friendly(RuntimeException e) {
        String m = String.valueOf(e.getMessage());
        if (m.contains("100 active")) {
            return "The catalogue is limited to 100 active products.";
        }
        if (m.contains("at most 5")) {
            return "A product can have at most 5 images.";
        }
        return "The product could not be saved. Check the details and try again.";
    }

    private static String image(Product p) {
        if (p.getImages().isEmpty()) {
            return "";
        }
        return "<img class=\"product-img\" src=\"/static/" + Html.esc(p.getImages().get(0)) + "\" alt=\"" + Html.esc(p.getName())
                + "\">";
    }

    private static String price(Product p) {
        return "$" + String.format("%,.0f", p.getPrice());
    }

    private static String supplierText(Product p) {
        if (p.getSupplierIds().isEmpty()) {
            return "none";
        }
        List<String> ids = new ArrayList<>();
        for (Long id : p.getSupplierIds()) {
            ids.add(String.format("S%03d", id));
        }
        return String.join(", ", ids);
    }

    private static String plain(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String field(String label, String name, String value, String type) {
        return "<label>" + label + "<br><input type=\"" + type + "\" name=\"" + name + "\" value=\"" + Html.esc(value)
                + "\" class=\"full-width\"></label><br>";
    }
}
