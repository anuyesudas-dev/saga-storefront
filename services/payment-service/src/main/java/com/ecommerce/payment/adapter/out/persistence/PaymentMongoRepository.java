package com.ecommerce.payment.adapter.out.persistence;

import com.ecommerce.payment.domain.model.Payment;
import com.ecommerce.payment.domain.port.PaymentRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentMongoRepository extends MongoRepository<Payment, String>, PaymentRepository {
}
