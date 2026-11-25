//Author: Karimah Brown
//Date of Creation: 23/11/2026
//Date of last modification: 24/11/2026



package peakfinds;

import java.io.File;
import java.util.ArrayList;
import peakfinds.database.Database;
import peakfinds.database.DatabaseInitializer;
import peakfinds.database.Rows;
import peakfinds.models.Product;
import peakfinds.models.RestockOrder;
import peakfinds.models.Supplier;

public class SupplierTests {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean result) {
        if (result) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name); }
    }

    public static void main(String[] args) throws Exception {
        //New test database with the sample data (suppliers S001-S002, products P001-P012)
        File dbFile = File.createTempFile("supplier-test", ".db");
        Database db = new Database("jdbc:sqlite:" + dbFile.getPath());
        DatabaseInitializer.initialise(db, dbFile.getPath());

        System.out.println("=== Feature 03: Supplier Management ===");

        // TC-3.1: a supplier with all fields is saved and appears in the list
        Supplier wayne = new Supplier("Wayne's Distributors", "876-898-2710", "sales@waynesdistributors.com", "Kingston");
        check("TC-3.1 supplier with all fields is saved", wayne.addSupplier(db));
        check("TC-3.1 supplier appears in the list", inList(Supplier.findAll(db), wayne.getId()));
        System.out.println("   TC-3.1 " + wayne);

        // TC-3.1 (location optional): a supplier with no location is still saved
        Supplier online = new Supplier("Online Bag Co", "876-555-0177", "hello@onlinebag.test", "");
        check("TC-3.1 supplier with no location is saved", online.addSupplier(db));

        // TC-3.2: two suppliers get different IDs
        Supplier trish = new Supplier("Trish Imports", "876-555-0142", "trishimports@gmail.com", "Portmore");
        trish.addSupplier(db);
        check("TC-3.2 IDs carry on after the sample suppliers (S003)", wayne.getSupplierID().equals("S003"));
        check("TC-3.2 two suppliers get different IDs", !wayne.getSupplierID().equals(trish.getSupplierID()));
        System.out.println("   TC-3.2 IDs: " + wayne.getSupplierID() + ", " + trish.getSupplierID());

        // TC-3.3: a missing required field is refused and nothing is saved
        int before = Supplier.findAll(db).size();
        check("TC-3.3 case 1 missing name is refused",
                !new Supplier("", "876-555-0177", "info@bagworld.com", "Spanish Town").addSupplier(db));
        check("TC-3.3 case 2 missing phone is refused",
                !new Supplier("Bag World", "", "info@bagworld.com", "Spanish Town").addSupplier(db));
        check("TC-3.3 case 3 missing email is refused",
                !new Supplier("Bag World", "876-555-0177", "", "Spanish Town").addSupplier(db));
        check("TC-3.3 nothing was saved", Supplier.findAll(db).size() == before);

        // TC-3.4: editing the email is saved
        wayne.editSupplierInfo(db, "Wayne's Distributors", "876-898-2710", "orders@waynesdistributors.com", "Kingston");
        check("TC-3.4 new email is saved",
                Supplier.findById(db, wayne.getId()).getEmail().equals("orders@waynesdistributors.com"));

        // TC-3.6: an edit that is not completed (phone cleared) changes nothing
        check("TC-3.6 incomplete edit is refused", !wayne.editSupplierInfo(db, "New Name", "", "a@b.com", "Kingston"));
        check("TC-3.6 supplier unchanged", Supplier.findById(db, wayne.getId()).getName().equals("Wayne's Distributors"));

        // TC-3.5: a removed supplier is no longer in the list
        check("TC-3.5 supplier is removed", trish.removeSupplier(db));
        check("TC-3.5 removed supplier is not in the list", !inList(Supplier.findAll(db), trish.getId()));

        System.out.println();
        System.out.println("=== Feature 04: Supplier Product Management ===");
        //Sample data: S001 supplies P001, P003, P005, P007, P009, P011; S002 supplies the other 6
        Supplier s001 = Supplier.findById(db, 1);
        Supplier s002 = Supplier.findById(db, 2);

        // TC-4.1: one product linked to two suppliers
        wayne.linkProduct(db, Product.findActiveById(db, 1));
        check("TC-4.1 P001 has 2 suppliers",
                Supplier.getSuppliersForProduct(db, Product.findActiveById(db, 1)).size() == 2);
        wayne.linkProduct(db, Product.findActiveById(db, 1)); // linking again should not add it twice
        check("TC-4.1 linking twice does not duplicate",
                Supplier.getSuppliersForProduct(db, Product.findActiveById(db, 1)).size() == 2);

        // TC-4.2: a supplier's product list shows its own products only
        check("TC-4.2 S001 shows its 6 products", s001.getProducts(db).size() == 6);
        check("TC-4.2 Wayne shows only P001", wayne.getProducts(db).size() == 1
                && wayne.getProducts(db).get(0).getProductId() == 1);

        // TC-4.4 / 4.5 (data for the low-stock alert): only P003's own supplier is returned
        ArrayList<Supplier> forP003 = Supplier.getSuppliersForProduct(db, Product.findActiveById(db, 3));
        check("TC-4.4 P003's supplier has a name and phone", forP003.size() == 1
                && forP003.get(0).getName().equals("Island Bags Wholesale") && forP003.get(0).getPhone() != null);
        check("TC-4.5 suppliers of other products are not included", !inList(forP003, wayne.getId()));

        // AC-1: a product's only supplier cannot be removed
        check("AC-1 removing S001 is refused (only supplier of P003)", !s001.removeSupplier(db));

        // TC-4.6: a restock order is saved with products, quantities, date and status Ordered
        RestockOrder first = new RestockOrder(1);
        first.addProduct(1, 15);
        first.addProduct(3, 5);
        check("TC-4.6 restock order is saved", first.createRestockOrder(db));
        RestockOrder saved = RestockOrder.findById(db, first.getId());
        check("TC-4.6 order has 2 products, a date and status Ordered", saved.getProductsOrdered().size() == 2
                && saved.getDateOrdered() != null && saved.getStatus().equals("Ordered"));
        check("TC-4.6 restock ID is in the format RST0001", saved.getOrderID().equals("RST0001"));

        // TC-4.8: a quantity of 0 is refused and nothing is saved
        int historyBefore = RestockOrder.findAll(db).size();
        RestockOrder zero = new RestockOrder(1);
        zero.addProduct(1, 0);
        check("TC-4.8 quantity 0 is refused", !zero.createRestockOrder(db));
        check("TC-4.8 history is unchanged", RestockOrder.findAll(db).size() == historyBefore);

        // TC-4.7: five restock orders are listed from newest to oldest
        int lastId = 0;
        for (int i = 1; i <= 5; i++) {
            RestockOrder o = new RestockOrder(wayne.getId());
            o.addProduct(1, i);
            o.createRestockOrder(db);
            lastId = o.getId();
        }
        check("TC-4.7 newest order is first", RestockOrder.findAll(db).get(0).getId() == lastId);

        // TC-4.9: stock 2 + 15 received = 17
        db.update("UPDATE products SET stock_quantity = 2 WHERE product_id = 1");
        RestockOrder rst = new RestockOrder(1);
        rst.addProduct(1, 15);
        rst.createRestockOrder(db);
        check("TC-4.9 order is marked received", rst.markAsReceived(db));
        check("TC-4.9 stock went from 2 to 17", stockOf(db, 1) == 17);

        // TC-4.10: P012 at 0 is back in stock after its restock is received
        check("TC-4.10 P012 starts Out of Stock", Product.findActiveById(db, 12).isOutOfStock());
        RestockOrder denim = new RestockOrder(2);
        denim.addProduct(12, 3);
        denim.createRestockOrder(db);
        denim.markAsReceived(db);
        check("TC-4.10 P012 is no longer Out of Stock", !Product.findActiveById(db, 12).isOutOfStock()
                && stockOf(db, 12) == 3);

        // TC-4.11: marking the same order received twice is refused and stock does not change
        check("TC-4.11 second time is refused (same order)", !rst.markAsReceived(db));
        check("TC-4.11 second time is refused (order loaded again)", !RestockOrder.findById(db, rst.getId()).markAsReceived(db));
        check("TC-4.11 stock stays at 17", stockOf(db, 1) == 17);

        // TC-3.5 (history kept): a removed supplier still shows on its old restock orders
        Supplier temp = new Supplier("Short Term Supplier", "876-555-0188", "temp@supplier.test", "");
        temp.addSupplier(db);
        temp.linkProduct(db, Product.findActiveById(db, 5)); // P005 also has S001, so removal is allowed
        RestockOrder past = new RestockOrder(temp.getId());
        past.addProduct(5, 2);
        past.createRestockOrder(db);
        check("TC-3.5 supplier with a shared product can be removed", temp.removeSupplier(db));
        check("TC-3.5 old restock order still shows the supplier",
                RestockOrder.findById(db, past.getId()).getSupplierName().equals("Short Term Supplier"));
        check("TC-3.5 removed supplier cannot be linked to products",
                !Supplier.findById(db, temp.getId()).linkProduct(db, Product.findActiveById(db, 2)));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        dbFile.delete();
    }

    //Checks if a supplier is in the list
    private static boolean inList(ArrayList<Supplier> list, int id) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == id) {
                return true;
            }
        }
        return false;
    }

    //Gets the stock of a product from the database
    private static int stockOf(Database db, int productId) {
        return Rows.intOf(db.query("SELECT stock_quantity FROM products WHERE product_id = ?", productId).get(0),
                "stock_quantity");
    }
}