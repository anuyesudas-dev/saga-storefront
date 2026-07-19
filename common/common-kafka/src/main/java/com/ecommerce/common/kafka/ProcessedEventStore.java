package com.ecommerce.common.kafka;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;

/**
 * Mongo-backed idempotency ledger. Writes go to the owning service's database —
 * each service keeps its own ledger.
 */
public class ProcessedEventStore {

    private final MongoTemplate mongoTemplate;
    private final Clock clock;

    public ProcessedEventStore(MongoTemplate mongoTemplate, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.clock = clock;
    }

    public boolean alreadyProcessed(String eventId) {
        return mongoTemplate.exists(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(eventId)),
                ProcessedEvent.class);
    }

    /**
     * Recorded AFTER successful handling — a crash mid-handling must lead to a
     * reprocess, not a skip. A concurrent duplicate insert loses on the primary
     * key and is ignored (the handlers themselves are idempotent — layer two).
     */
    public void record(String eventId, String eventType, String listener) {
        try {
            mongoTemplate.insert(new ProcessedEvent(eventId, eventType, listener, clock.instant()));
        } catch (DuplicateKeyException ignored) {
            // concurrent consumer recorded it first — fine
        }
    }
}
