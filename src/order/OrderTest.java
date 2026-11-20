package order;
import java.util.HashMap;

public class OrderTest {
    public static void main(String[] args) {
        HashMap<String, Integer> items = new HashMap<>();
        items.put("P001", 2);

        // TS-1: two customers get different order IDs, each linked to the right customer
        Order a = new Order("C10", items, "1 King St", "Cash on Delivery", "876-555-0110");
        Order b = new Order("C11", items, "2 Queen St", "Cash on Delivery", "876-555-0111");
        a.placeOrder();
        b.placeOrder();
        System.out.println("TS-1 IDs: " + a.getOrderID() + " (" + a.getCustomerID() + "), "
                + b.getOrderID() + " (" + b.getCustomerID() + ")");
        System.out.println("TS-1 different IDs: " + !a.getOrderID().equals(b.getOrderID()));

        // TS-2: missing address is refused and the order gets no ID (also TS-6: no invoice is printed)
        Order noAddress = new Order("C3", items, "", "Cash on Delivery", "876-555-0103");
        System.out.println("No address placed: " + noAddress.placeOrder());
        System.out.println("TS-2 refused order ID: " + noAddress.getOrderID());

        // TS-4: empty cart is refused
        Order empty = new Order("C4", new HashMap<>(), "5 Main St", "Cash on Delivery", "876-555-0104");
        System.out.println("Empty cart placed: " + empty.placeOrder());

        // TS-7: bank transfer order waits for payment
        Order bank = new Order("C1", items, "12 Hope Rd", "Bank Transfer", "876-555-0101");
        System.out.println("Placed: " + bank.placeOrder() + ", status: " + bank.getOrderStatus());
        bank.confirmPayment();
        System.out.println("After confirming: " + bank.getOrderStatus() + ", " + bank.getPaymentStatus());

        // TS-8: cash on delivery is confirmed right away
        Order cash = new Order("C2", items, "5 Main St", "Cash on Delivery", "876-555-0102");
        System.out.println("Placed: " + cash.placeOrder() + ", status: " + cash.getOrderStatus());

        // TS-9: status history
        System.out.println("Bank history: " + bank.trackOrder());

        // TS-11: cancelling during "Order Packaging"
        Order packing = new Order("C12", items, "3 Duke St", "Cash on Delivery", "876-555-0112");
        packing.placeOrder();
        packing.updateStatus("Order Packaging");
        System.out.println("TS-11 cancel during packaging: " + packing.cancelOrder()
                + ", status: " + packing.getOrderStatus());

        // TS-16: cancelling twice is refused and no updates after cancelling
        System.out.println("Cancel cash order: " + cash.cancelOrder());
        System.out.println("Cancel again: " + cash.cancelOrder());
        cash.updateStatus("Order Packaging");
        System.out.println("Status after cancel: " + cash.getOrderStatus());
    }
}