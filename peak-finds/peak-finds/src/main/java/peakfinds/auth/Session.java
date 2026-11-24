package peakfinds.auth;

/**
 * Who is logged in. Feature code reads this from request.session():
 * role() for what they may do, customerId() / bearerId() for which records are theirs.
 * customerId is null unless the user is a customer; bearerId is null unless the user is a bearer.
 * csrfToken is the secret that must be sent back with every form (see Layout.csrfField).
 */
public record Session(
        long userId,
        String email,
        String role,
        String displayName,
        Long customerId,
        Long bearerId,
        String csrfToken) {}
