package peakfinds.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import peakfinds.web.Router;

/**
 * Remembers who is logged in. After a successful login the server stores the Session here under a long
 * random token and gives the browser that token in a cookie. Sessions expire after a period of no activity
 * or after a maximum total lifetime, and are lost if the server restarts (users simply log in again).
 */
public final class SessionManager {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration MAX_LIFETIME = Duration.ofHours(12);

    private static final class Entry {
        final Session session;
        final long createdMs = System.currentTimeMillis();
        volatile long lastSeenMs = createdMs;

        Entry(Session session) {
            this.session = session;
        }
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final long idleTimeoutMs;

    public SessionManager(Duration idleTimeout) {
        this.idleTimeoutMs = idleTimeout.toMillis();
    }

    /** Stores the session and returns the new random token for the cookie (a fresh token on every login). */
    public String create(Session session) {
        purgeExpired();
        String token = randomToken();
        entries.put(token, new Entry(session));
        return token;
    }

    /** Finds the session for a token, or null if unknown or expired. Counts as activity. */
    public Session get(String token) {
        Entry e = entries.get(token);
        if (e == null) {
            return null;
        }
        if (expired(e, System.currentTimeMillis())) {
            entries.remove(token);
            return null;
        }
        e.lastSeenMs = System.currentTimeMillis();
        return e.session;
    }

    /** Logs the token out. */
    public void destroy(String token) {
        if (token != null) {
            entries.remove(token);
        }
    }

    public void destroyForUser(long userId) {
        entries.values().removeIf(e -> e.session.userId() == userId);
    }

    /** A random unguessable value (also used for anti-forgery tokens). */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The Set-Cookie header that stores the token in the browser. */
    public static String cookieHeader(String token, boolean secure) {
        return Router.SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax" + (secure ? "; Secure" : "");
    }

    /** The Set-Cookie header that deletes the cookie. */
    public static String clearCookieHeader(boolean secure) {
        return Router.SESSION_COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax" + (secure ? "; Secure" : "");
    }

    private boolean expired(Entry e, long now) {
        return now - e.lastSeenMs > idleTimeoutMs || now - e.createdMs > MAX_LIFETIME.toMillis();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        entries.values().removeIf(e -> expired(e, now));
    }
}
