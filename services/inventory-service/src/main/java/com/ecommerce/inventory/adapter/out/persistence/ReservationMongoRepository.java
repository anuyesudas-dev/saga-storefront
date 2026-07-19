package com.ecommerce.inventory.adapter.out.persistence;

import com.ecommerce.inventory.domain.model.Reservation;
import com.ecommerce.inventory.domain.port.ReservationRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReservationMongoRepository
        extends MongoRepository<Reservation, String>, ReservationRepository {
}
