package com.ecommerce.inventory.domain.model;

import com.ecommerce.inventory.domain.exception.IllegalReservationStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    private static final Instant NOW = Instant.parse("2026-07-18T10:00:00Z");

    private Reservation activeReservation() {
        return Reservation.active("r1", "order-1", List.of(new ReservedItem("p1", 2)), NOW);
    }

    @Test
    void confirmAndReleaseAreOnlyLegalFromActive() {
        Reservation confirmed = activeReservation();
        confirmed.confirm(NOW);
        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);

        Reservation released = activeReservation();
        released.release(NOW);
        assertThat(released.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        assertThatThrownBy(() -> confirmed.release(NOW))
                .isInstanceOf(IllegalReservationStateException.class);
        assertThatThrownBy(() -> released.confirm(NOW))
                .isInstanceOf(IllegalReservationStateException.class);
    }

    @Test
    void tombstoneIsReleasedWithNoItems() {
        Reservation tombstone = Reservation.releasedTombstone("r1", "order-1", NOW);

        assertThat(tombstone.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(tombstone.getItems()).isEmpty();
    }

    @Test
    void reservationRequiresItems() {
        assertThatThrownBy(() -> Reservation.active("r1", "order-1", List.of(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
