
package peakfinds.services;

import java.util.Map;
import peakfinds.auth.AdminAuth;
import peakfinds.auth.ValidationException;
import peakfinds.auth.Validator;
import peakfinds.database.Database;

public class CustomerService {

    private final Database db;
    private final AdminAuth auth;

    public CustomerService(Database db, AdminAuth auth) {
        this.db = db;
        this.auth = auth;
    }

    // Customer information displayed on the account page
    public record CustomerDetails(
            String name,
            String email,
            String phoneNumber,
            String deliveryAddress) {
    }

    // Register a new customer
    public long registerCustomer(
            String name,
            String email,
            String password,
            String phoneNumber,
            String deliveryAddress) {

        String cleanName = Validator.clean(name);
        String cleanPhone = Validator.clean(phoneNumber);
        String cleanAddress = Validator.clean(deliveryAddress);

        if (cleanName.isEmpty()) {
            throw new ValidationException("Customer name is required.");
        }

        if (cleanPhone.isEmpty()) {
            throw new ValidationException("Phone number is required.");
        }

        return db.transaction(() -> {

            long userId = auth.createUser(
                    email, password, "customer");

            return db.insert(
                    "INSERT INTO customers " +
                    "(user_id, name, phone_number, delivery_address) " +
                    "VALUES (?, ?, ?, ?)",
                    userId,
                    cleanName,
                    cleanPhone,
                    cleanAddress
            );
        });
    }

    // Retrieve the logged-in customer's information
    public CustomerDetails getCustomer(long customerId) {

        Map<String, Object> row = db.queryOne(
                "SELECT c.name, u.email, c.phone_number, " +
                "c.delivery_address " +
                "FROM customers c " +
                "JOIN users u ON c.user_id = u.user_id " +
                "WHERE c.customer_id = ? " +
                "AND c.is_active = 1 AND u.is_active = 1",
                customerId
        ).orElseThrow(() ->
                new ValidationException("Customer account not found.")
        );

        return new CustomerDetails(
                value(row, "name"),
                value(row, "email"),
                value(row, "phone_number"),
                value(row, "delivery_address")
        );
    }

    // Update the logged-in customer's information
    public void updateCustomer(
            long customerId,
            String name,
            String email,
            String phoneNumber,
            String deliveryAddress) {

        String cleanName = Validator.clean(name);
        String cleanEmail = Validator.email(email);
        String cleanPhone = Validator.clean(phoneNumber);
        String cleanAddress = Validator.clean(deliveryAddress);

        if (cleanName.isEmpty()) {
            throw new ValidationException("Customer name is required.");
        }

        if (cleanPhone.isEmpty()) {
            throw new ValidationException("Phone number is required.");
        }

        db.transaction(() -> {

            Map<String, Object> customer = db.queryOne(
                    "SELECT c.user_id " +
                    "FROM customers c " +
                    "JOIN users u ON c.user_id = u.user_id " +
                    "WHERE c.customer_id = ? " +
                    "AND c.is_active = 1 AND u.is_active = 1",
                    customerId
            ).orElseThrow(() ->
                    new ValidationException("Customer account not found.")
            );

            long userId =
                    ((Number) customer.get("user_id")).longValue();

            // Prevent another customer's email from being used
            if (db.queryOne(
                    "SELECT user_id FROM users " +
                    "WHERE email = ? AND user_id <> ?",
                    cleanEmail, userId
            ).isPresent()) {
                throw new ValidationException(
                        "This email address is already registered.");
            }

            // Update the login email
            db.update(
                    "UPDATE users SET email = ? WHERE user_id = ?",
                    cleanEmail, userId
            );

            // Update customer details
            db.update(
                    "UPDATE customers " +
                    "SET name = ?, phone_number = ?, " +
                    "delivery_address = ? " +
                    "WHERE customer_id = ?",
                    cleanName,
                    cleanPhone,
                    cleanAddress,
                    customerId
            );

            return null;
        });
    }

    private static String value(
            Map<String, Object> row, String column) {

        Object result = row.get(column);
        return result == null ? "" : result.toString();
    }
}
