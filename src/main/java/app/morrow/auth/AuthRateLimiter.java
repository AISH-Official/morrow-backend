package app.morrow.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {
    private final ConcurrentHashMap<String, ArrayDeque<Instant>> attempts = new ConcurrentHashMap<>();

    public void check(String key) { check(key, 8); }

    public void check(String key, int maximum) {
        var now = Instant.now();
        var bucket = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst().isBefore(now.minusSeconds(60))) bucket.removeFirst();
            if (bucket.size() >= maximum) throw new TooManyAttemptsException();
            bucket.addLast(now);
        }
    }

    public static class TooManyAttemptsException extends RuntimeException {
        public TooManyAttemptsException() { super("잠시 후 다시 시도해 주세요."); }
    }
}
