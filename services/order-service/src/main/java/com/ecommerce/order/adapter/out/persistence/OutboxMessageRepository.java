package com.ecommerce.order.adapter.out.persistence;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface OutboxMessageRepository extends MongoRepository<OutboxMessage, String> {

    /** Oldest-first batch for the relay — preserves publish order per aggregate. */
    List<OutboxMessage> findTop50ByStatusOrderByCreatedAtAsc(OutboxMessage.Status status);

    /** Backs the outbox.pending gauge. */
    long countByStatus(OutboxMessage.Status status);
}
