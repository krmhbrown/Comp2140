package peakfinds.models;

import java.nio.file.Files;
import java.nio.file.Path;
import peakfinds.database.Database;
import peakfinds.services.Storage;

/** Plain Java checks for Feature 5 (no JUnit). Run: mvn test-compile exec:java -Dexec.mainClass=peakfinds.models.ProductTest -Dexec.classpathScope=test */
public class ProductTest {
    private static int passed = 0, failed = 0;

    private static void check(String label, boolean ok) {
        System.out.println((ok ? "PASS  " : "FAIL  ") + label);
        if (ok) passed++; else failed++;
    }

    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile("peakfinds-test", ".db");
        Database db = new Database("jdbc:sqlite:" + file);
        check("database connects", db.connect());
        db.executeScript(new String(ProductTest.class.getResourceAsStream("/database/schema.sql").readAllBytes()));
        db.executeScript(new String(ProductTest.class.getResourceAsStream("/database/seed.sql").readAllBytes()));

        check("catalogue has 12 products", Product.findActive(db).size() == 12);
        Product first = Product.findActiveById(db, 1);
        check("first product id displays as P001", first.displayId().equals("P001"));
        check("first product has an image", first.getImages().size() == 1);

        Product p = new Product("Test Bag", "desc", "Red", "Tote Bag", 5000, 3, 1);
        p.addImage("images/test.jpeg");
        p.addSupplierId(1);
        long id = p.addProduct(db);
        check("addProduct returns a new id (13)", id == 13);
        check("new product shows as P013", Product.findActiveById(db, id).displayId().equals("P013"));
        check("supplier saved", Product.findActiveById(db, id).getSupplierIds().contains(1L));

        Product bad = new Product("", "", "", "Tote Bag", 5000, 1, 1);
        check("missing name is rejected", !bad.hasAllRequiredFields());

        Product t = Product.findActiveById(db, id);
        t.updateStock(db, 5);
        check("raising stock does not alert", db.query("SELECT * FROM notifications").isEmpty());
        t.updateStock(db, 1);
        check("lowering stock to reorder level creates ONE alert", db.query("SELECT * FROM notifications WHERE product_id = " + id).size() == 1);
        String alertText = String.valueOf(db.query("SELECT message FROM notifications WHERE product_id = " + id).get(0).get("message"));
        check("alert names the supplier", alertText.contains("Island Bags Wholesale"));
        check("alert shows the supplier phone number", alertText.contains("876-555-0301"));
        check("alert leaves out suppliers not linked to the bag", !alertText.contains("Caribbean Fashion Imports"));
        t.updateStock(db, 0);
        check("second drop does not duplicate unread alert", db.query("SELECT * FROM notifications WHERE product_id = " + id).size() == 1);
        check("stock 0 shows out of stock", Product.findActiveById(db, id).isOutOfStock());
        boolean threw = false;
        try { t.updateStock(db, -1); } catch (IllegalArgumentException e) { threw = true; }
        check("negative stock is rejected", threw);

        t.setName("Edited Bag");
        t.editProduct(db);
        check("editProduct saves the new name", Product.findActiveById(db, id).getName().equals("Edited Bag"));

        Storage storage = new Storage(db);
        check("checkAvailability false when out of stock", !storage.checkAvailability(id, 1));
        check("checkAvailability true when enough stock", storage.checkAvailability(3, 2));
        check("checkAvailability false when asking for too many", !storage.checkAvailability(3, 3));
        check("low-stock list includes the test bag", storage.lowStockProducts().stream().anyMatch(x -> x.getProductId() == id));

        t.removeProduct(db);
        check("removed product leaves the catalogue", Product.findActiveById(db, id) == null);
        check("removed product row is kept (history)", db.query("SELECT * FROM products WHERE product_id = " + id).size() == 1);

        Product many = Product.findActiveById(db, 2);
        boolean imgThrew = false;
        try { for (int i = 0; i < 6; i++) many.addImage("images/x" + i + ".jpeg"); } catch (IllegalStateException e) { imgThrew = true; }
        check("6th image is rejected", imgThrew);

        System.out.println("\n" + passed + " passed, " + failed + " failed");
        Files.deleteIfExists(file);
        System.exit(failed == 0 ? 0 : 1);
    }
}
