//Author: Karimah Brown
//Date of Creation: 20/11/2026
//Date of last modification: 23/11/2026

/* RestockOrder class that represents a restock order in the system. 
It contains information about the order details, including the supplier, 
products, and quantities. The class provides methods to manage and update
restock orders.*/

package supplier;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import product.Product;

public class RestockOrder {
    private String orderID;
    private Supplier supplier;
    private ArrayList<Product> productsOrdered;
    private ArrayList<Integer> quantitiesOrdered;
    private LocalDateTime dateOrdered;
    private boolean isReceived;
    private static int nextID = 1;

    //Constructor to create instance of a restock order.
    public RestockOrder(Supplier supplier) {
        this.supplier = supplier;
        this.productsOrdered = new ArrayList<>();
        this.quantitiesOrdered = new ArrayList<>();
        this.isReceived = false; // Default to not received
    }

    //Adds one product and the quantity ordered.
    public void addProduct(Product product, int quantity) {
        productsOrdered.add(product);
        quantitiesOrdered.add(quantity);
    }

    //Order is only valid if the supplier is active, it has at least one product,
    //every quantity is at least 1, and every product is supplied by this supplier.
    public boolean validateOrder() {
        if (supplier == null || !supplier.isActive() || productsOrdered.isEmpty()) {
            return false;
        }
        for (int i = 0; i < productsOrdered.size(); i++) {
            if (quantitiesOrdered.get(i) < 1) {
                return false;
            }
            if (!supplier.getProductsSupplied().contains(productsOrdered.get(i))) {
                return false;
            }
        }
        return true;
    }

    //Places the order: gives it an ID, e.g. RST0001, and today's date. Returns false if it is not valid.
    public boolean placeOrder() {
        if (orderID != null || !validateOrder()) {
            return false;
        }
        orderID = "RST" + String.format("%04d", nextID);
        nextID++;
        dateOrdered = LocalDateTime.now();
        return true;
    }

    //Marks the order as received and adds each quantity to the product's stock.
    //Returns false if the order was not placed or was already received, so stock is only added once.
    public boolean markAsReceived() {
        if (orderID == null || isReceived) {
            return false;
        }
        for (int i = 0; i < productsOrdered.size(); i++) {
            productsOrdered.get(i).updateStock(quantitiesOrdered.get(i));
        }
        isReceived = true;
        return true;
    }

    public String getStatus() {
        if (isReceived) {
            return "Received";
        }
        return "Ordered";
    }

    //Getters
    public String getOrderID() {
        return orderID;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public ArrayList<Product> getProductsOrdered() {
        return productsOrdered;
    }

    public ArrayList<Integer> getQuantitiesOrdered() {
        return quantitiesOrdered;
    }

    public LocalDateTime getDateOrdered() {
        return dateOrdered;
    }

    public boolean isReceived() {
        return isReceived;
    }

    public void displayOrder() {
        System.out.println("Restock ID: " + orderID);
        System.out.println("Supplier: " + supplier.getName() + " (" + supplier.getSupplierID() + ")");
        System.out.println("Date ordered: " + dateOrdered.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        for (int i = 0; i < productsOrdered.size(); i++) {
            System.out.println("  " + productsOrdered.get(i).getProductID() + " " + productsOrdered.get(i).getName()
                    + ", Quantity: " + quantitiesOrdered.get(i));
        }
        System.out.println("Status: " + getStatus());
    }
}