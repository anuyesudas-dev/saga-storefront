package com.ecommerce.notification.domain.port;

import com.ecommerce.notification.domain.model.Channel;

/**
 * Port per delivery channel. All registered channel beans are injected as a list
 * into the dispatcher — adding a push-notification channel means adding one
 * adapter class, nothing else changes.
 */
public interface NotificationChannel {

    Channel channel();

    /** @return the resolved recipient address (fake, derived from the customer id). */
    String recipientFor(String customerId);

    void send(String recipient, String content);
}
