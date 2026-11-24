
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Objects;
import java.math.BigDecimal;

/**
 * The Cart class represents a customer's shopping cart
 * in the Peak Finds Order, Inventory and Delivery Management System.
 *
 * It allows customers to add products, remove products,
 * update quantities, calculate subtotals, calculate the
 * total cost, select a payment method and proceed to checkout.
 *
 * The class belongs to the Business Logic Layer.
 *
 * It communicates with Product Management to retrieve
 * product information and verify stock availability.
 *
 * The cart does not process payments or directly update
 * product stock quantities. These operations are handled
 * by the appropriate business functionality.
 */
public class Cart {

    // Stores the unique identifier assigned to this shopping cart.
    private String cartID;

    // Stores the unique ID of the customer who owns the cart.
    private String customerID;

    /*
     * Stores the products currently in the shopping cart.
     *
     * The key is the unique product ID.
     * The value is the quantity selected by the customer.
     *
     * For example:
     * "P001" -> 2
     * "P002" -> 1
     *
     * This means the customer has selected two units
     * of product P001 and one unit of product P002.
     */
    private HashMap<String, Integer> item;

    // Stores the total monetary value of all products in the cart.
    private Double totalAmount;

    // Stores the payment method selected by the customer.
    private String paymentMethod;

    /*
     * Provides access to information about products.
     *
     * The implementation must retrieve current product
     * information from the shared system rather than
     * using outdated or manually entered prices.
     */
    private ProductCatalogue productCatalogue;


    /**
     * Constructs a shopping cart for a particular customer.
     *
     * A customer may have one active shopping cart at a time.
     * The customer ID associates the cart with the correct account.
     *
     * @param customerID The unique ID of the customer.
     */
    public Cart(String customerID) {

        // Check whether a valid customer identifier was provided.
        if (customerID == null || customerID.isBlank()) {

            // Prevent creation of a cart without a customer.
            throw new IllegalArgumentException(
                    "A valid customer ID is required."
            );
        }

        // Associate the shopping cart with the customer.
        this.customerID = customerID;

        // Initialise the product collection as empty.
        this.item = new HashMap<>();

        // Initialise the cart total to zero.
        this.totalAmount = 0.0;

        // No payment method has been selected yet.
        this.paymentMethod = null;

        /*
         * The cart ID must eventually be assigned through
         * the shared database to ensure uniqueness.
         *
         * It is initially null because the cart has
         * not yet been saved to the database.
         */
        this.cartID = null;
    }


    /**
     * Connects the cart to the product catalogue.
     *
     * The product catalogue is responsible for providing
     * current product prices, names and stock quantities.
     *
     * @param productCatalogue The product information provider.
     */
    public void setProductCatalogue(
            ProductCatalogue productCatalogue) {

        // Prevent a missing product catalogue connection.
        this.productCatalogue =
                Objects.requireNonNull(productCatalogue);
    }


    /**
     * Adds a selected product to the shopping cart.
     *
     * SR-1: Customers must be able to add available products.
     * SR-3: Customers cannot select more than the available stock.
     *
     * The method verifies that the selected quantity is positive,
     * that the product exists and that enough stock is available.
     *
     * If the product is already in the cart, the new quantity
     * is added to the quantity previously selected.
     *
     * @param productID The unique ID of the selected product.
     * @param quantity The number of units to add.
     * @return true if the product is successfully added.
     */
    public boolean addProduct(String productID, int quantity) {

        // Reject a missing or empty product identifier.
        if (productID == null || productID.isBlank()) {
            return false;
        }

        // Customers must select at least one unit.
        if (quantity <= 0) {
            return false;
        }

        // Retrieve the product's current information.
        ProductInfo product = getProduct(productID);

        // Reject the request if the product does not exist.
        if (product == null) {
            return false;
        }

        // Prevent inactive products from being added to the cart.
        if (!product.isActive()) {
            return false;
        }

        // Retrieve the quantity already selected for this product.
        // If the product is not in the cart, the quantity is zero.
        int currentQuantity = item.getOrDefault(productID, 0);

        /*
         * Calculate the new quantity using long arithmetic.
         *
         * This prevents integer overflow when combining
         * the existing quantity with the requested quantity.
         */
        long newQuantity = (long) currentQuantity + quantity;

        // Prevent a quantity greater than the available stock.
        if (newQuantity > product.getStockQuantity()) {
            return false;
        }

        // Save the new quantity in the shopping cart.
        item.put(productID, (int) newQuantity);

        // Recalculate the total after adding the product.
        calculateTotal();

        // The product was successfully added.
        return true;
    }


