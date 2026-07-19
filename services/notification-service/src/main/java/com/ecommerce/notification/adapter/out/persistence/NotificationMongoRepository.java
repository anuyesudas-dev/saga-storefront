package com.ecommerce.notification.adapter.out.persistence;

import com.ecommerce.notification.domain.model.Notification;
import com.ecommerce.notification.domain.port.NotificationRepository;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationMongoRepository
        extends MongoRepository<Notification, String>, NotificationRepository {
}
