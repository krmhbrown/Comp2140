package peakfinds.services;

import java.util.ArrayList;
import java.util.List;
import peakfinds.database.Database;
import peakfinds.models.Product;

/**
 * SDS class "Storage" (Feature 5). Answers stock questions about the whole catalogue.
 * (Product holds one bag's stock; Storage looks across all bags.)
 */
public class Storage {
    private final Database db;

    public Storage(Database db) {
        this.db = db;
    }

    /** True if the product exists and has at least `quantity` in stock. */
    public boolean checkAvailability(long productId, int quantity) {
        Product p = Product.findActiveById(db, productId);
        return p != null && p.checkAvailability(quantity);
    }

    /** Active products at or below their reorder level. */
    public List<Product> lowStockProducts() {
        List<Product> low = new ArrayList<>();
        for (Product p : Product.findActive(db)) {
            if (p.checkLowStock()) {
                low.add(p);
            }
        }
        return low;
    }

    /** Active products with no stock. */
    public List<Product> outOfStockProducts() {
        List<Product> out = new ArrayList<>();
        for (Product p : Product.findActive(db)) {
            if (p.isOutOfStock()) {
                out.add(p);
            }
        }
        return out;
    }
}
