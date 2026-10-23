package com.tariffhelper.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory limiter: a fixed one-minute window per client, plus one daily cap for the whole service
 * (UTC day) that bounds the maximum Claude spend. Nothing is persisted; client keys live in memory
 * for at most one minute. State is per instance, so with several instances the limits apply per instance.
 */
public class RateLimiter {

    public enum Decision { ALLOWED, CLIENT_LIMIT, DAILY_LIMIT }

    public record Result(Decision decision, long retryAfterSeconds) {
        public boolean allowed() {
            return decision == Decision.ALLOWED;
        }
    }

    private final Clock clock;
    private final int perClientPerMinute;
    private final int globalPerDay;

    private final Map<String, Integer> clientCounts = new HashMap<>();
    private long currentMinute = -1;
    private long currentDay = -1;
    private int dayCount;

    public RateLimiter(Clock clock, int perClientPerMinute, int globalPerDay) {
        this.clock = clock;
        this.perClientPerMinute = perClientPerMinute;
        this.globalPerDay = globalPerDay;
    }

    public synchronized Result tryAcquire(String clientKey) {
        Instant now = clock.instant();
        long epochSecond = now.getEpochSecond();
        long minute = epochSecond / 60;
        long day = epochSecond / 86_400;

        if (minute != currentMinute) {
            clientCounts.clear();
            currentMinute = minute;
        }
        if (day != currentDay) {
            dayCount = 0;
            currentDay = day;
        }

        int clientCount = clientCounts.getOrDefault(clientKey, 0);
        if (clientCount >= perClientPerMinute) {
            return new Result(Decision.CLIENT_LIMIT, 60 - (epochSecond % 60));
        }
        if (dayCount >= globalPerDay) {
            return new Result(Decision.DAILY_LIMIT, 86_400 - (epochSecond % 86_400));
        }
        clientCounts.put(clientKey, clientCount + 1);
        dayCount++;
        return new Result(Decision.ALLOWED, 0);
    }
}
