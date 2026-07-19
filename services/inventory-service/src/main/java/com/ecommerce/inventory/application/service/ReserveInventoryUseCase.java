package com.ecommerce.inventory.application.service;

import com.ecommerce.common.events.EventEnvelope;
import com.ecommerce.common.events.EventTypes;
import com.ecommerce.common.events.Topics;
import com.ecommerce.common.events.inventory.InventoryReservationFailedPayload;
import com.ecommerce.common.events.inventory.InventoryReservedPayload;
import com.ecommerce.common.events.inventory.ReservedLine;
import com.ecommerce.common.web.CorrelationContext;
import com.ecommerce.inventory.application.command.ReserveInventoryCommand;
import com.ecommerce.inventory.application.port.EventPublisher;
import com.ecommerce.inventory.domain.exception.InsufficientStockException;
import com.ecommerce.inventory.domain.model.Product;
import com.ecommerce.inventory.domain.model.Reservation;
import com.ecommerce.inventory.domain.model.ReservedItem;
import com.ecommerce.inventory.domain.port.ProductRepository;
import com.ecommerce.inventory.domain.port.ReservationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles OrderCreated: atomically reserves stock across all line items, then
 * publishes the outcome.
 *
 * <p>Structure note: the reservation runs inside a programmatic transaction
 * (TransactionTemplate) and the Kafka publish happens strictly AFTER commit —
 * publishing from inside the transaction could emit an event for a reservation
 * that then rolls back.
 */
@Service
public class ReserveInventoryUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReserveInventoryUseCase.class);

    private final ProductRepository products;
    private final ReservationRepository reservations;
    private final EventPublisher publisher;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final String serviceName;

    public ReserveInventoryUseCase(ProductRepository products, ReservationRepository reservations,
                                   EventPublisher publisher, TransactionTemplate transaction,
                                   Clock clock, MeterRegistry meterRegistry,
                                   @Value("${spring.application.name}") String serviceName) {
        this.products = products;
        this.reservations = reservations;
        this.publisher = publisher;
        this.transaction = transaction;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.serviceName = serviceName;
    }

    public void handle(ReserveInventoryCommand command) {
        Optional<Reservation> existing = reservations.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            // duplicate delivery, or a tombstone from order-cancelled overtaking order-created
            log.info("Reservation for order {} already exists with status {} — skipping",
                    command.orderId(), existing.get().getStatus());
            return;
        }

        Reservation reservation;
        try {
            reservation = transaction.execute(status -> reserveAll(command));
        } catch (InsufficientStockException e) {
            meterRegistry.counter("inventory.reservations", "outcome", "failed").increment();
            log.info("Reservation failed for order {}: {}", command.orderId(), e.getMessage());
            publisher.publish(Topics.INVENTORY_FAILED, command.orderId(),
                    envelope(EventTypes.INVENTORY_RESERVATION_FAILED,
                            new InventoryReservationFailedPayload(command.orderId(), command.customerId(),
                                    e.getMessage(), e.getUnavailableProductIds())));
            return;
        } catch (DuplicateKeyException e) {
            // two consumers raced past the existence check; the unique orderId index broke the tie
            log.info("Concurrent duplicate reservation for order {} — skipping", command.orderId());
            return;
        }

        meterRegistry.counter("inventory.reservations", "outcome", "reserved").increment();
        log.info("Reserved {} item line(s) for order {}", reservation.getItems().size(),
                command.orderId());
        List<ReservedLine> lines = reservation.getItems().stream()
                .map(i -> new ReservedLine(i.productId(), i.quantity()))
                .toList();
        publisher.publish(Topics.INVENTORY_RESERVED, command.orderId(),
                envelope(EventTypes.INVENTORY_RESERVED,
                        new InventoryReservedPayload(command.orderId(), command.customerId(),
                                reservation.getId(), lines, command.totalAmount(), command.currency())));
    }

    /** Runs inside the Mongo transaction: all products reserved, or none. */
    private Reservation reserveAll(ReserveInventoryCommand command) {
        Instant now = clock.instant();

        // first pass: check availability everywhere so the failure event can name
        // every unavailable product, not just the first
        List<String> unavailable = new ArrayList<>();
        List<Product> loaded = new ArrayList<>();
        for (ReserveInventoryCommand.Line line : command.items()) {
            Optional<Product> product = products.findById(line.productId());
            if (product.isEmpty() || !product.get().hasAvailable(line.quantity())) {
                unavailable.add(line.productId());
            } else {
                loaded.add(product.get());
            }
        }
        if (!unavailable.isEmpty()) {
            throw new InsufficientStockException(unavailable);
        }

        for (int i = 0; i < loaded.size(); i++) {
            Product product = loaded.get(i);
            product.reserve(command.items().get(i).quantity(), now);
            products.save(product);
        }

        List<ReservedItem> items = command.items().stream()
                .map(l -> new ReservedItem(l.productId(), l.quantity()))
                .toList();
        return reservations.save(
                Reservation.active(UUID.randomUUID().toString(), command.orderId(), items, now));
    }

    private EventEnvelope<?> envelope(String eventType, Object payload) {
        return EventEnvelope.of(eventType, CorrelationContext.currentOrNew(), serviceName, payload);
    }
}
