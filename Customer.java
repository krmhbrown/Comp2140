
import java.util.ArrayList;
import java.util.Objects;

/**
 * Customer class for the Peak Finds website.
 *
 * This class handles customer registration,
 * account information, delivery addresses,
 * account deletion and order history.
 *
 * It belongs to the Business Logic Layer.
 */
public class Customer {

    // Unique identifier for the customer.
    private String customerID;

    // Customer's personal information.
    private String name;
    private String email;
    private String phoneNumber;
    private String password;
    private String deliveryAddress;

    // Indicates whether the account is active.
    private boolean isActive;

    // Provides access to the shared database.
    private final CustomerDataAccess database;

    // Provides secure password hashing.
    private final PasswordEncoder passwordEncoder;


    /**
     * Constructor: Creates a new Customer object.
     *
     * The database generates the unique customer ID
     * when the account is successfully registered.
     */
    public Customer(String name, String email,
                    String phoneNumber, String password,
                    CustomerDataAccess database,
                    PasswordEncoder passwordEncoder) {

        // Save the database connection.
        this.database = Objects.requireNonNull(database);

        // Save the password encoder.
        this.passwordEncoder =
                Objects.requireNonNull(passwordEncoder);

        // Validate and store the customer details.
        this.name = requireText(name, "Name");
        this.email = validateEmail(email);
        this.phoneNumber =
                requireText(phoneNumber, "Phone number");

        // Keep the password only until registration.
        this.password = requireText(password, "Password");

        // Initially, the customer has no delivery address.
        this.deliveryAddress = "";

        // New customer accounts are active.
        this.isActive = true;

        // The database assigns the ID during registration.
        this.customerID = null;
    }


    /**
     * Creates a new customer account.
     *
     * Returns true when registration succeeds.
     */
    public boolean createAccount() {

        // Prevent an existing customer from registering twice.
        if (customerID != null) {
            return false;
        }

        // Make sure the email is not already registered.
        if (database.emailExists(email)) {
            return false;
        }

        // Hash the password before saving it.
        String encodedPassword = passwordEncoder.encode(password);

        // Ask the database to save the new customer.
        String newID = database.createCustomer(
                name,
                email,
                phoneNumber,
                encodedPassword,
                isActive
        );

        // Check whether registration succeeded.
        if (newID == null || newID.isBlank()) {
            return false;
        }

        // Store the unique ID assigned by the database.
        customerID = newID;

        // Stop retaining the plaintext password.
        password = null;

        return true;
    }


    /**
     * Retrieves the customer's saved information.
     *
     * The password must not be included in the result.
     */
    public String viewDetails() {

        // Check that the account exists and is active.
        if (customerID == null || !isActive) {
            return "Customer account is unavailable.";
        }

        // Request the customer's information.
        return database.getCustomerDetails(customerID);
    }


    /**
     * Allows the customer to edit their account details.
     */
    public boolean editDetails(String newName,
                               String newEmail,
                               String newPhoneNumber) {

        // Prevent changes to unavailable accounts.
        if (customerID == null || !isActive) {
            return false;
        }

        // Check the new information before saving it.
        String validName = requireText(newName, "Name");
        String validEmail = validateEmail(newEmail);
        String validPhone = requireText(
                newPhoneNumber, "Phone number"
        );

        // Prevent customers from using another account's email.
        if (database.emailExistsForOtherCustomer(
                validEmail, customerID)) {
            return false;
        }

        // Save the changes in the database.
        boolean updated = database.updateCustomer(
                customerID,
                validName,
                validEmail,
                validPhone
        );

        // Update the object only if saving succeeds.
        if (updated) {
            name = validName;
            email = validEmail;
            phoneNumber = validPhone;
        }

        return updated;
    }


