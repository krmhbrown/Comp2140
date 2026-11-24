package peakfinds.auth;

import java.util.Map;
import java.util.Optional;
import peakfinds.Constants;
import peakfinds.database.Database;
import peakfinds.database.Rows;

/**
 * Login verification (SDS class "AdminAuth"; the SDS component table calls it AuthService, same thing).
 * It checks an email and password against the users table and builds the Session for a valid login.
 * It also creates login accounts, which the customer sign-up and add-bearer features call.
 */
public final class AdminAuth {
    /** Checked when the email is unknown, so a wrong email takes as long as a wrong password. */
    private static final String DUMMY_HASH = PasswordUtil.hash("not-a-real-password");

    private final Database db;

    public AdminAuth(Database db) {
        this.db = db;
    }

    /**
     * Returns the Session for a correct email and password, or empty for anything else.
     * Wrong email, wrong password and deactivated accounts all give the same empty answer, so an attacker
     * cannot learn which emails have accounts.
     */
    public Optional<Session> authenticateUser(String email, String password) {
        String normalised = Validator.clean(email).toLowerCase();
        Optional<Map<String, Object>> found = db.queryOne(
                "SELECT u.user_id, u.email, u.password_hash, u.role, u.is_active, "
                        + "c.customer_id, c.name AS customer_name, c.is_active AS customer_active, "
                        + "b.bearer_id, b.name AS bearer_name, b.is_active AS bearer_active "
                        + "FROM users u "
                        + "LEFT JOIN customers c ON c.user_id = u.user_id "
                        + "LEFT JOIN bearers b ON b.user_id = u.user_id "
                        + "WHERE u.email = ?", normalised);

        String storedHash = found.map(r -> Rows.str(r, "password_hash")).orElse(DUMMY_HASH);
        boolean passwordOk = PasswordUtil.verify(password, storedHash);
        if (found.isEmpty() || !passwordOk || !isActive(found.get())) {
            return Optional.empty();
        }
        return Optional.of(buildSession(found.get()));
    }

    /** Ends a login by removing its session. */
    public void logOut(SessionManager sessions, String token) {
        sessions.destroy(token);
    }

    /**
     * Creates a login account and returns its user ID. Used by customer sign-up and "add bearer"
     * (call it inside db.transaction so the account and its customer/bearer row are saved together).
     */
    public long createUser(String email, String password, String role) {
        String cleanEmail = Validator.email(email);
        Validator.newPassword(password);
        Validator.oneOf(role, "role", Constants.ROLES);
        if (db.queryOne("SELECT 1 AS found FROM users WHERE email = ?", cleanEmail).isPresent()) {
            throw new ValidationException("An account with that email already exists.");
        }
        return db.insert("INSERT INTO users (email, password_hash, role) VALUES (?, ?, ?)",
                cleanEmail, PasswordUtil.hash(password), role);
    }

    private static boolean isActive(Map<String, Object> row) {
        if (!Rows.bool(row, "is_active")) {
            return false;
        }
        // A deleted customer or deactivated bearer can no longer sign in.
        if (Rows.longOrNull(row, "customer_id") != null && !Rows.bool(row, "customer_active")) {
            return false;
        }
        return Rows.longOrNull(row, "bearer_id") == null || Rows.bool(row, "bearer_active");
    }

    private static Session buildSession(Map<String, Object> row) {
        String name = Rows.str(row, "customer_name");
        if (name == null) {
            name = Rows.str(row, "bearer_name");
        }
        if (name == null) {
            name = "Owner";
        }
        return new Session(
                Rows.longOf(row, "user_id"),
                Rows.str(row, "email"),
                Rows.str(row, "role"),
                name,
                Rows.longOrNull(row, "customer_id"),
                Rows.longOrNull(row, "bearer_id"),
                SessionManager.randomToken());
    }
}
