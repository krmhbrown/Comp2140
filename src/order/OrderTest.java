package order;
import java.util.HashMap;

import product.Product;

//Runs the Order Management test cases (TC-1.1 to TC-1.16) using the data from the test plan
public class OrderTest {
    public static void main(String[] args) {
        // Products from Product Management (names and prices from the owner's list, 50 of each in stock).
        // addProduct() gives them the IDs P001, P002 ... in this order.
        String[][] productList = {
            {"Blush Pink Juicy Couture", "Blush Pink", "Backpack", "7000"},
            {"Dark Pink Juicy C. Backpack", "Dark Pink", "Backpack", "10500"},
            {"Pink Juicy Shoulder Bag", "Pink", "Shoulder Bag", "9500"},
            {"Jesica Simpson Backpack", "White", "Backpack", "6500"},
            {"Basic Fashion Shoulder Bag", "White", "Shoulder Bag", "6500"},
            {"White Steve Madden Shoulder Bag", "White", "Shoulder Bag", "9000"},
            {"Karl Lagerfeld Paris Crossbody Bag", "White", "Crossbody Bag", "13500"},
            {"DKNY Margot Crescent Crossbody Bag", "White", "Crossbody Bag", "14500"},
            {"Pink Juicy Crossbody Bag", "Pink", "Crossbody Bag", "8500"}
        };
        HashMap<String, Product> catalogue = new HashMap<>();
        for (String[] p : productList) {
            Product product = new Product(p[0], p[1], p[2], Double.parseDouble(p[3]), 50);
            product.setDescription(p[0] + ".");
            product.setReorderLevel(1);
            product.addImage("images/" + p[0] + ".jpg");
            product.addSupplierID("S-001");
            product.addProduct();
            catalogue.put(product.getProductID(), product);
        }
        Order.setCatalogue(catalogue);

        HashMap<String, Integer> items = new HashMap<>();
        items.put("P001", 2);

        // TS-1 (TC-1.1): two customers get different order IDs, each linked to the right customer
        Order a = new Order("C1000", items, "12 Hope Road", "Bank Transfer", "876-555-0101");
        Order b = new Order("C1001", items, "2 Queen St", "Cash on Delivery", "876-555-0102");
        a.placeOrder();
        b.placeOrder();
        System.out.println("TS-1 IDs: " + a.getOrderID() + " (" + a.getCustomerID() + "), "
                + b.getOrderID() + " (" + b.getCustomerID() + ")");
        System.out.println("TS-1 different IDs: " + !a.getOrderID().equals(b.getOrderID()));

        // TS-2 (TC-1.2): missing delivery address is refused and the order gets no ID
        Order noAddress = new Order("C1002", items, "", "Bank Transfer", "876-555-0101");
        System.out.println("TS-2 order placed: " + noAddress.placeOrder());
        System.out.println("TS-2 order ID: " + noAddress.getOrderID());

        // TS-3 (TC-1.3): an order with at least one item and all required fields is accepted
        Order valid = new Order("C1003", items, "7 Hope Road", "Cash on Delivery", "876-555-0103");
        System.out.println("TS-3 passes validation: " + valid.validateOrder());
        System.out.println("TS-3 order placed: " + valid.placeOrder());

        // TS-4 (TC-1.4): an order with no items is refused
        Order empty = new Order("C1004", new HashMap<>(), "5 Main St", "Cash on Delivery", "876-555-0104");
        System.out.println("TS-4 empty cart placed: " + empty.placeOrder());

        // TS-5 (TC-1.5): the invoice displays accurate order and payment information
        Order invoiceOrder = new Order("C1005", items, "9 White Road", "Bank Transfer", "876-555-0105");
        System.out.println("TS-5 order placed: " + invoiceOrder.placeOrder()); // the invoice prints here
        System.out.println("TS-5 check -> ID: " + invoiceOrder.getOrderID()
                + ", Customer: " + invoiceOrder.getCustomerID()
                + ", Address: " + invoiceOrder.getDeliveryAddress()
                + ", Payment Method: " + invoiceOrder.getPaymentMethod()
                + ", Payment Status: " + invoiceOrder.getPaymentStatus());

        // TS-6 (TC-1.6): no invoice is sent for an order with a missing contact number
        Order noContact = new Order("C1006", items, "22 Molynes Road", "Cash on Delivery", "");
        System.out.println("TS-6 order placed: " + noContact.placeOrder());
        System.out.println("TS-6 order ID: " + noContact.getOrderID() + ", status: " + noContact.getOrderStatus());

        // TS-7 (TC-1.7): a bank transfer order is "Awaiting Payment" until the authorised user confirms it
        Order bank = new Order("C1007", items, "12 Duke Street", "Bank Transfer", "876-555-0101");
        bank.placeOrder();
        System.out.println("TS-7 status after placing: " + bank.getOrderStatus());
        bank.confirmPayment();
        System.out.println("TS-7 status after confirming: " + bank.getOrderStatus() + ", " + bank.getPaymentStatus());

        // TS-8 (TC-1.8): a cash on delivery order becomes "Order Confirmed", not "Awaiting Payment"
        Order cash = new Order("C1008", items, "13 Wire Street", "Cash on Delivery", "876-555-0108");
        cash.placeOrder();
        System.out.println("TS-8 status: " + cash.getOrderStatus());

        // TS-9 (TC-1.9): each status update appears in the status history with the date and time
        Order history = new Order("C1009", items, "13 Browns Street", "Cash on Delivery", "876-555-0109");
        history.placeOrder();                      // "Order Confirmed"
        history.updateStatus("Order Packaging");   // authorised user updates the status
        System.out.println("TS-9 status history: " + history.trackOrder());

        // TS-10 (TC-1.10): PENDING - needs customer login to check one customer can't view another's history

        // TS-11 (TC-1.11): a customer can cancel an order with the status "Order Packaging"
        Order packing = new Order("C1011", items, "13 Lighter Street", "Cash on Delivery", "876-555-0111");
        packing.placeOrder();
        packing.updateStatus("Order Packaging");
        System.out.println("TS-11 cancelled: " + packing.cancelOrder());
        System.out.println("TS-11 status: " + packing.getOrderStatus());

        // TS-12 (TC-1.12): an order that has completed all processing stages cannot be cancelled
        Order inDelivery = new Order("C1012", items, "12 Hope Pastures", "Cash on Delivery", "876-555-0112");
        inDelivery.placeOrder();
        inDelivery.updateStatus("Order Packaging");
        inDelivery.updateStatus("Order Entered Delivery Management Process");
        System.out.println("TS-12 cancelled: " + inDelivery.cancelOrder());
        System.out.println("TS-12 status: " + inDelivery.getOrderStatus());

        // TS-13 (TC-1.13): the cancellation message correctly displays the details of the order
        Order cancelMsg = new Order("C1013", items, "12 Hope Pastures", "Cash on Delivery", "876-555-0113");
        cancelMsg.placeOrder();
        cancelMsg.updateStatus("Order Packaging");
        System.out.println("TS-13 expected order ID in message: " + cancelMsg.getOrderID());
        System.out.println("TS-13 cancelled: " + cancelMsg.cancelOrder()); // the cancellation message prints here

        // TS-14 (TC-1.14): no cancellation message is sent when a cancellation is refused
        Order refusedCancel = new Order("C1014", items, "12 Pines Crescent", "Cash on Delivery", "876-555-0114");
        refusedCancel.placeOrder();
        refusedCancel.updateStatus("Order Packaging");
        refusedCancel.updateStatus("Order Entered Delivery Management Process");
        System.out.println("TS-14 cancelled: " + refusedCancel.cancelOrder()); // no cancellation message should print

        // TS-15 (TC-1.15): cancelling an order with two products returns the exact quantity of each to stock
        HashMap<String, Integer> twoItems = new HashMap<>();
        twoItems.put("P009", 2); // Pink Juicy Crossbody Bag
        twoItems.put("P002", 3); // second item
        Order multi = new Order("C1015", twoItems, "12 Pines Crescent", "Cash on Delivery", "876-555-0115");
        multi.placeOrder();
        multi.updateStatus("Order Packaging");
        System.out.println("TS-15 stock before cancelling: P009 = " + catalogue.get("P009").getStockQuantity()
                + ", P002 = " + catalogue.get("P002").getStockQuantity());
        System.out.println("TS-15 cancelled: " + multi.cancelOrder());
        System.out.println("TS-15 stock after cancelling: P009 = " + catalogue.get("P009").getStockQuantity()
                + ", P002 = " + catalogue.get("P002").getStockQuantity());

        // TS-16 (TC-1.16): stock does not change when cancelling an order that is in delivery
        Order delivering = new Order("C1016", items, "11 Trafalgar Road", "Cash on Delivery", "876-555-0116");
        delivering.placeOrder();
        delivering.updateStatus("Order Packaging");
        delivering.updateStatus("Order Entered Delivery Management Process");
        int stockBefore = catalogue.get("P001").getStockQuantity();
        System.out.println("TS-16 cancel in delivery: " + delivering.cancelOrder());
        System.out.println("TS-16 P001 stock before: " + stockBefore + ", after: " + catalogue.get("P001").getStockQuantity());
    }
}