    /**
     * Saves the customer's delivery address.
     */
    public boolean saveDeliveryAddress(String address) {

        // Only active customers can update their address.
        if (customerID == null || !isActive) {
            return false;
        }

        // Make sure the address is not empty.
        String validAddress =
                requireText(address, "Delivery address");

        // Save the new address in the database.
        boolean saved = database.updateDeliveryAddress(
                customerID, validAddress
        );

        // Update the local object after saving succeeds.
        if (saved) {
            deliveryAddress = validAddress;
        }

        return saved;
    }


    /**
     * Checks whether the customer has an active order.
     *
     * Customers with active orders cannot delete their accounts.
     */
    public boolean hasActiveOrders() {

        // An unsaved customer has no recorded orders.
        if (customerID == null) {
            return false;
        }

        // Request the active-order check from the database.
        return database.hasActiveOrders(customerID);
    }


    /**
     * Deletes the account after customer confirmation.
     *
     * The database must preserve historical order records
     * while removing the customer's personal information.
     */
    public boolean deleteAccount(boolean confirmed) {

        // Refuse deletion without confirmation.
        if (!confirmed) {
            return false;
        }

        // Check whether the account is available.
        if (customerID == null || !isActive) {
            return false;
        }

        // Customers cannot delete accounts with active orders.
        if (hasActiveOrders()) {
            return false;
        }

        // Request safe account anonymisation.
        boolean deleted =
                database.anonymizeCustomer(customerID);

        // Clear the customer's personal information.
        if (deleted) {
            name = null;
            email = null;
            phoneNumber = null;
            password = null;
            deliveryAddress = null;
            isActive = false;
        }

        return deleted;
    }


    /**
     * Original no-argument method from the SDS.
     *
     * Deletion is refused until confirmation is provided.
     */
    public boolean deleteAccount() {
        return deleteAccount(false);
    }


    /**
     * Retrieves the customer's order history.
     */
    public ArrayList<String> viewOrderHistory() {

        // Return an empty list if the account is not saved.
        if (customerID == null) {
            return new ArrayList<>();
        }

        // Retrieve orders belonging to this customer.
        return database.getOrderHistory(customerID);
    }


    /**
     * Checks that a required field contains text.
     */
    private String requireText(String value, String fieldName) {

        // Reject empty or missing information.
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be empty."
            );
        }

        return value.trim();
    }


    /**
     * Checks that the email has a basic valid format.
     */
    private String validateEmail(String value) {

        String validEmail = requireText(value, "Email");

        // Reject obviously invalid email addresses.
        if (!validEmail.matches(
                "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "Please enter a valid email address."
            );
        }

        return validEmail.toLowerCase(java.util.Locale.ROOT);
    }


    // Returns the customer's unique identifier.
    public String getCustomerID() {
        return customerID;
    }

    // Returns the customer's name.
    public String getName() {
        return name;
    }

    // Returns the customer's email address.
    public String getEmail() {
        return email;
    }

    // Returns the customer's phone number.
    public String getPhoneNumber() {
        return phoneNumber;
    }

    // Returns the customer's delivery address.
    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    // Returns the customer's account status.
    public boolean isActive() {
        return isActive;
    }


    /**
     * Defines the database operations needed by Customer.
     *
     * These methods must be connected to the shared database.
     */
    public interface CustomerDataAccess {

        boolean emailExists(String email);

        boolean emailExistsForOtherCustomer(
                String email, String customerID
        );

        String createCustomer(
                String name,
                String email,
                String phoneNumber,
                String encodedPassword,
                boolean isActive
        );

        String getCustomerDetails(String customerID);

        boolean updateCustomer(
                String customerID,
                String name,
                String email,
                String phoneNumber
        );

        boolean updateDeliveryAddress(
                String customerID,
                String address
        );

        boolean hasActiveOrders(String customerID);

        boolean anonymizeCustomer(String customerID);

        ArrayList<String> getOrderHistory(String customerID);
    }


    /**
     * Defines password hashing functionality.
     *
     * The group's authentication implementation must
     * provide a secure password-hashing algorithm.
     */
    public interface PasswordEncoder {

        String encode(String password);
    }
}
