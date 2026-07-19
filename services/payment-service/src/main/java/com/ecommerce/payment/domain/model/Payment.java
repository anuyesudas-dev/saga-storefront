package com.ecommerce.payment.domain.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Deliberately thin aggregate: payments are simulated, so a record is written
 * once with its outcome — no PENDING/auth/capture lifecycle. The unique orderId
 * index enforces one payment attempt per order, backing consumer idempotency.
 */
@Document(collection = "payments")
public class Payment {

    @Id
    private final String id;
    @Indexed(unique = true)
    private final String orderId;
    private final String customerId;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus status;
    private final String failureReason;
    private final Instant processedAt;
    @Version
    private Long version;

    @PersistenceCreator
    Payment(String id, String orderId, String customerId, BigDecimal amount, String currency,
            PaymentStatus status, String failureReason, Instant processedAt, Long version) {
        this.id = id;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.failureReason = failureReason;
        this.processedAt = processedAt;
        this.version = version;
    }

    public static Payment completed(String id, String orderId, String customerId,
                                    BigDecimal amount, String currency, Instant now) {
        return new Payment(id, orderId, customerId, amount, currency,
                PaymentStatus.COMPLETED, null, now, null);
    }

    public static Payment failed(String id, String orderId, String customerId,
                                 BigDecimal amount, String currency, String reason, Instant now) {
        return new Payment(id, orderId, customerId, amount, currency,
                PaymentStatus.FAILED, reason, now, null);
    }

    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }

    public String getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
