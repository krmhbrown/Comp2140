package order;

import java.util.HashMap;

public class OrderTest {
    public static void main(String[] args) {
        HashMap<String, Integer> items = new HashMap<>();
        items.put("P001", 2);

        // TS-1 / TS-7: bank transfer order waits for payment
        Order bank = new Order("C1", items, "12 Hope Rd", "Bank Transfer", "876-555-0101");
        System.out.println("Placed: " + bank.placeOrder() + ", status: " + bank.getOrderStatus());
        bank.confirmPayment();
        System.out.println("After confirming: " + bank.getOrderStatus() + ", " + bank.getPaymentStatus());

        // TS-8: cash on delivery is confirmed right away
        Order cash = new Order("C2", items, "5 Main St", "Cash on Delivery", "876-555-0102");
        System.out.println("Placed: " + cash.placeOrder() + ", status: " + cash.getOrderStatus());

        // TS-2: missing address is refused
        Order noAddress = new Order("C3", items, "", "Cash on Delivery", "876-555-0103");
        System.out.println("No address placed: " + noAddress.placeOrder());

        // TS-4: empty cart is refused
        Order empty = new Order("C4", new HashMap<>(), "5 Main St", "Cash on Delivery", "876-555-0104");
        System.out.println("Empty cart placed: " + empty.placeOrder());

        // TS-11 / TS-12 / TS-14: cancelling
        System.out.println("Cancel cash order: " + cash.cancelOrder());
        System.out.println("Cancel again: " + cash.cancelOrder());
        cash.updateStatus("Order Packaging");
        System.out.println("Status after cancel: " + cash.getOrderStatus());

        // TS-9: status history
        System.out.println("Bank history: " + bank.trackOrder());
    }
}