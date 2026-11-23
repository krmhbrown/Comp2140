//author: Kandia Brown
//date: 11/22/2026
//This is the product management class.
package product;

import java.util.ArrayList;

/**
 * Product class from the SDS class diagram (Feature 05, Product Management).
 * Holds a bag's details and the rules for adding, editing, removing and tracking its stock.
 */
public class Product {

    private static final int MAX_IMAGES = 5;      // A-3: at most 5 images per product
    private static int nextNumber = 1;            // used to give every product a unique ID (SR-2)

    private String productID;
    private String name;
    private String description;
    private String colour;
    private String category;
    private Double price;
    private Integer stockQuantity;
    private Integer reorderLevel;
    private ArrayList<String> image;
    private ArrayList<String> supplierIDs;         // one or more suppliers, e.g. S-001 (SR-1)
    private boolean isActive;

    private String lastAlertMessage;               // the most recent low-stock alert (SR-13)

    public Product(String name, String colour, String category, Double price, Integer quantity) {
        this.name = name;
        this.colour = colour;
        this.category = category;
        this.price = price;
        this.stockQuantity = quantity;
        this.image = new ArrayList<>();
        this.supplierIDs = new ArrayList<>();
        this.isActive = false;                     // becomes active once addProduct() succeeds
    }

    // ----- Methods from the class diagram -----

    /** SR-1, SR-2, SR-4: saves the product only if every required field is filled in, and gives it a unique ID. */
    public boolean addProduct() {
        if (productID != null) {
            return false;                          // already added
        }
        if (!hasAllRequiredFields()) {
            return false;                          // refuse while a required field is empty
        }
        productID = String.format("P%03d", nextNumber++);
        isActive = true;
        return true;
    }

    /** SR-3, SR-4: confirms an edit. Refuses (throws) while a required field is empty. */
    public void editProduct() {
        if (!hasAllRequiredFields()) {
            throw new IllegalStateException("Please complete all required fields.");
        }
    }

    /** SR-7: takes the product out of the catalogue. Its details are kept for past orders (SR-8). */
    public boolean removeProduct() {
        if (!isActive) {
            return false;
        }
        isActive = false;
        return true;
    }

    /** SR-9: only an active product with enough stock can be ordered. */
    public boolean checkAvailability(Integer quantity) {
        return isActive && quantity != null && quantity > 0 && stockQuantity >= quantity;
    }

    /**
     * SR-10, SR-11: reduces stock when an order is confirmed (negative change)
     * and restores it when an order is cancelled or returned (positive change).
     */
    public void updateStock(Integer quantityChange) {
        int newQuantity = stockQuantity + quantityChange;
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Stock cannot go below 0.");
        }
        stockQuantity = newQuantity;
        if (quantityChange < 0 && checkLowStock()) {
            sendLowStockAlert();                   // SR-13
        }
    }

    /** SR-13: true when stock has reached or fallen below the reorder level. */
    public boolean checkLowStock() {
        return reorderLevel != null && stockQuantity <= reorderLevel;
    }

    /** SR-13: notifies the authorised user. The shared Notification class will send the real message. */
    public void sendLowStockAlert() {
        lastAlertMessage = "Low stock: " + name + " (" + productID + ") has " + stockQuantity
                + " left (reorder level " + reorderLevel + ").";
        // TODO: pass lastAlertMessage to the group's Notification class once it exists.
    }

    // ----- Small helpers (getters and setters are not shown on the class diagram) -----

    /** SR-12: the catalogue shows "Out of Stock" when stock reaches 0. */
    public boolean isOutOfStock() {
        return stockQuantity == 0;
    }

    /** Adds a photo path. A product can have at most 5 photos (A-3). */
    public void addImage(String imagePath) {
        if (image.size() >= MAX_IMAGES) {
            throw new IllegalStateException("A product can have at most " + MAX_IMAGES + " images.");
        }
        image.add(imagePath);
    }

    /** Links the product to a supplier, e.g. "S-001". A product can have more than one. */
    public void addSupplierID(String supplierID) {
        if (supplierID != null && !supplierID.trim().isEmpty() && !supplierIDs.contains(supplierID.trim())) {
            supplierIDs.add(supplierID.trim());
        }
    }

    private boolean hasAllRequiredFields() {
        return notBlank(name) && notBlank(colour) && notBlank(category) && notBlank(description)
                && price != null && price >= 0
                && stockQuantity != null && stockQuantity >= 0
                && reorderLevel != null && reorderLevel >= 0
                && !image.isEmpty()                // a photo is required
                && !supplierIDs.isEmpty();         // at least one Supplier ID is required
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String getProductID() { return productID; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getColour() { return colour; }
    public void setColour(String colour) { this.colour = colour; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getStockQuantity() { return stockQuantity; }
    public Integer getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(Integer reorderLevel) { this.reorderLevel = reorderLevel; }
    public ArrayList<String> getImage() { return image; }
    public ArrayList<String> getSupplierIDs() { return supplierIDs; }
    public boolean isActive() { return isActive; }
    public String getLastAlertMessage() { return lastAlertMessage; }
}