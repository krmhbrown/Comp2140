package peakfinds.models;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import peakfinds.Constants;
import peakfinds.database.Database;
import peakfinds.database.Rows;

/**
 * SDS class "Product" (Feature 5 - Product Management).
 * One bag in the catalogue. Data lives in the products, product_images and product_suppliers tables.
 * The database creates the numeric id; it is shown to people as P001, P002, ...
 */
public class Product {
    private long productId;
    private String name;
    private String description;
    private String colour;
    private String category;
    private double price;
    private int stockQuantity;
    private int reorderLevel;
    private boolean active = true;
    private final List<String> images = new ArrayList<>();
    private final List<Long> supplierIds = new ArrayList<>();

    public Product() {}

    public Product(String name, String description, String colour, String category,
                   double price, int stockQuantity, int reorderLevel) {
        this.name = name;
        this.description = description;
        this.colour = colour;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.reorderLevel = reorderLevel;
    }

    // ---------- getters ----------
    public long getProductId() { return productId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getColour() { return colour; }
    public String getCategory() { return category; }
    public double getPrice() { return price; }
    public int getStockQuantity() { return stockQuantity; }
    public int getReorderLevel() { return reorderLevel; }
    public boolean isActive() { return active; }
    public List<String> getImages() { return images; }
    public List<Long> getSupplierIds() { return supplierIds; }

    // ---------- setters (used by the forms) ----------
    public void setProductId(long productId) { this.productId = productId; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setColour(String colour) { this.colour = colour; }
    public void setCategory(String category) { this.category = category; }
    public void setPrice(double price) { this.price = price; }
    public void setReorderLevel(int reorderLevel) { this.reorderLevel = reorderLevel; }

    /** The id as people see it, e.g. P001. */
    public String displayId() { return String.format("P%03d", productId); }

    // ---------- plain logic (no database, easy to unit test) ----------

    /** True when every required field is filled in and sensible. */
    public boolean hasAllRequiredFields() {
        return name != null && !name.isBlank()
                && category != null && !category.isBlank()
                && price >= 0 && stockQuantity >= 0 && reorderLevel >= 0;
    }

    public boolean isOutOfStock() { return stockQuantity == 0; }

    /** True if at least `quantity` units are in stock. */
    public boolean checkAvailability(int quantity) { return quantity > 0 && stockQuantity >= quantity; }

    /** Stock is at or below the reorder level. */
    public boolean checkLowStock() { return stockQuantity <= reorderLevel; }

    /** The text of the low-stock alert for this product. */
    public String lowStockMessage() {
        return "Low stock: " + name + " (" + displayId() + ") has " + stockQuantity
                + " left (reorder level " + reorderLevel + ").";
    }

    /** Adds an image path in memory (maximum 5 per product). */
    public void addImage(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Image path is required.");
        }
        if (images.size() >= Constants.MAX_IMAGES_PER_PRODUCT) {
            throw new IllegalStateException("A product can have at most " + Constants.MAX_IMAGES_PER_PRODUCT + " images.");
        }
        images.add(path.strip());
    }

    public void addSupplierId(long supplierId) {
        if (!supplierIds.contains(supplierId)) {
            supplierIds.add(supplierId);
        }
    }

    /** Sets the quantity in memory. Negative stock is never allowed. */
    public void setStockQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }
        this.stockQuantity = quantity;
    }

    // ---------- database operations ----------

    /** addProduct(): saves a new product (and its images and suppliers). Returns the new id. */
    public long addProduct(Database db) {
        if (!hasAllRequiredFields()) {
            throw new IllegalStateException("Fill in all the required fields.");
        }
        return db.transaction(() -> {
            productId = db.insert("INSERT INTO products (name, description, colour, category, price, stock_quantity, reorder_level)"
                    + " VALUES (?, ?, ?, ?, ?, ?, ?)", name, description, colour, category, price, stockQuantity, reorderLevel);
            saveLinks(db);
            return productId;
        });
    }

    /** editProduct(): saves changes to an existing product, its images and its suppliers. */
    public void editProduct(Database db) {
        if (!hasAllRequiredFields()) {
            throw new IllegalStateException("Fill in all the required fields.");
        }
        db.transaction(() -> {
            db.update("UPDATE products SET name = ?, description = ?, colour = ?, category = ?, price = ?, reorder_level = ?"
                    + " WHERE product_id = ?", name, description, colour, category, price, reorderLevel, productId);
            db.update("DELETE FROM product_images WHERE product_id = ?", productId);
            db.update("DELETE FROM product_suppliers WHERE product_id = ?", productId);
            saveLinks(db);
            return null;
        });
    }

    /** removeProduct(): hides the product from the catalogue. The row stays so past orders keep working. */
    public void removeProduct(Database db) {
        db.update("UPDATE products SET is_active = 0 WHERE product_id = ?", productId);
        active = false;
    }

    /**
     * updateStock(): sets a new stock quantity. If the stock went DOWN and is now at or below the reorder
     * level, a low-stock alert is created (sendLowStockAlert).
     */
    public void updateStock(Database db, int newQuantity) {
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock cannot be negative.");
        }
        int old = stockQuantity;
        db.update("UPDATE products SET stock_quantity = ? WHERE product_id = ?", newQuantity, productId);
        stockQuantity = newQuantity;
        if (newQuantity < old && checkLowStock()) {
            sendLowStockAlert(db);
        }
    }

    /** Who to reorder from: the active suppliers of this product with their phone numbers (uses Supplier). */
    public String supplierContacts(Database db) {
        List<Supplier> suppliers = Supplier.getSuppliersForProduct(db, this);
        if (suppliers.isEmpty()) {
            return "No supplier is linked to this product.";
        }
        List<String> parts = new ArrayList<>();
        for (Supplier s : suppliers) {
            parts.add(s.getName() + " (" + s.getPhone() + ")");
        }
        return "Reorder from: " + String.join("; ", parts) + ".";
    }

    /** The low-stock alert text including the supplier names and phone numbers (SR-4.3). */
    public String lowStockAlertText(Database db) {
        return lowStockMessage() + " " + supplierContacts(db);
    }

    /** sendLowStockAlert(): adds one notification for the owner (not repeated while an unread one exists). */
    public void sendLowStockAlert(Database db) {
        boolean already = db.queryOne("SELECT 1 AS x FROM notifications WHERE kind = 'low_stock' AND product_id = ? AND is_read = 0",
                productId).isPresent();
        if (!already) {
            db.insert("INSERT INTO notifications (kind, message, product_id) VALUES ('low_stock', ?, ?)",
                    lowStockAlertText(db), productId);
        }
    }

    private void saveLinks(Database db) {
        int order = 0;
        for (String path : images) {
            db.insert("INSERT INTO product_images (product_id, image_path, sort_order) VALUES (?, ?, ?)", productId, path, order++);
        }
        for (Long supplierId : supplierIds) {
            db.insert("INSERT INTO product_suppliers (product_id, supplier_id) VALUES (?, ?)", productId, supplierId);
        }
    }

    // ---------- loading ----------

    /** All active products (the catalogue), newest id first not needed: lowest id first. */
    public static List<Product> findActive(Database db) {
        List<Product> list = new ArrayList<>();
        for (Map<String, Object> row : db.query("SELECT * FROM products WHERE is_active = 1 ORDER BY product_id")) {
            list.add(fromRow(db, row));
        }
        return list;
    }

    /** One active product, or null if it does not exist or was removed. */
    public static Product findActiveById(Database db, long id) {
        return db.queryOne("SELECT * FROM products WHERE product_id = ? AND is_active = 1", id)
                .map(row -> fromRow(db, row)).orElse(null);
    }

    private static Product fromRow(Database db, Map<String, Object> row) {
        Product p = new Product();
        p.productId = Rows.longOf(row, "product_id");
        p.name = Rows.str(row, "name");
        p.description = Rows.str(row, "description");
        p.colour = Rows.str(row, "colour");
        p.category = Rows.str(row, "category");
        p.price = Rows.doubleOf(row, "price");
        p.stockQuantity = Rows.intOf(row, "stock_quantity");
        p.reorderLevel = Rows.intOf(row, "reorder_level");
        p.active = Rows.bool(row, "is_active");
        for (Map<String, Object> img : db.query("SELECT image_path FROM product_images WHERE product_id = ? ORDER BY sort_order", p.productId)) {
            p.images.add(Rows.str(img, "image_path"));
        }
        for (Map<String, Object> s : db.query("SELECT supplier_id FROM product_suppliers WHERE product_id = ?", p.productId)) {
            p.supplierIds.add(Rows.longOf(s, "supplier_id"));
        }
        return p;
    }
}
