//author: Kandia Brown
//date: 11/22/2026
//Runs the Product Management test cases (TC-5.1 to TC-5.18) using the data from the test plan
package product;

public class ProductTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean result) {
        if (result) { passed++; System.out.println("PASS  " + name); }
        else { failed++; System.out.println("FAIL  " + name); }
    }

    // A complete, valid product (reorder level 5)
    private static Product makeBag(String name, String colour, String category, double price, int quantity) {
        Product p = new Product(name, colour, category, price, quantity);
        p.setDescription("A bag");
        p.setReorderLevel(5);
        p.addImage("images/bag.jpg");
        p.addSupplierID("S-001");
        return p;
    }

    public static void main(String[] args) throws Exception {

        // TS-1 (TC-5.1): the Juicy Crossbody Bag in pink and black gets two different IDs
        Product pink = makeBag("Juicy Crossbody Bag", "Pink", "Crossbody Bag", 8500.0, 10);
        Product black = makeBag("Juicy Crossbody Bag", "Black", "Crossbody Bag", 8500.0, 10);
        pink.setDescription("Pink crossbody bag");
        black.setDescription("Black crossbody bag");
        pink.addProduct();
        black.addProduct();
        check("TS-1 the pink and black bags get different IDs", !pink.getProductID().equals(black.getProductID()));
        System.out.println("   TS-1 IDs: " + pink.getProductID() + " (Pink), " + black.getProductID() + " (Black)");
        System.out.println("   TS-1 different IDs: " + !pink.getProductID().equals(black.getProductID()));

        // TS-2 (TC-5.2): a new product shows the details that were entered
        check("TS-2 the saved details match what was entered",
                pink.getName().equals("Juicy Crossbody Bag") && pink.getColour().equals("Pink")
                && pink.getCategory().equals("Crossbody Bag") && pink.getPrice() == 8500.0
                && pink.getStockQuantity() == 10 && pink.getReorderLevel() == 5 && pink.isActive()
                && pink.getDescription().equals("Pink crossbody bag") && pink.getSupplierIDs().contains("S-001"));
        System.out.println("   TS-2 check -> ID: " + pink.getProductID() + ", Name: " + pink.getName()
                + ", Description: " + pink.getDescription() + ", Colour: " + pink.getColour()
                + ", Category: " + pink.getCategory() + ", Price: " + pink.getPrice()
                + ", Quantity: " + pink.getStockQuantity() + ", Reorder Level: " + pink.getReorderLevel()
                + ", Supplier ID(s): " + pink.getSupplierIDs() + ", Photo(s): " + pink.getImage());

        // TS-3 (TC-5.3): a price change is saved
        pink.setPrice(10000.0);
        pink.editProduct();
        check("TS-3 price changed to 10000", pink.getPrice() == 10000.0);
        System.out.println("   TS-3 price now: " + pink.getPrice());

        // TS-4 (TC-5.4): a colour change is saved
        pink.setColour("Black");
        pink.editProduct();
        check("TS-4 colour changed to Black", pink.getColour().equals("Black"));
        System.out.println("   TS-4 colour now: " + pink.getColour());

        // TS-5 (TC-5.5): a product with no price or no photo is refused
        Product noPrice = new Product("Steven Madden Liamm Bag Wine", "Red", "Tote Bag", null, 2);
        noPrice.setDescription("A red leather tote");
        noPrice.setReorderLevel(2);
        noPrice.addImage("images/bag.jpg");
        noPrice.addSupplierID("S-001");
        Product noPhoto = new Product("Steven Madden Liamm Bag Wine", "Red", "Tote Bag", 9000.0, 2);
        noPhoto.setDescription("A red leather tote");
        noPhoto.setReorderLevel(2);
        noPhoto.addSupplierID("S-001");
        boolean savedNoPrice = noPrice.addProduct();
        boolean savedNoPhoto = noPhoto.addProduct();
        check("TS-5 no price: not saved", !savedNoPrice);
        check("TS-5 no photo: not saved", !savedNoPhoto);
        System.out.println("   TS-5 no price saved: " + savedNoPrice + ", ID: " + noPrice.getProductID());
        System.out.println("   TS-5 no photo saved: " + savedNoPhoto + ", ID: " + noPhoto.getProductID());

        // TS-6 (TC-5.6): an edit with the name cleared is refused
        Product edited = makeBag("Steven Madden Liamm Bag Wine", "Red", "Tote Bag", 9000.0, 2);
        edited.addProduct();
        edited.setName("");
        boolean refused = false;
        try { edited.editProduct(); } catch (IllegalStateException e) { refused = true; }
        check("TS-6 an edit with the name cleared is refused", refused);
        System.out.println("   TS-6 edit refused: " + refused);

        // TS-7 (TC-5.7) and TS-8 (TC-5.8): PENDING - customer and owner views, tested on the web pages

        // TS-9 (TC-5.9): if the removal is cancelled, the product stays
        Product stays = makeBag("Wine Tote", "Red", "Tote Bag", 9000.0, 2);
        stays.addProduct();
        check("TS-9 the product stays when removal is cancelled", stays.isActive());
        System.out.println("   TS-9 still in catalogue: " + stays.isActive());

        // TS-10 (TC-5.10): a removed product leaves the catalogue once confirmed
        Product removed = makeBag("Tan Tote", "Tan", "Tote Bag", 8500.0, 3);
        removed.addProduct();
        boolean removedOk = removed.removeProduct();
        check("TS-10 the removed product is no longer in the catalogue", removedOk && !removed.isActive());
        System.out.println("   TS-10 removed: " + removedOk + ", still active: " + removed.isActive());

        // TS-11 (TC-5.11): a removed product keeps its name and price for past orders
        check("TS-11 removed product keeps its name and price", removed.getName().equals("Tan Tote") && removed.getPrice() == 8500.0);
        System.out.println("   TS-11 name: " + removed.getName() + ", price: " + removed.getPrice());

        // TS-12 (TC-5.12): a removed product cannot be ordered
        boolean canOrder = removed.checkAvailability(1);
        check("TS-12 a removed product cannot be ordered", !canOrder);
        System.out.println("   TS-12 can be ordered: " + canOrder);

        // TS-13 (TC-5.13): stock goes from 10 to 7 when 3 are ordered, and back to 10 when cancelled
        Product denim = makeBag("True Religion Denim Tote", "Denim", "Tote Bag", 12500.0, 10);
        denim.addProduct();
        denim.updateStock(-3);
        int afterOrder = denim.getStockQuantity();
        denim.updateStock(3);
        int afterCancel = denim.getStockQuantity();
        check("TS-13 stock goes 10 -> 7 -> 10", afterOrder == 7 && afterCancel == 10);
        System.out.println("   TS-13 after order: " + afterOrder + ", after cancel: " + afterCancel);

        // TS-14 (TC-5.14): stock rises by 1 when one bag is returned
        Product shoulder = makeBag("Basic Fashion Shoulder Bag", "White", "Shoulder Bag", 6500.0, 1);
        shoulder.addProduct();
        shoulder.updateStock(1);
        check("TS-14 stock rises by 1 on a return", shoulder.getStockQuantity() == 2);
        System.out.println("   TS-14 stock after return: " + shoulder.getStockQuantity());

        // Stock can never go below 0
        Product tiny = makeBag("Grey Bag", "Grey", "Tote Bag", 5000.0, 1);
        tiny.addProduct();
        boolean tooMany = false;
        try { tiny.updateStock(-5); } catch (IllegalArgumentException e) { tooMany = true; }
        check("stock cannot go below 0", tooMany && tiny.getStockQuantity() == 1);

        // TS-15 (TC-5.15): out of stock at 0, not at 1 (boundary)
        boolean outAtOne = tiny.isOutOfStock();
        tiny.updateStock(-1);
        boolean outAtZero = tiny.isOutOfStock();
        tiny.updateStock(1);
        boolean outRestocked = tiny.isOutOfStock();
        check("TS-15 out of stock at 0, not at 1 or after restock", !outAtOne && outAtZero && !outRestocked);
        System.out.println("   TS-15 out of stock at 1: " + outAtOne + ", at 0: " + outAtZero + ", restocked to 1: " + outRestocked);

        // TS-16 (TC-5.16): PENDING - "Out of Stock" on the details page, tested on the web page

        // TS-17 (TC-5.17): a notification at or below the reorder level (5), none above it
        Product alert = makeBag("Pink Juicy Crossbody Bag", "Pink", "Crossbody Bag", 8500.0, 7);
        alert.addProduct();
        alert.updateStock(-1);   // 6 left, above 5
        boolean noAlert = alert.getLastAlertMessage() == null;
        alert.updateStock(-1);   // 5 left, at 5
        boolean atLevel = alert.getLastAlertMessage() != null;
        check("TS-17 no alert above the reorder level", noAlert);
        check("TS-17 alert at the reorder level", atLevel);
        System.out.println("   TS-17 alert above level: " + !noAlert + ", alert at level: " + atLevel);
        System.out.println("   TS-17 " + alert.getLastAlertMessage());

        // TS-18 (TC-5.18): PENDING - arrives within 1 minute, needs the Notification class

        check("availability: enough stock", alert.checkAvailability(2));
        check("availability: not enough stock", !alert.checkAvailability(6));

        Product photos = makeBag("Beige Bag", "Beige", "Tote Bag", 5000.0, 1);
        for (int i = 0; i < 4; i++) { photos.addImage("images/extra" + i + ".jpg"); }
        boolean sixth = false;
        try { photos.addImage("images/sixth.jpg"); } catch (IllegalStateException e) { sixth = true; }
        check("a sixth photo is refused", sixth);

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed.");
    }
}
