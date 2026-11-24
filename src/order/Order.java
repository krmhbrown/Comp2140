//author: Arriyanna Grant
//date: 11/23/2026
//This is the order management class.
package order;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;

import product.Product;

public class Order {

    private String orderID; //unique identifier for the order
    private LocalDateTime orderDate; //date and time when the order was placed
    private String customerID; //unique identifier for the customer
    private HashMap<String, Integer> items;  //product ID and quantity
    private String deliveryAddress; //address where the order will be delivered
    private String contactNumber; //customer's contact number
    private Double totalAmount; //calculated based on items
    private String paymentMethod; //method of payment
    private String paymentStatus; //status of payment (Unpaid or Paid)
    private String orderStatus; //current status of the order (Awaiting Payment, Order Confirmed, Order Packaging, Order Entered Delivery Management Process, Cancelled)
    private ArrayList<String> statusHistory; //all statuses the order has gone through
    private boolean stockReserved; //true once the order's items have been taken out of stock

    private static int nextOrderNumber = 1; //shared by all orders so each ID is unique
    private static HashMap<String, Product> catalogue = new HashMap<>(); //products from Product Management, by product ID

    // Constructor
    public Order(String customerID, HashMap<String, Integer> items, String deliveryAddress, String paymentMethod, String contactNumber) {
        this.customerID = customerID;
        this.items = items;
        this.deliveryAddress = deliveryAddress;
        this.paymentMethod = paymentMethod;
        this.contactNumber = contactNumber;
        this.orderDate = LocalDateTime.now();
        this.totalAmount = 0.0;
        this.paymentStatus = "Unpaid"; //initialised until payment is confirmed
        this.statusHistory = new ArrayList<>();
        this.stockReserved = false;
    }

    //Connects Order Management to Product Management's products, so orders use real prices and stock
    public static void setCatalogue(HashMap<String, Product> products) {
        catalogue = products;
    }

    //order only valid if all required fields are filled
    public boolean validateOrder() {
        if (customerID == null || customerID.isEmpty() || items == null || items.isEmpty() || deliveryAddress == null || deliveryAddress.isEmpty() || paymentMethod == null || paymentMethod.isEmpty() || contactNumber == null || contactNumber.isEmpty()) {
            return false;
        }
        return true;
    }

    //every product in the order must be available in the quantity ordered (Product Management SR-9)
    private boolean itemsAvailable() {
        for (String productID : items.keySet()) {
            Product product = catalogue.get(productID);
            if (product == null || !product.checkAvailability(items.get(productID))) {
                return false;
            }
        }
        return true;
    }

    public boolean placeOrder() {
        if (!validateOrder() || !itemsAvailable()) { //invalid orders and unavailable items cannot be placed
            return false;
        }
        // The generation of order Id
        this.orderID = String.format("ORD%04d", nextOrderNumber++);
        orderDate = LocalDateTime.now();
        //Bank transfer orders require payment confirmation before order confirmation
        if (paymentMethod.equals("Bank Transfer")) {
            updateStatus("Awaiting Payment");
            paymentStatus = "Unpaid";

        //Cash on delivery orders are confirmed immediately, but payment is expected upon delivery
        } else if (paymentMethod.equals("Cash on Delivery")) {
            updateStatus("Order Confirmed");
            paymentStatus = "Unpaid";
            reserveStock(); //stock is taken once the order is confirmed (Product Management SR-10)
        }
        //calculate the total amount for the order based on the items and their quantities
        this.totalAmount = calculateTotal();
        generateInvoice(); //the customer gets an invoice once the order is placed (SR-3)
        return true;
    }

