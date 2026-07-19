package com.ecommerce.common.kafka;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.serialization.EventJson;
import com.ecommerce.common.web.CorrelationContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * The standard consumer pipeline every Kafka listener runs through:
 *
 * <ol>
 *   <li>deserialize the envelope (typed per topic — each topic carries one event type)</li>
 *   <li>restore the correlation id into MDC so every log line joins the saga trace</li>
 *   <li>skip if the eventId was already processed (at-least-once dedupe, ADR-0002)</li>
 *   <li>invoke the handler; only on success record the eventId in the ledger</li>
 * </ol>
 *
 * <p>Handler exceptions propagate to Spring Kafka's error handler (retry, then
 * DLT). Deserialization failures are poison pills — the error handler treats
 * them as not-retryable and dead-letters immediately.
 */
public class IdempotentEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentEventProcessor.class);

    private final ProcessedEventStore store;
    private final MeterRegistry meterRegistry;
    private final String listenerName;

    public IdempotentEventProcessor(ProcessedEventStore store, MeterRegistry meterRegistry,
                                    String listenerName) {
        this.store = store;
        this.meterRegistry = meterRegistry;
        this.listenerName = listenerName;
    }

    public <T> void process(String message, Class<T> payloadType, Consumer<EventEnvelope<T>> handler) {
        EventEnvelope<T> envelope = EventJson.fromJson(message, payloadType);
        CorrelationContext.set(envelope.correlationId());
        try {
            if (store.alreadyProcessed(envelope.eventId())) {
                meterRegistry.counter("kafka.consumer.duplicates",
                        "event", envelope.eventType()).increment();
                log.info("Skipping duplicate {} (eventId={})", envelope.eventType(),
                        envelope.eventId());
                return;
            }
            handler.accept(envelope);
            store.record(envelope.eventId(), envelope.eventType(), listenerName);
        } finally {
            CorrelationContext.clear();
        }
    }
}
