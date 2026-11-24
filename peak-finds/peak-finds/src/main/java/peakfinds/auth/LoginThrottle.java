package peakfinds.auth;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Slows down password guessing: after 5 failed logins for one email within 10 minutes, further
 * attempts for that email are refused until the oldest failure is 10 minutes old.
 */
final class LoginThrottle {
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final int MAX_TRACKED = 10_000;

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /** True if this email has had too many recent failures. */
    boolean isBlocked(String key) {
        Deque<Long> times = failures.get(key);
        if (times == null) {
            return false;
        }
        synchronized (times) {
            prune(times);
            return times.size() >= MAX_FAILURES;
        }
    }

    /** Remembers a failed login. */
    void recordFailure(String key) {
        if (failures.size() > MAX_TRACKED) {
            failures.clear();   // protects memory if someone floods the form with made-up emails
        }
        Deque<Long> times = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            prune(times);
            times.addLast(System.currentTimeMillis());
        }
    }

    /** Forgets the failures after a successful login. */
    void clear(String key) {
        failures.remove(key);
    }

    private static void prune(Deque<Long> times) {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!times.isEmpty() && times.peekFirst() < cutoff) {
            times.removeFirst();
        }
    }
}
