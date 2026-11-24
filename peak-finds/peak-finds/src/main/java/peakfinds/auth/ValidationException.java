package peakfinds.auth;

/** Thrown when user input is not acceptable. The message is safe to show to the user (the router shows a 400 page). */
public class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
}
