package peakfinds.web;

/** Thrown when a request is malformed (bad number in the URL, body too large). The router answers 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
