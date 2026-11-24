
import java.util.ArrayList;
import java.util.Objects;

/**
 * CustomerUI handles customer-facing operations
 * for the Peak Finds website.
 *
 * Customers can browse products, view their cart,
 * check out, track orders and manage their accounts.
 *
 * This class belongs to the User Interface Layer.
 */
public class CustomerUI {

    // Stores the unique customer identifier.
    private String customerID;

    // Provides access to the business logic.
    private CustomerUIService service;

    // Provides access to the website display.
    private CustomerUIView view;


    /**
     * Creates the customer interface for a specific customer.
     */
    public CustomerUI(String customerID) {

        // Check that a customer ID was provided.
        if (customerID == null || customerID.isBlank()) {
            throw new IllegalArgumentException(
                    "A valid customer ID is required."
            );
        }

        // Store the customer's identifier.
        this.customerID = customerID;
    }


    /**
     * Connects the interface to the backend services.
     */
    public void setService(CustomerUIService service) {
        this.service = Objects.requireNonNull(service);
    }


    /**
     * Connects the interface to the website pages.
     */
    public void setView(CustomerUIView view) {
        this.view = Objects.requireNonNull(view);
    }


    /**
     * Retrieves and displays the product catalogue.
     */
    public ArrayList<String> viewCatalogue() {

        // Check that both connections are available.
        checkConnections();

        // Retrieve products through Product Management.
        ArrayList<String> products = service.getCatalogue();

        // Display products on the catalogue page.
        view.showCatalogue(products);

        return products;
    }


    /**
     * Displays information about a selected product.
     */
    public void viewProduct(String productID) {

        checkConnections();

        // Ensure the product identifier is valid.
        if (productID == null || productID.isBlank()) {
            throw new IllegalArgumentException(
                    "A valid product ID is required."
            );
        }

        // Retrieve product details from Product Management.
        String details = service.getProductDetails(productID);

        // Display the selected product.
        view.showProduct(details);
    }


    /**
     * Displays the customer's current shopping cart.
     */
    public void viewCart() {

        checkConnections();

        // Retrieve the customer's cart information.
        String details = service.getCartDetails(customerID);

        // Display the cart contents and total.
        view.showCart(details);
    }


    /**
     * Displays the checkout page.
     *
     * Customers may select Cash on Delivery or Bank Transfer.
     * Cart and Order Management handle the actual processing.
     */
    public void checkout() {

        checkConnections();

        // Retrieve the checkout information.
        String details = service.getCheckoutDetails(customerID);

        // Display the checkout page.
        view.showCheckout(details);
    }


    /**
     * Displays the current status of a customer's order.
     */
    public void trackOrder(String orderID) {

        checkConnections();

        // Ensure an order identifier was entered.
        if (orderID == null || orderID.isBlank()) {
            throw new IllegalArgumentException(
                    "A valid order ID is required."
            );
        }

        // Retrieve the order from Order Management.
        // The backend must verify the customer's access.
        String details = service.getOrderDetails(
                customerID, orderID
        );

        // Display the order information.
        view.showOrder(details);
    }


    /**
     * Requests cancellation of an order.
     *
     * The cancellation must first be confirmed.
     */
    public boolean cancelOrder(String orderID) {

        checkConnections();

        // Check that the order identifier was provided.
        if (orderID == null || orderID.isBlank()) {
            throw new IllegalArgumentException(
                    "A valid order ID is required."
            );
        }

        // Display the cancellation confirmation.
        view.showCancellationConfirmation(orderID);

        // No cancellation has occurred yet.
        return false;
    }


    /**
     * Processes cancellation after confirmation.
     */
    public boolean confirmCancellation(
            String orderID, boolean confirmed) {

        checkConnections();

        // Refuse cancellation if confirmation was not given.
        if (!confirmed) {
            return false;
        }

        // Request cancellation through Order Management.
        boolean cancelled = service.cancelCustomerOrder(
                customerID, orderID
        );

        // Display whether cancellation succeeded.
        view.showCancellationResult(cancelled);

        return cancelled;
    }


    /**
     * Displays the customer's account management page.
     *
     * The customer can view account information
     * and access the account management options.
     */
    public void managementAccount() {

        checkConnections();

        // Retrieve account information from Customer Management.
        String details = service.getAccountDetails(customerID);

        // Display the account information.
        view.showAccount(details);
    }


    /**
     * Checks that the required connections exist.
     */
    private void checkConnections() {

        if (service == null) {
            throw new IllegalStateException(
                    "Customer service is not connected."
            );
        }

        if (view == null) {
            throw new IllegalStateException(
                    "Customer website view is not connected."
            );
        }
    }


    /**
     * Operations that the backend must provide.
     *
     * These will connect to the group's Customer,
     * Cart, Product and Order implementations.
     */
    public interface CustomerUIService {

        ArrayList<String> getCatalogue();

        String getProductDetails(String productID);

        String getCartDetails(String customerID);

        String getCheckoutDetails(String customerID);

        String getOrderDetails(
                String customerID, String orderID
        );

        boolean cancelCustomerOrder(
                String customerID, String orderID
        );

        String getAccountDetails(String customerID);
    }


    /**
     * Operations supplied by the website's view layer.
     *
     * The actual implementation depends on the Java
     * web framework chosen by the group.
     */
    public interface CustomerUIView {

        void showCatalogue(ArrayList<String> products);

        void showProduct(String details);

        void showCart(String details);

        void showCheckout(String details);

        void showOrder(String details);

        void showCancellationConfirmation(String orderID);

        void showCancellationResult(boolean cancelled);

        void showAccount(String details);
    }
}