    //The order invoice
    public void generateInvoice() {
        // The invoice can only be generated if the order is in a processing stage
        if (isProcessingStage()) {
            System.out.println("==== PEAK FINDS INVOICE ====");
            System.out.println("Order ID: " + orderID);
            System.out.println("Order Date: " + orderDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            System.out.println("Items:");

            //Iterates through the items in the order and prints their name, quantity and price
            for (String productID : items.keySet()) {
                System.out.println("  " + productID + " " + getProductName(productID) + ", Quantity: " + items.get(productID)
                        + ", Price: $" + String.format("%.2f", getProductPrice(productID))
                        + ", Total: $" + String.format("%.2f", getProductPrice(productID) * items.get(productID)));
            }
            System.out.println("Customer ID: " + customerID);
            System.out.println("Delivery Address: " + deliveryAddress);
            System.out.println("Contact Number: " + contactNumber);
            System.out.println("Total Amount: $" + String.format("%.2f", totalAmount));
            System.out.println("Payment Method: " + paymentMethod);
            System.out.println("Payment Status: " + paymentStatus);
        } else {
            System.out.println("Invoice unavailable.");
        }
    }

    //The total amount for the order
    public Double calculateTotal() {
        double total = 0.0;
        for (String productID : items.keySet()) {
            total += getProductPrice(productID) * items.get(productID);
        }
        return total;
    }

    //Updates the order status and adds it to the status history with a timestamp
    public void updateStatus(String status) {
        //A cancelled order cannot receive any further status updates
        if ("Cancelled".equals(orderStatus)) return;
        this.orderStatus = status;
        this.statusHistory.add(status + " - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    }

    //Returns the status history of the order for tracking purposes
    public ArrayList<String> trackOrder() {
        return statusHistory;
    }

    //Allows order cancellation if the order is in a processing stage (Awaiting Payment, Order Confirmed, Order Packaging)
    public boolean cancelOrder() {
        if (isProcessingStage()) {
            updateStatus("Cancelled");

            //Cancelled items are returned to inventory stock (SR-8)
            if (stockReserved) {
                for (String productID : items.keySet()) {
                    catalogue.get(productID).updateStock(items.get(productID));
                    System.out.println("Restock " + productID + ": +" + items.get(productID));
                }
                stockReserved = false;
            }
            //Cancellation message to the customer (SR-7)
            System.out.println("Your order " + orderID + " has been cancelled.");
            System.out.println("Cancelled items: " + items + ", Delivery Address: " + deliveryAddress
                    + ", Payment Method: " + paymentMethod);
            return true;
        }
        return false;
    }

    //Gets the product's price from Product Management
    private Double getProductPrice(String productID) {
        Product product = catalogue.get(productID);
        return (product == null) ? 0.0 : product.getPrice();
    }

    //Gets the product's name from Product Management
    private String getProductName(String productID) {
        Product product = catalogue.get(productID);
        return (product == null) ? "" : product.getName();
    }

    //Takes the order's items out of stock (only once per order)
    private void reserveStock() {
        if (stockReserved) return;
        for (String productID : items.keySet()) {
            catalogue.get(productID).updateStock(-items.get(productID));
        }
        stockReserved = true;
    }

    //Confirms payment for bank transfer orders that are awaiting payment and updates the order status to "Order Confirmed"
    public void confirmPayment() {
        if ("Awaiting Payment".equals(orderStatus)) {
            paymentStatus = "Paid";
            updateStatus("Order Confirmed");
            reserveStock(); //stock is taken once the order is confirmed (Product Management SR-10)
        }
    }

    //Getters for the order attributes
    public String getOrderID() { return orderID; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public String getCustomerID() { return customerID; }
    public HashMap<String, Integer> getItems() { return items; }
    public String getDeliveryAddress() { return deliveryAddress; }
    public String getContactNumber() { return contactNumber; }
    public Double getTotalAmount() { return totalAmount; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getOrderStatus() { return orderStatus; }
    public ArrayList<String> getStatusHistory() { return statusHistory; }

    //This method checks if the order is in a processing stage (Awaiting Payment, Order Confirmed, Order Packaging)
    public boolean isProcessingStage() {
        return "Awaiting Payment".equals(orderStatus)
                || "Order Confirmed".equals(orderStatus)
                || "Order Packaging".equals(orderStatus);
    }
}
