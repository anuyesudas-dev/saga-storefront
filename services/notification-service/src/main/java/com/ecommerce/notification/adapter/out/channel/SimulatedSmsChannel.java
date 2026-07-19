package com.ecommerce.notification.adapter.out.channel;

import com.ecommerce.notification.domain.model.Channel;
import com.ecommerce.notification.domain.port.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Simulated SMS delivery — a real adapter would call Twilio or an SMS gateway. */
@Component
public class SimulatedSmsChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(SimulatedSmsChannel.class);

    @Override
    public Channel channel() {
        return Channel.SMS;
    }

    @Override
    public String recipientFor(String customerId) {
        // deterministic fake number derived from the customer id
        return "+1-555-%04d".formatted(Math.abs(customerId.hashCode()) % 10_000);
    }

    @Override
    public void send(String recipient, String content) {
        log.info("[SMS] to {}: {}", recipient, content);
    }
}
