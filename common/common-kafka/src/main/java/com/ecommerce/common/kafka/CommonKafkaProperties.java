package com.ecommerce.common.kafka;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Retry/backoff tuning for consumer error handling; sane defaults, overridable per service. */
@Validated
@ConfigurationProperties(prefix = "app.kafka.retry")
public record CommonKafkaProperties(
        @DefaultValue("3") @Min(0) int maxRetries,
        @DefaultValue("1000") @Min(1) long initialIntervalMs,
        @DefaultValue("2.0") double multiplier,
        @DefaultValue("10000") @Min(1) long maxIntervalMs) {
}
