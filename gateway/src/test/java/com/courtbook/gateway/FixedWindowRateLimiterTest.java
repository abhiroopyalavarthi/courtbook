package com.courtbook.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, 60_000, now::get);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isTrue();
        assertThat(limiter.tryAcquire("1.2.3.4")).isFalse();
    }

    @Test
    void keysAreCountedSeparately() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("a")).isFalse();
        assertThat(limiter.tryAcquire("b")).isTrue();
    }

    @Test
    void newWindowResetsTheCount() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("a");
        }
        now.addAndGet(limiter.millisUntilReset());
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void oldWindowsAreEvicted() {
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("ip-" + i);
        }
        now.addAndGet(120_000);
        for (int i = 0; i < 1024; i++) {
            limiter.tryAcquire("same");
        }
        assertThat(limiter.trackedKeys()).isLessThan(100);
    }
}
