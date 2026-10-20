package com.courtbook.gateway;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Counts requests per key in fixed time windows. In-memory, so it only works
 * with one gateway instance, which is all this app runs. With more instances
 * the counts would move to Redis.
 */
public class FixedWindowRateLimiter {

    private final int limit;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();

    public FixedWindowRateLimiter(int limit, long windowMillis, LongSupplier clock) {
        if (limit <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Returns true if this request is allowed. */
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        long windowStart = now - (now % windowMillis);
        Window w = windows.compute(key, (k, existing) ->
                existing == null || existing.start != windowStart ? new Window(windowStart, 1) : existing.increment());
        if (calls.incrementAndGet() % 1024 == 0) {
            evictOlderThan(windowStart);
        }
        return w.count <= limit;
    }

    /** Milliseconds until the current window ends; used for the Retry-After header. */
    public long millisUntilReset() {
        long now = clock.getAsLong();
        return windowMillis - (now % windowMillis);
    }

    int trackedKeys() {
        return windows.size();
    }

    private void evictOlderThan(long windowStart) {
        windows.entrySet().removeIf(e -> e.getValue().start < windowStart);
    }

    private record Window(long start, int count) {
        Window increment() {
            return new Window(start, count + 1);
        }
    }
}