    /**
     * Removes a selected product from the shopping cart.
     *
     * SR-4: Customers must be able to remove products.
     * SR-8: The cart total must update after product removal.
     *
     * @param productID The ID of the product to remove.
     */
    public void removeProduct(String productID) {

        // Do nothing if the product identifier is invalid.
        if (productID == null || productID.isBlank()) {
            return;
        }

        // Remove the product and its quantity from the cart.
        item.remove(productID);

        // Recalculate the total after removing the product.
        calculateTotal();
    }


    /**
     * Changes the quantity of a product already in the cart.
     *
     * SR-2: Customers can change product quantities.
     * SR-3: Quantities cannot exceed available stock.
     * SR-8: Cart totals must update after quantity changes.
     *
     * @param productID The ID of the selected product.
     * @param quantity The new quantity requested.
     * @return true if the quantity is successfully updated.
     */
    public boolean updateQuantity(
            String productID, int quantity) {

        // Reject an invalid product identifier.
        if (productID == null || productID.isBlank()) {
            return false;
        }

        // Check whether the product is already in the cart.
        if (!item.containsKey(productID)) {
            return false;
        }

        /*
         * If the new quantity is zero, remove the product.
         *
         * This allows customers to remove an item
         * by changing its quantity to zero.
         */
        if (quantity == 0) {

            // Remove the selected product.
            removeProduct(productID);

            // The quantity change was completed successfully.
            return true;
        }

        // Reject negative quantities.
        if (quantity < 0) {
            return false;
        }

        // Retrieve the current product information.
        ProductInfo product = getProduct(productID);

        // Reject products that no longer exist.
        if (product == null) {
            return false;
        }

        // Prevent customers from purchasing inactive products.
        if (!product.isActive()) {
            return false;
        }

        // Prevent quantities greater than available stock.
        if (quantity > product.getStockQuantity()) {
            return false;
        }

        // Replace the existing quantity with the new quantity.
        item.put(productID, quantity);

        // Update the total amount payable.
        calculateTotal();

        // Indicate that the quantity was successfully changed.
        return true;
    }


    /**
     * Calculates the subtotal for a selected product.
     *
     * SR-6: Each product's subtotal must equal
     * its unit price multiplied by its selected quantity.
     *
     * For example:
     *
     * Unit price = J$4,000
     * Quantity = 2
     *
     * Subtotal = 4000 * 2 = J$8,000
     *
     * @param productID The ID of the selected product.
     * @return The subtotal for the selected product.
     */
    public Double calculateSubtotal(String productID) {

        // Return zero if the product is not in the cart.
        if (!item.containsKey(productID)) {
            return 0.0;
        }

        // Retrieve the latest information for the product.
        ProductInfo product = getProduct(productID);

        /*
         * Do not calculate a subtotal using missing
         * product information because the price
         * would be unknown.
         */
        if (product == null) {
            throw new IllegalStateException(
                    "Product information is unavailable."
            );
        }

        // Retrieve the quantity selected by the customer.
        int quantity = item.get(productID);

        /*
         * Convert the product price to BigDecimal.
         *
         * BigDecimal is used for the calculation because
         * ordinary floating-point arithmetic can introduce
         * small rounding errors when working with money.
         */
        BigDecimal price =
                BigDecimal.valueOf(product.getPrice());

        // Multiply the unit price by the selected quantity.
        BigDecimal subtotal =
                price.multiply(BigDecimal.valueOf(quantity));

        // Return the subtotal as the Double required by the SDS.
        return subtotal.doubleValue();
    }


    /**
     * Calculates the total cost of the shopping cart.
     *
     * SR-7: The total must equal the sum of all subtotals.
     * SR-8: The total must update when cart contents change.
     *
     * For example:
     *
     * Product A subtotal = J$8,000
     * Product B subtotal = J$3,000
     *
     * Cart total = 8000 + 3000 = J$11,000
     *
     * @return The total amount payable.
     */
    public Double calculateTotal() {

        // Start the calculation with a total of zero.
        BigDecimal total = BigDecimal.ZERO;

        // Go through every product currently in the cart.
        for (String productID : item.keySet()) {

            // Calculate the subtotal of the current product.
            Double subtotal = calculateSubtotal(productID);

            // Add the subtotal to the running total.
            total = total.add(BigDecimal.valueOf(subtotal));
        }

        // Store the newly calculated total.
        totalAmount = total.doubleValue();

        // Return the total cost of the shopping cart.
        return totalAmount;
    }


