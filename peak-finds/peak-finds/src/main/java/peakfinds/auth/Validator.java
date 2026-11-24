package peakfinds.auth;

import java.util.regex.Pattern;

/**
 * Data Validation (SDS Authentication layer). Each method checks one kind of input and either returns
 * the cleaned value or throws ValidationException with a friendly message.
 * Use it on every form field before saving anything. (Database calls also use "?" parameters, and pages
 * escape output with Html.esc, which together stop injection and XSS.)
 */
public final class Validator {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ()-]{7,20}$");

    private Validator() {}

    /** Trims the text; null becomes an empty string. */
    public static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    /** The value must not be empty. */
    public static String required(String value, String label) {
        String v = clean(value);
        if (v.isEmpty()) {
            throw new ValidationException(label + " is required.");
        }
        return v;
    }

    /** The value must be present and at most max characters. */
    public static String text(String value, String label, int max) {
        String v = required(value, label);
        if (v.length() > max) {
            throw new ValidationException(label + " must be " + max + " characters or fewer.");
        }
        return v;
    }

    /** A valid-looking email address, returned in lower case. */
    public static String email(String value) {
        String v = required(value, "Email").toLowerCase();
        if (v.length() > 254 || !EMAIL.matcher(v).matches()) {
            throw new ValidationException("Enter a valid email address.");
        }
        return v;
    }

    /** A phone number with 7 to 15 digits (spaces, +, ( ) and - are allowed). */
    public static String phone(String value) {
        String v = required(value, "Phone number");
        long digits = v.chars().filter(Character::isDigit).count();
        if (!PHONE.matcher(v).matches() || digits < 7 || digits > 15) {
            throw new ValidationException("Enter a valid phone number.");
        }
        return v;
    }

    /** A new password: 8 to 128 characters. */
    public static String newPassword(String value) {
        if (value == null || value.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters.");
        }
        if (value.length() > 128) {
            throw new ValidationException("Password must be 128 characters or fewer.");
        }
        return value;
    }

    /** A whole number within a range. */
    public static int intInRange(String value, String label, int min, int max) {
        try {
            int n = Integer.parseInt(clean(value));
            if (n < min || n > max) {
                throw new ValidationException(label + " must be between " + min + " and " + max + ".");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new ValidationException(label + " must be a whole number.");
        }
    }

    /** One of the allowed values (for example a status or payment method from Constants). */
    public static String oneOf(String value, String label, Iterable<String> allowed) {
        String v = clean(value);
        for (String a : allowed) {
            if (a.equals(v)) {
                return v;
            }
        }
        throw new ValidationException("Choose a valid " + label + ".");
    }
}
