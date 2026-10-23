package com.tariffhelper.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    /** A clock the test can move. */
    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    // 2026-10-23T12:00:00Z, aligned to a minute boundary
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));

    @Test
    void allowsUpToTheLimitThenBlocksTheClient() {
        RateLimiter limiter = new RateLimiter(clock, 2, 100);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        RateLimiter.Result blocked = limiter.tryAcquire("a");
        assertThat(blocked.decision()).isEqualTo(RateLimiter.Decision.CLIENT_LIMIT);
        assertThat(blocked.retryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void otherClientsAreNotAffected() {
        RateLimiter limiter = new RateLimiter(clock, 1, 100);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void windowResetsAfterAMinute() {
        RateLimiter limiter = new RateLimiter(clock, 1, 100);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        clock.advanceSeconds(20);
        assertThat(limiter.tryAcquire("a").retryAfterSeconds()).isEqualTo(40);
        clock.advanceSeconds(40);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
    }

    @Test
    void dailyCapBlocksEveryoneUntilTheNextUtcDay() {
        RateLimiter limiter = new RateLimiter(clock, 100, 2);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
        RateLimiter.Result blocked = limiter.tryAcquire("c");
        assertThat(blocked.decision()).isEqualTo(RateLimiter.Decision.DAILY_LIMIT);
        assertThat(blocked.retryAfterSeconds()).isEqualTo(12 * 3600);

        clock.advanceSeconds(12 * 3600);
        assertThat(limiter.tryAcquire("c").allowed()).isTrue();
    }

    @Test
    void aBlockedClientRequestDoesNotUseUpTheDailyCap() {
        RateLimiter limiter = new RateLimiter(clock, 1, 2);
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }
}
