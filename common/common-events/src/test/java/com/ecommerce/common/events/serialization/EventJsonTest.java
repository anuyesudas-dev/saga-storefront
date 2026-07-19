package com.ecommerce.common.events.serialization;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.order.OrderCreatedPayload;
import com.ecommerce.common.events.order.OrderLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventJsonTest {

    private final OrderCreatedPayload payload = new OrderCreatedPayload(
            "order-1",
            "cust-42",
            List.of(new OrderLine("prod-1", "Mechanical Keyboard", 2, new BigDecimal("89.99"))),
            new BigDecimal("179.98"),
            "USD");

    @Test
    void roundTripPreservesEnvelopeAndPayloadExactly() {
        EventEnvelope<OrderCreatedPayload> original =
                EventEnvelope.of(EventTypes.ORDER_CREATED, "corr-1", "order-service", payload);

        EventEnvelope<OrderCreatedPayload> restored =
                EventJson.fromJson(EventJson.toJson(original), OrderCreatedPayload.class);

        assertThat(restored).isEqualTo(original);
    }

    @Test
    void bigDecimalIsWrittenAsStringToAvoidPrecisionLoss() {
        EventEnvelope<OrderCreatedPayload> envelope =
                EventEnvelope.of(EventTypes.ORDER_CREATED, "corr-1", "order-service", payload);

        String json = EventJson.toJson(envelope);

        assertThat(json).contains("\"totalAmount\":\"179.98\"");
        assertThat(json).contains("\"unitPrice\":\"89.99\"");
    }

    @Test
    void instantIsWrittenAsIso8601() {
        EventEnvelope<OrderCreatedPayload> envelope =
                EventEnvelope.of(EventTypes.ORDER_CREATED, "corr-1", "order-service", payload);

        String json = EventJson.toJson(envelope);

        assertThat(json).containsPattern("\"occurredAt\":\"\\d{4}-\\d{2}-\\d{2}T");
    }

    @Test
    void unknownFieldsAreIgnoredByTolerantReader() {
        EventEnvelope<OrderCreatedPayload> original =
                EventEnvelope.of(EventTypes.ORDER_CREATED, "corr-1", "order-service", payload);
        String withExtraField = EventJson.toJson(original)
                .replaceFirst("\\{", "{\"someFutureField\":\"ignored\",");

        EventEnvelope<OrderCreatedPayload> restored =
                EventJson.fromJson(withExtraField, OrderCreatedPayload.class);

        assertThat(restored.payload()).isEqualTo(payload);
    }
}
