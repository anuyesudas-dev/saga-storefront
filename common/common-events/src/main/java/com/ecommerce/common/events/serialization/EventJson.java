package com.ecommerce.common.events.serialization;

import com.ecommerce.common.events.EventEnvelope;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.math.BigDecimal;

/**
 * The single source of truth for the event wire format:
 *
 * <ul>
 *   <li>{@link BigDecimal} serialized as a JSON string ("179.98") — JSON numbers pass
 *       through IEEE-754 doubles in many parsers and can silently lose precision.</li>
 *   <li>{@link java.time.Instant} as ISO-8601 UTC strings, never numeric timestamps.</li>
 *   <li>Tolerant reader: unknown fields are ignored, so additive contract changes
 *       never break old consumers (ADR-0005).</li>
 * </ul>
 */
public final class EventJson {

    private static final ObjectMapper MAPPER = createMapper();

    private EventJson() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static String toJson(EventEnvelope<?> envelope) {
        try {
            return MAPPER.writeValueAsString(envelope);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize event " + envelope.eventType(), e);
        }
    }

    /**
     * Deserializes an envelope with a typed payload. The payload type is chosen by the
     * caller per topic — each topic carries exactly one event type, so no type sniffing
     * (and no Java class names on the wire) is needed.
     */
    public static <T> EventEnvelope<T> fromJson(String json, Class<T> payloadType) {
        JavaType type = MAPPER.getTypeFactory().constructParametricType(EventEnvelope.class, payloadType);
        try {
            return MAPPER.readValue(json, type);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to deserialize event as " + payloadType.getSimpleName(), e);
        }
    }

    private static ObjectMapper createMapper() {
        SimpleModule bigDecimalAsString = new SimpleModule()
                .addSerializer(BigDecimal.class, ToStringSerializer.instance);
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(bigDecimalAsString)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
    }
}
