//author: Arriyanna Grant
//date: 2/15/2024
//This is the order management class.
package order;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.format.DateTimeFormatter;

public class Order {

    private String orderID; //unique identifier for the order
    private LocalDateTime orderDate; //date and time when the order was placed
    private String customerID; //unique identifier for the customer
    private HashMap<String, Integer> items;  //product ID and quantity
    private String deliveryAddress; //address where the order will be delivered
    private String contactNumber; //customer's contact number
    private Double totalAmount; //calculated based on items
    private String paymentMethod; //method of payment
    private String paymentStatus; //status of payment (e.g., Paid, Unpaid, Pay on Delivery)
    private String orderStatus; //current status of the order (e.g., Awaiting Payment, Order Confirmed, Order Packaging, Order Entered Delivery Management Process, Order Cancelled)
    private ArrayList<String> statusHistory; //all statuses the order has gone through

    // Constructor
 public Order(String customerID, HashMap<String, Integer> items, String deliveryAddress, String paymentMethod, String contactNumber) {
        this.customerID = customerID;
        this.items = items;
        this.deliveryAddress = deliveryAddress;
        this.paymentMethod = paymentMethod;
        this.contactNumber = contactNumber;
        this.orderDate = LocalDateTime.now();
        this.totalAmount = 0.0;
        this.paymentStatus = "Unpaid"; //initialzed until payment is confirmed
        this.statusHistory = new ArrayList<>();
    }

    //order only valid if all required fields are filled
    public boolean validateOrder() {
        if (customerID == null || customerID.isEmpty() || items == null || items.isEmpty() || deliveryAddress == null || deliveryAddress.isEmpty() || paymentMethod == null || paymentMethod.isEmpty() || contactNumber == null || contactNumber.isEmpty()) {
            return false;
        }
        return true;
    }

        public boolean placeOrder() {
        if (!validateOrder()) { //invalid orders cannot be placed
            return false;
        }
        // The generation of order Id
        this.orderID = "ORD" + System.currentTimeMillis();
        orderDate = LocalDateTime.now();
        //Bank transfer orders require payment confirmation before order confirmation
        if (paymentMethod.equals("Bank Transfer")) {
            updateStatus("Awaiting Payment");
            paymentStatus = "Unpaid";

        //Cash on delivery orders are confirmed immediately, but payment is expected upon delivery
        } else if (paymentMethod.equals("Cash on Delivery")) {
            updateStatus("Order Confirmed");
            paymentStatus = "Unpaid";
        }
        //calculate the total amount for the order based on the items and their quantities
        this.totalAmount = calculateTotal();
        generateInvoice(); // NEW: the customer gets an invoice once the order is placed (SR-3)
        return true;
    }
    
    //The order invoice
        //The order invoice
    public void generateInvoice() {
        // The invoice can only be generated if the order is in a processing stage
        if (isProcessingStage()) { 
            System.out.println("==== PEAK FINDS INVOICE ====");
            System.out.println("Order ID: " + orderID);
            System.out.println("Order Date: " + orderDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            System.out.println("Items:");

            //Iterates through the items in the order and prints their product ID and quantity
            for (String productID : items.keySet()) {
                System.out.println("  Product ID: " + productID + ", Quantity: " + items.get(productID));
                System.out.println("  Price: $" + getProductPrice(productID) + ", Total: $" + (getProductPrice(productID) * items.get(productID)));
            }
            System.out.println("Customer ID: " + customerID);
            System.out.println("Delivery Address: " + deliveryAddress);
            System.out.println("Contact Number: " + contactNumber);
            System.out.println("Total Amount: $" + totalAmount);
            System.out.println("Payment Method: " + paymentMethod); // NEW: TS-5 checks payment info
            System.out.println("Payment Status: " + paymentStatus); // NEW
        } else {
            System.out.println("Invoice unavailable.");
        }
    }

    //The total amount for the order
    public Double calculateTotal() {
        return items.entrySet().stream()
                .mapToDouble(entry -> getProductPrice(entry.getKey()) * entry.getValue())
                .sum();
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
        return statusHistory; // TODO
    }

    //Allows order cancellation if the order is in a processing stage (Awaiting Payment, Order Confirmed, Order Packaging)
    public boolean cancelOrder() {
        if (isProcessingStage()) { 
            updateStatus("Cancelled"); 

            //Cancelled items are returned to inventory stock 
            for (String productID : items.keySet()) { // NEW
                System.out.println("Restock " + productID + ": +" + items.get(productID));
                //TODO: call product.updateStock(quantity) once connected to Product
            }
            //Cancellation message to the customer (SR-7)
            System.out.println("Your order " + orderID + " has been cancelled."); // NEW
            return true;
        }
        return false;
    }
    //Temporary method to get product price, to be replaced with actual product price retrieval logic
    private Double getProductPrice(String productID) {
        return 0.0;
    }

    //Confirms payment for bank transfer orders that are awaiting payment and updates the order status to "Order Confirmed"
    public void confirmPayment() {
        if ("Awaiting Payment".equals(orderStatus)) { // CHANGED: was paymentStatus.equals("Unpaid")
            paymentStatus = "Paid";
            updateStatus("Order Confirmed"); // CHANGED: was "ORDER_CONFIRMED"
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

    //This method checks if the order is in a processing stage (Awaiting Payment, Order Confirmed, Order Packaging, Order Entered Delivery Management Process)
    public boolean isProcessingStage() {
        return "Awaiting Payment".equals(orderStatus)
                || "Order Confirmed".equals(orderStatus)
                || "Order Packaging".equals(orderStatus);
        }
    }