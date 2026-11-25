//Author: Karimah Brown
//Date of Creation: 22/11/2026
//Date of last modification: 23/11/2026

package supplier;

import java.util.ArrayList;
import product.Product;

public class SupplierTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean result) {
        if (result) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name); }
    }

    //A bag with all of Product's required fields except the supplier (linked separately)
    private static Product makeBag(String name, int quantity) {
        Product p = new Product(name, "Black", "Tote Bag", 8500.0, quantity);
        p.setDescription("A bag");
        p.setReorderLevel(5);
        p.addImage("images/bag.jpg");
        return p;
    }

    public static void main(String[] args) {
        SupplierManager suppliers = new SupplierManager();
        RestockOrderManager restocks = new RestockOrderManager();

        System.out.println("=== Feature 03: Supplier Management ===");

        // TC-3.1: a supplier with all fields is added to the list and given an ID
        Supplier wayne = new Supplier("Wayne's Distributors", "876-898-2710", "sales@waynesdistributors.com", "Kingston");
        check("TC-3.1 supplier with all fields is added", suppliers.addSupplier(wayne));
        check("TC-3.1 supplier appears in the list with an ID", suppliers.getAllSuppliers().contains(wayne));
        System.out.println("   TC-3.1 " + wayne);

        // TC-3.1 (location optional): a supplier with no location is still added
        Supplier online = new Supplier("Online Bag Co", "876-555-0177", "hello@onlinebag.test", "");
        check("TC-3.1 supplier with no location is added", suppliers.addSupplier(online));
        check("TC-3.1 location shows Not provided", online.getLocation().equals("Not provided"));

        // TC-3.2: two suppliers get different IDs
        Supplier trish = new Supplier("Trish Imports", "876-555-0142", "trishimports@gmail.com", "Portmore");
        suppliers.addSupplier(trish);
        check("TC-3.2 IDs are in the format S001", wayne.getSupplierID().equals("S001"));
        check("TC-3.2 two suppliers get different IDs", !wayne.getSupplierID().equals(trish.getSupplierID()));
        System.out.println("   TC-3.2 IDs: " + wayne.getSupplierID() + ", " + trish.getSupplierID());

        // TC-3.3: a missing required field is refused, nothing is added and no ID is given
        int before = suppliers.getAllSuppliers().size();
        Supplier noName = new Supplier("", "876-555-0177", "info@bagworld.com", "Spanish Town");
        Supplier noPhone = new Supplier("Bag World", "", "info@bagworld.com", "Spanish Town");
        Supplier noEmail = new Supplier("Bag World", "876-555-0177", "", "Spanish Town");
        check("TC-3.3 case 1 missing name is refused", !suppliers.addSupplier(noName));
        check("TC-3.3 case 2 missing phone is refused", !suppliers.addSupplier(noPhone));
        check("TC-3.3 case 3 missing email is refused", !suppliers.addSupplier(noEmail));
        check("TC-3.3 nothing was added", suppliers.getAllSuppliers().size() == before);
        check("TC-3.3 no ID was given", noName.getSupplierID() == null);

        // TC-3.4: editing the email is saved and the other details stay the same
        wayne.editSupplierInfo("Wayne's Distributors", "876-898-2710", "orders@waynesdistributors.com", "Kingston");
        check("TC-3.4 new email is saved", wayne.getEmail().equals("orders@waynesdistributors.com"));
        check("TC-3.4 other details unchanged", wayne.getPhone().equals("876-898-2710"));

        // TC-3.6: an edit that is not completed (phone cleared) changes nothing
        check("TC-3.6 incomplete edit is refused", !wayne.editSupplierInfo("New Name", "", "a@b.com", "Kingston"));
        check("TC-3.6 supplier unchanged", wayne.getName().equals("Wayne's Distributors"));

        // TC-3.5: a removed supplier is no longer in the list
        check("TC-3.5 supplier is removed", suppliers.removeSupplier(trish));
        check("TC-3.5 removed supplier is not in the list", !suppliers.getAllSuppliers().contains(trish));

        System.out.println();
        System.out.println("=== Feature 04: Supplier Product Management ===");

        // TC-4.3: a product with no supplier cannot be saved (checked by Product.addProduct)
        Product noSupplier = makeBag("Plain Tote", 10);
        check("TC-4.3 product with no supplier is refused", !noSupplier.addProduct());

        // TC-4.1: one product linked to two suppliers
        Product tote = makeBag("Guess Tote Bag", 2);
        suppliers.linkProduct(wayne, tote);
        suppliers.linkProduct(online, tote);
        tote.addProduct();
        check("TC-4.1 product is linked to 2 suppliers", suppliers.getSuppliersForProduct(tote).size() == 2);
        suppliers.linkProduct(wayne, tote); // linking again should not add it twice
        check("TC-4.1 linking twice does not duplicate", wayne.getProductsSupplied().size() == 1
                && tote.getSupplierIDs().size() == 2);

        // TC-4.2: a supplier's product list shows its own products and shared ones only
        Product crossbody = makeBag("Juicy Crossbody Bag", 10);
        suppliers.linkProduct(online, crossbody);
        crossbody.addProduct();
        check("TC-4.2 Online Bag Co shows both products", suppliers.getProducts(online).size() == 2);
        check("TC-4.2 Wayne shows only the tote", suppliers.getProducts(wayne).size() == 1
                && suppliers.getProducts(wayne).contains(tote));

        // TC-4.4: the low-stock alert shows the supplier's name and phone number
        Product clutch = makeBag("Steve Madden Clutch", 3); // reorder level 5, so stock is low
        suppliers.linkProduct(wayne, clutch);
        clutch.addProduct();
        String contacts = suppliers.getReorderContacts(clutch);
        check("TC-4.4 product is low on stock", clutch.checkLowStock());
        check("TC-4.4 alert shows supplier name and phone", contacts.contains("Wayne's Distributors")
                && contacts.contains("876-898-2710"));
        System.out.println("   TC-4.4 " + contacts);

        // TC-4.5: suppliers of other products are not included
        check("TC-4.5 only the clutch's supplier is shown", !contacts.contains("Online Bag Co"));

        // AC-1: a product's only supplier cannot be removed
        check("AC-1 removing Wayne is refused (only supplier of the clutch)", !suppliers.removeSupplier(wayne));

        // TC-4.6: a restock order is saved with products, quantities, date and status Ordered
        RestockOrder first = new RestockOrder(online);
        first.addProduct(tote, 15);
        first.addProduct(crossbody, 5);
        check("TC-4.6 restock order is saved", restocks.createRestockOrder(first));
        check("TC-4.6 order has 2 products, a date and status Ordered", first.getProductsOrdered().size() == 2
                && first.getDateOrdered() != null && first.getStatus().equals("Ordered"));
        check("TC-4.6 restock ID is in the format RST0001", first.getOrderID().equals("RST0001"));

        // TC-4.8: a quantity of 0 is refused and nothing is saved
        int historyBefore = restocks.getHistory().size();
        RestockOrder zero = new RestockOrder(online);
        zero.addProduct(tote, 0);
        check("TC-4.8 quantity 0 is refused", !restocks.createRestockOrder(zero));
        check("TC-4.8 history is unchanged", restocks.getHistory().size() == historyBefore);

        // TC-4.7: five restock orders are listed from newest to oldest
        RestockOrder last = null;
        for (int i = 1; i <= 5; i++) {
            RestockOrder o = new RestockOrder(wayne);
            o.addProduct(clutch, i);
            restocks.createRestockOrder(o);
            last = o;
        }
        ArrayList<RestockOrder> wayneHistory = restocks.getHistory(wayne);
        check("TC-4.7 all 5 orders are shown", wayneHistory.size() == 5);
        check("TC-4.7 newest order is first", wayneHistory.get(0) == last);

        // TC-4.9: stock 2 + 15 received = 17
        check("TC-4.9 tote starts with 2 in stock", tote.getStockQuantity() == 2);
        check("TC-4.9 order is marked received", restocks.markAsReceived(first.getOrderID()));
        check("TC-4.9 stock went from 2 to 17", tote.getStockQuantity() == 17);
        check("TC-4.9 status is Received", first.getStatus().equals("Received"));

        // TC-4.10: a product at 0 is back in stock after its restock is received
        Product denim = makeBag("True Religion Denim Tote", 0);
        suppliers.linkProduct(online, denim);
        denim.addProduct();
        check("TC-4.10 product starts Out of Stock", denim.isOutOfStock());
        RestockOrder denimOrder = new RestockOrder(online);
        denimOrder.addProduct(denim, 3);
        restocks.createRestockOrder(denimOrder);
        restocks.markAsReceived(denimOrder.getOrderID());
        check("TC-4.10 product is no longer Out of Stock", !denim.isOutOfStock() && denim.getStockQuantity() == 3);

        // TC-4.11: marking the same order received twice is refused and stock does not change
        check("TC-4.11 second time is refused", !restocks.markAsReceived(first.getOrderID()));
        check("TC-4.11 stock stays at 17", tote.getStockQuantity() == 17);

        // TC-3.5 (history kept): a removed supplier still shows on its old restock orders
        Supplier temp = new Supplier("Short Term Supplier", "876-555-0188", "temp@supplier.test", "");
        suppliers.addSupplier(temp);
        suppliers.linkProduct(temp, denim);
        RestockOrder past = new RestockOrder(temp);
        past.addProduct(denim, 2);
        restocks.createRestockOrder(past);
        check("TC-3.5 supplier with a shared product can be removed", suppliers.removeSupplier(temp));
        check("TC-3.5 old restock order still shows the supplier",
                past.getSupplier().getName().equals("Short Term Supplier"));
        check("TC-3.5 removed supplier cannot be linked to products", !suppliers.linkProduct(temp, tote));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
    }
}
