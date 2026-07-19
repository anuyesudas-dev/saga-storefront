package com.ecommerce.notification.domain.port;

import com.ecommerce.notification.domain.model.Notification;

import java.util.List;

public interface NotificationRepository {

    Notification save(Notification notification);

    List<Notification> findByOrderId(String orderId);

    List<Notification> findAll();
}
