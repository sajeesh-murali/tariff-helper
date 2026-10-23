package com.tariffhelper.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings bound from {@code app.rate-limit.*}. Every estimate can cost a Claude call, so both limits matter. */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("5") int perClientPerMinute,
        @DefaultValue("500") int globalPerDay) {
}