    /**
     * Allows the customer to choose a payment method.
     *
     * SR-9: Only Cash on Delivery and Bank Transfer are allowed.
     * SR-10: A payment method must be selected before checkout.
     *
     * The system does not process card or online payments.
     *
     * @param method The payment method selected by the customer.
     * @return true if the payment method is accepted.
     */
    public boolean selectPaymentMethod(String method) {

        // Reject missing or empty payment methods.
        if (method == null || method.isBlank()) {
            return false;
        }

        // Check whether Cash on Delivery was selected.
        if (method.equalsIgnoreCase("Cash on Delivery")) {

            // Save the accepted payment method.
            paymentMethod = "Cash on Delivery";

            return true;
        }

        // Check whether Bank Transfer was selected.
        if (method.equalsIgnoreCase("Bank Transfer")) {

            // Save the accepted payment method.
            paymentMethod = "Bank Transfer";

            return true;
        }

        // Reject all other payment methods.
        return false;
    }


    /**
     * Validates the shopping cart before checkout.
     *
     * The method verifies that:
     *
     * 1. The cart contains at least one product.
     * 2. The customer has selected an accepted payment method.
     * 3. All product quantities are greater than zero.
     * 4. All selected products are active.
     * 5. All selected quantities are within available stock.
     * 6. Product prices are valid.
     *
     * @return true if the shopping cart is valid.
     */
    public boolean validateCart() {

        // A customer cannot check out with an empty cart.
        if (item.isEmpty()) {
            return false;
        }

        // A payment method must be selected before checkout.
        if (paymentMethod == null) {
            return false;
        }

        // Go through each product selected by the customer.
        for (Map.Entry<String, Integer> entry
                : item.entrySet()) {

            // Retrieve the current product identifier.
            String productID = entry.getKey();

            // Retrieve the quantity selected for this product.
            int quantity = entry.getValue();

            // Product quantities must be greater than zero.
            if (quantity <= 0) {
                return false;
            }

            // Retrieve current product information.
            ProductInfo product = getProduct(productID);

            // Prevent checkout if the product no longer exists.
            if (product == null) {
                return false;
            }

            // Prevent checkout if the product is inactive.
            if (!product.isActive()) {
                return false;
            }

            // Prevent checkout when the requested quantity is unavailable.
            if (quantity > product.getStockQuantity()) {
                return false;
            }

            // Prevent checkout if the product price is invalid.
            if (!Double.isFinite(product.getPrice())
                    || product.getPrice() < 0) {
                return false;
            }
        }

        // All required cart conditions have been satisfied.
        return true;
    }


    /**
     * Prepares the shopping cart for order placement.
     *
     * The class diagram specifies that checkout should
     * eventually return an Order object.
     *
     * Until Order Management is integrated, this method
     * returns a CheckoutRequest containing the information
     * required to create the order.
     *
     * No order is saved by this method.
     * No stock is deducted by this method.
     * No payment is processed by this method.
     *
     * @return A validated checkout request.
     */
    public CheckoutRequest checkout() {

        // Validate the cart before allowing checkout to proceed.
        if (!validateCart()) {

            // Stop checkout if any requirement is not satisfied.
            throw new IllegalStateException(
                    "Checkout cannot proceed. "
                    + "Check your cart, stock quantities "
                    + "and payment method."
            );
        }

        // Recalculate the total using current product prices.
        calculateTotal();

        /*
         * Prepare a snapshot of the items being purchased.
         *
         * This prevents later changes to the shopping cart
         * from changing the checkout request.
         */
        HashMap<String, Integer> selectedItems =
                new HashMap<>(item);

        /*
         * Return the customer ID, selected products,
         * total amount and payment method.
         *
         * The order-processing functionality must recheck
         * stock and prices and save the order and stock changes
         * together in one database transaction.
         */
        return new CheckoutRequest(
                customerID,
                selectedItems,
                totalAmount,
                paymentMethod
        );
    }


    /**
     * Retrieves product information using the product identifier.
     *
     * The cart does not maintain a separate product database.
     * Instead, it requests current information from the
     * product catalogue.
     *
     * @param productID The product identifier.
     * @return The current product information.
     */
    private ProductInfo getProduct(String productID) {

        // Check that Product Management has been connected.
        if (productCatalogue == null) {
            throw new IllegalStateException(
                    "Product catalogue is not connected."
            );
        }

        // Retrieve the selected product's information.
        return productCatalogue.getProduct(productID);
    }


    /**
     * Returns the unique identifier assigned to this cart.
     */
    public String getCartID() {
        return cartID;
    }


    /**
     * Returns the identifier of the customer who owns the cart.
     */
    public String getCustomerID() {
        return customerID;
    }


    /**
     * Returns a copy of the products and quantities in the cart.
     *
     * A copy is returned so other classes cannot directly
     * modify the internal product collection without validation.
     */
    public Map<String, Integer> getItems() {

        // Return an unmodifiable copy of the cart contents.
        return Collections.unmodifiableMap(
                new HashMap<>(item)
        );
    }


    /**
     * Returns the current total cost of the shopping cart.
     *
     * The total is recalculated to reflect the current
     * prices of the products selected.
     */
    public Double getTotalAmount() {
        return calculateTotal();
    }


    /**
     * Returns the payment method selected by the customer.
     */
    public String getPaymentMethod() {
        return paymentMethod;
    }


    /**
     * Represents the product information required by the Cart class.
     *
     * The cart requires the product ID, name, price,
     * stock quantity and availability status.
     *
     * This is a temporary integration representation.
     * It does not replace the system's Product class.
     */
    public static class ProductInfo {

        // Stores the unique product identifier.
        private final String productID;

        // Stores the name displayed to customers.
        private final String name;

        // Stores the current unit price of the product.
        private final double price;

        // Stores the number of units available.
        private final int stockQuantity;

        // Indicates whether the product is active.
        private final boolean active;


        /**
         * Creates a product information object.
         */
        public ProductInfo(String productID,
                           String name,
                           double price,
                           int stockQuantity,
                           boolean active) {

            // Store the product identifier.
            this.productID = Objects.requireNonNull(productID);

            // Store the product name.
            this.name = Objects.requireNonNull(name);

            // Prevent invalid product prices.
            if (!Double.isFinite(price) || price < 0) {
                throw new IllegalArgumentException(
                        "Product price must be valid."
                );
            }

            // Prevent negative stock quantities.
            if (stockQuantity < 0) {
                throw new IllegalArgumentException(
                        "Stock quantity cannot be negative."
                );
            }

            // Store the validated unit price.
            this.price = price;

            // Store the available stock quantity.
            this.stockQuantity = stockQuantity;

            // Store the product's availability status.
            this.active = active;
        }


        // Returns the unique product identifier.
        public String getProductID() {
            return productID;
        }

        // Returns the product name.
        public String getName() {
            return name;
        }

        // Returns the product unit price.
        public double getPrice() {
            return price;
        }

        // Returns the number of available units.
        public int getStockQuantity() {
            return stockQuantity;
        }

        // Indicates whether the product is active.
        public boolean isActive() {
            return active;
        }
    }


    /**
     * Defines how the cart retrieves product information.
     *
     * The implementation will use the shared Product
     * functionality and central database.
     */
    public interface ProductCatalogue {

        // Retrieves a product using its unique identifier.
        ProductInfo getProduct(String productID);
    }


    /**
     * Represents the information prepared during checkout.
     *
     * The checkout request contains the customer identifier,
     * selected products, calculated total and payment method.
     *
     * This information will eventually be passed to
     * Order Management to create the customer's order.
     */
    public static class CheckoutRequest {

        // Identifies the customer placing the order.
        private final String customerID;

        // Stores the products and their requested quantities.
        private final Map<String, Integer> items;

        // Stores the total cost calculated during checkout.
        private final Double totalAmount;

        // Stores the customer's selected payment method.
        private final String paymentMethod;


        /**
         * Creates a checkout request containing validated cart details.
         */
        private CheckoutRequest(
                String customerID,
                Map<String, Integer> items,
                Double totalAmount,
                String paymentMethod) {

            // Store the identifier of the customer.
            this.customerID = customerID;

            // Create a protected copy of the selected products.
            this.items = Collections.unmodifiableMap(
                    new HashMap<>(items)
            );

            // Store the calculated total amount.
            this.totalAmount = totalAmount;

            // Store the selected payment method.
            this.paymentMethod = paymentMethod;
        }


        // Returns the customer placing the order.
        public String getCustomerID() {
            return customerID;
        }

        // Returns the selected products and quantities.
        public Map<String, Integer> getItems() {
            return items;
        }

        // Returns the total amount payable.
        public Double getTotalAmount() {
            return totalAmount;
        }

        // Returns the payment method selected at checkout.
        public String getPaymentMethod() {
            return paymentMethod;
        }
    }
}
